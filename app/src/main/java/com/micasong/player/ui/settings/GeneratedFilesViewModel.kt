package com.micasong.player.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micasong.player.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class MaintenanceUsage(
    val offlineCount: Int = 0,
    val offlineBytes: Long = 0,
    val lyricsCount: Int = 0,
    val imageCacheBytes: Long = 0,
)

/** Backs the "Administrar archivos generados" screen (spec §44): report cache usage and clear it. */
@HiltViewModel
class GeneratedFilesViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: MediaRepository,
) : ViewModel() {

    private val _usage = MutableStateFlow(MaintenanceUsage())
    val usage: StateFlow<MaintenanceUsage> = _usage.asStateFlow()

    private val lyricsDir get() = File(context.filesDir, "lyrics")
    private val imageCacheDir get() = File(context.cacheDir, "image_cache")

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val (count, bytes) = repository.offlineUsage()
        val lyrics = withContext(Dispatchers.IO) { lyricsDir.listFiles()?.size ?: 0 }
        val images = withContext(Dispatchers.IO) { dirSize(imageCacheDir) }
        _usage.value = MaintenanceUsage(count, bytes, lyrics, images)
    }

    fun clearDownloads() = viewModelScope.launch { repository.clearAllDownloads(); refresh() }

    fun clearLyrics() = viewModelScope.launch {
        withContext(Dispatchers.IO) { lyricsDir.listFiles()?.forEach { it.delete() } }
        refresh()
    }

    fun clearImageCache() = viewModelScope.launch {
        withContext(Dispatchers.IO) { imageCacheDir.deleteRecursively() }
        refresh()
    }

    private fun dirSize(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
