package com.example.mapicomandas.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Impresión ESC/POS a impresora de red (TCP, normalmente puerto 9100).
 * El destino se especifica como "IP" o "IP:puerto".
 */
object EscPosPrinter {

    private val INIT = byteArrayOf(0x1B, 0x40)              // ESC @  (reset)
    private val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val CORTE = byteArrayOf(0x1D, 0x56, 0x42, 0x00) // GS V B 0 (corte parcial)
    private val PULSO_CAJON = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte()) // abre cajón

    /** Imprime las líneas de texto. Devuelve null si OK, o el mensaje de error. */
    suspend fun imprimir(
        destino: String,
        lineas: List<String>,
        abrirCajon: Boolean = true
    ): String? = withContext(Dispatchers.IO) {
        val (host, puerto) = parseDestino(destino)
        try {
            withTimeout(10_000) {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, puerto), 8_000)
                    val out: OutputStream = socket.getOutputStream()
                    out.write(INIT)
                    out.write(ALIGN_LEFT)
                    val charset = charsetCompatible()
                    lineas.forEach { linea ->
                        out.write(linea.toByteArray(charset))
                        out.write('\n'.code)
                    }
                    out.write("\n\n\n".toByteArray())
                    out.write(CORTE)
                    if (abrirCajon) out.write(PULSO_CAJON)
                    out.flush()
                }
            }
            null
        } catch (e: Exception) {
            "No se pudo imprimir en $host:$puerto — ${e.message}"
        }
    }

    private fun parseDestino(destino: String): Pair<String, Int> {
        val t = destino.trim()
        val idx = t.lastIndexOf(':')
        return if (idx > 0) {
            val host = t.substring(0, idx)
            val port = t.substring(idx + 1).toIntOrNull() ?: 9100
            host to port
        } else {
            t to 9100
        }
    }

    private fun charsetCompatible() = try {
        charset("CP437")
    } catch (e: Exception) {
        Charsets.ISO_8859_1
    }
}
