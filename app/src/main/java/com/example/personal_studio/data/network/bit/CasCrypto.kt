package com.example.personal_studio.data.network.bit

import android.annotation.SuppressLint
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Random
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * The extra crypto the school's CAS front end applies to its JSON API, as its own login page
 * does it (sso.bit.edu.cn `cas-gateway` bundle, read 2026-11):
 *
 * - **CSRF headers** on every `protected` path: `Csrf-Key` is 32 random alphanumerics,
 *   `Csrf-Value` the MD5 hex of its Base64 with the first and second halves wrapped around it.
 * - **Encrypted calls** for the paths the server lists under `URL_CRYPTO_DATA`: a random 16-byte
 *   AES key (sent as Base64 text) encrypts the JSON body with AES/ECB/PKCS5; the key text is
 *   RSA/PKCS#1-encrypted with the school's public key into the `privateKey` header, with
 *   `hasCrypto: true`. The reply is Base64 AES ciphertext with the same key, sometimes twice.
 *
 * None of this protects a secret of ours; it only has to match what the server expects.
 */
internal object CasCrypto {
    /** The RSA key embedded in the school's login page script. */
    const val URL_CRYPTO_PUBLIC_KEY =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjVr1zKwohU3xA0afprWLSQvIymaSH/V27MedFc+CecXSnORIFMAp4uEIb4ta" +
            "Dq/2X4eMeTI66Mu/rB5GKSFDbExF2Gu4NaO/CNDpf1gHMScUrIFCh4CDqzBnx17kclvezLkIK0T8FVa4cRsINvzjbnA6jUSMaf6Fm1n9" +
            "wTAtW6QYBjssGOEtCj+c38PTBdFMmJbXp3brt1tEBesz6lb3Fjp76FGvDZ08xtYG8fxYPuiMwKU04eS+mcX/BunwgpU3zwekHYB+PWRI" +
            "vq0lBry9Wms25sJE5T/RAv5fEuMLbBkfcZK3+7ivSZthTmPpr2Ap/ji70ZZ6u2jvR5VJq+LJHQIDAQAB"

    private const val ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    fun csrfHeaders(random: Random = SecureRandom()): Map<String, String> {
        val key = buildString { repeat(32) { append(ALPHANUMERIC[random.nextInt(ALPHANUMERIC.length)]) } }
        val encoded = b64.encodeToString(key.toByteArray(Charsets.US_ASCII))
        val half = encoded.length / 2
        val mixed = encoded.substring(0, half) + encoded + encoded.substring(half)
        return mapOf("Csrf-Key" to key, "Csrf-Value" to md5Hex(mixed))
    }

    fun newAesKey(random: Random = SecureRandom()): ByteArray = ByteArray(16).also { random.nextBytes(it) }

    fun aesEncrypt(plain: String, key: ByteArray): String =
        b64.encodeToString(aes(Cipher.ENCRYPT_MODE, key).doFinal(plain.toByteArray(Charsets.UTF_8)))

    fun aesDecrypt(cipherBase64: String, key: ByteArray): String =
        String(aes(Cipher.DECRYPT_MODE, key).doFinal(unb64.decode(cipherBase64.trim())), Charsets.UTF_8)

    /** RSA/PKCS#1 v1.5 of [text] (the AES key's Base64), as Base64. */
    fun rsaEncrypt(text: String, publicKeyBase64: String = URL_CRYPTO_PUBLIC_KEY): String {
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(unb64.decode(publicKeyBase64)))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.ENCRYPT_MODE, key) }
        return b64.encodeToString(cipher.doFinal(text.toByteArray(Charsets.US_ASCII)))
    }

    fun base64(bytes: ByteArray): String = b64.encodeToString(bytes)

    // ECB is what the school's server decrypts; this protects nothing of ours (see the class note).
    @SuppressLint("GetInstance")
    private fun aes(mode: Int, key: ByteArray): Cipher =
        Cipher.getInstance("AES/ECB/PKCS5Padding").apply { init(mode, SecretKeySpec(key, "AES")) }

    private fun md5Hex(text: String): String = MessageDigest.getInstance("MD5")
        .digest(text.toByteArray(Charsets.US_ASCII)).joinToString("") { "%02x".format(it) }
}
