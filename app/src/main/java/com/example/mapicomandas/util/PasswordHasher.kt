package com.example.mapicomandas.util

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Verificación de contraseñas compatible con MapiPOS (PasswordHasher.cs).
 * Algoritmo: PBKDF2-SHA256, 100 000 iteraciones, salt de 16 bytes, hash de 32 bytes.
 * El hash y el salt se guardan en SQL Server como VARBINARY crudo (no base64).
 */
object PasswordHasher {

    private const val ITERATIONS = 100_000
    private const val KEY_SIZE_BITS = 256  // 32 bytes

    /**
     * Verifica [password] contra el [hash] y [salt] almacenados (bytes crudos del VARBINARY).
     * Comparación en tiempo constante.
     */
    fun verify(password: String, hash: ByteArray, salt: ByteArray): Boolean {
        val derived = derive(password, salt)
        return fixedTimeEquals(derived, hash)
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_SIZE_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun fixedTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
        return result == 0
    }
}
