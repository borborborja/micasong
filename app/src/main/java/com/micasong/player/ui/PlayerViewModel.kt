package com.micasong.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micasong.player.data.model.Track
import com.micasong.player.data.repository.MediaRepository
import com.micasong.player.playback.PlaybackConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity-scoped view model exposing the shared now-playing state and playback controls to
 * the mini-player and the expanded Now Playing screen.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: PlaybackConnection,
    private val repository: MediaRepository,
) : ViewModel() {

    val state = playback.state

    fun togglePlayPause() = playback.togglePlayPause()
    fun next() = playback.next()
    fun previous() = playback.previous()
    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)
    fun toggleShuffle() = playback.toggleShuffle()
    fun cycleRepeat() = playback.cycleRepeat()
    fun setSpeed(speed: Float) = playback.setSpeed(speed)

    fun toggleFavoriteCurrent() {
        val mediaId = state.value.mediaId ?: return
        val id = mediaId.removePrefix("track/").toLongOrNull() ?: return
        viewModelScope.launch {
            repository.trackById(id)?.let { repository.toggleTrackFavorite(it) }
        }
    }

    fun play(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) =
        playback.playTracks(tracks, startIndex, shuffle)
}
