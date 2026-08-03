package com.example.mapicomandas.data.netpay

import com.example.mapicomandas.data.ConfigService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cliente Smart de NetPay — 100% sin SQL. El resultado del cobro llega por el receptor
 * embebido (servicio de respuesta en la intranet) DIRECTO a memoria; no se escribe ni se
 * consulta dbo.PagosNetPay. El pago se registra en la venta al cerrar la comanda (API).
 *
 * Flujo:
 *  1. OAuth (Basic→Bearer) + POST sale → encola la venta en la terminal (con traceability.mapiTxnId).
 *  2. La terminal cobra y POSTea el resultado al receptor embebido (http://<ip-tablet>:puerto).
 *  3. El receptor completa la espera en memoria → resultado inmediato.
 *
 * La config (serial, storeId, credenciales, puerto) se lee de la API central (GET /v1/config).
 */
@Singleton
class NetPayService @Inject constructor(
    private val config: ConfigService,
    private val responseServer: NetPayResponseServer
) {
    suspend fun obtenerConfig(): NetPayConfig {
        config.cargar()
        fun rutaOAuth(v: String) =
            if (v.contains("/gateway/") || v == "/oauth/token" || v.isBlank())
                "/oauth-service/oauth/token" else v
        fun rutaSale(v: String) =
            if (v.contains("/gateway/") || v.isBlank())
                "/integration-service/transactions/sale" else v
        fun rutaReprint(v: String) =
            if (v.contains("/gateway/") || v.isBlank())
                "/integration-service/transactions/reprint" else v
        return NetPayConfig(
            baseUrl = config.texto("NetPayBaseUrl", "https://api-154.api-netpay.com"),
            oauthPath = rutaOAuth(config.texto("NetPayOAuthPath", "/oauth-service/oauth/token")),
            salePath = rutaSale(config.texto("NetPaySalePath", "/integration-service/transactions/sale")),
            reprintPath = rutaReprint(config.texto("NetPayReprintPath", "/integration-service/transactions/reprint")),
            authString = config.texto("NetPayAuthString"),
            username = config.texto("NetPayUsername"),
            password = config.texto("NetPayPassword"),
            serialNumber = config.texto("NetPaySerialNumber"),
            storeId = config.texto("NetPayStoreId"),
            responsePort = config.numero("NetPayResponsePort", 8081.0).toInt().takeIf { it in 1..65535 } ?: 8081
        )
    }

    /** Arranca el receptor embebido (idempotente). Devuelve el error o null. */
    suspend fun iniciarReceptor(): String? =
        responseServer.asegurarIniciado(obtenerConfig().responsePort)

    /** Prueba las credenciales pidiendo el token OAuth. null si OK, o el mensaje de error. */
    suspend fun probarCredenciales(cfg: NetPayConfig): String? = withContext(Dispatchers.IO) {
        if (cfg.username.isBlank() || cfg.authString.isBlank())
            return@withContext "Faltan Usuario y/o Auth String."
        val urlUsada = cfg.baseUrl.trimEnd('/') + cfg.oauthPath
        runCatching { solicitarToken(cfg) }.fold(
            onSuccess = { null },
            onFailure = { "POST $urlUsada\n${it.message}" }
        )
    }

    /**
     * Cobra [monto] con la terminal. Bloquea hasta el resultado (receptor embebido) o timeout.
     * Cancelable cancelando la corrutina.
     */
    suspend fun cobrar(
        monto: Double,
        folioNumber: String? = null,
        msi: Int? = null,
        onProgreso: (String) -> Unit = {}
    ): NetPayResultado {
        val cfg = obtenerConfig()
        if (!cfg.estaConfigurado)
            return NetPayResultado(false, "ERROR", "", mensaje = "NetPay no está configurado (Ajustes → Terminal NetPay).")

        onProgreso("Preparando transacción…")
        val mapiTxnId = java.util.UUID.randomUUID().toString()

        responseServer.asegurarIniciado(cfg.responsePort)?.let { err ->
            return NetPayResultado(false, "ERROR", mapiTxnId, mensaje = err)
        }
        val espera = responseServer.registrar(mapiTxnId)   // registrar ANTES de despachar

        onProgreso("Despachando a la terminal…")
        val despacho = withContext(Dispatchers.IO) {
            runCatching { despacharVenta(cfg, solicitarToken(cfg), monto, mapiTxnId, folioNumber, msi) }
        }
        if (despacho.isFailure) {
            responseServer.cancelar(mapiTxnId)
            return NetPayResultado(false, "ERROR", mapiTxnId, mensaje = "Error al despachar a la terminal: ${despacho.exceptionOrNull()?.message}")
        }

        onProgreso("Esperando terminal… presione la tarjeta")
        return try {
            withTimeoutOrNull(cfg.pollTimeoutSeconds * 1000L) { espera.await() }
                ?: NetPayResultado(
                    false, "TIMEOUT", mapiTxnId,
                    mensaje = "La terminal no respondió en ${cfg.pollTimeoutSeconds}s. Verifica que en la terminal " +
                              "esté configurado el servicio de respuesta hacia " +
                              com.example.mapicomandas.util.NetworkUtils.urlReceptorNetPay(cfg.responsePort)
                )
        } finally {
            responseServer.cancelar(mapiTxnId)
        }
    }

    /**
     * Recupera el resultado de una venta que no llegó al receptor (corte de red tras aprobar):
     * pide una REIMPRESIÓN por folio, que re-entrega el JSON al receptor embebido.
     */
    suspend fun recuperarPorFolio(folioId: String): NetPayResultado {
        val cfg = obtenerConfig()
        if (!cfg.estaConfigurado) return NetPayResultado(false, "ERROR", "", mensaje = "NetPay no está configurado.")
        if (folioId.isBlank()) return NetPayResultado(false, "ERROR", "", mensaje = "Falta el folio para reimprimir.")

        val mapiTxnId = java.util.UUID.randomUUID().toString()
        responseServer.asegurarIniciado(cfg.responsePort)?.let { err ->
            return NetPayResultado(false, "ERROR", "", mensaje = err)
        }
        val espera = responseServer.registrar(mapiTxnId)

        val despacho = withContext(Dispatchers.IO) {
            runCatching { despacharReimpresion(cfg, solicitarToken(cfg), folioId, mapiTxnId) }
        }
        if (despacho.isFailure) {
            responseServer.cancelar(mapiTxnId)
            return NetPayResultado(false, "ERROR", mapiTxnId,
                mensaje = "Error al solicitar reimpresión: ${despacho.exceptionOrNull()?.message}")
        }
        return try {
            withTimeoutOrNull(cfg.pollTimeoutSeconds * 1000L) { espera.await() }
                ?: NetPayResultado(false, "TIMEOUT", mapiTxnId,
                    mensaje = "La terminal no re-entregó el resultado en ${cfg.pollTimeoutSeconds}s.")
        } finally {
            responseServer.cancelar(mapiTxnId)
        }
    }

    /** Cancela una venta del mismo día (orderId del resultado). */
    suspend fun cancelar(orderId: String): Boolean = withContext(Dispatchers.IO) {
        val cfg = obtenerConfig()
        if (!cfg.estaConfigurado || orderId.isBlank()) return@withContext false
        runCatching {
            val token = solicitarToken(cfg)
            val url = URL(cfg.baseUrl.trimEnd('/') + config.texto("NetPayCancelPath", "/integration-service/transactions/cancel"))
            val body = JSONObject().apply {
                put("serialNumber", cfg.serialNumber); put("orderId", orderId); put("storeId", cfg.storeId)
            }.toString()
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 30_000; readTimeout = 30_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json"); doOutput = true
            }
            conn.outputStream.use { it.write(body.toByteArray()) }
            leerRespuesta(conn)
        }.isSuccess
    }

    private fun despacharReimpresion(cfg: NetPayConfig, token: String, folioId: String, mapiTxnId: String) {
        val url = URL(cfg.baseUrl.trimEnd('/') + cfg.reprintPath)
        val body = JSONObject().apply {
            put("serialNumber", cfg.serialNumber); put("storeId", cfg.storeId)
            put("folioId", folioId); put("orderId", "")
            put("traceability", JSONObject().put("mapiTxnId", mapiTxnId))
        }.toString()
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 30_000; readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json"); doOutput = true
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        leerRespuesta(conn)
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────
    private fun solicitarToken(cfg: NetPayConfig): String {
        val url = URL(cfg.baseUrl.trimEnd('/') + cfg.oauthPath)
        val body = "grant_type=password" +
                "&username=" + URLEncoder.encode(cfg.username, "UTF-8") +
                "&password=" + URLEncoder.encode(cfg.password, "UTF-8")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 30_000; readTimeout = 30_000
            setRequestProperty("Authorization", "Basic " + cfg.authString)
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            doOutput = true
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val json = JSONObject(leerRespuesta(conn))
        return json.optString("access_token").ifBlank { throw IllegalStateException("NetPay no devolvió access_token") }
    }

    private fun despacharVenta(
        cfg: NetPayConfig, token: String, monto: Double, mapiTxnId: String, folioNumber: String?, msi: Int?
    ) {
        val url = URL(cfg.baseUrl.trimEnd('/') + cfg.salePath)
        val body = JSONObject().apply {
            put("serialNumber", cfg.serialNumber)
            put("amount", Math.round(monto * 100.0) / 100.0)
            put("storeId", cfg.storeId)
            put("traceability", JSONObject().put("mapiTxnId", mapiTxnId))
            if (!folioNumber.isNullOrBlank()) put("folioNumber", folioNumber)
            if (msi != null && msi > 0) put("msi", msi)
        }.toString()
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 30_000; readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json"); doOutput = true
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        leerRespuesta(conn)
    }

    private fun leerRespuesta(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val texto = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw IllegalStateException("HTTP $code: $texto")
        return texto
    }
}
