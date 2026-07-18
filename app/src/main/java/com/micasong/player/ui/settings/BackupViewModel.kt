package com.micasong.player.ui.settings

import androidx.lifecycle.ViewModel
import com.micasong.player.BuildConfig
import com.micasong.player.data.backup.BackupContent
import com.micasong.player.data.backup.BackupManager
import com.micasong.player.data.backup.RestoreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Drives the backup/restore screen (spec §43): holds the content selection and hands byte payloads
 * to/from [BackupManager]. File I/O (SAF) stays in the composable; this only does crypto + DB work.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
) : ViewModel() {

    private val _selection = MutableStateFlow(SUPPORTED.toSet())
    val selection: StateFlow<Set<BackupContent>> = _selection.asStateFlow()

    fun toggle(content: BackupContent) {
        _selection.value = _selection.value.toMutableSet().apply {
            if (contains(content)) remove(content) else add(content)
        }
    }

    /** Build the encrypted archive bytes for the current selection (spec §43). */
    suspend fun buildArchive(password: String): ByteArray = backupManager.createBackup(
        selection = _selection.value,
        password = password,
        appVersion = BuildConfig.VERSION_NAME,
        createdAtMs = System.currentTimeMillis(),
    )

    suspend fun restore(bytes: ByteArray, password: String): RestoreResult =
        backupManager.restoreBackup(bytes, password)

    companion object {
        /** Content types the engine currently gathers and restores (spec §43). */
        val SUPPORTED = listOf(
            BackupContent.SETTINGS,
            BackupContent.PROVIDERS,
            BackupContent.PLAYLISTS,
        )
    }
}
