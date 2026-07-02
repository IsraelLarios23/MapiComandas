package com.example.mapicomandas.data.netpay

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Servicio de respuesta ("parámetros de retorno") de NetPay EMBEBIDO en la app.
 *
 * La terminal Smart hace POST del resultado de cada operación a este servidor
 * (http://<ip-tablet>:<puerto><path>), en la misma red local. Correlacionamos por
 * traceability.mapiTxnId, resolvemos el cobro en curso al instante y respondemos el
 * acuse `{"code":"00","message":"Recibido"}` que la terminal exige. No requiere
 * desplegar ningún backend ni polling a la BD.
 *
 * Contrato (docs.netpay: "Recibiendo la respuesta"): responder HTTP 200 + ese JSON,
 * incluso al POST de prueba del primer registro (que llega sin traceability).
 */
@Singleton
class NetPayResponseServer @Inject constructor() {

    private var servidor: Servidor? = null
    @Volatile var puerto: Int = 8081
        private set

    // Cobros en espera, por mapiTxnId
    private val pendientes = ConcurrentHashMap<String, CompletableDeferred<NetPayResultado>>()

    /** Arranca el servidor en [puertoDeseado] si no está corriendo. Devuelve error o null. */
    @Synchronized
    fun asegurarIniciado(puertoDeseado: Int): String? {
        if (servidor != null && puerto == puertoDeseado) return null
        // Puerto cambió: reinicia
        detener()
        return runCatching {
            val s = Servidor(puertoDeseado)
            s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            servidor = s
            puerto = puertoDeseado
            null
        }.getOrElse { e ->
            "No se pudo abrir el puerto $puertoDeseado del receptor NetPay: ${e.message}"
        }
    }

    @Synchronized
    fun detener() {
        runCatching { servidor?.stop() }
        servidor = null
    }

    /** Registra un cobro en espera; el receptor lo completará al llegar el POST. */
    fun registrar(mapiTxnId: String): CompletableDeferred<NetPayResultado> {
        val d = CompletableDeferred<NetPayResultado>()
        pendientes[mapiTxnId] = d
        return d
    }

    fun cancelar(mapiTxnId: String) {
        pendientes.remove(mapiTxnId)
    }

    private inner class Servidor(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val ack = newFixedLengthResponse(
                Response.Status.OK, "application/json",
                """{"code":"00","message":"Recibido"}"""
            )
            try {
                if (session.method != Method.POST) return ack
                val body = leerBody(session)
                val json = runCatching { JSONObject(body) }.getOrNull() ?: return ack

                // POST de prueba del primer registro: sin traceability → solo acusar
                val trace = json.optJSONObject("traceability")
                val mapiTxnId = trace?.optString("mapiTxnId")?.takeIf { it.isNotBlank() }
                    ?: return ack

                val res = parseResultado(json, mapiTxnId)
                pendientes.remove(mapiTxnId)?.complete(res)
            } catch (t: Throwable) {
                Log.w("NetPayResponseServer", "Error procesando respuesta: ${t.message}")
            }
            // SIEMPRE acusar 200 (si no, la terminal marca fallo y se bloquea)
            return ack
        }

        private fun leerBody(session: IHTTPSession): String {
            val files = HashMap<String, String>()
            session.parseBody(files)
            // NanoHTTPD deja el cuerpo crudo en la clave "postData" para content-type JSON
            return files["postData"] ?: files.values.firstOrNull() ?: ""
        }
    }

    private fun parseResultado(json: JSONObject, mapiTxnId: String): NetPayResultado {
        fun s(k: String): String? = json.optString(k).takeIf { it.isNotBlank() }
        val responseCode = s("responseCode")
        val aprobada = responseCode == "00"
        return NetPayResultado(
            aprobada = aprobada,
            estatus = if (aprobada) "APROBADA" else "RECHAZADA",
            mapiTxnId = mapiTxnId,
            responseCode = responseCode,
            authCode = s("authCode"),
            orderId = s("orderId"),
            marca = s("cardTypeName") ?: s("bankName"),
            ultimos4 = s("cardNumber"),               // la terminal manda solo los últimos 4
            tipoTarjeta = s("cardType"),
            montoCobrado = s("amount"),
            mensaje = s("message")
        )
    }
}
