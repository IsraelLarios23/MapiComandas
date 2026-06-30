package com.example.mapicomandas.data.netpay

/** Configuración del cliente Smart de NetPay (leída de ConfiguracionSistema). */
data class NetPayConfig(
    val baseUrl: String = "https://api-154.api-netpay.com",
    val oauthPath: String = "/gateway/oauth-service/oauth/token",
    val salePath: String = "/gateway/integration-service/transactions/sale",
    val authString: String = "",   // Basic <base64> sin el prefijo "Basic "
    val username: String = "",
    val password: String = "",
    val serialNumber: String = "",
    val storeId: String = "",
    val pollIntervalMs: Long = 1500,
    val pollTimeoutSeconds: Int = 90
) {
    val estaConfigurado: Boolean
        get() = baseUrl.isNotBlank() && serialNumber.isNotBlank() &&
                storeId.isNotBlank() && username.isNotBlank() && authString.isNotBlank()
}

/** Resultado del cobro con terminal NetPay. */
data class NetPayResultado(
    val aprobada: Boolean,
    val estatus: String,            // PENDIENTE / APROBADA / RECHAZADA / TIMEOUT / ERROR / CANCELADA
    val mapiTxnId: String,
    val responseCode: String? = null,
    val authCode: String? = null,
    val orderId: String? = null,
    val marca: String? = null,       // marca de la tarjeta (VISA, MC…)
    val ultimos4: String? = null,
    val tipoTarjeta: String? = null, // CR / DB
    val montoCobrado: String? = null,
    val mensaje: String? = null
)
