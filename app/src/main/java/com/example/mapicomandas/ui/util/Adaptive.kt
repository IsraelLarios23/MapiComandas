package com.example.mapicomandas.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import com.example.mapicomandas.SessionManager

/**
 * ¿La pantalla debe usar la vista COMPACTA (celular)?
 * El usuario puede forzarla en Ajustes ("telefono"/"tableta"); en "auto" decide el
 * ancho real: < 600 dp = teléfono (umbral estándar de Android para tablets).
 */
@Composable
fun rememberVistaCompacta(session: SessionManager): Boolean {
    val modo by session.modoVista.collectAsState()
    val anchoDp = LocalConfiguration.current.screenWidthDp
    return when (modo) {
        "telefono" -> true
        "tableta" -> false
        else -> anchoDp < 600
    }
}
