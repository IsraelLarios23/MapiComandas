package com.example.mapicomandas.data

import com.example.mapicomandas.data.repository.RestauranteRepository
import com.example.mapicomandas.util.KitchenEscPos
import com.example.mapicomandas.util.PrinterService
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imprime las comandas en los puntos de impresión configurados (cocina, barra, …),
 * ruteando por categoría y expandiendo kits — réplica de ComandaPrintService de MapiPOS.
 */
@Singleton
class ImpresionCocinaService @Inject constructor(
    private val repo: RestauranteRepository,
    private val printer: PrinterService
) {
    /**
     * Rutea e imprime. Devuelve un resumen legible por línea (✓/✗ por punto).
     * [soloRecienEnviadas] = solo lo recién enviado a cocina; [todasLasLineas] = toda la comanda (al cobrar).
     */
    suspend fun imprimir(
        idComanda: Int,
        soloRecienEnviadas: Boolean = true,
        todasLasLineas: Boolean = false
    ): List<String> {
        val tickets = repo.construirTicketsCocina(idComanda, soloRecienEnviadas, todasLasLineas)
        if (tickets.puntos.isEmpty())
            return listOf("Sin puntos con categorías asignadas para estas líneas.")

        val hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val resumen = mutableListOf<String>()
        tickets.puntos.forEach { p ->
            if (p.impresora.isBlank()) {
                resumen.add("✗ ${p.nombre}: sin impresora configurada")
                return@forEach
            }
            val bytes = KitchenEscPos.construir(tickets.cabecera, p, hora)
            var okAlguna = false
            var ultimoError: String? = null
            repeat(maxOf(1, p.copias)) {
                val err = printer.enviarBytes(p.impresora, bytes)
                if (err == null) okAlguna = true else ultimoError = err
            }
            resumen.add(
                if (okAlguna) "✓ ${p.nombre} (${p.impresora}) — ${p.lineas.size} línea(s)"
                else "✗ ${p.nombre} (${p.impresora}): $ultimoError"
            )
        }
        return resumen
    }

    /** Envía un ticket de prueba al punto. Devuelve null si OK. */
    suspend fun probar(impresora: String, nombre: String, ancho: Int): String? {
        if (impresora.isBlank()) return "Sin impresora configurada"
        return printer.enviarBytes(impresora, KitchenEscPos.prueba(nombre, ancho))
    }
}
