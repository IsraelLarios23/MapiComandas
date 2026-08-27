package com.example.mapicomandas.data.repository

import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.data.api.ApiClient
import com.example.mapicomandas.data.api.ApiException
import com.example.mapicomandas.data.api.AuthMode
import com.example.mapicomandas.data.api.IdentidadService
import com.example.mapicomandas.data.api.dto.*
import com.example.mapicomandas.data.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [RestauranteRepository] sobre la API central (https://api.mapi.codesi.mx),
 * en lugar de jTDS/SQL directo. El servidor calcula IEPS, folio e importes y deriva
 * tienda/caja/almacén de dbo.ClienteConfig, así que la app ya NO los manda.
 *
 * El contrato del servidor (rama central-restaurante-100) ya trae layout de mesas, esKit,
 * tipoServicio/entrega en la comanda, kitRef y filtro por punto en cocina, ventas por
 * tienda y config por caja. Hueco restante: editar datos de un pedido a domicilio abierto.
 */
@Singleton
class RestauranteRepositoryHttpImpl @Inject constructor(
    private val api: ApiClient,
    private val identidad: IdentidadService,
    private val session: SessionManager
) : RestauranteRepository {

    // ── helpers ───────────────────────────────────────────────────────────────
    private inline fun <reified T> parse(el: JsonElement): T = api.json.decodeFromJsonElement<T>(el)
    private fun JsonElement.int(field: String): Int =
        runCatching { jsonObject[field]?.jsonPrimitive?.intOrNull ?: 0 }.getOrDefault(0)

    // ── Login / usuarios ────────────────────────────────────────────────────────
    override suspend fun login(usuario: String, password: String): Usuario? =
        try {
            identidad.login(usuario, password)   // guarda el token de sesión
            Usuario(session.idUsuario, session.nombreUsuarioActual.ifBlank { usuario }, usuario, 0, true)
        } catch (e: ApiException.Negocio) {
            null   // usuario/contraseña incorrectos
        }   // ApiException.Reautenticar(vincular) se propaga → la UI manda a vincular

    // La API central no expone un catálogo de usuarios (identidad va por login).
    override suspend fun obtenerUsuarios(): List<Usuario> = emptyList()

    // LIMITACIÓN CONOCIDA: "autorización de supervisor" = un login cualquiera (igual que el
    // POS de escritorio). Cualquier usuario ACTIVO puede autorizar una cancelación.
    // TODO(api): cuando la central exponga un permiso/rol de supervisor, exigirlo aquí.
    override suspend fun autorizarSupervisor(usuario: String, password: String): Boolean =
        runCatching {
            val body = buildJsonObject { put("usuario", usuario.trim()); put("password", password) }
            api.post("/v1/login", body, AuthMode.DEVICE)   // valida credenciales; el token emitido se descarta
            true
        }.getOrDefault(false)

    // ── Ventas ──────────────────────────────────────────────────────────────────
    override suspend fun obtenerVentasDia(): List<VentaDia> =
        // Shape real DocHistorialDto: {idVenta,folio,hora,cliente,total,saldoPendiente,cancelada}.
        // OJO servidor: filtra por el usuario de la sesión (no toda la tienda).
        parse<List<VentaDiaDto>>(api.get("/v1/ventas?ambito=tienda")).map {
            VentaDia(it.idVenta, it.folio, it.hora, it.total, it.cancelada, null)
        }

    override suspend fun construirTicketVenta(idVenta: Int): List<String> {
        // Shape real TicketDetalleDto (líneas usan `importe`, no `total`).
        val dto = parse<VentaDetalleDto>(api.get("/v1/ventas/$idVenta"))
        val emp = obtenerEmpresaConfig()
        val l = mutableListOf<String>()
        if (emp.empresa.isNotBlank()) l += emp.empresa.take(40)
        if (emp.rfc.isNotBlank()) l += "RFC: ${emp.rfc}"
        if (emp.encabezado.isNotBlank()) emp.encabezado.lines().forEach { l += it.take(40) }
        l += "VENTA ${dto.folio}" + if (dto.cancelada) "  **CANCELADA**" else ""
        if (dto.fecha.isNotBlank()) l += dto.fecha
        if (dto.cliente.isNotBlank()) l += "Cliente: ${dto.cliente}".take(40)
        l += "-".repeat(40)
        dto.lineas.forEach {
            l += ("${it.cantidad.toInt()} x ${it.nombre}").take(30).padEnd(30) +
                 String.format("%10.2f", it.importe)
        }
        l += "-".repeat(40)
        l += "Subtotal:".padEnd(30) + String.format("%10.2f", dto.subtotal)
        if (dto.descuento > 0) l += "Descuento:".padEnd(30) + String.format("%10.2f", -dto.descuento)
        l += "IVA:".padEnd(30) + String.format("%10.2f", dto.iva)
        l += "TOTAL:".padEnd(30) + String.format("%10.2f", dto.total)
        dto.pagos.forEach { l += "  ${it.forma}:".padEnd(30) + String.format("%10.2f", it.monto) }
        if (dto.cambio > 0) l += "Cambio:".padEnd(30) + String.format("%10.2f", dto.cambio)
        if (emp.pie.isNotBlank()) { l += ""; emp.pie.lines().forEach { l += it.take(40) } }
        return l
    }

    override suspend fun cancelarVenta(idVenta: Int) {
        api.post("/v1/ventas/$idVenta/cancelar")
    }

    // ── Paridad desktop vía API ────────────────────────────────────────────────
    override suspend fun obtenerEmpresaConfig(): EmpresaConfig =
        runCatching { parse<EmpresaConfigDto>(api.get("/v1/configuracion")) }
            .map { EmpresaConfig(it.empresa, it.rfc, it.telefono, it.encabezado, it.pie) }
            .getOrDefault(EmpresaConfig())

    override suspend fun buscarArticuloPorCodigo(codigo: String): Articulo? =
        runCatching { parse<ArticuloApiDto>(api.get("/v1/articulos/codigo/${enc(codigo)}")) }
            .getOrNull()?.toArticulo()

    override suspend fun obtenerClientes(q: String): List<ClienteLite> =
        parse<List<ClienteApiDto>>(api.get("/v1/clientes?q=${enc(q)}"))
            .map { ClienteLite(it.idCliente, it.nombre, it.rfc ?: "") }

    override suspend fun obtenerImagenArticulo(idArticulo: Int): ByteArray? =
        api.getBinary("/v1/articulos/$idArticulo/imagen")

    override suspend fun cancelarComanda(idComanda: Int) {
        api.post("/v1/comandas/$idComanda/cancelar")
    }

    // ── Mesas ─────────────────────────────────────────────────────────────────
    override suspend fun obtenerMesas(zona: String?): List<MesaUi> {
        val q = if (!zona.isNullOrBlank()) "?zona=${enc(zona)}" else ""
        return parse<List<MesaDto>>(api.get("/v1/mesas$q")).map { it.toMesaUi() }
    }

    override suspend fun obtenerZonas(): List<String> =
        parse<List<String>>(api.get("/v1/mesas/zonas"))

    override suspend fun obtenerMesasLibres(): List<Mesa> =
        parse<List<MesaDto>>(api.get("/v1/mesas"))
            .filter { it.status == StatusMesa.LIBRE }
            .map { it.toMesa() }

    override suspend fun obtenerMeserosActivos(): List<Mesero> = obtenerMeseros(true)

    override suspend fun obtenerMeseros(soloActivos: Boolean): List<Mesero> =
        parse<List<MeseroDto>>(api.get("/v1/meseros?soloActivos=$soloActivos"))
            .map { Mesero(it.idMesero, it.nombre, "", it.codigo ?: "", it.activo) }

    // ── Apertura ───────────────────────────────────────────────────────────────
    override suspend fun abrirComanda(
        idMesa: Int, idMesero: Int, numPersonas: Int, idTienda: Int, idCaja: Int, obs: String
    ): Int {
        val body = buildJsonObject {
            put("idMesa", idMesa); put("idMesero", idMesero)
            put("numPersonas", numPersonas); put("observaciones", obs)
        }
        // 400 "Esa mesa ya está ocupada por otra caja." se propaga → la UI lo muestra.
        return parse<ComandaDto>(api.post("/v1/comandas", body)).idComanda
    }

    override suspend fun abrirComandaSinMesa(
        tipoServicio: Int, idMesero: Int, idTienda: Int, idCaja: Int,
        cliente: String, tel: String, dir: String, idRepartidor: Int?, idZona: Int?, cargoEntrega: Double
    ): Int {
        val body = buildJsonObject {
            put("tipoServicio", tipoServicio)
            put("nombreCliente", cliente); put("telefonoCliente", tel); put("direccionEntrega", dir)
            idRepartidor?.let { put("idRepartidor", it) }
            idZona?.let { put("idZonaReparto", it) }
            put("cargoEntrega", cargoEntrega)
            put("idMesero", idMesero)
        }
        return parse<ComandaSinMesaDto>(api.post("/v1/domicilio/comandas", body)).idComanda
    }

    // ── Líneas ─────────────────────────────────────────────────────────────────
    override suspend fun agregarArticulo(
        idComanda: Int, idArticulo: Int, cantidad: Double, precio: Double, tasaIva: Double,
        ieps: Double, notas: String, mods: List<ModificadorAplicado>, componentesKit: List<ComponenteKit>?
    ): Int {
        // NO se manda precio/tasaIva/ieps: el servidor los relee y calcula (arregla el bug de IEPS).
        val body = buildJsonObject {
            put("idArticulo", idArticulo)
            put("cantidad", cantidad)
            put("notas", notas)
            if (mods.isNotEmpty()) putJsonArray("modificadores") {
                mods.forEach { m -> addJsonObject { put("idModificador", m.idModificador) } }
            }
            if (!componentesKit.isNullOrEmpty()) putJsonArray("componentesKit") {
                componentesKit.forEach { c -> addJsonObject {
                    put("idKitSlot", c.idKitSlot); put("idArticulo", c.idArticulo); put("cantidad", c.cantidad)
                } }
            }
        }
        val comanda = parse<ComandaDto>(api.post("/v1/comandas/$idComanda/lineas", body))
        return comanda.lineas.maxByOrNull { it.idDetalle }?.idDetalle ?: 0
    }

    override suspend fun cancelarLinea(idDetalle: Int) {
        api.delete("/v1/comandas/lineas/$idDetalle")
    }

    override suspend fun separarCantidad(idDetalle: Int, cantidadMover: Double, nuevoLugar: Int) {
        val body = buildJsonObject { put("cantidad", cantidadMover); put("numLugar", nuevoLugar) }
        api.post("/v1/comandas/lineas/$idDetalle/separar", body)
    }

    override suspend fun obtenerDetalle(idComanda: Int): List<LineaComanda> =
        parse<ComandaDto>(api.get("/v1/comandas/$idComanda")).lineas.map { it.toLineaComanda(idComanda) }

    override suspend fun obtenerComanda(idComanda: Int): MaestroComanda =
        // El server ya manda tipoServicio/entrega/observaciones en la comanda (1 llamada).
        parse<ComandaDto>(api.get("/v1/comandas/$idComanda")).toMaestroComanda()

    // ── Operaciones de mesa ────────────────────────────────────────────────────
    override suspend fun cambiarMesero(idComanda: Int, idMeseroNuevo: Int) {
        api.put("/v1/comandas/$idComanda/mesero", buildJsonObject { put("idMesero", idMeseroNuevo) })
    }

    override suspend fun cambiarMesa(idComanda: Int, idMesaActual: Int, idMesaNueva: Int) {
        api.put("/v1/comandas/$idComanda/mesa", buildJsonObject { put("idMesaDestino", idMesaNueva) })
    }

    override suspend fun actualizarComensales(idComanda: Int, numPersonas: Int) {
        api.put("/v1/comandas/$idComanda/comensales", buildJsonObject { put("numPersonas", numPersonas) })
    }

    // ── Cocina / KDS ────────────────────────────────────────────────────────────
    override suspend fun contarPlatillosListos(): Int =
        parse<List<PlatilloKdsDto>>(api.get("/v1/cocina?soloListos=true")).size

    override suspend fun enviarACocina(idComanda: Int) {
        api.post("/v1/comandas/$idComanda/enviar-cocina")
    }

    override suspend fun marcarListo(idDetalle: Int) {
        api.post("/v1/cocina/$idDetalle/listo")
    }

    override suspend fun marcarEntregado(idDetalle: Int) {
        api.post("/v1/cocina/$idDetalle/entregado")
    }

    override suspend fun obtenerPlatillosCocina(idPunto: Int?): List<PlatilloKds> {
        val q = if (idPunto != null && idPunto > 0) "?punto=$idPunto" else ""
        return parse<List<PlatilloKdsDto>>(api.get("/v1/cocina$q")).map {
            PlatilloKds(
                idDetalleComanda = it.idDetalle, idComanda = it.idComanda, folio = it.folio,
                mesa = it.mesa?.toString() ?: "", articulo = it.nombre, cantidad = it.cantidad,
                notas = it.notas ?: "", status = it.status, fechaEnvio = null, minutos = null,
                minutosTranscurridos = it.minutosTranscurridos, kitRef = it.kitRef ?: ""
            )
        }
    }

    // ── Cobro ──────────────────────────────────────────────────────────────────
    override suspend fun cerrarComanda(
        idComanda: Int, idFormaPago: Int, idCliente: Int, idUsuario: Int, idTienda: Int,
        idCaja: Int, idAlmacen: Int, tasaIva: Double, propina: Double, pagos: List<PagoVenta>?
    ): Int {
        // Manda propina (se persiste en la venta) y NO manda IEPS/total (server calcula).
        // Sin pagos reales NO se cierra: un pago con monto 0 dejaría saldo=total (venta fiada).
        val listaPagos = pagos?.takeIf { it.isNotEmpty() && it.any { p -> p.importe > 0.0 } }
            ?: throw ApiException.Negocio(400, "No hay pagos capturados para cerrar la cuenta.")
        val body = buildJsonObject {
            putJsonArray("pagos") {
                listaPagos.forEach { p -> addJsonObject {
                    put("idFormaPago", p.idFormaPago); put("monto", p.importe)
                    if (p.referencia.isNotBlank()) put("referencia", p.referencia)
                } }
            }
            put("propina", propina)
            if (idCliente > 0) put("idCliente", idCliente)
        }
        return parse<CerrarResultadoDto>(api.post("/v1/comandas/$idComanda/cerrar", body)).idDocumento
    }

    override suspend fun calcularPropinaSugerida(idComanda: Int): Double =
        parse<PropinaSugeridaDto>(api.get("/v1/comandas/$idComanda/propina-sugerida")).propina

    override suspend fun obtenerFormasPago(): List<FormaPago> =
        parse<List<FormaPagoDto>>(api.get("/v1/formas-pago")).map {
            FormaPago(
                idFormaPago = it.idFormaPago, nombre = it.nombre, activo = true,
                esEfectivo = it.nombre.contains("efectivo", ignoreCase = true),
                usaTerminal = it.nombre.contains("netpay", ignoreCase = true) ||
                              it.nombre.contains("terminal", ignoreCase = true)
            )
        }

    // ── Domicilio ──────────────────────────────────────────────────────────────
    override suspend fun obtenerComandasSinMesaAbiertas(): List<ComandaSinMesa> =
        parse<List<ComandaSinMesaDto>>(api.get("/v1/domicilio/comandas")).map {
            ComandaSinMesa(
                idComanda = it.idComanda, folio = it.folio, tipoServicio = it.tipoServicio,
                nombreCliente = it.nombreCliente, telefonoCliente = it.telefonoCliente,
                direccionEntrega = it.direccionEntrega, idRepartidor = it.idRepartidor,
                nombreRepartidor = null, idZonaReparto = it.idZonaReparto, nombreZona = null,
                cargoEntrega = it.cargoEntrega, statusEntrega = it.statusEntrega, total = it.total,
                fechaApertura = it.fechaApertura, status = StatusComanda.ABIERTA
            )
        }

    override suspend fun actualizarStatusEntrega(idComanda: Int, status: Int) {
        api.put("/v1/domicilio/comandas/$idComanda/entrega", buildJsonObject { put("status", status) })
    }

    // TODO(api): no hay endpoint para editar los datos de un pedido a domicilio ya abierto.
    // Se deja sin efecto para no romper el flujo; el pedido conserva sus datos originales.
    override suspend fun actualizarDomicilio(
        idComanda: Int, cliente: String, tel: String, dir: String, idRepartidor: Int?, idZona: Int?, cargo: Double
    ) { /* pendiente-servidor */ }

    override suspend fun obtenerRepartidores(soloActivos: Boolean): List<Repartidor> =
        parse<List<RepartidorDto>>(api.get("/v1/domicilio/repartidores"))
            .filter { !soloActivos || it.activo }
            .map { Repartidor(it.idRepartidor, it.nombre, it.telefono ?: "", it.activo) }

    override suspend fun guardarRepartidor(id: Int, nombre: String, tel: String, activo: Boolean): Int {
        val body = buildJsonObject {
            put("nombre", nombre); put("telefono", tel); put("activo", activo)
            if (id > 0) put("idRepartidor", id)
        }
        return api.post("/v1/domicilio/repartidores", body).int("idRepartidor")
    }

    override suspend fun obtenerZonasReparto(soloActivos: Boolean): List<ZonaReparto> =
        parse<List<ZonaRepartoDto>>(api.get("/v1/domicilio/zonas"))
            .filter { !soloActivos || it.activo }
            .map { ZonaReparto(it.idZonaReparto, it.nombre, it.cargo, it.activo) }

    override suspend fun guardarZonaReparto(id: Int, nombre: String, cargo: Double, activo: Boolean): Int {
        val body = buildJsonObject {
            put("nombre", nombre); put("cargo", cargo); put("activo", activo)
            if (id > 0) put("idZonaReparto", id)
        }
        return api.post("/v1/domicilio/zonas", body).int("idZonaReparto")
    }

    // ── Reservaciones ──────────────────────────────────────────────────────────
    override suspend fun obtenerReservaciones(fecha: String): List<Reservacion> =
        parse<List<ReservacionApiDto>>(api.get("/v1/reservaciones?fecha=${enc(fecha)}")).map {
            Reservacion(
                idReservacion = it.idReservacion, idMesa = it.idMesa ?: 0,
                mesa = it.idMesa?.toString() ?: "", nombreCliente = it.nombreCliente,
                telefono = it.telefono ?: "",
                // El server serializa ISO "yyyy-MM-ddTHH:mm:ss" → normaliza a "yyyy-MM-dd HH:mm"
                fechaHora = it.fechaHora.replace('T', ' ').take(16),
                personas = it.personas,
                observaciones = it.observaciones ?: "", status = it.status
            )
        }

    override suspend fun guardarReservacion(
        id: Int, idMesa: Int, nombre: String, telefono: String, fechaHora: String,
        personas: Int, observaciones: String, idUsuario: Int
    ): Int {
        // El server enlaza FechaHora como DateTime (System.Text.Json): requiere ISO-8601 con 'T'.
        val fhIso = fechaHora.trim().replace(' ', 'T').let { if (it.length == 16) "$it:00" else it }
        val body = buildJsonObject {
            put("nombreCliente", nombre); put("fechaHora", fhIso); put("personas", personas)
            if (idMesa > 0) put("idMesa", idMesa)
            put("telefono", telefono); put("observaciones", observaciones)
            if (id > 0) put("idReservacion", id)
        }
        return api.post("/v1/reservaciones", body).int("idReservacion")
    }

    override suspend fun cambiarStatusReservacion(idReservacion: Int, status: Int) {
        api.put("/v1/reservaciones/$idReservacion/status", buildJsonObject { put("status", status) })
    }

    // ── Catálogos ──────────────────────────────────────────────────────────────
    override suspend fun obtenerArticulos(idCategoria: Int?, clave: String?, nombre: String?): List<Articulo> {
        val q = (clave ?: nombre)?.takeIf { it.isNotBlank() }?.let { "?q=${enc(it)}" } ?: ""
        val path = if (idCategoria != null && idCategoria > 0) "/v1/articulos/categoria/$idCategoria$q"
                   else "/v1/articulos$q"
        return parse<List<ArticuloApiDto>>(api.get(path)).map { it.toArticulo() }
    }

    override suspend fun obtenerCategorias(): List<Categoria> =
        parse<List<CategoriaApiDto>>(api.get("/v1/categorias"))
            .map { Categoria(it.idCategoria, it.nombre, true, null, null) }

    override suspend fun obtenerModificadores(idArticulo: Int?): List<Modificador> {
        if (idArticulo == null || idArticulo <= 0) return emptyList()
        return parse<List<ModificadorApiDto>>(api.get("/v1/articulos/$idArticulo/modificadores")).map {
            Modificador(it.idModificador, it.nombre, it.tipo, it.afectaInventario, it.idArticuloInsumo, it.cantidadDelta, it.precioExtra)
        }
    }

    override suspend fun obtenerKitSlots(idArticulo: Int): List<KitSlot> =
        parse<List<KitSlotDto>>(api.get("/v1/articulos/$idArticulo/kit")).map { s ->
            KitSlot(
                idKitSlot = s.idKitSlot, idArticuloPadre = idArticulo, etiqueta = s.etiqueta,
                cantidadDefecto = s.cantidadDefecto,
                opciones = s.opciones.map { o ->
                    Articulo(
                        idArticulo = o.idArticulo, clave = "", nombre = o.nombre, precioVenta = o.precioExtra,
                        costo = 0.0, idCategoria = 0, codigoBarras = null, esPlatillo = true, esKit = false,
                        esInsumo = false, manejaInventario = false, colorBoton = null, idPuntoImpresion = null,
                        tasaIEPS = 0.0, exento = false, precioIncluyeImpuesto = false, iepsTipoFactor = null,
                        iepsCuota = 0.0
                    )
                }
            )
        }

    override suspend fun obtenerPuntosImpresion(): List<PuntoImpresion> =
        parse<List<PuntoImpresionDto>>(api.get("/v1/puntos-impresion")).map { it.toPuntoImpresion() }

    // ── Puntos de impresión (config) ───────────────────────────────────────────
    override suspend fun guardarPuntoImpresion(punto: PuntoImpresion): Int {
        val body = buildJsonObject {
            put("nombre", punto.nombre); put("impresora", punto.impresora)
            put("ancho", punto.ancho); put("copias", punto.copias)
            put("imprimirAlEnviar", punto.imprimirAlEnviar)
            if (punto.idPuntoImpresion > 0) put("idPuntoImpresion", punto.idPuntoImpresion)
            putJsonArray("categorias") { punto.categorias.forEach { add(it) } }
        }
        return api.post("/v1/puntos-impresion", body).int("idPuntoImpresion")
    }

    override suspend fun eliminarPuntoImpresion(idPunto: Int) {
        api.delete("/v1/puntos-impresion/$idPunto")
    }

    override suspend fun asignarCategoriasPunto(idPunto: Int, categorias: List<Int>) {
        // La API reemplaza categorías en el POST del punto completo: relee y re-guarda.
        val punto = obtenerPuntosImpresion().firstOrNull { it.idPuntoImpresion == idPunto }
            ?: throw ApiException.Negocio(404, "Punto de impresión $idPunto no encontrado.")
        guardarPuntoImpresion(punto.copy(categorias = categorias))
    }

    // ── Impresión (texto local; los datos salen de la API) ──────────────────────
    override suspend fun imprimirComanda(idComanda: Int, soloRecienEnviadas: Boolean, todasLasLineas: Boolean): List<String> {
        val c = parse<ComandaDto>(api.get("/v1/comandas/$idComanda"))
        val l = mutableListOf<String>()
        l += "CUENTA ${c.folio}"
        l += "-".repeat(32)
        c.lineas.filter { it.status != StatusLinea.CANCELADO }.forEach {
            l += "${it.cantidad.toInt()} x ${it.nombre}".take(24).padEnd(24) + String.format("%8.2f", it.total)
        }
        l += "-".repeat(32)
        l += "TOTAL:".padEnd(24) + String.format("%8.2f", c.total)
        return l
    }

    // La sub-cuenta imprime vía PrinterService en el ViewModel (Fase 3, local).
    override suspend fun imprimirSubCuenta(idComanda: Int, etiqueta: String, total: Double, idsDetalle: List<Int>) { }

    override suspend fun construirTicketsCocina(idComanda: Int, soloRecienEnviadas: Boolean, todasLasLineas: Boolean): TicketsCocina {
        val puntos = parse<List<TicketCocinaDto>>(api.get("/v1/comandas/$idComanda/tickets-cocina"))
        val cab = puntos.firstOrNull()
        return TicketsCocina(
            cabecera = CabeceraCocina(
                folio = cab?.folio ?: "", mesa = cab?.mesa?.toString() ?: "",
                mesero = cab?.mesero ?: "", numPersonas = null
            ),
            puntos = puntos.map { p ->
                PuntoImpresionTicket(
                    idPunto = p.idPuntoImpresion, nombre = p.punto, impresora = p.impresora ?: "",
                    ancho = p.ancho, copias = p.copias,
                    lineas = p.lineas.map { ln ->
                        LineaCocina(
                            cantidad = ln.cantidad, articulo = ln.nombre, kitRef = "",
                            notas = ln.notas ?: "",
                            modificadores = ln.modificadores.map { ModCocina(TipoModificador.AGREGA_GRATIS, it, 0.0) }
                        )
                    }
                )
            }
        )
    }

    // ── Caja ───────────────────────────────────────────────────────────────────
    override suspend fun habilitarCaja(idCaja: Int, idUsuario: Int, fondoInicial: Double) {
        api.post("/v1/caja/habilitar", buildJsonObject { put("fondoInicial", fondoInicial) })
    }

    override suspend fun registrarMovimientoCaja(mov: MovimientoCaja): Int {
        // La API exige el tipo con nombre completo: "Ingreso" | "Retiro" (no "I"/"R").
        val tipoApi = when (mov.tipo.trim().uppercase()) {
            "I", "INGRESO" -> "Ingreso"
            "R", "RETIRO" -> "Retiro"
            else -> mov.tipo
        }
        val body = buildJsonObject { put("tipo", tipoApi); put("monto", mov.importe); put("concepto", mov.concepto) }
        api.post("/v1/caja/movimientos", body)
        return 1
    }

    override suspend fun obtenerResumenCaja(idCaja: Int, idTienda: Int): ResumenCaja {
        val r = parse<ResumenCajaDto>(api.get("/v1/caja/resumen"))
        return ResumenCaja(
            totalVentas = r.totalVentas,
            totalEfectivo = r.ventasEfectivo,
            totalOtros = r.ventasTarjeta + r.ventasTransferencia + r.ventasOtros,
            totalRetiros = r.retiros, totalIngresos = r.ingresos,
            saldoFinal = r.efectivoEsperado, numTransacciones = r.numVentas
        )
    }

    override suspend fun realizarCorteZ(idCaja: Int, idUsuario: Int, efectivoReal: Double, observaciones: String) {
        api.post("/v1/caja/corte-z", buildJsonObject {
            put("efectivoReal", efectivoReal)
            if (observaciones.isNotBlank()) put("observaciones", observaciones)
        })
    }

    // ── Reportes ───────────────────────────────────────────────────────────────
    override suspend fun obtenerReportesDia(fecha: String?): ReportesDia {
        val q = fecha?.takeIf { it.isNotBlank() }?.let { "?fecha=${enc(it)}" } ?: ""
        val r = parse<ReportesDiaDto>(api.get("/v1/reportes/dia$q"))
        fun map(l: List<ReporteFilaDto>) = l.map { ReporteFila(it.concepto, it.cantidad, it.importe) }
        val efectivo = r.porFormaPago.firstOrNull { it.concepto.contains("efectivo", true) }?.importe ?: 0.0
        val tarjeta = r.porFormaPago.firstOrNull { it.concepto.contains("tarjeta", true) }?.importe ?: 0.0
        return ReportesDia(
            resumen = ResumenDia(
                fecha = r.fecha, totalVentas = r.totalVentas, numTickets = r.numVentas,
                ticketPromedio = r.ticketPromedio, totalEfectivo = efectivo, totalTarjeta = tarjeta,
                totalOtros = (r.totalVentas - efectivo - tarjeta).coerceAtLeast(0.0), totalDescuentos = r.descuentos
            ),
            porFormaPago = map(r.porFormaPago), porMesero = map(r.porMesero),
            porCategoria = map(r.porCategoria), productosTop = map(r.topProductos)
        )
    }

    // ── Configuración ──────────────────────────────────────────────────────────
    override suspend fun obtenerConfiguracion(idTienda: Int, idCaja: Int): List<ConfigEntry> {
        // GET /v1/config ya resuelve la cascada caja→tienda→global en el servidor.
        val obj = api.get("/v1/config").jsonObject
        return obj.entries.map { ConfigEntry(it.key, it.value.jsonPrimitive.contentOrNull ?: "") }
    }

    // El overlay de scope ya viene resuelto en obtenerConfiguracion (server); no hay 2º nivel.
    override suspend fun obtenerConfiguracionScope(idTienda: Int, idCaja: Int): List<ConfigEntry> = emptyList()

    override suspend fun guardarConfig(clave: String, valor: String) {
        api.put("/v1/config", buildJsonObject { put("clave", clave); put("valor", valor) })
    }

    override suspend fun guardarConfigScope(clave: String, valor: String, idTienda: Int, idCaja: Int) {
        api.put("/v1/config", buildJsonObject {
            put("clave", clave); put("valor", valor); put("idCaja", idCaja)
        })
    }

    // ── mapeos DTO → dominio ────────────────────────────────────────────────────
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private fun MesaDto.toMesaUi() = MesaUi(
        idMesa = idMesa, numero = numero.toString(), zona = zona ?: "", capacidad = capacidad, status = status,
        posX = posX, posY = posY, ancho = ancho, alto = alto, forma = forma,
        color = color ?: "", idGrupoMesa = idGrupoMesa,
        idComanda = idComanda, folio = folio, fechaApertura = fechaApertura,
        importeCuenta = importeCuenta, reservasHoy = reservasHoy
    )

    private fun MesaDto.toMesa() = Mesa(
        idMesa = idMesa, numero = numero.toString(), zona = zona ?: "", capacidad = capacidad, status = status,
        posX = posX, posY = posY, ancho = ancho, alto = alto, forma = forma,
        color = color ?: "", idGrupoMesa = idGrupoMesa, activa = true
    )

    private fun LineaComandaDto.toLineaComanda(idComanda: Int) = LineaComanda(
        idDetalleComanda = idDetalle, idComanda = idComanda, idArticulo = idArticulo, nombreArticulo = nombre,
        linea = linea, cantidad = cantidad, precioUnitario = precioUnitario, descuento = 0.0,
        subtotal = total, iva = 0.0, ieps = 0.0, total = total, status = status, notas = notas ?: "",
        numLugar = numLugar, fechaEnvio = null, fechaListo = null, minutosCocina = null, costoUnitario = 0.0
    )

    private fun ComandaDto.toMaestroComanda() = MaestroComanda(
        idComanda = idComanda, folio = folio, idMesa = idMesa, idMesero = idMesero ?: 0, idVenta = null,
        idUsuario = null, idTienda = 0, numPersonas = numPersonas, status = status,
        fechaApertura = fechaApertura ?: "", fechaCierre = null, observaciones = observaciones ?: "",
        subtotal = subtotal, descuento = descuento, iva = iva, total = total,
        tipoServicio = tipoServicio,
        nombreCliente = nombreCliente, telefonoCliente = telefonoCliente,
        direccionEntrega = direccionEntrega, idRepartidor = null,
        idZonaReparto = null, cargoEntrega = cargoEntrega, statusEntrega = statusEntrega
    )

    private fun ArticuloApiDto.toArticulo() = Articulo(
        idArticulo = idArticulo, clave = clave, nombre = nombre, precioVenta = precioVenta, costo = 0.0,
        idCategoria = idCategoria ?: 0, codigoBarras = codigoBarras, esPlatillo = esPlatillo, esKit = esKit,
        esInsumo = false, manejaInventario = manejaInventario, colorBoton = null, idPuntoImpresion = null,
        tasaIEPS = tasaIeps, exento = exento, precioIncluyeImpuesto = precioIncluyeImpuesto,
        iepsTipoFactor = iepsTipoFactor.ifBlank { null }, iepsCuota = 0.0, tasaIva = tasaIva,
        imagenBase64 = null, tieneImagen = tieneImagen
    )

    private fun PuntoImpresionDto.toPuntoImpresion() = PuntoImpresion(
        idPuntoImpresion = idPuntoImpresion, nombre = nombre, impresora = impresora ?: "", ancho = ancho,
        copias = copias, imprimirAlEnviar = imprimirAlEnviar, activo = activo, categorias = categorias
    )
}

// DTOs de ventas — shapes REALES verificados contra MapiPOS.Api (Historial.cs).
@Serializable
private data class VentaDiaDto(   // DocHistorialDto
    val idVenta: Int = 0, val folio: String = "", val hora: String = "", val cliente: String = "",
    val total: Double = 0.0, val saldoPendiente: Double = 0.0, val cancelada: Boolean = false
)

@Serializable
private data class VentaDetalleDto(   // TicketDetalleDto
    val idVenta: Int = 0, val folio: String = "", val fecha: String = "", val cliente: String = "",
    val rfc: String = "", val usuario: String = "", val subtotal: Double = 0.0,
    val descuento: Double = 0.0, val iva: Double = 0.0, val total: Double = 0.0,
    val pagado: Double = 0.0, val cambio: Double = 0.0, val saldoPendiente: Double = 0.0,
    val cancelada: Boolean = false,
    val lineas: List<VentaLineaDto> = emptyList(),
    val pagos: List<VentaPagoDto> = emptyList()
)

@Serializable
private data class VentaLineaDto(   // TicketLineaDto: el importe de línea es `importe`
    val cantidad: Double = 0.0, val nombre: String = "",
    val precioUnitario: Double = 0.0, val importe: Double = 0.0
)

@Serializable
private data class VentaPagoDto(val forma: String = "", val monto: Double = 0.0)

@Serializable
private data class EmpresaConfigDto(
    val empresa: String = "", val rfc: String = "", val telefono: String = "",
    val encabezado: String = "", val pie: String = ""
)

@Serializable
private data class ClienteApiDto(
    val idCliente: Int = 0, val clave: String? = null, val nombre: String = "",
    val rfc: String? = null, val telefono: String? = null,
    val diasCredito: Int = 0, val limiteCredito: Double = 0.0, val saldoActual: Double = 0.0
)
