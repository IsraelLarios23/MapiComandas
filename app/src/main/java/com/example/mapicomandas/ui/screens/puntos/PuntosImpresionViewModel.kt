package com.example.mapicomandas.ui.screens.puntos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.data.ImpresionCocinaService
import com.example.mapicomandas.data.model.Categoria
import com.example.mapicomandas.data.model.PuntoImpresion
import com.example.mapicomandas.data.repository.RestauranteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PuntosImpresionUiState(
    val puntos: List<PuntoImpresion> = emptyList(),
    val categorias: List<Categoria> = emptyList(),
    val cargando: Boolean = false,
    val error: String? = null,
    val exito: String? = null,
    val editando: PuntoImpresion? = null,       // punto en edición (o null)
    val asignandoCategorias: PuntoImpresion? = null
)

@HiltViewModel
class PuntosImpresionViewModel @Inject constructor(
    private val repo: RestauranteRepository,
    private val impresion: ImpresionCocinaService
) : ViewModel() {

    private val _uiState = MutableStateFlow(PuntosImpresionUiState())
    val uiState: StateFlow<PuntosImpresionUiState> = _uiState

    init { cargar() }

    fun cargar() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true)
            try {
                val puntos = repo.obtenerPuntosImpresion()
                val categorias = repo.obtenerCategorias()
                _uiState.value = _uiState.value.copy(
                    puntos = puntos, categorias = categorias, cargando = false, error = null
                )
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message)
            }
        }
    }

    fun nuevoPunto() {
        _uiState.value = _uiState.value.copy(
            editando = PuntoImpresion(0, "", "", 32, 1, imprimirAlEnviar = true, activo = true)
        )
    }

    fun editar(punto: PuntoImpresion) { _uiState.value = _uiState.value.copy(editando = punto) }
    fun cerrarEditor() { _uiState.value = _uiState.value.copy(editando = null) }

    fun guardar(punto: PuntoImpresion) {
        viewModelScope.launch {
            try {
                repo.guardarPuntoImpresion(punto)
                _uiState.value = _uiState.value.copy(editando = null, exito = "Punto guardado")
                cargar()
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun eliminar(punto: PuntoImpresion) {
        viewModelScope.launch {
            try {
                repo.eliminarPuntoImpresion(punto.idPuntoImpresion)
                _uiState.value = _uiState.value.copy(exito = "Punto eliminado")
                cargar()
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun abrirCategorias(punto: PuntoImpresion) {
        _uiState.value = _uiState.value.copy(asignandoCategorias = punto)
    }
    fun cerrarCategorias() { _uiState.value = _uiState.value.copy(asignandoCategorias = null) }

    fun guardarCategorias(idPunto: Int, categorias: List<Int>) {
        viewModelScope.launch {
            try {
                repo.asignarCategoriasPunto(idPunto, categorias)
                _uiState.value = _uiState.value.copy(asignandoCategorias = null, exito = "Categorías asignadas")
                cargar()
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun probar(punto: PuntoImpresion) {
        viewModelScope.launch {
            val err = impresion.probar(punto.impresora, punto.nombre, punto.ancho)
            _uiState.value = _uiState.value.copy(
                exito = if (err == null) "✓ Impresión de prueba enviada a ${punto.nombre}" else null,
                error = err
            )
        }
    }

    fun limpiarMensajes() { _uiState.value = _uiState.value.copy(error = null, exito = null) }
}
