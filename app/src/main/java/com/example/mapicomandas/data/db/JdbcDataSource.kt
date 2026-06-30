package com.example.mapicomandas.data.db

import com.example.mapicomandas.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JdbcDataSource @Inject constructor(
    private val session: SessionManager
) {
    private var _connection: Connection? = null

    private fun buildUrl(): String {
        val cfg = session.dbConfig
        // encrypt=false evita el handshake TLS que falla en Android con mssql-jdbc
        // (AssertionError numMsgsRcvd/numMsgsSent). Para LAN/on-premise es lo correcto.
        return "jdbc:sqlserver://${cfg.host}:${cfg.puerto};" +
                "databaseName=${cfg.baseDatos};" +
                "user=${cfg.usuario};" +
                "password=${cfg.password};" +
                "encrypt=false;" +
                "trustServerCertificate=true;" +
                "loginTimeout=30;"
    }

    suspend fun getConnection(): Connection = withContext(Dispatchers.IO) {
        val conn = _connection
        if (conn != null && !conn.isClosed) return@withContext conn
        try {
            // Registrar el driver explícitamente (Android no usa ServiceLoader de JDBC)
            val driver = Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
                .getDeclaredConstructor().newInstance() as java.sql.Driver
            DriverManager.registerDriver(driver)
            val newConn = DriverManager.getConnection(buildUrl())
            _connection = newConn
            newConn
        } catch (t: Throwable) {
            // Convertir cualquier Error del driver (NoClassDefFound, etc.) en Exception capturable
            throw java.sql.SQLException(
                "Error de conexión JDBC: ${t.javaClass.simpleName}: ${t.message}", t
            )
        }
    }

    suspend fun <T> query(sql: String, params: List<Any?> = emptyList(), map: (ResultSet) -> T): List<T> =
        withContext(Dispatchers.IO) {
            getConnection().prepareStatement(sql).use { stmt ->
                setParams(stmt, params)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<T>()
                    while (rs.next()) result.add(map(rs))
                    result
                }
            }
        }

    suspend fun <T> queryOne(sql: String, params: List<Any?> = emptyList(), map: (ResultSet) -> T): T? =
        withContext(Dispatchers.IO) {
            getConnection().prepareStatement(sql).use { stmt ->
                setParams(stmt, params)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) map(rs) else null
                }
            }
        }

    suspend fun execute(sql: String, params: List<Any?> = emptyList()) =
        withContext(Dispatchers.IO) {
            getConnection().prepareStatement(sql).use { stmt ->
                setParams(stmt, params)
                stmt.executeUpdate()
            }
        }

    suspend fun executeAndGetId(sql: String, params: List<Any?> = emptyList()): Int =
        withContext(Dispatchers.IO) {
            getConnection().prepareStatement(
                sql, java.sql.Statement.RETURN_GENERATED_KEYS
            ).use { stmt ->
                setParams(stmt, params)
                stmt.executeUpdate()
                stmt.generatedKeys.use { rs ->
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
        }

    suspend fun <T> inTransaction(block: suspend (Connection) -> T): T =
        withContext(Dispatchers.IO) {
            val conn = getConnection()
            val prevAutoCommit = conn.autoCommit
            conn.autoCommit = false
            try {
                val result = block(conn)
                conn.commit()
                result
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = prevAutoCommit
            }
        }

    private fun setParams(stmt: PreparedStatement, params: List<Any?>) {
        params.forEachIndexed { i, v ->
            when (v) {
                null -> stmt.setNull(i + 1, java.sql.Types.NULL)
                is Int -> stmt.setInt(i + 1, v)
                is Long -> stmt.setLong(i + 1, v)
                is Double -> stmt.setDouble(i + 1, v)
                is String -> stmt.setString(i + 1, v)
                is Boolean -> stmt.setBoolean(i + 1, v)
                is java.math.BigDecimal -> stmt.setBigDecimal(i + 1, v)
                else -> stmt.setObject(i + 1, v)
            }
        }
    }

    fun invalidate() {
        _connection?.runCatching { close() }
        _connection = null
    }
}
