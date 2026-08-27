package com.example.mapicomandas.data.api.dto

import kotlinx.serialization.Serializable

// ═══════════════════════════════════════════════════════════════════════════
//  DTOs de ajustes de comanda (cortesías/descuentos/correcciones/mesas)
//  Espejo de MapiPOS.Api/Datos/ComandasAjustes.cs — JSON camelCase.
// ═══════════════════════════════════════════════════════════════════════════

/** Motivo del catálogo. Tipo: 1 = Cortesía, 2 = Descuento, 3 = Cancelación. */
@Serializable
data class MotivoAjusteDto(
    val idMotivo: Int,
    val nombre: String = "",
    val tipo: Int = 2,
    val porcentajeSugerido: Double = 0.0,
    val requiereAutorizacion: Boolean = false,
    val orden: Int = 0,
    val activo: Boolean = true
)

/** Resultado de operar una partida, con totales recalculados. [aviso] se muestra al cajero. */
@Serializable
data class LineaAjustadaDto(
    val idDetalle: Int,
    val idComanda: Int,
    val cantidad: Double = 0.0,
    val precioUnitario: Double = 0.0,
    val descuento: Double = 0.0,
    val totalLinea: Double = 0.0,
    val importeNoCobrado: Double = 0.0,
    val totalComanda: Double = 0.0,
    val aviso: String = ""
)

/** Vista previa del descuento a la cuenta. Impedimentos vacío = aplicable. */
@Serializable
data class DescuentoCuentaPreviewDto(
    val porcentaje: Double = 0.0,
    val importeDescuento: Double = 0.0,
    val totalNuevo: Double = 0.0,
    val impedimentos: List<String> = emptyList()
)

@Serializable
data class PartidaDevueltaDto(val idDetalle: Int, val idComanda: Int, val totalComanda: Double = 0.0)

@Serializable
data class PartidaDivididaDto(
    val idDetalle: Int,
    val idComanda: Int,
    val partes: Int = 0,
    val idsNuevos: List<Int> = emptyList(),
    val totalComanda: Double = 0.0
)

@Serializable
data class PartidasTransferidasDto(
    val idComandaOrigen: Int,
    val idComandaDestino: Int,
    val folioDestino: String = "",
    val partidasMovidas: Int = 0,
    val totalOrigen: Double = 0.0,
    val totalDestino: Double = 0.0
)

@Serializable
data class MesaStatusDto(val idMesa: Int, val status: Int, val idComanda: Int? = null)

@Serializable
data class MesasUnidasDto(
    val idComandaDestino: Int,
    val folioDestino: String = "",
    val partidasMovidas: Int = 0,
    val folioAbsorbido: String = "",
    val importeAbsorbido: Double = 0.0,
    val mesasAgrupadas: Boolean = false,
    val totalDestino: Double = 0.0
)

@Serializable
data class SepararMesaDto(val enGrupo: Boolean = false, val liberadas: Int = 0)

/** Cuenta pendiente de cobrar (módulo "Pagos en caja"). */
@Serializable
data class PendientePagoDto(
    val idComanda: Int,
    val folio: String = "",
    val mesa: Int? = null,
    val mesero: String? = null,
    val total: Double = 0.0,
    val status: Int = 0,
    val fechaApertura: String? = null
)

/** Asiento de la bitácora de acciones (mermas/cortesías/devoluciones). */
@Serializable
data class AccionComandaDto(
    val fecha: String? = null,
    val accion: String = "",
    val folio: String = "",
    val articulo: String? = null,
    val cantidad: Double = 0.0,
    val importe: Double = 0.0,
    val motivo: String? = null,
    val usuario: String? = null
)
