package com.micasong.player.data.radio

import com.micasong.player.data.playlist.PlaylistFileParser
import java.net.URI
import kotlin.random.Random

/** An internet radio station node (spec §10). */
data class RadioStation(
    val id: Long,
    val name: String,
    val streamUrl: String,
    val homepage: String? = null,
    val imageUrl: String? = null,
)

/** A stream URL with any embedded HTTP basic-auth credentials split out. */
data class RadioAuth(val url: String, val username: String?, val password: String?) {
    val hasAuth: Boolean get() = username != null
}

/**
 * Internet radio behaviour (spec §10): clicking a station queues *all* stations (so skip cycles
 * through them), a stream URL may carry `user:pass@` basic-auth credentials, and a `.pls` stream
 * list resolves to a random entry per play intent. Pure and unit-testable.
 */
object InternetRadio {

    /** Rotate the station list so the clicked station leads — skip then cycles through the rest. */
    fun orderedQueue(stations: List<RadioStation>, clickedId: Long): List<RadioStation> {
        val idx = stations.indexOfFirst { it.id == clickedId }
        if (idx <= 0) return stations
        return stations.subList(idx, stations.size) + stations.subList(0, idx)
    }

    /** Split embedded `http://user:pass@host/…` basic-auth credentials from the URL. */
    fun extractBasicAuth(url: String): RadioAuth = try {
        val uri = URI(url)
        val userInfo = uri.userInfo
        if (userInfo.isNullOrEmpty()) {
            RadioAuth(url, null, null)
        } else {
            val parts = userInfo.split(":", limit = 2)
            val clean = URI(uri.scheme, null, uri.host, uri.port, uri.path, uri.query, uri.fragment).toString()
            RadioAuth(clean, parts[0], parts.getOrNull(1))
        }
    } catch (e: Exception) {
        RadioAuth(url, null, null)
    }

    /** Pick a random stream entry from a `.pls` playlist body (spec §10 "random PLS entry"). */
    fun randomEntry(plsContent: String, random: Random = Random.Default): String? {
        val paths = PlaylistFileParser.parsePls(plsContent).paths
        return paths.randomOrNull(random)
    }
}
