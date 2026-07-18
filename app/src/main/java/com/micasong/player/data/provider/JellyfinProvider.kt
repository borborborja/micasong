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
                for (i in 0 until items.length()) {
                    val it = items.getJSONObject(i)
                    val id = it.optString("Id").stableId()
                    val albumName = it.optString("Album").ifBlank { "Álbum desconocido" }
                    val albumId = it.optString("AlbumId").ifBlank { albumName }.stableId()
                    val artistName = it.optString("AlbumArtist").ifBlank { it.optString("Artists") }
                    val artistId = artistName.stableId()
                    val year = it.optInt("ProductionYear").takeIf { y -> y > 0 }
                    tracks += TrackEntity(
                        id = id,
                        providerId = config.id,
                        mediaUri = it.optString("Id"),   // server item id; resolved by streamUri
                        title = it.optString("Name", "Sin título"),
                        titleSort = it.optString("Name").lowercase(),
                        albumId = albumId,
                        albumName = albumName,
                        artistId = artistId,
                        artistName = artistName.ifBlank { "Artista desconocido" },
                        albumArtist = it.optString("AlbumArtist").ifBlank { null },
                        trackNumber = it.optInt("IndexNumber").takeIf { n -> n > 0 },
                        discNumber = it.optInt("ParentIndexNumber").takeIf { n -> n > 0 },
                        durationMs = it.optLong("RunTimeTicks") / 10_000,   // ticks (100 ns) → ms
                        year = year,
                        genre = it.optJSONArray("Genres")?.optString(0),
                        mimeType = null,
                        bitrate = null,
                        sampleRate = null,
                        bitDepth = null,
                        sizeBytes = null,
                        artworkUri = JellyfinAuth.endpointUrl(config.primaryUrl!!, "/Items/${it.optString("Id")}/Images/Primary"),
                        dateAdded = 0L,
                    )
                    albums.getOrPut(albumId) {
                        AlbumEntity(albumId, albumName, albumName.lowercase(), artistName, artistId, year, 0, 0, null)
                    }
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

    override suspend fun streamUri(track: TrackEntity, maxBitrate: Int): String {
        val params = buildMap {
            put("static", if (maxBitrate > 0) "false" else "true")
            if (maxBitrate > 0) put("maxStreamingBitrate", maxBitrate.toString())
            config.secret?.let { put("api_key", it) }
        }
        return JellyfinAuth.endpointUrl(config.primaryUrl ?: "", "/Audio/${track.mediaUri}/universal", params)
    }

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
