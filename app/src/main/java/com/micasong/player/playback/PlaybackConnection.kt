package com.micasong.player.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.MoreExecutors
import com.micasong.player.data.model.Track
import com.micasong.player.data.smart.WeightedShuffle
import com.micasong.player.widget.NowPlayingWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Immutable snapshot of what the "now playing" UI needs to render. */
data class NowPlayingState(
    val hasItem: Boolean = false,
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queueSize: Int = 0,
    val queueIndex: Int = 0,
    val speed: Float = 1f,
)

/**
 * UI-facing bridge to [PlaybackService]. Owns a [MediaController], mirrors its state into a
 * [StateFlow] the Compose layer collects, and translates UI intents (play these tracks,
 * skip, seek, shuffle, repeat) into controller commands.
 */
@Singleton
class PlaybackConnection @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    /** Remaining sleep-timer milliseconds, or null when no timer is armed (spec §12). */
    private val _sleepRemainingMs = MutableStateFlow<Long?>(null)
    val sleepRemainingMs: StateFlow<Long?> = _sleepRemainingMs.asStateFlow()
    private var sleepJob: Job? = null

    init {
        val future = MediaController.Builder(context, playbackSessionToken(context)).buildAsync()
        future.addListener({
            controller = future.get().also { c ->
                c.addListener(playerListener)
                pushState()
            }
            startPositionUpdates()
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = pushState()
    }

    private fun startPositionUpdates() {
        scope.launch {
            while (true) {
                if (controller?.isPlaying == true) pushState()
                delay(500)
            }
        }
    }

    private fun pushState() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val md = item?.mediaMetadata
        _state.value = NowPlayingState(
            hasItem = item != null,
            mediaId = item?.mediaId,
            title = md?.title?.toString().orEmpty(),
            artist = md?.artist?.toString().orEmpty(),
            album = md?.albumTitle?.toString().orEmpty(),
            artworkUri = md?.artworkUri?.toString(),
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.coerceAtLeast(0),
            bufferedMs = c.bufferedPosition.coerceAtLeast(0),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queueSize = c.mediaItemCount,
            queueIndex = c.currentMediaItemIndex,
            speed = c.playbackParameters.speed,
        )
        // Keep the home-screen widget (spec §40) in sync with playback.
        NowPlayingWidget.updateAll(context, _state.value)
    }

    // ---- intents ----
    fun playTracks(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        // Weighted shuffle (spec §17) reorders the list up front to spread artists/albums, rather
        // than relying on the player's plain random shuffle.
        val ordered = if (shuffle) WeightedShuffle.shuffleTracks(tracks) else tracks
        val start = if (shuffle) 0 else startIndex.coerceIn(0, tracks.lastIndex)
        c.shuffleModeEnabled = false
        c.setMediaItems(ordered.map { it.toMediaItem() }, start, 0L)
        c.prepare()
        c.play()
    }

    fun playTrack(track: Track) = playTracks(listOf(track))

    fun addToQueue(tracks: List<Track>) {
        val c = controller ?: return
        c.addMediaItems(tracks.map { it.toMediaItem() })
        if (c.mediaItemCount == tracks.size) { c.prepare(); c.play() }
    }

    fun playNext(tracks: List<Track>) {
        val c = controller ?: return
        val index = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
        c.addMediaItems(index, tracks.map { it.toMediaItem() })
        if (c.mediaItemCount == tracks.size) { c.prepare(); c.play() }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else { c.prepare(); c.play() }
    }

    fun next() = controller?.seekToNext().let { }
    fun previous() = controller?.seekToPrevious().let { }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun setSpeed(speed: Float) { controller?.setPlaybackSpeed(speed.coerceIn(0.25f, 3f)) }

    /** Arm a sleep timer for [minutes]; playback pauses when it elapses (spec §12). */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) { _sleepRemainingMs.value = null; return }
        sleepJob = scope.launch {
            var remaining = minutes * 60_000L
            _sleepRemainingMs.value = remaining
            while (isActive && remaining > 0) {
                delay(1_000)
                remaining -= 1_000
                _sleepRemainingMs.value = remaining.coerceAtLeast(0)
            }
            if (isActive) {
                controller?.pause()
                _sleepRemainingMs.value = null
            }
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepRemainingMs.value = null
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}
