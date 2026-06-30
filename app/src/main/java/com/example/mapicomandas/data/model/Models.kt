package com.example.mapicomandas.data.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal

// ─── Enums de status ──────────────────────────────────────────────────────────

object StatusMesa {
    const val LIBRE = 1
    const val OCUPADA = 2
    const val RESERVADA = 3
    const val CUENTA_PEDIDA = 4
    const val EN_LIMPIEZA = 5
}

object StatusComanda {
    const val ABIERTA = 1
    const val EN_COCINA = 2
    const val LISTA = 3
    const val CUENTA_PEDIDA = 4
    const val CERRADA = 5
    const val CANCELADA = 6
}

object StatusLinea {
    const val PENDIENTE = 1
    const val EN_COCINA = 2
    const val LISTO = 3
    const val ENTREGADO = 4
    const val CANCELADO = 5
}

object TipoServicio {
    const val COMEDOR = 1
    const val PARA_LLEVAR = 2
    const val DOMICILIO = 3
}

object StatusEntrega {
    const val NA = 0
    const val PENDIENTE = 1
    const val EN_CAMINO = 2
    const val ENTREGADO = 3
}

object TipoModificador {
    const val QUITA = 1
    const val AGREGA_GRATIS = 2
    const val AGREGA_CON_COSTO = 3
}

// ─── Mesas ────────────────────────────────────────────────────────────────────

@Serializable
data class Mesa(
    val idMesa: Int,
    val numero: String,
    val zona: String,
    val capacidad: Int,
    val status: Int,
    val posX: Int,
    val posY: Int,
    val ancho: Int,
    val alto: Int,
    val forma: Int,
    val color: Int,
    val idGrupoMesa: Int?,
    val activa: Boolean
)

@Serializable
data class MesaUi(
    val idMesa: Int,
    val numero: String,
    val zona: String,
    val capacidad: Int,
    val status: Int,
    val posX: Int,
    val posY: Int,
    val ancho: Int,
    val alto: Int,
    val forma: Int,
    val color: Int,
    val idGrupoMesa: Int?,
    val idComanda: Int?,
    val folio: String?,
    val fechaApertura: String?,
    val importeCuenta: Double,
    val reservasHoy: Int
)

// ─── Meseros ──────────────────────────────────────────────────────────────────

@Serializable
data class Mesero(
    val idMesero: Int,
    val nombre: String,
    val apellidos: String,
    val codigo: String,
    val activo: Boolean
)

// ─── Artículos ────────────────────────────────────────────────────────────────

@Serializable
data class Articulo(
    val idArticulo: Int,
    val clave: String,
    val nombre: String,
    val precioVenta: Double,
    val costo: Double,
    val idCategoria: Int,
    val codigoBarras: String?,
    val esPlatillo: Boolean,
    val esKit: Boolean,
    val esInsumo: Boolean,
    val manejaInventario: Boolean,
    val colorBoton: Int?,
    val idPuntoImpresion: Int?,
    val tasaIEPS: Double,
    val exento: Boolean,
    val precioIncluyeImpuesto: Boolean,
    val iepsTipoFactor: String?,
    val iepsCuota: Double,
    val tasaIva: Double = 0.16,
    val imagenBase64: String? = null
)

@Serializable
data class Categoria(
    val idCategoria: Int,
    val nombre: String,
    val activo: Boolean,
    val colorBoton: Int?,
    val imagenBase64: String?
)

// ─── Comandas ─────────────────────────────────────────────────────────────────

@Serializable
data class MaestroComanda(
    val idComanda: Int,
    val folio: String,
    val idMesa: Int?,
    val idMesero: Int,
    val idVenta: Int?,
    val idUsuario: Int?,
    val idTienda: Int,
    val numPersonas: Int,
    val status: Int,
    val fechaApertura: String,
    val fechaCierre: String?,
    val observaciones: String,
    val subtotal: Double,
    val descuento: Double,
    val iva: Double,
    val total: Double,
    val tipoServicio: Int,
    val nombreCliente: String?,
    val telefonoCliente: String?,
    val direccionEntrega: String?,
    val idRepartidor: Int?,
    val idZonaReparto: Int?,
    val cargoEntrega: Double,
    val statusEntrega: Int
)

@Serializable
data class LineaComanda(
    val idDetalleComanda: Int,
    val idComanda: Int,
    val idArticulo: Int,
    val nombreArticulo: String,
    val linea: Int,
    val cantidad: Double,
    val precioUnitario: Double,
    val descuento: Double,
    val subtotal: Double,
    val iva: Double,
    val ieps: Double,
    val total: Double,
    val status: Int,
    val notas: String,
    val numLugar: Int,
    val fechaEnvio: String?,
    val fechaListo: String?,
    val minutosCocina: Int?,
    val costoUnitario: Double,
    val modificadores: List<ModificadorAplicado> = emptyList(),
    val componentesKit: List<ComponenteKit> = emptyList()
)

// ─── Modificadores ────────────────────────────────────────────────────────────

@Serializable
data class Modificador(
    val idModificador: Int,
    val nombre: String,
    val tipo: Int,
    val afectaInventario: Boolean,
    val idArticuloInsumo: Int?,
    val cantidadDelta: Double,
    val precioExtra: Double
)

@Serializable
data class ModificadorAplicado(
    val idModificador: Int,
    val tipo: Int,
    val nombreSnapshot: String,
    val precioExtra: Double,
    val afectaInventario: Boolean,
    val idArticuloInsumo: Int?,
    val cantidadDelta: Double
)

// ─── Kits ─────────────────────────────────────────────────────────────────────

@Serializable
data class KitSlot(
    val idKitSlot: Int,
    val idArticuloPadre: Int,
    val etiqueta: String,
    val cantidadDefecto: Double,
    val opciones: List<Articulo> = emptyList()
)

@Serializable
data class ComponenteKit(
    val idKitSlot: Int,
    val idArticulo: Int,
    val cantidad: Double,
    val etiquetaSlot: String,
    val nombreSnapshot: String
)

// ─── KDS ──────────────────────────────────────────────────────────────────────

@Serializable
data class PlatilloKds(
    val idDetalleComanda: Int,
    val idComanda: Int,
    val folio: String,
    val mesa: String,
    val articulo: String,
    val cantidad: Double,
    val notas: String,
    val status: Int,
    val fechaEnvio: String?,
    val minutos: Int?,
    val minutosTranscurridos: Int?,
    val kitRef: String
)

data class GrupoKds(
    val idComanda: Int,
    val folio: String,
    val mesa: String,
    val platillos: List<PlatilloKds>,
    val maxMinutosTranscurridos: Int
)

// ─── Puntos de impresión ──────────────────────────────────────────────────────

@Serializable
data class PuntoImpresion(
    val idPuntoImpresion: Int,
    val nombre: String,
    val impresora: String,
    val ancho: Int,
    val copias: Int,
    val imprimirAlEnviar: Boolean,
    val activo: Boolean,
    val categorias: List<Int> = emptyList()
)

// ─── Domicilio ────────────────────────────────────────────────────────────────

@Serializable
data class Repartidor(
    val idRepartidor: Int,
    val nombre: String,
    val telefono: String,
    val activo: Boolean
)

@Serializable
data class ZonaReparto(
    val idZonaReparto: Int,
    val nombre: String,
    val cargo: Double,
    val activo: Boolean
)

@Serializable
data class ComandaSinMesa(
    val idComanda: Int,
    val folio: String,
    val tipoServicio: Int,
    val nombreCliente: String?,
    val telefonoCliente: String?,
    val direccionEntrega: String?,
    val idRepartidor: Int?,
    val nombreRepartidor: String?,
    val idZonaReparto: Int?,
    val nombreZona: String?,
    val cargoEntrega: Double,
    val statusEntrega: Int,
    val total: Double,
    val fechaApertura: String,
    val status: Int
)

// ─── Pagos ────────────────────────────────────────────────────────────────────

@Serializable
data class PagoVenta(
    val idFormaPago: Int,
    val nombreFormaPago: String,
    val importe: Double,
    val referencia: String = ""
)

@Serializable
data class FormaPago(
    val idFormaPago: Int,
    val nombre: String,
    val activo: Boolean,
    val esEfectivo: Boolean
)

// ─── Requests / Responses ─────────────────────────────────────────────────────

@Serializable
data class AbrirComandaRequest(
    val idMesa: Int?,
    val idMesero: Int,
    val numPersonas: Int,
    val idTienda: Int,
    val idCaja: Int,
    val observaciones: String = "",
    val tipoServicio: Int = TipoServicio.COMEDOR,
    val nombreCliente: String = "",
    val telefonoCliente: String = "",
    val direccionEntrega: String = "",
    val idRepartidor: Int? = null,
    val idZonaReparto: Int? = null,
    val cargoEntrega: Double = 0.0
)

@Serializable
data class AgregarArticuloRequest(
    val idComanda: Int,
    val idArticulo: Int,
    val cantidad: Double,
    val precio: Double,
    val tasaIva: Double,
    val ieps: Double,
    val notas: String,
    val modificadores: List<ModificadorAplicado> = emptyList(),
    val componentesKit: List<ComponenteKit>? = null
)

@Serializable
data class CerrarComandaRequest(
    val idComanda: Int,
    val idFormaPago: Int,
    val idCliente: Int,
    val idUsuario: Int,
    val idTienda: Int,
    val idCaja: Int,
    val idAlmacen: Int,
    val tasaIva: Double,
    val propina: Double = 0.0,
    val pagos: List<PagoVenta>? = null
)

@Serializable
data class IdResponse(val id: Int)

@Serializable
data class MessageResponse(val mensaje: String)

// ─── Configuración ────────────────────────────────────────────────────────────

@Serializable
data class ConfigEntry(
    val clave: String,
    val valor: String
)

// ─── Caja ────────────────────────────────────────────────────────────────────

@Serializable
data class MovimientoCaja(
    val tipo: String,
    val concepto: String,
    val importe: Double,
    val idCaja: Int,
    val idUsuario: Int
)

@Serializable
data class ResumenCaja(
    val totalVentas: Double,
    val totalEfectivo: Double,
    val totalOtros: Double,
    val totalRetiros: Double,
    val totalIngresos: Double,
    val saldoFinal: Double,
    val numTransacciones: Int
)
