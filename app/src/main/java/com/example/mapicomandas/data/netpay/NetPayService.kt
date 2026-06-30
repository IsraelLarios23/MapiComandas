package com.example.mapicomandas.data.netpay

import com.example.mapicomandas.data.ConfigService
import com.example.mapicomandas.data.db.JdbcDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
            baseUrl = config.texto("NetPayBaseUrl", "https://suite.netpay.com.mx"),
            oauthPath = config.texto("NetPayOAuthPath", "/gateway/oauth-service/oauth/token"),
            salePath = config.texto("NetPaySalePath", "/gateway/integration-service/transactions/sale"),
            authString = config.texto("NetPayAuthString"),
            username = config.texto("NetPayUsername"),
            password = config.texto("NetPayPassword"),
            serialNumber = config.texto("NetPaySerialNumber"),
            storeId = config.texto("NetPayStoreId")
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

    /** Cobra [monto] con la terminal. Bloquea hasta resultado o timeout. */
    suspend fun cobrar(monto: Double, folioNumber: String? = null): NetPayResultado {
        val cfg = obtenerConfig()
        if (!cfg.estaConfigurado)
            return NetPayResultado(false, "ERROR", "", mensaje = "NetPay no está configurado (ConfiguracionSistema).")

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
        val despacho = withContext(Dispatchers.IO) {
            runCatching {
                val token = solicitarToken(cfg)
                despacharVenta(cfg, token, monto, mapiTxnId, folioNumber)
            }
        }
        if (despacho.isFailure)
            return NetPayResultado(false, "ERROR", mapiTxnId, mensaje = "Error al despachar a la terminal: ${despacho.exceptionOrNull()?.message}")

        // 4. Polling sobre PagosNetPay
        return esperarResultado(cfg, mapiTxnId)
    }

    private suspend fun esperarResultado(cfg: NetPayConfig, mapiTxnId: String): NetPayResultado {
        val deadline = System.currentTimeMillis() + cfg.pollTimeoutSeconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            val fila = runCatching {
                db.queryOne(
                    """SELECT Estatus, AuthCode, OrderId, MontoCobrado, Mensaje
                       FROM dbo.PagosNetPay WHERE MapiTxnId=?""",
                    listOf(mapiTxnId)
                ) { rs ->
                    NetPayResultado(
                        aprobada = rs.getString("Estatus").equals("APROBADA", true),
                        estatus = rs.getString("Estatus") ?: "PENDIENTE",
                        mapiTxnId = mapiTxnId,
                        authCode = rs.getString("AuthCode"),
                        orderId = rs.getString("OrderId"),
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
        cfg: NetPayConfig, token: String, monto: Double, mapiTxnId: String, folioNumber: String?
    ) {
        val url = URL(cfg.baseUrl.trimEnd('/') + cfg.salePath)
        val body = JSONObject().apply {
            put("serialNumber", cfg.serialNumber)
            put("amount", Math.round(monto * 100.0) / 100.0)
            put("storeId", cfg.storeId)
            put("traceability", JSONObject().put("mapiTxnId", mapiTxnId))
            if (!folioNumber.isNullOrBlank()) put("folioNumber", folioNumber)
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
