package com.example.mapicomandas.ui.screens.cobro

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.data.model.*
import com.example.mapicomandas.data.repository.RestauranteRepository
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
    // División de cuenta
    val modoDivision: ModoDivision = ModoDivision.NINGUNO,
    val partesDivision: Int = 1,
    val lineasDivision: Map<Int, List<LineaComanda>> = emptyMap()
)

enum class ModoDivision { NINGUNO, PARTES_IGUALES, POR_LUGAR, POR_IMPORTE }

@HiltViewModel
class CobroViewModel @Inject constructor(
    private val repo: RestauranteRepository,
    val session: SessionManager,
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

    fun setPropina(propina: Double) {
        val total = (_uiState.value.comanda?.total ?: 0.0) + propina
        val totalPagado = _uiState.value.totalPagado
        _uiState.value = _uiState.value.copy(
            propinaIngresada = propina,
            cambio = maxOf(0.0, totalPagado - total)
        )
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
                    idCliente = 1,
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
                    cobrado = true
                )
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message)
            }
        }
    }

    fun setModoDivision(modo: ModoDivision) {
        _uiState.value = _uiState.value.copy(modoDivision = modo)
        if (modo == ModoDivision.POR_LUGAR) {
            organizarPorLugar()
        }
    }

    private fun organizarPorLugar() {
        val agrupado = _uiState.value.lineas
            .filter { it.status != StatusLinea.CANCELADO }
            .groupBy { it.numLugar }
        _uiState.value = _uiState.value.copy(lineasDivision = agrupado)
    }

    fun limpiarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
