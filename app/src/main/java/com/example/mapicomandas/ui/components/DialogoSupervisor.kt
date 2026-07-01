package com.example.mapicomandas.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Diálogo de autorización de supervisor (usuario + contraseña). */
@Composable
fun DialogoSupervisor(
    titulo: String = "Autorización de supervisor",
    mensaje: String = "",
    onConfirmar: (usuario: String, password: String) -> Unit,
    onCancelar: () -> Unit
) {
    var usuario by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (mensaje.isNotBlank()) Text(mensaje)
                OutlinedTextField(
                    value = usuario, onValueChange = { usuario = it },
                    label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirmar(usuario.trim(), password) }, enabled = usuario.isNotBlank()) { Text("Autorizar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}
