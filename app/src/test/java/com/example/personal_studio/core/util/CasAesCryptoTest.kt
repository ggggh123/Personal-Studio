package com.example.personal_studio.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CasAesCryptoTest {

    // Test salts are base64-encoded 16-byte values, matching the real-server
    // contract (BIT's CAS returns the salt as base64 — e.g. `zWC9gZm+MKdxkZP6Sitlvg==`).
    // B64_KEY_ABCDEF decodes to the 16 ASCII bytes "0123456789abcdef".
    private val B64_KEY_ABCDEF = "MDEyMzQ1Njc4OWFiY2RlZg=="     // base64 of "0123456789abcdef"

    /** AES-ECB is deterministic, so a fixed (plain, key) pair yields a stable
     *  ciphertext. Golden value computed independently (pure-Python AES-128-ECB
     *  reference) to verify our impl matches the standard, not just itself. */
    @Test fun `ECB yields the expected golden ciphertext`() {
        val cipher = CasAesCrypto.encryptPassword(plain = "hunter2", salt = B64_KEY_ABCDEF)
        assertEquals("rVYw5JZRZ03Wi78zJzGfGQ==", cipher)
    }

    @Test fun `ECB is deterministic — same input yields same ciphertext`() {
        val a = CasAesCrypto.encryptPassword("samePassword", B64_KEY_ABCDEF)
        val b = CasAesCrypto.encryptPassword("samePassword", B64_KEY_ABCDEF)
        assertEquals(a, b)
    }

    @Test fun `output is valid base64`() {
        val cipher = CasAesCrypto.encryptPassword("pwd", B64_KEY_ABCDEF)
        java.util.Base64.getDecoder().decode(cipher)
    }

    @Test fun `non-base64 salt fails with informative message`() {
        try {
            CasAesCrypto.encryptPassword("p", salt = "not-base64-because-of-dashes!")
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assert(e.message?.contains("base64", ignoreCase = true) == true) {
                "unexpected message: ${e.message}"
            }
        }
    }

    @Test fun `wrong-length decoded salt fails`() {
        // base64 of 8 bytes (not 16): "MTIzNDU2Nzg=" = "12345678"
        try {
            CasAesCrypto.encryptPassword("p", salt = "MTIzNDU2Nzg=")
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assert(e.message?.contains("16 bytes") == true) {
                "unexpected message: ${e.message}"
            }
        }
    }
}
