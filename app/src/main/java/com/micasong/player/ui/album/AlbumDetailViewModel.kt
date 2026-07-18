package com.micasong.player.ui.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micasong.player.data.model.Album
import com.micasong.player.data.model.Track
import com.micasong.player.data.repository.MediaRepository
import com.micasong.player.playback.PlaybackConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumDetailState(
    val album: Album? = null,
    val tracks: List<Track> = emptyList(),
) {
    val totalDurationMs: Long get() = tracks.sumOf { it.durationMs }
}

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val playback: PlaybackConnection,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val albumId: Long = savedStateHandle.get<Long>("albumId") ?: -1L

    val state = kotlinx.coroutines.flow.combine(
        repository.album(albumId),
        repository.tracksByAlbum(albumId),
    ) { album, tracks -> AlbumDetailState(album, tracks) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlbumDetailState())

    fun play(shuffle: Boolean = false) {
        val tracks = state.value.tracks
        if (tracks.isNotEmpty()) playback.playTracks(tracks, 0, shuffle)
    }

    fun playAt(index: Int) {
        val tracks = state.value.tracks
        if (tracks.isNotEmpty()) playback.playTracks(tracks, index)
    }

    fun addToQueue() = playback.addToQueue(state.value.tracks)

    fun toggleFavorite() {
        val album = state.value.album ?: return
        viewModelScope.launch { repository.toggleAlbumFavorite(album) }
    }
}
