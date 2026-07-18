package com.micasong.player.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.MoreExecutors
import com.micasong.player.data.model.Track
import com.micasong.player.data.repository.MediaRepository
import com.micasong.player.data.smart.SmartFlowMode
import com.micasong.player.data.smart.SmartQueueMode
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.micasong.player.data.cache.DownloadState
import java.io.File
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

/** One entry of the playback queue, for the "Colas" list (spec §13). */
data class QueueItem(
    val index: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val isCurrent: Boolean,
)

/**
 * UI-facing bridge to [PlaybackService]. Owns a [MediaController], mirrors its state into a
 * [StateFlow] the Compose layer collects, and translates UI intents (play these tracks,
 * skip, seek, shuffle, repeat) into controller commands.
 */
@Singleton
class PlaybackConnection @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: MediaRepository,
    private val settings: com.micasong.player.data.settings.SettingsRepository,
    private val queueStore: com.micasong.player.data.queue.QueueStore,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    /** Remaining sleep-timer milliseconds, or null when no timer is armed (spec §12). */
    private val _sleepRemainingMs = MutableStateFlow<Long?>(null)
    val sleepRemainingMs: StateFlow<Long?> = _sleepRemainingMs.asStateFlow()
    private var sleepJob: Job? = null

    /** The full playback queue (spec §13), refreshed on every player event. */
    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    /** Smart Flow mode (spec §16): inserts a similar track after the current one on each change. */
    private val _smartFlowMode = MutableStateFlow<SmartFlowMode?>(null)
    val smartFlowMode: StateFlow<SmartFlowMode?> = _smartFlowMode.asStateFlow()

    /** Smart Queue mode (spec §16): keeps appending matching tracks so the queue never runs dry. */
    private val _smartQueueMode = MutableStateFlow<SmartQueueMode?>(null)
    val smartQueueMode: StateFlow<SmartQueueMode?> = _smartQueueMode.asStateFlow()
    private var extending = false

    /** trackId → local file path for completed downloads, so playback prefers the offline copy. */
    @Volatile private var localPaths: Map<Long, String> = emptyMap()

    // Volume pipeline: final controller volume = replayGain × fade (spec §13, §15).
    @Volatile private var rgVolume = 1f
    @Volatile private var fadeGain = 1f
    @Volatile private var rgMode = com.micasong.player.data.audio.ReplayGainMode.OFF
    @Volatile private var crossfadeMs = 0
    private var lastRgId: String? = null

    init {
        val future = MediaController.Builder(context, playbackSessionToken(context)).buildAsync()
        future.addListener({
            controller = future.get().also { c ->
                c.addListener(playerListener)
                pushState()
            }
            startPositionUpdates()
        }, MoreExecutors.directExecutor())

        // Keep the offline-file index fresh so tracks play from disk when downloaded (spec §35).
        repository.downloads
            .onEach { rows ->
                localPaths = rows
                    .filter { it.state == DownloadState.COMPLETED.ordinal && it.localPath != null }
                    .associate { it.trackId to it.localPath!! }
            }
            .launchIn(scope)

        // Track the audio settings the volume pipeline reacts to (spec §13, §15).
        settings.settings
            .onEach {
                rgMode = it.replayGainMode
                crossfadeMs = it.crossfadeMs
                if (rgMode == com.micasong.player.data.audio.ReplayGainMode.OFF) { rgVolume = 1f; applyVolume() }
                lastRgId = null // force a recompute against the new mode
            }
            .launchIn(scope)
    }

    private fun applyVolume() {
        controller?.volume = (rgVolume * fadeGain).coerceIn(0f, 1f)
    }

    /** Recompute the ReplayGain attenuation for the current track (spec §15). */
    private fun updateReplayGain(mediaId: String?) {
        val id = mediaId?.removePrefix("track/")?.toLongOrNull()
        if (id == null || rgMode == com.micasong.player.data.audio.ReplayGainMode.OFF) {
            rgVolume = 1f; applyVolume(); return
        }
        scope.launch {
            val t = repository.trackById(id) ?: return@launch
            val info = com.micasong.player.data.audio.ReplayGainInfo(t.trackGainDb, t.albumGainDb, t.trackPeak, t.albumPeak)
            rgVolume = com.micasong.player.data.audio.ReplayGain.volumeFor(info, rgMode, albumContext = false)
            applyVolume()
        }
    }

    /** Fade the volume in/out near track boundaries when crossfade is enabled (spec §13). */
    private fun updateFade() {
        val c = controller ?: return
        if (crossfadeMs <= 0) { if (fadeGain != 1f) { fadeGain = 1f; applyVolume() }; return }
        val pos = c.currentPosition.coerceAtLeast(0)
        val dur = c.duration.coerceAtLeast(0)
        val curve = com.micasong.player.data.audio.FadeCurve.SMOOTH
        val g = when {
            dur > 0 && dur - pos <= crossfadeMs -> curve.gainOut(((crossfadeMs - (dur - pos)).toFloat() / crossfadeMs))
            pos < crossfadeMs -> curve.gainIn(pos.toFloat() / crossfadeMs)
            else -> 1f
        }
        if (g != fadeGain) { fadeGain = g; applyVolume() }
    }

    /** Build a MediaItem, substituting the local downloaded file when one exists (spec §35). */
    private fun Track.toPlayableItem(): MediaItem {
        val local = localPaths[id] ?: return toMediaItem()
        return toMediaItem().buildUpon().setUri(Uri.fromFile(File(local))).build()
    }

    private fun List<Track>.toPlayableItems(): List<MediaItem> = map { it.toPlayableItem() }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = pushState()

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            applySmartFlow(mediaItem)
            fadeGain = 1f // reset fade for the new track
        }
    }

    /** Smart Flow (spec §16): insert similar tracks right after the current one on each change. */
    private fun applySmartFlow(current: MediaItem?) {
        val mode = _smartFlowMode.value ?: return
        val id = current?.mediaId?.removePrefix("track/")?.toLongOrNull() ?: return
        scope.launch {
            val track = repository.trackById(id) ?: return@launch
            val inserts = repository.smartFlowInsertions(track, mode)
            val c = controller ?: return@launch
            if (inserts.isNotEmpty()) {
                val at = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
                c.addMediaItems(at, inserts.toPlayableItems())
            }
        }
    }

    /** Smart Queue (spec §16): when the queue is nearly exhausted, append a fresh matching batch. */
    private fun maybeExtendSmartQueue() {
        val mode = _smartQueueMode.value ?: return
        val c = controller ?: return
        if (extending) return
        val remaining = c.mediaItemCount - c.currentMediaItemIndex - 1
        if (remaining > 2) return
        val seedId = c.currentMediaItem?.mediaId?.removePrefix("track/")?.toLongOrNull() ?: return
        extending = true
        scope.launch {
            try {
                val seed = repository.trackById(seedId) ?: return@launch
                val more = repository.smartQueueExtension(listOf(seed), mode, count = 10)
                if (more.isNotEmpty()) controller?.addMediaItems(more.toPlayableItems())
            } finally {
                extending = false
            }
        }
    }

    fun setSmartFlowMode(mode: SmartFlowMode?) { _smartFlowMode.value = mode }
    fun setSmartQueueMode(mode: SmartQueueMode?) {
        _smartQueueMode.value = mode
        if (mode != null) maybeExtendSmartQueue()
    }

    private fun startPositionUpdates() {
        scope.launch {
            while (true) {
                if (controller?.isPlaying == true) { pushState(); maybeExtendSmartQueue(); updateFade() }
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
        refreshQueue(c)
        // Recompute ReplayGain when the current track changes (spec §15).
        if (item?.mediaId != lastRgId) {
            lastRgId = item?.mediaId
            updateReplayGain(item?.mediaId)
        }
    }

    private fun refreshQueue(c: MediaController) {
        val current = c.currentMediaItemIndex
        _queue.value = (0 until c.mediaItemCount).map { i ->
            val mi = c.getMediaItemAt(i)
            QueueItem(
                index = i,
                mediaId = mi.mediaId,
                title = mi.mediaMetadata.title?.toString().orEmpty(),
                artist = mi.mediaMetadata.artist?.toString().orEmpty(),
                artworkUri = mi.mediaMetadata.artworkUri?.toString(),
                isCurrent = i == current,
            )
        }
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
        c.setMediaItems(ordered.toPlayableItems(), start, 0L)
        c.prepare()
        c.play()
    }

    fun playTrack(track: Track) = playTracks(listOf(track))

    /** Play internet radio (spec §10): queue all stations so skip cycles, leading with the tapped one. */
    fun playRadio(stations: List<com.micasong.player.data.radio.RadioStation>, clickedId: Long) {
        val c = controller ?: return
        if (stations.isEmpty()) return
        val ordered = com.micasong.player.data.radio.InternetRadio.orderedQueue(stations, clickedId)
        val items = ordered.map { st ->
            MediaItem.Builder()
                .setMediaId("radio/${st.id}")
                .setUri(st.streamUrl)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(st.name)
                        .setIsPlayable(true)
                        .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                        .apply { st.imageUrl?.let { setArtworkUri(Uri.parse(it)) } }
                        .build()
                )
                .build()
        }
        c.setMediaItems(items, 0, 0L)
        c.prepare()
        c.play()
    }

    fun addToQueue(tracks: List<Track>) {
        val c = controller ?: return
        c.addMediaItems(tracks.toPlayableItems())
        if (c.mediaItemCount == tracks.size) { c.prepare(); c.play() }
    }

    fun playNext(tracks: List<Track>) {
        val c = controller ?: return
        val index = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
        c.addMediaItems(index, tracks.toPlayableItems())
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

    // ---- Multiple saved queues ("Colas", spec §16) ----
    val queueBook = queueStore.queueBook

    /** Save the current playback queue as a named, independent queue (spec §16). */
    fun saveCurrentQueue(name: String) {
        val c = controller ?: return
        val trackIds = (0 until c.mediaItemCount).mapNotNull {
            c.getMediaItemAt(it).mediaId.removePrefix("track/").toLongOrNull()
        }
        if (trackIds.isEmpty()) return
        val saved = com.micasong.player.data.queue.SavedQueue(
            id = System.currentTimeMillis(),
            name = name.ifBlank { "Cola" },
            trackIds = trackIds,
            currentIndex = c.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = c.currentPosition.coerceAtLeast(0),
            repeatMode = c.repeatMode,
            shuffle = c.shuffleModeEnabled,
            speed = c.playbackParameters.speed,
        )
        scope.launch { queueStore.update { it.add(saved) } }
    }

    /** Load a saved queue into the player, restoring its position and modes (spec §16). */
    fun loadQueue(id: Long) {
        scope.launch {
            val saved = queueStore.current().queues.firstOrNull { it.id == id } ?: return@launch
            val tracks = repository.tracksByIds(saved.trackIds)
                .sortedBy { saved.trackIds.indexOf(it.id) } // preserve the saved order
            val c = controller ?: return@launch
            if (tracks.isEmpty()) return@launch
            c.shuffleModeEnabled = false
            c.setMediaItems(tracks.toPlayableItems(), saved.currentIndex.coerceIn(0, tracks.lastIndex), saved.positionMs)
            c.repeatMode = saved.repeatMode
            c.shuffleModeEnabled = saved.shuffle
            c.setPlaybackSpeed(saved.speed)
            c.prepare()
            c.play()
            queueStore.update { it.switchTo(id) }
        }
    }

    fun deleteQueue(id: Long) { scope.launch { queueStore.update { it.remove(id) } } }
    fun renameQueue(id: Long, name: String) { scope.launch { queueStore.update { it.rename(id, name) } } }

    /** Jump to queue position [index] and start playing it (spec §13). */
    fun jumpTo(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekTo(index, 0L)
        c.play()
    }

    /** Remove the queue entry at [index]; the player keeps playing the current item. */
    fun removeQueueItem(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
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
