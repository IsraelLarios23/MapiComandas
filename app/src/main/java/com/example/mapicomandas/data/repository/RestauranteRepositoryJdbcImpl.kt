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
                   0 AS ReservasHoy
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
    ): Int = db.inTransaction { conn ->
        val folio = generarFolio(conn, "K", idTienda, idCaja)
        val statusEntrega = if (tipoServicio == TipoServicio.DOMICILIO) 1 else 0
        val idComanda = conn.insertAndGetId(
            """INSERT INTO dbo.MaestroComandas
                   (Folio, IdMesa, IdMesero, IdUsuario, IdTienda, NumPersonas, Status, FechaApertura, Observaciones,
                    Subtotal, Descuento, IVA, Total,
                    TipoServicio, NombreCliente, TelefonoCliente, DireccionEntrega, IdRepartidor,
                    IdZonaReparto, CargoEntrega, StatusEntrega)
               VALUES (?, NULL, ?, ?, ?, 1, 1, GETDATE(), '', 0,0,0,0,
                       ?, ?, ?, ?, ?, ?, ?, ?)""",
            listOf(folio, idMesero, session.idUsuario, idTienda,
                   tipoServicio, cliente, tel, dir, idRepartidor, idZona, cargoEntrega, statusEntrega)
        )
        // Cargo de envío como línea real del artículo de servicio 'ENV' (si existe)
        if (cargoEntrega > 0) {
            val idEnv = conn.queryOne(
                "SELECT TOP 1 IdArticulo FROM dbo.Articulos WHERE Clave='ENV'",
                emptyList()
            ) { rs -> rs.getInt("IdArticulo") }
            if (idEnv != null) {
                val linea = conn.queryInt(
                    "SELECT ISNULL(MAX(Linea),0)+1 FROM dbo.DetalleComandas WITH (UPDLOCK,HOLDLOCK) WHERE IdComanda=?",
                    listOf(idComanda)
                )
                conn.executeUpdate(
                    """INSERT INTO dbo.DetalleComandas
                       (IdComanda,IdArticulo,Linea,Cantidad,PrecioUnitario,Descuento,Subtotal,IVA,Total,Status,Notas,NumLugar)
                       VALUES (?,?,?,1,?,0,?,0,?,1,'Cargo de envío',0)""",
                    listOf(idComanda, idEnv, linea, cargoEntrega, cargoEntrega, cargoEntrega)
                )
                recalcularTotales(conn, idComanda)
            }
        }
        idComanda
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
                   CASE WHEN mc.IdMesa IS NULL THEN
                        CASE ISNULL(mc.TipoServicio,2) WHEN 3 THEN N'DOMICILIO' WHEN 2 THEN N'PARA LLEVAR' ELSE N'SIN MESA' END
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
                   CASE WHEN mc.IdMesa IS NULL THEN
                        CASE ISNULL(mc.TipoServicio,2) WHEN 3 THEN N'DOMICILIO' WHEN 2 THEN N'PARA LLEVAR' ELSE N'SIN MESA' END
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
    ): Int {
        // Se permite vender sin caja abierta: NO se auto-abre ningún corte aquí.
        return db.inTransaction { conn ->
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
    }

    override suspend fun calcularPropinaSugerida(idComanda: Int): Double {
        val total = db.queryOne(
            "SELECT Total FROM dbo.MaestroComandas WHERE IdComanda=?",
            listOf(idComanda)
        ) { rs -> rs.getDouble("Total") } ?: 0.0

        // ConfiguracionSistema real = solo (Clave, Valor). Tolerante a errores → 0% si falla.
        val pct = runCatching {
            db.queryOne(
                "SELECT TOP 1 Valor FROM dbo.ConfiguracionSistema WHERE Clave='REST_PROPINA_GLOBAL'",
                emptyList()
            ) { rs -> rs.getString("Valor")?.toDoubleOrNull() }
        }.getOrNull() ?: 0.0

        return total * pct
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
    // DOMICILIO / PARA LLEVAR
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun obtenerComandasSinMesaAbiertas(): List<ComandaSinMesa> =
        db.query(
            """SELECT mc.IdComanda, mc.Folio, ISNULL(mc.TipoServicio,2) AS TipoServicio,
                      mc.NombreCliente, mc.TelefonoCliente, mc.DireccionEntrega,
                      mc.IdRepartidor, r.Nombre AS NombreRepartidor,
                      mc.IdZonaReparto, z.Nombre AS NombreZona,
                      ISNULL(mc.CargoEntrega,0) AS CargoEntrega, ISNULL(mc.StatusEntrega,0) AS StatusEntrega,
                      ISNULL(mc.Total,0) AS Total, mc.Status,
                      CONVERT(NVARCHAR(30),mc.FechaApertura,126) AS FechaAperturaStr
               FROM dbo.MaestroComandas mc
               LEFT JOIN dbo.ZonasReparto z ON z.IdZonaReparto = mc.IdZonaReparto
               LEFT JOIN dbo.RepartidoresRest r ON r.IdRepartidor = mc.IdRepartidor
               WHERE mc.IdMesa IS NULL AND mc.Status NOT IN (5,6)
               ORDER BY mc.FechaApertura DESC"""
        ) { rs ->
            ComandaSinMesa(
                idComanda = rs.getInt("IdComanda"),
                folio = rs.getString("Folio"),
                tipoServicio = rs.getInt("TipoServicio"),
                nombreCliente = rs.getString("NombreCliente"),
                telefonoCliente = rs.getString("TelefonoCliente"),
                direccionEntrega = rs.getString("DireccionEntrega"),
                idRepartidor = rs.getObject("IdRepartidor") as? Int,
                nombreRepartidor = rs.getString("NombreRepartidor"),
                idZonaReparto = rs.getObject("IdZonaReparto") as? Int,
                nombreZona = rs.getString("NombreZona"),
                cargoEntrega = rs.getDouble("CargoEntrega"),
                statusEntrega = rs.getInt("StatusEntrega"),
                total = rs.getDouble("Total"),
                fechaApertura = rs.getString("FechaAperturaStr") ?: "",
                status = rs.getInt("Status")
            )
        }

    override suspend fun actualizarStatusEntrega(idComanda: Int, status: Int) {
        db.execute(
            "UPDATE dbo.MaestroComandas SET StatusEntrega=? WHERE IdComanda=?",
            listOf(status, idComanda)
        )
    }

    override suspend fun actualizarDomicilio(
        idComanda: Int, cliente: String, tel: String, dir: String,
        idRepartidor: Int?, idZona: Int?, cargo: Double
    ) {
        db.execute(
            """UPDATE dbo.MaestroComandas
               SET NombreCliente=?, TelefonoCliente=?, DireccionEntrega=?,
                   IdRepartidor=?, IdZonaReparto=?, CargoEntrega=?
               WHERE IdComanda=?""",
            listOf(cliente, tel, dir, idRepartidor, idZona, cargo, idComanda)
        )
    }

    override suspend fun obtenerRepartidores(soloActivos: Boolean): List<Repartidor> =
        db.query(
            "SELECT IdRepartidor, Nombre, ISNULL(Telefono,'') AS Telefono, Activo FROM dbo.RepartidoresRest" +
                (if (soloActivos) " WHERE Activo=1" else "") + " ORDER BY Nombre"
        ) { rs ->
            Repartidor(rs.getInt("IdRepartidor"), rs.getString("Nombre"),
                rs.getString("Telefono") ?: "", rs.getBoolean("Activo"))
        }

    override suspend fun guardarRepartidor(id: Int, nombre: String, tel: String, activo: Boolean): Int =
        if (id > 0) {
            db.execute(
                "UPDATE dbo.RepartidoresRest SET Nombre=?,Telefono=?,Activo=? WHERE IdRepartidor=?",
                listOf(nombre, tel, activo, id)
            ); id
        } else db.executeAndGetId(
            "INSERT INTO dbo.RepartidoresRest (Nombre,Telefono,Activo) VALUES (?,?,?)",
            listOf(nombre, tel, activo)
        )

    override suspend fun obtenerZonasReparto(soloActivos: Boolean): List<ZonaReparto> =
        db.query(
            "SELECT IdZonaReparto, Nombre, Cargo, Activo FROM dbo.ZonasReparto" +
                (if (soloActivos) " WHERE Activo=1" else "") + " ORDER BY Nombre"
        ) { rs ->
            ZonaReparto(rs.getInt("IdZonaReparto"), rs.getString("Nombre"),
                rs.getDouble("Cargo"), rs.getBoolean("Activo"))
        }

    override suspend fun guardarZonaReparto(id: Int, nombre: String, cargo: Double, activo: Boolean): Int =
        if (id > 0) {
            db.execute(
                "UPDATE dbo.ZonasReparto SET Nombre=?,Cargo=?,Activo=? WHERE IdZonaReparto=?",
                listOf(nombre, cargo, activo, id)
            ); id
        } else db.executeAndGetId(
            "INSERT INTO dbo.ZonasReparto (Nombre,Cargo,Activo) VALUES (?,?,?)",
            listOf(nombre, cargo, activo)
        )

    // ─────────────────────────────────────────────────────────────────────────
    // CATÁLOGOS
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun obtenerArticulos(idCategoria: Int?, clave: String?, nombre: String?): List<Articulo> {
        // Precio resuelto desde dbo.Precios: prioriza la lista de la CAJA (AsignacionListaCaja),
        // luego cualquier precio activo del artículo, y por último Articulos.PrecioVenta.
        val precioExpr = """
            COALESCE(
              (SELECT TOP 1 p.Precio FROM dbo.Precios p
                 INNER JOIN dbo.AsignacionListaCaja al ON al.IdListaPrecio = p.ListaPrecioId
                 WHERE p.ArticuloId = a.IdArticulo AND ISNULL(p.Activo,1)=1
                   AND al.IdCaja = ? AND ISNULL(al.Activo,1)=1 AND p.CantidadMinima <= 1
                 ORDER BY p.CantidadMinima DESC),
              (SELECT TOP 1 p.Precio FROM dbo.Precios p
                 WHERE p.ArticuloId = a.IdArticulo AND ISNULL(p.Activo,1)=1 AND p.CantidadMinima <= 1
                 ORDER BY ISNULL(p.ListaPrecioId,0), p.CantidadMinima DESC),
              a.PrecioVenta, 0
            ) AS PrecioResuelto
        """.trimIndent()

        // Foto del botón: tabla ArticulosImagenes (si existe)
        val tieneImgs = db.queryOne(
            "SELECT CASE WHEN OBJECT_ID('dbo.ArticulosImagenes') IS NOT NULL THEN 1 ELSE 0 END AS E",
            emptyList()
        ) { rs -> rs.getInt("E") } == 1
        val joinImg = if (tieneImgs) "LEFT JOIN dbo.ArticulosImagenes ai ON ai.IdArticulo = a.IdArticulo" else ""
        val selImg = if (tieneImgs) ", ai.Imagen AS Imagen" else ""

        val where = mutableListOf("ISNULL(a.Activo,1)=1")
        val params = mutableListOf<Any?>(session.idCaja)   // para el subquery de la lista de caja
        if (idCategoria != null) { where.add("a.IdCategoria=?"); params.add(idCategoria) }
        if (clave != null) { where.add("(a.Clave=? OR a.CodigoBarras=?)"); params.addAll(listOf(clave, clave)) }
        if (nombre != null) { where.add("a.Nombre LIKE ?"); params.add("%$nombre%") }
        val sql = """
            SELECT a.*, $precioExpr$selImg
            FROM dbo.Articulos a
            $joinImg
            WHERE ${where.joinToString(" AND ")}
            ORDER BY a.Nombre
        """.trimIndent()
        return db.query(sql, params) { rs -> rs.toArticulo() }
    }

    override suspend fun obtenerCategorias(): List<Categoria> =
        db.query(
            "SELECT * FROM dbo.Categorias WHERE ISNULL(Activo,1)=1 ORDER BY Nombre"
        ) { rs ->
            Categoria(
                idCategoria = rs.getInt("IdCategoria"),
                nombre = rs.getString("Nombre"),
                activo = rs.optBoolean("Activo", true),
                colorBoton = rs.optInt("ColorBoton"),
                imagenBase64 = rs.optImagenBase64("Imagen")
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

    // Detecta el esquema de movimientos de caja: 'lower' (movimientos_caja + aperturas_caja),
    // 'upper' (MovimientosCaja) o 'none'.
    private suspend fun esquemaMovCaja(): String =
        db.queryOne(
            """SELECT CASE
                 WHEN OBJECT_ID('dbo.movimientos_caja') IS NOT NULL THEN 'lower'
                 WHEN OBJECT_ID('dbo.MovimientosCaja') IS NOT NULL THEN 'upper'
                 ELSE 'none' END AS E""",
            emptyList()
        ) { rs -> rs.getString("E") } ?: "none"

    /** IdCorteCaja del corte ABIERTO de la caja, o null si no hay (caja cerrada). */
    private suspend fun corteAbiertoId(idTienda: Int, idCaja: Int): Int? =
        db.queryOne(
            """SELECT TOP 1 IdCorteCaja FROM dbo.CorteCaja
               WHERE IdTienda=? AND ISNULL(IdCaja,?)=? AND Estatus='Abierta'
               ORDER BY IdCorteCaja DESC""",
            listOf(idTienda, idCaja, idCaja)
        ) { rs -> rs.getInt("IdCorteCaja") }

    override suspend fun habilitarCaja(idCaja: Int, idUsuario: Int) {
        val idTienda = session.idTienda
        // Si ya hay un corte abierto no se vuelve a abrir
        if (corteAbiertoId(idTienda, idCaja) != null) return

        // Apertura para movimientos (esquema lowercase)
        if (esquemaMovCaja() == "lower") {
            db.execute(
                """INSERT INTO dbo.aperturas_caja (IdTienda,IdCaja,IdCajero,FondoInicial,FechaApertura)
                   VALUES (?,?,?,0,GETDATE())""",
                listOf(idTienda, idCaja, idUsuario)
            )
        }
        // Corte 'Abierta' = inicio del periodo
        val folio = "C" + java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmmss"))
        db.execute(
            """INSERT INTO dbo.CorteCaja
               (Folio,Fecha,HoraInicio,HoraFin,IdTienda,IdCaja,IdCajero,FondoInicial,Estatus)
               VALUES (?,CAST(GETDATE() AS DATE),CAST(GETDATE() AS TIME),CAST(GETDATE() AS TIME),
                       ?,?,?,0,'Abierta')""",
            listOf(folio, idTienda, idCaja, idUsuario)
        )
    }

    override suspend fun registrarMovimientoCaja(mov: MovimientoCaja): Int =
        when (esquemaMovCaja()) {
            "lower" -> {
                val apertura = db.queryOne(
                    """SELECT TOP 1 IdApertura FROM dbo.aperturas_caja
                       WHERE IdTienda=? AND ISNULL(IdCaja,?)=?
                       ORDER BY FechaApertura DESC""",
                    listOf(session.idTienda, mov.idCaja, mov.idCaja)
                ) { rs -> rs.getInt("IdApertura") }
                val tipoMov = if (mov.tipo == "I") "Incremento" else "Retiro"
                db.executeAndGetId(
                    """INSERT INTO dbo.movimientos_caja (apertura_id,tipo_movimiento,monto,motivo,usuario_id,fecha)
                       VALUES (?,?,?,?,?,GETDATE())""",
                    listOf(apertura, tipoMov, mov.importe, mov.concepto, mov.idUsuario)
                )
            }
            "upper" -> db.executeAndGetId(
                """INSERT INTO dbo.MovimientosCaja (IdTienda,IdUsuario,Tipo,Monto,Concepto,Fecha,IdCaja)
                   VALUES (?,?,?,?,?,GETDATE(),?)""",
                listOf(session.idTienda, mov.idUsuario,
                    if (mov.tipo == "I") "Incremento" else "Retiro",
                    mov.importe, mov.concepto, mov.idCaja)
            )
            else -> 0
        }

    // Predicado SQL: limita al periodo del corte abierto (o al día si idCorte es null).
    private fun filtroPeriodoVentas(idCorte: Int?): String =
        if (idCorte != null)
            "AND (CAST(v.Fecha AS DATETIME)+CAST(v.Hora AS DATETIME)) >= " +
            "(SELECT CAST(c.Fecha AS DATETIME)+CAST(c.HoraInicio AS DATETIME) FROM dbo.CorteCaja c WHERE c.IdCorteCaja=$idCorte)"
        else "AND v.Fecha=CAST(GETDATE() AS DATE)"

    private fun filtroPeriodoMov(idCorte: Int?): String =
        if (idCorte != null)
            ">= (SELECT CAST(c.Fecha AS DATETIME)+CAST(c.HoraInicio AS DATETIME) FROM dbo.CorteCaja c WHERE c.IdCorteCaja=$idCorte)"
        else ">= CAST(CAST(GETDATE() AS DATE) AS DATETIME)"

    /** Ingresos/retiros del periodo del corte abierto. */
    private suspend fun movimientosPeriodo(idCaja: Int, idCorte: Int?): Pair<Double, Double> {
        val periodo = filtroPeriodoMov(idCorte)
        val sql = when (esquemaMovCaja()) {
            "lower" -> """
                SELECT
                  ISNULL(SUM(CASE WHEN m.tipo_movimiento='Incremento' THEN m.monto ELSE 0 END),0) AS Ingresos,
                  ISNULL(SUM(CASE WHEN m.tipo_movimiento='Retiro' THEN m.monto ELSE 0 END),0) AS Retiros
                FROM dbo.movimientos_caja m
                LEFT JOIN dbo.aperturas_caja a ON a.IdApertura=m.apertura_id
                WHERE m.fecha $periodo AND (a.IdCaja IS NULL OR a.IdCaja=?)
            """.trimIndent()
            "upper" -> """
                SELECT
                  ISNULL(SUM(CASE WHEN Tipo IN ('I','Incremento','Ingreso') THEN Monto ELSE 0 END),0) AS Ingresos,
                  ISNULL(SUM(CASE WHEN Tipo IN ('R','Retiro') THEN Monto ELSE 0 END),0) AS Retiros
                FROM dbo.MovimientosCaja
                WHERE IdCaja=? AND Fecha $periodo
            """.trimIndent()
            else -> return 0.0 to 0.0
        }
        return runCatching {
            db.queryOne(sql, listOf(idCaja)) { rs ->
                rs.getDouble("Ingresos") to rs.getDouble("Retiros")
            }
        }.getOrNull() ?: (0.0 to 0.0)
    }

    private data class TotalesVentaPeriodo(
        val efectivo: Double, val tarjeta: Double, val transfer: Double,
        val otros: Double, val total: Double, val num: Int
    )

    private suspend fun ventasPeriodo(idTienda: Int, idCorte: Int?): TotalesVentaPeriodo {
        val filtro = filtroPeriodoVentas(idCorte)
        return db.queryOne(
            """SELECT
               ISNULL(SUM(CASE WHEN LOWER(fp.Nombre) LIKE '%efectivo%' THEN pv.Monto ELSE 0 END),0) AS Efectivo,
               ISNULL(SUM(CASE WHEN LOWER(fp.Nombre) LIKE '%tarjeta%' OR LOWER(fp.Nombre) LIKE '%credito%' OR LOWER(fp.Nombre) LIKE '%debito%' THEN pv.Monto ELSE 0 END),0) AS Tarjeta,
               ISNULL(SUM(CASE WHEN LOWER(fp.Nombre) LIKE '%transfer%' THEN pv.Monto ELSE 0 END),0) AS Transferencia,
               ISNULL((SELECT SUM(v2.Total) FROM dbo.Ventas v2 WHERE v2.IdTienda=? AND ISNULL(v2.Cancelada,0)=0
                       ${filtro.replace("v.", "v2.")}),0) AS Total,
               COUNT(DISTINCT v.IdVenta) AS Num
               FROM dbo.Ventas v
               INNER JOIN dbo.PagosVenta pv ON pv.IdVenta=v.IdVenta
               INNER JOIN dbo.FormasPago fp ON fp.IdFormaPago=pv.IdFormaPago
               WHERE v.IdTienda=? AND ISNULL(v.Cancelada,0)=0 $filtro""",
            listOf(idTienda, idTienda)
        ) { rs ->
            TotalesVentaPeriodo(
                efectivo = rs.getDouble("Efectivo"),
                tarjeta = rs.getDouble("Tarjeta"),
                transfer = rs.getDouble("Transferencia"),
                otros = 0.0,
                total = rs.getDouble("Total"),
                num = rs.getInt("Num")
            )
        } ?: TotalesVentaPeriodo(0.0, 0.0, 0.0, 0.0, 0.0, 0)
    }

    override suspend fun obtenerResumenCaja(idCaja: Int, idTienda: Int): ResumenCaja {
        val idCorte = corteAbiertoId(idTienda, idCaja)
        // Sin corte abierto → caja cerrada → todo en cero
        if (idCorte == null) return ResumenCaja(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0)

        val v = ventasPeriodo(idTienda, idCorte)
        val (ingresos, retiros) = movimientosPeriodo(idCaja, idCorte)
        val otros = (v.total - v.efectivo - v.tarjeta - v.transfer).coerceAtLeast(0.0)

        return ResumenCaja(
            totalVentas = v.total,
            totalEfectivo = v.efectivo,
            totalOtros = v.tarjeta + v.transfer + otros,
            totalRetiros = retiros,
            totalIngresos = ingresos,
            saldoFinal = v.efectivo + ingresos - retiros,
            numTransacciones = v.num
        )
    }

    override suspend fun realizarCorteZ(idCaja: Int, idUsuario: Int) {
        val idTienda = session.idTienda
        val idCorte = corteAbiertoId(idTienda, idCaja)
            ?: error("No hay una caja abierta para realizar el Corte Z.")

        val v = ventasPeriodo(idTienda, idCorte)
        val (incrementos, retiros) = movimientosPeriodo(idCaja, idCorte)
        val otros = (v.total - v.efectivo - v.tarjeta - v.transfer).coerceAtLeast(0.0)
        val fondoInicial = 0.0
        val efectivoEsperado = fondoInicial + v.efectivo + incrementos - retiros

        db.inTransaction { conn ->
            // Cerrar el corte abierto con los totales del periodo
            conn.executeUpdate(
                """UPDATE dbo.CorteCaja SET
                     HoraFin=CAST(GETDATE() AS TIME),
                     VentasEfectivo=?, VentasTarjeta=?, VentasTransferencia=?, VentasOtros=?,
                     TotalVentas=?, Retiros=?, Incrementos=?,
                     EfectivoEsperado=?, EfectivoReal=?, Diferencia=0, Estatus='Cerrada'
                   WHERE IdCorteCaja=? AND Estatus='Abierta'""",
                listOf(v.efectivo, v.tarjeta, v.transfer, otros,
                       v.total, retiros, incrementos,
                       efectivoEsperado, efectivoEsperado, idCorte)
            )

            // Retiro automático del efectivo esperado → deja el cajón en cero
            if (efectivoEsperado > 0) {
                when (esquemaMovCaja()) {
                    "lower" -> {
                        val apertura = conn.queryOne(
                            """SELECT TOP 1 IdApertura FROM dbo.aperturas_caja
                               WHERE IdTienda=? AND ISNULL(IdCaja,?)=? ORDER BY FechaApertura DESC""",
                            listOf(idTienda, idCaja, idCaja)
                        ) { rs -> rs.getInt("IdApertura") }
                        conn.executeUpdate(
                            """INSERT INTO dbo.movimientos_caja (apertura_id,tipo_movimiento,monto,motivo,usuario_id,fecha)
                               VALUES (?,?,?,?,?,GETDATE())""",
                            listOf(apertura, "Retiro", efectivoEsperado, "Retiro automático - Corte Z", idUsuario)
                        )
                    }
                    "upper" -> conn.executeUpdate(
                        """INSERT INTO dbo.MovimientosCaja (IdTienda,IdUsuario,Tipo,Monto,Concepto,Fecha,IdCaja)
                           VALUES (?,?,'Retiro',?,?,GETDATE(),?)""",
                        listOf(idTienda, idUsuario, efectivoEsperado, "Retiro automático - Corte Z", idCaja)
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONFIGURACIÓN
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun obtenerConfiguracion(idTienda: Int, idCaja: Int): List<ConfigEntry> =
        db.query(
            "SELECT Clave, Valor FROM dbo.ConfiguracionSistema"
        ) { rs -> ConfigEntry(rs.getString("Clave"), rs.getString("Valor") ?: "") }

    override suspend fun guardarConfig(clave: String, valor: String) {
        db.execute(
            """MERGE dbo.ConfiguracionSistema AS d
               USING (SELECT ? AS Clave, ? AS Valor) AS s ON d.Clave = s.Clave
               WHEN MATCHED THEN UPDATE SET Valor = s.Valor
               WHEN NOT MATCHED THEN INSERT (Clave, Valor) VALUES (s.Clave, s.Valor);""",
            listOf(clave, valor)
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS INTERNOS
    // ─────────────────────────────────────────────────────────────────────────

    private fun generarFolio(conn: Connection, serie: String, idTienda: Int, idCaja: Int): String {
        // MaestroComandas y Ventas NO tienen columna IdCaja → folio por tienda.
        val tabla = if (serie == "K") "MaestroComandas" else "Ventas"
        val num = conn.queryInt(
            """SELECT ISNULL(MAX(CAST(SUBSTRING(Folio, LEN(?)+1, 20) AS INT)), 0)+1
               FROM dbo.$tabla WITH (UPDLOCK,HOLDLOCK)
               WHERE IdTienda=? AND Folio LIKE ? AND ISNUMERIC(SUBSTRING(Folio, LEN(?)+1, 20)) = 1""",
            listOf(serie, idTienda, "$serie%", serie)
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
        tipoServicio = optInt("TipoServicio") ?: TipoServicio.COMEDOR,
        nombreCliente = optString("NombreCliente"),
        telefonoCliente = optString("TelefonoCliente"),
        direccionEntrega = optString("DireccionEntrega"),
        idRepartidor = optInt("IdRepartidor"),
        idZonaReparto = optInt("IdZonaReparto"),
        cargoEntrega = optDouble("CargoEntrega"),
        statusEntrega = optInt("StatusEntrega") ?: StatusEntrega.NA
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

    // Accesores opcionales: devuelven default si la columna no existe en el ResultSet
    private fun ResultSet.hasCol(name: String): Boolean =
        try { findColumn(name); true } catch (e: java.sql.SQLException) { false }
    private fun ResultSet.optDouble(name: String, def: Double = 0.0) = if (hasCol(name)) getDouble(name) else def
    private fun ResultSet.optBoolean(name: String, def: Boolean = false) = if (hasCol(name)) getBoolean(name) else def
    private fun ResultSet.optString(name: String): String? = if (hasCol(name)) getString(name) else null
    private fun ResultSet.optInt(name: String): Int? = if (hasCol(name)) getObject(name) as? Int else null
    private fun ResultSet.optImagenBase64(name: String): String? {
        if (!hasCol(name)) return null
        val bytes = getBytes(name) ?: return null
        if (bytes.isEmpty()) return null
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    private fun ResultSet.toArticulo() = Articulo(
        idArticulo = getInt("IdArticulo"),
        clave = optString("Clave") ?: "",
        nombre = getString("Nombre"),
        precioVenta = if (hasCol("PrecioResuelto")) getDouble("PrecioResuelto") else optDouble("PrecioVenta"),
        costo = optDouble("Costo"),
        idCategoria = optInt("IdCategoria") ?: 0,
        codigoBarras = optString("CodigoBarras"),
        esPlatillo = optBoolean("EsPlatillo"),
        esKit = optBoolean("EsKit"),
        esInsumo = optBoolean("EsInsumo"),
        manejaInventario = optBoolean("ManejaInventario"),
        colorBoton = optInt("ColorBoton"),
        idPuntoImpresion = optInt("IdPuntoImpresion"),
        tasaIEPS = optDouble("TasaIEPS"),
        exento = optBoolean("Exento"),
        precioIncluyeImpuesto = optBoolean("PrecioIncluyeImpuesto"),
        iepsTipoFactor = optString("IepsTipoFactor"),
        iepsCuota = optDouble("IepsCuota"),
        tasaIva = optDouble("TasaIVA", 0.16),
        imagenBase64 = optImagenBase64("Imagen")
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
