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

/**
 * Emby connector (spec §46). Emby shares its media API and `AuthenticateByName` flow with Jellyfin
 * (common ancestry), so this reuses [JellyfinAuth] URL/header building and [JellyfinMappers], with
 * one difference: Emby carries the client identity in the `X-Emby-Authorization` header (and the
 * token in `X-Emby-Token`) rather than the `Authorization` header.
 *
 * [ProviderConfig.secret] holds the access token and [ProviderConfig.username] the user id.
 */
class EmbyProvider(
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
                val base = config.primaryUrl ?: return@withContext empty()
                val url = JellyfinAuth.endpointUrl(
                    base, "/Users/$userId/Items",
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
                    val item = items.getJSONObject(i)
                    val itemId = item.optString("Id")
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
                Log.w("EmbyProvider", "sync failed: ${e.message}")
            }
            ProviderSnapshot(
                tracks,
                albums.values.toList(),
                artists.values.toList(),
                genres.entries.map { GenreEntity(StableId.of(it.key), it.key, it.value) },
            )
        }

    override suspend fun streamUri(track: TrackEntity, maxBitrate: Int): String = track.mediaUri

    private fun empty() = ProviderSnapshot(emptyList(), emptyList(), emptyList(), emptyList())

    private fun getJson(urlString: String): JSONObject? = try {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 15000
            requestMethod = "GET"
            setRequestProperty("X-Emby-Authorization", JellyfinAuth.authorizationHeader(deviceId, token = config.secret))
            config.secret?.let { setRequestProperty("X-Emby-Token", it) }
        }
        conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    } catch (e: Exception) {
        Log.w("EmbyProvider", "request failed: ${e.message}")
        null
    }

    /** The result of a successful Emby login (token + user id). */
    data class EmbySession(val token: String, val userId: String)

    companion object {
        /** Authenticate against Emby (`POST /Users/AuthenticateByName`) for a token + user id. */
        suspend fun authenticate(
            baseUrl: String,
            username: String,
            password: String,
            deviceId: String = "micasong-device",
        ): EmbySession? = withContext(Dispatchers.IO) {
            try {
                val url = JellyfinAuth.endpointUrl(baseUrl, "/Users/AuthenticateByName")
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 8000
                    readTimeout = 15000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Emby-Authorization", JellyfinAuth.authorizationHeader(deviceId))
                }
                val body = JSONObject().put("Username", username).put("Pw", password).toString()
                conn.outputStream.use { it.write(body.toByteArray()) }
                val response = conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                val token = response.optString("AccessToken").ifBlank { return@withContext null }
                val userId = response.optJSONObject("User")?.optString("Id")?.ifBlank { null }
                    ?: return@withContext null
                EmbySession(token, userId)
            } catch (e: Exception) {
                Log.w("EmbyProvider", "authenticate failed: ${e.message}")
                null
            }
        }
    }
}
