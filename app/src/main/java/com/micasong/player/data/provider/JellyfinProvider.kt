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
import kotlin.math.abs

/**
 * Jellyfin connector (spec §46). Uses [JellyfinAuth] for header/URL building and reads the music
 * library through the `/Users/{id}/Items` endpoint. Like the other server providers, network
 * calls are defensive: a failure yields an empty snapshot so the app stays usable offline (§2).
 *
 * The [ProviderConfig.secret] holds the access token and [ProviderConfig.username] the user id
 * (obtained from an interactive AuthenticateByName step handled by the settings flow).
 */
class JellyfinProvider(
    override val config: ProviderConfig,
    private val deviceId: String = "micasong-device",
) : MediaProvider {

    override val capabilities = ProviderCapabilities(
        multiArtist = true,
        multiGenre = true,
        composers = true,
        playlistImport = true,
        playlistPush = true,
        serverTranscoding = true,
        ratings = true,
        tagsCollections = true,
    )

    override suspend fun sync(onProgress: (Float, String) -> Unit): ProviderSnapshot =
        withContext(Dispatchers.IO) {
            val tracks = ArrayList<TrackEntity>()
            val albums = LinkedHashMap<Long, AlbumEntity>()
            val artists = LinkedHashMap<Long, ArtistEntity>()
            val genres = LinkedHashMap<String, Int>()
            try {
                onProgress(0f, "Conectando")
                val userId = config.username ?: return@withContext empty()
                val url = JellyfinAuth.endpointUrl(
                    config.primaryUrl ?: return@withContext empty(),
                    "/Users/$userId/Items",
                    mapOf(
                        "IncludeItemTypes" to "Audio",
                        "Recursive" to "true",
                        "Fields" to "Genres,MediaSources,DateCreated,ProductionYear",
                        "SortBy" to "SortName",
                    ),
                )
                val json = getJson(url) ?: return@withContext empty()
                val items = json.optJSONArray("Items") ?: return@withContext empty()
                onProgress(0.5f, "Recopilando pistas")
                val base = config.primaryUrl ?: return@withContext empty()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val itemId = item.optString("Id")
                    // Playable stream URL (a bare item id isn't playable): direct stream + api_key.
                    val streamUrl = JellyfinAuth.endpointUrl(
                        base, "/Audio/$itemId/stream",
                        buildMap { put("static", "true"); config.secret?.let { put("api_key", it) } },
                    )
                    val coverUrl = JellyfinAuth.endpointUrl(
                        base, "/Items/$itemId/Images/Primary",
                        buildMap { config.secret?.let { put("api_key", it) } },
                    )
                    val track = JellyfinMappers.parseItem(item, config.id, streamUrl, coverUrl)
                    tracks += track
                    track.albumId?.let { albumId ->
                        albums.getOrPut(albumId) {
                            AlbumEntity(albumId, track.albumName ?: "?", (track.albumName ?: "").lowercase(),
                                track.artistName, track.artistId, track.year, 0, 0, null)
                        }
                    }
                    item.optJSONArray("Genres")?.optString(0)?.ifBlank { null }?.let { genres[it] = (genres[it] ?: 0) + 1 }
                }
                onProgress(1f, "Completado")
            } catch (e: Exception) {
                Log.w("JellyfinProvider", "sync failed: ${e.message}")
            }
            ProviderSnapshot(
                tracks,
                albums.values.toList(),
                artists.values.toList(),
                genres.entries.map { GenreEntity(it.key.stableId(), it.key, it.value) },
            )
        }

    // Tracks already carry a ready-to-play, authenticated stream URL in mediaUri (built at sync).
    override suspend fun streamUri(track: TrackEntity, maxBitrate: Int): String = track.mediaUri

    private fun empty() = ProviderSnapshot(emptyList(), emptyList(), emptyList(), emptyList())

    private fun getJson(urlString: String): JSONObject? = try {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 15000
            requestMethod = "GET"
            setRequestProperty("Authorization", JellyfinAuth.authorizationHeader(deviceId, token = config.secret))
            config.secret?.let { setRequestProperty("X-Emby-Token", it) }
        }
        conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    } catch (e: Exception) {
        Log.w("JellyfinProvider", "request failed: ${e.message}")
        null
    }

    private fun String.stableId(): Long = abs(hashCode().toLong()) or 1L
}
