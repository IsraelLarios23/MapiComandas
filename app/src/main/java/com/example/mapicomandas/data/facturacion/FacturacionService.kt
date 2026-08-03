package com.example.mapicomandas.data.facturacion

import com.example.mapicomandas.data.ConfigService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facturación CFDI 4.0 — sin SQL. El timbrado real necesita CSD + PAC, así que la app
 * hace POST del receptor+emisor+venta a un backend de timbrado configurado
 * (ConfiguracionSistema REST_FACTURACION_URL, o CFDI_UrlPAC de respaldo), que devuelve el UUID.
 * Si no hay backend configurado, informa que está pendiente (no persiste nada localmente).
 *
 * Config (emisor, backend) se lee de la API central (GET /v1/config). Bandera:
 * CFDI_CAJA_FACTURA_HABILITADA.
 */
@Singleton
class FacturacionService @Inject constructor(
    private val config: ConfigService
) {
    suspend fun facturacionHabilitada(): Boolean =
        config.bool("CFDI_CAJA_FACTURA_HABILITADA", false)

    /** Envía la solicitud al backend de timbrado y devuelve el resultado. */
    suspend fun solicitarFactura(
        idVenta: Int, folioVenta: String, total: Double, datos: DatosFactura
    ): FacturaResultado {
        if (!facturacionHabilitada())
            return FacturaResultado(false, mensaje = "Facturación no habilitada (CFDI_CAJA_FACTURA_HABILITADA).")
        if (datos.rfc.isBlank() || datos.razonSocial.isBlank())
            return FacturaResultado(false, mensaje = "RFC y razón social son obligatorios.")

        val urlBackend = config.texto("REST_FACTURACION_URL").ifBlank { config.texto("CFDI_UrlPAC") }
        if (urlBackend.isBlank())
            return FacturaResultado(false, mensaje = "No hay backend de timbrado configurado (REST_FACTURACION_URL).")

        return withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("idVenta", idVenta); put("folio", folioVenta); put("total", total)
                    put("receptor", JSONObject().apply {
                        put("rfc", datos.rfc); put("razonSocial", datos.razonSocial)
                        put("usoCfdi", datos.usoCfdi); put("regimenFiscal", datos.regimenFiscal)
                        put("codigoPostal", datos.codigoPostal); put("email", datos.email)
                    })
                    put("emisor", JSONObject().apply {
                        put("rfc", config.texto("CFDI_RFC")); put("razonSocial", config.texto("CFDI_RazonSocial"))
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
                FacturaResultado(uuid != null, uuid = uuid,
                    mensaje = if (uuid != null) "Factura timbrada · UUID $uuid" else "Timbrado sin UUID: $texto")
            }.getOrElse { e ->
                FacturaResultado(false, mensaje = "Error al timbrar: ${e.message}")
            }
        }
    }
}
