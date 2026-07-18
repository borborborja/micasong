package com.micasong.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micasong.player.data.model.Track
import com.micasong.player.data.repository.MediaRepository
import com.micasong.player.playback.PlaybackConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.flow.stateIn
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

    /** Lyrics of the current track (spec §41), fetched+cached when the track changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentLyrics: StateFlow<com.micasong.player.data.lyrics.Lyrics?> = state
        .map { it.mediaId?.removePrefix("track/")?.toLongOrNull() }
        .distinctUntilChanged()
        .mapLatest { id -> if (id == null) null else repository.lyricsFor(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Chapters of the current audiobook/podcast track (spec §19), empty when none. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentChapters: StateFlow<com.micasong.player.data.audio.ChapterInfo> = state
        .map { it.mediaId?.removePrefix("track/")?.toLongOrNull() }
        .distinctUntilChanged()
        .mapLatest { id ->
            val t = if (id == null) null else repository.trackById(id)
            com.micasong.player.data.audio.Chapters.fromJson(t?.chaptersJson, t?.durationMs ?: 0L)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.micasong.player.data.audio.ChapterInfo(emptyList()))

    /** Downsampled amplitude envelope of the current track for the waveform seekbar (spec §13), or null. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentWaveform: StateFlow<FloatArray?> = state
        .map { it.mediaId?.removePrefix("track/")?.toLongOrNull() }
        .distinctUntilChanged()
        .mapLatest { id ->
            val t = if (id == null) null else repository.trackById(id)
            t?.waveformJson?.let { json ->
                runCatching { kotlinx.serialization.json.Json.decodeFromString<List<Float>>(json).toFloatArray() }.getOrNull()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 0–10 rating of the track currently playing, reacting to DB changes (spec §11). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentRating: StateFlow<Int> = state
        .map { it.mediaId?.removePrefix("track/")?.toLongOrNull() }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(0) else repository.trackFlow(id).map { it?.userRating ?: 0 }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Whether the track currently playing is a favorite, reacting to DB changes (spec §10). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentFavorite: StateFlow<Boolean> = state
        .map { it.mediaId?.removePrefix("track/")?.toLongOrNull() }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(false) else repository.trackFlow(id).map { it?.isFavorite ?: false }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setRating(rating: Int) {
        val id = state.value.mediaId?.removePrefix("track/")?.toLongOrNull() ?: return
        viewModelScope.launch { repository.setTrackRating(id, rating.coerceIn(0, 10)) }
    }

    /** Remaining sleep-timer milliseconds (null = off), for the Now Playing countdown (spec §12). */
    val sleepRemainingMs: StateFlow<Long?> = playback.sleepRemainingMs

    fun setSleepTimer(minutes: Int) = playback.setSleepTimer(minutes)
    fun cancelSleepTimer() = playback.cancelSleepTimer()

    /** The playback queue for the "Colas" sheet (spec §13). */
    val queue = playback.queue

    fun jumpToQueueItem(index: Int) = playback.jumpTo(index)
    fun removeQueueItem(index: Int) = playback.removeQueueItem(index)

    // ---- Multiple saved queues ("Colas", spec §16) ----
    val queueBook = playback.queueBook
    fun saveCurrentQueue(name: String) = playback.saveCurrentQueue(name)
    fun loadQueue(id: Long) = playback.loadQueue(id)
    fun deleteQueue(id: Long) = playback.deleteQueue(id)

    // ---- Smart Queue / Smart Flow (spec §16) ----
    val smartFlowMode = playback.smartFlowMode
    val smartQueueMode = playback.smartQueueMode
    fun setSmartFlowMode(mode: com.micasong.player.data.smart.SmartFlowMode?) = playback.setSmartFlowMode(mode)
    fun setSmartQueueMode(mode: com.micasong.player.data.smart.SmartQueueMode?) = playback.setSmartQueueMode(mode)

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
