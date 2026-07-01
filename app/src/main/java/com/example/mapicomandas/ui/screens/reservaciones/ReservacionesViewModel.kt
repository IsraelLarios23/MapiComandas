package com.example.mapicomandas.ui.screens.reservaciones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.data.model.Mesa
import com.example.mapicomandas.data.model.Reservacion
import com.example.mapicomandas.data.repository.RestauranteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ReservacionesUiState(
    val fecha: LocalDate = LocalDate.now(),
    val reservaciones: List<Reservacion> = emptyList(),
    val mesas: List<Mesa> = emptyList(),
    val cargando: Boolean = false,
    val error: String? = null,
    val mensaje: String? = null
)

@HiltViewModel
class ReservacionesViewModel @Inject constructor(
    private val repo: RestauranteRepository,
    private val session: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReservacionesUiState())
    val uiState: StateFlow<ReservacionesUiState> = _uiState

    init {
        cargarMesas()
        cargar()
    }

    private fun cargarMesas() {
        viewModelScope.launch {
            runCatching { repo.obtenerMesasLibres() }.getOrNull()?.let {
                // usa todas las mesas (libres + ocupadas) si existe; libres basta para el picker
                _uiState.value = _uiState.value.copy(mesas = it)
            }
        }
    }

    fun cargar() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true)
            try {
                val fecha = _uiState.value.fecha.toString()   // yyyy-MM-dd
                val lista = repo.obtenerReservaciones(fecha)
                _uiState.value = _uiState.value.copy(reservaciones = lista, cargando = false, error = null)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message)
            }
        }
    }

    fun cambiarFecha(dias: Long) {
        _uiState.value = _uiState.value.copy(fecha = _uiState.value.fecha.plusDays(dias))
        cargar()
    }

    fun guardar(
        id: Int, idMesa: Int, nombre: String, telefono: String,
        fechaHora: String, personas: Int, observaciones: String
    ) {
        viewModelScope.launch {
            try {
                repo.guardarReservacion(
                    id = id, idMesa = idMesa, nombre = nombre, telefono = telefono,
                    fechaHora = fechaHora, personas = personas, observaciones = observaciones,
                    idUsuario = session.idUsuario
                )
                _uiState.value = _uiState.value.copy(mensaje = "Reservación guardada")
                cargar()
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun cambiarStatus(idReservacion: Int, status: Int) {
        viewModelScope.launch {
            try {
                repo.cambiarStatusReservacion(idReservacion, status)
                cargar()
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun limpiarMensajes() {
        _uiState.value = _uiState.value.copy(error = null, mensaje = null)
    }
}
