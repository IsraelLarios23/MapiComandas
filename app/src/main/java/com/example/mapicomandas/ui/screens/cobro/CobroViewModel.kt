package com.example.mapicomandas.ui.screens.cobro

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.data.model.*
import com.example.mapicomandas.data.ConfigService
import com.example.mapicomandas.data.netpay.NetPayService
import com.example.mapicomandas.data.repository.RestauranteRepository
import com.example.mapicomandas.util.PrinterService
import com.example.mapicomandas.util.TicketData
import com.example.mapicomandas.util.TicketFormatter
import com.example.mapicomandas.util.TicketRenglon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CobroUiState(
    val comanda: MaestroComanda? = null,
    val lineas: List<LineaComanda> = emptyList(),
    val formasPago: List<FormaPago> = emptyList(),
    val pagos: MutableList<PagoVenta> = mutableListOf(),
    val propinaSugerida: Double = 0.0,
    val propinaIngresada: Double = 0.0,
    val totalPagado: Double = 0.0,
    val cambio: Double = 0.0,
    val idVentaGenerada: Int? = null,
    val cargando: Boolean = false,
    val error: String? = null,
    val cobrado: Boolean = false,
    // Ticket / finalizar
    val ticketLineas: List<String> = emptyList(),
    val imprimiendo: Boolean = false,
    val mensajeImpresion: String? = null,
    val finalizado: Boolean = false,
    val nuevaComandaFastFood: Int? = null,   // id de la nueva comanda en modo fast food
    val procesandoNetPay: Boolean = false,
    val mensajeNetPay: String? = null,
    val ultimoNetPay: com.example.mapicomandas.data.netpay.NetPayResultado? = null,
    val netPayReintentarFolio: String? = null,   // no-null → ofrecer reimpresión por folio
    // Cliente de la venta (crédito/lealtad — "★ Cliente" del desktop)
    val clienteSeleccionado: com.example.mapicomandas.data.model.ClienteLite? = null,
    val clientesEncontrados: List<com.example.mapicomandas.data.model.ClienteLite> = emptyList(),
    val buscandoCliente: Boolean = false,
    val mostrarBuscarCliente: Boolean = false,
    // División de cuenta (paridad FrmDividirCuenta: iguales / por importe / por lugar;
    // las sub-cuentas viven en memoria y al final se cierra UNA sola venta, como el desktop)
    val modoDivision: ModoDivision = ModoDivision.NINGUNO,
    val partesDivision: Int = 1,
    val lineasDivision: Map<Int, List<LineaComanda>> = emptyMap(),
    val subCuentas: List<SubCuentaUi> = emptyList(),
    val parteSeleccionada: Int? = null,
    val importesPersonalizados: List<Double> = emptyList()
)

enum class ModoDivision { NINGUNO, PARTES_IGUALES, POR_LUGAR, POR_IMPORTE }

/** Sub-cuenta en memoria: etiqueta, total de la parte, líneas (solo por-lugar) y lo pagado. */
data class SubCuentaUi(
    val etiqueta: String,
    val total: Double,
    val idsDetalle: List<Int> = emptyList(),
    val compartido: Double = 0.0,   // prorrateo del lugar 0 en modo por-lugar
    val pagado: Double = 0.0
) {
    val restante: Double get() = (total - pagado).coerceAtLeast(0.0)
    val cubierta: Boolean get() = restante < 0.01
}

@HiltViewModel
class CobroViewModel @Inject constructor(
    private val repo: RestauranteRepository,
    val session: SessionManager,
    private val printerService: PrinterService,
    private val configService: ConfigService,
    private val netPayService: NetPayService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val idComanda: Int = checkNotNull(savedStateHandle["idComanda"])

    private val _uiState = MutableStateFlow(CobroUiState())
    val uiState: StateFlow<CobroUiState> = _uiState

    init {
        cargarDatos()
    }

    private fun cargarDatos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true)
            try {
                val comanda = repo.obtenerComanda(idComanda)
                val lineas = repo.obtenerDetalle(idComanda)
                val formasPago = repo.obtenerFormasPago()
                val propina = repo.calcularPropinaSugerida(idComanda)
                _uiState.value = _uiState.value.copy(
                    comanda = comanda, lineas = lineas, formasPago = formasPago,
                    propinaSugerida = propina, propinaIngresada = propina,
                    cargando = false, error = null
                )
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message)
            }
        }
    }

    fun agregarPago(formaPago: FormaPago, importe: Double) {
        if (importe <= 0.0) return
        val pagos = _uiState.value.pagos.toMutableList()
        val existente = pagos.indexOfFirst { it.idFormaPago == formaPago.idFormaPago }
        if (existente >= 0) {
            // Combina con el pago existente de la misma forma
            pagos[existente] = pagos[existente].copy(importe = pagos[existente].importe + importe)
        } else {
            pagos.add(PagoVenta(formaPago.idFormaPago, formaPago.nombre, importe))
        }
        recalcularPagos(pagos)
        registrarPagoEnParte(formaPago.idFormaPago, importe)
    }

    private var netPayJob: kotlinx.coroutines.Job? = null
    // Últimos datos del intento NetPay, para recuperación por folio
    private var netPayFormaPago: FormaPago? = null
    private var netPayMonto: Double = 0.0

    /** Cobra el [monto] con la terminal NetPay; al aprobar, registra el pago. */
    fun cobrarConNetPay(formaPago: FormaPago, monto: Double) {
        if (monto <= 0.0) return
        netPayFormaPago = formaPago
        netPayMonto = monto
        val folio = _uiState.value.comanda?.folio
        netPayJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                procesandoNetPay = true,
                mensajeNetPay = "Iniciando…",
                netPayReintentarFolio = null
            )
            val res = try {
                netPayService.cobrar(monto, folio) { msg ->
                    _uiState.value = _uiState.value.copy(mensajeNetPay = msg)
                }
            } catch (c: kotlinx.coroutines.CancellationException) {
                com.example.mapicomandas.data.netpay.NetPayResultado(false, "CANCELADA", "", mensaje = "Cobro cancelado")
            } catch (e: Throwable) {
                com.example.mapicomandas.data.netpay.NetPayResultado(false, "ERROR", "", mensaje = e.message)
            }
            aplicarResultadoNetPay(formaPago, monto, res, folio)
        }
    }

    /**
     * Recupera el resultado de un cobro que no llegó (timeout) pidiendo una
     * reimpresión por folio; el JSON re-entregado cae en el receptor embebido.
     */
    fun reintentarNetPayPorFolio() {
        val folio = _uiState.value.netPayReintentarFolio ?: return
        val formaPago = netPayFormaPago ?: return
        val monto = netPayMonto
        netPayJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                procesandoNetPay = true,
                mensajeNetPay = "Solicitando reimpresión por folio…",
                netPayReintentarFolio = null
            )
            val res = try {
                netPayService.recuperarPorFolio(folio)
            } catch (c: kotlinx.coroutines.CancellationException) {
                com.example.mapicomandas.data.netpay.NetPayResultado(false, "CANCELADA", "", mensaje = "Reintento cancelado")
            } catch (e: Throwable) {
                com.example.mapicomandas.data.netpay.NetPayResultado(false, "ERROR", "", mensaje = e.message)
            }
            aplicarResultadoNetPay(formaPago, monto, res, folio)
        }
    }

    fun descartarReintentoNetPay() {
        _uiState.value = _uiState.value.copy(netPayReintentarFolio = null)
    }

    /** Registra el pago si se aprobó; si fue timeout, ofrece recuperar por folio. */
    private fun aplicarResultadoNetPay(
        formaPago: FormaPago, monto: Double,
        res: com.example.mapicomandas.data.netpay.NetPayResultado,
        folio: String?
    ) {
        if (res.aprobada) {
            val tarjeta = listOfNotNull(res.marca, res.ultimos4?.let { "****$it" })
                .joinToString(" ").ifBlank { "" }
            val ref = res.authCode ?: res.orderId ?: ""
            val pagos = _uiState.value.pagos.toMutableList()
            pagos.add(
                PagoVenta(
                    idFormaPago = formaPago.idFormaPago,
                    nombreFormaPago = formaPago.nombre + if (tarjeta.isNotBlank()) " ($tarjeta)" else "",
                    importe = monto,
                    referencia = ref
                )
            )
            recalcularPagos(pagos)
            registrarPagoEnParte(formaPago.idFormaPago, monto)
            _uiState.value = _uiState.value.copy(
                procesandoNetPay = false,
                mensajeNetPay = "Pago aprobado · auth ${res.authCode ?: "-"} $tarjeta".trim(),
                ultimoNetPay = res,
                netPayReintentarFolio = null
            )
            imprimirVoucherNetPay(res, "COMERCIO")
        } else if (res.estatus == "TIMEOUT" && !folio.isNullOrBlank()) {
            // Ofrecer recuperación por reimpresión (el cargo pudo haberse aprobado)
            _uiState.value = _uiState.value.copy(
                procesandoNetPay = false,
                mensajeNetPay = res.mensaje ?: "Sin respuesta de la terminal",
                netPayReintentarFolio = folio
            )
        } else {
            _uiState.value = _uiState.value.copy(
                procesandoNetPay = false,
                mensajeNetPay = "Pago no aprobado: ${res.mensaje ?: res.estatus}"
            )
        }
    }

    fun cancelarNetPay() {
        netPayJob?.cancel()
        _uiState.value = _uiState.value.copy(
            procesandoNetPay = false,
            mensajeNetPay = "Cobro con terminal cancelado"
        )
    }

    fun limpiarMensajeNetPay() {
        _uiState.value = _uiState.value.copy(mensajeNetPay = null)
    }

    /** Imprime el comprobante NetPay en la impresora de tickets configurada. */
    private fun imprimirVoucherNetPay(
        res: com.example.mapicomandas.data.netpay.NetPayResultado,
        copia: String
    ) {
        val impresora = session.impresoraTicket
        if (impresora.isBlank()) return
        viewModelScope.launch {
            val cfg = runCatching { netPayService.obtenerConfig() }.getOrNull()
            val lineas = com.example.mapicomandas.util.NetPayVoucher.construir(
                res = res,
                storeId = cfg?.storeId ?: "",
                serial = cfg?.serialNumber ?: "",
                fechaHora = fechaActual(),
                copia = copia
            )
            val error = printerService.imprimir(impresora, lineas)
            _uiState.value = _uiState.value.copy(
                mensajeNetPay = error ?: "Comprobante impreso ($copia)"
            )
        }
    }

    /** Reimprime el comprobante de la última transacción NetPay aprobada. */
    fun reimprimirVoucherNetPay(copia: String = "CLIENTE") {
        val res = _uiState.value.ultimoNetPay ?: return
        imprimirVoucherNetPay(res, copia)
    }

    /** Cancela (void) la última transacción NetPay del día vía orderId. */
    fun cancelarTransaccionNetPay() {
        val res = _uiState.value.ultimoNetPay ?: return
        val orderId = res.orderId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(procesandoNetPay = true, mensajeNetPay = "Cancelando transacción…")
            val ok = runCatching { netPayService.cancelar(orderId) }.getOrDefault(false)
            if (ok) {
                // Quita el pago con esa referencia y limpia el comprobante
                val pagos = _uiState.value.pagos.filterNot { it.referencia == (res.authCode ?: res.orderId) }.toMutableList()
                recalcularPagos(pagos)
                _uiState.value = _uiState.value.copy(
                    procesandoNetPay = false,
                    ultimoNetPay = null,
                    mensajeNetPay = "Transacción cancelada"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    procesandoNetPay = false,
                    mensajeNetPay = "No se pudo cancelar la transacción"
                )
            }
        }
    }

    fun editarPago(idFormaPago: Int, nuevoMonto: Double) {
        val pagos = _uiState.value.pagos.toMutableList()
        val idx = pagos.indexOfFirst { it.idFormaPago == idFormaPago }
        if (idx < 0) return
        if (nuevoMonto <= 0.0) {
            pagos.removeAt(idx)
        } else {
            pagos[idx] = pagos[idx].copy(importe = nuevoMonto)
        }
        recalcularPagos(pagos)
        // División: el registro por parte de esa forma se rehace con el nuevo monto
        quitarPagosDeParte(idFormaPago)
        if (nuevoMonto > 0.0) registrarPagoEnParte(idFormaPago, nuevoMonto)
    }

    private fun recalcularPagos(pagos: MutableList<PagoVenta>) {
        val totalPagado = pagos.sumOf { it.importe }
        val total = (_uiState.value.comanda?.total ?: 0.0) + _uiState.value.propinaIngresada
        _uiState.value = _uiState.value.copy(
            pagos = pagos,
            totalPagado = totalPagado,
            cambio = maxOf(0.0, totalPagado - total)
        )
    }

    fun quitarPago(idFormaPago: Int) {
        quitarPagosDeParte(idFormaPago)
        val pagos = _uiState.value.pagos.toMutableList()
        pagos.removeAll { it.idFormaPago == idFormaPago }
        val totalPagado = pagos.sumOf { it.importe }
        val total = (_uiState.value.comanda?.total ?: 0.0) + _uiState.value.propinaIngresada
        _uiState.value = _uiState.value.copy(
            pagos = pagos,
            totalPagado = totalPagado,
            cambio = maxOf(0.0, totalPagado - total)
        )
    }

    // ── Cliente de la venta (★ Cliente) ───────────────────────────────────────
    fun setMostrarBuscarCliente(mostrar: Boolean) {
        _uiState.value = _uiState.value.copy(mostrarBuscarCliente = mostrar, clientesEncontrados = emptyList())
    }

    fun buscarClientes(q: String) {
        if (q.length < 2) return
        _uiState.value = _uiState.value.copy(buscandoCliente = true)
        viewModelScope.launch {
            val res = runCatching { repo.obtenerClientes(q) }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(clientesEncontrados = res, buscandoCliente = false)
        }
    }

    fun seleccionarCliente(cliente: com.example.mapicomandas.data.model.ClienteLite?) {
        _uiState.value = _uiState.value.copy(
            clienteSeleccionado = cliente, mostrarBuscarCliente = false, clientesEncontrados = emptyList()
        )
    }

    fun setPropina(propina: Double) {
        val total = (_uiState.value.comanda?.total ?: 0.0) + propina
        val totalPagado = _uiState.value.totalPagado
        _uiState.value = _uiState.value.copy(
            propinaIngresada = propina,
            cambio = maxOf(0.0, totalPagado - total)
        )
        recomputarSubCuentas()
    }

    fun cobrar() {
        val state = _uiState.value
        val comanda = state.comanda ?: return
        val primerPago = state.pagos.firstOrNull() ?: return

        viewModelScope.launch {
            _uiState.value = state.copy(cargando = true)
            try {
                val idVenta = repo.cerrarComanda(
                    idComanda = idComanda,
                    idFormaPago = primerPago.idFormaPago,
                    // 0 = público en general; el server exige cliente solo si queda saldo (crédito)
                    idCliente = state.clienteSeleccionado?.idCliente ?: 0,
                    idUsuario = session.idUsuario,
                    idTienda = session.idTienda,
                    idCaja = session.idCaja,
                    idAlmacen = session.idAlmacen,
                    tasaIva = 0.16,
                    propina = state.propinaIngresada,
                    pagos = state.pagos.toList()
                )
                _uiState.value = _uiState.value.copy(
                    idVentaGenerada = idVenta,
                    cargando = false,
                    cobrado = true,
                    ticketLineas = construirTicket(idVenta)
                )
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message)
            }
        }
    }

    // Encabezado del negocio (empresa/RFC/leyendas), cargado una vez de GET /v1/configuracion
    private var empresaConfig: com.example.mapicomandas.data.model.EmpresaConfig? = null
    private suspend fun empresa(): com.example.mapicomandas.data.model.EmpresaConfig =
        empresaConfig ?: runCatching { repo.obtenerEmpresaConfig() }
            .getOrDefault(com.example.mapicomandas.data.model.EmpresaConfig())
            .also { empresaConfig = it }

    private suspend fun construirTicket(idVenta: Int): List<String> {
        val s = _uiState.value
        val comanda = s.comanda
        val emp = empresa()
        val renglones = s.lineas
            .filter { it.status != StatusLinea.CANCELADO }
            .map { TicketRenglon(it.cantidad, it.nombreArticulo, it.total) }
        val pagoTexto = s.pagos.joinToString(", ") { it.nombreFormaPago }
        val ticketPagos = s.pagos.map {
            com.example.mapicomandas.util.TicketPago(it.nombreFormaPago, it.importe, it.referencia)
        }
        val totalConPropina = (comanda?.total ?: 0.0) + s.propinaIngresada
        return TicketFormatter.construir(
            TicketData(
                empresa = emp.empresa + if (emp.rfc.isNotBlank()) "\nRFC: ${emp.rfc}" else "",
                header = emp.encabezado,
                footer = emp.pie,
                folio = "T-${comanda?.folio ?: idVenta}",
                fecha = fechaActual(),
                caja = session.idCaja.toString(),
                cajero = session.nombreUsuarioActual.ifBlank { session.idUsuario.toString() },
                renglones = renglones,
                subtotal = comanda?.subtotal ?: 0.0,
                descuento = comanda?.descuento ?: 0.0,
                impuesto = comanda?.iva ?: 0.0,
                total = totalConPropina,
                pagado = s.totalPagado,
                cambio = s.cambio,
                formaPago = pagoTexto,
                pagos = ticketPagos,
                observaciones = listOfNotNull(
                    s.clienteSeleccionado?.let { "Cliente: ${it.nombre}" },
                    if (s.propinaIngresada > 0)
                        "Propina: $${String.format(java.util.Locale.US, "%,.2f", s.propinaIngresada)}" else null
                ).joinToString("\n")
            )
        )
    }

    private fun fechaActual(): String =
        java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))

    /** Dispara la impresión del ticket y marca la venta como finalizada. */
    fun finalizar(imprimir: Boolean) {
        val lineas = _uiState.value.ticketLineas
        viewModelScope.launch {
            if (imprimir && session.impresoraTicket.isNotBlank()) {
                _uiState.value = _uiState.value.copy(imprimiendo = true)
                val error = printerService.imprimir(session.impresoraTicket, lineas)
                _uiState.value = _uiState.value.copy(
                    imprimiendo = false,
                    mensajeImpresion = error ?: "Ticket impreso"
                )
            }

            // Modo Comida Rápida: leído de ConfiguracionSistema (REST_COMIDA_RAPIDA),
            // con fallback al toggle local.
            val esParaLlevar = _uiState.value.comanda?.tipoServicio == TipoServicio.PARA_LLEVAR
            val comidaRapida = configService.bool("REST_COMIDA_RAPIDA", session.fastFoodActivo)
            val nuevaComanda = if (comidaRapida && esParaLlevar) {
                runCatching {
                    repo.abrirComandaSinMesa(
                        tipoServicio = TipoServicio.PARA_LLEVAR,
                        idMesero = session.idMesero,
                        idTienda = session.idTienda,
                        idCaja = session.idCaja
                    )
                }.getOrNull()
            } else null

            _uiState.value = _uiState.value.copy(
                finalizado = true,
                nuevaComandaFastFood = nuevaComanda
            )
        }
    }

    fun limpiarMensajeImpresion() {
        _uiState.value = _uiState.value.copy(mensajeImpresion = null)
    }

    // Registro (parteIdx, monto, idFormaPago) de cada pago aplicado a una sub-cuenta.
    private val registroPartes = mutableListOf<Triple<Int, Double, Int>>()

    fun setModoDivision(modo: ModoDivision) {
        val partes = when (modo) {
            ModoDivision.PARTES_IGUALES, ModoDivision.POR_IMPORTE ->
                _uiState.value.partesDivision.coerceAtLeast(2)
            else -> 1
        }
        _uiState.value = _uiState.value.copy(
            modoDivision = modo, partesDivision = partes, parteSeleccionada = null
        )
        registroPartes.clear()
        if (modo == ModoDivision.POR_LUGAR) organizarPorLugar()
        recomputarSubCuentas()
    }

    fun setPartesDivision(n: Int) {
        _uiState.value = _uiState.value.copy(partesDivision = n.coerceIn(2, 20))
        recomputarSubCuentas()
    }

    fun setImporteParte(idx: Int, monto: Double) {
        val s = _uiState.value
        val lista = s.importesPersonalizados.toMutableList()
        while (lista.size < s.partesDivision) lista.add(0.0)
        if (idx in lista.indices) lista[idx] = monto.coerceAtLeast(0.0)
        _uiState.value = s.copy(importesPersonalizados = lista)
        recomputarSubCuentas()
    }

    fun seleccionarParte(idx: Int?) {
        _uiState.value = _uiState.value.copy(parteSeleccionada = idx)
    }

    /** Monto sugerido para el pago: el restante de la parte seleccionada, o el faltante global. */
    fun montoPorParte(): Double {
        val s = _uiState.value
        val totalConPropina = (s.comanda?.total ?: 0.0) + s.propinaIngresada
        val parte = s.parteSeleccionada?.let { s.subCuentas.getOrNull(it) }
        return parte?.restante ?: run {
            val partes = s.partesDivision.coerceAtLeast(1)
            if (partes > 1) totalConPropina / partes else totalConPropina
        }
    }

    private fun organizarPorLugar() {
        val agrupado = _uiState.value.lineas
            .filter { it.status != StatusLinea.CANCELADO }
            .groupBy { it.numLugar }
        _uiState.value = _uiState.value.copy(lineasDivision = agrupado)
    }

    private fun r2(v: Double) = kotlin.math.round(v * 100.0) / 100.0

    /** Arma las sub-cuentas según el modo (residuos a la última, como el desktop). */
    private fun recomputarSubCuentas() {
        val s = _uiState.value
        val total = r2((s.comanda?.total ?: 0.0) + s.propinaIngresada)
        val pagadoPor = HashMap<Int, Double>()
        registroPartes.forEach { (idx, monto, _) -> pagadoPor[idx] = (pagadoPor[idx] ?: 0.0) + monto }

        val partes: List<SubCuentaUi> = when (s.modoDivision) {
            ModoDivision.NINGUNO -> emptyList()

            ModoDivision.PARTES_IGUALES -> {
                val n = s.partesDivision.coerceAtLeast(2)
                val base = r2(total / n)
                (0 until n).map { i ->
                    val monto = if (i == n - 1) r2(total - base * (n - 1)) else base
                    SubCuentaUi("Parte ${i + 1}", monto)
                }
            }

            ModoDivision.POR_IMPORTE -> {
                val n = s.partesDivision.coerceAtLeast(2)
                val base = r2(total / n)
                val importes = (0 until n).map { i ->
                    s.importesPersonalizados.getOrNull(i)
                        ?: if (i == n - 1) r2(total - base * (n - 1)) else base
                }
                importes.mapIndexed { i, m -> SubCuentaUi("Parte ${i + 1}", r2(m)) }
            }

            ModoDivision.POR_LUGAR -> {
                val lineas = s.lineas.filter { it.status != StatusLinea.CANCELADO }
                val porLugar = lineas.groupBy { it.numLugar }
                val lugares = porLugar.keys.filter { it > 0 }.sorted()
                if (lugares.isEmpty()) {
                    listOf(SubCuentaUi("Cuenta completa", total, lineas.map { it.idDetalleComanda }))
                } else {
                    // Lugar 0 = Compartido: se prorratea entre lugares (residuo al último)
                    val compartidoTotal = r2(porLugar[0].orEmpty().sumOf { it.total })
                    val cuota = r2(compartidoTotal / lugares.size)
                    lugares.mapIndexed { i, lugar ->
                        val propias = porLugar[lugar].orEmpty()
                        val share = if (i == lugares.size - 1)
                            r2(compartidoTotal - cuota * (lugares.size - 1)) else cuota
                        SubCuentaUi(
                            etiqueta = "Lugar $lugar",
                            total = r2(propias.sumOf { it.total } + share),
                            idsDetalle = propias.map { it.idDetalleComanda },
                            compartido = share
                        )
                    }
                }
            }
        }.map { it.copy() }

        val conPagos = partes.mapIndexed { i, p -> p.copy(pagado = r2(pagadoPor[i] ?: 0.0)) }
        _uiState.value = _uiState.value.copy(subCuentas = conPagos)
    }

    /** Registra un pago sobre la parte seleccionada y avanza a la siguiente sin cubrir. */
    private fun registrarPagoEnParte(idFormaPago: Int, monto: Double) {
        val s = _uiState.value
        if (s.modoDivision == ModoDivision.NINGUNO) return
        val idx = s.parteSeleccionada ?: return
        registroPartes.add(Triple(idx, monto, idFormaPago))
        recomputarSubCuentas()
        // auto-avanza a la siguiente parte con restante
        val siguiente = _uiState.value.subCuentas.withIndex()
            .firstOrNull { !it.value.cubierta }?.index
        _uiState.value = _uiState.value.copy(parteSeleccionada = siguiente)
    }

    private fun quitarPagosDeParte(idFormaPago: Int) {
        if (registroPartes.removeAll { it.third == idFormaPago }) recomputarSubCuentas()
    }

    /** Imprime la sub-cuenta (papel de cortesía) de una parte. */
    fun imprimirParte(idx: Int) {
        val s = _uiState.value
        val parte = s.subCuentas.getOrNull(idx) ?: return
        val impresora = session.impresoraTicket
        if (impresora.isBlank()) {
            _uiState.value = s.copy(mensajeImpresion = "Configura la impresora de tickets en Ajustes")
            return
        }
        viewModelScope.launch {
            val ancho = 40
            val l = mutableListOf<String>()
            l += "SUB-CUENTA  ${parte.etiqueta}".take(ancho)
            l += "Cuenta ${s.comanda?.folio ?: ""}  ·  ${fechaActual()}"
            l += "-".repeat(ancho)
            if (parte.idsDetalle.isNotEmpty()) {
                s.lineas.filter { it.idDetalleComanda in parte.idsDetalle }.forEach { ln ->
                    l += ("${ln.cantidad.toInt()} ${ln.nombreArticulo}").take(30).padEnd(30) +
                         String.format(java.util.Locale.US, "%10.2f", ln.total)
                }
                if (parte.compartido > 0.01)
                    l += "Compartido (proporcional)".padEnd(30) +
                         String.format(java.util.Locale.US, "%10.2f", parte.compartido)
            } else {
                l += "Parte proporcional de la cuenta"
            }
            l += "-".repeat(ancho)
            l += "TOTAL PARTE:".padEnd(30) + String.format(java.util.Locale.US, "%10.2f", parte.total)
            l += ""
            l += "*** NO ES COMPROBANTE FISCAL ***".take(ancho)
            val err = printerService.imprimir(impresora, l)
            _uiState.value = _uiState.value.copy(
                mensajeImpresion = err ?: "Sub-cuenta ${parte.etiqueta} impresa"
            )
        }
    }

    fun limpiarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
