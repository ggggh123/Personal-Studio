package com.example.personal_studio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AesCbcCryptoTest {

    /** Deterministic IV makes the output reproducible — used as an authoritative
     *  reference value that future refactors must not break.
     *
     *  TODO(p5-polish): replace this self-referential golden with a fixture
     *  captured from a real BIT CAS login response (salt+iv+plain→ciphertext),
     *  so the assertion verifies protocol compat with BIT, not just regression. */
    @Test fun `deterministic IV + salt + prefix yields stable ciphertext`() {
        val salt = "0123456789abcdef"          // 16 ASCII bytes
        val iv = ByteArray(16) { 0x42.toByte() }
        val prefixOverride = "AAAAAAAA"        // skip random prefix
        val cipher = AesCbcCrypto.encryptPassword(
            plain = "hunter2",
            salt = salt,
            iv = iv,
            prefixOverride = prefixOverride,
        )
        // Stable golden value — recompute and update once with a one-time
        // println if the algorithm parameters change deliberately.
        assertEquals("NMcv2anPAtSLodprLMgVfw==", cipher)
    }

    @Test fun `different IVs produce different ciphertexts`() {
        val salt = "0123456789abcdef"
        val iv1 = ByteArray(16) { 0x00 }
        val iv2 = ByteArray(16) { 0x01 }
        val c1 = AesCbcCrypto.encryptPassword("p", salt, iv1, "PFX")
        val c2 = AesCbcCrypto.encryptPassword("p", salt, iv2, "PFX")
        assertNotEquals(c1, c2)
    }

    @Test fun `random invocations differ`() {
        val a = AesCbcCrypto.encryptPassword("samePassword", "0123456789abcdef")
        val b = AesCbcCrypto.encryptPassword("samePassword", "0123456789abcdef")
        assertNotEquals("random IV + random prefix must yield distinct ciphertexts", a, b)
    }

    @Test fun `output is valid base64`() {
        val cipher = AesCbcCrypto.encryptPassword("pwd", "0123456789abcdef")
        java.util.Base64.getDecoder().decode(cipher)
    }
}
