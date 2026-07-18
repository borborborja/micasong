package com.micasong.player.playback

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.google.android.gms.cast.framework.CastContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Chromecast bridge (spec §36), present only in the "full" flavor. Builds a media3 [CastPlayer]
 * from the shared [CastContext] and, whenever a Cast session connects, moves the queue and playback
 * position from the local ExoPlayer onto the cast device — handing it back when the session ends.
 *
 * Note: only network-reachable streams cast (server stream URLs); `content://` files on the phone
 * are not reachable by the Chromecast, so local-file playback stays on the device.
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

        val cp = CastPlayer(castContext)
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
        val index = from.currentMediaItemIndex.coerceAtLeast(0)
        val position = from.currentPosition.coerceAtLeast(0)
        val playWhenReady = from.playWhenReady

        from.pause() // release audio focus before the other player takes over
        s.player = target
        if (items.isNotEmpty()) {
            target.setMediaItems(items, index, position)
            target.playWhenReady = playWhenReady
            target.prepare()
        }
        casting = target === castPlayer
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
