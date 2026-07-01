package com.example.mapicomandas.ui.screens.reservaciones

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mapicomandas.data.model.Mesa
import com.example.mapicomandas.data.model.Reservacion
import java.time.format.DateTimeFormatter

private val ESTADOS = mapOf(
    1 to ("Pendiente" to Color(0xFFF9A825)),
    2 to ("Confirmada" to Color(0xFF2E7D32)),
    3 to ("Cumplida" to Color(0xFF1565C0)),
    4 to ("No-Show" to Color(0xFFC62828)),
    5 to ("Cancelada" to Color(0xFF616161))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservacionesScreen(
    onVolver: () -> Unit,
    onIrHome: () -> Unit = {},
    viewModel: ReservacionesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var editando by remember { mutableStateOf<Reservacion?>(null) }
    var creando by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error, uiState.mensaje) {
        (uiState.error ?: uiState.mensaje)?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.limpiarMensajes()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reservaciones", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = {
                    IconButton(onClick = onIrHome) { Icon(Icons.Default.Home, "Inicio") }
                    IconButton(onClick = { viewModel.cargar() }) { Icon(Icons.Default.Refresh, "Refrescar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF6A1B9A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creando = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Nueva") },
                containerColor = Color(0xFF6A1B9A),
                contentColor = Color.White
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Selector de fecha
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { viewModel.cambiarFecha(-1) }) { Icon(Icons.Default.ChevronLeft, "Día anterior") }
                Text(
                    uiState.fecha.format(DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy")),
                    fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
                IconButton(onClick = { viewModel.cambiarFecha(1) }) { Icon(Icons.Default.ChevronRight, "Día siguiente") }
            }
            Divider()

            if (uiState.cargando) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (uiState.reservaciones.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EventAvailable, null, modifier = Modifier.size(56.dp), tint = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("Sin reservaciones este día", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.reservaciones, key = { it.idReservacion }) { r ->
                        TarjetaReservacion(
                            r = r,
                            onEditar = { editando = r },
                            onStatus = { st -> viewModel.cambiarStatus(r.idReservacion, st) }
                        )
                    }
                }
            }
        }
    }

    if (creando || editando != null) {
        DialogoReservacion(
            reservacion = editando,
            mesas = uiState.mesas,
            fechaDefault = uiState.fecha.toString(),
            onGuardar = { id, idMesa, nom, tel, fh, per, obs ->
                viewModel.guardar(id, idMesa, nom, tel, fh, per, obs)
                creando = false; editando = null
            },
            onDismiss = { creando = false; editando = null }
        )
    }
}

@Composable
private fun TarjetaReservacion(
    r: Reservacion,
    onEditar: () -> Unit,
    onStatus: (Int) -> Unit
) {
    val (estadoTxt, estadoColor) = ESTADOS[r.status] ?: ("?" to Color.Gray)
    var menu by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.fechaHora.takeLast(5), fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    modifier = Modifier.width(60.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.nombreCliente, fontWeight = FontWeight.Bold)
                    Text("Mesa ${r.mesa} · ${r.personas} pers." +
                        (if (r.telefono.isNotBlank()) " · ${r.telefono}" else ""),
                        fontSize = 12.sp, color = Color.Gray)
                    if (r.observaciones.isNotBlank())
                        Text(r.observaciones, fontSize = 12.sp, color = Color(0xFF6A1B9A))
                }
                AssistChip(
                    onClick = { menu = true },
                    label = { Text(estadoTxt, fontSize = 12.sp) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = estadoColor)
                )
                IconButton(onClick = onEditar) { Icon(Icons.Default.Edit, "Editar") }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                ESTADOS.forEach { (st, par) ->
                    DropdownMenuItem(
                        text = { Text(par.first) },
                        onClick = { onStatus(st); menu = false }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoReservacion(
    reservacion: Reservacion?,
    mesas: List<Mesa>,
    fechaDefault: String,
    onGuardar: (Int, Int, String, String, String, Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var nombre by remember { mutableStateOf(reservacion?.nombreCliente ?: "") }
    var telefono by remember { mutableStateOf(reservacion?.telefono ?: "") }
    var personas by remember { mutableStateOf((reservacion?.personas ?: 2).toString()) }
    var observaciones by remember { mutableStateOf(reservacion?.observaciones ?: "") }
    var hora by remember { mutableStateOf(reservacion?.fechaHora?.takeLast(5) ?: "20:00") }
    val fecha = reservacion?.fechaHora?.take(10) ?: fechaDefault
    var idMesa by remember { mutableStateOf(reservacion?.idMesa ?: mesas.firstOrNull()?.idMesa ?: 0) }
    var expandMesa by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (reservacion == null) "Nueva reservación" else "Editar reservación") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = nombre, onValueChange = { nombre = it },
                    label = { Text("Nombre del cliente") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = telefono, onValueChange = { telefono = it },
                    label = { Text("Teléfono") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = hora, onValueChange = { hora = it },
                        label = { Text("Hora (HH:mm)") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = personas, onValueChange = { personas = it.filter { c -> c.isDigit() } },
                        label = { Text("Personas") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Selector de mesa
                ExposedDropdownMenuBox(expanded = expandMesa, onExpandedChange = { expandMesa = it }) {
                    val mesaTxt = mesas.firstOrNull { it.idMesa == idMesa }?.let { "Mesa ${it.numero}" } ?: "Mesa $idMesa"
                    OutlinedTextField(
                        value = mesaTxt, onValueChange = {}, readOnly = true,
                        label = { Text("Mesa") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandMesa) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expandMesa, onDismissRequest = { expandMesa = false }) {
                        mesas.forEach { m ->
                            DropdownMenuItem(
                                text = { Text("Mesa ${m.numero} (${m.zona})") },
                                onClick = { idMesa = m.idMesa; expandMesa = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = observaciones, onValueChange = { observaciones = it },
                    label = { Text("Observaciones") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = nombre.isNotBlank() && idMesa > 0 && Regex("\\d{1,2}:\\d{2}").matches(hora),
                onClick = {
                    val fechaHora = "$fecha ${hora.padStart(5, '0')}"
                    onGuardar(
                        reservacion?.idReservacion ?: 0, idMesa, nombre.trim(), telefono.trim(),
                        fechaHora, personas.toIntOrNull() ?: 1, observaciones.trim()
                    )
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
