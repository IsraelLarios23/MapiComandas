package com.example.mapicomandas.data.api

import com.example.mapicomandas.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Con qué token se autentica el request contra la API central. */
enum class AuthMode { NONE, DEVICE, SESSION }

/** Errores tipados de la API central (envelope {ok,error,motivo}). */
sealed class ApiException(message: String) : Exception(message) {
    /** Falla de red / no se pudo contactar la API. */
    class Red(message: String, cause: Throwable? = null) : ApiException(message)
    /** 401: hay que re-autenticar. [motivo] = "login" (renovar sesión) o "vincular" (re-emparejar). */
    class Reautenticar(val motivo: String, message: String) : ApiException(message)
    /** 403: el token es válido pero la app no tiene esa capacidad. NO mandar a login. */
    class SinPermiso(message: String) : ApiException(message)
    /** Cualquier otro {ok:false} (400, 409 candado de mesa, 500…). */
    class Negocio(val status: Int, message: String) : ApiException(message)
}

/**
 * Cliente HTTP de la API central de MapiPOS (https://api.mapi.codesi.mx).
 *
 * Convenciones de la casa (verificadas contra el servicio vivo):
 *  - Header obligatorio en TODO request: `X-Mapi-App: comandas`.
 *  - Auth Bearer: token de dispositivo (vincular→login) o de sesión (resto).
 *  - Envelope éxito: `{ ok:true, datos:<T>, generadoUtc }`; error: `{ ok:false, error, motivo? }`.
 *  - 401 con `motivo` (login|vincular); 403 = capacidad denegada (token bueno).
 *
 * Devuelve el nodo `datos` como [JsonElement]; el repositorio decodifica a DTOs con [json].
 */
@Singleton
class ApiClient @Inject constructor(
    private val session: SessionManager
) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun get(path: String, auth: AuthMode = AuthMode.SESSION): JsonElement =
        request("GET", path, null, auth)

    suspend fun post(path: String, body: JsonElement? = null, auth: AuthMode = AuthMode.SESSION): JsonElement =
        request("POST", path, body, auth)

    suspend fun put(path: String, body: JsonElement? = null, auth: AuthMode = AuthMode.SESSION): JsonElement =
        request("PUT", path, body, auth)

    suspend fun delete(path: String, auth: AuthMode = AuthMode.SESSION): JsonElement =
        request("DELETE", path, null, auth)

    /**
     * GET binario (p.ej. /v1/articulos/{id}/imagen devuelve image/jpeg crudo SIN envelope).
     * Devuelve null en 404 (sin imagen); errores del envelope se traducen igual que request().
     */
    suspend fun getBinary(path: String, auth: AuthMode = AuthMode.SESSION): ByteArray? =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(session.apiBaseUrl.trimEnd('/') + path)
                .header("X-Mapi-App", "comandas")
            val token = if (auth == AuthMode.SESSION) session.sessionToken.ifBlank { session.deviceToken } else ""
            if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
            val resp = try {
                http.newCall(builder.get().build()).execute()
            } catch (e: IOException) {
                throw ApiException.Red("Sin conexión con la API central: ${e.message}", e)
            }
            resp.use { r ->
                when {
                    r.code == 404 -> null
                    r.isSuccessful -> r.body?.bytes()
                    r.code == 401 -> {
                        if (auth == AuthMode.SESSION) session.notificarReautenticacion("login")
                        throw ApiException.Reautenticar("login", "Sesión inválida")
                    }
                    else -> throw ApiException.Negocio(r.code, "Error ${r.code}")
                }
            }
        }

    private suspend fun request(
        method: String, path: String, body: JsonElement?, auth: AuthMode
    ): JsonElement = withContext(Dispatchers.IO) {
        val url = session.apiBaseUrl.trimEnd('/') + path
        val builder = Request.Builder().url(url).header("X-Mapi-App", "comandas")

        val token = when (auth) {
            AuthMode.NONE -> ""
            AuthMode.DEVICE -> session.deviceToken
            AuthMode.SESSION -> session.sessionToken.ifBlank { session.deviceToken }
        }
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")

        val reqBody = body?.let { json.encodeToString(JsonElement.serializer(), it).toRequestBody(jsonMedia) }
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete(reqBody)
            "PUT" -> builder.put(reqBody ?: "".toRequestBody(jsonMedia))
            else -> builder.post(reqBody ?: "".toRequestBody(jsonMedia))
        }

        val resp = try {
            http.newCall(builder.build()).execute()
        } catch (e: IOException) {
            throw ApiException.Red("Sin conexión con la API central: ${e.message}", e)
        }

        resp.use { r ->
            val texto = r.body?.string().orEmpty()
            val root: JsonObject? = runCatching { json.parseToJsonElement(texto).jsonObject }.getOrNull()
            val ok = root?.get("ok")?.jsonPrimitive?.booleanOrNull ?: false

            if (r.isSuccessful && ok) {
                return@withContext root?.get("datos") ?: JsonNull
            }
            val error = root?.get("error")?.jsonPrimitive?.contentOrNull ?: "Error ${r.code}"
            val motivo = root?.get("motivo")?.jsonPrimitive?.contentOrNull
            when (r.code) {
                401 -> {
                    // Aviso global SOLO en llamadas de datos (SESSION): las de identidad
                    // (vincular=NONE, login=DEVICE) manejan su propio 401 en pantalla.
                    if (auth == AuthMode.SESSION) session.notificarReautenticacion(motivo ?: "login")
                    throw ApiException.Reautenticar(motivo ?: "login", error)
                }
                403 -> throw ApiException.SinPermiso(error)
                else -> throw ApiException.Negocio(r.code, error)
            }
        }
    }
}
