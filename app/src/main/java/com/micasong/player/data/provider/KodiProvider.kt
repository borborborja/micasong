package com.micasong.player.data.provider

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.micasong.player.data.db.AlbumEntity
import com.micasong.player.data.db.ArtistEntity
import com.micasong.player.data.db.GenreEntity
import com.micasong.player.data.db.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Kodi connector (spec §46, Kodi 19+). Reads the music library over JSON-RPC (`AudioLibrary.
 * GetSongs`) and streams files through Kodi's web server VFS (`/vfs/{path}`). Kodi's web server
 * uses HTTP Basic auth, so both the JSON-RPC POST and the stream URLs carry credentials — stream
 * URLs embed them in the userinfo, which [credentialMediaSourceFactory] turns into an auth header.
 */
class KodiProvider(
    override val config: ProviderConfig,
) : MediaProvider {

    override val capabilities = ProviderCapabilities(
        multiArtist = true,
        multiGenre = true,
        composers = true,
        albumMood = true,
        albumType = true,
        songBpm = true,
        playlistImport = true,
        ratings = true,
    )

    override suspend fun sync(onProgress: (Float, String) -> Unit): ProviderSnapshot =
        withContext(Dispatchers.IO) {
            val tracks = ArrayList<TrackEntity>()
            val albums = LinkedHashMap<Long, AlbumEntity>()
            val genres = LinkedHashMap<String, Int>()
            try {
                onProgress(0f, "Conectando")
                val base = config.primaryUrl ?: return@withContext empty()
                val params = JSONObject().put(
                    "properties",
                    JSONArray(listOf("title", "artist", "albumartist", "album", "track", "disc", "duration", "year", "genre", "file", "thumbnail")),
                )
                val result = rpc(base, "AudioLibrary.GetSongs", params) ?: return@withContext empty()
                val songs = result.optJSONArray("songs") ?: return@withContext empty()
                onProgress(0.5f, "Recopilando pistas")
                for (i in 0 until songs.length()) {
                    val song = songs.getJSONObject(i)
                    val file = song.optString("file").ifBlank { null } ?: continue
                    val streamUrl = vfsUrl(base, file)
                    val coverUrl = song.optString("thumbnail").ifBlank { null }?.let { imageUrl(base, it) }
                    val track = KodiMappers.parseSong(song, config.id, streamUrl, coverUrl)
                    tracks += track
                    track.albumId?.let { albumId ->
                        albums.getOrPut(albumId) {
                            AlbumEntity(albumId, track.albumName ?: "?", (track.albumName ?: "").lowercase(),
                                track.artistName, track.artistId, track.year, 0, 0, null)
                        }
                    }
                    track.genre?.let { genres[it] = (genres[it] ?: 0) + 1 }
                }
                onProgress(1f, "Completado")
            } catch (e: Exception) {
                Log.w("KodiProvider", "sync failed: ${e.message}")
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

    /** A Kodi VFS stream URL with Basic-auth credentials embedded in the userinfo. */
    private fun vfsUrl(base: String, filePath: String): String {
        val u = Uri.parse(base.trimEnd('/'))
        val userInfo = credentialsUserInfo()
        val authority = (if (userInfo != null) "$userInfo@" else "") + u.host + (if (u.port > 0) ":${u.port}" else "")
        return "${u.scheme}://$authority/vfs/${URLEncoder.encode(filePath, "UTF-8")}"
    }

    private fun imageUrl(base: String, thumb: String): String {
        val u = Uri.parse(base.trimEnd('/'))
        val userInfo = credentialsUserInfo()
        val authority = (if (userInfo != null) "$userInfo@" else "") + u.host + (if (u.port > 0) ":${u.port}" else "")
        return "${u.scheme}://$authority/image/${URLEncoder.encode(thumb, "UTF-8")}"
    }

    private fun credentialsUserInfo(): String? {
        val user = config.username?.takeIf { it.isNotBlank() } ?: return null
        return "$user:${config.secret.orEmpty()}"
    }

    private fun rpc(base: String, method: String, params: JSONObject): JSONObject? = try {
        val url = URL("${base.trimEnd('/')}/jsonrpc")
        val body = JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", params).toString()
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 8000
            readTimeout = 15000
            setRequestProperty("Content-Type", "application/json")
            config.username?.takeIf { it.isNotBlank() }?.let {
                val creds = Base64.encodeToString("$it:${config.secret.orEmpty()}".toByteArray(), Base64.NO_WRAP)
                setRequestProperty("Authorization", "Basic $creds")
            }
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }.optJSONObject("result")
    } catch (e: Exception) {
        Log.w("KodiProvider", "rpc $method failed: ${e.message}")
        null
    }
}
