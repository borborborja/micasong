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

    // Auto-negotiated from the server's ping response (spec §5.1, §46): the API version to request,
    // whether to use legacy auth, and whether OpenSubsonic extensions are available.
    @Volatile private var apiVersion: String = SubsonicAuth.API_VERSION
    @Volatile private var useLegacyAuth: Boolean = false
    @Volatile var openSubsonic: Boolean = false
        private set

    /** Build a fully-authenticated endpoint URL, adapting to the negotiated version/auth (spec §47). */
    fun endpoint(view: String, params: Map<String, String> = emptyMap()): String =
        endpointWith(view, params, legacy = useLegacyAuth, version = apiVersion)

    private fun endpointWith(view: String, params: Map<String, String>, legacy: Boolean, version: String): String {
        val base = config.primaryUrl ?: return ""
        val user = config.username ?: ""
        val secret = config.secret ?: ""
        val auth = if (legacy) {
            SubsonicAuth.legacyAuthParams(user, secret, clientName, version)
        } else {
            SubsonicAuth.authParams(user, secret, SubsonicAuth.randomSalt(), clientName, version)
        }
        return SubsonicAuth.endpointUrl(base, view, auth + params)
    }

    /**
     * Ping the server and adapt to it (spec §5.1): read its API version, detect OpenSubsonic, and
     * fall back to legacy auth if token auth is rejected. Returns null on success or a user-facing
     * error. Safe to call before every sync — it's one cheap request.
     */
    suspend fun negotiate(): String? = withContext(Dispatchers.IO) {
        if (config.primaryUrl.isNullOrBlank()) return@withContext "Introduce la URL del servidor."
        // Probe with token auth at the minimum version first (any server ≥1.13 accepts it).
        val tokenResp = getJson(endpointWith("ping", emptyMap(), legacy = false, version = SubsonicAuth.API_VERSION))
            ?.optJSONObject("subsonic-response")
            ?: return@withContext "No se pudo conectar. Revisa la URL y el puerto, y que el servidor esté encendido."

        if (tokenResp.optString("status") == "ok") {
            useLegacyAuth = false
            adopt(tokenResp)
            return@withContext null
        }

        // Token rejected — if it's an auth-method problem, retry with legacy auth.
        val code = tokenResp.optJSONObject("error")?.optInt("code")
        if (code == 41 || code == 42 || code == 44) {
            val legacyResp = getJson(endpointWith("ping", emptyMap(), legacy = true, version = SubsonicAuth.API_VERSION))
                ?.optJSONObject("subsonic-response")
            if (legacyResp?.optString("status") == "ok") {
                useLegacyAuth = true
                adopt(legacyResp)
                return@withContext null
            }
        }
        errorMessage(tokenResp)
    }

    /** Adopt the server's reported version + OpenSubsonic support from a successful ping. */
    private fun adopt(resp: org.json.JSONObject) {
        resp.optString("version").ifBlank { null }?.let { apiVersion = it }
        openSubsonic = resp.optBoolean("openSubsonic", false)
    }

    private fun errorMessage(resp: org.json.JSONObject): String {
        val error = resp.optJSONObject("error")
        return when (error?.optInt("code")) {
            40 -> "Usuario o contraseña incorrectos."
            41, 42, 44 -> "El servidor no acepta este método de autenticación. Prueba con una clave API."
            30 -> "La versión del servidor es incompatible."
            else -> error?.optString("message")?.ifBlank { null } ?: "El servidor rechazó la conexión."
        }
    }

    override suspend fun sync(onProgress: (Float, String) -> Unit): ProviderSnapshot =
        withContext(Dispatchers.IO) {
            val tracks = ArrayList<TrackEntity>()
            val albums = ArrayList<AlbumEntity>()
            val artists = ArrayList<ArtistEntity>()
            val genres = LinkedHashMap<String, Int>()
            try {
                onProgress(0f, "Conectando")
                // Adapt to the server (version, OpenSubsonic, legacy auth) and verify auth first.
                val error = negotiate()
                if (error != null) {
                    Log.w("SubsonicProvider", "connection failed: $error")
                    return@withContext ProviderSnapshot(emptyList(), emptyList(), emptyList(), emptyList())
                }

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

                onProgress(0.3f, "Recopilando álbumes")
                // getAlbumList2 is paginated (max 500 per page) — walk every page.
                val rawAlbums = ArrayList<JSONObject>()
                var offset = 0
                val pageSize = 500
                while (true) {
                    val page = getJson(
                        endpoint("getAlbumList2", mapOf("type" to "alphabeticalByName", "size" to "$pageSize", "offset" to "$offset"))
                    )?.optJSONObject("subsonic-response")?.optJSONObject("albumList2")?.optJSONArray("album")
                    if (page == null || page.length() == 0) break
                    for (i in 0 until page.length()) rawAlbums += page.getJSONObject(i)
                    if (page.length() < pageSize) break
                    offset += pageSize
                }

                // For each album: map the album AND fetch its songs (the actual playable tracks).
                for ((index, al) in rawAlbums.withIndex()) {
                    val rawAlbumId = al.optString("id")
                    albums += AlbumEntity(
                        id = SubsonicMappers.stableId(rawAlbumId),
                        name = al.optString("name", "?"),
                        nameSort = al.optString("name").lowercase(),
                        albumArtist = al.optString("artist").ifBlank { null },
                        artistId = al.optString("artistId").ifBlank { null }?.let { SubsonicMappers.stableId(it) },
                        year = al.optInt("year").takeIf { it > 0 },
                        trackCount = al.optInt("songCount"),
                        durationMs = al.optLong("duration") * 1000,
                        artworkUri = coverArtUri(al.optString("coverArt")),
                    )

                    val songArr = getJson(endpoint("getAlbum", mapOf("id" to rawAlbumId)))
                        ?.optJSONObject("subsonic-response")?.optJSONObject("album")?.optJSONArray("song")
                    if (songArr != null) {
                        for (s in 0 until songArr.length()) {
                            val song = songArr.getJSONObject(s)
                            val streamUrl = endpoint("stream", mapOf("id" to song.optString("id")))
                            val coverUrl = coverArtUri(song.optString("coverArt"))
                            tracks += SubsonicMappers.parseSong(song, config.id, streamUrl, coverUrl)
                            song.optString("genre").ifBlank { null }?.let { genres[it] = (genres[it] ?: 0) + 1 }
                        }
                    }
                    if (rawAlbums.isNotEmpty()) {
                        onProgress(0.3f + 0.6f * (index + 1) / rawAlbums.size, "Recopilando pistas")
                    }
                }
                onProgress(1f, "Completado")
            } catch (e: Exception) {
                Log.w("SubsonicProvider", "sync failed: ${e.message}")
            }
            val genreEntities = genres.entries.map { GenreEntity(it.key.stableId(), it.key, it.value) }
            ProviderSnapshot(tracks, albums, artists, genreEntities)
        }

    // Tracks already carry a ready-to-play, authenticated stream URL in mediaUri (built at sync).
    override suspend fun streamUri(track: TrackEntity, maxBitrate: Int): String = track.mediaUri

    /**
     * Test the connection: pings the server and returns null on success, or a specific,
     * user-facing error otherwise (spec §5.1). Used when adding a provider so misconfigurations
     * surface immediately instead of producing a silent empty library.
     */
    suspend fun testConnection(): String? = negotiate()

    /** Fetch lyrics, adapting to the server: OpenSubsonic getLyricsBySongId, else legacy getLyrics. */
    override suspend fun lyrics(track: TrackEntity): String? = withContext(Dispatchers.IO) {
        val serverId = android.net.Uri.parse(track.mediaUri).getQueryParameter("id")
        // Preferred: OpenSubsonic synced lyrics by song id.
        if (serverId != null) {
            getJson(endpoint("getLyricsBySongId", mapOf("id" to serverId)))
                ?.let { SubsonicMappers.parseLyrics(it) }
                ?.let { return@withContext it }
        }
        // Fallback: legacy getLyrics by artist + title (plain text), for older servers.
        val artist = track.artistName ?: return@withContext null
        getJson(endpoint("getLyrics", mapOf("artist" to artist, "title" to track.title)))
            ?.let { SubsonicMappers.parseLyrics(it) }
    }

    /** Submit a scrobble for a completed play (spec §47 scrobble.view). */
    override suspend fun scrobble(track: TrackEntity) {
        withContext(Dispatchers.IO) {
            val serverId = android.net.Uri.parse(track.mediaUri).getQueryParameter("id") ?: return@withContext
            getJson(endpoint("scrobble", mapOf("id" to serverId, "submission" to "true")))
        }
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

    private fun String.stableId(): Long = StableId.of(this)
}
