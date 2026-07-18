package com.micasong.player.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.micasong.player.data.audio.PlaybackMarking
import com.micasong.player.data.repository.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Observes the player to maintain per-user playback state (spec §10, §20): play counts,
 * last-played timestamps and resume points. A play is only counted once a track finishes
 * naturally, approximating the spec's "minimum play % before marking as played".
 */
class PlaybackStatsListener(
    private val repository: MediaRepository,
    private val scope: CoroutineScope,
    private val playerProvider: () -> ExoPlayer,
) : Player.Listener {

    private var lastMediaId: String? = null

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // A natural transition means the previous track completed → count it as a play.
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            lastMediaId?.let(::registerPlay)
        }
        // Persist the resume point of the item we left, then track the new one.
        lastMediaId = mediaItem?.mediaId
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            playerProvider().currentMediaItem?.mediaId?.let(::registerPlay)
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying) saveResumePosition()
    }

    private fun registerPlay(mediaId: String) {
        val id = mediaId.trackId() ?: return
        scope.launch {
            repository.registerPlay(id, System.currentTimeMillis())
            repository.scrobble(id) // report the completed play to the server (spec §9)
        }
    }

    private fun saveResumePosition() {
        val player = playerProvider()
        val id = player.currentMediaItem?.mediaId?.trackId() ?: return
        val pos = player.currentPosition
        val duration = player.duration.coerceAtLeast(0)
        // Only persist a resume point once past the minimum time and before the track is finished.
        if (!PlaybackMarking.shouldSaveResume(pos, duration)) return
        scope.launch { repository.setResumePosition(id, pos) }
    }

    private fun String.trackId(): Long? =
        removePrefix(MediaTree.Ids.PREFIX_TRACK).toLongOrNull()
}
