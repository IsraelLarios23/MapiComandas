package com.example.mapicomandas.ui.screens.puntos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import com.example.mapicomandas.data.model.Categoria
import com.example.mapicomandas.data.model.PuntoImpresion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuntosImpresionScreen(
    onVolver: () -> Unit,
    onIrHome: () -> Unit = {},
    viewModel: PuntosImpresionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show(); viewModel.limpiarMensajes() }
    }
    LaunchedEffect(uiState.exito) {
        uiState.exito?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show(); viewModel.limpiarMensajes() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Puntos de Impresión", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = {
                    IconButton(onClick = onIrHome) { Icon(Icons.Default.Home, "Inicio") }
                    IconButton(onClick = { viewModel.nuevoPunto() }) { Icon(Icons.Default.Add, "Nuevo") }
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
                onClick = { viewModel.nuevoPunto() },
                icon = { Icon(Icons.Default.Print, null) },
                text = { Text("Nuevo punto") }
            )
        }
    ) { padding ->
        if (uiState.cargando) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (uiState.puntos.isEmpty()) {
                item {
                    Text(
                        "No hay puntos de impresión. Crea uno (Cocina, Barra, …), asígnale una impresora IP y sus categorías.",
                        color = Color.Gray, modifier = Modifier.padding(16.dp)
                    )
                }
            }
            items(uiState.puntos, key = { it.idPuntoImpresion }) { punto ->
                TarjetaPunto(
                    punto = punto,
                    nombresCategorias = uiState.categorias
                        .filter { punto.categorias.contains(it.idCategoria) }
                        .map { it.nombre },
                    onEditar = { viewModel.editar(punto) },
                    onCategorias = { viewModel.abrirCategorias(punto) },
                    onProbar = { viewModel.probar(punto) },
                    onEliminar = { viewModel.eliminar(punto) }
                )
            }
        }
    }

    // Diálogo editar/crear punto
    uiState.editando?.let { punto ->
        DialogoEditarPunto(
            punto = punto,
            onGuardar = { viewModel.guardar(it) },
            onCancelar = { viewModel.cerrarEditor() }
        )
    }

    // Diálogo asignar categorías
    uiState.asignandoCategorias?.let { punto ->
        DialogoAsignarCategorias(
            punto = punto,
            categorias = uiState.categorias,
            onGuardar = { seleccion -> viewModel.guardarCategorias(punto.idPuntoImpresion, seleccion) },
            onCancelar = { viewModel.cerrarCategorias() }
        )
    }
}

@Composable
fun TarjetaPunto(
    punto: PuntoImpresion,
    nombresCategorias: List<String>,
    onEditar: () -> Unit,
    onCategorias: () -> Unit,
    onProbar: () -> Unit,
    onEliminar: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEditar), elevation = CardDefaults.cardElevation(3.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(punto.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(punto.impresora.ifBlank { "(sin impresora)" }, fontSize = 13.sp, color = Color.Gray)
                    Text("${punto.ancho} cols · ${punto.copias} copia(s)" +
                        (if (punto.imprimirAlEnviar) " · imprime al enviar" else " · manual"),
                        fontSize = 11.sp, color = Color.Gray)
                    Text(
                        "Categorías: " + (if (nombresCategorias.isEmpty()) "(ninguna)" else nombresCategorias.joinToString(", ")),
                        fontSize = 12.sp, color = Color(0xFF6A1B9A)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onCategorias, modifier = Modifier.height(34.dp)) {
                    Icon(Icons.Default.Category, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("Categorías", fontSize = 12.sp)
                }
                OutlinedButton(onClick = onProbar, modifier = Modifier.height(34.dp)) {
                    Icon(Icons.Default.Print, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("Probar", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onEliminar, modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                ) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(15.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoEditarPunto(
    punto: PuntoImpresion,
    onGuardar: (PuntoImpresion) -> Unit,
    onCancelar: () -> Unit
) {
    var nombre by remember { mutableStateOf(punto.nombre) }
    var impresora by remember { mutableStateOf(punto.impresora) }
    var ancho by remember { mutableStateOf(punto.ancho.toString()) }
    var copias by remember { mutableStateOf(punto.copias.toString()) }
    var imprimirAlEnviar by remember { mutableStateOf(punto.imprimirAlEnviar) }
    var activo by remember { mutableStateOf(punto.activo) }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (punto.idPuntoImpresion > 0) "Editar punto" else "Nuevo punto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nombre, onValueChange = { nombre = it },
                    label = { Text("Nombre (Cocina, Barra, …)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = impresora, onValueChange = { impresora = it },
                    label = { Text("Impresora") },
                    placeholder = { Text("192.168.1.200:9100") },
                    supportingText = { Text("Red: IP:puerto · Bluetooth: bt:NOMBRE · USB: usb") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = ancho, onValueChange = { ancho = it.filter { c -> c.isDigit() } },
                        label = { Text("Ancho (cols)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        value = copias, onValueChange = { copias = it.filter { c -> c.isDigit() } },
                        label = { Text("Copias") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Imprimir al enviar a cocina")
                    Switch(checked = imprimirAlEnviar, onCheckedChange = { imprimirAlEnviar = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Activo")
                    Switch(checked = activo, onCheckedChange = { activo = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onGuardar(
                        punto.copy(
                            nombre = nombre.trim(),
                            impresora = impresora.trim(),
                            ancho = ancho.toIntOrNull()?.coerceIn(24, 64) ?: 32,
                            copias = copias.toIntOrNull()?.coerceIn(1, 5) ?: 1,
                            imprimirAlEnviar = imprimirAlEnviar,
                            activo = activo
                        )
                    )
                },
                enabled = nombre.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

@Composable
fun DialogoAsignarCategorias(
    punto: PuntoImpresion,
    categorias: List<Categoria>,
    onGuardar: (List<Int>) -> Unit,
    onCancelar: () -> Unit
) {
    val seleccion = remember { mutableStateListOf<Int>().apply { addAll(punto.categorias) } }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Categorías → ${punto.nombre}") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                items(categorias, key = { it.idCategoria }) { cat ->
                    val marcada = seleccion.contains(cat.idCategoria)
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                if (marcada) seleccion.remove(cat.idCategoria) else seleccion.add(cat.idCategoria)
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = marcada, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(cat.nombre)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onGuardar(seleccion.toList()) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}
