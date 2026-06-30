package com.example.mapicomandas.util

import java.util.Locale

/** Datos para construir el ticket de venta (réplica de MapiPOS TicketPrintService). */
data class TicketRenglon(val cantidad: Double, val descripcion: String, val importe: Double)

/** Pago en el ticket; [referencia] = autorización de la terminal (si aplica). */
data class TicketPago(val nombre: String, val importe: Double, val referencia: String = "")

data class TicketData(
    val empresa: String = "",
    val header: String = "",
    val footer: String = "",
    val folio: String,
    val fecha: String,
    val caja: String,
    val cajero: String,
    val vendedor: String = "",
    val renglones: List<TicketRenglon>,
    val subtotal: Double,
    val descuento: Double,
    val impuesto: Double,
    val ieps: Double = 0.0,
    val total: Double,
    val pagado: Double,
    val cambio: Double,
    val formaPago: String,
    val pagos: List<TicketPago> = emptyList(),
    val observaciones: String = "",
    val desglosaIva: Boolean = true
)

/**
 * Construye las líneas de texto del ticket con el MISMO formato que MapiPOS
 * (TicketPrintService.ConstruirLineasTicketTexto), ancho 40 columnas (80 mm).
 */
object TicketFormatter {

    private const val ANCHO = 40

    fun construir(d: TicketData): List<String> {
        val l = mutableListOf<String>()

        fun centrar(s: String): String {
            val t = s.trim()
            if (t.length >= ANCHO) return t.substring(0, ANCHO)
            val pad = (ANCHO - t.length) / 2
            return " ".repeat(pad) + t
        }
        fun dosColumnas(izqIn: String, der: String): String {
            var izq = izqIn
            var espacio = ANCHO - izq.length - der.length
            if (espacio < 1) {
                izq = izq.substring(0, maxOf(0, ANCHO - der.length - 1)); espacio = 1
            }
            return izq + " ".repeat(espacio) + der
        }
        fun money(v: Double) = "$" + String.format(Locale.US, "%,.2f", v)
        fun linea() = "-".repeat(ANCHO)

        // ── Encabezado ──
        if (d.empresa.isNotBlank()) l.add(centrar(d.empresa))
        d.header.lines().filter { it.isNotBlank() }.forEach { l.add(centrar(it)) }
        l.add(linea())

        // ── Datos del documento ──
        l.add(dosColumnas("Folio: ${d.folio}", d.fecha))
        l.add("Caja: ${d.caja}   Cajero: ${d.cajero}")
        if (d.vendedor.isNotBlank()) l.add("Vendedor: ${d.vendedor}")
        l.add(linea())

        // ── Renglones ──
        d.renglones.forEach { r ->
            val desc = r.descripcion.ifBlank { "(artículo)" }
            l.add(if (desc.length > ANCHO) desc.substring(0, ANCHO) else desc)
            val cant = String.format(Locale.US, "%.3f", r.cantidad).trimEnd('0').trimEnd('.')
            l.add(dosColumnas("  $cant x", money(r.importe)))
        }
        l.add(linea())

        // ── Totales ──
        if (d.desglosaIva) {
            l.add(dosColumnas("Subtotal:", money(d.subtotal)))
            if (d.descuento > 0) l.add(dosColumnas("Descuento:", "-" + money(d.descuento)))
            if (d.ieps > 0) {
                l.add(dosColumnas("IVA:", money(d.impuesto)))
                l.add(dosColumnas("IEPS:", money(d.ieps)))
            } else {
                l.add(dosColumnas("Impuesto:", money(d.impuesto)))
            }
        } else if (d.descuento > 0) {
            l.add(dosColumnas("Descuento:", "-" + money(d.descuento)))
        }
        l.add(dosColumnas("TOTAL:", money(d.total)))
        // Pagos: una línea por forma + autorización de la terminal (si hay)
        if (d.pagos.isNotEmpty()) {
            d.pagos.forEach { p ->
                l.add(dosColumnas("Pago (${p.nombre}):", money(p.importe)))
                if (p.referencia.isNotBlank()) l.add("  Autorización: ${p.referencia}")
            }
        } else {
            l.add(dosColumnas("Pago (${d.formaPago}):", money(d.pagado)))
        }
        if (d.cambio > 0) l.add(dosColumnas("Cambio:", money(d.cambio)))

        // ── Observaciones + pie ──
        if (d.observaciones.isNotBlank()) {
            l.add(linea())
            d.observaciones.lines().forEach { l.add(it) }
        }
        if (d.footer.isNotBlank()) {
            l.add(linea())
            d.footer.lines().filter { it.isNotBlank() }.forEach { l.add(centrar(it)) }
        }
        return l
    }
}
