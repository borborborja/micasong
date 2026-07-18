package com.micasong.player.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micasong.player.data.model.Album
import com.micasong.player.data.model.Artist
import com.micasong.player.data.model.Genre
import com.micasong.player.data.model.Playlist
import com.micasong.player.data.model.Track
import com.micasong.player.data.repository.MediaRepository
import com.micasong.player.playback.PlaybackConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val playback: PlaybackConnection,
) : ViewModel() {

    val albums = repository.albums.stateIn(scope(), started(), emptyList<Album>())
    val artists = repository.artists.stateIn(scope(), started(), emptyList<Artist>())
    val genres = repository.genres.stateIn(scope(), started(), emptyList<Genre>())
    val tracks = repository.allTracks.stateIn(scope(), started(), emptyList<Track>())
    val playlists = repository.playlists.stateIn(scope(), started(), emptyList<Playlist>())

    fun playAllTracks(startIndex: Int) {
        val list = tracks.value
        if (list.isNotEmpty()) playback.playTracks(list, startIndex)
    }

    // ---- Playlist management (spec §32) ----
    fun createPlaylist(name: String) {
        if (name.isNotBlank()) viewModelScope.launch { repository.createPlaylist(name.trim()) }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch { repository.addTracksToPlaylist(playlistId, listOf(trackId)) }
    }

    fun createPlaylistWithTrack(name: String, trackId: Long) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.createPlaylist(name.trim())
            repository.addTracksToPlaylist(id, listOf(trackId))
        }
    }

    private fun scope() = viewModelScope
    private fun started() = SharingStarted.WhileSubscribed(5000)
}
