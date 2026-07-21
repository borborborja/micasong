package com.micasong.player.playback

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastContext
import com.micasong.player.data.audio.AudioMime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Chromecast bridge (spec §36), present only in the "full" flavor. Builds a media3 [CastPlayer]
 * from the shared [CastContext] and, whenever a Cast session connects, moves the queue and playback
 * position from the local ExoPlayer onto the cast device — handing it back when the session ends.
 *
 * Note: only network-reachable streams cast (server stream URLs); `content://`/`file://` tracks on
 * the phone are not reachable by the Chromecast, so they are filtered out of the cast queue and the
 * handoff is skipped entirely when nothing in the queue is castable.
 */
@Singleton
class RealCastSessionManager @Inject constructor() : CastSessionManager {

    private var castPlayer: CastPlayer? = null
    private var session: MediaSession? = null
    private var localPlayer: Player? = null
    private var casting = false

    override fun attach(context: Context, session: MediaSession, localPlayer: Player) {
        this.session = session
        this.localPlayer = localPlayer

        // getSharedInstance reads the OptionsProvider from the manifest; it throws if Google Play
        // services / Cast are unavailable, in which case we simply stay local-only.
        val castContext = runCatching {
            CastContext.getSharedInstance(context.applicationContext)
        }.getOrNull() ?: return

        val cp = CastPlayer(castContext, LenientMediaItemConverter())
        cp.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() = setCurrentPlayer(cp)
            override fun onCastSessionUnavailable() = this@RealCastSessionManager.localPlayer?.let(::setCurrentPlayer) ?: Unit
        })
        castPlayer = cp

        // Adopt an already-live cast session (e.g. the service restarted while casting).
        if (cp.isCastSessionAvailable) setCurrentPlayer(cp)
    }

    /** Move the queue + position from the session's current player onto [target] and switch to it. */
    private fun setCurrentPlayer(target: Player) {
        val s = session ?: return
        val from = s.player
        if (from === target) return

        val items = (0 until from.mediaItemCount).map { from.getMediaItemAt(it) }
        var index = from.currentMediaItemIndex.coerceAtLeast(0)
        var position = from.currentPosition.coerceAtLeast(0)
        val playWhenReady = from.playWhenReady

        // The Chromecast can only fetch http(s) streams — keep the castable subset and stay on the
        // local player when there is none, instead of handing it an unplayable (crashing) queue.
        var queue = items
        if (target === castPlayer) {
            val castable = items.filter { isCastable(it) }
            if (castable.isEmpty() && items.isNotEmpty()) return
            val currentId = items.getOrNull(index)?.mediaId
            val newIndex = castable.indexOfFirst { it.mediaId == currentId }
            if (newIndex < 0) position = 0
            index = newIndex.coerceAtLeast(0)
            queue = castable
        }

        from.pause() // release audio focus before the other player takes over
        s.player = target
        if (queue.isNotEmpty()) {
            target.setMediaItems(queue, index, position)
            target.playWhenReady = playWhenReady
            target.prepare()
        }
        casting = target === castPlayer
    }

    private fun isCastable(item: MediaItem): Boolean {
        val scheme = item.localConfiguration?.uri?.scheme?.lowercase()
        return scheme == "http" || scheme == "https"
    }

    override fun isCasting(): Boolean = casting

    override fun release() {
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        castPlayer = null
        session = null
        localPlayer = null
        casting = false
    }
}

/**
 * media3's [DefaultMediaItemConverter] throws ("The item must specify its mimeType") on items with
 * no MIME type, which several providers cannot supply. Fill in a best-effort audio MIME before
 * delegating so casting works for every backend.
 */
private class LenientMediaItemConverter : MediaItemConverter {
    private val delegate = DefaultMediaItemConverter()

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem = delegate.toMediaItem(mediaQueueItem)

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val config = mediaItem.localConfiguration
        if (config?.mimeType != null) return delegate.toMediaQueueItem(mediaItem)
        val guessed = AudioMime.forUrl(config?.uri?.toString())
        return delegate.toMediaQueueItem(mediaItem.buildUpon().setMimeType(guessed).build())
    }
}
