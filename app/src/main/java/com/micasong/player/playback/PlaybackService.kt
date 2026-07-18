package com.micasong.player.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionToken
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.micasong.player.MainActivity
import com.micasong.player.data.audio.EqPresets
import com.micasong.player.data.audio.EqProfile
import com.micasong.player.data.repository.MediaRepository
import com.micasong.player.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The single playback service. As a [MediaLibraryService] it simultaneously powers:
 *  - the in-app Compose player (via a MediaController),
 *  - the system media notification / lock screen,
 *  - Android Auto and Wear OS browsing & playback (via the library browse tree).
 *
 * ExoPlayer is configured for gapless playback and proper audio-focus handling (spec §11, §18).
 */
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var mediaTree: MediaTree
    @Inject lateinit var repository: MediaRepository
    @Inject lateinit var settings: SettingsRepository

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private val audioEffects = AudioEffectsController()
    private var currentEqProfile: EqProfile = EqProfile(id = "main", name = "Principal", bands = EqPresets.flat(10))

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)   // pause when headphones unplugged (§18)
            .build()

        val sessionActivity = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(sessionActivity)
            .build()

        player.addListener(PlaybackStatsListener(repository, serviceScope) { player })

        // Attach the equalizer chain to the audio session for local playback (spec §14), and
        // re-apply the active profile whenever the session (output) changes.
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
                audioEffects.attach(audioSessionId)
                audioEffects.applyProfile(currentEqProfile)
            }
        })

        // React to equalizer changes made in the UI.
        serviceScope.launch {
            settings.eqProfile.collect { profile ->
                currentEqProfile = profile
                audioEffects.applyProfile(profile)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Stop the service when the app is swiped away and nothing is playing.
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        audioEffects.release()
        session.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
            LibraryResult.ofItem(mediaTree.rootItem(), params)
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            val children = mediaTree.children(parentId)
            LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
            val item = mediaTree.resolvePlayable(mediaId)
            if (item != null) LibraryResult.ofItem(item, null)
            else LibraryResult.ofError(SessionError(SessionError.ERROR_BAD_VALUE, "No encontrado"))
        }

        /**
         * MediaItems handed across the Binder lose their playback URI, and browsable folders
         * (e.g. an album chosen in Android Auto) must be expanded into their tracks. Rebuild a
         * fully playable list here.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = serviceScope.future {
            val resolved = mutableListOf<MediaItem>()
            for (item in mediaItems) {
                val searchQuery = item.requestMetadata.searchQuery
                when {
                    item.localConfiguration != null -> resolved += item
                    !searchQuery.isNullOrBlank() ->    // voice search from Android Auto (spec §38)
                        resolved += repository.searchTracks(searchQuery).first().map { it.toMediaItem() }
                    item.mediaId.startsWith(MediaTree.Ids.PREFIX_TRACK) ->
                        mediaTree.resolvePlayable(item.mediaId)?.let { resolved += it }
                    else -> resolved += mediaTree.tracksForPlayback(item.mediaId).map { it.toMediaItem() }
                }
            }
            resolved
        }
    }
}

/** Token helper used by the UI-side connection. */
fun playbackSessionToken(context: android.content.Context): SessionToken =
    SessionToken(context, android.content.ComponentName(context, PlaybackService::class.java))
