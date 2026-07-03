package com.example.mapicomandas.data.netpay

import com.example.mapicomandas.data.ConfigService
import com.example.mapicomandas.data.db.JdbcDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cliente Smart de NetPay portado de MapiPOS (NetPaySmartClient + flujo de PagosNetPay).
 *
 * Flujo:
 *  1. Inserta fila en dbo.PagosNetPay (MapiTxnId GUID, Estatus='PENDIENTE').
 *  2. OAuth (Basic→Bearer) + POST sale → encola la venta en la terminal.
 *  3. La terminal cobra y un servicio callback actualiza PagosNetPay.
 *  4. Polling JDBC sobre Estatus hasta APROBADA/RECHAZADA o timeout.
 *
 * Requiere que el servicio NetPayCallback esté desplegado y la terminal configurada
 * para postearle; si no, el polling termina en TIMEOUT.
 */
@Singleton
class NetPayService @Inject constructor(
    private val db: JdbcDataSource,
    private val config: ConfigService,
    private val responseServer: NetPayResponseServer
) {
    suspend fun obtenerConfig(): NetPayConfig {
        config.cargar()
        // Normaliza rutas viejas (/gateway/ o /oauth/token) a las correctas de la doc
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
            responsePort = config.numero("NetPayResponsePort", 8081.0).toInt().takeIf { it in 1..65535 } ?: 8081,
            callbackUrl = config.texto("NetPayCallbackUrl").trim()
        )
    }

    /**
     * Arranca el receptor embebido (servicio de respuesta) para que la terminal pueda
     * postearle — incluido el POST de prueba al registrar la URL. Idempotente.
     * Devuelve el error o null.
     */
    /**
     * Diagnóstico: últimas transacciones de dbo.PagosNetPay LEÍDAS POR LA CONEXIÓN DE LA APP.
     * Si el callback externo escribiera en otra BD, aquí NO aparecerían filas APROBADA
     * (solo los PENDIENTE que inserta la propia app) → prueba directa del problema de "no llega".
     */
    suspend fun ultimasTransacciones(n: Int = 15): List<NetPayFila> {
        asegurarTabla()
        val top = n.coerceIn(1, 100)
        return runCatching {
            db.query(
                """SELECT TOP $top CONVERT(char(36), MapiTxnId) AS MapiTxnId, Estatus, AuthCode, OrderId, MontoCobrado, FechaAlta, FechaResp
                   FROM dbo.PagosNetPay ORDER BY FechaAlta DESC""",
                emptyList()
            ) { rs ->
                NetPayFila(
                    mapiTxnId = rs.getString("MapiTxnId") ?: "",
                    estatus = rs.getString("Estatus") ?: "",
                    authCode = rs.getString("AuthCode"),
                    orderId = rs.getString("OrderId"),
                    monto = rs.getString("MontoCobrado"),
                    fechaAlta = rs.getTimestamp("FechaAlta")?.toString()?.take(19) ?: "",
                    fechaResp = rs.getTimestamp("FechaResp")?.toString()?.take(19)
                )
            }
        }.getOrElse { emptyList() }
    }

    suspend fun iniciarReceptor(): String? {
        val cfg = obtenerConfig()
        if (cfg.usaCallbackExterno) {
            // En modo callback no hay receptor local: detén el que pudiera estar corriendo
            responseServer.detener()
            return null
        }
        return responseServer.asegurarIniciado(cfg.responsePort)
    }

    /**
     * Prueba las credenciales pidiendo el token OAuth (como FrmNetPayConfig de MapiPOS).
     * Usa los valores [cfg] (sin guardar) para validar antes de persistir.
     * Devuelve null si OK, o el mensaje de error.
     */
    suspend fun probarCredenciales(cfg: NetPayConfig): String? = withContext(Dispatchers.IO) {
        if (cfg.username.isBlank() || cfg.authString.isBlank())
            return@withContext "Faltan Usuario y/o Auth String."
        val urlUsada = cfg.baseUrl.trimEnd('/') + cfg.oauthPath
        runCatching { solicitarToken(cfg) }.fold(
            onSuccess = { null },
            onFailure = { "POST $urlUsada\n${it.message}" }
        )
    }

    /** Crea dbo.PagosNetPay si no existe (mismo esquema que MapiPOS). */
    suspend fun asegurarTabla() {
        runCatching {
            db.execute(
                """IF OBJECT_ID('dbo.PagosNetPay','U') IS NULL
                   CREATE TABLE dbo.PagosNetPay (
                     MapiTxnId UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
                     VentaId INT NULL,
                     MontoSolicit DECIMAL(18,2) NOT NULL,
                     Estatus VARCHAR(12) NOT NULL CONSTRAINT DF_PagosNetPay_Estatus DEFAULT('PENDIENTE'),
                     ResponseCode VARCHAR(4) NULL, AuthCode VARCHAR(20) NULL, OrderId VARCHAR(60) NULL,
                     Marca VARCHAR(30) NULL, Ultimos4 VARCHAR(4) NULL, TipoTarjeta VARCHAR(2) NULL,
                     MontoCobrado VARCHAR(20) NULL, Propina VARCHAR(20) NULL, Mensaje NVARCHAR(200) NULL,
                     FechaTerminal VARCHAR(40) NULL, PayloadJson NVARCHAR(MAX) NULL,
                     FechaAlta DATETIME2 NOT NULL CONSTRAINT DF_PagosNetPay_FechaAlta DEFAULT(SYSUTCDATETIME()),
                     FechaResp DATETIME2 NULL)""",
                emptyList()
            )
        }
    }

    /**
     * Cobra [monto] con la terminal. Bloquea hasta resultado/timeout.
     * [onProgreso] recibe mensajes de estado. Se cancela cancelando la corrutina.
     */
    suspend fun cobrar(
        monto: Double,
        folioNumber: String? = null,
        msi: Int? = null,
        onProgreso: (String) -> Unit = {}
    ): NetPayResultado {
        val cfg = obtenerConfig()
        if (!cfg.estaConfigurado)
            return NetPayResultado(false, "ERROR", "", mensaje = "NetPay no está configurado (Settings → Terminal NetPay).")

        onProgreso("Preparando transacción…")
        val mapiTxnId = java.util.UUID.randomUUID().toString()

        // Modo callback externo (Azure/Spin): la terminal postea al servicio, que escribe
        // en dbo.PagosNetPay; la app hace polling a la BD. Sin receptor embebido.
        if (cfg.usaCallbackExterno) {
            asegurarTabla()
            runCatching {
                db.execute(
                    "INSERT INTO dbo.PagosNetPay (MapiTxnId, MontoSolicit, Estatus) VALUES (CONVERT(uniqueidentifier, ?),?, 'PENDIENTE')",
                    listOf(mapiTxnId, monto)
                )
            }.onFailure { return NetPayResultado(false, "ERROR", mapiTxnId, mensaje = "No se pudo registrar la transacción: ${it.message}") }

            onProgreso("Despachando a la terminal…")
            val despachoCb = withContext(Dispatchers.IO) {
                runCatching {
                    val token = solicitarToken(cfg)
                    despacharVenta(cfg, token, monto, mapiTxnId, folioNumber, msi)
                }
            }
            if (despachoCb.isFailure)
                return NetPayResultado(false, "ERROR", mapiTxnId, mensaje = "Error al despachar a la terminal: ${despachoCb.exceptionOrNull()?.message}")

            onProgreso("Esperando terminal… presione la tarjeta")
            return esperarResultadoBD(cfg, mapiTxnId)
        }

        // Modo receptor embebido (intranet): la app recibe el POST directo.
        // 0. Arrancar el receptor embebido (servicio de respuesta en la intranet)
        responseServer.asegurarIniciado(cfg.responsePort)?.let { err ->
            return NetPayResultado(false, "ERROR", mapiTxnId, mensaje = err)
        }
        // Registrar la espera ANTES de despachar (la respuesta puede llegar muy rápido)
        val esperaReceptor = responseServer.registrar(mapiTxnId)

        // 1. Insertar PENDIENTE (best-effort, para historial; no bloquea el cobro)
        asegurarTabla()
        runCatching {
            db.execute(
                "INSERT INTO dbo.PagosNetPay (MapiTxnId, MontoSolicit, Estatus) VALUES (CONVERT(uniqueidentifier, ?),?, 'PENDIENTE')",
                listOf(mapiTxnId, monto)
            )
        }

        // 2. OAuth + 3. Sale
        onProgreso("Despachando a la terminal…")
        val despacho = withContext(Dispatchers.IO) {
            runCatching {
                val token = solicitarToken(cfg)
                despacharVenta(cfg, token, monto, mapiTxnId, folioNumber, msi)
            }
        }
        if (despacho.isFailure) {
            responseServer.cancelar(mapiTxnId)
            return NetPayResultado(false, "ERROR", mapiTxnId, mensaje = "Error al despachar a la terminal: ${despacho.exceptionOrNull()?.message}")
        }

        // 4. Esperar el POST de la terminal al receptor embebido (cancelable).
        //    Respaldo: si venciera, una lectura a PagosNetPay (por si hubiera webhook externo).
        onProgreso("Esperando terminal… presione la tarjeta")
        return try {
            kotlinx.coroutines.withTimeoutOrNull(cfg.pollTimeoutSeconds * 1000L) {
                esperaReceptor.await()
            } ?: run {
                // Respaldo: ¿algún callback externo escribió el resultado?
                leerResultadoBD(mapiTxnId) ?: NetPayResultado(
                    false, "TIMEOUT", mapiTxnId,
                    mensaje = "La terminal no respondió en ${cfg.pollTimeoutSeconds}s. " +
                              "Verifica que en la terminal esté configurado el servicio de respuesta " +
                              "hacia ${com.example.mapicomandas.util.NetworkUtils.urlReceptorNetPay(cfg.responsePort)}"
                )
            }
        } finally {
            responseServer.cancelar(mapiTxnId)
        }
    }

    /** Polling a dbo.PagosNetPay hasta resultado no-PENDIENTE o timeout (modo callback externo). */
    private suspend fun esperarResultadoBD(cfg: NetPayConfig, mapiTxnId: String): NetPayResultado {
        val deadline = System.currentTimeMillis() + cfg.pollTimeoutSeconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            kotlin.coroutines.coroutineContext.ensureActive()   // permite cancelar
            leerResultadoBD(mapiTxnId)?.let { return it }
            delay(cfg.pollIntervalMs)
        }
        return NetPayResultado(false, "TIMEOUT", mapiTxnId,
            mensaje = "Sin resultado en ${cfg.pollTimeoutSeconds}s. Verifica que la terminal apunte a " +
                      "${cfg.callbackUrl} y que ese callback escriba dbo.PagosNetPay en la MISMA base de datos " +
                      "que usa esta app (correlacionando por traceability.mapiTxnId).")
    }

    /** Lectura única de PagosNetPay (respaldo si existiera un callback externo). */
    private suspend fun leerResultadoBD(mapiTxnId: String): NetPayResultado? =
        runCatching {
            db.queryOne(
                """SELECT Estatus, ResponseCode, AuthCode, OrderId, Marca, Ultimos4, TipoTarjeta, MontoCobrado, Mensaje
                   FROM dbo.PagosNetPay WHERE MapiTxnId = CONVERT(uniqueidentifier, ?)""",
                listOf(mapiTxnId)
            ) { rs ->
                NetPayResultado(
                    aprobada = rs.getString("Estatus").equals("APROBADA", true),
                    estatus = rs.getString("Estatus") ?: "PENDIENTE",
                    mapiTxnId = mapiTxnId,
                    responseCode = rs.getString("ResponseCode"),
                    authCode = rs.getString("AuthCode"),
                    orderId = rs.getString("OrderId"),
                    marca = rs.getString("Marca"),
                    ultimos4 = rs.getString("Ultimos4"),
                    tipoTarjeta = rs.getString("TipoTarjeta"),
                    montoCobrado = rs.getString("MontoCobrado"),
                    mensaje = rs.getString("Mensaje")
                )
            }
        }.getOrNull()?.takeIf { !it.estatus.equals("PENDIENTE", true) }

    /** Cancela una venta del mismo día (orderId del resultado). */
    suspend fun cancelar(orderId: String): Boolean = withContext(Dispatchers.IO) {
        val cfg = obtenerConfig()
        if (!cfg.estaConfigurado || orderId.isBlank()) return@withContext false
        runCatching {
            val token = solicitarToken(cfg)
            val url = URL(cfg.baseUrl.trimEnd('/') + config.texto("NetPayCancelPath", "/integration-service/transactions/cancel"))
            val body = JSONObject().apply {
                put("serialNumber", cfg.serialNumber)
                put("orderId", orderId)
                put("storeId", cfg.storeId)
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

    /**
     * Recupera el resultado de una venta cuyo resultado NO llegó al receptor
     * (corte de red tras aprobar). NetPay no tiene consulta directa: se pide una
     * REIMPRESIÓN por folio, que re-entrega el JSON completo al servicio de respuesta.
     * Enviamos traceability.mapiTxnId propio para correlacionar la re-entrega.
     *
     * @param folioId el mismo folioNumber enviado en la venta.
     */
    suspend fun recuperarPorFolio(folioId: String): NetPayResultado {
        val cfg = obtenerConfig()
        if (!cfg.estaConfigurado)
            return NetPayResultado(false, "ERROR", "", mensaje = "NetPay no está configurado.")
        if (folioId.isBlank())
            return NetPayResultado(false, "ERROR", "", mensaje = "Falta el folio para reimprimir.")

        val mapiTxnId = java.util.UUID.randomUUID().toString()

        // Modo callback externo: la reimpresión re-entrega al callback → poll a la BD.
        if (cfg.usaCallbackExterno) {
            asegurarTabla()
            runCatching {
                db.execute(
                    "INSERT INTO dbo.PagosNetPay (MapiTxnId, MontoSolicit, Estatus) VALUES (CONVERT(uniqueidentifier, ?),?, 'PENDIENTE')",
                    listOf(mapiTxnId, 0.0)
                )
            }
            val despachoCb = withContext(Dispatchers.IO) {
                runCatching { despacharReimpresion(cfg, solicitarToken(cfg), folioId, mapiTxnId) }
            }
            if (despachoCb.isFailure)
                return NetPayResultado(false, "ERROR", mapiTxnId,
                    mensaje = "Error al solicitar reimpresión: ${despachoCb.exceptionOrNull()?.message}")
            return esperarResultadoBD(cfg, mapiTxnId)
        }

        // Modo receptor embebido
        responseServer.asegurarIniciado(cfg.responsePort)?.let { err ->
            return NetPayResultado(false, "ERROR", "", mensaje = err)
        }
        val espera = responseServer.registrar(mapiTxnId)

        val despacho = withContext(Dispatchers.IO) {
            runCatching {
                val token = solicitarToken(cfg)
                despacharReimpresion(cfg, token, folioId, mapiTxnId)
            }
        }
        if (despacho.isFailure) {
            responseServer.cancelar(mapiTxnId)
            return NetPayResultado(false, "ERROR", mapiTxnId,
                mensaje = "Error al solicitar reimpresión: ${despacho.exceptionOrNull()?.message}")
        }

        return try {
            kotlinx.coroutines.withTimeoutOrNull(cfg.pollTimeoutSeconds * 1000L) {
                espera.await()
            } ?: NetPayResultado(false, "TIMEOUT", mapiTxnId,
                mensaje = "La terminal no re-entregó el resultado en ${cfg.pollTimeoutSeconds}s.")
        } finally {
            responseServer.cancelar(mapiTxnId)
        }
    }

    private fun despacharReimpresion(cfg: NetPayConfig, token: String, folioId: String, mapiTxnId: String) {
        val url = URL(cfg.baseUrl.trimEnd('/') + cfg.reprintPath)
        val body = JSONObject().apply {
            put("serialNumber", cfg.serialNumber)
            put("storeId", cfg.storeId)
            put("folioId", folioId)
            put("orderId", "")   // vacío → NetPay busca por folioId
            put("traceability", JSONObject().put("mapiTxnId", mapiTxnId))
        }.toString()
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 30_000; readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json"); doOutput = true
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        leerRespuesta(conn)   // lanza si HTTP != 2xx
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private fun solicitarToken(cfg: NetPayConfig): String {
        val url = URL(cfg.baseUrl.trimEnd('/') + cfg.oauthPath)
        val body = "grant_type=password" +
                "&username=" + URLEncoder.encode(cfg.username, "UTF-8") +
                "&password=" + URLEncoder.encode(cfg.password, "UTF-8")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000; readTimeout = 30_000
            setRequestProperty("Authorization", "Basic " + cfg.authString)
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            doOutput = true
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val texto = leerRespuesta(conn)
        val json = JSONObject(texto)
        return json.optString("access_token").ifBlank {
            throw IllegalStateException("NetPay no devolvió access_token")
        }
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
            requestMethod = "POST"
            connectTimeout = 30_000; readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        leerRespuesta(conn)   // lanza si HTTP != 2xx
    }

    private fun leerRespuesta(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val texto = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw IllegalStateException("HTTP $code: $texto")
        return texto
    }
}
