package com.example.mapicomandas.data.openpay

import com.example.mapicomandas.data.ConfigService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Resultado de generar un link de pago OpenPay. */
data class OpenPayLink(
    val ok: Boolean,
    val url: String? = null,
    val chargeId: String? = null,
    val mensaje: String
)

/**
 * Genera links de pago (payment link) OpenPay para cobrar pedidos a domicilio en línea.
 *
 * Crea un cargo pendiente con method="card", confirm=false y send_email=true; la respuesta
 * incluye payment_method.url = página de pago hospedada por OpenPay que se comparte al cliente.
 *
 * Configuración (ConfiguracionSistema):
 *  - OPENPAY_MERCHANT_ID, OPENPAY_PRIVATE_KEY (obligatorios)
 *  - OPENPAY_PRODUCCION = TRUE/1 para producción (default sandbox)
 *  - OPENPAY_REDIRECT_URL = URL de retorno tras el pago (opcional)
 */
@Singleton
class OpenPayService @Inject constructor(
    private val config: ConfigService
) {

    suspend fun estaConfigurado(): Boolean =
        config.texto("OPENPAY_MERCHANT_ID").isNotBlank() &&
        config.texto("OPENPAY_PRIVATE_KEY").isNotBlank()

    private suspend fun baseUrl(): String {
        val merchant = config.texto("OPENPAY_MERCHANT_ID")
        val host = if (config.bool("OPENPAY_PRODUCCION", false))
            "https://api.openpay.mx" else "https://sandbox-api.openpay.mx"
        return "$host/v1/$merchant"
    }

    suspend fun crearLinkPago(
        monto: Double,
        descripcion: String,
        ordenId: String,
        clienteNombre: String,
        clienteEmail: String,
        clienteTelefono: String
    ): OpenPayLink {
        if (!estaConfigurado())
            return OpenPayLink(false, mensaje = "OpenPay no está configurado (OPENPAY_MERCHANT_ID / OPENPAY_PRIVATE_KEY).")
        if (monto <= 0.0)
            return OpenPayLink(false, mensaje = "Monto inválido.")

        val privateKey = config.texto("OPENPAY_PRIVATE_KEY")
        val redirect = config.texto("OPENPAY_REDIRECT_URL")
        val base = baseUrl()

        return withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("method", "card")
                    put("amount", Math.round(monto * 100.0) / 100.0)
                    put("currency", "MXN")
                    put("description", descripcion)
                    put("order_id", ordenId)
                    put("send_email", clienteEmail.isNotBlank())
                    put("confirm", false)
                    if (redirect.isNotBlank()) put("redirect_url", redirect)
                    val customer = JSONObject().apply {
                        put("name", clienteNombre.ifBlank { "Cliente" })
                        if (clienteEmail.isNotBlank()) put("email", clienteEmail)
                        if (clienteTelefono.isNotBlank()) put("phone_number", clienteTelefono)
                    }
                    put("customer", customer)
                }.toString()

                // Auth Basic: private key como usuario, password vacío
                val auth = android.util.Base64.encodeToString(
                    "$privateKey:".toByteArray(), android.util.Base64.NO_WRAP
                )
                val conn = (URL("$base/charges").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 30_000; readTimeout = 60_000
                    setRequestProperty("Authorization", "Basic $auth")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                val texto = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) {
                    val msg = runCatching { JSONObject(texto).optString("description") }.getOrNull()
                    throw IllegalStateException(msg?.ifBlank { "HTTP $code" } ?: "HTTP $code")
                }

                val json = JSONObject(texto)
                val chargeId = json.optString("id")
                val url = json.optJSONObject("payment_method")?.optString("url")
                if (url.isNullOrBlank())
                    OpenPayLink(false, chargeId = chargeId, mensaje = "OpenPay no devolvió URL de pago.")
                else
                    OpenPayLink(true, url = url, chargeId = chargeId, mensaje = "Link de pago generado.")
            }.getOrElse { e ->
                OpenPayLink(false, mensaje = "Error OpenPay: ${e.message}")
            }
        }
    }
}
