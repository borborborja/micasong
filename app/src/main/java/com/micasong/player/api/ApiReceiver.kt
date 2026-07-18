package com.micasong.player.api

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.micasong.player.data.repository.MediaRepository
import com.micasong.player.playback.PlaybackConnection
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Broadcast automation API (spec §42), mirroring the reference app's Tasker-friendly intents with
 * MiCaSong's own action namespace. The intent is parsed into a typed [ApiCommand] (unit-tested via
 * [ApiCommandParser]) and dispatched to playback / sync / provider control.
 */
@AndroidEntryPoint
class ApiReceiver : BroadcastReceiver() {

    @Inject lateinit var playback: PlaybackConnection
    @Inject lateinit var repository: MediaRepository

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        val command = ApiCommandParser.parse(intent.action, IntentExtras(intent))
        when (command) {
            is ApiCommand.MediaControl -> handleControl(command)
            is ApiCommand.MediaSync -> runAsync { repository.syncAll() }
            is ApiCommand.MediaStart -> handleStart(command)
            is ApiCommand.CustomAction -> handleCustom(command)
            else -> Log.d(TAG, "Unhandled API command: $command")
        }
    }

    private fun handleControl(cmd: ApiCommand.MediaControl) {
        when (cmd.command) {
            "play", "pause" -> playback.togglePlayPause()
            "next" -> playback.next()
            "previous" -> playback.previous()
            "shuffle" -> playback.toggleShuffle()
            "repeat" -> playback.cycleRepeat()
            "seek" -> cmd.intParam?.let { playback.seekTo(it * 1000L) }
            else -> Log.d(TAG, "Unhandled media command: ${cmd.command}")
        }
    }

    private fun handleStart(cmd: ApiCommand.MediaStart) {
        when (cmd.mediaType) {
            "song_mix" -> runAsync {
                val tracks = repository.trackMix()
                if (tracks.isNotEmpty()) playback.playTracks(tracks, 0, shuffle = cmd.shuffle)
            }
            else -> Log.d(TAG, "MEDIA_START ${cmd.mediaType} not yet wired")
        }
    }

    private fun handleCustom(cmd: ApiCommand.CustomAction) {
        when (cmd.action) {
            "force_provider_connection" -> {
                val rowId = (cmd.providerId ?: return) - PROVIDER_ID_BASE
                val connection = cmd.activeConnection ?: 1
                runAsync { repository.setActiveConnection(rowId, connection) }
            }
            "cleanup_offline_cache", "load_media_queue" -> runAsync { repository.syncAll() }
            else -> Log.d(TAG, "Unhandled custom action: ${cmd.action}")
        }
    }

    private fun runAsync(block: suspend () -> Unit) {
        val pending = goAsync()
        scope.launch {
            try { block() } finally { pending.finish() }
        }
    }

    /** Intent-backed [ApiExtras] that tolerates int/string/bool encodings from `adb`/Tasker. */
    private class IntentExtras(private val intent: Intent) : ApiExtras {
        override fun string(key: String): String? = intent.extras?.get(key)?.toString()
        override fun int(key: String): Int? {
            val v = intent.extras?.get(key) ?: return null
            return (v as? Number)?.toInt() ?: v.toString().toIntOrNull()
        }
        override fun bool(key: String, default: Boolean): Boolean = when (val v = intent.extras?.get(key)) {
            is Boolean -> v
            is String -> v.equals("true", true) || v == "1"
            is Number -> v.toInt() != 0
            else -> default
        }
    }

    companion object {
        private const val TAG = "ApiReceiver"
        private const val PROVIDER_ID_BASE = 1000L

        // Used by the home-screen widget to drive playback (spec §40, §42).
        const val ACTION_MEDIA_COMMAND = "${ApiCommandParser.PREFIX}.MEDIA_COMMAND"
        const val EXTRA_COMMAND = "COMMAND"
    }
}
