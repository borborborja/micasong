package com.micasong.player.data.provider

import android.net.Uri
import android.util.Log
import com.micasong.player.data.db.AlbumEntity
import com.micasong.player.data.db.ArtistEntity
import com.micasong.player.data.db.GenreEntity
import com.micasong.player.data.db.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * WebDAV connector (spec §46). Crawls the share with PROPFIND (Depth: 1, recursing into folders up
 * to a bounded depth) and exposes the audio files it finds. Tags aren't read over WebDAV, so titles
 * come from the filename until local tag extraction lands; playback streams the file directly with
 * HTTP Basic auth embedded in the URL (handled by [credentialMediaSourceFactory]).
 */
class WebDavProvider(
    override val config: ProviderConfig,
    private val maxDepth: Int = 4,
    private val maxFiles: Int = 5000,
) : MediaProvider {

    override val capabilities = ProviderCapabilities(playlistImport = true)

    private val client = OkHttpClient()

    override suspend fun sync(onProgress: (Float, String) -> Unit): ProviderSnapshot =
        withContext(Dispatchers.IO) {
            val tracks = ArrayList<TrackEntity>()
            try {
                onProgress(0f, "Conectando")
                val base = config.primaryUrl ?: return@withContext empty()
                val root = Uri.parse(base.trimEnd('/'))
                crawl(base.trimEnd('/'), root, depth = 0, tracks)
                onProgress(1f, "Completado")
            } catch (e: Exception) {
                Log.w("WebDavProvider", "sync failed: ${e.message}")
            }
            ProviderSnapshot(tracks, emptyList<AlbumEntity>(), emptyList<ArtistEntity>(), emptyList<GenreEntity>())
        }

    private fun crawl(baseNoSlash: String, dirUrl: Uri, depth: Int, out: ArrayList<TrackEntity>) {
        if (depth > maxDepth || out.size >= maxFiles) return
        val xml = propfind(dirUrl.toString()) ?: return
        val entries = runCatching { WebDavParser.parse(xml) }.getOrDefault(emptyList())
        val root = Uri.parse(baseNoSlash)
        for (entry in entries) {
            if (out.size >= maxFiles) return
            val abs = resolveHref(root, entry.href)
            // Skip the directory's own self-entry.
            if (abs.path?.trimEnd('/') == dirUrl.path?.trimEnd('/')) continue
            when {
                entry.isCollection -> crawl(baseNoSlash, abs, depth + 1, out)
                WebDavParser.isAudio(entry) -> out += toTrack(abs, entry)
            }
        }
    }

    private fun toTrack(fileUrl: Uri, entry: WebDavEntry): TrackEntity {
        val name = Uri.decode(fileUrl.lastPathSegment ?: "audio").substringBeforeLast('.')
        return TrackEntity(
            id = StableId.of("webdav:${fileUrl}"),
            providerId = config.id,
            mediaUri = withCredentials(fileUrl),
            title = name,
            titleSort = name.lowercase(),
            albumId = null, albumName = null, artistId = null, artistName = null, albumArtist = null,
            trackNumber = null, discNumber = null, durationMs = 0, year = null, genre = null,
            mimeType = entry.contentType, bitrate = null, sampleRate = null, bitDepth = null,
            sizeBytes = entry.contentLength, artworkUri = null, dateAdded = 0L,
        )
    }

    override suspend fun streamUri(track: TrackEntity, maxBitrate: Int): String = track.mediaUri

    private fun empty() = ProviderSnapshot(emptyList(), emptyList(), emptyList(), emptyList())

    /** Resolve a PROPFIND href (often an absolute path) against the server root into a full URL. */
    private fun resolveHref(root: Uri, href: String): Uri {
        if (href.startsWith("http")) return Uri.parse(href)
        val authority = root.host + if (root.port > 0) ":${root.port}" else ""
        return Uri.parse("${root.scheme}://$authority${if (href.startsWith("/")) href else "/$href"}")
    }

    /** Embed Basic-auth credentials in the URL userinfo for the credential-aware data source. */
    private fun withCredentials(url: Uri): String {
        val user = config.username?.takeIf { it.isNotBlank() } ?: return url.toString()
        val authority = "$user:${config.secret.orEmpty()}@" + url.host + if (url.port > 0) ":${url.port}" else ""
        return "${url.scheme}://$authority${url.encodedPath}"
    }

    private fun propfind(url: String): String? = try {
        val req = Request.Builder()
            .url(url)
            .method("PROPFIND", "".toRequestBody())
            .header("Depth", "1")
            .apply {
                config.username?.takeIf { it.isNotBlank() }?.let {
                    header("Authorization", Credentials.basic(it, config.secret.orEmpty()))
                }
            }
            .build()
        client.newCall(req).execute().use { resp -> if (resp.isSuccessful) resp.body?.string() else null }
    } catch (e: Exception) {
        Log.w("WebDavProvider", "propfind failed: ${e.message}")
        null
    }
}
