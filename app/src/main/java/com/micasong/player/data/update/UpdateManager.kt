package com.micasong.player.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.micasong.player.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class UpdatePhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, ERROR }

data class UpdateState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val latestVersion: String? = null,
    val progress: Float = 0f,
    val message: String? = null,
)

/**
 * In-app updater (user request): checks the latest GitHub release, downloads the APK matching this
 * flavor in the background, and launches the system installer. Works because every build is signed
 * with the same fixed key, so the downloaded APK installs as an upgrade over the running one.
 */
@Singleton
class UpdateManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient()

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var apkUrl: String? = null

    /** Query the latest release and decide whether it's newer than the running version. */
    fun check() {
        _state.value = UpdateState(phase = UpdatePhase.CHECKING)
        scope.launch {
            try {
                val json = getJson("https://api.github.com/repos/$REPO/releases/latest")
                    ?: run { _state.value = _state.value.copy(phase = UpdatePhase.ERROR, message = "No se pudo consultar GitHub."); return@launch }
                val latest = json.optString("tag_name").removePrefix("v").ifBlank { null }
                    ?: run { _state.value = _state.value.copy(phase = UpdatePhase.ERROR, message = "No hay releases publicadas."); return@launch }
                val assetName = if (BuildConfig.IS_FOSS) "MiCaSong-foss.apk" else "MiCaSong-full.apk"
                apkUrl = json.optJSONArray("assets")?.let { assets ->
                    (0 until assets.length()).map { assets.getJSONObject(it) }
                        .firstOrNull { it.optString("name") == assetName }
                        ?.optString("browser_download_url")
                }
                _state.value = if (isNewer(latest, BuildConfig.VERSION_NAME) && apkUrl != null) {
                    _state.value.copy(phase = UpdatePhase.AVAILABLE, latestVersion = latest)
                } else {
                    _state.value.copy(phase = UpdatePhase.UP_TO_DATE, latestVersion = latest)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(phase = UpdatePhase.ERROR, message = e.message)
            }
        }
    }

    /** Download the APK in the background (with progress), then launch the installer. */
    fun downloadAndInstall() {
        val url = apkUrl ?: return
        _state.value = _state.value.copy(phase = UpdatePhase.DOWNLOADING, progress = 0f)
        scope.launch {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val file = File(dir, "update.apk")
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    val body = resp.body ?: error("Descarga vacía")
                    if (!resp.isSuccessful) error("Error ${resp.code}")
                    val total = body.contentLength()
                    var read = 0L
                    file.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var n = input.read(buffer)
                            while (n >= 0) {
                                out.write(buffer, 0, n)
                                read += n
                                if (total > 0) _state.value = _state.value.copy(progress = (read.toFloat() / total).coerceIn(0f, 1f))
                                n = input.read(buffer)
                            }
                        }
                    }
                }
                _state.value = _state.value.copy(phase = UpdatePhase.READY, progress = 1f)
                launchInstaller(file)
            } catch (e: Exception) {
                _state.value = _state.value.copy(phase = UpdatePhase.ERROR, message = e.message)
            }
        }
    }

    private fun launchInstaller(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun getJson(url: String): JSONObject? = try {
        val req = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
        client.newCall(req).execute().use { resp -> if (resp.isSuccessful) resp.body?.string()?.let { JSONObject(it) } else null }
    } catch (e: Exception) {
        null
    }

    companion object {
        const val REPO = "borborborja/micasong"

        /** True when [latest] (e.g. "0.0.15") is a higher version than [current] ("0.0.14-foss"). */
        fun isNewer(latest: String, current: String): Boolean {
            fun parts(v: String) = v.substringBefore("-").trim().split(".").map { it.toIntOrNull() ?: 0 }
            val l = parts(latest)
            val c = parts(current)
            for (i in 0 until maxOf(l.size, c.size)) {
                val lv = l.getOrElse(i) { 0 }
                val cv = c.getOrElse(i) { 0 }
                if (lv != cv) return lv > cv
            }
            return false
        }
    }
}
