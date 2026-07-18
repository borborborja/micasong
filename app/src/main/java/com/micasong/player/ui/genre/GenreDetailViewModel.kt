package com.micasong.player.ui.genre

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micasong.player.data.repository.MediaRepository
import com.micasong.player.playback.PlaybackConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class GenreDetailViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val playback: PlaybackConnection,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val genreName: String = android.net.Uri.decode(savedStateHandle.get<String>("genreName") ?: "")

    val tracks = repository.tracksByGenre(genreName)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun playAt(index: Int) {
        val list = tracks.value
        if (list.isNotEmpty()) playback.playTracks(list, index)
    }
}
