package com.example.mapicomandas.ui.screens.caja

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.data.model.MovimientoCaja
import com.example.mapicomandas.data.model.ResumenCaja
import com.example.mapicomandas.data.repository.RestauranteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CajaUiState(
    val resumen: ResumenCaja? = null,
    val cargando: Boolean = false,
    val error: String? = null,
    val exito: String? = null,
    val mostrarMovimiento: Boolean = false,
    val mostrarCorteZ: Boolean = false
)

@HiltViewModel
class CajaViewModel @Inject constructor(
    private val repo: RestauranteRepository,
    val session: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CajaUiState())
    val uiState: StateFlow<CajaUiState> = _uiState

    init {
        cargarResumen()
    }

    fun cargarResumen() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true)
            try {
                val resumen = repo.obtenerResumenCaja(session.idCaja, session.idTienda)
                _uiState.value = _uiState.value.copy(resumen = resumen, cargando = false, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message)
            }
        }
    }

    fun habilitarCaja() {
        viewModelScope.launch {
            try {
                repo.habilitarCaja(session.idCaja, session.idUsuario)
                session.setCajaHabilitada(true)
                _uiState.value = _uiState.value.copy(exito = "Caja habilitada")
                cargarResumen()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun registrarMovimiento(tipo: String, concepto: String, importe: Double) {
        viewModelScope.launch {
            try {
                repo.registrarMovimientoCaja(
                    MovimientoCaja(
                        tipo = tipo,
                        concepto = concepto,
                        importe = importe,
                        idCaja = session.idCaja,
                        idUsuario = session.idUsuario
                    )
                )
                _uiState.value = _uiState.value.copy(
                    mostrarMovimiento = false,
                    exito = "Movimiento registrado"
                )
                cargarResumen()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun realizarCorteZ() {
        viewModelScope.launch {
            try {
                repo.realizarCorteZ(session.idCaja, session.idUsuario)
                _uiState.value = _uiState.value.copy(
                    mostrarCorteZ = false,
                    exito = "Corte Z realizado"
                )
                cargarResumen()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun setMostrarMovimiento(mostrar: Boolean) {
        _uiState.value = _uiState.value.copy(mostrarMovimiento = mostrar)
    }

    fun setMostrarCorteZ(mostrar: Boolean) {
        _uiState.value = _uiState.value.copy(mostrarCorteZ = mostrar)
    }

    fun limpiarMensajes() {
        _uiState.value = _uiState.value.copy(error = null, exito = null)
    }
}
