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

    // ---- Queue actions (spec §13) ----
    fun playTrackNext(track: Track) = playback.playNext(listOf(track))
    fun addTrackToQueue(track: Track) = playback.addToQueue(listOf(track))

    // ---- Offline download (spec §35) ----
    fun downloadTrack(track: Track) = viewModelScope.launch { repository.enqueueDownloads(listOf(track.id)) }

    // ---- Internet radio (spec §10) ----
    val radioStations = repository.radioStations.stateIn(scope(), started(), emptyList<com.micasong.player.data.radio.RadioStation>())

    fun addRadioStation(name: String, url: String) {
        if (name.isNotBlank() && url.isNotBlank()) viewModelScope.launch { repository.addRadioStation(name, url) }
    }

    fun deleteRadioStation(id: Long) = viewModelScope.launch { repository.deleteRadioStation(id) }

    fun playRadio(clickedId: Long) = playback.playRadio(radioStations.value, clickedId)

    // ---- Playlist management (spec §32) ----
    fun createPlaylist(name: String) {
        if (name.isNotBlank()) viewModelScope.launch { repository.createPlaylist(name.trim()) }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch { repository.addTracksToPlaylist(playlistId, listOf(trackId)) }
    }

    fun createSmartPlaylist(name: String, def: com.micasong.player.data.smart.SmartPlaylistDefinition) {
        if (name.isNotBlank()) viewModelScope.launch { repository.createSmartPlaylist(name.trim(), def) }
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
