package com.example.personal_studio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AesCbcCryptoTest {

    // Test salts are base64-encoded 16-byte values, matching the real-server
    // contract (BIT's CAS returns the salt as base64 — e.g. `zWC9gZm+MKdxkZP6Sitlvg==`).
    // `B64_KEY_ABCDEF` decodes to the 16 ASCII bytes "0123456789abcdef" so it
    // yields the same ciphertext the original test (pre-base64-fix) recorded.
    private val B64_KEY_ABCDEF = "MDEyMzQ1Njc4OWFiY2RlZg=="     // base64 of "0123456789abcdef"

    /** Deterministic IV makes the output reproducible — used as an authoritative
     *  reference value that future refactors must not break.
     *
     *  TODO(p5-polish): replace this self-referential golden with a fixture
     *  captured from a real BIT CAS login response (salt+iv+plain→ciphertext),
     *  so the assertion verifies protocol compat with BIT, not just regression. */
    @Test fun `deterministic IV + salt + prefix yields stable ciphertext`() {
        val iv = ByteArray(16) { 0x42.toByte() }
        val prefixOverride = "AAAAAAAA"
        val cipher = AesCbcCrypto.encryptPassword(
            plain = "hunter2",
            salt = B64_KEY_ABCDEF,
            iv = iv,
            prefixOverride = prefixOverride,
        )
        // Golden ciphertext: same as pre-base64-fix because the decoded key
        // bytes are identical to the old ASCII-key bytes.
        assertEquals("NMcv2anPAtSLodprLMgVfw==", cipher)
    }

    @Test fun `different IVs produce different ciphertexts`() {
        val iv1 = ByteArray(16) { 0x00 }
        val iv2 = ByteArray(16) { 0x01 }
        val c1 = AesCbcCrypto.encryptPassword("p", B64_KEY_ABCDEF, iv1, "PFX")
        val c2 = AesCbcCrypto.encryptPassword("p", B64_KEY_ABCDEF, iv2, "PFX")
        assertNotEquals(c1, c2)
    }

    @Test fun `random invocations differ`() {
        val a = AesCbcCrypto.encryptPassword("samePassword", B64_KEY_ABCDEF)
        val b = AesCbcCrypto.encryptPassword("samePassword", B64_KEY_ABCDEF)
        assertNotEquals("random IV + random prefix must yield distinct ciphertexts", a, b)
    }

    @Test fun `output is valid base64`() {
        val cipher = AesCbcCrypto.encryptPassword("pwd", B64_KEY_ABCDEF)
        java.util.Base64.getDecoder().decode(cipher)
    }

    @Test fun `non-base64 salt fails with informative message`() {
        try {
            AesCbcCrypto.encryptPassword("p", salt = "not-base64-because-of-dashes!")
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Either "not valid base64" or "must base64-decode to 16 bytes" is acceptable
            assert(e.message?.contains("base64", ignoreCase = true) == true) {
                "unexpected message: ${e.message}"
            }
        }
    }

    @Test fun `wrong-length decoded salt fails`() {
        // base64 of 8 bytes (not 16): "MTIzNDU2Nzg=" = "12345678"
        try {
            AesCbcCrypto.encryptPassword("p", salt = "MTIzNDU2Nzg=")
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assert(e.message?.contains("16 bytes") == true) {
                "unexpected message: ${e.message}"
            }
        }
    }
}
