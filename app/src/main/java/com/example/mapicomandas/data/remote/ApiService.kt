package com.example.mapicomandas.data.remote

import com.example.mapicomandas.data.model.*
import retrofit2.http.*

interface ApiService {

    // ── Mesas ─────────────────────────────────────────────────────────────────
    @GET("api/mesas")
    suspend fun obtenerMesas(@Query("zona") zona: String? = null): List<MesaUi>

    @GET("api/mesas/zonas")
    suspend fun obtenerZonas(): List<String>

    @GET("api/mesas/libres")
    suspend fun obtenerMesasLibres(): List<Mesa>

    // ── Meseros ───────────────────────────────────────────────────────────────
    @GET("api/meseros")
    suspend fun obtenerMeseros(@Query("soloActivos") soloActivos: Boolean = true): List<Mesero>

    @POST("api/meseros")
    suspend fun guardarMesero(@Body mesero: Mesero): IdResponse

    // ── Comandas ──────────────────────────────────────────────────────────────
    @POST("api/comandas/abrir")
    suspend fun abrirComanda(@Body req: AbrirComandaRequest): IdResponse

    @GET("api/comandas/{id}")
    suspend fun obtenerComanda(@Path("id") idComanda: Int): MaestroComanda

    @GET("api/comandas/{id}/detalle")
    suspend fun obtenerDetalle(@Path("id") idComanda: Int): List<LineaComanda>

    @POST("api/comandas/articulo")
    suspend fun agregarArticulo(@Body req: AgregarArticuloRequest): IdResponse

    @DELETE("api/comandas/linea/{idDetalle}")
    suspend fun cancelarLinea(@Path("idDetalle") idDetalle: Int): MessageResponse

    @POST("api/comandas/linea/{idDetalle}/separar")
    suspend fun separarCantidad(
        @Path("idDetalle") idDetalle: Int,
        @Query("cantidad") cantidad: Double,
        @Query("nuevoLugar") nuevoLugar: Int
    ): MessageResponse

    @PUT("api/comandas/{id}/mesero/{idMesero}")
    suspend fun cambiarMesero(
        @Path("id") idComanda: Int,
        @Path("idMesero") idMesero: Int
    ): MessageResponse

    @PUT("api/comandas/{id}/mesa/{idMesaNueva}")
    suspend fun cambiarMesa(
        @Path("id") idComanda: Int,
        @Path("idMesaNueva") idMesaNueva: Int,
        @Query("idMesaActual") idMesaActual: Int
    ): MessageResponse

    @PUT("api/comandas/{id}/comensales/{num}")
    suspend fun actualizarComensales(
        @Path("id") idComanda: Int,
        @Path("num") numPersonas: Int
    ): MessageResponse

    @POST("api/comandas/{id}/enviar-cocina")
    suspend fun enviarACocina(@Path("id") idComanda: Int): MessageResponse

    @PUT("api/comandas/linea/{idDetalle}/listo")
    suspend fun marcarListo(@Path("idDetalle") idDetalle: Int): MessageResponse

    @PUT("api/comandas/linea/{idDetalle}/entregado")
    suspend fun marcarEntregado(@Path("idDetalle") idDetalle: Int): MessageResponse

    @POST("api/comandas/cerrar")
    suspend fun cerrarComanda(@Body req: CerrarComandaRequest): IdResponse

    @GET("api/comandas/{id}/propina")
    suspend fun calcularPropinaSugerida(@Path("id") idComanda: Int): Double

    // ── KDS ───────────────────────────────────────────────────────────────────
    @GET("api/kds/platillos")
    suspend fun obtenerPlatillosCocina(@Query("idPunto") idPunto: Int? = null): List<PlatilloKds>

    // ── Domicilio ─────────────────────────────────────────────────────────────
    @GET("api/comandas/sin-mesa")
    suspend fun obtenerComandasSinMesa(): List<ComandaSinMesa>

    @PUT("api/comandas/{id}/status-entrega/{status}")
    suspend fun actualizarStatusEntrega(
        @Path("id") idComanda: Int,
        @Path("status") status: Int
    ): MessageResponse

    @PUT("api/comandas/{id}/domicilio")
    suspend fun actualizarDomicilio(
        @Path("id") idComanda: Int,
        @Body body: Map<String, String>
    ): MessageResponse

    // ── Repartidores ──────────────────────────────────────────────────────────
    @GET("api/repartidores")
    suspend fun obtenerRepartidores(@Query("soloActivos") soloActivos: Boolean = true): List<Repartidor>

    @POST("api/repartidores")
    suspend fun guardarRepartidor(@Body repartidor: Repartidor): IdResponse

    // ── Zonas de reparto ──────────────────────────────────────────────────────
    @GET("api/zonas-reparto")
    suspend fun obtenerZonasReparto(@Query("soloActivos") soloActivos: Boolean = true): List<ZonaReparto>

    @POST("api/zonas-reparto")
    suspend fun guardarZonaReparto(@Body zona: ZonaReparto): IdResponse

    // ── Catálogos ─────────────────────────────────────────────────────────────
    @GET("api/articulos")
    suspend fun obtenerArticulos(
        @Query("idCategoria") idCategoria: Int? = null,
        @Query("clave") clave: String? = null,
        @Query("nombre") nombre: String? = null
    ): List<Articulo>

    @GET("api/categorias")
    suspend fun obtenerCategorias(): List<Categoria>

    @GET("api/modificadores")
    suspend fun obtenerModificadores(@Query("idArticulo") idArticulo: Int? = null): List<Modificador>

    @GET("api/kits/{idArticulo}/slots")
    suspend fun obtenerKitSlots(@Path("idArticulo") idArticulo: Int): List<KitSlot>

    @GET("api/puntos-impresion")
    suspend fun obtenerPuntosImpresion(): List<PuntoImpresion>

    @POST("api/puntos-impresion")
    suspend fun guardarPuntoImpresion(@Body punto: PuntoImpresion): IdResponse

    @GET("api/formas-pago")
    suspend fun obtenerFormasPago(): List<FormaPago>

    // ── Impresión ─────────────────────────────────────────────────────────────
    @POST("api/imprimir/comanda/{id}")
    suspend fun imprimirComanda(
        @Path("id") idComanda: Int,
        @Query("soloRecientes") soloRecientes: Boolean = true,
        @Query("todasLineas") todasLineas: Boolean = false
    ): List<String>

    @POST("api/imprimir/subcuenta/{id}")
    suspend fun imprimirSubCuenta(
        @Path("id") idComanda: Int,
        @Query("etiqueta") etiqueta: String,
        @Query("total") total: Double,
        @Body idsDetalle: List<Int>
    ): MessageResponse

    // ── Caja ─────────────────────────────────────────────────────────────────
    @POST("api/caja/habilitar")
    suspend fun habilitarCaja(@Query("idCaja") idCaja: Int, @Query("idUsuario") idUsuario: Int): MessageResponse

    @POST("api/caja/movimiento")
    suspend fun registrarMovimientoCaja(@Body mov: MovimientoCaja): IdResponse

    @GET("api/caja/resumen")
    suspend fun obtenerResumenCaja(
        @Query("idCaja") idCaja: Int,
        @Query("idTienda") idTienda: Int
    ): ResumenCaja

    @POST("api/caja/corte-z")
    suspend fun realizarCorteZ(
        @Query("idCaja") idCaja: Int,
        @Query("idUsuario") idUsuario: Int
    ): MessageResponse

    // ── Configuración ─────────────────────────────────────────────────────────
    @GET("api/config")
    suspend fun obtenerConfiguracion(
        @Query("idTienda") idTienda: Int,
        @Query("idCaja") idCaja: Int
    ): List<ConfigEntry>
}
