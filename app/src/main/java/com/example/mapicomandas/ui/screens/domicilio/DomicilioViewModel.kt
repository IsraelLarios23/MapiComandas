package com.example.mapicomandas.ui.screens.domicilio

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

data class DomicilioUiState(
    val comandas: List<ComandaSinMesa> = emptyList(),
    val repartidores: List<Repartidor> = emptyList(),
    val zonas: List<ZonaReparto> = emptyList(),
    val cargando: Boolean = false,
    val error: String? = null,
    val exito: String? = null,
    val mostrarNuevoPedido: Boolean = false,
    val mostrarEditarRepartidores: Boolean = false,
    val mostrarEditarZonas: Boolean = false
)

@HiltViewModel
class DomicilioViewModel @Inject constructor(
    private val repo: RestauranteRepository,
    val session: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DomicilioUiState())
    val uiState: StateFlow<DomicilioUiState> = _uiState

    init {
        cargarDatos()
    }

    fun cargarDatos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true)
            try {
                val comandas = repo.obtenerComandasSinMesaAbiertas()
                val repartidores = repo.obtenerRepartidores()
                val zonas = repo.obtenerZonasReparto()
                _uiState.value = _uiState.value.copy(
                    comandas = comandas,
                    repartidores = repartidores,
                    zonas = zonas,
                    cargando = false,
                    error = null
                )
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message)
            }
        }
    }

    fun abrirNuevoPedido(
        tipoServicio: Int, cliente: String, tel: String, dir: String,
        idRepartidor: Int?, idZona: Int?, cargo: Double,
        onSuccess: (Int) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val id = repo.abrirComandaSinMesa(
                    tipoServicio = tipoServicio,
                    idMesero = session.idMesero,
                    idTienda = session.idTienda,
                    idCaja = session.idCaja,
                    cliente = cliente, tel = tel, dir = dir,
                    idRepartidor = idRepartidor, idZona = idZona, cargoEntrega = cargo
                )
                cargarDatos()
                _uiState.value = _uiState.value.copy(mostrarNuevoPedido = false)
                onSuccess(id)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun actualizarStatusEntrega(idComanda: Int, status: Int) {
        viewModelScope.launch {
            try {
                repo.actualizarStatusEntrega(idComanda, status)
                cargarDatos()
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun guardarRepartidor(id: Int, nombre: String, tel: String, activo: Boolean) {
        viewModelScope.launch {
            try {
                repo.guardarRepartidor(id, nombre, tel, activo)
                val repartidores = repo.obtenerRepartidores()
                _uiState.value = _uiState.value.copy(repartidores = repartidores, exito = "Repartidor guardado")
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun guardarZona(id: Int, nombre: String, cargo: Double, activo: Boolean) {
        viewModelScope.launch {
            try {
                repo.guardarZonaReparto(id, nombre, cargo, activo)
                val zonas = repo.obtenerZonasReparto()
                _uiState.value = _uiState.value.copy(zonas = zonas, exito = "Zona guardada")
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun setMostrarNuevoPedido(mostrar: Boolean) {
        _uiState.value = _uiState.value.copy(mostrarNuevoPedido = mostrar)
    }

    fun setMostrarEditarRepartidores(mostrar: Boolean) {
        _uiState.value = _uiState.value.copy(mostrarEditarRepartidores = mostrar)
    }

    fun setMostrarEditarZonas(mostrar: Boolean) {
        _uiState.value = _uiState.value.copy(mostrarEditarZonas = mostrar)
    }

    fun limpiarMensajes() {
        _uiState.value = _uiState.value.copy(error = null, exito = null)
    }
}
