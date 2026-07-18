package com.micasong.player.data.provider

import android.util.Log
import com.micasong.player.data.db.AlbumEntity
import com.micasong.player.data.db.ArtistEntity
import com.micasong.player.data.db.GenreEntity
import com.micasong.player.data.db.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Plex connector (spec §46). Authenticates with a Plex **access token** ([ProviderConfig.secret]);
 * discovers the music library sections, then reads their tracks via
 * `/library/sections/{id}/all?type=10`. Every request carries `X-Plex-Token` and asks for JSON.
 * Stream URLs are the media Part key + token, so they're directly playable.
 */
class PlexProvider(
    override val config: ProviderConfig,
    private val clientId: String = "micasong-device",
) : MediaProvider {

    override val capabilities = ProviderCapabilities(
        albumMood = true,
        albumStyle = true,
        albumType = true,
        tagsCollections = true,
        playlistImport = true,
        playlistPush = true,
        serverTranscoding = true,
        ratings = true,
        similarArtists = true,
    )

    override suspend fun sync(onProgress: (Float, String) -> Unit): ProviderSnapshot =
        withContext(Dispatchers.IO) {
            val tracks = ArrayList<TrackEntity>()
            val albums = LinkedHashMap<Long, AlbumEntity>()
            val genres = LinkedHashMap<String, Int>()
            try {
                onProgress(0f, "Conectando")
                val base = config.primaryUrl ?: return@withContext empty()
                val sections = getJson(url(base, "/library/sections"))
                    ?.optJSONObject("MediaContainer")?.optJSONArray("Directory")
                    ?: return@withContext empty()

                // Music sections are those whose type is "artist".
                val musicSectionKeys = (0 until sections.length())
                    .map { sections.getJSONObject(it) }
                    .filter { it.optString("type") == "artist" }
                    .map { it.optString("key") }

                onProgress(0.3f, "Recopilando pistas")
                for (key in musicSectionKeys) {
                    val meta = getJson(url(base, "/library/sections/$key/all", mapOf("type" to "10")))
                        ?.optJSONObject("MediaContainer")?.optJSONArray("Metadata") ?: continue
                    for (i in 0 until meta.length()) {
                        val item = meta.getJSONObject(i)
                        val partKey = PlexMappers.partKey(item) ?: continue
                        val streamUrl = url(base, partKey)
                        val coverUrl = item.optString("thumb").ifBlank { null }?.let { url(base, it) }
                        val track = PlexMappers.parseTrack(item, config.id, streamUrl, coverUrl)
                        tracks += track
                        track.albumId?.let { albumId ->
                            albums.getOrPut(albumId) {
                                AlbumEntity(albumId, track.albumName ?: "?", (track.albumName ?: "").lowercase(),
                                    track.artistName, track.artistId, track.year, 0, 0, null)
                            }
                        }
                        track.genre?.let { genres[it] = (genres[it] ?: 0) + 1 }
                    }
                }
                onProgress(1f, "Completado")
            } catch (e: Exception) {
                Log.w("PlexProvider", "sync failed: ${e.message}")
            }
            ProviderSnapshot(
                tracks,
                albums.values.toList(),
                emptyList<ArtistEntity>(),
                genres.entries.map { GenreEntity(StableId.of(it.key), it.key, it.value) },
            )
        }

    override suspend fun streamUri(track: TrackEntity, maxBitrate: Int): String = track.mediaUri

    private fun empty() = ProviderSnapshot(emptyList(), emptyList(), emptyList(), emptyList())

    /** Build `{base}{path}?X-Plex-Token=…&{params}` with URL-encoded values. */
    private fun url(base: String, path: String, params: Map<String, String> = emptyMap()): String {
        val b = base.trimEnd('/')
        val p = if (path.startsWith("/")) path else "/$path"
        val sep = if (path.contains("?")) "&" else "?"
        val all = params + mapOf("X-Plex-Token" to (config.secret ?: ""))
        val query = all.entries.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
        return "$b$p$sep$query"
    }

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    private fun getJson(urlString: String): JSONObject? = try {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 15000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Plex-Client-Identifier", clientId)
            setRequestProperty("X-Plex-Product", "MiCaSong")
            config.secret?.let { setRequestProperty("X-Plex-Token", it) }
        }
        conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    } catch (e: Exception) {
        Log.w("PlexProvider", "request failed: ${e.message}")
        null
    }
}
