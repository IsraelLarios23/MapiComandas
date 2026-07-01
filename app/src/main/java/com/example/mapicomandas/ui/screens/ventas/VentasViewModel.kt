package com.example.mapicomandas.ui.screens.ventas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.data.facturacion.DatosFactura
import com.example.mapicomandas.data.facturacion.FacturacionService
import com.example.mapicomandas.data.model.VentaDia
import com.example.mapicomandas.data.repository.RestauranteRepository
import com.example.mapicomandas.util.PrinterService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VentasUiState(
    val ventas: List<VentaDia> = emptyList(),
    val cargando: Boolean = false,
    val error: String? = null,
    val exito: String? = null,
    val ventaParaCancelar: VentaDia? = null,   // muestra diálogo de PIN
    val facturacionActiva: Boolean = false,
    val ventaParaFacturar: VentaDia? = null,    // muestra diálogo de receptor
    val facturando: Boolean = false
)

@HiltViewModel
class VentasViewModel @Inject constructor(
    private val repo: RestauranteRepository,
    private val printer: PrinterService,
    private val facturacion: FacturacionService,
    val session: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(VentasUiState())
    val uiState: StateFlow<VentasUiState> = _uiState

    init {
        cargar()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                facturacionActiva = runCatching { facturacion.facturacionHabilitada() }.getOrDefault(false)
            )
        }
    }

    fun cargar() {
        _uiState.value = _uiState.value.copy(cargando = true)
        viewModelScope.launch {
            try {
                val ventas = repo.obtenerVentasDia()
                _uiState.value = _uiState.value.copy(ventas = ventas, cargando = false, error = null)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message)
            }
        }
    }

    fun pedirFacturar(venta: VentaDia) { _uiState.value = _uiState.value.copy(ventaParaFacturar = venta) }
    fun cerrarFacturar() { _uiState.value = _uiState.value.copy(ventaParaFacturar = null) }

    fun facturar(venta: VentaDia, datos: DatosFactura) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(facturando = true)
            try {
                val res = facturacion.solicitarFactura(venta.idVenta, venta.folio, venta.total, datos)
                _uiState.value = _uiState.value.copy(
                    facturando = false,
                    ventaParaFacturar = null,
                    exito = if (res.timbrada || res.registradaLocal) res.mensaje else null,
                    error = if (!res.timbrada && !res.registradaLocal) res.mensaje else null
                )
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(facturando = false, error = e.message)
            }
        }
    }

    fun reimprimir(venta: VentaDia) {
        viewModelScope.launch {
            try {
                if (session.impresoraTicket.isBlank()) {
                    _uiState.value = _uiState.value.copy(error = "Configura la impresora de tickets en Ajustes")
                    return@launch
                }
                val lineas = repo.construirTicketVenta(venta.idVenta)
                val err = printer.imprimir(session.impresoraTicket, lineas, abrirCajon = false)
                _uiState.value = if (err == null)
                    _uiState.value.copy(exito = "Ticket ${venta.folio} reimpreso")
                else _uiState.value.copy(error = err)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun pedirCancelar(venta: VentaDia) { _uiState.value = _uiState.value.copy(ventaParaCancelar = venta) }
    fun cerrarCancelar() { _uiState.value = _uiState.value.copy(ventaParaCancelar = null) }

    fun confirmarCancelar(venta: VentaDia, usuario: String, password: String) {
        viewModelScope.launch {
            try {
                if (!repo.autorizarSupervisor(usuario, password)) {
                    _uiState.value = _uiState.value.copy(error = "Autorización inválida")
                    return@launch
                }
                repo.cancelarVenta(venta.idVenta)
                _uiState.value = _uiState.value.copy(ventaParaCancelar = null, exito = "Venta ${venta.folio} cancelada")
                cargar()
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun limpiarMensajes() { _uiState.value = _uiState.value.copy(error = null, exito = null) }
}
