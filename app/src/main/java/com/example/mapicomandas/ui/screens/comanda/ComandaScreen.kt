package com.example.mapicomandas.ui.screens.comanda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mapicomandas.data.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComandaScreen(
    onVolver: () -> Unit,
    onCobrar: (Int) -> Unit,
    viewModel: ComandaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var codigoInput by remember { mutableStateOf("") }
    var articuloPendiente by remember { mutableStateOf<Articulo?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.limpiarMensajes()
        }
    }
    LaunchedEffect(uiState.exito) {
        uiState.exito?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.limpiarMensajes()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Comanda ${uiState.comanda?.folio ?: ""}",
                            fontWeight = FontWeight.Bold
                        )
                        uiState.comanda?.let { c ->
                            Text(
                                "Mesa ${c.idMesa ?: "Sin mesa"} • ${c.numPersonas} personas",
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Panel izquierdo: catálogo de artículos
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
            ) {
                // Barra de búsqueda / código
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = codigoInput,
                        onValueChange = { codigoInput = it },
                        label = { Text("Código / Nombre") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.QrCodeScanner, null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (codigoInput.isNotBlank()) {
                                viewModel.buscarPorClave(codigoInput.trim())
                                codigoInput = ""
                            }
                        })
                    )
                    IconButton(
                        onClick = {
                            if (codigoInput.isNotBlank()) {
                                viewModel.buscarArticulo(codigoInput)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Search, "Buscar")
                    }
                }

                // Categorías
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = uiState.categoriaSeleccionada == null,
                            onClick = { viewModel.seleccionarCategoria(null) },
                            label = { Text("Todos") }
                        )
                    }
                    items(uiState.categorias) { cat ->
                        FilterChip(
                            selected = uiState.categoriaSeleccionada == cat.idCategoria,
                            onClick = { viewModel.seleccionarCategoria(cat.idCategoria) },
                            label = { Text(cat.nombre) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = cat.colorBoton?.let { Color(it) }
                                    ?: MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }

                // Grid de artículos
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(uiState.articulos) { articulo ->
                        BotonArticulo(
                            articulo = articulo,
                            onClick = {
                                articuloPendiente = articulo
                                viewModel.seleccionarArticuloParaAgregar(articulo)
                            }
                        )
                    }
                }
            }

            Divider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
            )

            // Panel derecho: líneas de la comanda + totales + botones
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
            ) {
                // Lista de líneas
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    items(
                        items = uiState.lineas.filter { it.status != StatusLinea.CANCELADO },
                        key = { it.idDetalleComanda }
                    ) { linea ->
                        LineaComandaItem(
                            linea = linea,
                            seleccionada = uiState.lineaSeleccionada?.idDetalleComanda == linea.idDetalleComanda,
                            onSeleccionar = { viewModel.setLineaSeleccionada(linea) },
                            onCancelar = { viewModel.cancelarLinea(linea.idDetalleComanda) }
                        )
                    }
                }

                // Totales
                uiState.comanda?.let { c ->
                    TotalesComanda(
                        subtotal = c.subtotal,
                        descuento = c.descuento,
                        iva = c.iva,
                        total = c.total
                    )
                }

                // Acciones secundarias
                val hayLineas = uiState.lineas.any { it.status != StatusLinea.CANCELADO }
                BotonesAccionComanda(
                    tieneLineas = hayLineas,
                    onEnviarCocina = { viewModel.enviarACocina() },
                    onImprimirCuenta = { viewModel.imprimirComanda() },
                    onDividir = { viewModel.setMostrarDividir(true) }
                )

                // Botón COBRAR fijo, siempre visible al fondo
                Button(
                    onClick = { onCobrar(viewModel.idComanda) },
                    enabled = hayLineas,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Icon(Icons.Default.AttachMoney, null)
                    Spacer(Modifier.width(6.dp))
                    Text("COBRAR", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Diálogo de modificadores
    if (uiState.mostrarModificadores && articuloPendiente != null) {
        DialogoModificadores(
            articulo = articuloPendiente!!,
            modificadores = uiState.modificadoresDisponibles,
            onConfirmar = { cantidad, notas, mods ->
                viewModel.agregarArticuloConMods(articuloPendiente!!, cantidad, notas, mods)
                articuloPendiente = null
            },
            onDismiss = {
                viewModel.setMostrarModificadores(false)
                articuloPendiente = null
            }
        )
    }

    // Diálogo de separar cantidad
    if (uiState.mostrarDividir && uiState.lineaSeleccionada != null) {
        DialogoSepararCantidad(
            linea = uiState.lineaSeleccionada!!,
            onConfirmar = { cantidadMover, nuevoLugar ->
                viewModel.separarCantidad(
                    uiState.lineaSeleccionada!!.idDetalleComanda,
                    cantidadMover, nuevoLugar
                )
            },
            onDismiss = { viewModel.setMostrarDividir(false) }
        )
    }
}

@Composable
fun BotonArticulo(articulo: Articulo, onClick: () -> Unit) {
    val colorFondo = articulo.colorBoton?.let { Color(it) }
        ?: MaterialTheme.colorScheme.primaryContainer

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = colorFondo),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (articulo.esKit) {
                Icon(Icons.Default.ViewModule, null, modifier = Modifier.size(20.dp))
            }
            Text(
                text = articulo.nombre,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = Color.Black
            )
            Text(
                text = "$${String.format("%.2f", articulo.precioVenta)}",
                fontSize = 11.sp,
                color = Color.DarkGray
            )
        }
    }
}

@Composable
fun LineaComandaItem(
    linea: LineaComanda,
    seleccionada: Boolean,
    onSeleccionar: () -> Unit,
    onCancelar: () -> Unit
) {
    val colorFondo = if (seleccionada) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface

    val colorStatus = when (linea.status) {
        StatusLinea.EN_COCINA -> Color(0xFFFF9800)
        StatusLinea.LISTO -> Color(0xFF4CAF50)
        StatusLinea.ENTREGADO -> Color(0xFF9E9E9E)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorFondo, RoundedCornerShape(6.dp))
            .clickable(onClick = onSeleccionar)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(40.dp)
                .background(colorStatus, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${linea.cantidad.toInt()} × ${linea.nombreArticulo}",
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (linea.notas.isNotBlank()) {
                Text(linea.notas, fontSize = 11.sp, color = Color.Gray)
            }
            linea.modificadores.forEach { mod ->
                val prefijo = when (mod.tipo) {
                    TipoModificador.QUITA -> "− SIN"
                    TipoModificador.AGREGA_CON_COSTO -> "+ extra"
                    else -> "+"
                }
                Text("$prefijo ${mod.nombreSnapshot}", fontSize = 10.sp, color = Color(0xFF795548))
            }
        }
        Text(
            text = "$${String.format("%.2f", linea.total)}",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        IconButton(
            onClick = onCancelar,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.Close, "Cancelar", tint = Color.Red, modifier = Modifier.size(16.dp))
        }
    }
    Spacer(Modifier.height(2.dp))
}

@Composable
fun TotalesComanda(subtotal: Double, descuento: Double, iva: Double, total: Double) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (descuento > 0) {
                FilaTotales("Subtotal", subtotal)
                FilaTotales("Descuento", -descuento, color = Color(0xFF4CAF50))
            }
            FilaTotales("IVA", iva)
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            FilaTotales("TOTAL", total, fontWeight = FontWeight.Bold, fontSize = 18)
        }
    }
}

@Composable
fun FilaTotales(
    label: String, valor: Double,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight = FontWeight.Normal,
    fontSize: Int = 14
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = fontSize.sp, fontWeight = fontWeight, color = color)
        Text(
            "$${String.format("%.2f", valor)}",
            fontSize = fontSize.sp,
            fontWeight = fontWeight,
            color = color
        )
    }
}

@Composable
fun BotonesAccionComanda(
    tieneLineas: Boolean,
    onEnviarCocina: () -> Unit,
    onImprimirCuenta: () -> Unit,
    onDividir: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Button(
            onClick = onEnviarCocina,
            enabled = tieneLineas,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
        ) {
            Icon(Icons.Default.Kitchen, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Cocina", fontSize = 11.sp)
        }
        Button(
            onClick = onImprimirCuenta,
            enabled = tieneLineas,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF607D8B))
        ) {
            Icon(Icons.Default.Print, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Cuenta", fontSize = 11.sp)
        }
        Button(
            onClick = onDividir,
            enabled = tieneLineas,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
        ) {
            Icon(Icons.Default.CallSplit, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Dividir", fontSize = 11.sp)
        }
    }
}

@Composable
fun DialogoModificadores(
    articulo: Articulo,
    modificadores: List<Modificador>,
    onConfirmar: (Double, String, List<ModificadorAplicado>) -> Unit,
    onDismiss: () -> Unit
) {
    var cantidad by remember { mutableStateOf("1") }
    var notas by remember { mutableStateOf("") }
    val seleccionados = remember { mutableStateListOf<ModificadorAplicado>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(articulo.nombre) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = cantidad,
                    onValueChange = { cantidad = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Cantidad") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Modificadores:", fontWeight = FontWeight.Bold)
                modificadores.forEach { mod ->
                    val aplicado = seleccionados.find { it.idModificador == mod.idModificador }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (aplicado != null) seleccionados.remove(aplicado)
                                else seleccionados.add(
                                    ModificadorAplicado(
                                        idModificador = mod.idModificador,
                                        tipo = mod.tipo,
                                        nombreSnapshot = mod.nombre,
                                        precioExtra = mod.precioExtra,
                                        afectaInventario = mod.afectaInventario,
                                        idArticuloInsumo = mod.idArticuloInsumo,
                                        cantidadDelta = mod.cantidadDelta
                                    )
                                )
                            }
                    ) {
                        Checkbox(checked = aplicado != null, onCheckedChange = null)
                        val prefijo = when (mod.tipo) {
                            TipoModificador.QUITA -> "− SIN"
                            TipoModificador.AGREGA_CON_COSTO -> "+ $${String.format("%.2f", mod.precioExtra)}"
                            else -> "+"
                        }
                        Text("$prefijo ${mod.nombre}", fontSize = 13.sp)
                    }
                }
                OutlinedTextField(
                    value = notas,
                    onValueChange = { notas = it },
                    label = { Text("Notas / instrucciones") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirmar(cantidad.toDoubleOrNull() ?: 1.0, notas, seleccionados.toList())
            }) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun DialogoSepararCantidad(
    linea: LineaComanda,
    onConfirmar: (Double, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var cantidadMover by remember { mutableStateOf("1") }
    var nuevoLugar by remember { mutableStateOf("2") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Separar: ${linea.nombreArticulo}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cantidad actual: ${linea.cantidad}")
                OutlinedTextField(
                    value = cantidadMover,
                    onValueChange = { cantidadMover = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Cantidad a mover") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nuevoLugar,
                    onValueChange = { nuevoLugar = it.filter { c -> c.isDigit() } },
                    label = { Text("Lugar / asiento destino") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val cant = cantidadMover.toDoubleOrNull() ?: return@Button
                val lugar = nuevoLugar.toIntOrNull() ?: return@Button
                onConfirmar(cant, lugar)
            }) { Text("Separar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
