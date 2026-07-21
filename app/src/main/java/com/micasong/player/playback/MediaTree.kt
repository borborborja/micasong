package com.micasong.player.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.micasong.player.data.model.Album
import com.micasong.player.data.model.Artist
import com.micasong.player.data.model.Genre
import com.micasong.player.data.model.Track
import com.micasong.player.data.repository.MediaRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the browsable media tree shared by Android Auto, Wear OS and any MediaBrowser
 * client (spec §38). The root exposes the configurable Auto tabs (Inicio / Recientes /
 * Biblioteca / Favoritos); Biblioteca drills into the library nodes → albums → tracks.
 */
@Singleton
class MediaTree @Inject constructor(
    private val repository: MediaRepository,
    private val settings: com.micasong.player.data.settings.SettingsRepository,
) {
    object Ids {
        const val ROOT = "root"
        const val TAB_HOME = "tab_home"
        const val TAB_RECENT = "tab_recent"
        const val TAB_LIBRARY = "tab_library"
        const val TAB_FAVORITES = "tab_favorites"

        const val NODE_ALBUMS = "node_albums"
        const val NODE_ARTISTS = "node_artists"
        const val NODE_SONGS = "node_songs"
        const val NODE_GENRES = "node_genres"

        const val PREFIX_ALBUM = "album/"
        const val PREFIX_ARTIST = "artist/"
        const val PREFIX_GENRE = "genre/"
        const val PREFIX_TRACK = "track/"
    }

    fun rootItem(): MediaItem = browsable(Ids.ROOT, "MiCaSong")

    suspend fun children(parentId: String): List<MediaItem> = when (parentId) {
        Ids.ROOT -> {
            // Only the tabs the user enabled for Android Auto (spec §38); never empty.
            val s = settings.settings.first()
            val tabs = buildList {
                if (s.autoTabHome) add(browsable(Ids.TAB_HOME, "Inicio"))
                if (s.autoTabRecent) add(browsable(Ids.TAB_RECENT, "Recientes"))
                if (s.autoTabLibrary) add(browsable(Ids.TAB_LIBRARY, "Biblioteca"))
                if (s.autoTabFavorites) add(browsable(Ids.TAB_FAVORITES, "Favoritos"))
            }
            tabs.ifEmpty { listOf(browsable(Ids.TAB_LIBRARY, "Biblioteca")) }
        }
        Ids.TAB_LIBRARY -> listOf(
            browsable(Ids.NODE_ALBUMS, "Álbumes"),
            browsable(Ids.NODE_ARTISTS, "Artistas"),
            browsable(Ids.NODE_SONGS, "Pistas"),
            browsable(Ids.NODE_GENRES, "Géneros"),
        )
        Ids.TAB_HOME -> repository.mostPlayedAlbums(20).first().map(::albumItem)
            .ifEmpty { repository.albums.first().take(20).map(::albumItem) }
        Ids.TAB_RECENT -> repository.recentlyPlayed(50).first().map(::trackItem)
            .ifEmpty { repository.recentlyAddedAlbums(20).first().map(::albumItem) }
        Ids.TAB_FAVORITES -> repository.favoriteTracks.first().map(::trackItem)
        Ids.NODE_ALBUMS -> repository.albums.first().map(::albumItem)
        Ids.NODE_ARTISTS -> repository.artists.first().map(::artistItem)
        Ids.NODE_SONGS -> repository.allTracks.first().take(500).map(::trackItem)
        Ids.NODE_GENRES -> repository.genres.first().map(::genreItem)
        else -> when {
            parentId.startsWith(Ids.PREFIX_ALBUM) ->
                repository.tracksByAlbum(parentId.removePrefix(Ids.PREFIX_ALBUM).toLong()).first().map(::trackItem)
            parentId.startsWith(Ids.PREFIX_ARTIST) ->
                repository.tracksByArtist(parentId.removePrefix(Ids.PREFIX_ARTIST).toLong()).first().map(::trackItem)
            parentId.startsWith(Ids.PREFIX_GENRE) ->
                repository.tracksByGenre(parentId.removePrefix(Ids.PREFIX_GENRE)).first().map(::trackItem)
            else -> emptyList()
        }
    }

    /** Resolve a (possibly browse-only) media id to a fully playable [MediaItem]. */
    suspend fun resolvePlayable(mediaId: String): MediaItem? {
        val trackId = mediaId.removePrefix(Ids.PREFIX_TRACK).toLongOrNull() ?: return null
        val track = repository.trackById(trackId) ?: return null
        return track.toMediaItem()
    }

    /** Expand a browsable id into the flat list of playable tracks it represents. */
    suspend fun tracksForPlayback(mediaId: String): List<Track> = when {
        mediaId.startsWith(Ids.PREFIX_TRACK) ->
            repository.trackById(mediaId.removePrefix(Ids.PREFIX_TRACK).toLong())?.let { listOf(it) } ?: emptyList()
        mediaId.startsWith(Ids.PREFIX_ALBUM) ->
            repository.tracksByAlbum(mediaId.removePrefix(Ids.PREFIX_ALBUM).toLong()).first()
        mediaId.startsWith(Ids.PREFIX_ARTIST) ->
            repository.tracksByArtist(mediaId.removePrefix(Ids.PREFIX_ARTIST).toLong()).first()
        mediaId.startsWith(Ids.PREFIX_GENRE) ->
            repository.tracksByGenre(mediaId.removePrefix(Ids.PREFIX_GENRE)).first()
        else -> emptyList()
    }

    // ---- item factories ----
    private fun albumItem(a: Album): MediaItem = browsable(
        id = Ids.PREFIX_ALBUM + a.id,
        title = a.name,
        subtitle = a.albumArtist,
        artwork = a.artworkUri,
        mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
    )

    private fun artistItem(a: Artist): MediaItem = browsable(
        id = Ids.PREFIX_ARTIST + a.id,
        title = a.name,
        artwork = a.artworkUri,
        mediaType = MediaMetadata.MEDIA_TYPE_ARTIST,
    )

    private fun genreItem(g: Genre): MediaItem = browsable(
        id = Ids.PREFIX_GENRE + g.name,
        title = g.name,
        mediaType = MediaMetadata.MEDIA_TYPE_GENRE,
    )

    private fun trackItem(t: Track): MediaItem = t.toMediaItem()

    private fun browsable(
        id: String,
        title: String,
        subtitle: String? = null,
        artwork: String? = null,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(mediaType)
            .apply { artwork?.let { setArtworkUri(Uri.parse(it)) } }
            .build()
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build()
    }
}

/** Map a domain [Track] into a fully playable Media3 [MediaItem] (title/artist/art/uri). */
fun Track.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artistName)
        .setAlbumTitle(albumName)
        .setAlbumArtist(albumArtist)
        .setTrackNumber(trackNumber)
        .setDiscNumber(discNumber)
        .setReleaseYear(year)
        .setGenre(genre)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setMediaType(if (isAudiobook) MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER else MediaMetadata.MEDIA_TYPE_MUSIC)
        .apply { artworkUri?.let { setArtworkUri(Uri.parse(it)) } }
        .build()
    return MediaItem.Builder()
        .setMediaId(MediaTree.Ids.PREFIX_TRACK + id)
        .setUri(mediaUri)
        // Cast queue conversion requires a MIME type; only real MIMEs qualify (Plex reports bare
        // codec names like "mp3", which would mislead ExoPlayer's content-type inference).
        .setMimeType(com.micasong.player.data.audio.AudioMime.declaredOrNull(mimeType))
        .setMediaMetadata(metadata)
        .build()
}
