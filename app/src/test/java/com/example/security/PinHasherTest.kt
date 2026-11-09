package com.example.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class PinHasherTest {
    @Test
    fun newHashVerifiesOnlyTheCorrectPin() {
        val salt = "9f83b4a72c19d6305e18a2b4c7d901fe"
        val hash = PinHasher.hash("483920", salt)
        assertTrue(PinHasher.verify("483920", salt, hash))
        assertFalse(PinHasher.verify("483921", salt, hash))
        assertFalse(PinHasher.needsUpgrade(hash))
    }

    @Test
    fun legacyHashCanBeVerifiedAndMarkedForMigration() {
        val salt = "9f83b4a72c19d6305e18a2b4c7d901fe"
        val legacy = MessageDigest.getInstance("SHA-256")
            .digest((salt + "483920").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertTrue(PinHasher.verify("483920", salt, legacy))
        assertFalse(PinHasher.verify("000000", salt, legacy))
        assertTrue(PinHasher.needsUpgrade(legacy))
    }
}
