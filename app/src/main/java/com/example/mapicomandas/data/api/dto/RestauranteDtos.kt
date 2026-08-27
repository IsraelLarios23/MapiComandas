package com.example.mapicomandas.data.api.dto

import kotlinx.serialization.Serializable

/*
 * DTOs de los endpoints de restaurante de la API central (camelCase, verificados contra
 * MapiPOS.Api rama togo-integracion). Los enums viajan como ENTEROS (const int), salvo:
 * caja movimientos.tipo = "Ingreso"/"Retiro", y cocina listo/entregado.status = string.
 * Montos = Double; nulos con default para tolerar campos ausentes.
 */

// ── Mesas / meseros ──────────────────────────────────────────────────────────
@Serializable
data class MesaDto(
    val idMesa: Int = 0,
    val numero: Int = 0,
    val zona: String? = null,
    val capacidad: Int = 0,
    val status: Int = 1,
    val idComanda: Int? = null,
    val folio: String? = null,
    val fechaApertura: String? = null,
    val importeCuenta: Double = 0.0,
    // Layout del editor del POS (el plano se dibuja igual que el desktop)
    val posX: Int = 0,
    val posY: Int = 0,
    val ancho: Int = 0,
    val alto: Int = 0,
    val forma: Int = 1,
    val color: String? = null,
    val idGrupoMesa: Int? = null,
    val reservasHoy: Int = 0
)

@Serializable
data class MeseroDto(
    val idMesero: Int = 0,
    val nombre: String = "",
    val codigo: String? = null,
    val activo: Boolean = true
)

// ── Comanda ──────────────────────────────────────────────────────────────────
@Serializable
data class ComandaDto(
    val idComanda: Int = 0,
    val folio: String = "",
    val idMesa: Int? = null,
    val idMesero: Int? = null,
    val numPersonas: Int = 1,
    val status: Int = 1,
    val subtotal: Double = 0.0,
    val descuento: Double = 0.0,
    val iva: Double = 0.0,
    val total: Double = 0.0,
    val lineas: List<LineaComandaDto> = emptyList(),
    val tipoServicio: Int = 1,
    val observaciones: String? = null,
    val fechaApertura: String? = null,
    val nombreCliente: String? = null,
    val telefonoCliente: String? = null,
    val direccionEntrega: String? = null,
    val cargoEntrega: Double = 0.0,
    val statusEntrega: Int = 0
)

@Serializable
data class LineaComandaDto(
    val idDetalle: Int = 0,
    val idArticulo: Int = 0,
    val linea: Int = 0,
    val nombre: String = "",
    val cantidad: Double = 0.0,
    val precioUnitario: Double = 0.0,
    val total: Double = 0.0,
    val status: Int = 1,
    val notas: String? = null,
    val numLugar: Int = 0
)

// ── Cocina / KDS ─────────────────────────────────────────────────────────────
@Serializable
data class PlatilloKdsDto(
    val idDetalle: Int = 0,
    val idComanda: Int = 0,
    val folio: String = "",
    val mesa: Int? = null,
    val nombre: String = "",
    val cantidad: Double = 0.0,
    val notas: String? = null,
    val status: Int = 1,
    val minutosTranscurridos: Int = 0,
    val kitRef: String? = null
)

@Serializable
data class TicketCocinaDto(
    val idPuntoImpresion: Int = 0,
    val punto: String = "",
    val impresora: String? = null,
    val ancho: Int = 48,
    val copias: Int = 1,
    val folio: String = "",
    val mesa: Int? = null,
    val mesero: String? = null,
    val lineas: List<LineaCocinaDto> = emptyList()
)

@Serializable
data class LineaCocinaDto(
    val cantidad: Double = 0.0,
    val nombre: String = "",
    val notas: String? = null,
    val modificadores: List<String> = emptyList()
)

// ── Cobro ────────────────────────────────────────────────────────────────────
@Serializable
data class PropinaSugeridaDto(val idComanda: Int = 0, val propina: Double = 0.0)

@Serializable
data class CerrarResultadoDto(
    val idDocumento: Int = 0,
    val folio: String = "",
    val total: Double = 0.0,
    val pagado: Double = 0.0,
    val cambio: Double = 0.0,
    val saldoPendiente: Double = 0.0
)

@Serializable
data class FormaPagoDto(
    val idFormaPago: Int = 0,
    val nombre: String = "",
    val creditoClientes: Boolean = false,
    val cFormaPago: String? = null
)

// ── Catálogo ─────────────────────────────────────────────────────────────────
@Serializable
data class CategoriaApiDto(val idCategoria: Int = 0, val nombre: String = "")

@Serializable
data class ArticuloApiDto(
    val idArticulo: Int = 0,
    val clave: String = "",
    val codigoBarras: String? = null,
    val nombre: String = "",
    val descripcion: String? = null,
    val precioVenta: Double = 0.0,
    val precioIncluyeImpuesto: Boolean = false,
    val exento: Boolean = false,
    val tasaIva: Double = 0.16,
    val tasaIeps: Double = 0.0,
    val iepsTipoFactor: String = "",
    val manejaInventario: Boolean = false,
    val idCategoria: Int? = null,
    val tieneImagen: Boolean = false,
    val claveProdServSat: String? = null,
    val claveUnidadSat: String? = null,
    val existencia: Double? = null,
    val precioLista: Double? = null,
    val esKit: Boolean = false,
    val esPlatillo: Boolean = false,
    val visibleTouch: Boolean = true
)

@Serializable
data class ModificadorApiDto(
    val idModificador: Int = 0,
    val nombre: String = "",
    val tipo: Int = 1,
    val precioExtra: Double = 0.0,
    val afectaInventario: Boolean = false,
    val idArticuloInsumo: Int? = null,
    val cantidadDelta: Double = 0.0
)

@Serializable
data class KitSlotDto(
    val idKitSlot: Int = 0,
    val etiqueta: String = "",
    val cantidadDefecto: Double = 1.0,
    val opciones: List<KitOpcionDto> = emptyList()
)

@Serializable
data class KitOpcionDto(
    val idArticulo: Int = 0,
    val nombre: String = "",
    val precioExtra: Double = 0.0
)

// ── Domicilio ────────────────────────────────────────────────────────────────
@Serializable
data class ComandaSinMesaDto(
    val idComanda: Int = 0,
    val folio: String = "",
    val tipoServicio: Int = 2,
    val nombreCliente: String? = null,
    val telefonoCliente: String? = null,
    val direccionEntrega: String? = null,
    val idRepartidor: Int? = null,
    val idZonaReparto: Int? = null,
    val cargoEntrega: Double = 0.0,
    val statusEntrega: Int = 0,
    val total: Double = 0.0,
    val fechaApertura: String = ""
)

@Serializable
data class RepartidorDto(
    val idRepartidor: Int = 0,
    val nombre: String = "",
    val telefono: String? = null,
    val activo: Boolean = true
)

@Serializable
data class ZonaRepartoDto(
    val idZonaReparto: Int = 0,
    val nombre: String = "",
    val cargo: Double = 0.0,
    val activo: Boolean = true
)

// ── Reservaciones ────────────────────────────────────────────────────────────
@Serializable
data class ReservacionApiDto(
    val idReservacion: Int = 0,
    val idMesa: Int? = null,
    val nombreCliente: String = "",
    val telefono: String? = null,
    val fechaHora: String = "",
    val personas: Int = 2,
    val observaciones: String? = null,
    val status: Int = 1
)

// ── Puntos de impresión ──────────────────────────────────────────────────────
@Serializable
data class PuntoImpresionDto(
    val idPuntoImpresion: Int = 0,
    val nombre: String = "",
    val impresora: String? = null,
    val ancho: Int = 48,
    val copias: Int = 1,
    val imprimirAlEnviar: Boolean = true,
    val activo: Boolean = true,
    val categorias: List<Int> = emptyList()
)

// ── Caja ─────────────────────────────────────────────────────────────────────
@Serializable
data class ResumenCajaDto(
    val abierta: Boolean = false,
    val idCorteCaja: Int? = null,
    val desde: String? = null,
    val fondoInicial: Double = 0.0,
    val ventasEfectivo: Double = 0.0,
    val ventasTarjeta: Double = 0.0,
    val ventasTransferencia: Double = 0.0,
    val ventasOtros: Double = 0.0,
    val totalVentas: Double = 0.0,
    val numVentas: Int = 0,
    val ingresos: Double = 0.0,
    val retiros: Double = 0.0,
    val efectivoEsperado: Double = 0.0,
    val movimientos: List<MovimientoCajaDto> = emptyList()
)

@Serializable
data class MovimientoCajaDto(
    val tipo: String = "",
    val monto: Double = 0.0,
    val concepto: String? = null,
    val fecha: String = "",
    val usuario: String? = null
)

@Serializable
data class CorteZResultadoDto(
    val folio: String = "",
    val efectivoEsperado: Double = 0.0,
    val efectivoReal: Double = 0.0,
    val diferencia: Double = 0.0
)

// ── Reportes ─────────────────────────────────────────────────────────────────
@Serializable
data class ReportesDiaDto(
    val fecha: String = "",
    val totalVentas: Double = 0.0,
    val numVentas: Int = 0,
    val descuentos: Double = 0.0,
    val ticketPromedio: Double = 0.0,
    val porFormaPago: List<ReporteFilaDto> = emptyList(),
    val porMesero: List<ReporteFilaDto> = emptyList(),
    val porCategoria: List<ReporteFilaDto> = emptyList(),
    val topProductos: List<ReporteFilaDto> = emptyList()
)

@Serializable
data class ReporteFilaDto(
    val concepto: String = "",
    val importe: Double = 0.0,
    val cantidad: Double = 0.0
)
