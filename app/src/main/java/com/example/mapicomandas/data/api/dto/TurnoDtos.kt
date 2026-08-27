package com.example.mapicomandas.data.api.dto

import kotlinx.serialization.Serializable

// ═══════════════════════════════════════════════════════════════════════════
//  DTOs de turno de restaurante, propinas y meseros
//  Espejo de MapiPOS.Api/Datos/TurnoRestaurante.cs — JSON camelCase.
// ═══════════════════════════════════════════════════════════════════════════

@Serializable
data class MeseroTurnoDto(
    val idMesero: Int,
    val nombre: String = "",
    val cuentas: Int = 0,
    val total: Double = 0.0,
    val cortesias: Double = 0.0,
    val descuentos: Double = 0.0,
    val propinas: Double = 0.0
)

@Serializable
data class FormaPagoTurnoDto(val forma: String = "", val importe: Double = 0.0)

/** Vista previa del turno (antes de cerrar). */
@Serializable
data class TurnoPreviewDto(
    val desde: String = "",
    val hasta: String = "",
    val totalVentas: Double = 0.0,
    val numCuentas: Int = 0,
    val propinas: Double = 0.0,
    val porMesero: List<MeseroTurnoDto> = emptyList(),
    val porFormaPago: List<FormaPagoTurnoDto> = emptyList()
)

@Serializable
data class CierreTurnoDto(
    val idCierre: Int,
    val folio: String = "",
    val desde: String = "",
    val hasta: String = "",
    val totalVentas: Double = 0.0,
    val propinas: Double = 0.0
)

@Serializable
data class CuentaMeseroDto(
    val folio: String = "",
    val mesa: Int? = null,
    val apertura: String? = null,
    val cierre: String? = null,
    val personas: Int = 0,
    val total: Double = 0.0
)

@Serializable
data class CorteMeseroDto(
    val mesero: String = "",
    val desde: String = "",
    val cuentas: List<CuentaMeseroDto> = emptyList(),
    val totalVentas: Double = 0.0,
    val cortesias: Double = 0.0,
    val descuentos: Double = 0.0,
    val propinas: Double = 0.0
)

// ── Reparto de propinas ──────────────────────────────────────────────────────

@Serializable
data class ReglaRepartoDto(val idPuesto: Int, val puesto: String = "", val porcentaje: Double = 0.0)

@Serializable
data class RepartoLineaDto(
    val idUsuario: Int,
    val nombre: String = "",
    val puesto: String = "",
    val monto: Double = 0.0
)

@Serializable
data class RepartoPreviewDto(
    val idCierre: Int,
    val totalPropina: Double = 0.0,
    val modo: String = "",
    val lineas: List<RepartoLineaDto> = emptyList(),
    val sinRepartir: Double = 0.0,
    val yaRepartido: Boolean = false
)

@Serializable
data class RepartoAplicadoDto(
    val idReparto: Int,
    val idCierre: Int,
    val totalPropina: Double = 0.0,
    val modo: String = "",
    val lineas: List<RepartoLineaDto> = emptyList(),
    val sinRepartir: Double = 0.0
)

// ── Meseros ──────────────────────────────────────────────────────────────────

@Serializable
data class MeseroGuardadoDto(
    val idMesero: Int,
    val nombre: String = "",
    val codigo: String? = null,
    val activo: Boolean = true
)

@Serializable
data class PinValidadoDto(val valido: Boolean = false, val idMesero: Int? = null, val nombre: String? = null)
