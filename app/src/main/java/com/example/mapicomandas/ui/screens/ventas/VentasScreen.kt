package com.example.mapicomandas.ui.screens.ventas

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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mapicomandas.data.model.VentaDia
import com.example.mapicomandas.ui.components.DialogoSupervisor
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VentasScreen(
    onVolver: () -> Unit,
    onIrHome: () -> Unit = {},
    viewModel: VentasViewModel = hiltViewModel()
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
                title = { Text("Ventas del día", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = {
                    IconButton(onClick = onIrHome) { Icon(Icons.Default.Home, "Inicio") }
                    IconButton(onClick = { viewModel.cargar() }) { Icon(Icons.Default.Refresh, "Refrescar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF455A64),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (uiState.cargando) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.ventas.isEmpty()) {
                item { Text("No hay ventas hoy.", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
            }
            items(uiState.ventas, key = { it.idVenta }) { venta ->
                TarjetaVenta(
                    venta = venta,
                    onReimprimir = { viewModel.reimprimir(venta) },
                    onCancelar = { viewModel.pedirCancelar(venta) }
                )
            }
        }
    }

    uiState.ventaParaCancelar?.let { venta ->
        DialogoSupervisor(
            titulo = "Cancelar venta ${venta.folio}",
            mensaje = "Ingresa credenciales de supervisor para cancelar esta venta ($${money(venta.total)}).",
            onConfirmar = { u, p -> viewModel.confirmarCancelar(venta, u, p) },
            onCancelar = { viewModel.cerrarCancelar() }
        )
    }
}

@Composable
fun TarjetaVenta(venta: VentaDia, onReimprimir: () -> Unit, onCancelar: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "T-${venta.folio}",
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (venta.cancelada) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (venta.cancelada) Color.Gray else Color.Unspecified
                )
                Text(venta.hora + (if (venta.cancelada) " · CANCELADA" else ""), fontSize = 12.sp,
                    color = if (venta.cancelada) Color.Red else Color.Gray)
            }
            Text("$${money(venta.total)}", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.padding(end = 8.dp))
            IconButton(onClick = onReimprimir) { Icon(Icons.Default.Print, "Reimprimir", tint = Color(0xFF2196F3)) }
            if (!venta.cancelada) {
                IconButton(onClick = onCancelar) { Icon(Icons.Default.Cancel, "Cancelar", tint = Color.Red) }
            }
        }
    }
}

private fun money(v: Double) = String.format(Locale.US, "%,.2f", v)
