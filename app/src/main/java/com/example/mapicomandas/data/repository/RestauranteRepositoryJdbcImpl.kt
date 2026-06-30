package com.example.mapicomandas.data.repository

import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.data.db.JdbcDataSource
import com.example.mapicomandas.data.model.*
import java.sql.Connection
import java.sql.ResultSet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestauranteRepositoryJdbcImpl @Inject constructor(
    private val db: JdbcDataSource,
    private val session: SessionManager
) : RestauranteRepository {

    // ─────────────────────────────────────────────────────────────────────────
    // LOGIN (usuarios MapiPOS)
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun login(usuario: String, password: String): Usuario? {
        // Esquema real MapiPOS: contraseña PBKDF2-SHA256 (PasswordHash/PasswordSalt VARBINARY),
        // con fallback a la columna legacy Password en texto plano.
        val row = db.queryOne(
            """SELECT TOP 1 u.IdUsuario, u.Usuario, u.Nombre,
                      u.Password, u.PasswordHash, u.PasswordSalt, u.PasswordAlgo,
                      ISNULL(u.IdTienda, 1) AS IdTienda
               FROM dbo.Usuarios u
               WHERE u.Usuario = ? AND u.Activo = 1""",
            listOf(usuario)
        ) { rs ->
            UsuarioLogin(
                idUsuario = rs.getInt("IdUsuario"),
                usuario = rs.getString("Usuario") ?: "",
                nombre = rs.getString("Nombre") ?: "",
                passwordPlano = rs.getString("Password"),
                passwordHash = rs.getBytes("PasswordHash"),
                passwordSalt = rs.getBytes("PasswordSalt"),
                passwordAlgo = rs.getString("PasswordAlgo"),
                idTienda = rs.getInt("IdTienda")
            )
        } ?: return null

        val ok = when {
            row.passwordAlgo?.equals("PBKDF2-SHA256", ignoreCase = true) == true &&
                row.passwordHash != null && row.passwordSalt != null ->
                com.example.mapicomandas.util.PasswordHasher.verify(
                    password, row.passwordHash, row.passwordSalt
                )
            // Fallback legacy: contraseña en texto plano (comparación ordinal)
            !row.passwordPlano.isNullOrEmpty() -> row.passwordPlano == password
            else -> false
        }

        return if (ok) {
            Usuario(
                idUsuario = row.idUsuario,
                nombre = row.nombre,
                usuario = row.usuario,
                idPerfil = 0,
                activo = true
            )
        } else null
    }

    override suspend fun obtenerUsuarios(): List<Usuario> =
        db.query(
            """SELECT IdUsuario, Nombre, Usuario, ISNULL(IdTienda,0) AS IdPerfil, Activo
               FROM dbo.Usuarios WHERE Activo = 1 ORDER BY Nombre"""
        ) { rs -> rs.toUsuario() }

    private data class UsuarioLogin(
        val idUsuario: Int,
        val usuario: String,
        val nombre: String,
        val passwordPlano: String?,
        val passwordHash: ByteArray?,
        val passwordSalt: ByteArray?,
        val passwordAlgo: String?,
        val idTienda: Int
    )

    // ─────────────────────────────────────────────────────────────────────────
    // MESAS
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun obtenerMesas(zona: String?): List<MesaUi> {
        val sql = """
            SELECT m.IdMesa, m.Numero, m.Zona, m.Capacidad, m.Status,
                   m.PosX, m.PosY, m.Ancho, m.Alto, m.Forma, m.Color, m.IdGrupoMesa,
                   c.IdComanda, c.Folio, CONVERT(NVARCHAR(30), c.FechaApertura, 126) AS FechaApertura,
                   ISNULL(c.Total, 0) AS ImporteCuenta,
                   (SELECT COUNT(*) FROM dbo.Reservaciones r
                    WHERE r.IdMesa = m.IdMesa
                      AND CAST(r.Fecha AS DATE) = CAST(GETDATE() AS DATE)) AS ReservasHoy
            FROM dbo.Mesas m
            LEFT JOIN dbo.MaestroComandas c
                   ON c.IdMesa = m.IdMesa AND c.Status NOT IN (5, 6)
            WHERE m.Activa = 1
              ${if (zona != null) "AND m.Zona = ?" else ""}
            ORDER BY m.Numero
        """.trimIndent()
        val params = if (zona != null) listOf(zona) else emptyList()
        return db.query(sql, params) { rs -> rs.toMesaUi() }
    }

    override suspend fun obtenerZonas(): List<String> =
        db.query("SELECT DISTINCT Zona FROM dbo.Mesas WHERE Activa=1 AND Zona IS NOT NULL ORDER BY Zona") { rs ->
            rs.getString("Zona")
        }

    override suspend fun obtenerMesasLibres(): List<Mesa> =
        db.query("SELECT * FROM dbo.Mesas WHERE Activa=1 AND Status=1 ORDER BY Numero") { rs ->
            rs.toMesa()
        }

    override suspend fun obtenerMeserosActivos(): List<Mesero> =
        db.query("SELECT * FROM dbo.Meseros WHERE Activo=1 ORDER BY Nombre") { rs -> rs.toMesero() }

    override suspend fun obtenerMeseros(soloActivos: Boolean): List<Mesero> {
        val sql = if (soloActivos)
            "SELECT * FROM dbo.Meseros WHERE Activo=1 ORDER BY Nombre"
        else
            "SELECT * FROM dbo.Meseros ORDER BY Nombre"
        return db.query(sql) { rs -> rs.toMesero() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // APERTURA DE COMANDA
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun abrirComanda(
        idMesa: Int, idMesero: Int, numPersonas: Int,
        idTienda: Int, idCaja: Int, obs: String
    ): Int = db.inTransaction { conn ->
        // Candado en la mesa (UPDLOCK, HOLDLOCK) para multi-caja
        conn.prepareStatement(
            "SELECT Status FROM dbo.Mesas WITH (UPDLOCK, HOLDLOCK) WHERE IdMesa=?"
        ).use { s ->
            s.setInt(1, idMesa)
            s.executeQuery().use { rs ->
                if (rs.next() && rs.getInt("Status") == StatusMesa.OCUPADA)
                    error("La mesa ya está ocupada por otra caja")
            }
        }
        val folio = generarFolio(conn, "K", idTienda, idCaja)
        val idComanda = conn.insertAndGetId(
            """INSERT INTO dbo.MaestroComandas
               (Folio,IdMesa,IdMesero,IdUsuario,IdTienda,NumPersonas,Status,FechaApertura,
                Observaciones,Subtotal,Descuento,IVA,Total)
               VALUES (?,?,?,?,?,?,1,GETDATE(),?,0,0,0,0)""",
            listOf(folio, idMesa, idMesero, session.idUsuario, idTienda, numPersonas, obs)
        )
        conn.executeUpdate(
            "UPDATE dbo.Mesas SET Status=2 WHERE IdMesa=?", listOf(idMesa)
        )
        idComanda
    }

    override suspend fun abrirComandaSinMesa(
        tipoServicio: Int, idMesero: Int, idTienda: Int, idCaja: Int,
        cliente: String, tel: String, dir: String,
        idRepartidor: Int?, idZona: Int?, cargoEntrega: Double
    ): Int {
        // Domicilio/para-llevar no existe en la BD MapiPOS actual (solo UI).
        // Ver memoria mapipos-port-pendientes: requiere migración para activarse.
        throw UnsupportedOperationException(
            "El módulo de domicilio/para-llevar no está disponible en esta base de datos."
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LÍNEAS
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun agregarArticulo(
        idComanda: Int, idArticulo: Int, cantidad: Double,
        precio: Double, tasaIva: Double, ieps: Double,
        notas: String, mods: List<ModificadorAplicado>,
        componentesKit: List<ComponenteKit>?
    ): Int = db.inTransaction { conn ->
        // Número de línea con candado — multi-caja seguro
        val linea = conn.queryInt(
            "SELECT ISNULL(MAX(Linea),0)+1 FROM dbo.DetalleComandas WITH (UPDLOCK,HOLDLOCK) WHERE IdComanda=?",
            listOf(idComanda)
        )
        // Calcular importes (la BD MapiPOS no maneja IEPS por línea en DetalleComandas)
        val base = precio * cantidad
        val ivaImporte = base * tasaIva
        val total = base + ivaImporte

        val idDetalle = conn.insertAndGetId(
            """INSERT INTO dbo.DetalleComandas
               (IdComanda,IdArticulo,Linea,Cantidad,PrecioUnitario,
                Descuento,Subtotal,IVA,Total,Status,Notas,NumLugar)
               VALUES (?,?,?,?,?,0,?,?,?,1,?,0)""",
            listOf(idComanda, idArticulo, linea, cantidad, precio,
                   base, ivaImporte, total, notas)
        )

        // Modificadores
        mods.forEach { mod ->
            conn.executeUpdate(
                """INSERT INTO dbo.DetalleComandaModificadores
                   (IdDetalleComanda,IdModificador,Tipo,NombreSnapshot,
                    PrecioExtra,AfectaInventario,IdArticuloInsumo,CantidadDelta)
                   VALUES (?,?,?,?,?,?,?,?)""",
                listOf(idDetalle, mod.idModificador, mod.tipo, mod.nombreSnapshot,
                       mod.precioExtra, mod.afectaInventario, mod.idArticuloInsumo, mod.cantidadDelta)
            )
        }

        // Componentes kit
        componentesKit?.forEach { comp ->
            conn.executeUpdate(
                """INSERT INTO dbo.DetalleComandaKitItems
                   (IdDetalleComanda,IdKitSlot,IdArticulo,Cantidad,EtiquetaSlot,NombreSnapshot)
                   VALUES (?,?,?,?,?,?)""",
                listOf(idDetalle, comp.idKitSlot, comp.idArticulo,
                       comp.cantidad, comp.etiquetaSlot, comp.nombreSnapshot)
            )
        }

        recalcularTotales(conn, idComanda)
        idDetalle
    }

    override suspend fun cancelarLinea(idDetalle: Int) = db.inTransaction { conn ->
        val idComanda = conn.queryOne(
            "SELECT IdComanda FROM dbo.DetalleComandas WHERE IdDetalleComanda=?",
            listOf(idDetalle)
        ) { rs -> rs.getInt("IdComanda") } ?: return@inTransaction

        conn.executeUpdate(
            "UPDATE dbo.DetalleComandas SET Status=5 WHERE IdDetalleComanda=?",
            listOf(idDetalle)
        )
        recalcularTotales(conn, idComanda)
    }

    override suspend fun separarCantidad(idDetalle: Int, cantidadMover: Double, nuevoLugar: Int) =
        db.inTransaction { conn ->
            val orig = conn.queryOne(
                """SELECT IdComanda, IdArticulo, Linea, Cantidad, PrecioUnitario,
                          Descuento, Subtotal, IVA, Total, Notas
                   FROM dbo.DetalleComandas WHERE IdDetalleComanda=?""",
                listOf(idDetalle)
            ) { rs ->
                mapOf(
                    "idComanda" to rs.getInt("IdComanda"),
                    "cantidad" to rs.getDouble("Cantidad"),
                    "precio" to rs.getDouble("PrecioUnitario"),
                    "iva" to rs.getDouble("IVA"),
                    "notas" to (rs.getString("Notas") ?: ""),
                    "idArticulo" to rs.getInt("IdArticulo")
                )
            } ?: return@inTransaction

            val cantOrig = orig["cantidad"] as Double
            val cantResta = cantOrig - cantidadMover
            val precio = orig["precio"] as Double
            val tasaIva = if (cantOrig > 0 && precio > 0) (orig["iva"] as Double) / cantOrig / precio else 0.16
            val idComanda = orig["idComanda"] as Int

            // Actualizar cantidad original (a prorrata)
            val subtotalResta = precio * cantResta
            conn.executeUpdate(
                """UPDATE dbo.DetalleComandas SET Cantidad=?,
                   Subtotal=?, IVA=?, Total=?
                   WHERE IdDetalleComanda=?""",
                listOf(cantResta, subtotalResta,
                       subtotalResta * tasaIva,
                       subtotalResta * (1 + tasaIva), idDetalle)
            )

            // Insertar nueva línea con la porción movida
            val linea = conn.queryInt(
                "SELECT ISNULL(MAX(Linea),0)+1 FROM dbo.DetalleComandas WITH (UPDLOCK,HOLDLOCK) WHERE IdComanda=?",
                listOf(idComanda)
            )
            val subtotalNuevo = precio * cantidadMover
            conn.insertAndGetId(
                """INSERT INTO dbo.DetalleComandas
                   (IdComanda,IdArticulo,Linea,Cantidad,PrecioUnitario,
                    Descuento,Subtotal,IVA,Total,Status,Notas,NumLugar)
                   VALUES (?,?,?,?,?,0,?,?,?,1,?,?)""",
                listOf(idComanda, orig["idArticulo"], linea, cantidadMover, precio,
                       subtotalNuevo, subtotalNuevo * tasaIva,
                       subtotalNuevo * (1 + tasaIva),
                       orig["notas"], nuevoLugar)
            )
            recalcularTotales(conn, idComanda)
        }

    override suspend fun obtenerDetalle(idComanda: Int): List<LineaComanda> {
        val lineas = db.query(
            """SELECT dc.*, a.Nombre AS NombreArticulo
               FROM dbo.DetalleComandas dc
               INNER JOIN dbo.Articulos a ON a.IdArticulo = dc.IdArticulo
               WHERE dc.IdComanda=?
               ORDER BY dc.Linea""",
            listOf(idComanda)
        ) { rs -> rs.toLineaComanda() }

        // Cargar modificadores por línea
        val mods = db.query(
            """SELECT * FROM dbo.DetalleComandaModificadores
               WHERE IdDetalleComanda IN (
                 SELECT IdDetalleComanda FROM dbo.DetalleComandas WHERE IdComanda=?
               )""",
            listOf(idComanda)
        ) { rs -> Pair(rs.getInt("IdDetalleComanda"), rs.toModificadorAplicado()) }

        val kits = db.query(
            """SELECT * FROM dbo.DetalleComandaKitItems
               WHERE IdDetalleComanda IN (
                 SELECT IdDetalleComanda FROM dbo.DetalleComandas WHERE IdComanda=?
               )""",
            listOf(idComanda)
        ) { rs -> Pair(rs.getInt("IdDetalleComanda"), rs.toComponenteKit()) }

        val modsPorLinea = mods.groupBy({ it.first }, { it.second })
        val kitsPorLinea = kits.groupBy({ it.first }, { it.second })

        return lineas.map { l ->
            l.copy(
                modificadores = modsPorLinea[l.idDetalleComanda] ?: emptyList(),
                componentesKit = kitsPorLinea[l.idDetalleComanda] ?: emptyList()
            )
        }
    }

    override suspend fun obtenerComanda(idComanda: Int): MaestroComanda =
        db.queryOne(
            """SELECT *, CONVERT(NVARCHAR(30),FechaApertura,126) AS FechaAperturaStr,
                      CONVERT(NVARCHAR(30),FechaCierre,126) AS FechaCierreStr
               FROM dbo.MaestroComandas WHERE IdComanda=?""",
            listOf(idComanda)
        ) { rs -> rs.toMaestroComanda() }
            ?: error("Comanda $idComanda no encontrada")

    // ─────────────────────────────────────────────────────────────────────────
    // OPERACIONES DE MESA
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun cambiarMesero(idComanda: Int, idMeseroNuevo: Int) {
        db.execute(
            "UPDATE dbo.MaestroComandas SET IdMesero=? WHERE IdComanda=?",
            listOf(idMeseroNuevo, idComanda)
        )
    }

    override suspend fun cambiarMesa(idComanda: Int, idMesaActual: Int, idMesaNueva: Int) =
        db.inTransaction { conn ->
            val statusNueva = conn.queryInt(
                "SELECT Status FROM dbo.Mesas WITH (UPDLOCK,HOLDLOCK) WHERE IdMesa=?",
                listOf(idMesaNueva)
            )
            if (statusNueva == StatusMesa.OCUPADA) error("La mesa destino ya está ocupada")
            conn.executeUpdate(
                "UPDATE dbo.MaestroComandas SET IdMesa=? WHERE IdComanda=?",
                listOf(idMesaNueva, idComanda)
            )
            conn.executeUpdate("UPDATE dbo.Mesas SET Status=1 WHERE IdMesa=?", listOf(idMesaActual))
            conn.executeUpdate("UPDATE dbo.Mesas SET Status=2 WHERE IdMesa=?", listOf(idMesaNueva))
        }

    override suspend fun actualizarComensales(idComanda: Int, numPersonas: Int) {
        db.execute(
            "UPDATE dbo.MaestroComandas SET NumPersonas=? WHERE IdComanda=?",
            listOf(numPersonas, idComanda)
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COCINA / KDS
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun enviarACocina(idComanda: Int) = db.inTransaction { conn ->
        conn.executeUpdate(
            "UPDATE dbo.DetalleComandas SET Status=2, FechaEnvio=GETDATE() WHERE IdComanda=? AND Status=1",
            listOf(idComanda)
        )
        conn.executeUpdate(
            "UPDATE dbo.MaestroComandas SET Status=2 WHERE IdComanda=?",
            listOf(idComanda)
        )
    }

    override suspend fun marcarListo(idDetalle: Int) {
        db.execute(
            """UPDATE dbo.DetalleComandas SET Status=3, FechaListo=GETDATE(),
               MinutosCocina=DATEDIFF(MINUTE,FechaEnvio,GETDATE())
               WHERE IdDetalleComanda=?""",
            listOf(idDetalle)
        )
    }

    override suspend fun marcarEntregado(idDetalle: Int) {
        db.execute(
            "UPDATE dbo.DetalleComandas SET Status=4 WHERE IdDetalleComanda=?",
            listOf(idDetalle)
        )
    }

    override suspend fun obtenerPlatillosCocina(idPunto: Int?): List<PlatilloKds> {
        val filtoPunto = if (idPunto != null)
            "AND (a.IdPuntoImpresion=@p OR EXISTS (SELECT 1 FROM dbo.PuntosImpresionCategorias pic WHERE pic.IdPuntoImpresion=@p AND pic.IdCategoria=a.IdCategoria))"
        else ""
        val sql = """
            -- Parte 1: líneas normales (no kit)
            SELECT dc.IdDetalleComanda, dc.IdComanda, mc.Folio,
                   CASE WHEN mc.IdMesa IS NULL THEN N'SIN MESA'
                        ELSE N'Mesa '+m.Numero END AS Mesa,
                   a.Nombre AS Articulo, dc.Cantidad, ISNULL(dc.Notas,'') AS Notas,
                   dc.Status, CONVERT(NVARCHAR(30),dc.FechaEnvio,126) AS FechaEnvio,
                   dc.MinutosCocina AS Minutos,
                   CASE WHEN dc.FechaEnvio IS NOT NULL
                        THEN DATEDIFF(MINUTE,dc.FechaEnvio,GETDATE()) END AS MinutosTranscurridos,
                   CAST('' AS NVARCHAR(150)) AS KitRef
            FROM dbo.DetalleComandas dc
            INNER JOIN dbo.MaestroComandas mc ON mc.IdComanda=dc.IdComanda
            LEFT JOIN dbo.Mesas m ON m.IdMesa=mc.IdMesa
            INNER JOIN dbo.Articulos a ON a.IdArticulo=dc.IdArticulo
            WHERE dc.Status IN (2,3) AND mc.Status NOT IN (5,6)
              AND NOT EXISTS (SELECT 1 FROM dbo.DetalleComandaKitItems ki WHERE ki.IdDetalleComanda=dc.IdDetalleComanda)
              ${if (idPunto != null) filtoPunto.replace("@p", idPunto.toString()) else ""}
            UNION ALL
            -- Parte 2: componentes de kit
            SELECT dc.IdDetalleComanda, dc.IdComanda, mc.Folio,
                   CASE WHEN mc.IdMesa IS NULL THEN N'SIN MESA'
                        ELSE N'Mesa '+m.Numero END AS Mesa,
                   ca.Nombre AS Articulo, dc.Cantidad*ISNULL(ki.Cantidad,1) AS Cantidad,
                   ISNULL(dc.Notas,'') AS Notas,
                   dc.Status, CONVERT(NVARCHAR(30),dc.FechaEnvio,126) AS FechaEnvio,
                   dc.MinutosCocina AS Minutos,
                   CASE WHEN dc.FechaEnvio IS NOT NULL
                        THEN DATEDIFF(MINUTE,dc.FechaEnvio,GETDATE()) END AS MinutosTranscurridos,
                   CAST(a.Nombre AS NVARCHAR(150)) AS KitRef
            FROM dbo.DetalleComandas dc
            INNER JOIN dbo.MaestroComandas mc ON mc.IdComanda=dc.IdComanda
            LEFT JOIN dbo.Mesas m ON m.IdMesa=mc.IdMesa
            INNER JOIN dbo.Articulos a ON a.IdArticulo=dc.IdArticulo
            INNER JOIN dbo.DetalleComandaKitItems ki ON ki.IdDetalleComanda=dc.IdDetalleComanda
            INNER JOIN dbo.Articulos ca ON ca.IdArticulo=ki.IdArticulo
            WHERE dc.Status IN (2,3) AND mc.Status NOT IN (5,6)
              ${if (idPunto != null) filtoPunto.replace("a.IdPuntoImpresion", "ca.IdPuntoImpresion").replace("a.IdCategoria", "ca.IdCategoria").replace("@p", idPunto.toString()) else ""}
            ORDER BY FechaEnvio ASC, IdComanda, IdDetalleComanda
        """.trimIndent()
        return db.query(sql) { rs -> rs.toPlatilloKds() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COBRO
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun cerrarComanda(
        idComanda: Int, idFormaPago: Int, idCliente: Int, idUsuario: Int,
        idTienda: Int, idCaja: Int, idAlmacen: Int, tasaIva: Double,
        propina: Double, pagos: List<PagoVenta>?
    ): Int = db.inTransaction { conn ->
        val comanda = conn.queryOne(
            "SELECT Subtotal,Descuento,IVA,Total,IdMesa FROM dbo.MaestroComandas WHERE IdComanda=?",
            listOf(idComanda)
        ) { rs ->
            mapOf(
                "subtotal" to rs.getDouble("Subtotal"),
                "descuento" to rs.getDouble("Descuento"),
                "iva" to rs.getDouble("IVA"),
                "total" to rs.getDouble("Total"),
                "idMesa" to rs.getObject("IdMesa")      // puede ser NULL
            )
        } ?: error("Comanda no encontrada")

        val total = comanda["total"] as Double
        val folioVenta = generarFolio(conn, "T", idTienda, idCaja)
        val formaPagoVenta = pagos?.firstOrNull()?.idFormaPago ?: idFormaPago

        // Crear Venta (esquema real: Fecha DATE + Hora TIME, sin Propina/IdCaja)
        val idVenta = conn.insertAndGetId(
            """INSERT INTO dbo.Ventas
               (Folio,Fecha,Hora,IdTienda,IdCliente,IdFormaPago,IdUsuario,IdComanda,
                Subtotal,Descuento,IVA,Total,Cancelada)
               VALUES (?,CAST(GETDATE() AS DATE),CAST(GETDATE() AS TIME),?,?,?,?,?,?,?,?,?,0)""",
            listOf(folioVenta, idTienda, idCliente, formaPagoVenta, idUsuario, idComanda,
                   comanda["subtotal"], comanda["descuento"], comanda["iva"], total)
        )

        // DetalleVentas — por cada línea no cancelada (PK real: IdDetailVenta)
        val lineas = conn.query(
            """SELECT dc.Linea, dc.IdArticulo, a.Nombre AS Descripcion, dc.Cantidad,
                      dc.PrecioUnitario, dc.Descuento, dc.Subtotal, dc.IVA, dc.Total
               FROM dbo.DetalleComandas dc
               INNER JOIN dbo.Articulos a ON a.IdArticulo = dc.IdArticulo
               WHERE dc.IdComanda=? AND dc.Status<>5""",
            listOf(idComanda)
        ) { rs ->
            mapOf(
                "linea" to rs.getInt("Linea"),
                "idArticulo" to rs.getInt("IdArticulo"),
                "descripcion" to (rs.getString("Descripcion") ?: ""),
                "cantidad" to rs.getDouble("Cantidad"),
                "precio" to rs.getDouble("PrecioUnitario"),
                "descuento" to rs.getDouble("Descuento"),
                "subtotal" to rs.getDouble("Subtotal"),
                "iva" to rs.getDouble("IVA"),
                "total" to rs.getDouble("Total")
            )
        }
        lineas.forEach { l ->
            conn.executeUpdate(
                """INSERT INTO dbo.DetalleVentas
                   (IdVenta,Linea,IdArticulo,Descripcion,Cantidad,PrecioUnitario,
                    Descuento,Subtotal,IVA,Total)
                   VALUES (?,?,?,?,?,?,?,?,?,?)""",
                listOf(idVenta, l["linea"], l["idArticulo"], l["descripcion"], l["cantidad"],
                       l["precio"], l["descuento"], l["subtotal"], l["iva"], l["total"])
            )
        }

        // Pagos (columna real: Monto). Pagos múltiples → PagosVenta + Pagos núcleo.
        val listaPagos = pagos ?: listOf(PagoVenta(idFormaPago, "", total))
        listaPagos.forEach { p ->
            conn.executeUpdate(
                "INSERT INTO dbo.PagosVenta (IdVenta,IdFormaPago,Monto,Referencia,Fecha) VALUES (?,?,?,?,GETDATE())",
                listOf(idVenta, p.idFormaPago, p.importe, p.referencia)
            )
            conn.executeUpdate(
                "INSERT INTO dbo.Pagos (IdVenta,IdFormaPago,IdUsuario,Monto,Moneda,Referencia,Tipo,Fecha) VALUES (?,?,?,?,'MXN',?,'Pago',GETDATE())",
                listOf(idVenta, p.idFormaPago, idUsuario, p.importe, p.referencia)
            )
        }

        // Liberar mesa (puede ser NULL). Si pertenece a un grupo, libera todo el grupo.
        val idMesa = comanda["idMesa"]
        if (idMesa != null) {
            conn.executeUpdate(
                """UPDATE dbo.Mesas SET Status=1
                   WHERE IdMesa=?
                      OR (IdGrupoMesa IS NOT NULL AND IdGrupoMesa =
                          (SELECT IdGrupoMesa FROM dbo.Mesas WHERE IdMesa=?))""",
                listOf(idMesa, idMesa)
            )
        }

        // Cerrar comanda
        conn.executeUpdate(
            "UPDATE dbo.MaestroComandas SET Status=5,FechaCierre=GETDATE(),IdVenta=? WHERE IdComanda=?",
            listOf(idVenta, idComanda)
        )
        idVenta
    }

    override suspend fun calcularPropinaSugerida(idComanda: Int): Double {
        val modo = db.queryOne(
            "SELECT Valor FROM dbo.ConfiguracionSistema WHERE Clave='REST_PROPINA_MODO' AND (IdTienda=? OR IdTienda IS NULL)",
            listOf(session.idTienda)
        ) { rs -> rs.getString("Valor") } ?: "GLOBAL"

        val total = db.queryOne(
            "SELECT Total FROM dbo.MaestroComandas WHERE IdComanda=?",
            listOf(idComanda)
        ) { rs -> rs.getDouble("Total") } ?: 0.0

        return if (modo == "GLOBAL") {
            val pct = db.queryOne(
                "SELECT Valor FROM dbo.ConfiguracionSistema WHERE Clave='REST_PROPINA_GLOBAL' AND (IdTienda=? OR IdTienda IS NULL)",
                listOf(session.idTienda)
            ) { rs -> rs.getString("Valor")?.toDoubleOrNull() } ?: 0.10
            total * pct
        } else 0.0
    }

    override suspend fun obtenerFormasPago(): List<FormaPago> =
        db.query(
            "SELECT IdFormaPago, Nombre, Activo, CASE WHEN LOWER(Nombre) LIKE '%efectivo%' THEN 1 ELSE 0 END AS EsEfectivo FROM dbo.FormasPago WHERE Activo=1 ORDER BY Nombre"
        ) { rs ->
            FormaPago(
                idFormaPago = rs.getInt("IdFormaPago"),
                nombre = rs.getString("Nombre"),
                activo = rs.getBoolean("Activo"),
                esEfectivo = rs.getInt("EsEfectivo") == 1
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DOMICILIO — no disponible en la BD MapiPOS actual (solo UI).
    // Requiere migración (columnas en MaestroComandas + tablas RepartidoresRest/
    // ZonasReparto). Ver memoria mapipos-port-pendientes.
    // ─────────────────────────────────────────────────────────────────────────

    private fun domicilioNoDisponible(): Nothing = throw UnsupportedOperationException(
        "El módulo de domicilio no está disponible en esta base de datos."
    )

    override suspend fun obtenerComandasSinMesaAbiertas(): List<ComandaSinMesa> = emptyList()

    override suspend fun actualizarStatusEntrega(idComanda: Int, status: Int) = domicilioNoDisponible()

    override suspend fun actualizarDomicilio(
        idComanda: Int, cliente: String, tel: String, dir: String,
        idRepartidor: Int?, idZona: Int?, cargo: Double
    ) = domicilioNoDisponible()

    override suspend fun obtenerRepartidores(soloActivos: Boolean): List<Repartidor> = emptyList()

    override suspend fun guardarRepartidor(id: Int, nombre: String, tel: String, activo: Boolean): Int =
        domicilioNoDisponible()

    override suspend fun obtenerZonasReparto(soloActivos: Boolean): List<ZonaReparto> = emptyList()

    override suspend fun guardarZonaReparto(id: Int, nombre: String, cargo: Double, activo: Boolean): Int =
        domicilioNoDisponible()

    // ─────────────────────────────────────────────────────────────────────────
    // CATÁLOGOS
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun obtenerArticulos(idCategoria: Int?, clave: String?, nombre: String?): List<Articulo> {
        val where = mutableListOf("a.EsInsumo=0")
        val params = mutableListOf<Any?>()
        if (idCategoria != null) { where.add("a.IdCategoria=?"); params.add(idCategoria) }
        if (clave != null) { where.add("(a.Clave=? OR a.CodigoBarras=? OR EXISTS (SELECT 1 FROM dbo.ArticulosCodigosEquivalentes ce WHERE ce.IdArticulo=a.IdArticulo AND ce.CodigoEquivalente=? AND ce.Activo=1))"); params.addAll(listOf(clave, clave, clave)) }
        if (nombre != null) { where.add("a.Nombre LIKE ?"); params.add("%$nombre%") }
        val sql = """
            SELECT a.*, ISNULL(ai.Imagen, NULL) AS Imagen
            FROM dbo.Articulos a
            LEFT JOIN dbo.ArticulosImagenes ai ON ai.IdArticulo = a.IdArticulo
            WHERE ${where.joinToString(" AND ")}
            ORDER BY a.Nombre
        """.trimIndent()
        return db.query(sql, params) { rs -> rs.toArticulo() }
    }

    override suspend fun obtenerCategorias(): List<Categoria> =
        db.query(
            "SELECT IdCategoria,Nombre,Activo,ColorBoton FROM dbo.Categorias WHERE Activo=1 ORDER BY Nombre"
        ) { rs ->
            Categoria(
                idCategoria = rs.getInt("IdCategoria"),
                nombre = rs.getString("Nombre"),
                activo = rs.getBoolean("Activo"),
                colorBoton = rs.getObject("ColorBoton") as? Int,
                imagenBase64 = null
            )
        }

    override suspend fun obtenerModificadores(idArticulo: Int?): List<Modificador> {
        val sql = if (idArticulo != null)
            """SELECT m.* FROM dbo.Modificadores m
               INNER JOIN dbo.ArticulosModificadores am ON am.IdModificador=m.IdModificador
               WHERE am.IdArticulo=? ORDER BY m.Nombre"""
        else "SELECT * FROM dbo.Modificadores ORDER BY Nombre"
        val params = if (idArticulo != null) listOf(idArticulo) else emptyList()
        return db.query(sql, params) { rs ->
            Modificador(
                idModificador = rs.getInt("IdModificador"),
                nombre = rs.getString("Nombre"),
                tipo = rs.getInt("Tipo"),
                afectaInventario = rs.getBoolean("AfectaInventario"),
                idArticuloInsumo = rs.getObject("IdArticuloInsumo") as? Int,
                cantidadDelta = rs.getDouble("CantidadDelta"),
                precioExtra = rs.getDouble("PrecioExtra")
            )
        }
    }

    override suspend fun obtenerKitSlots(idArticulo: Int): List<KitSlot> =
        db.query(
            "SELECT * FROM dbo.KitSlots WHERE IdArticuloPadre=? ORDER BY IdKitSlot",
            listOf(idArticulo)
        ) { rs ->
            KitSlot(
                idKitSlot = rs.getInt("IdKitSlot"),
                idArticuloPadre = rs.getInt("IdArticuloPadre"),
                etiqueta = rs.getString("Etiqueta") ?: "",
                cantidadDefecto = rs.getDouble("CantidadDefecto")
            )
        }

    override suspend fun obtenerPuntosImpresion(): List<PuntoImpresion> {
        val puntos = db.query(
            "SELECT * FROM dbo.PuntosImpresion WHERE Activo=1 ORDER BY Nombre"
        ) { rs ->
            PuntoImpresion(
                idPuntoImpresion = rs.getInt("IdPuntoImpresion"),
                nombre = rs.getString("Nombre"),
                impresora = rs.getString("Impresora") ?: "",
                ancho = rs.getInt("Ancho"),
                copias = rs.getInt("Copias"),
                imprimirAlEnviar = rs.getBoolean("ImprimirAlEnviar"),
                activo = rs.getBoolean("Activo")
            )
        }
        val cats = db.query(
            "SELECT IdPuntoImpresion, IdCategoria FROM dbo.PuntosImpresionCategorias"
        ) { rs -> Pair(rs.getInt("IdPuntoImpresion"), rs.getInt("IdCategoria")) }
        val catsPorPunto = cats.groupBy({ it.first }, { it.second })
        return puntos.map { p -> p.copy(categorias = catsPorPunto[p.idPuntoImpresion] ?: emptyList()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // IMPRESIÓN
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun imprimirComanda(idComanda: Int, soloRecienEnviadas: Boolean, todasLasLineas: Boolean): List<String> {
        // Devuelve los textos de ticket por punto; la app los manda a la impresora ESC/POS
        val statusFiltro = if (todasLasLineas) "dc.Status <> 5"
        else if (soloRecienEnviadas) "dc.Status IN (2,3)" else "dc.Status NOT IN (5)"
        return db.query(
            """SELECT p.Nombre AS Punto, p.Impresora, p.Ancho, p.Copias,
                      a.Nombre AS Articulo, dc.Cantidad, dc.Notas, dc.Status
               FROM dbo.DetalleComandas dc
               INNER JOIN dbo.Articulos a ON a.IdArticulo=dc.IdArticulo
               LEFT JOIN dbo.PuntosImpresion p ON p.IdPuntoImpresion=a.IdPuntoImpresion
               WHERE dc.IdComanda=? AND $statusFiltro
               ORDER BY p.Nombre, dc.Linea""",
            listOf(idComanda)
        ) { rs -> "${rs.getString("Punto") ?: "General"}: ${rs.getDouble("Cantidad").toInt()}x ${rs.getString("Articulo")}" }
    }

    override suspend fun imprimirSubCuenta(idComanda: Int, etiqueta: String, total: Double, idsDetalle: List<Int>) {
        // En la implementación real: generar ESC/POS y enviar a la impresora
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CAJA
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun habilitarCaja(idCaja: Int, idUsuario: Int) {
        // La BD MapiPOS no tiene tabla de apertura: la apertura es el FondoInicial del
        // CorteCaja vigente. El estado "habilitada" se mantiene local en SessionManager.
    }

    override suspend fun registrarMovimientoCaja(mov: MovimientoCaja): Int =
        db.executeAndGetId(
            """INSERT INTO dbo.MovimientosCaja (IdTienda,IdUsuario,Tipo,TipoMovimiento,Monto,Concepto,Fecha,IdCaja)
               VALUES (?,?,?,?,?,?,GETDATE(),?)""",
            listOf(session.idTienda, mov.idUsuario, mov.tipo, mov.tipo, mov.importe, mov.concepto, mov.idCaja)
        )

    override suspend fun obtenerResumenCaja(idCaja: Int, idTienda: Int): ResumenCaja {
        // Ventas no tiene IdCaja en el esquema real → se filtra por IdTienda + fecha de hoy.
        val resumen = db.queryOne(
            """SELECT
               ISNULL(SUM(v.Total),0) AS TotalVentas,
               ISNULL(SUM(CASE WHEN LOWER(fp.Nombre) LIKE '%efectivo%' THEN pv.Monto ELSE 0 END),0) AS TotalEfectivo,
               ISNULL(SUM(CASE WHEN LOWER(fp.Nombre) LIKE '%efectivo%' THEN 0 ELSE pv.Monto END),0) AS TotalOtros,
               COUNT(DISTINCT v.IdVenta) AS NumTransacciones
               FROM dbo.Ventas v
               INNER JOIN dbo.PagosVenta pv ON pv.IdVenta=v.IdVenta
               INNER JOIN dbo.FormasPago fp ON fp.IdFormaPago=pv.IdFormaPago
               WHERE v.IdTienda=? AND v.Fecha=CAST(GETDATE() AS DATE) AND ISNULL(v.Cancelada,0)=0""",
            listOf(idTienda)
        ) { rs ->
            mapOf(
                "ventas" to rs.getDouble("TotalVentas"),
                "efectivo" to rs.getDouble("TotalEfectivo"),
                "otros" to rs.getDouble("TotalOtros"),
                "num" to rs.getInt("NumTransacciones")
            )
        } ?: mapOf("ventas" to 0.0, "efectivo" to 0.0, "otros" to 0.0, "num" to 0)

        val movimientos = db.queryOne(
            """SELECT
               ISNULL(SUM(CASE WHEN Tipo='I' THEN Monto ELSE 0 END),0) AS TotalIngresos,
               ISNULL(SUM(CASE WHEN Tipo='R' THEN Monto ELSE 0 END),0) AS TotalRetiros
               FROM dbo.MovimientosCaja WHERE IdCaja=? AND CAST(Fecha AS DATE)=CAST(GETDATE() AS DATE)""",
            listOf(idCaja)
        ) { rs -> Pair(rs.getDouble("TotalIngresos"), rs.getDouble("TotalRetiros")) }
            ?: Pair(0.0, 0.0)

        val ventas = resumen["ventas"] as Double
        val efectivo = resumen["efectivo"] as Double
        val otros = resumen["otros"] as Double
        val num = resumen["num"] as Int
        val ingresos = movimientos.first
        val retiros = movimientos.second

        return ResumenCaja(
            totalVentas = ventas,
            totalEfectivo = efectivo,
            totalOtros = otros,
            totalRetiros = retiros,
            totalIngresos = ingresos,
            saldoFinal = efectivo + ingresos - retiros,
            numTransacciones = num
        )
    }

    override suspend fun realizarCorteZ(idCaja: Int, idUsuario: Int) {
        // El corte Z de MapiPOS (tabla CorteCaja) tiene muchas columnas obligatorias
        // calculadas. Pendiente de implementar contra el flujo real. Ver memoria.
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONFIGURACIÓN
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun obtenerConfiguracion(idTienda: Int, idCaja: Int): List<ConfigEntry> =
        db.query(
            """SELECT Clave, Valor FROM dbo.ConfiguracionSistema
               WHERE (IdTienda IS NULL OR IdTienda=?)
                 AND (IdCaja IS NULL OR IdCaja=?)
               ORDER BY IdTienda DESC, IdCaja DESC""",
            listOf(idTienda, idCaja)
        ) { rs -> ConfigEntry(rs.getString("Clave"), rs.getString("Valor") ?: "") }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS INTERNOS
    // ─────────────────────────────────────────────────────────────────────────

    private fun generarFolio(conn: Connection, serie: String, idTienda: Int, idCaja: Int): String {
        val num = conn.queryInt(
            """SELECT ISNULL(MAX(CAST(SUBSTRING(Folio, LEN(?)+1, 20) AS INT)), 0)+1
               FROM dbo.${if (serie == "K") "MaestroComandas" else "Ventas"} WITH (UPDLOCK,HOLDLOCK)
               WHERE IdTienda=? AND IdCaja=? AND Folio LIKE ?""",
            listOf(serie, idTienda, idCaja, "$serie%")
        )
        return "$serie${String.format("%06d", num)}"
    }

    private fun recalcularTotales(conn: Connection, idComanda: Int) {
        conn.executeUpdate(
            """UPDATE dbo.MaestroComandas SET
               Subtotal=ISNULL((SELECT SUM(Subtotal) FROM dbo.DetalleComandas WHERE IdComanda=? AND Status<>5),0),
               Descuento=ISNULL((SELECT SUM(Descuento) FROM dbo.DetalleComandas WHERE IdComanda=? AND Status<>5),0),
               IVA=ISNULL((SELECT SUM(IVA) FROM dbo.DetalleComandas WHERE IdComanda=? AND Status<>5),0),
               Total=ISNULL((SELECT SUM(Total) FROM dbo.DetalleComandas WHERE IdComanda=? AND Status<>5),0)
               WHERE IdComanda=?""",
            listOf(idComanda, idComanda, idComanda, idComanda, idComanda)
        )
    }

    // ─── Extensiones Connection ───────────────────────────────────────────────

    private fun Connection.executeUpdate(sql: String, params: List<Any?>) {
        prepareStatement(sql).use { s -> applyParams(s, params); s.executeUpdate() }
    }

    private fun Connection.insertAndGetId(sql: String, params: List<Any?>): Int {
        prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { s ->
            applyParams(s, params)
            s.executeUpdate()
            return s.generatedKeys.use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    private fun <T> Connection.queryOne(sql: String, params: List<Any?>, map: (ResultSet) -> T): T? {
        return prepareStatement(sql).use { s ->
            applyParams(s, params)
            s.executeQuery().use { rs -> if (rs.next()) map(rs) else null }
        }
    }

    private fun <T> Connection.query(sql: String, params: List<Any?> = emptyList(), map: (ResultSet) -> T): List<T> {
        return prepareStatement(sql).use { s ->
            applyParams(s, params)
            s.executeQuery().use { rs ->
                val result = mutableListOf<T>()
                while (rs.next()) result.add(map(rs))
                result
            }
        }
    }

    private fun Connection.queryInt(sql: String, params: List<Any?>): Int =
        queryOne(sql, params) { rs -> rs.getInt(1) } ?: 0

    private fun Connection.queryDouble(sql: String, params: List<Any?>): Double =
        queryOne(sql, params) { rs -> rs.getDouble(1) } ?: 0.0

    private fun applyParams(s: java.sql.PreparedStatement, params: List<Any?>) {
        params.forEachIndexed { i, v ->
            when (v) {
                null -> s.setNull(i + 1, java.sql.Types.NULL)
                is Int -> s.setInt(i + 1, v)
                is Double -> s.setDouble(i + 1, v)
                is String -> s.setString(i + 1, v)
                is Boolean -> s.setBoolean(i + 1, v)
                else -> s.setObject(i + 1, v)
            }
        }
    }

    // ─── Mappers ResultSet → data class ──────────────────────────────────────

    private fun ResultSet.toMesaUi() = MesaUi(
        idMesa = getInt("IdMesa"),
        numero = getString("Numero"),
        zona = getString("Zona") ?: "",
        capacidad = getInt("Capacidad"),
        status = getInt("Status"),
        posX = getInt("PosX"),
        posY = getInt("PosY"),
        ancho = getInt("Ancho"),
        alto = getInt("Alto"),
        forma = getInt("Forma"),
        color = getString("Color") ?: "",
        idGrupoMesa = getObject("IdGrupoMesa") as? Int,
        idComanda = getObject("IdComanda") as? Int,
        folio = getString("Folio"),
        fechaApertura = getString("FechaApertura"),
        importeCuenta = getDouble("ImporteCuenta"),
        reservasHoy = getInt("ReservasHoy")
    )

    private fun ResultSet.toMesa() = Mesa(
        idMesa = getInt("IdMesa"),
        numero = getString("Numero"),
        zona = getString("Zona") ?: "",
        capacidad = getInt("Capacidad"),
        status = getInt("Status"),
        posX = getInt("PosX"),
        posY = getInt("PosY"),
        ancho = getInt("Ancho"),
        alto = getInt("Alto"),
        forma = getInt("Forma"),
        color = getString("Color") ?: "",
        idGrupoMesa = getObject("IdGrupoMesa") as? Int,
        activa = getBoolean("Activa")
    )

    private fun ResultSet.toUsuario() = Usuario(
        idUsuario = getInt("IdUsuario"),
        nombre = getString("Nombre") ?: "",
        usuario = getString("Usuario") ?: "",
        idPerfil = getInt("IdPerfil"),
        activo = getBoolean("Activo")
    )

    private fun ResultSet.toMesero() = Mesero(
        idMesero = getInt("IdMesero"),
        nombre = getString("Nombre"),
        apellidos = getString("Apellidos") ?: "",
        codigo = getString("Codigo") ?: "",
        activo = getBoolean("Activo")
    )

    private fun ResultSet.toMaestroComanda() = MaestroComanda(
        idComanda = getInt("IdComanda"),
        folio = getString("Folio"),
        idMesa = getObject("IdMesa") as? Int,
        idMesero = getInt("IdMesero"),
        idVenta = getObject("IdVenta") as? Int,
        idUsuario = getObject("IdUsuario") as? Int,
        idTienda = getInt("IdTienda"),
        numPersonas = getInt("NumPersonas"),
        status = getInt("Status"),
        fechaApertura = getString("FechaAperturaStr") ?: "",
        fechaCierre = getString("FechaCierreStr"),
        observaciones = getString("Observaciones") ?: "",
        subtotal = getDouble("Subtotal"),
        descuento = getDouble("Descuento"),
        iva = getDouble("IVA"),
        total = getDouble("Total"),
        // Domicilio no existe en la BD MapiPOS actual → valores neutros (comedor)
        tipoServicio = TipoServicio.COMEDOR,
        nombreCliente = null,
        telefonoCliente = null,
        direccionEntrega = null,
        idRepartidor = null,
        idZonaReparto = null,
        cargoEntrega = 0.0,
        statusEntrega = StatusEntrega.NA
    )

    private fun ResultSet.toLineaComanda() = LineaComanda(
        idDetalleComanda = getInt("IdDetalleComanda"),
        idComanda = getInt("IdComanda"),
        idArticulo = getInt("IdArticulo"),
        nombreArticulo = getString("NombreArticulo") ?: "",
        linea = getInt("Linea"),
        cantidad = getDouble("Cantidad"),
        precioUnitario = getDouble("PrecioUnitario"),
        descuento = getDouble("Descuento"),
        subtotal = getDouble("Subtotal"),
        iva = getDouble("IVA"),
        ieps = 0.0,
        total = getDouble("Total"),
        status = getInt("Status"),
        notas = getString("Notas") ?: "",
        numLugar = getInt("NumLugar"),
        fechaEnvio = getString("FechaEnvio"),
        fechaListo = getString("FechaListo"),
        minutosCocina = getObject("MinutosCocina") as? Int,
        costoUnitario = 0.0
    )

    private fun ResultSet.toModificadorAplicado() = ModificadorAplicado(
        idModificador = getInt("IdModificador"),
        tipo = getInt("Tipo"),
        nombreSnapshot = getString("NombreSnapshot") ?: "",
        precioExtra = getDouble("PrecioExtra"),
        afectaInventario = getBoolean("AfectaInventario"),
        idArticuloInsumo = getObject("IdArticuloInsumo") as? Int,
        cantidadDelta = getDouble("CantidadDelta")
    )

    private fun ResultSet.toComponenteKit() = ComponenteKit(
        idKitSlot = getInt("IdKitSlot"),
        idArticulo = getInt("IdArticulo"),
        cantidad = getDouble("Cantidad"),
        etiquetaSlot = getString("EtiquetaSlot") ?: "",
        nombreSnapshot = getString("NombreSnapshot") ?: ""
    )

    private fun ResultSet.toArticulo() = Articulo(
        idArticulo = getInt("IdArticulo"),
        clave = getString("Clave") ?: "",
        nombre = getString("Nombre"),
        precioVenta = getDouble("PrecioVenta"),
        costo = getDouble("Costo"),
        idCategoria = getInt("IdCategoria"),
        codigoBarras = getString("CodigoBarras"),
        esPlatillo = getBoolean("EsPlatillo"),
        esKit = getBoolean("EsKit"),
        esInsumo = getBoolean("EsInsumo"),
        manejaInventario = getBoolean("ManejaInventario"),
        colorBoton = getObject("ColorBoton") as? Int,
        idPuntoImpresion = getObject("IdPuntoImpresion") as? Int,
        tasaIEPS = getDouble("TasaIEPS"),
        exento = getBoolean("Exento"),
        precioIncluyeImpuesto = getBoolean("PrecioIncluyeImpuesto"),
        iepsTipoFactor = getString("IepsTipoFactor"),
        iepsCuota = getDouble("IepsCuota"),
        tasaIva = 0.16
    )

    private fun ResultSet.toPlatilloKds() = PlatilloKds(
        idDetalleComanda = getInt("IdDetalleComanda"),
        idComanda = getInt("IdComanda"),
        folio = getString("Folio"),
        mesa = getString("Mesa") ?: "",
        articulo = getString("Articulo"),
        cantidad = getDouble("Cantidad"),
        notas = getString("Notas") ?: "",
        status = getInt("Status"),
        fechaEnvio = getString("FechaEnvio"),
        minutos = getObject("Minutos") as? Int,
        minutosTranscurridos = getObject("MinutosTranscurridos") as? Int,
        kitRef = getString("KitRef") ?: ""
    )
}
