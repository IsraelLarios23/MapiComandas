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
    private val config: ConfigService
) {
    suspend fun obtenerConfig(): NetPayConfig {
        config.cargar()
        return NetPayConfig(
            baseUrl = config.texto("NetPayBaseUrl", "https://api-154.api-netpay.com"),
            oauthPath = config.texto("NetPayOAuthPath", "/oauth-service/oauth/token"),
            salePath = config.texto("NetPaySalePath", "/integration-service/transactions/sale"),
            authString = config.texto("NetPayAuthString"),
            username = config.texto("NetPayUsername"),
            password = config.texto("NetPayPassword"),
            serialNumber = config.texto("NetPaySerialNumber"),
            storeId = config.texto("NetPayStoreId")
        )
    }

    /**
     * Prueba las credenciales pidiendo el token OAuth (como FrmNetPayConfig de MapiPOS).
     * Usa los valores [cfg] (sin guardar) para validar antes de persistir.
     * Devuelve null si OK, o el mensaje de error.
     */
    suspend fun probarCredenciales(cfg: NetPayConfig): String? = withContext(Dispatchers.IO) {
        if (cfg.username.isBlank() || cfg.authString.isBlank())
            return@withContext "Faltan Usuario y/o Auth String."
        runCatching { solicitarToken(cfg) }.fold(
            onSuccess = { null },
            onFailure = { "Falló la autenticación: ${it.message}" }
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
        asegurarTabla()
        val mapiTxnId = java.util.UUID.randomUUID().toString()

        // 1. Insertar PENDIENTE
        runCatching {
            db.execute(
                "INSERT INTO dbo.PagosNetPay (MapiTxnId, MontoSolicit, Estatus) VALUES (?,?, 'PENDIENTE')",
                listOf(mapiTxnId, monto)
            )
        }.onFailure { return NetPayResultado(false, "ERROR", mapiTxnId, mensaje = "No se pudo registrar la transacción: ${it.message}") }

        // 2. OAuth + 3. Sale
        onProgreso("Despachando a la terminal…")
        val despacho = withContext(Dispatchers.IO) {
            runCatching {
                val token = solicitarToken(cfg)
                despacharVenta(cfg, token, monto, mapiTxnId, folioNumber, msi)
            }
        }
        if (despacho.isFailure)
            return NetPayResultado(false, "ERROR", mapiTxnId, mensaje = "Error al despachar a la terminal: ${despacho.exceptionOrNull()?.message}")

        // 4. Polling sobre PagosNetPay (cancelable)
        onProgreso("Esperando terminal… presione la tarjeta")
        return esperarResultado(cfg, mapiTxnId)
    }

    private suspend fun esperarResultado(cfg: NetPayConfig, mapiTxnId: String): NetPayResultado {
        val deadline = System.currentTimeMillis() + cfg.pollTimeoutSeconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            kotlin.coroutines.coroutineContext.ensureActive()   // permite cancelar
            val fila = runCatching {
                db.queryOne(
                    """SELECT Estatus, ResponseCode, AuthCode, OrderId, Marca, Ultimos4, TipoTarjeta, MontoCobrado, Mensaje
                       FROM dbo.PagosNetPay WHERE MapiTxnId=?""",
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
            }.getOrNull()

            if (fila != null && !fila.estatus.equals("PENDIENTE", true)) return fila
            delay(cfg.pollIntervalMs)
        }
        return NetPayResultado(false, "TIMEOUT", mapiTxnId,
            mensaje = "La terminal no respondió en ${cfg.pollTimeoutSeconds}s.")
    }

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
