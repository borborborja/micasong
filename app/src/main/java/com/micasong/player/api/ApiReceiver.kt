package com.micasong.player.api

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.micasong.player.data.repository.MediaRepository
import com.micasong.player.playback.PlaybackConnection
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Broadcast automation API (spec §42), mirroring the reference app's Tasker-friendly intents
 * with MiCaSong's own action namespace. This lets external automation drive playback and
 * trigger syncs. Actions map 1:1 to the documented command set; a representative subset is
 * wired for playback control and sync.
 */
@AndroidEntryPoint
class ApiReceiver : BroadcastReceiver() {

    @Inject lateinit var playback: PlaybackConnection
    @Inject lateinit var repository: MediaRepository

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MEDIA_COMMAND -> handleCommand(intent.getStringExtra(EXTRA_COMMAND))
            ACTION_MEDIA_SYNC, "$PREFIX.CUSTOM_ACTION" -> {
                val pending = goAsync()
                scope.launch {
                    try { repository.syncAll() } finally { pending.finish() }
                }
            }
        }
    }

    private fun handleCommand(command: String?) {
        when (command) {
            "play", "pause" -> playback.togglePlayPause()
            "next" -> playback.next()
            "previous" -> playback.previous()
            "shuffle" -> playback.toggleShuffle()
            "repeat" -> playback.cycleRepeat()
        }
    }

    companion object {
        private const val PREFIX = "com.micasong.api"
        const val ACTION_MEDIA_COMMAND = "$PREFIX.MEDIA_COMMAND"
        const val ACTION_MEDIA_SYNC = "$PREFIX.MEDIA_SYNC"
        const val EXTRA_COMMAND = "COMMAND"
    }
}
