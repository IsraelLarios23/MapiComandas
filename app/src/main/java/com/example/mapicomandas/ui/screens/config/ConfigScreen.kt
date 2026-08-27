package com.example.mapicomandas.ui.screens.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    onDesvincular: () -> Unit = {},
    onVolver: () -> Unit = {},
    viewModel: ConfigViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(uiState.desvinculado) { if (uiState.desvinculado) onDesvincular() }
    LaunchedEffect(uiState.guardado) {
        if (uiState.guardado) android.widget.Toast.makeText(context, "Guardado", android.widget.Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(uiState.npGuardado) {
        if (uiState.npGuardado) android.widget.Toast.makeText(context, "NetPay guardado", android.widget.Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show(); viewModel.limpiarError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.Default.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White, navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Conexión con la API central ──────────────────────────────────
            Text("Conexión (API central)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (uiState.vinculado) Icons.Default.CheckCircle else Icons.Default.Warning,
                        null, tint = if (uiState.vinculado) Color(0xFF2E7D32) else Color(0xFFF9A825))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(if (uiState.vinculado) "Dispositivo vinculado" else "Sin vincular", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (uiState.negocio.isNotBlank()) Text(uiState.negocio, fontSize = 13.sp, color = Color.Gray)
                    }
                }
            }
            OutlinedTextField(
                value = uiState.apiUrl, onValueChange = viewModel::setApiUrl,
                label = { Text("URL de la API") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.desvincular() }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))) {
                    Icon(Icons.Default.LinkOff, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Desvincular")
                }
                Button(onClick = { viewModel.guardarGeneral() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Guardar")
                }
            }
            Text("La tienda, la caja y el almacén los define el servidor (ClienteConfig). La app se configura solo con el código de vinculación.",
                fontSize = 11.sp, color = Color.Gray)

            Divider(Modifier.padding(vertical = 4.dp))

            // ── Impresora / comida rápida ────────────────────────────────────
            Text("Impresora de tickets", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            OutlinedTextField(
                value = uiState.impresoraTicket, onValueChange = viewModel::setImpresoraTicket,
                label = { Text("Impresora ESC/POS") }, placeholder = { Text("192.168.1.200:9100 · bt:NOMBRE · usb") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = uiState.fastFood, onCheckedChange = viewModel::setFastFood)
                Spacer(Modifier.width(8.dp)); Text("Modo comida rápida (para llevar)")
            }
            OutlinedTextField(
                value = uiState.propinaGlobal, onValueChange = viewModel::setPropinaGlobal,
                label = { Text("Propina sugerida (%)") }, placeholder = { Text("10") },
                supportingText = { Text("REST_PROPINA_GLOBAL — se guarda en el negocio (API) al Guardar") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            Divider(Modifier.padding(vertical = 4.dp))

            // ── Terminal NetPay ──────────────────────────────────────────────
            Text("Terminal NetPay", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            val ipLocal = remember { com.example.mapicomandas.util.NetworkUtils.obtenerIpLocal() }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF6))) {
                Column(Modifier.padding(12.dp)) {
                    Text("Servicio de respuesta (configúralo en la terminal):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (ipLocal != null) SelectionContainer {
                        Column {
                            Text("• IP/DNS:  http://$ipLocal:8081", fontSize = 13.sp, color = Color(0xFF1A237E), fontWeight = FontWeight.Bold)
                            Text("• Path:  /netpay", fontSize = 13.sp, color = Color(0xFF1A237E), fontWeight = FontWeight.Bold)
                        }
                    } else Text("Conecta la tablet al Wi-Fi para ver su IP.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    Text("Terminal y tablet en la misma Wi-Fi (con internet). El resultado llega directo a la app.",
                        fontSize = 11.sp, color = Color.Gray)
                }
            }
            OutlinedTextField(uiState.npBaseUrl, viewModel::setNpBaseUrl, label = { Text("Base URL") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(uiState.npStoreId, viewModel::setNpStoreId, label = { Text("Store ID") },
                    singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(uiState.npSerial, viewModel::setNpSerial, label = { Text("Serial") },
                    singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(uiState.npAuthString, viewModel::setNpAuthString, label = { Text("Auth String (Basic base64)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(uiState.npUsername, viewModel::setNpUsername, label = { Text("Usuario") },
                    singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(uiState.npPassword, viewModel::setNpPassword, label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.probarNetPay() }, enabled = !uiState.npProbando, modifier = Modifier.weight(1f)) {
                    if (uiState.npProbando) CircularProgressIndicator(Modifier.size(18.dp))
                    else { Icon(Icons.Default.Link, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Probar") }
                }
                Button(onClick = { viewModel.guardarNetPay() }, enabled = !uiState.npGuardando, modifier = Modifier.weight(1f)) {
                    if (uiState.npGuardando) CircularProgressIndicator(Modifier.size(18.dp))
                    else { Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Guardar") }
                }
            }
            uiState.npResultadoPrueba?.let {
                Text(it, fontSize = 13.sp, color = if (it.startsWith("✅")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
