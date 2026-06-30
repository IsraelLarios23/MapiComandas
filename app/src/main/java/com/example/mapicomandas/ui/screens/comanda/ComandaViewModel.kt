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
    val cargando: Boolean = false,
    val error: String? = null,
    val exito: String? = null
)

@HiltViewModel
class ComandaViewModel @Inject constructor(
    private val repo: RestauranteRepository,
    val session: SessionManager,
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
        }
    }

    private fun cargarCatalogo() {
        viewModelScope.launch {
            try {
                val categorias = repo.obtenerCategorias()
                val articulos = repo.obtenerArticulos()
                _uiState.value = _uiState.value.copy(categorias = categorias, articulos = articulos)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun seleccionarCategoria(idCategoria: Int?) {
        _uiState.value = _uiState.value.copy(categoriaSeleccionada = idCategoria)
        viewModelScope.launch {
            try {
                val articulos = repo.obtenerArticulos(idCategoria = idCategoria)
                _uiState.value = _uiState.value.copy(articulos = articulos)
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
                    val articulos = repo.obtenerArticulos(nombre = query)
                    _uiState.value = _uiState.value.copy(articulos = articulos)
                } catch (e: Throwable) {
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            }
        }
    }

    fun buscarPorClave(clave: String) {
        viewModelScope.launch {
            try {
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

    fun seleccionarArticuloParaAgregar(articulo: Articulo) {
        if (articulo.esKit) {
            cargarKitSlots(articulo)
        } else if (articulo.idArticulo != 0) {
            cargarModificadores(articulo)
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
                repo.enviarACocina(idComanda)
                cargarComanda()
                _uiState.value = _uiState.value.copy(exito = "Enviado a cocina")
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun imprimirComanda() {
        viewModelScope.launch {
            try {
                repo.imprimirComanda(idComanda, soloRecienEnviadas = false, todasLasLineas = true)
                _uiState.value = _uiState.value.copy(exito = "Impresión enviada")
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
}
