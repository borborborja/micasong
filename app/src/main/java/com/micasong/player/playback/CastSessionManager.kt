package com.micasong.player.playback

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaSession

/**
 * Bridges the shared [PlaybackService] to the (proprietary) Google Cast stack (spec §36).
 *
 * The FOSS flavor binds a no-op implementation so it stays free of Google Play Services; the full
 * flavor binds a real one that builds a `CastPlayer` and swaps it into the media session whenever a
 * Chromecast session connects, handing playback back to the local ExoPlayer when it disconnects.
 */
interface CastSessionManager {

    /**
     * Begin managing casting for [session], whose local player is [localPlayer]. Called once from
     * [PlaybackService.onCreate]. Implementations must swap [MediaSession.setPlayer] on the session's
     * application thread when a cast session becomes (un)available.
     */
    fun attach(context: Context, session: MediaSession, localPlayer: Player)

    /** Whether a Chromecast session is currently receiving playback. */
    fun isCasting(): Boolean = false

    /** Release any Cast resources. Called from [PlaybackService.onDestroy]. */
    fun release()
}
