package com.example.mapicomandas.ui.screens.mesas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mapicomandas.data.model.MesaUi
import com.example.mapicomandas.data.model.StatusMesa
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MesasScreen(
    onAbrirComanda: (Int) -> Unit,
    onIrAKds: () -> Unit,
    onIrACaja: () -> Unit,
    onIrADomicilio: () -> Unit,
    onVolver: (() -> Unit)? = null,
    viewModel: MesasViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var mostrarDialogoApertura by remember { mutableStateOf(false) }
    var mostrarDialogoCambioMesero by remember { mutableStateOf(false) }
    var mostrarDialogoCambioMesa by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.limpiarError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MapiComandas — Plano de Mesas", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onVolver != null) {
                        IconButton(onClick = onVolver) {
                            Icon(Icons.Default.ArrowBack, "Volver")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refrescarMesas() }) {
                        Icon(Icons.Default.Refresh, "Refrescar")
                    }
                    IconButton(onClick = onIrAKds) {
                        Icon(Icons.Default.Kitchen, "Monitor Cocina")
                    }
                    IconButton(onClick = onIrADomicilio) {
                        Icon(Icons.Default.DeliveryDining, "Domicilio")
                    }
                    IconButton(onClick = onIrACaja) {
                        Icon(Icons.Default.PointOfSale, "Caja")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Selector de zonas
            if (uiState.zonas.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        ZonaChip(
                            label = "Todas",
                            seleccionada = uiState.zonaSeleccionada == null,
                            onClick = { viewModel.seleccionarZona(null) }
                        )
                    }
                    items(uiState.zonas) { zona ->
                        ZonaChip(
                            label = zona,
                            seleccionada = uiState.zonaSeleccionada == zona,
                            onClick = { viewModel.seleccionarZona(zona) }
                        )
                    }
                }
            }

            if (uiState.cargando) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Plano de mesas (escala proporcional a la pantalla)
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    PlanoMesas(
                        mesas = uiState.mesas,
                        onMesaClick = { mesa ->
                            when (mesa.status) {
                                StatusMesa.LIBRE -> {
                                    viewModel.setMesaContextual(mesa)
                                    mostrarDialogoApertura = true
                                }
                                StatusMesa.OCUPADA, StatusMesa.CUENTA_PEDIDA -> {
                                    mesa.idComanda?.let { onAbrirComanda(it) }
                                }
                                else -> {}
                            }
                        },
                        onMesaLongClick = { mesa ->
                            viewModel.setMesaContextual(mesa)
                        }
                    )
                }
            }
        }

    }

    // Diálogo apertura de comanda
    if (mostrarDialogoApertura && uiState.mesaContextual != null) {
        DialogoAperturaComanda(
            mesa = uiState.mesaContextual!!,
            meseros = uiState.meseros,
            onConfirmar = { idMesero, numPersonas, obs ->
                viewModel.abrirComanda(
                    idMesa = uiState.mesaContextual!!.idMesa,
                    idMesero = idMesero,
                    numPersonas = numPersonas,
                    obs = obs,
                    onSuccess = { idComanda ->
                        mostrarDialogoApertura = false
                        onAbrirComanda(idComanda)
                    }
                )
            },
            onDismiss = { mostrarDialogoApertura = false }
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PlanoMesas(
    mesas: List<MesaUi>,
    onMesaClick: (MesaUi) -> Unit,
    onMesaLongClick: (MesaUi) -> Unit
) {
    // ¿Las mesas traen layout del editor (PosX/Ancho/Alto válidos)?
    val tienenLayout = mesas.any { it.ancho > 0 && it.alto > 0 }

    if (tienenLayout) {
        // Tamaño total del plano según el editor
        val padding = 16
        val contenidoX = (mesas.maxOfOrNull { it.posX + maxOf(it.ancho, 1) } ?: 800) + padding
        val contenidoY = (mesas.maxOfOrNull { it.posY + maxOf(it.alto, 1) } ?: 600) + padding

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Factor de escala para que TODO el plano quepa en la pantalla (sin deformar)
            val dispW = maxWidth.value
            val dispH = maxHeight.value
            val escala = minOf(dispW / contenidoX, dispH / contenidoY).coerceAtMost(1.5f)

            Box(modifier = Modifier.fillMaxSize()) {
                mesas.forEach { mesa ->
                    BotonMesa(
                        mesa = mesa,
                        modifier = Modifier
                            .offset(x = (mesa.posX * escala).dp, y = (mesa.posY * escala).dp)
                            .width((maxOf(mesa.ancho, 70) * escala).dp)
                            .height((maxOf(mesa.alto, 70) * escala).dp),
                        onClick = { onMesaClick(mesa) },
                        onLongClick = { onMesaLongClick(mesa) }
                    )
                }
            }
        }
    } else {
        // Sin layout → rejilla automática que se ajusta al ancho disponible
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            mesas.forEach { mesa ->
                BotonMesa(
                    mesa = mesa,
                    modifier = Modifier.size(96.dp),
                    onClick = { onMesaClick(mesa) },
                    onLongClick = { onMesaLongClick(mesa) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotonMesa(
    mesa: MesaUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colorFondo = colorParaStatus(mesa.status)
    val colorTexto = if (mesa.status == StatusMesa.LIBRE) Color.Black else Color.White

    val minutosOcupada = mesa.fechaApertura?.let { calcularMinutos(it) }

    Card(
        modifier = modifier
            .clip(if (mesa.forma == 1) CircleShape else RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = colorFondo),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = mesa.numero,
                color = colorTexto,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            if (mesa.status == StatusMesa.OCUPADA || mesa.status == StatusMesa.CUENTA_PEDIDA) {
                mesa.importeCuenta.let { importe ->
                    Text(
                        text = "$${String.format("%.2f", importe)}",
                        color = colorTexto,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
                minutosOcupada?.let { min ->
                    Text(
                        text = formatearTiempo(min),
                        color = if (min > 90) Color(0xFFFF6B6B) else colorTexto,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (mesa.reservasHoy > 0 && mesa.status == StatusMesa.LIBRE) {
                Icon(Icons.Default.Event, "", tint = Color(0xFF9C27B0), modifier = Modifier.size(12.dp))
            }
        }
    }
}

fun colorParaStatus(status: Int) = when (status) {
    StatusMesa.LIBRE -> Color(0xFF4CAF50)
    StatusMesa.OCUPADA -> Color(0xFFF44336)
    StatusMesa.RESERVADA -> Color(0xFF9C27B0)
    StatusMesa.CUENTA_PEDIDA -> Color(0xFFFF9800)
    StatusMesa.EN_LIMPIEZA -> Color(0xFF2196F3)
    else -> Color.Gray
}

fun calcularMinutos(fechaStr: String): Int? = runCatching {
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    val fecha = LocalDateTime.parse(fechaStr, formatter)
    ChronoUnit.MINUTES.between(fecha, LocalDateTime.now()).toInt()
}.getOrNull()

fun formatearTiempo(minutos: Int): String {
    val h = minutos / 60
    val m = minutos % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
fun ZonaChip(label: String, seleccionada: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = seleccionada,
        onClick = onClick,
        label = { Text(label) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoAperturaComanda(
    mesa: MesaUi,
    meseros: List<com.example.mapicomandas.data.model.Mesero>,
    onConfirmar: (Int, Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var meseroSel by remember { mutableIntStateOf(meseros.firstOrNull()?.idMesero ?: 1) }
    var personas by remember { mutableStateOf("2") }
    var obs by remember { mutableStateOf("") }
    var expandedMesero by remember { mutableStateOf(false) }
    val meseroNombre = meseros.find { it.idMesero == meseroSel }?.nombre ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abrir Mesa ${mesa.numero}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expandedMesero,
                    onExpandedChange = { expandedMesero = it }
                ) {
                    OutlinedTextField(
                        value = meseroNombre,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Mesero") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMesero) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedMesero,
                        onDismissRequest = { expandedMesero = false }
                    ) {
                        meseros.forEach { m ->
                            DropdownMenuItem(
                                text = { Text("${m.nombre} ${m.apellidos}") },
                                onClick = { meseroSel = m.idMesero; expandedMesero = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = personas,
                    onValueChange = { personas = it.filter { c -> c.isDigit() } },
                    label = { Text("Personas") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = obs,
                    onValueChange = { obs = it },
                    label = { Text("Observaciones") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirmar(meseroSel, personas.toIntOrNull() ?: 1, obs)
            }) { Text("Abrir") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
