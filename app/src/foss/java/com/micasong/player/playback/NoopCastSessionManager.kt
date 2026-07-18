package com.micasong.player.playback

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import javax.inject.Inject

/**
 * FOSS build has no Google Cast dependency, so casting is unavailable and this does nothing. The
 * app plays locally exactly as before. (Cast lives only in the "full" flavor — spec §45.)
 */
class NoopCastSessionManager @Inject constructor() : CastSessionManager {
    override fun attach(context: Context, session: MediaSession, localPlayer: Player) = Unit
    override fun release() = Unit
}
