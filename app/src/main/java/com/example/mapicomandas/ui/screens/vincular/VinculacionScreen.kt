package com.example.mapicomandas.ui.screens.vincular

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VinculacionScreen(
    onVinculado: () -> Unit,
    viewModel: VinculacionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.vinculado) { if (uiState.vinculado) onVinculado() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vincular dispositivo", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = androidx.compose.ui.graphics.Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Link, null, modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("Empareja este dispositivo con tu negocio", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Genera un código de 8 caracteres en el portal (api.mapi.codesi.mx/admin → tu cliente → Generar código) y captúralo aquí.",
                fontSize = 13.sp, textAlign = TextAlign.Center, color = androidx.compose.ui.graphics.Color.Gray,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.apiUrl, onValueChange = viewModel::setApiUrl,
                label = { Text("URL de la API") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.codigo, onValueChange = viewModel::setCodigo,
                label = { Text("Código de vinculación") },
                placeholder = { Text("ABCD1234") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                supportingText = { Text("${uiState.codigo.length}/8") },
                modifier = Modifier.fillMaxWidth()
            )

            uiState.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { viewModel.vincular() },
                enabled = !uiState.cargando && uiState.codigo.length == 8,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (uiState.cargando) CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp
                ) else Text("Vincular", fontSize = 16.sp)
            }
        }
    }
}
