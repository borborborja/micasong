package com.micasong.player.data.cache

import android.content.Context
import android.net.ConnectivityManager
import com.micasong.player.data.db.DownloadDao
import com.micasong.player.data.db.MusicDao
import com.micasong.player.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Minimal contract the repository uses to kick the download loop (keeps tests network-free). */
fun interface DownloadTrigger {
    fun trigger()
}

/**
 * Executes queued offline downloads (spec §35). Reacts to [DownloadDao] rows enqueued by
 * [com.micasong.player.data.repository.MediaRepository]: streams each track's bytes to
 * `filesDir/offline/<trackId>.<ext>` with OkHttp, publishing progress back to the row.
 *
 * Runs on an app-scoped coroutine — reliable while the process lives (playback keeps it alive).
 * Persisting across process death via WorkManager is a future hardening step.
 */
@Singleton
class DownloadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val musicDao: MusicDao,
    private val settings: SettingsRepository,
) : DownloadTrigger {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder().addInterceptor(BasicAuthInterceptor).build()
    private val running = AtomicBoolean(false)
    private val dir = File(context.filesDir, "offline").apply { mkdirs() }

    /** Kick the processing loop if it isn't already running. Safe to call repeatedly. */
    override fun trigger() {
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (true) {
                    if (!canDownloadNow()) break // Wi-Fi-only on a metered network: wait
                    val next = nextQueuedTrackId() ?: break
                    downloadOne(next)
                }
            } finally {
                running.set(false)
            }
        }
    }

    /** Honour "downloads only on Wi-Fi" (spec §35): pause when the network is metered. */
    private suspend fun canDownloadNow(): Boolean {
        if (!settings.settings.first().downloadsWifiOnly) return true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return cm?.isActiveNetworkMetered != true
    }

    private suspend fun nextQueuedTrackId(): Long? {
        val snapshot = downloadDao.snapshot()
        val queue = DownloadQueue(
            tasks = snapshot
                .filter { it.state == DownloadState.QUEUED.ordinal }
                .map { DownloadTask(it.trackId, DownloadState.QUEUED, it.tier, it.progress, it.enqueuedAt) },
            maxConcurrent = 1,
        )
        return queue.nextToStart().firstOrNull()?.trackId
    }

    private suspend fun downloadOne(trackId: Long) {
        val track = musicDao.trackById(trackId)
        if (track == null) { downloadDao.delete(trackId); return }
        val url = track.mediaUri
        // Only network streams are downloadable; local content:// files are already offline.
        if (!url.startsWith("http", ignoreCase = true)) {
            downloadDao.updateState(trackId, DownloadState.FAILED.ordinal, 0f)
            return
        }

        downloadDao.updateState(trackId, DownloadState.DOWNLOADING.ordinal, 0f)
        val file = File(dir, "$trackId.${extensionFor(track.mimeType)}")
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body
                if (!resp.isSuccessful || body == null) {
                    downloadDao.updateState(trackId, DownloadState.FAILED.ordinal, 0f)
                    return
                }
                val total = body.contentLength()
                var read = 0L
                file.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var n = input.read(buffer)
                        var lastPublished = 0f
                        while (n >= 0) {
                            out.write(buffer, 0, n)
                            read += n
                            if (total > 0) {
                                val p = (read.toFloat() / total).coerceIn(0f, 1f)
                                if (p - lastPublished >= 0.02f) { downloadDao.updateProgress(trackId, p); lastPublished = p }
                            }
                            n = input.read(buffer)
                        }
                    }
                }
                downloadDao.markComplete(trackId, file.absolutePath, file.length())
            }
        } catch (e: Exception) {
            runCatching { file.delete() }
            downloadDao.updateState(trackId, DownloadState.FAILED.ordinal, 0f)
        }
    }

    private fun extensionFor(mime: String?): String = when {
        mime == null -> "audio"
        mime.contains("flac") -> "flac"
        mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
        mime.contains("aac") || mime.contains("mp4") || mime.contains("m4a") -> "m4a"
        mime.contains("ogg") || mime.contains("vorbis") -> "ogg"
        mime.contains("opus") -> "opus"
        mime.contains("wav") -> "wav"
        else -> "audio"
    }
}
