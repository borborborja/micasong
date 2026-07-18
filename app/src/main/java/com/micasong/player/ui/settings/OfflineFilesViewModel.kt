package com.micasong.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micasong.player.data.cache.CacheTier
import com.micasong.player.data.cache.DownloadState
import com.micasong.player.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Row shown in the offline-files manager (spec §35). */
data class OfflineRow(
    val trackId: Long,
    val title: String,
    val subtitle: String,
    val state: DownloadState,
    val progress: Float,
    val tier: CacheTier,
)

@HiltViewModel
class OfflineFilesViewModel @Inject constructor(
    private val repository: MediaRepository,
) : ViewModel() {

    val rows: StateFlow<List<OfflineRow>> =
        combine(repository.downloads, repository.allTracks) { downloads, tracks ->
            val byId = tracks.associateBy { it.id }
            downloads.map { d ->
                val t = byId[d.trackId]
                OfflineRow(
                    trackId = d.trackId,
                    title = t?.title ?: "#${d.trackId}",
                    subtitle = t?.artistName.orEmpty(),
                    state = DownloadState.entries.getOrElse(d.state) { DownloadState.QUEUED },
                    progress = d.progress,
                    tier = CacheTier.entries.getOrElse(d.tier) { CacheTier.ROLLING },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun remove(trackId: Long) = viewModelScope.launch { repository.removeDownload(trackId) }
    fun retry(trackId: Long) = viewModelScope.launch { repository.retryDownload(trackId) }
    fun setTier(trackId: Long, tier: CacheTier) = viewModelScope.launch { repository.setDownloadTier(trackId, tier) }
}
