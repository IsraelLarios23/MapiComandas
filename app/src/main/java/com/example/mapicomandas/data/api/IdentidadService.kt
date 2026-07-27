package com.example.mapicomandas.data.api

import android.os.Build
import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.data.api.dto.LoginDto
import com.example.mapicomandas.data.api.dto.SesionDto
import com.example.mapicomandas.data.api.dto.VinculacionDto
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Identidad contra la API central: vinculación del dispositivo (código de 8 caracteres),
 * login del usuario POS y cierre/desvinculación. Guarda los tokens en [SessionManager].
 */
@Singleton
class IdentidadService @Inject constructor(
    private val api: ApiClient,
    private val session: SessionManager
) {
    /** Canjea el código de 8 caracteres por el token de dispositivo. Lanza [ApiException] si falla. */
    suspend fun vincular(codigo: String) {
        val body = buildJsonObject {
            put("codigo", codigo.trim().uppercase())
            put("dispositivo", "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
        }
        val datos = api.post("/v1/vincular", body, AuthMode.NONE)
        val resp = api.json.decodeFromJsonElement(VinculacionDto.serializer(), datos)
        session.guardarVinculacion(resp.token, resp.negocio)
    }

    /** Autentica al usuario POS con el token de dispositivo → token de sesión (30 días). */
    suspend fun login(usuario: String, password: String) {
        val body = buildJsonObject {
            put("usuario", usuario.trim())
            put("password", password)
        }
        val datos = api.post("/v1/login", body, AuthMode.DEVICE)
        val resp = api.json.decodeFromJsonElement(LoginDto.serializer(), datos)
        session.guardarSesionApi(resp.token, resp.usuario.id, resp.usuario.nombre.ifBlank { usuario.trim() })
    }

    /** Valida/renueva la sesión y devuelve la identidad (cliente, usuario, alcance). */
    suspend fun sesion(): SesionDto {
        val datos = api.get("/v1/sesion", AuthMode.SESSION)
        return api.json.decodeFromJsonElement(SesionDto.serializer(), datos)
    }

    /** Cierra la sesión (conserva la vinculación del dispositivo). */
    suspend fun logout() {
        runCatching { api.post("/v1/logout", null, AuthMode.SESSION) }
        session.cerrarSesionApi()
    }

    /** Desvincula el dispositivo y revoca todas sus sesiones. */
    suspend fun desvincular() {
        runCatching { api.post("/v1/desvincular", null, AuthMode.SESSION) }
        session.desvincular()
    }
}
