package com.micasong.player.data.provider

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.micasong.player.data.db.AlbumEntity
import com.micasong.player.data.db.ArtistEntity
import com.micasong.player.data.db.GenreEntity
import com.micasong.player.data.db.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Local device provider backed by [MediaStore] (spec §5.3 "By Android" access mode).
 *
 * This is the Phase-1 MVP source (spec §48). The richer SAF + TagLib parser path (§7) — with
 * 60+ tags, multi-artist/genre and CUE support — is a later phase; this connector already
 * yields a fully browsable, playable unified library from the device's own audio.
 */
class LocalProvider(
    private val context: Context,
    override val config: ProviderConfig,
) : MediaProvider {

    override val capabilities = ProviderCapabilities(
        multiArtist = false,      // MediaStore is single-artist; SAF+TagLib path adds multi
        multiGenre = false,
        songBpm = false,
        albumType = false,
        composers = false,
        languages = false,
        playlistImport = true,
        playlistPush = false,
        serverTranscoding = false,
        ratings = true,
    )

    override suspend fun sync(onProgress: (Float, String) -> Unit): ProviderSnapshot =
        withContext(Dispatchers.IO) {
            onProgress(0f, "Recopilando metadatos")
            val tracks = ArrayList<TrackEntity>()
            val albums = LinkedHashMap<Long, AlbumEntity>()
            val artists = LinkedHashMap<Long, ArtistEntity>()
            val genresByName = LinkedHashMap<String, Int>()
            val albumTrackCount = HashMap<Long, Int>()
            val albumDuration = HashMap<Long, Long>()
            val artistTrackCount = HashMap<Long, Int>()
            val artistAlbums = HashMap<Long, MutableSet<Long>>()

            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.ALBUM_ARTIST,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.GENRE,
                MediaStore.Audio.Media.BITRATE,
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE_KEY} ASC"

            context.contentResolver.query(collection, projection, selection, null, sortOrder)
                ?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val artistIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                    val albumArtistCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                    val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                    val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                    val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val genreCol = c.getColumnIndex(MediaStore.Audio.Media.GENRE)
                    val bitrateCol = c.getColumnIndex(MediaStore.Audio.Media.BITRATE)

                    val total = c.count.coerceAtLeast(1)
                    var index = 0
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val contentUri = ContentUris.withAppendedId(collection, id).toString()
                        val albumId = c.getLong(albumIdCol)
                        val artistId = c.getLong(artistIdCol)
                        val artistName = c.getString(artistCol) ?: "Artista desconocido"
                        val albumName = c.getString(albumCol) ?: "Álbum desconocido"
                        val albumArtist = if (albumArtistCol >= 0) c.getString(albumArtistCol) else null
                        val duration = c.getLong(durCol)
                        val year = c.getInt(yearCol).takeIf { it > 0 }
                        val rawTrack = c.getInt(trackCol)
                        // MediaStore encodes disc*1000 + track
                        val trackNumber = (rawTrack % 1000).takeIf { it > 0 }
                        val discNumber = (rawTrack / 1000).takeIf { it > 0 }
                        val genre = if (genreCol >= 0) c.getString(genreCol) else null
                        val bitrate = if (bitrateCol >= 0) c.getInt(bitrateCol).takeIf { it > 0 } else null
                        val artUri = albumArtUri(albumId)

                        tracks += TrackEntity(
                            id = id,
                            providerId = config.id,
                            mediaUri = contentUri,
                            title = c.getString(titleCol) ?: "Sin título",
                            titleSort = c.getString(titleCol)?.lowercase(),
                            albumId = albumId,
                            albumName = albumName,
                            artistId = artistId,
                            artistName = artistName,
                            albumArtist = albumArtist,
                            trackNumber = trackNumber,
                            discNumber = discNumber,
                            durationMs = duration,
                            year = year,
                            genre = genre,
                            mimeType = c.getString(mimeCol),
                            bitrate = bitrate,
                            sampleRate = null,
                            bitDepth = null,
                            sizeBytes = c.getLong(sizeCol),
                            artworkUri = artUri,
                            dateAdded = c.getLong(addedCol),
                        )

                        albums.getOrPut(albumId) {
                            AlbumEntity(
                                id = albumId,
                                name = albumName,
                                nameSort = albumName.lowercase(),
                                albumArtist = albumArtist ?: artistName,
                                artistId = artistId,
                                year = year,
                                trackCount = 0,
                                durationMs = 0,
                                artworkUri = artUri,
                                dateAdded = c.getLong(addedCol),
                            )
                        }
                        albumTrackCount[albumId] = (albumTrackCount[albumId] ?: 0) + 1
                        albumDuration[albumId] = (albumDuration[albumId] ?: 0) + duration

                        artists.getOrPut(artistId) {
                            ArtistEntity(
                                id = artistId,
                                name = artistName,
                                nameSort = artistName.lowercase(),
                                albumCount = 0,
                                trackCount = 0,
                                artworkUri = null,
                            )
                        }
                        artistTrackCount[artistId] = (artistTrackCount[artistId] ?: 0) + 1
                        artistAlbums.getOrPut(artistId) { mutableSetOf() }.add(albumId)

                        genre?.takeIf { it.isNotBlank() }?.let {
                            genresByName[it] = (genresByName[it] ?: 0) + 1
                        }

                        index++
                        if (index % 50 == 0) onProgress(index.toFloat() / total, "Recopilando metadatos")
                    }
                }

            val finalAlbums = albums.values.map {
                it.copy(
                    trackCount = albumTrackCount[it.id] ?: 0,
                    durationMs = albumDuration[it.id] ?: 0,
                )
            }
            val finalArtists = artists.values.map {
                it.copy(
                    trackCount = artistTrackCount[it.id] ?: 0,
                    albumCount = artistAlbums[it.id]?.size ?: 0,
                )
            }
            val finalGenres = genresByName.entries.mapIndexed { i, e ->
                GenreEntity(id = e.key.stableId(), name = e.key, trackCount = e.value)
            }

            onProgress(1f, "Completado")
            ProviderSnapshot(tracks, finalAlbums, finalArtists, finalGenres)
        }

    override suspend fun streamUri(track: TrackEntity, maxBitrate: Int): String = track.mediaUri

    private fun albumArtUri(albumId: Long): String =
        ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), albumId).toString()

    private fun String.stableId(): Long = abs(hashCode().toLong()) or 1L

    // Small local helper to avoid pulling in androidx.core Uri parsing here.
    private fun String.toUri() = android.net.Uri.parse(this)
}
