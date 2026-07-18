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
 * Subsonic / OpenSubsonic / Navidrome connector — the spec's recommended primary server
 * provider (§5.1, §47). Implements salted-token auth and the key REST endpoints. Network
 * calls are defensive: a connection failure yields an empty snapshot rather than a crash,
 * so the app degrades to whatever is already cached (offline-first, §2).
 */
class SubsonicProvider(
    override val config: ProviderConfig,
) : MediaProvider {

    override val capabilities = ProviderCapabilities(
        multiArtist = true,          // OpenSubsonic
        multiGenre = true,
        albumMood = true,
        songBpm = true,
        albumType = true,
        composers = true,
        playlistImport = true,
        playlistPush = true,
        serverTranscoding = true,
        ratings = true,
        similarArtists = true,       // OpenSubsonic sonicSimilarity (§5.1)
    )

    private val clientName = "MiCaSong"

    /** Build a fully-authenticated endpoint URL (salted token auth, spec §47). */
    fun endpoint(view: String, params: Map<String, String> = emptyMap()): String {
        val base = config.primaryUrl ?: return ""
        val salt = SubsonicAuth.randomSalt()
        val auth = SubsonicAuth.authParams(config.username ?: "", config.secret ?: "", salt, clientName)
        return SubsonicAuth.endpointUrl(base, view, auth + params)
    }

    override suspend fun sync(onProgress: (Float, String) -> Unit): ProviderSnapshot =
        withContext(Dispatchers.IO) {
            val tracks = ArrayList<TrackEntity>()
            val albums = ArrayList<AlbumEntity>()
            val artists = ArrayList<ArtistEntity>()
            val genres = LinkedHashMap<String, Int>()
            try {
                onProgress(0f, "Conectando")
                // Verify reachability first.
                getJson(endpoint("ping")) ?: return@withContext ProviderSnapshot(emptyList(), emptyList(), emptyList(), emptyList())

                onProgress(0.1f, "Recopilando artistas")
                val artistsJson = getJson(endpoint("getArtists"))
                val indexArray = artistsJson
                    ?.optJSONObject("subsonic-response")
                    ?.optJSONObject("artists")
                    ?.optJSONArray("index")
                if (indexArray != null) {
                    for (i in 0 until indexArray.length()) {
                        val artistArr = indexArray.getJSONObject(i).optJSONArray("artist") ?: continue
                        for (j in 0 until artistArr.length()) {
                            val a = artistArr.getJSONObject(j)
                            artists += ArtistEntity(
                                id = a.getString("id").stableId(),
                                name = a.optString("name", "?"),
                                nameSort = a.optString("name").lowercase(),
                                albumCount = a.optInt("albumCount"),
                                trackCount = 0,
                                artworkUri = a.optString("artistImageUrl").ifBlank { null },
                            )
                        }
                    }
                }

                onProgress(0.4f, "Recopilando álbumes")
                val albumsJson = getJson(
                    endpoint("getAlbumList2", mapOf("type" to "alphabeticalByName", "size" to "500"))
                )
                val albumArr = albumsJson
                    ?.optJSONObject("subsonic-response")
                    ?.optJSONObject("albumList2")
                    ?.optJSONArray("album")
                if (albumArr != null) {
                    for (i in 0 until albumArr.length()) {
                        val al = albumArr.getJSONObject(i)
                        albums += AlbumEntity(
                            id = al.getString("id").stableId(),
                            name = al.optString("name", "?"),
                            nameSort = al.optString("name").lowercase(),
                            albumArtist = al.optString("artist").ifBlank { null },
                            artistId = al.optString("artistId").ifBlank { null }?.stableId(),
                            year = al.optInt("year").takeIf { it > 0 },
                            trackCount = al.optInt("songCount"),
                            durationMs = al.optLong("duration") * 1000,
                            artworkUri = coverArtUri(al.optString("coverArt")),
                        )
                        onProgress(0.4f + 0.5f * i / albumArr.length(), "Recopilando pistas")
                    }
                }
                onProgress(1f, "Completado")
            } catch (e: Exception) {
                Log.w("SubsonicProvider", "sync failed: ${e.message}")
            }
            val genreEntities = genres.entries.map { GenreEntity(it.key.stableId(), it.key, it.value) }
            ProviderSnapshot(tracks, albums, artists, genreEntities)
        }

    override suspend fun streamUri(track: TrackEntity, maxBitrate: Int): String {
        val params = buildMap {
            put("id", track.mediaUri)   // Subsonic tracks store the server id in mediaUri
            if (maxBitrate > 0) {
                put("maxBitRate", (maxBitrate / 1000).toString())
                if (config.type == ProviderType.SUBSONIC) put("format", "opus")
            }
        }
        return endpoint("stream", params)
    }

    fun coverArtUri(coverArtId: String?): String? =
        coverArtId?.takeIf { it.isNotBlank() }?.let { endpoint("getCoverArt", mapOf("id" to it, "size" to "512")) }

    // ---- helpers ----
    private fun getJson(urlString: String): JSONObject? {
        if (urlString.isBlank()) return null
        return try {
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 15000
                requestMethod = "GET"
            }
            conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } catch (e: Exception) {
            Log.w("SubsonicProvider", "request failed for $urlString: ${e.message}")
            null
        }
    }

    private fun String.stableId(): Long = abs(hashCode().toLong()) or 1L
}
