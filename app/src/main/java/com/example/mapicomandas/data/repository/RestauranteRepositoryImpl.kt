package com.example.mapicomandas.data.repository

import com.example.mapicomandas.data.model.*
import com.example.mapicomandas.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestauranteRepositoryImpl @Inject constructor(
    private val api: ApiService
) : RestauranteRepository {

    override suspend fun obtenerMesas(zona: String?) = withContext(Dispatchers.IO) {
        api.obtenerMesas(zona)
    }

    override suspend fun obtenerZonas() = withContext(Dispatchers.IO) {
        api.obtenerZonas()
    }

    override suspend fun obtenerMesasLibres() = withContext(Dispatchers.IO) {
        api.obtenerMesasLibres()
    }

    override suspend fun obtenerMeserosActivos() = withContext(Dispatchers.IO) {
        api.obtenerMeseros(soloActivos = true)
    }

    override suspend fun abrirComanda(
        idMesa: Int, idMesero: Int, numPersonas: Int,
        idTienda: Int, idCaja: Int, obs: String
    ) = withContext(Dispatchers.IO) {
        api.abrirComanda(
            AbrirComandaRequest(
                idMesa = idMesa, idMesero = idMesero, numPersonas = numPersonas,
                idTienda = idTienda, idCaja = idCaja, observaciones = obs,
                tipoServicio = TipoServicio.COMEDOR
            )
        ).id
    }

    override suspend fun abrirComandaSinMesa(
        tipoServicio: Int, idMesero: Int, idTienda: Int, idCaja: Int,
        cliente: String, tel: String, dir: String,
        idRepartidor: Int?, idZona: Int?, cargoEntrega: Double
    ) = withContext(Dispatchers.IO) {
        api.abrirComanda(
            AbrirComandaRequest(
                idMesa = null, idMesero = idMesero, numPersonas = 1,
                idTienda = idTienda, idCaja = idCaja, tipoServicio = tipoServicio,
                nombreCliente = cliente, telefonoCliente = tel, direccionEntrega = dir,
                idRepartidor = idRepartidor, idZonaReparto = idZona, cargoEntrega = cargoEntrega
            )
        ).id
    }

    override suspend fun agregarArticulo(
        idComanda: Int, idArticulo: Int, cantidad: Double,
        precio: Double, tasaIva: Double, ieps: Double,
        notas: String, mods: List<ModificadorAplicado>,
        componentesKit: List<ComponenteKit>?
    ) = withContext(Dispatchers.IO) {
        api.agregarArticulo(
            AgregarArticuloRequest(
                idComanda = idComanda, idArticulo = idArticulo, cantidad = cantidad,
                precio = precio, tasaIva = tasaIva, ieps = ieps, notas = notas,
                modificadores = mods, componentesKit = componentesKit
            )
        ).id
    }

    override suspend fun cancelarLinea(idDetalle: Int) = withContext(Dispatchers.IO) {
        api.cancelarLinea(idDetalle)
        Unit
    }

    override suspend fun separarCantidad(idDetalle: Int, cantidadMover: Double, nuevoLugar: Int) =
        withContext(Dispatchers.IO) {
            api.separarCantidad(idDetalle, cantidadMover, nuevoLugar)
            Unit
        }

    override suspend fun obtenerDetalle(idComanda: Int) = withContext(Dispatchers.IO) {
        api.obtenerDetalle(idComanda)
    }

    override suspend fun obtenerComanda(idComanda: Int) = withContext(Dispatchers.IO) {
        api.obtenerComanda(idComanda)
    }

    override suspend fun cambiarMesero(idComanda: Int, idMeseroNuevo: Int) = withContext(Dispatchers.IO) {
        api.cambiarMesero(idComanda, idMeseroNuevo)
        Unit
    }

    override suspend fun cambiarMesa(idComanda: Int, idMesaActual: Int, idMesaNueva: Int) =
        withContext(Dispatchers.IO) {
            api.cambiarMesa(idComanda, idMesaNueva, idMesaActual)
            Unit
        }

    override suspend fun actualizarComensales(idComanda: Int, numPersonas: Int) =
        withContext(Dispatchers.IO) {
            api.actualizarComensales(idComanda, numPersonas)
            Unit
        }

    override suspend fun enviarACocina(idComanda: Int) = withContext(Dispatchers.IO) {
        api.enviarACocina(idComanda)
        Unit
    }

    override suspend fun marcarListo(idDetalle: Int) = withContext(Dispatchers.IO) {
        api.marcarListo(idDetalle)
        Unit
    }

    override suspend fun marcarEntregado(idDetalle: Int) = withContext(Dispatchers.IO) {
        api.marcarEntregado(idDetalle)
        Unit
    }

    override suspend fun obtenerPlatillosCocina(idPunto: Int?) = withContext(Dispatchers.IO) {
        api.obtenerPlatillosCocina(idPunto)
    }

    override suspend fun cerrarComanda(
        idComanda: Int, idFormaPago: Int, idCliente: Int, idUsuario: Int,
        idTienda: Int, idCaja: Int, idAlmacen: Int, tasaIva: Double,
        propina: Double, pagos: List<PagoVenta>?
    ) = withContext(Dispatchers.IO) {
        api.cerrarComanda(
            CerrarComandaRequest(
                idComanda = idComanda, idFormaPago = idFormaPago, idCliente = idCliente,
                idUsuario = idUsuario, idTienda = idTienda, idCaja = idCaja,
                idAlmacen = idAlmacen, tasaIva = tasaIva, propina = propina, pagos = pagos
            )
        ).id
    }

    override suspend fun calcularPropinaSugerida(idComanda: Int) = withContext(Dispatchers.IO) {
        api.calcularPropinaSugerida(idComanda)
    }

    override suspend fun obtenerFormasPago() = withContext(Dispatchers.IO) {
        api.obtenerFormasPago()
    }

    override suspend fun obtenerComandasSinMesaAbiertas() = withContext(Dispatchers.IO) {
        api.obtenerComandasSinMesa()
    }

    override suspend fun actualizarStatusEntrega(idComanda: Int, status: Int) =
        withContext(Dispatchers.IO) {
            api.actualizarStatusEntrega(idComanda, status)
            Unit
        }

    override suspend fun actualizarDomicilio(
        idComanda: Int, cliente: String, tel: String, dir: String,
        idRepartidor: Int?, idZona: Int?, cargo: Double
    ) = withContext(Dispatchers.IO) {
        val body = buildMap<String, String> {
            put("nombreCliente", cliente)
            put("telefonoCliente", tel)
            put("direccionEntrega", dir)
            put("cargoEntrega", cargo.toString())
            idRepartidor?.let { put("idRepartidor", it.toString()) }
            idZona?.let { put("idZonaReparto", it.toString()) }
        }
        api.actualizarDomicilio(idComanda, body)
        Unit
    }

    override suspend fun obtenerRepartidores(soloActivos: Boolean) = withContext(Dispatchers.IO) {
        api.obtenerRepartidores(soloActivos)
    }

    override suspend fun guardarRepartidor(id: Int, nombre: String, tel: String, activo: Boolean) =
        withContext(Dispatchers.IO) {
            api.guardarRepartidor(Repartidor(id, nombre, tel, activo)).id
        }

    override suspend fun obtenerZonasReparto(soloActivos: Boolean) = withContext(Dispatchers.IO) {
        api.obtenerZonasReparto(soloActivos)
    }

    override suspend fun guardarZonaReparto(id: Int, nombre: String, cargo: Double, activo: Boolean) =
        withContext(Dispatchers.IO) {
            api.guardarZonaReparto(ZonaReparto(id, nombre, cargo, activo)).id
        }

    override suspend fun obtenerArticulos(idCategoria: Int?, clave: String?, nombre: String?) =
        withContext(Dispatchers.IO) {
            api.obtenerArticulos(idCategoria, clave, nombre)
        }

    override suspend fun obtenerCategorias() = withContext(Dispatchers.IO) {
        api.obtenerCategorias()
    }

    override suspend fun obtenerModificadores(idArticulo: Int?) = withContext(Dispatchers.IO) {
        api.obtenerModificadores(idArticulo)
    }

    override suspend fun obtenerKitSlots(idArticulo: Int) = withContext(Dispatchers.IO) {
        api.obtenerKitSlots(idArticulo)
    }

    override suspend fun obtenerPuntosImpresion() = withContext(Dispatchers.IO) {
        api.obtenerPuntosImpresion()
    }

    override suspend fun obtenerMeseros(soloActivos: Boolean) = withContext(Dispatchers.IO) {
        api.obtenerMeseros(soloActivos)
    }

    override suspend fun imprimirComanda(idComanda: Int, soloRecienEnviadas: Boolean, todasLasLineas: Boolean) =
        withContext(Dispatchers.IO) {
            api.imprimirComanda(idComanda, soloRecienEnviadas, todasLasLineas)
        }

    override suspend fun imprimirSubCuenta(idComanda: Int, etiqueta: String, total: Double, idsDetalle: List<Int>) =
        withContext(Dispatchers.IO) {
            api.imprimirSubCuenta(idComanda, etiqueta, total, idsDetalle)
            Unit
        }

    override suspend fun habilitarCaja(idCaja: Int, idUsuario: Int) = withContext(Dispatchers.IO) {
        api.habilitarCaja(idCaja, idUsuario)
        Unit
    }

    override suspend fun registrarMovimientoCaja(mov: MovimientoCaja) = withContext(Dispatchers.IO) {
        api.registrarMovimientoCaja(mov).id
    }

    override suspend fun obtenerResumenCaja(idCaja: Int, idTienda: Int) = withContext(Dispatchers.IO) {
        api.obtenerResumenCaja(idCaja, idTienda)
    }

    override suspend fun realizarCorteZ(idCaja: Int, idUsuario: Int) = withContext(Dispatchers.IO) {
        api.realizarCorteZ(idCaja, idUsuario)
        Unit
    }

    override suspend fun obtenerConfiguracion(idTienda: Int, idCaja: Int) = withContext(Dispatchers.IO) {
        api.obtenerConfiguracion(idTienda, idCaja)
    }
}
