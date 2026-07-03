package com.example.mapicomandas.ui.screens.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
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
                label = { Text("Impresora de tickets") },
                placeholder = { Text("192.168.1.200:9100") },
                leadingIcon = { Icon(Icons.Default.Print, null) },
                supportingText = {
                    Text(
                        "ESC/POS. Red: IP:puerto · Bluetooth: bt:NOMBRE · USB: usb (o usb:VID:PID). " +
                        "Vacío = solo vista previa."
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Divider()

            // Modo Comida Rápida (Fast Food)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Venta Comida Rápida (Fast Food)", fontWeight = FontWeight.Medium)
                    Text(
                        "REST_COMIDA_RAPIDA: se lee de la configuración de MapiPOS; este interruptor " +
                        "es el valor local de respaldo si la BD no tiene la clave.",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
                Switch(
                    checked = uiState.fastFood,
                    onCheckedChange = { viewModel.setFastFood(it) }
                )
            }

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

            Divider()

            // ── NetPay (terminal de tarjeta) ──
            Text("Terminal NetPay", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            // Servicio de respuesta: modo callback externo (si hay URL) o receptor embebido
            val puertoReceptor = 8081
            val ipLocal = remember { com.example.mapicomandas.util.NetworkUtils.obtenerIpLocal() }
            val modoCallback = uiState.npCallbackUrl.isNotBlank()
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF6))) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SettingsEthernet, null, tint = Color(0xFF3949AB), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Servicio de respuesta (configúralo en la terminal)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    if (modoCallback) {
                        Text("Modo callback externo. En la terminal → \"Configurar respuesta del servicio\" apunta a tu servicio:", fontSize = 12.sp)
                        SelectionContainer {
                            Text(uiState.npCallbackUrl, fontSize = 13.sp, color = Color(0xFF1A237E), fontWeight = FontWeight.Bold)
                        }
                        Text("⚠ El callback DEBE escribir dbo.PagosNetPay en la MISMA base de datos que usa esta app " +
                            "(la de arriba: ${uiState.host.ifBlank { "—" }}/${uiState.baseDatos.ifBlank { "—" }}), " +
                            "correlacionando por traceability.mapiTxnId. Si escribe en otra BD, todo cobro dará timeout.",
                            fontSize = 11.sp, color = Color(0xFFB71C1C))
                    } else if (ipLocal != null) {
                        Text("Receptor embebido. En la terminal → \"Configurar respuesta del servicio\":", fontSize = 12.sp)
                        SelectionContainer {
                            Column {
                                Text("• IP/DNS:  http://$ipLocal:$puertoReceptor", fontSize = 13.sp, color = Color(0xFF1A237E), fontWeight = FontWeight.Bold)
                                Text("• Path:  /netpay", fontSize = 13.sp, color = Color(0xFF1A237E), fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("La tablet y la terminal deben estar en la misma red Wi-Fi. Fija esta IP (reserva DHCP). " +
                            "Para usar un callback externo, captura su URL abajo.",
                            fontSize = 11.sp, color = Color.Gray)
                    } else {
                        Text("Sin conexión de red: conecta la tablet al Wi-Fi para obtener su IP.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            OutlinedTextField(
                value = uiState.npBaseUrl, onValueChange = viewModel::setNpBaseUrl,
                label = { Text("Base URL") }, placeholder = { Text("https://api-154.api-netpay.com") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = uiState.npOAuthPath, onValueChange = viewModel::setNpOAuthPath,
                label = { Text("Ruta OAuth token") }, placeholder = { Text("/oauth/token") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = uiState.npSalePath, onValueChange = viewModel::setNpSalePath,
                label = { Text("Ruta venta (sale)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = uiState.npStoreId, onValueChange = viewModel::setNpStoreId,
                label = { Text("Store ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = uiState.npSerial, onValueChange = viewModel::setNpSerial,
                label = { Text("Serial de la terminal") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = uiState.npCallbackUrl, onValueChange = viewModel::setNpCallbackUrl,
                label = { Text("Callback URL (opcional)") },
                placeholder = { Text("https://tu-servicio.azurewebsites.net/api/netpay/callback") },
                supportingText = { Text("Con valor: la app lee de PagosNetPay. Vacío: receptor embebido en la app.") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = uiState.npAuthString, onValueChange = viewModel::setNpAuthString,
                label = { Text("Auth String (Basic base64)") },
                supportingText = { Text("Sin el prefijo 'Basic '") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.npUsername, onValueChange = viewModel::setNpUsername,
                    label = { Text("Usuario") }, modifier = Modifier.weight(1f), singleLine = true
                )
                OutlinedTextField(
                    value = uiState.npPassword, onValueChange = viewModel::setNpPassword,
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f), singleLine = true
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.probarNetPay() },
                    enabled = !uiState.npProbando,
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.npProbando) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Default.Link, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Probar")
                    }
                }
                OutlinedButton(
                    onClick = { viewModel.guardarNetPay() },
                    enabled = !uiState.npGuardando,
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.npGuardando) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (uiState.npGuardado) "Guardado ✓" else "Guardar")
                    }
                }
            }
            uiState.npResultadoPrueba?.let { res ->
                Text(
                    res,
                    color = if (res.startsWith("✅")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }

            // ── Diagnóstico: últimas transacciones en dbo.PagosNetPay (conexión de la app) ──
            OutlinedButton(
                onClick = { viewModel.diagnosticarNetPay() },
                enabled = !uiState.npDiagCargando,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Diagnóstico: últimas transacciones (BD)")
            }
            if (uiState.npDiagMostrado) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
                    Column(Modifier.padding(10.dp)) {
                        Text("dbo.PagosNetPay · BD ${uiState.host}/${uiState.baseDatos} · Caja ${uiState.idCaja} (config scope por caja)",
                            fontSize = 11.sp, color = Color.Gray)
                        uiState.npDiagAutotest?.let { at ->
                            Spacer(Modifier.height(6.dp))
                            SelectionContainer {
                                Text(at, fontSize = 12.sp,
                                    color = if (at.startsWith("✅")) Color(0xFF2E7D32) else Color(0xFFB71C1C))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        when {
                            uiState.npDiagCargando -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            uiState.npDiag.isEmpty() -> Text(
                                "Sin filas. Si acabas de cobrar y no aparece ni el PENDIENTE, la app no está " +
                                "escribiendo/leyendo esta tabla en esta BD.", fontSize = 12.sp, color = Color(0xFFB71C1C))
                            else -> {
                                val hayAprobada = uiState.npDiag.any { it.estatus.equals("APROBADA", true) }
                                if (!hayAprobada) {
                                    Text("⚠ Solo hay filas PENDIENTE — el callback NO está escribiendo APROBADA en ESTA base. " +
                                        "Revisa que el connection string de tu callback en Azure apunte a ${uiState.host}/${uiState.baseDatos}.",
                                        fontSize = 12.sp, color = Color(0xFFB71C1C))
                                    Spacer(Modifier.height(6.dp))
                                }
                                uiState.npDiag.forEach { f ->
                                    val col = when (f.estatus.uppercase()) {
                                        "APROBADA" -> Color(0xFF2E7D32)
                                        "RECHAZADA" -> Color(0xFFC62828)
                                        else -> Color(0xFFF9A825)
                                    }
                                    SelectionContainer {
                                        Text(
                                            "• ${f.estatus.padEnd(9)} ${f.mapiTxnId.take(8)}… " +
                                            (f.authCode?.let { "auth $it " } ?: "") +
                                            (f.monto?.let { "$$it " } ?: "") +
                                            "alta ${f.fechaAlta.takeLast(8)}" +
                                            (f.fechaResp?.let { " · resp ${it.takeLast(8)}" } ?: ""),
                                            fontSize = 11.sp, color = col
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
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
