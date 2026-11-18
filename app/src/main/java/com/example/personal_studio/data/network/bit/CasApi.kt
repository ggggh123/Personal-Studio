package com.example.personal_studio.data.network.bit

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.SecureRandom
import java.util.Base64
import java.util.Random

/**
 * The school CAS's own JSON API, used for the extra sign-in steps (SMS and picture captcha),
 * called the way its login page calls it: CSRF headers on `protected` paths, and encryption for
 * the paths the server lists in its `URL_CRYPTO_DATA` config (see [CasCrypto]). One instance
 * lives for one sign-in; it shares the session's cookies, so the CAS sees one browser.
 */
internal class CasApi(private val session: BitAuthSession, private val random: Random = SecureRandom()) {
    /** The page the steps belong to, sent as Referer. */
    var referer: HttpUrl? = null
    private val base = session.urls.casLogin.toHttpUrl()
    private var encryptedPaths: List<String>? = null

    suspend fun get(path: String): JsonObject? = call(url(path, cacheBust = true), path, null)

    suspend fun post(path: String, body: JsonElement): JsonObject? = call(url(path, cacheBust = false), path, body)

    /** The picture captcha the CAS wants before checking [username]'s password; null when none (or unknown). */
    suspend fun captchaFor(username: String): CasCaptcha? {
        val url = url(CAPTCHA_COUNT, cacheBust = true).newBuilder().addPathSegment(username).build()
        val data = call(url, CAPTCHA_COUNT, null)?.get("data") as? JsonObject ?: return null
        if (!truthy(data["captchaInvisible"])) return null
        val image = (data["captchaUrl"] as? JsonPrimitive)?.contentOrNull?.let(::dataUrlBytes)
        return CasCaptcha(image, data["captchaPayload"] as? JsonObject ?: JsonObject(emptyMap()))
    }

    private suspend fun call(url: HttpUrl, path: String, body: JsonElement?): JsonObject? {
        val headers = protectedHeaders(path)
        if (body != null && needsEncryption(path)) return encrypted(url, body, headers)
        return parse(session.requestApi(url, body?.toString(), headers, referer).body)
    }

    private suspend fun encrypted(url: HttpUrl, body: JsonElement, headers: Map<String, String>): JsonObject? {
        val key = CasCrypto.newAesKey(random)
        val cryptoHeaders = mapOf("hasCrypto" to "true", "privateKey" to CasCrypto.rsaEncrypt(CasCrypto.base64(key)))
        var text = session.requestApi(url, CasCrypto.aesEncrypt(body.toString(), key), headers + cryptoHeaders, referer).body
        // The reply is Base64 ciphertext, sometimes encrypted twice; plain JSON when the server skipped it.
        repeat(3) {
            text = text.trim().removeSurrounding("\"")
            if (looksLikeJson(text)) return parse(text)
            text = try { CasCrypto.aesDecrypt(text, key) } catch (_: Exception) { return null }
        }
        return parse(text)
    }

    private suspend fun needsEncryption(path: String): Boolean {
        val paths = encryptedPaths ?: loadEncryptedPaths().also { encryptedPaths = it }
        return paths.any { matches(path, it) }
    }

    /** The server's list of paths that must be encrypted; the known one when it cannot be read. */
    private suspend fun loadEncryptedPaths(): List<String> = try {
        val config = encrypted(
            url(CRYPTO_CONFIG, cacheBust = false),
            JsonArray(listOf(JsonPrimitive("URL_CRYPTO_DATA"))),
            protectedHeaders(CRYPTO_CONFIG),
        )
        val data = (config?.get("data") as? JsonObject ?: config)?.get("URL_CRYPTO_DATA") as? JsonObject
        (data?.get("SID") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.takeIf { it.isNotEmpty() } ?: DEFAULT_ENCRYPTED
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        DEFAULT_ENCRYPTED
    }

    private fun protectedHeaders(path: String): Map<String, String> =
        if ("protected" in path) CasCrypto.csrfHeaders(random) + ("Sid-Language" to "zh_CN") else emptyMap()

    /** GETs carry a timestamp as their query, like the school's page, so nothing caches them. */
    private fun url(path: String, cacheBust: Boolean): HttpUrl {
        val url = base.resolve(path) ?: error("bad CAS path")
        return if (cacheBust) url.newBuilder().encodedQuery(System.currentTimeMillis().toString()).build() else url
    }

    companion object {
        const val CRYPTO_CONFIG = "/gate/sso-extend/protected/api/dictconfig/get"
        private const val CAPTCHA_COUNT = "/cas/api/protected/user/findCaptchaCount/"
        val DEFAULT_ENCRYPTED = listOf("/api/protected/sms/getPhoneNumberByUserId")
        private val json = Json { ignoreUnknownKeys = true }

        /** The config lists paths below `/cas` or `/gate`; `xxx` and `*` stand for one segment. */
        fun matches(path: String, pattern: String): Boolean {
            fun segments(p: String) = p.substringBefore('?').trim('/').split('/')
            val want = segments(pattern)
            return listOf(path, path.removePrefix("/cas"), path.removePrefix("/gate")).any { candidate ->
                val have = segments(candidate)
                have.size == want.size && want.indices.all { want[it] == "xxx" || want[it] == "*" || want[it] == have[it] }
            }
        }

        fun parse(text: String): JsonObject? = try {
            json.parseToJsonElement(text.trim()) as? JsonObject
        } catch (_: Exception) {
            null
        }

        fun code(reply: JsonObject): Int? = (reply["code"] as? JsonPrimitive)?.intOrNull

        /** The school's message, wherever this reply keeps it; never a JSON blob. */
        fun message(reply: JsonObject): String {
            listOf("message", "msg", "errorMessage").forEach { key ->
                (reply[key] as? JsonPrimitive)?.contentOrNull?.trim()
                    ?.takeIf { it.isNotEmpty() && !it.startsWith("{") && it != "OK" }?.let { return it }
            }
            return (reply["data"] as? JsonObject)?.let(::message).orEmpty()
        }

        fun truthy(value: JsonElement?): Boolean = (value as? JsonPrimitive)?.let {
            it.booleanOrNull ?: it.contentOrNull?.equals("true", ignoreCase = true)
        } == true

        private fun looksLikeJson(text: String) = text.startsWith("{") || text.startsWith("[")

        /** `data:image/png;base64,...` → the image bytes; null for anything else. */
        fun dataUrlBytes(url: String): ByteArray? {
            val comma = url.indexOf(',')
            if (!url.startsWith("data:") || comma < 0 || ";base64" !in url.substring(0, comma)) return null
            return try { Base64.getDecoder().decode(url.substring(comma + 1).trim()) } catch (_: IllegalArgumentException) { null }
        }
    }
}

/** The CAS's picture captcha: [image] to show (null if it came in a form we cannot show) and the payload to echo back. */
internal class CasCaptcha(val image: ByteArray?, val payload: JsonObject)
