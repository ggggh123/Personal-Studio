package com.example.personal_studio.core.util

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-CBC + PKCS5 + 16-byte IV password encryption for BIT 统一身份认证 CAS.
 *
 * The CAS server returns a per-request salt (`croypto` field in the login form).
 * The salt itself is used as the AES key. Plaintext is prefixed with 64 random
 * base64 characters so the same password produces a different ciphertext per
 * login — this prevents replay-attack reuse of a captured POST body.
 *
 * IV and prefix are exposed as parameters with default `random*` values, both
 * for testability and so callers can swap them if BIT changes the protocol.
 */
object AesCbcCrypto {

    private const val PREFIX_BYTES = 64
    private const val IV_BYTES = 16

    fun encryptPassword(
        plain: String,
        salt: String,
        iv: ByteArray = randomIv(),
        prefixOverride: String? = null,
    ): String {
        require(salt.toByteArray(Charsets.UTF_8).size == 16) {
            "salt must be 16 ASCII bytes; got ${salt.length}-char salt '$salt'"
        }
        require(iv.size == IV_BYTES) { "iv must be 16 bytes; got ${iv.size}" }
        val key = SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val prefix = prefixOverride ?: randomBase64(PREFIX_BYTES)
        val plaintext = (prefix + plain).toByteArray(Charsets.UTF_8)
        val cipherBytes = cipher.doFinal(plaintext)
        return Base64.getEncoder().encodeToString(cipherBytes)
    }

    private fun randomIv(): ByteArray = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }

    private fun randomBase64(nChars: Int): String {
        val raw = ByteArray((nChars * 3 + 3) / 4).also { SecureRandom().nextBytes(it) }
        return Base64.getEncoder().encodeToString(raw).take(nChars)
    }
}
