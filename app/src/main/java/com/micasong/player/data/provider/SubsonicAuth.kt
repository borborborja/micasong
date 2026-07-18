package com.micasong.player.data.provider

import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Pure, testable Subsonic authentication and URL building (spec §47). Subsonic uses salted-token
 * auth: `t = md5(password + salt)` sent alongside a random `s` (salt), so the plaintext password
 * never travels. Extracted from [SubsonicProvider] so the crypto and query construction can be
 * verified against the official documentation's known vector.
 */
object SubsonicAuth {

    const val API_VERSION = "1.16.1"

    fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** The salted token `t = md5(password + salt)`. */
    fun token(password: String, salt: String): String = md5Hex(password + salt)

    fun randomSalt(random: Random = Random.Default, length: Int = 12): String =
        (1..length).joinToString("") { random.nextInt(16).toString(16) }

    /** Standard auth query parameters shared by every request. */
    fun authParams(username: String, password: String, salt: String, clientName: String): Map<String, String> =
        linkedMapOf(
            "u" to username,
            "t" to token(password, salt),
            "s" to salt,
            "v" to API_VERSION,
            "c" to clientName,
            "f" to "json",
        )

    /** Build a full `{base}/rest/{view}.view?{query}` URL with URL-encoded values. */
    fun endpointUrl(baseUrl: String, view: String, params: Map<String, String>): String {
        val base = baseUrl.trimEnd('/')
        val query = params.entries.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
        return "$base/rest/$view.view?$query"
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
