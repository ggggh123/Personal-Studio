package com.example.personal_studio.core.util

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * AES-ECB password encryption for BIT 统一身份认证 CAS (Wisedu 金智 platform).
 *
 * The CAS server returns a per-request salt (`croypto`) which is a base64-encoded
 * 16-byte value — that's the AES-128 key. Encryption is **AES/ECB/PKCS5Padding**:
 * no IV, no random prefix. ECB is deterministic, so the server simply ECB-decrypts
 * and string-compares; there's nothing to transmit besides the ciphertext.
 *
 * **History**: the first P5 iteration used AES-CBC with a random IV + a 64-char
 * random prefix (a different Wisedu variant). Real-device DoD always returned
 * "用户名或密码错误" because the server ECB-decrypted our CBC ciphertext into
 * garbage. Confirmed the correct scheme is ECB against the BIT101 reference
 * (`AESUtils`, AGPL — observed, not copied) plus live testing.
 */
object CasAesCrypto {

    private const val KEY_BYTES = 16

    fun encryptPassword(plain: String, salt: String): String {
        val keyBytes = try {
            Base64.getDecoder().decode(salt)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("salt is not valid base64: '$salt'", e)
        }
        require(keyBytes.size == KEY_BYTES) {
            "salt must base64-decode to $KEY_BYTES bytes (AES-128 key); " +
                "got ${keyBytes.size} bytes from '$salt'"
        }
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
        val cipherBytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipherBytes)
    }
}
