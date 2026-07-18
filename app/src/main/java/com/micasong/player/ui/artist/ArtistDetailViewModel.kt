package com.micasong.player.ui.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micasong.player.data.model.Album
import com.micasong.player.data.model.Artist
import com.micasong.player.data.model.Track
import com.micasong.player.data.repository.MediaRepository
import com.micasong.player.playback.PlaybackConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ArtistDetailState(
    val artist: Artist? = null,
    val albums: List<Album> = emptyList(),
    val topTracks: List<Track> = emptyList(),
)

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val playback: PlaybackConnection,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val artistId: Long = savedStateHandle.get<Long>("artistId") ?: -1L

    val state = combine(
        repository.artist(artistId),
        repository.albumsByArtist(artistId),
        repository.tracksByArtist(artistId),
    ) { artist, albums, tracks ->
        ArtistDetailState(artist, albums, tracks.take(10))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArtistDetailState())

    fun playTopTracks(startIndex: Int) {
        val tracks = state.value.topTracks
        if (tracks.isNotEmpty()) playback.playTracks(tracks, startIndex)
    }

    fun shuffleAll() {
        val tracks = state.value.topTracks
        if (tracks.isNotEmpty()) playback.playTracks(tracks, 0, shuffle = true)
    }
}
