package com.example.security

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal object PinHasher {
    private const val SHA256_PREFIX = "pbkdf2-sha256-v1"
    private const val SHA1_PREFIX = "pbkdf2-sha1-v1"
    private const val ITERATIONS = 210_000
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 1_000_000
    private const val KEY_LENGTH_BITS = 256

    fun hash(pin: String, salt: String): String {
        val algorithm = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            "PBKDF2WithHmacSHA256"
        } catch (_: NoSuchAlgorithmException) {
            "PBKDF2WithHmacSHA1"
        }
        val prefix = if (algorithm.endsWith("SHA256")) SHA256_PREFIX else SHA1_PREFIX
        return prefix + ":" + ITERATIONS + ":" + toHex(derive(pin, salt, ITERATIONS, algorithm))
    }

    fun verify(pin: String, salt: String, storedHash: String): Boolean {
        val parts = storedHash.split(":")
        if (parts.size == 3 && parts[0] in setOf(SHA256_PREFIX, SHA1_PREFIX)) {
            val iterations = parts[1].toIntOrNull() ?: return false
            if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) return false
            val expected = fromHex(parts[2]) ?: return false
            if (expected.size != KEY_LENGTH_BITS / 8) return false
            val algorithm = if (parts[0] == SHA256_PREFIX) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
            return MessageDigest.isEqual(expected, derive(pin, salt, iterations, algorithm))
        }

        // Migrate legacy SHA-256 hashes only after a correct PIN is entered.
        val expectedLegacy = fromHex(storedHash) ?: return false
        val actualLegacy = MessageDigest.getInstance("SHA-256")
            .digest((salt + pin).toByteArray(Charsets.UTF_8))
        return MessageDigest.isEqual(expectedLegacy, actualLegacy)
    }

    fun needsUpgrade(storedHash: String): Boolean =
        !storedHash.startsWith(SHA256_PREFIX + ":") && !storedHash.startsWith(SHA1_PREFIX + ":")

    private fun derive(pin: String, salt: String, iterations: Int, algorithm: String): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt.toByteArray(Charsets.UTF_8), iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun fromHex(value: String): ByteArray? {
        if (value.isEmpty() || value.length % 2 != 0 || value.any { Character.digit(it, 16) < 0 }) return null
        return ByteArray(value.length / 2) { index ->
            val high = Character.digit(value[index * 2], 16)
            val low = Character.digit(value[index * 2 + 1], 16)
            ((high shl 4) or low).toByte()
        }
    }
}
