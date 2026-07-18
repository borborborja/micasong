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
 * AudioBookShelf connector (spec §46, experimental). Reads book libraries via the REST API and maps
 * each item to an audiobook track with its chapters. [ProviderConfig.secret] holds the API token;
 * media streams carry it as `?token=`, so files play with a plain GET.
 */
class AudioBookShelfProvider(
    override val config: ProviderConfig,
) : MediaProvider {

    override val capabilities = ProviderCapabilities(serverTranscoding = true)

    override suspend fun sync(onProgress: (Float, String) -> Unit): ProviderSnapshot =
        withContext(Dispatchers.IO) {
            val tracks = ArrayList<TrackEntity>()
            val albums = LinkedHashMap<Long, AlbumEntity>()
            try {
                onProgress(0f, "Conectando")
                val base = config.primaryUrl?.trimEnd('/') ?: return@withContext empty()
                val libraries = getJson("$base/api/libraries")?.optJSONArray("libraries") ?: return@withContext empty()
                onProgress(0.3f, "Recopilando audiolibros")
                for (l in 0 until libraries.length()) {
                    val lib = libraries.getJSONObject(l)
                    if (lib.optString("mediaType") != "book") continue
                    val results = getJson("$base/api/libraries/${lib.optString("id")}/items")?.optJSONArray("results") ?: continue
                    for (i in 0 until results.length()) {
                        val itemId = results.getJSONObject(i).optString("id")
                        val detail = getJson("$base/api/items/$itemId") ?: continue
                        val ino = detail.optJSONObject("media")?.optJSONArray("audioFiles")?.optJSONObject(0)?.optString("ino")
                        val streamUrl = tokenUrl("$base/api/items/$itemId/file/${ino.orEmpty()}")
                        val coverUrl = tokenUrl("$base/api/items/$itemId/cover")
                        val track = AudioBookShelfMappers.parseItem(detail, config.id, streamUrl, coverUrl)
                        tracks += track
                        track.albumId?.let { albumId ->
                            albums.getOrPut(albumId) {
                                AlbumEntity(albumId, track.albumName ?: "?", (track.albumName ?: "").lowercase(),
                                    track.artistName, track.artistId, track.year, 0, 0, null)
                            }
                        }
                    }
                }
                onProgress(1f, "Completado")
            } catch (e: Exception) {
                Log.w("AbsProvider", "sync failed: ${e.message}")
            }
            ProviderSnapshot(tracks, albums.values.toList(), emptyList<ArtistEntity>(), emptyList<GenreEntity>())
        }

    override suspend fun streamUri(track: TrackEntity, maxBitrate: Int): String = track.mediaUri

    private fun empty() = ProviderSnapshot(emptyList(), emptyList(), emptyList(), emptyList())

    private fun tokenUrl(url: String): String {
        val token = config.secret ?: return url
        val sep = if (url.contains("?")) "&" else "?"
        return "$url${sep}token=$token"
    }

    private fun getJson(urlString: String): JSONObject? = try {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 15000
            requestMethod = "GET"
            config.secret?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    } catch (e: Exception) {
        Log.w("AbsProvider", "request failed: ${e.message}")
        null
    }

    /** The result of a successful AudioBookShelf login (API token). */
    data class AbsSession(val token: String)

    companion object {
        /** Log in (`POST /login`) to exchange username + password for an API token. */
        suspend fun authenticate(baseUrl: String, username: String, password: String): AbsSession? =
            withContext(Dispatchers.IO) {
                try {
                    val url = URL("${baseUrl.trimEnd('/')}/login")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 8000
                        readTimeout = 15000
                        setRequestProperty("Content-Type", "application/json")
                    }
                    val body = JSONObject().put("username", username).put("password", password).toString()
                    conn.outputStream.use { it.write(body.toByteArray()) }
                    val response = conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                    val token = response.optJSONObject("user")?.optString("token")?.ifBlank { null }
                        ?: return@withContext null
                    AbsSession(token)
                } catch (e: Exception) {
                    Log.w("AbsProvider", "authenticate failed: ${e.message}")
                    null
                }
            }
    }
}
