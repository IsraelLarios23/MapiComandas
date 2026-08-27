package com.example.mapicomandas.ui.screens.comanda

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.data.model.*
import com.example.mapicomandas.data.repository.RestauranteRepository
import com.example.mapicomandas.util.ImpuestosCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ComandaUiState(
    val comanda: MaestroComanda? = null,
    val lineas: List<LineaComanda> = emptyList(),
    val categorias: List<Categoria> = emptyList(),
    val articulos: List<Articulo> = emptyList(),
    val categoriaSeleccionada: Int? = null,
    val busqueda: String = "",
    val lineaSeleccionada: LineaComanda? = null,
    val modificadoresDisponibles: List<Modificador> = emptyList(),
    val kitSlots: List<KitSlot> = emptyList(),
    val mostrarModificadores: Boolean = false,
    val mostrarKitSelector: Boolean = false,
    val mostrarDividir: Boolean = false,
    val mostrarNuevaLinea: Boolean = false,
    // ── Ajustes de partida y cuenta (cortesías/descuentos, API central) ──
    val motivosAjuste: List<com.example.mapicomandas.data.api.dto.MotivoAjusteDto> = emptyList(),
    val menuPartida: LineaComanda? = null,          // línea con el menú ⋮ abierto
    val dialogoAjusteTipo: Int? = null,             // 1 = cortesía, 2 = descuento
    val dialogoCorregir: Boolean = false,
    val dialogoDevolver: Boolean = false,
    val dialogoDividirPartes: Boolean = false,
    val dialogoTransferir: Boolean = false,
    val dialogoDescuentoCuenta: Boolean = false,
    val mesasTransferir: List<MesaUi> = emptyList(),
    val descuentoPreview: com.example.mapicomandas.data.api.dto.DescuentoCuentaPreviewDto? = null,
    val pideAutorizacion: Boolean = false,          // el motivo exige supervisor
    // ── Tiempo de mesa (P4) ──
    val relojActivo: com.example.mapicomandas.data.api.dto.RelojActivoDto? = null,
    val cobroTiempo: com.example.mapicomandas.data.api.dto.CobroTiempoDto? = null,
    val cargando: Boolean = false,
    val error: String? = null,
    val exito: String? = null
)

@HiltViewModel
class ComandaViewModel @Inject constructor(
    private val repo: RestauranteRepository,
    val session: SessionManager,
    private val impresionCocina: com.example.mapicomandas.data.ImpresionCocinaService,
    private val printerService: com.example.mapicomandas.util.PrinterService,
    private val ajustes: com.example.mapicomandas.data.api.AjustesService,
    private val operacion: com.example.mapicomandas.data.api.OperacionService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val idComanda: Int = checkNotNull(savedStateHandle["idComanda"])

    private val _uiState = MutableStateFlow(ComandaUiState())
    val uiState: StateFlow<ComandaUiState> = _uiState

    init {
        cargarComanda()
        cargarCatalogo()
    }

    fun cargarComanda() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true)
            try {
                val comanda = repo.obtenerComanda(idComanda)
                val lineas = repo.obtenerDetalle(idComanda)
                _uiState.value = _uiState.value.copy(
                    comanda = comanda, lineas = lineas, cargando = false, error = null
                )
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message)
            }
            refrescarReloj()
        }
    }

    // ── Menú por caja: ids permitidos (null = sin restricción, invariante desktop) ──
    private var menuCats: Set<Int>? = null
    private var menuArts: Set<Int>? = null

    private fun filtrarMenu(articulos: List<Articulo>): List<Articulo> =
        menuArts?.let { m -> articulos.filter { it.idArticulo in m } } ?: articulos

    private fun cargarCatalogo() {
        viewModelScope.launch {
            try {
                // Restricción de menú de ESTA caja (fallo abierto: sin config = todo)
                runCatching { operacion.menuCaja() }.getOrNull()?.let { menu ->
                    menuCats = menu.categorias.map { it.idCategoria }.toSet().ifEmpty { null }
                    menuArts = menu.articulos.map { it.idArticulo }.toSet().ifEmpty { null }
                }
                val categorias = repo.obtenerCategorias().let { cats ->
                    menuCats?.let { m -> cats.filter { it.idCategoria in m } } ?: cats
                }
                val articulos = filtrarMenu(repo.obtenerArticulos())
                _uiState.value = _uiState.value.copy(categorias = categorias, articulos = articulos)
                cargarImagenes(articulos)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun seleccionarCategoria(idCategoria: Int?) {
        _uiState.value = _uiState.value.copy(categoriaSeleccionada = idCategoria)
        viewModelScope.launch {
            try {
                val articulos = filtrarMenu(repo.obtenerArticulos(idCategoria = idCategoria))
                _uiState.value = _uiState.value.copy(articulos = articulos)
                cargarImagenes(articulos)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun buscarArticulo(query: String) {
        _uiState.value = _uiState.value.copy(busqueda = query)
        if (query.length >= 2) {
            viewModelScope.launch {
                try {
                    val articulos = filtrarMenu(repo.obtenerArticulos(nombre = query))
                    _uiState.value = _uiState.value.copy(articulos = articulos)
                    cargarImagenes(articulos)
                } catch (e: Throwable) {
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            }
        }
    }

    fun buscarPorClave(clave: String) {
        viewModelScope.launch {
            try {
                // 1º resolución EXACTA por código de barras (con oferta/promoción vigente
                // aplicada por el server), como el lector del desktop; 2º búsqueda por texto.
                val exacto = runCatching { repo.buscarArticuloPorCodigo(clave.trim()) }.getOrNull()
                if (exacto != null) {
                    agregarArticuloRapido(exacto, 1.0)
                    return@launch
                }
                val articulos = repo.obtenerArticulos(clave = clave)
                if (articulos.size == 1) {
                    agregarArticuloRapido(articulos[0], 1.0)
                } else {
                    _uiState.value = _uiState.value.copy(articulos = articulos)
                }
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    // Cache de detección de kit: la API no trae esKit en el artículo, así que se
    // consulta /articulos/{id}/kit una vez y se recuerda por id.
    private val esKitCache = HashMap<Int, Boolean>()

    // Fotos del catálogo (menú táctil): la API sirve JPEG binario por id; se cachean
    // en memoria ("" = sin imagen, para no reintentar el 404).
    private val imagenCache = HashMap<Int, String>()

    private fun cargarImagenes(articulos: List<Articulo>) {
        val pendientes = articulos.filter {
            it.tieneImagen && it.imagenBase64 == null && imagenCache[it.idArticulo] != ""
        }.take(40)
        if (pendientes.isEmpty()) return
        viewModelScope.launch {
            for (art in pendientes) {
                val b64 = imagenCache[art.idArticulo]?.takeIf { it.isNotEmpty() }
                    ?: runCatching { repo.obtenerImagenArticulo(art.idArticulo) }.getOrNull()
                        ?.let { bytes ->
                            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        }.also { imagenCache[art.idArticulo] = it ?: "" }
                if (b64.isNullOrEmpty()) continue
                _uiState.value = _uiState.value.copy(
                    articulos = _uiState.value.articulos.map {
                        if (it.idArticulo == art.idArticulo) it.copy(imagenBase64 = b64) else it
                    }
                )
            }
        }
    }

    fun seleccionarArticuloParaAgregar(articulo: Articulo) {
        if (articulo.idArticulo == 0) return
        viewModelScope.launch {
            try {
                val esKit = articulo.esKit || esKitCache[articulo.idArticulo] ?: run {
                    val slots = runCatching { repo.obtenerKitSlots(articulo.idArticulo) }
                        .getOrDefault(emptyList())
                    val kit = slots.isNotEmpty()
                    esKitCache[articulo.idArticulo] = kit
                    if (kit) {
                        _uiState.value = _uiState.value.copy(kitSlots = slots, mostrarKitSelector = true)
                        return@launch
                    }
                    false
                }
                if (esKit) cargarKitSlots(articulo) else cargarModificadores(articulo)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    private fun cargarKitSlots(articulo: Articulo) {
        viewModelScope.launch {
            try {
                val slots = repo.obtenerKitSlots(articulo.idArticulo)
                _uiState.value = _uiState.value.copy(
                    kitSlots = slots,
                    mostrarKitSelector = true
                )
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    private fun cargarModificadores(articulo: Articulo) {
        viewModelScope.launch {
            try {
                val mods = repo.obtenerModificadores(articulo.idArticulo)
                if (mods.isEmpty()) {
                    agregarArticuloRapido(articulo, 1.0)
                } else {
                    _uiState.value = _uiState.value.copy(
                        modificadoresDisponibles = mods,
                        mostrarModificadores = true
                    )
                }
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun agregarArticuloRapido(articulo: Articulo, cantidad: Double, notas: String = "") {
        viewModelScope.launch {
            try {
                val calc = ImpuestosCalculator.calcularConDouble(
                    cantidad = cantidad,
                    precioUnitario = articulo.precioVenta,
                    tasaIva = articulo.tasaIva,
                    iepsTipoFactor = articulo.iepsTipoFactor,
                    iepsValor = articulo.tasaIEPS,
                    precioIncluyeImpuesto = articulo.precioIncluyeImpuesto,
                    exento = articulo.exento
                )
                repo.agregarArticulo(
                    idComanda = idComanda,
                    idArticulo = articulo.idArticulo,
                    cantidad = cantidad,
                    precio = articulo.precioVenta,
                    tasaIva = articulo.tasaIva,
                    ieps = calc.ieps.toDouble(),
                    notas = notas,
                    mods = emptyList(),
                    componentesKit = null
                )
                cargarComanda()
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun agregarArticuloConMods(
        articulo: Articulo, cantidad: Double, notas: String,
        mods: List<ModificadorAplicado>
    ) {
        viewModelScope.launch {
            try {
                val precioExtra = mods.filter { it.tipo == TipoModificador.AGREGA_CON_COSTO }
                    .sumOf { it.precioExtra }
                val precioFinal = articulo.precioVenta + precioExtra
                val calc = ImpuestosCalculator.calcularConDouble(
                    cantidad = cantidad,
                    precioUnitario = precioFinal,
                    tasaIva = articulo.tasaIva,
                    iepsTipoFactor = articulo.iepsTipoFactor,
                    iepsValor = articulo.tasaIEPS,
                    precioIncluyeImpuesto = articulo.precioIncluyeImpuesto,
                    exento = articulo.exento
                )
                repo.agregarArticulo(
                    idComanda = idComanda,
                    idArticulo = articulo.idArticulo,
                    cantidad = cantidad,
                    precio = precioFinal,
                    tasaIva = articulo.tasaIva,
                    ieps = calc.ieps.toDouble(),
                    notas = notas,
                    mods = mods,
                    componentesKit = null
                )
                _uiState.value = _uiState.value.copy(mostrarModificadores = false)
                cargarComanda()
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun agregarKit(articulo: Articulo, cantidad: Double, componentes: List<ComponenteKit>, notas: String) {
        viewModelScope.launch {
            try {
                val calc = ImpuestosCalculator.calcularConDouble(
                    cantidad = cantidad,
                    precioUnitario = articulo.precioVenta,
                    tasaIva = articulo.tasaIva,
                    exento = articulo.exento
                )
                repo.agregarArticulo(
                    idComanda = idComanda,
                    idArticulo = articulo.idArticulo,
                    cantidad = cantidad,
                    precio = articulo.precioVenta,
                    tasaIva = articulo.tasaIva,
                    ieps = calc.ieps.toDouble(),
                    notas = notas,
                    mods = emptyList(),
                    componentesKit = componentes
                )
                _uiState.value = _uiState.value.copy(mostrarKitSelector = false)
                cargarComanda()
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun cancelarLinea(idDetalle: Int) {
        viewModelScope.launch {
            try {
                repo.cancelarLinea(idDetalle)
                cargarComanda()
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun enviarACocina() {
        viewModelScope.launch {
            try {
                // 1. Imprime las líneas PENDIENTES (Status=1) en sus puntos — determinístico,
                //    antes de marcar, para no depender de una ventana de tiempo.
                val resumen = runCatching {
                    impresionCocina.imprimir(idComanda, soloRecienEnviadas = false, todasLasLineas = false)
                }.getOrElse { listOf("Impresión: ${it.message}") }
                // 2. Marca como enviadas a cocina (Status=2, FechaEnvio)
                repo.enviarACocina(idComanda)
                cargarComanda()
                _uiState.value = _uiState.value.copy(exito = "Enviado a cocina · ${resumen.joinToString(" | ")}")
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun imprimirComanda() {
        viewModelScope.launch {
            try {
                // Reimpresión de cocina: /tickets-cocina solo trae PENDIENTES, así que tras
                // enviar regresa vacío. Respaldo: comanda completa a la impresora local.
                val resumen = impresionCocina.imprimir(idComanda, soloRecienEnviadas = false, todasLasLineas = true)
                val nadaImpreso = resumen.isEmpty() || resumen.all { it.contains("Sin puntos", true) }
                if (nadaImpreso) {
                    imprimirPreCuenta(titulo = "REIMPRESIÓN COMANDA")
                } else {
                    _uiState.value = _uiState.value.copy(exito = resumen.joinToString(" | "))
                }
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /** Pre-cuenta (papel no fiscal antes de cobrar), como "Cuenta" del desktop. */
    fun imprimirPreCuenta(titulo: String = "CUENTA") {
        viewModelScope.launch {
            val impresora = session.impresoraTicket
            if (impresora.isBlank()) {
                _uiState.value = _uiState.value.copy(error = "Configura la impresora de tickets en Ajustes")
                return@launch
            }
            try {
                val s = _uiState.value
                val ancho = 40
                val l = mutableListOf<String>()
                l += titulo
                l += "Cuenta ${s.comanda?.folio ?: idComanda}"
                s.comanda?.idMesa?.let { l += "Mesa $it   Personas: ${s.comanda?.numPersonas ?: 1}" }
                l += "-".repeat(ancho)
                s.lineas.filter { it.status != StatusLinea.CANCELADO }.forEach { ln ->
                    l += ("${ln.cantidad.toInt()} ${ln.nombreArticulo}").take(30).padEnd(30) +
                         String.format(java.util.Locale.US, "%10.2f", ln.total)
                    if (ln.notas.isNotBlank()) l += "   → ${ln.notas}".take(ancho)
                }
                l += "-".repeat(ancho)
                l += "TOTAL:".padEnd(30) +
                     String.format(java.util.Locale.US, "%10.2f", s.comanda?.total ?: 0.0)
                l += ""
                l += "*** NO ES COMPROBANTE FISCAL ***"
                val err = printerService.imprimir(impresora, l)
                _uiState.value = _uiState.value.copy(exito = err ?: "Cuenta impresa")
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun separarCantidad(idDetalle: Int, cantidadMover: Double, nuevoLugar: Int) {
        viewModelScope.launch {
            try {
                repo.separarCantidad(idDetalle, cantidadMover, nuevoLugar)
                _uiState.value = _uiState.value.copy(mostrarDividir = false)
                cargarComanda()
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /** Cancela toda la comanda tras autorización de supervisor. */
    fun cancelarComanda(usuario: String, password: String, onCancelada: () -> Unit) {
        viewModelScope.launch {
            try {
                if (!repo.autorizarSupervisor(usuario, password)) {
                    _uiState.value = _uiState.value.copy(error = "Autorización inválida")
                    return@launch
                }
                repo.cancelarComanda(idComanda)
                _uiState.value = _uiState.value.copy(exito = "Comanda cancelada")
                onCancelada()
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun setLineaSeleccionada(linea: LineaComanda?) {
        _uiState.value = _uiState.value.copy(lineaSeleccionada = linea)
    }

    fun setMostrarDividir(mostrar: Boolean) {
        _uiState.value = _uiState.value.copy(mostrarDividir = mostrar)
    }

    fun setMostrarModificadores(mostrar: Boolean) {
        _uiState.value = _uiState.value.copy(mostrarModificadores = mostrar)
    }

    fun setMostrarKitSelector(mostrar: Boolean) {
        _uiState.value = _uiState.value.copy(mostrarKitSelector = mostrar)
    }

    fun limpiarMensajes() {
        _uiState.value = _uiState.value.copy(error = null, exito = null)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Ajustes de partida y cuenta (cortesía / descuento / corregir / devolver
    //  / dividir / transferir) — API central, con candado de supervisor cuando
    //  el motivo lo exige.
    // ═══════════════════════════════════════════════════════════════════════

    /** Acción diferida a que el supervisor autorice (motivo.requiereAutorizacion). */
    private var accionPendiente: (() -> Unit)? = null

    private suspend fun cargarMotivosSiFaltan() {
        if (_uiState.value.motivosAjuste.isEmpty()) {
            val lista = runCatching { ajustes.motivos() }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(motivosAjuste = lista)
        }
    }

    fun abrirMenuPartida(linea: LineaComanda) {
        _uiState.value = _uiState.value.copy(menuPartida = linea, lineaSeleccionada = linea)
    }

    fun cerrarDialogosAjuste() {
        accionPendiente = null
        _uiState.value = _uiState.value.copy(
            menuPartida = null, dialogoAjusteTipo = null, dialogoCorregir = false,
            dialogoDevolver = false, dialogoDividirPartes = false, dialogoTransferir = false,
            dialogoDescuentoCuenta = false, descuentoPreview = null, pideAutorizacion = false
        )
    }

    fun abrirDialogoAjuste(tipo: Int) {
        viewModelScope.launch {
            cargarMotivosSiFaltan()
            _uiState.value = _uiState.value.copy(menuPartida = null, dialogoAjusteTipo = tipo)
        }
    }

    fun abrirDialogoCorregir() {
        viewModelScope.launch {
            cargarMotivosSiFaltan()
            _uiState.value = _uiState.value.copy(menuPartida = null, dialogoCorregir = true)
        }
    }

    fun abrirDialogoDevolver() {
        viewModelScope.launch {
            cargarMotivosSiFaltan()
            _uiState.value = _uiState.value.copy(menuPartida = null, dialogoDevolver = true)
        }
    }

    fun abrirDialogoDividirPartes() {
        _uiState.value = _uiState.value.copy(menuPartida = null, dialogoDividirPartes = true)
    }

    fun abrirDialogoTransferir() {
        viewModelScope.launch {
            val mesas = runCatching { repo.obtenerMesas(null) }.getOrDefault(emptyList())
                .filter { it.idMesa != _uiState.value.comanda?.idMesa }
            _uiState.value = _uiState.value.copy(menuPartida = null, dialogoTransferir = true,
                mesasTransferir = mesas)
        }
    }

    fun abrirDialogoDescuentoCuenta() {
        viewModelScope.launch {
            cargarMotivosSiFaltan()
            _uiState.value = _uiState.value.copy(dialogoDescuentoCuenta = true, descuentoPreview = null)
        }
    }

    /** Corre [accion] directo, o la deja pendiente de supervisor si el motivo lo exige. */
    private fun conCandado(idMotivo: Int, accion: () -> Unit) {
        val motivo = _uiState.value.motivosAjuste.find { it.idMotivo == idMotivo }
        if (motivo?.requiereAutorizacion == true) {
            accionPendiente = accion
            _uiState.value = _uiState.value.copy(pideAutorizacion = true)
        } else accion()
    }

    /** El supervisor tecleó sus credenciales para la acción pendiente. */
    fun autorizarAccionPendiente(usuario: String, password: String) {
        viewModelScope.launch {
            try {
                if (!repo.autorizarSupervisor(usuario, password)) {
                    _uiState.value = _uiState.value.copy(error = "Autorización inválida")
                    return@launch
                }
                _uiState.value = _uiState.value.copy(pideAutorizacion = false)
                accionPendiente?.invoke()
                accionPendiente = null
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun cancelarAutorizacion() {
        accionPendiente = null
        _uiState.value = _uiState.value.copy(pideAutorizacion = false)
    }

    fun aplicarAjustePartida(tipo: Int, idMotivo: Int, porcentaje: Double?, importe: Double?, nota: String?) {
        val linea = _uiState.value.lineaSeleccionada ?: return
        conCandado(idMotivo) {
            viewModelScope.launch {
                try {
                    val r = ajustes.aplicarAjuste(linea.idDetalleComanda, tipo, idMotivo, porcentaje, importe, nota)
                    cerrarDialogosAjuste()
                    cargarComanda()
                    _uiState.value = _uiState.value.copy(
                        exito = r.aviso.ifBlank { if (tipo == 1) "Cortesía aplicada" else "Descuento aplicado" }
                    )
                } catch (e: Throwable) {
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            }
        }
    }

    fun quitarAjustePartida() {
        val linea = _uiState.value.menuPartida ?: _uiState.value.lineaSeleccionada ?: return
        viewModelScope.launch {
            try {
                ajustes.quitarAjuste(linea.idDetalleComanda)
                cerrarDialogosAjuste()
                cargarComanda()
                _uiState.value = _uiState.value.copy(exito = "Ajuste retirado")
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun corregirPartida(cantidad: Double?, precio: Double?, idMotivo: Int) {
        val linea = _uiState.value.lineaSeleccionada ?: return
        conCandado(idMotivo) {
            viewModelScope.launch {
                try {
                    ajustes.corregirPartida(linea.idDetalleComanda, cantidad, precio, idMotivo)
                    cerrarDialogosAjuste()
                    cargarComanda()
                    _uiState.value = _uiState.value.copy(exito = "Partida corregida")
                } catch (e: Throwable) {
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            }
        }
    }

    fun devolverPartida(idMotivo: Int) {
        val linea = _uiState.value.lineaSeleccionada ?: return
        conCandado(idMotivo) {
            viewModelScope.launch {
                try {
                    ajustes.devolverPartida(linea.idDetalleComanda, idMotivo)
                    cerrarDialogosAjuste()
                    cargarComanda()
                    _uiState.value = _uiState.value.copy(exito = "Partida devuelta (merma registrada)")
                } catch (e: Throwable) {
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            }
        }
    }

    fun dividirPartidaEnPartes(partes: Int) {
        val linea = _uiState.value.lineaSeleccionada ?: return
        viewModelScope.launch {
            try {
                val r = ajustes.dividirPartida(linea.idDetalleComanda, partes)
                cerrarDialogosAjuste()
                cargarComanda()
                _uiState.value = _uiState.value.copy(exito = "Partida dividida en ${r.partes}")
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun transferirPartida(idMesaDestino: Int) {
        val linea = _uiState.value.lineaSeleccionada ?: return
        viewModelScope.launch {
            try {
                val r = ajustes.transferirPartidas(listOf(linea.idDetalleComanda), idMesaDestino)
                cerrarDialogosAjuste()
                cargarComanda()
                _uiState.value = _uiState.value.copy(
                    exito = "Movida a ${r.folioDestino} (mesa $idMesaDestino)"
                )
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun previewDescuentoCuenta(porcentaje: Double, idMotivo: Int) {
        viewModelScope.launch {
            try {
                val p = ajustes.descuentoPreview(idComanda, porcentaje, idMotivo)
                _uiState.value = _uiState.value.copy(descuentoPreview = p)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun aplicarDescuentoCuenta(porcentaje: Double, idMotivo: Int) {
        conCandado(idMotivo) {
            viewModelScope.launch {
                try {
                    val r = ajustes.aplicarDescuentoCuenta(idComanda, porcentaje, idMotivo)
                    cerrarDialogosAjuste()
                    cargarComanda()
                    _uiState.value = _uiState.value.copy(
                        exito = "Descuento del ${porcentaje}% aplicado (−$${String.format(java.util.Locale.US, "%.2f", r.importeDescuento)})"
                    )
                } catch (e: Throwable) {
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            }
        }
    }

    // ═══ Tiempo de mesa (billar/renta): reloj del servidor, cobro al detener ═══

    private fun refrescarReloj() {
        viewModelScope.launch {
            val reloj = runCatching { operacion.tiemposActivos() }.getOrDefault(emptyList())
                .find { it.idComanda == idComanda }
            _uiState.value = _uiState.value.copy(relojActivo = reloj)
        }
    }

    fun iniciarTiempo() {
        viewModelScope.launch {
            try {
                val p = operacion.iniciarTiempo(idComanda)
                _uiState.value = _uiState.value.copy(
                    exito = "⏱ Tiempo iniciado (${p.nombreTipo.ifBlank { "tarifa de la mesa" }})"
                )
                refrescarReloj()
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /** Detiene el reloj y asienta el renglón cobrable (o lo cierra sin cobro con motivo). */
    fun detenerTiempo(motivoSinCobro: String? = null) {
        viewModelScope.launch {
            try {
                val r = operacion.detenerTiempo(idComanda, motivoSinCobro)
                if (!r.detenido) {
                    _uiState.value = _uiState.value.copy(exito = "No había reloj corriendo", relojActivo = null)
                    return@launch
                }
                _uiState.value = _uiState.value.copy(cobroTiempo = r, relojActivo = null)
                cargarComanda()   // el renglón del tiempo ya está en la cuenta
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun cerrarDialogoTiempo() { _uiState.value = _uiState.value.copy(cobroTiempo = null) }

    /** "Terminar" del desktop: la mesa pasa a Cuenta Pedida (4) y ya no admite capturas. */
    fun marcarCuentaPedida() {
        val idMesa = _uiState.value.comanda?.idMesa ?: run {
            _uiState.value = _uiState.value.copy(error = "La comanda no tiene mesa")
            return
        }
        viewModelScope.launch {
            try {
                ajustes.cambiarStatusMesa(idMesa, 4)
                _uiState.value = _uiState.value.copy(exito = "Cuenta pedida — mesa bloqueada para capturar")
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
