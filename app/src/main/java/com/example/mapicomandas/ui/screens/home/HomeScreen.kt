package com.example.mapicomandas.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

data class FuncionHome(
    val titulo: String,
    val icono: ImageVector,
    val color: Color,
    val accion: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onIrAMesas: () -> Unit,
    onIrAKds: () -> Unit,
    onIrADomicilio: () -> Unit,
    onIrACaja: () -> Unit,
    onIrASettings: () -> Unit,
    onIrAPuntosImpresion: () -> Unit = {},
    onIrAReportes: () -> Unit = {},
    onIrAVentas: () -> Unit = {},
    onIrAReservaciones: () -> Unit = {},
    onCerrarSesion: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val nombreUsuario by viewModel.session.nombreUsuario.collectAsState()

    val funciones = listOf(
        FuncionHome("Mesas", Icons.Default.TableRestaurant, Color(0xFF4CAF50), onIrAMesas),
        FuncionHome("Cocina (KDS)", Icons.Default.Kitchen, Color(0xFF1A237E), onIrAKds),
        FuncionHome("Domicilio /\nPara Llevar", Icons.Default.DeliveryDining, Color(0xFF0277BD), onIrADomicilio),
        FuncionHome("Caja", Icons.Default.PointOfSale, Color(0xFF37474F), onIrACaja),
        FuncionHome("Ventas del día", Icons.Default.ReceiptLong, Color(0xFF455A64), onIrAVentas),
        FuncionHome("Reportes", Icons.Default.BarChart, Color(0xFF00838F), onIrAReportes),
        FuncionHome("Reservaciones", Icons.Default.EventAvailable, Color(0xFF6A1B9A), onIrAReservaciones),
        FuncionHome("Puntos de\nImpresión", Icons.Default.Print, Color(0xFF00695C), onIrAPuntosImpresion),
        FuncionHome("Configuración", Icons.Default.Settings, Color(0xFF6A1B9A), onIrASettings),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MapiComandas", fontWeight = FontWeight.Bold) },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, null, tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text(nombreUsuario, color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = onCerrarSesion) {
                            Icon(Icons.Default.Logout, "Cerrar sesión", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
      Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        val clienteLogo by viewModel.clienteLogo.collectAsState()
        com.example.mapicomandas.ui.components.FondoLogoIntermitente(clienteLogoBase64 = clienteLogo)
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val conectado by viewModel.conectado.collectAsState()
            if (!conectado) {
                Surface(color = Color(0xFFB71C1C), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudOff, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Sin conexión a la base de datos — reintentando…", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
            Text(
                "¿Qué deseas hacer?",
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            // Rejilla de 2 columnas con filas de peso igual → todos los botones caben sin scroll
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                funciones.chunked(2).forEach { fila ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        fila.forEach { f ->
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                BotonFuncion(f)
                            }
                        }
                        // Rellena si la fila tiene 1 solo elemento (impar)
                        if (fila.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
      }
    }
}

@Composable
fun BotonFuncion(funcion: FuncionHome) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = funcion.accion),
        colors = CardDefaults.cardColors(containerColor = funcion.color),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                funcion.icono, null,
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                funcion.titulo,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
