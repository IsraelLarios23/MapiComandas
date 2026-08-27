package com.example.mapicomandas.data.api.dto

import kotlinx.serialization.Serializable

// ═══════════════════════════════════════════════════════════════════════════
//  DTOs de operación del restaurante: tiempo de mesa, plataformas delivery,
//  menú por caja y disponibilidad de porciones.
//  Espejo de MapiPOS.Api/Datos/RestauranteOperacion.cs — JSON camelCase.
// ═══════════════════════════════════════════════════════════════════════════

@Serializable
data class TipoMesaDto(
    val idTipoMesa: Int,
    val nombre: String = "",
    val cobraTiempo: Boolean = false,
    val tarifaFraccion: Double = 0.0,
    val minutosFraccion: Int = 60,
    val minutosMinimo: Int = 0,
    val toleranciaMinutos: Int = 0,
    val minutosMaximo: Int = 0,
    val modoRedondeo: Int = 1,
    val idArticuloServicio: Int? = null,
    val color: String? = null,
    val activo: Boolean = true,
    val mesas: Int = 0
)

/** Reloj recién abierto (la hora la puso el SERVIDOR). */
@Serializable
data class PeriodoTiempoDto(
    val idComandaTiempo: Int,
    val idComanda: Int,
    val idMesa: Int = 0,
    val nombreTipo: String = "",
    val fechaInicio: String = "",
    val tarifaFraccion: Double = 0.0,
    val minutosFraccion: Int = 60
)

/** Resultado de detener: detenido=false = no había reloj (idempotente). */
@Serializable
data class CobroTiempoDto(
    val idComandaTiempo: Int = 0,
    val idComanda: Int = 0,
    val detenido: Boolean = false,
    val minutosTranscurridos: Int = 0,
    val minutosCobrados: Int = 0,
    val fracciones: Double = 0.0,
    val importe: Double = 0.0,
    val aplicoMinimo: Boolean = false,
    val aplicoTope: Boolean = false,
    val motivoSinCobro: String = "",
    val idDetalle: Int? = null
)

/** Un reloj corriendo (para el plano de mesas / la comanda). */
@Serializable
data class RelojActivoDto(
    val idComanda: Int,
    val idMesa: Int? = null,
    val inicio: String = "",
    val minutos: Int = 0
)

@Serializable
data class PlataformaDto(
    val idPlataforma: Int,
    val nombre: String = "",
    val comisionPct: Double = 0.0,
    val idListaPrecio: Int? = null,
    val activo: Boolean = true
)

@Serializable
data class ComandaPlataformaDto(val idComanda: Int, val folio: String = "")

/** sinColumna = campos que esta BD no tiene (no quedaron guardados). */
@Serializable
data class DomicilioActualizadoDto(
    val idComanda: Int,
    val actualizados: List<String> = emptyList(),
    val sinColumna: List<String> = emptyList()
)

/** Menú táctil de la caja. Listas VACÍAS = sin restricción (catálogo completo). */
@Serializable
data class MenuCajaDto(
    val categorias: List<CategoriaCajaDto> = emptyList(),
    val articulos: List<ArticuloCajaDto> = emptyList()
)

@Serializable
data class CategoriaCajaDto(val idCategoria: Int, val nombre: String = "", val orden: Int = 0)

@Serializable
data class ArticuloCajaDto(val idArticulo: Int, val idCategoria: Int? = null, val orden: Int = 0)

/** Renglón del tablero "¿cuántas alcanzan?". Estado: Disponible/Por agotarse/Agotado/Sin receta. */
@Serializable
data class DisponibilidadDto(
    val idArticulo: Int,
    val nombre: String = "",
    val categoria: String = "",
    val porciones: Int = 0,
    val limitante: String = "",
    val estado: String = "",
    val costoPorcion: Double = 0.0
)
