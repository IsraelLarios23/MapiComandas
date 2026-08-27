package com.example.mapicomandas.data.api.dto

import kotlinx.serialization.Serializable

// ═══════════════════════════════════════════════════════════════════════════
//  DTOs de catálogos del restaurante: mermas, monederos, habitaciones y
//  comisiones. Espejo de MapiPOS.Api/Datos/RestauranteCatalogos.cs.
// ═══════════════════════════════════════════════════════════════════════════

// ── Mermas ──────────────────────────────────────────────────────────────────

@Serializable
data class CausaMermaDto(val idCausa: Int, val nombre: String = "")

@Serializable
data class MermaRegistradaDto(val folio: String = "", val existenciaNueva: Double = 0.0)

@Serializable
data class MermaReporteDto(
    val fecha: String = "",
    val articulo: String = "",
    val cantidad: Double = 0.0,
    val causa: String = "",
    val costoEstimado: Double = 0.0,
    val usuario: String = ""
)

// ── Monederos ───────────────────────────────────────────────────────────────

@Serializable
data class MonederoDto(
    val idMonedero: Int,
    val codigo: String = "",
    val saldo: Double = 0.0,
    val activo: Boolean = true,
    val cliente: String? = null
)

@Serializable
data class MonederoMovidoDto(
    val idMonedero: Int,
    val concepto: String = "",
    val saldoAnterior: Double = 0.0,
    val saldoNuevo: Double = 0.0
)

// ── Habitaciones / estancias ────────────────────────────────────────────────

@Serializable
data class HabitacionDto(
    val idHabitacion: Int,
    val numero: String = "",
    val descripcion: String = "",
    val activo: Boolean = true
)

@Serializable
data class EstanciaAbiertaDto(
    val idEstancia: Int,
    val idHabitacion: Int,
    val numero: String = "",
    val idCliente: Int = 0,
    val nombreHuesped: String = "",
    val fechaEntrada: String = "",
    val saldoActual: Double = 0.0
)

@Serializable
data class CheckOutDto(val idEstancia: Int, val saldoPendiente: Double = 0.0)

// ── Comisiones ──────────────────────────────────────────────────────────────

/** ambito: 1=Global 2=Familia 3=Categoría 4=Artículo · baseCalculo: 1=% importe 2=% utilidad 3=monto/unidad. */
@Serializable
data class ReglaComisionDto(
    val idReglaComision: Int,
    val nombre: String = "",
    val ambito: Int = 1,
    val idReferencia: Int = 0,
    val referencia: String? = null,
    val baseCalculo: Int = 1,
    val valor: Double = 0.0,
    val idMesero: Int? = null,
    val desde: String? = null,
    val hasta: String? = null,
    val activo: Boolean = true
)

@Serializable
data class ComisionMeseroDto(
    val idMesero: Int,
    val nombre: String = "",
    val documentos: Int = 0,
    val base: Double = 0.0,
    val comision: Double = 0.0
)
