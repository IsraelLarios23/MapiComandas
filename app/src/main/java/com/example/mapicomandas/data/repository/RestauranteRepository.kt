package com.example.mapicomandas.data.repository

import com.example.mapicomandas.data.model.*

interface RestauranteRepository {

    // ── Login (usuarios MapiPOS) ──────────────────────────────────────────────
    suspend fun login(usuario: String, password: String): Usuario?
    suspend fun obtenerUsuarios(): List<Usuario>

    // ── Mesas ─────────────────────────────────────────────────────────────────
    suspend fun obtenerMesas(zona: String? = null): List<MesaUi>
    suspend fun obtenerZonas(): List<String>
    suspend fun obtenerMesasLibres(): List<Mesa>
    suspend fun obtenerMeserosActivos(): List<Mesero>

    // ── Apertura de comanda ───────────────────────────────────────────────────
    suspend fun abrirComanda(
        idMesa: Int, idMesero: Int, numPersonas: Int,
        idTienda: Int, idCaja: Int, obs: String = ""
    ): Int

    suspend fun abrirComandaSinMesa(
        tipoServicio: Int, idMesero: Int, idTienda: Int, idCaja: Int,
        cliente: String = "", tel: String = "", dir: String = "",
        idRepartidor: Int? = null, idZona: Int? = null, cargoEntrega: Double = 0.0
    ): Int

    // ── Líneas ────────────────────────────────────────────────────────────────
    suspend fun agregarArticulo(
        idComanda: Int, idArticulo: Int, cantidad: Double,
        precio: Double, tasaIva: Double, ieps: Double,
        notas: String, mods: List<ModificadorAplicado>,
        componentesKit: List<ComponenteKit>?
    ): Int

    suspend fun cancelarLinea(idDetalle: Int)

    suspend fun separarCantidad(idDetalle: Int, cantidadMover: Double, nuevoLugar: Int)

    suspend fun obtenerDetalle(idComanda: Int): List<LineaComanda>

    suspend fun obtenerComanda(idComanda: Int): MaestroComanda

    // ── Operaciones de mesa ───────────────────────────────────────────────────
    suspend fun cambiarMesero(idComanda: Int, idMeseroNuevo: Int)
    suspend fun cambiarMesa(idComanda: Int, idMesaActual: Int, idMesaNueva: Int)
    suspend fun actualizarComensales(idComanda: Int, numPersonas: Int)

    // ── Cocina / KDS ──────────────────────────────────────────────────────────
    suspend fun enviarACocina(idComanda: Int)
    suspend fun marcarListo(idDetalle: Int)
    suspend fun marcarEntregado(idDetalle: Int)
    suspend fun obtenerPlatillosCocina(idPunto: Int? = null): List<PlatilloKds>

    // ── Cobro ─────────────────────────────────────────────────────────────────
    suspend fun cerrarComanda(
        idComanda: Int, idFormaPago: Int, idCliente: Int, idUsuario: Int,
        idTienda: Int, idCaja: Int, idAlmacen: Int, tasaIva: Double,
        propina: Double = 0.0, pagos: List<PagoVenta>? = null
    ): Int

    suspend fun calcularPropinaSugerida(idComanda: Int): Double

    suspend fun obtenerFormasPago(): List<FormaPago>

    // ── Domicilio ─────────────────────────────────────────────────────────────
    suspend fun obtenerComandasSinMesaAbiertas(): List<ComandaSinMesa>
    suspend fun actualizarStatusEntrega(idComanda: Int, status: Int)
    suspend fun actualizarDomicilio(
        idComanda: Int, cliente: String, tel: String, dir: String,
        idRepartidor: Int?, idZona: Int?, cargo: Double
    )

    suspend fun obtenerRepartidores(soloActivos: Boolean = true): List<Repartidor>
    suspend fun guardarRepartidor(id: Int, nombre: String, tel: String, activo: Boolean): Int
    suspend fun obtenerZonasReparto(soloActivos: Boolean = true): List<ZonaReparto>
    suspend fun guardarZonaReparto(id: Int, nombre: String, cargo: Double, activo: Boolean): Int

    // ── Catálogos ─────────────────────────────────────────────────────────────
    suspend fun obtenerArticulos(idCategoria: Int? = null, clave: String? = null, nombre: String? = null): List<Articulo>
    suspend fun obtenerCategorias(): List<Categoria>
    suspend fun obtenerModificadores(idArticulo: Int? = null): List<Modificador>
    suspend fun obtenerKitSlots(idArticulo: Int): List<KitSlot>
    suspend fun obtenerPuntosImpresion(): List<PuntoImpresion>
    suspend fun obtenerMeseros(soloActivos: Boolean = true): List<Mesero>

    // ── Puntos de impresión (config) ──────────────────────────────────────────
    suspend fun guardarPuntoImpresion(punto: PuntoImpresion): Int
    suspend fun eliminarPuntoImpresion(idPunto: Int)
    suspend fun asignarCategoriasPunto(idPunto: Int, categorias: List<Int>)

    // ── Impresión ─────────────────────────────────────────────────────────────
    suspend fun imprimirComanda(idComanda: Int, soloRecienEnviadas: Boolean, todasLasLineas: Boolean): List<String>
    suspend fun imprimirSubCuenta(idComanda: Int, etiqueta: String, total: Double, idsDetalle: List<Int>)
    /** Rutea las líneas a los puntos de impresión (por categoría, con expansión de kits). */
    suspend fun construirTicketsCocina(idComanda: Int, soloRecienEnviadas: Boolean, todasLasLineas: Boolean): TicketsCocina

    // ── Caja ──────────────────────────────────────────────────────────────────
    suspend fun habilitarCaja(idCaja: Int, idUsuario: Int)
    suspend fun registrarMovimientoCaja(mov: MovimientoCaja): Int
    suspend fun obtenerResumenCaja(idCaja: Int, idTienda: Int): ResumenCaja
    suspend fun realizarCorteZ(idCaja: Int, idUsuario: Int)

    // ── Configuración ─────────────────────────────────────────────────────────
    suspend fun obtenerConfiguracion(idTienda: Int, idCaja: Int): List<ConfigEntry>
    suspend fun guardarConfig(clave: String, valor: String)
}
