package com.example.mapicomandas.data.facturacion

import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.data.ConfigService
import com.example.mapicomandas.data.db.JdbcDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facturación CFDI 4.0 — SCAFFOLD.
 *
 * El timbrado real de un CFDI requiere los certificados CSD (.cer/.key) y un PAC,
 * lo cual no es viable ejecutar en el dispositivo. Este servicio:
 *  1. Registra la solicitud de factura (receptor + venta) en dbo.SolicitudesFacturaApp.
 *  2. Si hay un backend de timbrado configurado (ConfiguracionSistema REST_FACTURACION_URL,
 *     o CFDI_UrlPAC como respaldo), le hace POST del receptor+venta para que timbre y
 *     devuelva el UUID.
 *  3. Si no hay backend, deja la solicitud PENDIENTE para que MapiPOS la timbre.
 *
 * Se activa con la bandera CFDI_CAJA_FACTURA_HABILITADA (misma que MapiPOS).
 */
@Singleton
class FacturacionService @Inject constructor(
    private val db: JdbcDataSource,
    private val config: ConfigService,
    private val session: SessionManager
) {

    suspend fun facturacionHabilitada(): Boolean =
        config.bool("CFDI_CAJA_FACTURA_HABILITADA", false)

    private suspend fun asegurarTabla() {
        runCatching {
            db.execute(
                """IF OBJECT_ID('dbo.SolicitudesFacturaApp','U') IS NULL
                   CREATE TABLE dbo.SolicitudesFacturaApp (
                     IdSolicitud   INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_SolFactApp PRIMARY KEY,
                     IdVenta       INT NOT NULL,
                     Rfc           NVARCHAR(20) NOT NULL,
                     RazonSocial   NVARCHAR(200) NOT NULL,
                     UsoCfdi       NVARCHAR(5) NOT NULL,
                     RegimenFiscal NVARCHAR(5) NOT NULL,
                     CodigoPostal  NVARCHAR(10) NULL,
                     Email         NVARCHAR(150) NULL,
                     Total         DECIMAL(18,2) NOT NULL,
                     Estatus       NVARCHAR(12) NOT NULL CONSTRAINT DF_SolFactApp_Est DEFAULT('PENDIENTE'),
                     Uuid          NVARCHAR(40) NULL,
                     IdTienda      INT NULL,
                     IdUsuario     INT NULL,
                     FechaAlta     DATETIME2 NOT NULL CONSTRAINT DF_SolFactApp_Fec DEFAULT(SYSUTCDATETIME())
                   )""",
                emptyList()
            )
        }
    }

    /** Registra la solicitud y (si hay backend) intenta timbrar. */
    suspend fun solicitarFactura(
        idVenta: Int, folioVenta: String, total: Double, datos: DatosFactura
    ): FacturaResultado {
        if (!facturacionHabilitada())
            return FacturaResultado(false, mensaje = "Facturación no habilitada (CFDI_CAJA_FACTURA_HABILITADA).")
        if (datos.rfc.isBlank() || datos.razonSocial.isBlank())
            return FacturaResultado(false, mensaje = "RFC y razón social son obligatorios.")

        asegurarTabla()

        // 1. Registrar solicitud PENDIENTE
        val idSolicitud = runCatching {
            db.executeAndGetId(
                """INSERT INTO dbo.SolicitudesFacturaApp
                   (IdVenta, Rfc, RazonSocial, UsoCfdi, RegimenFiscal, CodigoPostal, Email, Total, IdTienda, IdUsuario)
                   VALUES (?,?,?,?,?,?,?,?,?,?)""",
                listOf(idVenta, datos.rfc, datos.razonSocial, datos.usoCfdi, datos.regimenFiscal,
                       datos.codigoPostal, datos.email, total, session.idTienda, session.idUsuario)
            )
        }.getOrNull() ?: 0

        // 2. ¿Hay backend de timbrado?
        val urlBackend = config.texto("REST_FACTURACION_URL").ifBlank { config.texto("CFDI_UrlPAC") }
        if (urlBackend.isBlank()) {
            return FacturaResultado(
                false, registradaLocal = idSolicitud > 0,
                mensaje = "Solicitud registrada (folio interno $idSolicitud). Pendiente de timbrar en MapiPOS " +
                          "(configura REST_FACTURACION_URL para timbrado automático)."
            )
        }

        // 3. POST al backend de timbrado
        return withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("idVenta", idVenta)
                    put("folio", folioVenta)
                    put("total", total)
                    put("receptor", JSONObject().apply {
                        put("rfc", datos.rfc)
                        put("razonSocial", datos.razonSocial)
                        put("usoCfdi", datos.usoCfdi)
                        put("regimenFiscal", datos.regimenFiscal)
                        put("codigoPostal", datos.codigoPostal)
                        put("email", datos.email)
                    })
                    put("emisor", JSONObject().apply {
                        put("rfc", config.texto("CFDI_RFC"))
                        put("razonSocial", config.texto("CFDI_RazonSocial"))
                        put("regimenFiscal", config.texto("CFDI_RegimenFiscal"))
                        put("lugarExpedicion", config.texto("CFDI_LugarExpedicion"))
                    })
                }.toString()

                val conn = (URL(urlBackend).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 30_000; readTimeout = 60_000
                    setRequestProperty("Content-Type", "application/json"); doOutput = true
                }
                conn.outputStream.use { it.write(payload.toByteArray()) }
                val code = conn.responseCode
                val texto = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) throw IllegalStateException("HTTP $code: $texto")

                val uuid = runCatching { JSONObject(texto).optString("uuid") }.getOrNull()?.ifBlank { null }
                if (uuid != null && idSolicitud > 0) {
                    runCatching {
                        db.execute(
                            "UPDATE dbo.SolicitudesFacturaApp SET Estatus='TIMBRADA', Uuid=? WHERE IdSolicitud=?",
                            listOf(uuid, idSolicitud)
                        )
                    }
                }
                FacturaResultado(uuid != null, uuid = uuid, registradaLocal = idSolicitud > 0,
                    mensaje = if (uuid != null) "Factura timbrada · UUID $uuid" else "Timbrado sin UUID: $texto")
            }.getOrElse { e ->
                FacturaResultado(false, registradaLocal = idSolicitud > 0,
                    mensaje = "Solicitud registrada; error al timbrar: ${e.message}")
            }
        }
    }
}
