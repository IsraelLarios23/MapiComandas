package com.example.mapicomandas.data.api.dto

import kotlinx.serialization.Serializable

/** `datos` de POST /v1/vincular. token = token de DISPOSITIVO. */
@Serializable
data class VinculacionDto(
    val token: String = "",
    val app: String = "",
    val cliente: String = "",
    val negocio: String = "",
    val tiendas: List<TiendaDto> = emptyList()
)

@Serializable
data class TiendaDto(val id: Int = 0, val nombre: String = "", val serie: String? = null)

/** `datos` de POST /v1/login. token = token de SESIÓN (30 días). */
@Serializable
data class LoginDto(
    val token: String = "",
    val expiraUtc: String = "",
    val usuario: UsuarioApiDto = UsuarioApiDto(),
    val app: String = ""
)

@Serializable
data class UsuarioApiDto(val id: Int = 0, val nombre: String = "")

/** `datos` de GET /v1/sesion. */
@Serializable
data class SesionDto(
    val valido: Boolean = false,
    val app: String = "",
    val tipoToken: String = "",
    val cliente: ClienteDto = ClienteDto(),
    val negocio: String = "",
    val usuario: UsuarioApiDto? = null,
    val alcanceTiendas: List<Int>? = null,
    val expiraUtc: String = "",
    val renovada: Boolean = false
)

@Serializable
data class ClienteDto(val codigo: String = "", val nombre: String = "")
