package com.micasong.player.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micasong.player.data.model.Album
import com.micasong.player.data.model.Track
import com.micasong.player.data.repository.MediaRepository
import com.micasong.player.data.repository.SyncState
import com.micasong.player.data.settings.SettingsRepository
import com.micasong.player.playback.PlaybackConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val hasLibrary: Boolean = false,
    val favoriteAlbums: List<Album> = emptyList(),
    val favoriteTracks: List<Track> = emptyList(),
    val recentlyAdded: List<Album> = emptyList(),
    val recentlyPlayed: List<Track> = emptyList(),
    val sync: SyncState = SyncState(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val playback: PlaybackConnection,
    private val settings: SettingsRepository,
) : ViewModel() {

    val uiState = combine(
        repository.trackCount,
        repository.favoriteAlbums,
        repository.favoriteTracks,
        repository.recentlyAddedAlbums(20),
        repository.syncState,
    ) { count, favAlbums, favTracks, recent, sync ->
        HomeUiState(
            hasLibrary = count > 0,
            favoriteAlbums = favAlbums,
            favoriteTracks = favTracks,
            recentlyAdded = recent,
            sync = sync,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun sync() = viewModelScope.launch { repository.syncAll() }

    fun playFavoriteTracks(startIndex: Int) {
        viewModelScope.launch {
            val tracks = uiState.value.favoriteTracks
            if (tracks.isNotEmpty()) playback.playTracks(tracks, startIndex)
        }
    }

    fun playAlbum(album: Album, shuffle: Boolean = false) {
        viewModelScope.launch {
            val tracks = repository.tracksByAlbum(album.id).first()
            if (tracks.isNotEmpty()) playback.playTracks(tracks, 0, shuffle)
        }
    }

    /** Mix de pistas — weighted random selection of tracks (spec §17). */
    fun playTrackMix() {
        viewModelScope.launch {
            val size = settings.settings.first().personalMixSize
            val tracks = repository.trackMix(size)
            if (tracks.isNotEmpty()) playback.playTracks(tracks, 0, shuffle = true)
        }
    }

    /** Mix de álbumes — pick random albums and queue their tracks in order (spec §17). */
    fun playAlbumMix() {
        viewModelScope.launch {
            val albums = repository.albums.first().shuffled().take(10)
            val tracks = albums.flatMap { repository.tracksByAlbum(it.id).first() }
            if (tracks.isNotEmpty()) playback.playTracks(tracks, 0)
        }
    }
}
