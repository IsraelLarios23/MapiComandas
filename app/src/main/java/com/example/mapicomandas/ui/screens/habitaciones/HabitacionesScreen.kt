package com.example.mapicomandas.ui.screens.habitaciones

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapicomandas.data.api.CatalogosService
import com.example.mapicomandas.data.api.dto.EstanciaAbiertaDto
import com.example.mapicomandas.data.api.dto.HabitacionDto
import com.example.mapicomandas.data.model.ClienteLite
import com.example.mapicomandas.data.repository.RestauranteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HabitacionesUiState(
    val habitaciones: List<HabitacionDto> = emptyList(),
    val estancias: List<EstanciaAbiertaDto> = emptyList(),
    val cargando: Boolean = false,
    // check-in
    val habitacionCheckIn: HabitacionDto? = null,
    val clientesEncontrados: List<ClienteLite> = emptyList(),
    // alta/edición de habitación
    val habitacionEnEdicion: HabitacionDto? = null,
    val mostrarNuevaHabitacion: Boolean = false,
    // check-out
    val checkOutSaldo: Double? = null,
    val error: String? = null,
    val exito: String? = null
)

/** Habitaciones (hotel): estancias abiertas con cargos a la habitación, check-in/out. */
@HiltViewModel
class HabitacionesViewModel @Inject constructor(
    private val catalogos: CatalogosService,
    private val repo: RestauranteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitacionesUiState())
    val uiState: StateFlow<HabitacionesUiState> = _uiState

    init { cargar() }

    fun cargar() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true)
            try {
                val habs = catalogos.habitaciones()
                val ests = runCatching { catalogos.estanciasAbiertas() }.getOrDefault(emptyList())
                _uiState.value = _uiState.value.copy(
                    habitaciones = habs, estancias = ests, cargando = false, error = null
                )
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message)
            }
        }
    }

    // ── Check-in ────────────────────────────────────────────────────────────
    fun abrirCheckIn(h: HabitacionDto) {
        _uiState.value = _uiState.value.copy(habitacionCheckIn = h, clientesEncontrados = emptyList())
    }

    fun cerrarCheckIn() { _uiState.value = _uiState.value.copy(habitacionCheckIn = null) }

    fun buscarClientes(q: String) {
        if (q.length < 2) return
        viewModelScope.launch {
            val lista = runCatching { repo.obtenerClientes(q) }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(clientesEncontrados = lista.take(12))
        }
    }

    fun checkIn(idCliente: Int, nombreHuesped: String) {
        val h = _uiState.value.habitacionCheckIn ?: return
        viewModelScope.launch {
            try {
                val e = catalogos.checkIn(h.idHabitacion, idCliente, nombreHuesped)
                _uiState.value = _uiState.value.copy(
                    habitacionCheckIn = null,
                    exito = "Check-in en ${e.numero} — ${e.nombreHuesped}"
                )
                cargar()
            } catch (ex: Throwable) {
                _uiState.value = _uiState.value.copy(error = ex.message)
            }
        }
    }

    // ── Check-out ───────────────────────────────────────────────────────────
    fun checkOut(idEstancia: Int) {
        viewModelScope.launch {
            try {
                val r = catalogos.checkOut(idEstancia)
                _uiState.value = _uiState.value.copy(checkOutSaldo = r.saldoPendiente)
                cargar()
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun cerrarCheckOut() { _uiState.value = _uiState.value.copy(checkOutSaldo = null) }

    // ── Catálogo de habitaciones ────────────────────────────────────────────
    fun editarHabitacion(h: HabitacionDto?) {
        _uiState.value = _uiState.value.copy(habitacionEnEdicion = h, mostrarNuevaHabitacion = true)
    }

    fun cerrarEdicion() {
        _uiState.value = _uiState.value.copy(habitacionEnEdicion = null, mostrarNuevaHabitacion = false)
    }

    fun guardarHabitacion(numero: String, descripcion: String, activo: Boolean) {
        val id = _uiState.value.habitacionEnEdicion?.idHabitacion
        viewModelScope.launch {
            try {
                catalogos.guardarHabitacion(numero, descripcion, id, activo)
                _uiState.value = _uiState.value.copy(
                    habitacionEnEdicion = null, mostrarNuevaHabitacion = false, exito = "Habitación guardada"
                )
                cargar()
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun limpiarMensajes() { _uiState.value = _uiState.value.copy(error = null, exito = null) }
}

private fun dinero(v: Double) = "$" + String.format(java.util.Locale.US, "%,.2f", v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitacionesScreen(
    onVolver: () -> Unit,
    onIrHome: () -> Unit = {},
    viewModel: HabitacionesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.limpiarMensajes()
        }
    }
    LaunchedEffect(uiState.exito) {
        uiState.exito?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.limpiarMensajes()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Habitaciones", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = {
                    IconButton(onClick = { viewModel.cargar() }) { Icon(Icons.Default.Refresh, "Actualizar") }
                    IconButton(onClick = onIrHome) { Icon(Icons.Default.Home, "Inicio") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF00695C),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text("Estancias (${uiState.estancias.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Habitaciones") })
            }
            if (uiState.cargando) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            if (tab == 0) TabEstancias(uiState, viewModel) else TabHabitaciones(uiState, viewModel)
        }
    }

    // Check-in
    uiState.habitacionCheckIn?.let { h ->
        DialogoCheckIn(
            habitacion = h,
            clientes = uiState.clientesEncontrados,
            onBuscar = viewModel::buscarClientes,
            onConfirmar = { idCliente, nombre -> viewModel.checkIn(idCliente, nombre) },
            onDismiss = { viewModel.cerrarCheckIn() }
        )
    }

    // Resultado de check-out
    uiState.checkOutSaldo?.let { saldo ->
        AlertDialog(
            onDismissRequest = { viewModel.cerrarCheckOut() },
            title = { Text("Check-out realizado") },
            text = {
                if (saldo > 0)
                    Text("Saldo PENDIENTE de cobro: ${dinero(saldo)}\nCóbralo en caja antes de que se vaya el huésped.",
                        color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                else Text("Sin cargos pendientes. ✔")
            },
            confirmButton = { TextButton(onClick = { viewModel.cerrarCheckOut() }) { Text("Aceptar") } }
        )
    }

    // Alta/edición de habitación
    if (uiState.mostrarNuevaHabitacion) {
        DialogoHabitacion(
            habitacion = uiState.habitacionEnEdicion,
            onGuardar = { num, desc, act -> viewModel.guardarHabitacion(num, desc, act) },
            onDismiss = { viewModel.cerrarEdicion() }
        )
    }
}

@Composable
private fun TabEstancias(uiState: HabitacionesUiState, viewModel: HabitacionesViewModel) {
    if (uiState.estancias.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Sin huéspedes hospedados. Haz check-in desde la pestaña Habitaciones.",
                color = Color.Gray, fontSize = 13.sp)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        items(uiState.estancias, key = { it.idEstancia }) { e ->
            var confirmar by remember(e.idEstancia) { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Hotel, null, tint = Color(0xFF00695C))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Hab. ${e.numero} — ${e.nombreHuesped}", fontWeight = FontWeight.Bold)
                        Text("Entrada: ${e.fechaEntrada.take(16).replace('T', ' ')}",
                            fontSize = 11.sp, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(dinero(e.saldoActual), fontWeight = FontWeight.Bold,
                            color = if (e.saldoActual > 0) Color(0xFFC62828) else Color(0xFF2E7D32))
                        TextButton(onClick = { confirmar = true }) { Text("Check-out", fontSize = 12.sp) }
                    }
                }
            }
            if (confirmar) {
                AlertDialog(
                    onDismissRequest = { confirmar = false },
                    title = { Text("Check-out de ${e.nombreHuesped}") },
                    text = { Text("Habitación ${e.numero} · consumos por ${dinero(e.saldoActual)}.") },
                    confirmButton = {
                        Button(onClick = { confirmar = false; viewModel.checkOut(e.idEstancia) }) {
                            Text("Confirmar")
                        }
                    },
                    dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } }
                )
            }
        }
    }
}

@Composable
private fun TabHabitaciones(uiState: HabitacionesUiState, viewModel: HabitacionesViewModel) {
    val ocupadas = uiState.estancias.map { it.idHabitacion }.toSet()
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Button(onClick = { viewModel.editarHabitacion(null) }) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Nueva habitación")
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn {
            items(uiState.habitaciones, key = { it.idHabitacion }) { h ->
                val ocupada = h.idHabitacion in ocupadas
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MeetingRoom, null,
                        tint = when {
                            !h.activo -> Color.Gray
                            ocupada -> Color(0xFFC62828)
                            else -> Color(0xFF2E7D32)
                        })
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f).clickable { viewModel.editarHabitacion(h) }) {
                        Text("Hab. ${h.numero}" + if (!h.activo) "  (inactiva)" else "",
                            fontWeight = FontWeight.Medium,
                            color = if (h.activo) Color.Unspecified else Color.Gray)
                        if (h.descripcion.isNotBlank())
                            Text(h.descripcion, fontSize = 11.sp, color = Color.Gray)
                    }
                    if (ocupada) Text("Ocupada", fontSize = 12.sp, color = Color(0xFFC62828))
                    else if (h.activo) TextButton(onClick = { viewModel.abrirCheckIn(h) }) {
                        Text("Check-in", fontSize = 12.sp)
                    }
                }
                Divider()
            }
        }
    }
}

@Composable
private fun DialogoCheckIn(
    habitacion: HabitacionDto,
    clientes: List<ClienteLite>,
    onBuscar: (String) -> Unit,
    onConfirmar: (idCliente: Int, nombreHuesped: String) -> Unit,
    onDismiss: () -> Unit
) {
    var q by remember { mutableStateOf("") }
    var clienteSel by remember { mutableStateOf<ClienteLite?>(null) }
    var huesped by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check-in — Hab. ${habitacion.numero}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = q, onValueChange = { q = it; onBuscar(it) },
                    label = { Text("Cliente (nombre/clave/RFC)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                    clientes.forEach { c ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { clienteSel = c; if (huesped.isBlank()) huesped = c.nombre }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = clienteSel?.idCliente == c.idCliente,
                                onClick = { clienteSel = c; if (huesped.isBlank()) huesped = c.nombre })
                            Text(c.nombre, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
                OutlinedTextField(
                    value = huesped, onValueChange = { huesped = it },
                    label = { Text("Nombre del huésped") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Los consumos del restaurante se podrán cargar a la habitación.",
                    fontSize = 11.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(
                onClick = { clienteSel?.let { onConfirmar(it.idCliente, huesped) } },
                enabled = clienteSel != null
            ) { Text("Check-in") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun DialogoHabitacion(
    habitacion: HabitacionDto?,
    onGuardar: (numero: String, descripcion: String, activo: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var numero by remember { mutableStateOf(habitacion?.numero ?: "") }
    var desc by remember { mutableStateOf(habitacion?.descripcion ?: "") }
    var activo by remember { mutableStateOf(habitacion?.activo ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (habitacion == null) "Nueva habitación" else "Editar habitación") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = numero, onValueChange = { numero = it },
                    label = { Text("Número") }, singleLine = true)
                OutlinedTextField(value = desc, onValueChange = { desc = it },
                    label = { Text("Descripción") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = activo, onCheckedChange = { activo = it })
                    Spacer(Modifier.width(8.dp)); Text("Activa")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onGuardar(numero.trim(), desc.trim(), activo) },
                enabled = numero.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
