package com.example.mapicomandas.ui.screens.domicilio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.mapicomandas.data.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomicilioScreen(
    onVolver: () -> Unit,
    onAbrirComanda: (Int) -> Unit,
    onIrHome: () -> Unit = {},
    viewModel: DomicilioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Refresca al volver a la pantalla (p.ej. tras agregar artículos a la comanda)
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.cargarDatos()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it); viewModel.limpiarMensajes() }
    }
    LaunchedEffect(uiState.exito) {
        uiState.exito?.let { snackbarHostState.showSnackbar(it); viewModel.limpiarMensajes() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Domicilio / Para Llevar", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onIrHome) {
                        Icon(Icons.Default.Home, "Inicio")
                    }
                    IconButton(onClick = { viewModel.setMostrarEditarRepartidores(true) }) {
                        Icon(Icons.Default.Person, "Repartidores")
                    }
                    IconButton(onClick = { viewModel.setMostrarEditarZonas(true) }) {
                        Icon(Icons.Default.Map, "Zonas")
                    }
                    IconButton(onClick = { viewModel.setMostrarNuevoPedido(true) }) {
                        Icon(Icons.Default.Add, "Nuevo pedido")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0277BD),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (uiState.cargando) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.comandas.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Sin pedidos activos", color = Color.Gray, fontSize = 16.sp)
                    }
                }
            }
            items(uiState.comandas, key = { it.idComanda }) { comanda ->
                TarjetaDomicilio(
                    comanda = comanda,
                    onAbrir = { onAbrirComanda(comanda.idComanda) },
                    onCambiarStatus = { nuevoStatus ->
                        viewModel.actualizarStatusEntrega(comanda.idComanda, nuevoStatus)
                    }
                )
            }
        }
    }

    // Diálogo nuevo pedido
    if (uiState.mostrarNuevoPedido) {
        DialogoNuevoPedido(
            repartidores = uiState.repartidores,
            zonas = uiState.zonas,
            onConfirmar = { tipo, cliente, tel, dir, idRep, idZona, cargo ->
                viewModel.abrirNuevoPedido(
                    tipoServicio = tipo, cliente = cliente, tel = tel, dir = dir,
                    idRepartidor = idRep, idZona = idZona, cargo = cargo,
                    onSuccess = { id -> onAbrirComanda(id) }
                )
            },
            onDismiss = { viewModel.setMostrarNuevoPedido(false) }
        )
    }
}

@Composable
fun TarjetaDomicilio(
    comanda: ComandaSinMesa,
    onAbrir: () -> Unit,
    onCambiarStatus: (Int) -> Unit
) {
    val (colorStatus, iconoStatus, labelStatus) = when (comanda.statusEntrega) {
        StatusEntrega.PENDIENTE -> Triple(Color(0xFFFF9800), Icons.Default.HourglassEmpty, "Pendiente")
        StatusEntrega.EN_CAMINO -> Triple(Color(0xFF2196F3), Icons.Default.DirectionsBike, "En camino")
        StatusEntrega.ENTREGADO -> Triple(Color(0xFF4CAF50), Icons.Default.CheckCircle, "Entregado")
        else -> Triple(Color.Gray, Icons.Default.ShoppingBag, "Para Llevar")
    }

    val labelTipo = when (comanda.tipoServicio) {
        TipoServicio.PARA_LLEVAR -> "Para llevar"
        TipoServicio.DOMICILIO -> "Domicilio"
        else -> "Sin mesa"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAbrir),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(iconoStatus, null, tint = colorStatus, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "$labelTipo — ${comanda.folio}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    comanda.nombreCliente?.let { Text("Cliente: $it", fontSize = 13.sp) }
                    comanda.telefonoCliente?.let { Text("Tel: $it", fontSize = 13.sp, color = Color.Gray) }
                    comanda.direccionEntrega?.let {
                        if (it.isNotBlank()) Text("Dir: $it", fontSize = 12.sp, color = Color.Gray)
                    }
                    comanda.nombreRepartidor?.let { Text("Repartidor: $it", fontSize = 12.sp) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$${String.format("%.2f", comanda.total)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (comanda.cargoEntrega > 0) {
                        Text(
                            "+$${String.format("%.2f", comanda.cargoEntrega)} envío",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Botones de cambio de status (solo domicilio)
            if (comanda.tipoServicio == TipoServicio.DOMICILIO) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (comanda.statusEntrega != StatusEntrega.EN_CAMINO) {
                        OutlinedButton(
                            onClick = { onCambiarStatus(StatusEntrega.EN_CAMINO) },
                            modifier = Modifier.height(32.dp)
                        ) { Text("En camino", fontSize = 11.sp) }
                    }
                    if (comanda.statusEntrega != StatusEntrega.ENTREGADO) {
                        Button(
                            onClick = { onCambiarStatus(StatusEntrega.ENTREGADO) },
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) { Text("Entregado", fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoNuevoPedido(
    repartidores: List<Repartidor>,
    zonas: List<ZonaReparto>,
    onConfirmar: (Int, String, String, String, Int?, Int?, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var tipoServicio by remember { mutableIntStateOf(TipoServicio.PARA_LLEVAR) }
    var cliente by remember { mutableStateOf("") }
    var tel by remember { mutableStateOf("") }
    var dir by remember { mutableStateOf("") }
    var repartidorSel by remember { mutableStateOf<Int?>(null) }
    var zonaSel by remember { mutableStateOf<Int?>(null) }
    var expandedRep by remember { mutableStateOf(false) }
    var expandedZona by remember { mutableStateOf(false) }
    val cargoZona = zonas.find { it.idZonaReparto == zonaSel }?.cargo ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Pedido") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Tipo de servicio
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tipoServicio == TipoServicio.PARA_LLEVAR,
                        onClick = { tipoServicio = TipoServicio.PARA_LLEVAR },
                        label = { Text("Para Llevar") },
                        leadingIcon = { Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.size(14.dp)) }
                    )
                    FilterChip(
                        selected = tipoServicio == TipoServicio.DOMICILIO,
                        onClick = { tipoServicio = TipoServicio.DOMICILIO },
                        label = { Text("Domicilio") },
                        leadingIcon = { Icon(Icons.Default.DeliveryDining, null, modifier = Modifier.size(14.dp)) }
                    )
                }
                OutlinedTextField(
                    value = cliente,
                    onValueChange = { cliente = it },
                    label = { Text("Nombre del cliente") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tel,
                    onValueChange = { tel = it },
                    label = { Text("Teléfono") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (tipoServicio == TipoServicio.DOMICILIO) {
                    OutlinedTextField(
                        value = dir,
                        onValueChange = { dir = it },
                        label = { Text("Dirección de entrega") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    // Selector de zona
                    ExposedDropdownMenuBox(expanded = expandedZona, onExpandedChange = { expandedZona = it }) {
                        OutlinedTextField(
                            value = zonas.find { it.idZonaReparto == zonaSel }?.let {
                                "${it.nombre} — $${String.format("%.2f", it.cargo)}"
                            } ?: "Sin zona",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Zona de reparto") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedZona) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = expandedZona, onDismissRequest = { expandedZona = false }) {
                            DropdownMenuItem(text = { Text("Sin zona") }, onClick = {
                                zonaSel = null; expandedZona = false
                            })
                            zonas.forEach { zona ->
                                DropdownMenuItem(
                                    text = { Text("${zona.nombre} — $${String.format("%.2f", zona.cargo)}") },
                                    onClick = { zonaSel = zona.idZonaReparto; expandedZona = false }
                                )
                            }
                        }
                    }
                    // Selector de repartidor
                    if (repartidores.isNotEmpty()) {
                        ExposedDropdownMenuBox(expanded = expandedRep, onExpandedChange = { expandedRep = it }) {
                            OutlinedTextField(
                                value = repartidores.find { it.idRepartidor == repartidorSel }?.nombre ?: "Sin asignar",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Repartidor") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRep) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = expandedRep, onDismissRequest = { expandedRep = false }) {
                                DropdownMenuItem(text = { Text("Sin asignar") }, onClick = {
                                    repartidorSel = null; expandedRep = false
                                })
                                repartidores.forEach { rep ->
                                    DropdownMenuItem(
                                        text = { Text(rep.nombre) },
                                        onClick = { repartidorSel = rep.idRepartidor; expandedRep = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirmar(tipoServicio, cliente, tel, dir, repartidorSel, zonaSel, cargoZona)
            }) { Text("Crear pedido") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
