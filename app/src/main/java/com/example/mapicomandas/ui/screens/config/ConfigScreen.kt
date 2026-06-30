package com.example.mapicomandas.ui.screens.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mapicomandas.DbConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    onConectado: () -> Unit,
    onVolver: (() -> Unit)? = null,
    viewModel: ConfigViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var mostrarPassword by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(uiState.conectado) {
        if (uiState.conectado) {
            Toast.makeText(context, "✅ Conexión exitosa", Toast.LENGTH_SHORT).show()
            onConectado()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            snackbarHostState.showSnackbar(it)
            viewModel.limpiarError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Configuración", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onVolver != null) {
                        IconButton(onClick = onVolver) {
                            Icon(Icons.Default.ArrowBack, "Volver")
                        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Base de Datos SQL Server", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            OutlinedTextField(
                value = uiState.host,
                onValueChange = viewModel::setHost,
                label = { Text("Servidor / IP") },
                placeholder = { Text("192.168.1.100 o servidor.dominio.com") },
                leadingIcon = { Icon(Icons.Default.Computer, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.puerto,
                onValueChange = viewModel::setPuerto,
                label = { Text("Puerto") },
                placeholder = { Text("1433") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.baseDatos,
                onValueChange = viewModel::setBaseDatos,
                label = { Text("Base de datos") },
                placeholder = { Text("MapiPOS") },
                leadingIcon = { Icon(Icons.Default.Storage, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.usuario,
                onValueChange = viewModel::setUsuario,
                label = { Text("Usuario SQL") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::setPassword,
                label = { Text("Contraseña") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { mostrarPassword = !mostrarPassword }) {
                        Icon(
                            if (mostrarPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null
                        )
                    }
                },
                visualTransformation = if (mostrarPassword) VisualTransformation.None
                else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Selector de modo SSL (jTDS)
            var expandedSsl by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandedSsl,
                onExpandedChange = { expandedSsl = it }
            ) {
                OutlinedTextField(
                    value = uiState.ssl,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Modo SSL") },
                    leadingIcon = { Icon(Icons.Default.Security, null) },
                    supportingText = {
                        Text("off = sin cifrado · require = exige SSL · request = usa SSL si está disponible")
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSsl) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expandedSsl,
                    onDismissRequest = { expandedSsl = false }
                ) {
                    ConfigUiState.OPCIONES_SSL.forEach { opcion ->
                        DropdownMenuItem(
                            text = { Text(opcion) },
                            onClick = {
                                viewModel.setSsl(opcion)
                                expandedSsl = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.impresoraTicket,
                onValueChange = viewModel::setImpresoraTicket,
                label = { Text("Impresora de tickets (IP:puerto)") },
                placeholder = { Text("192.168.1.200:9100") },
                leadingIcon = { Icon(Icons.Default.Print, null) },
                supportingText = { Text("Impresora ESC/POS de red. Vacío = solo vista previa.") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Divider()

            Text("Configuración de la caja", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.idTienda,
                    onValueChange = viewModel::setIdTienda,
                    label = { Text("Id Tienda") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.idCaja,
                    onValueChange = viewModel::setIdCaja,
                    label = { Text("Id Caja") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.idAlmacen,
                    onValueChange = viewModel::setIdAlmacen,
                    label = { Text("Id Almacén") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.probarYGuardar() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !uiState.probando
            ) {
                if (uiState.probando) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Conectando…")
                } else {
                    Icon(Icons.Default.Link, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Probar y Guardar", fontSize = 16.sp)
                }
            }

            if (uiState.conectado) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                    Spacer(Modifier.width(8.dp))
                    Text("Conexión exitosa", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
