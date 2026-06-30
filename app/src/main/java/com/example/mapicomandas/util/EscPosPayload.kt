package com.example.mapicomandas.util

import java.io.ByteArrayOutputStream

/** Construye el payload ESC/POS (bytes) a partir de las líneas de texto del ticket. */
object EscPosPayload {

    private val INIT = byteArrayOf(0x1B, 0x40)               // ESC @  (reset)
    private val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val CORTE = byteArrayOf(0x1D, 0x56, 0x42, 0x00)  // GS V B 0 (corte parcial)
    private val PULSO_CAJON = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte())

    fun construir(lineas: List<String>, abrirCajon: Boolean = true): ByteArray {
        val bos = ByteArrayOutputStream()
        bos.write(INIT)
        bos.write(ALIGN_LEFT)
        val charset = try { charset("CP437") } catch (e: Exception) { Charsets.ISO_8859_1 }
        lineas.forEach { linea ->
            bos.write(linea.toByteArray(charset))
            bos.write('\n'.code)
        }
        bos.write("\n\n\n".toByteArray())
        bos.write(CORTE)
        if (abrirCajon) bos.write(PULSO_CAJON)
        return bos.toByteArray()
    }
}
