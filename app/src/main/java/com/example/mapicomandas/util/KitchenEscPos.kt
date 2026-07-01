package com.example.mapicomandas.util

import com.example.mapicomandas.data.model.CabeceraCocina
import com.example.mapicomandas.data.model.LineaCocina
import com.example.mapicomandas.data.model.PuntoImpresionTicket
import com.example.mapicomandas.data.model.TipoModificador
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Construye el ticket de cocina en ESC/POS con el MISMO formato que MapiPOS
 * (ComandaPrintService.ConstruirTicket): encabezado del punto en doble alto,
 * mesa/mesero/folio, cantidad en negrita doble alto, referencia de kit,
 * modificadores (− SIN / + extra / *) y notas. Corte parcial al final.
 */
object KitchenEscPos {

    private const val ESC = 0x1B.toByte()
    private const val GS = 0x1D.toByte()

    fun construir(cab: CabeceraCocina, punto: PuntoImpresionTicket, horaHHmm: String): ByteArray {
        val w = punto.ancho.coerceIn(24, 48)
        val out = ByteArrayOutputStream()
        val cp437 = try { charset("CP437") } catch (e: Exception) { Charsets.ISO_8859_1 }

        fun text(s: String) = out.write(s.toByteArray(cp437))
        fun line(s: String = "") { text(s); out.write('\n'.code) }
        fun raw(vararg b: Byte) = out.write(b)

        // Reset + codepage 437
        raw(ESC, '@'.code.toByte())
        raw(ESC, 't'.code.toByte(), 0)

        // Encabezado centrado, doble alto/ancho
        raw(ESC, 'a'.code.toByte(), 1)              // center
        raw(GS, '!'.code.toByte(), 0x11)            // doble W+H
        line(punto.nombre.uppercase(Locale.getDefault()))
        raw(GS, '!'.code.toByte(), 0x00)            // normal
        line("=".repeat(w))
        raw(ESC, 'a'.code.toByte(), 0)              // left

        // Mesa / mesero / folio / hora
        line(fila("Mesa ${cab.mesa}", "Mesero: ${cab.mesero}", w))
        line(fila("Folio: ${cab.folio}", horaHHmm, w))
        cab.numPersonas?.let { line("Personas: $it") }
        line("-".repeat(w))

        // Líneas
        punto.lineas.forEach { l ->
            raw(ESC, 'E'.code.toByte(), 1)          // bold on
            val cant = String.format(Locale.US, "%.2f", l.cantidad).trimEnd('0').trimEnd('.')
            line(trunc("$cant  ${l.articulo}".uppercase(Locale.getDefault()), w))
            raw(ESC, 'E'.code.toByte(), 0)          // bold off

            if (l.kitRef.isNotBlank()) line(trunc("   (Kit: ${l.kitRef})", w))

            l.modificadores.forEach { m ->
                val prefix = when (m.tipo) {
                    TipoModificador.QUITA -> "   - SIN "
                    TipoModificador.AGREGA_CON_COSTO -> "   + "
                    else -> "   * "
                }
                val suf = if (m.tipo == TipoModificador.AGREGA_CON_COSTO)
                    " (+${String.format(Locale.US, "%.2f", m.precioExtra)})" else ""
                line(trunc(prefix + m.nombre + suf, w))
            }
            if (l.notas.isNotBlank()) line(trunc("   ! ${l.notas}", w))
        }

        line("=".repeat(w))
        line(); line(); line()
        raw(GS, 'V'.code.toByte(), 1)               // corte parcial
        return out.toByteArray()
    }

    /** Ticket de prueba para un punto. */
    fun prueba(nombre: String, ancho: Int): ByteArray {
        val w = ancho.coerceIn(24, 48)
        val out = ByteArrayOutputStream()
        val cp437 = try { charset("CP437") } catch (e: Exception) { Charsets.ISO_8859_1 }
        out.write(byteArrayOf(ESC, '@'.code.toByte()))
        out.write(byteArrayOf(ESC, 'a'.code.toByte(), 1))
        out.write(byteArrayOf(GS, '!'.code.toByte(), 0x11))
        out.write(("PRUEBA\n").toByteArray(cp437))
        out.write(byteArrayOf(GS, '!'.code.toByte(), 0x00))
        out.write((nombre + "\n").toByteArray(cp437))
        out.write(("=".repeat(w) + "\n").toByteArray(cp437))
        out.write(("Impresion OK\n\n\n").toByteArray(cp437))
        out.write(byteArrayOf(GS, 'V'.code.toByte(), 1))
        return out.toByteArray()
    }

    private fun fila(l: String, r: String, w: Int): String {
        if (l.length + r.length + 1 > w) return trunc("$l $r", w)
        return l + " ".repeat(w - l.length - r.length) + r
    }

    private fun trunc(s: String, w: Int): String = if (s.length <= w) s else s.substring(0, w)
}
