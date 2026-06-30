package com.example.mapicomandas.ui.screens.mesas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.data.model.Mesa
import com.example.mapicomandas.data.model.MesaUi
import com.example.mapicomandas.data.model.Mesero
import com.example.mapicomandas.data.repository.RestauranteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MesasUiState(
    val mesas: List<MesaUi> = emptyList(),
    val zonas: List<String> = emptyList(),
    val zonaSeleccionada: String? = null,
    val mesasLibres: List<Mesa> = emptyList(),
    val meseros: List<Mesero> = emptyList(),
    val mesaContextual: MesaUi? = null,
    val cargando: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MesasViewModel @Inject constructor(
    private val repo: RestauranteRepository,
    val session: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MesasUiState())
    val uiState: StateFlow<MesasUiState> = _uiState

    private var pollingJob: Job? = null

    init {
        cargarInicial()
        iniciarPolling()
    }

    private fun cargarInicial() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true)
            try {
                val zonas = repo.obtenerZonas()
                val meseros = repo.obtenerMeserosActivos()
                val mesas = repo.obtenerMesas(_uiState.value.zonaSeleccionada)
                _uiState.value = _uiState.value.copy(
                    mesas = mesas, zonas = zonas, meseros = meseros, cargando = false, error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message)
            }
        }
    }

    private fun iniciarPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                refrescarMesas()
            }
        }
    }

    fun refrescarMesas() {
        viewModelScope.launch {
            try {
                val mesas = repo.obtenerMesas(_uiState.value.zonaSeleccionada)
                _uiState.value = _uiState.value.copy(mesas = mesas, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun seleccionarZona(zona: String?) {
        _uiState.value = _uiState.value.copy(zonaSeleccionada = zona)
        viewModelScope.launch {
            try {
                val mesas = repo.obtenerMesas(zona)
                _uiState.value = _uiState.value.copy(mesas = mesas)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun setMesaContextual(mesa: MesaUi?) {
        _uiState.value = _uiState.value.copy(mesaContextual = mesa)
    }

    fun abrirComanda(idMesa: Int, idMesero: Int, numPersonas: Int, obs: String, onSuccess: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val idComanda = repo.abrirComanda(
                    idMesa = idMesa,
                    idMesero = idMesero,
                    numPersonas = numPersonas,
                    idTienda = session.idTienda,
                    idCaja = session.idCaja,
                    obs = obs
                )
                refrescarMesas()
                onSuccess(idComanda)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun cambiarMesero(idComanda: Int, idMeseroNuevo: Int) {
        viewModelScope.launch {
            try {
                repo.cambiarMesero(idComanda, idMeseroNuevo)
                refrescarMesas()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun cambiarMesa(idComanda: Int, idMesaActual: Int, idMesaNueva: Int) {
        viewModelScope.launch {
            try {
                repo.cambiarMesa(idComanda, idMesaActual, idMesaNueva)
                refrescarMesas()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun limpiarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
