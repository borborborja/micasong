package com.micasong.player.data.cache

/** Lifecycle of an offline download (spec §35). */
enum class DownloadState { QUEUED, DOWNLOADING, COMPLETED, FAILED }

data class DownloadTask(
    val trackId: Long,
    val state: DownloadState = DownloadState.QUEUED,
    val priority: Int = 0,          // higher runs sooner
    val progress: Float = 0f,       // 0f..1f
    val enqueuedAt: Long = 0L,      // tie-breaker (FIFO within a priority)
)

/**
 * Immutable offline download queue (spec §35). Enforces a max number of concurrent downloads and
 * orders pending work by priority (then FIFO). Pure and unit-testable; the actual byte transfer is
 * driven by a worker that reacts to [nextToStart].
 */
data class DownloadQueue(
    val tasks: List<DownloadTask> = emptyList(),
    val maxConcurrent: Int = 3,
) {
    val active: List<DownloadTask> get() = tasks.filter { it.state == DownloadState.DOWNLOADING }
    val pending: List<DownloadTask> get() = tasks.filter { it.state == DownloadState.QUEUED }

    /** Queued tasks that should start now, respecting the concurrency limit and priority order. */
    fun nextToStart(): List<DownloadTask> {
        val slots = (maxConcurrent - active.size).coerceAtLeast(0)
        if (slots == 0) return emptyList()
        return pending
            .sortedWith(compareByDescending<DownloadTask> { it.priority }.thenBy { it.enqueuedAt })
            .take(slots)
    }

    fun enqueue(trackId: Long, priority: Int = 0, enqueuedAt: Long = 0L): DownloadQueue {
        if (tasks.any { it.trackId == trackId }) return this   // no duplicates
        return copy(tasks = tasks + DownloadTask(trackId, DownloadState.QUEUED, priority, 0f, enqueuedAt))
    }

    fun markDownloading(trackId: Long): DownloadQueue = update(trackId) { it.copy(state = DownloadState.DOWNLOADING) }
    fun markCompleted(trackId: Long): DownloadQueue = update(trackId) { it.copy(state = DownloadState.COMPLETED, progress = 1f) }
    fun markFailed(trackId: Long): DownloadQueue = update(trackId) { it.copy(state = DownloadState.FAILED) }
    fun setProgress(trackId: Long, progress: Float): DownloadQueue = update(trackId) { it.copy(progress = progress.coerceIn(0f, 1f)) }

    fun remove(trackId: Long): DownloadQueue = copy(tasks = tasks.filterNot { it.trackId == trackId })

    /** Requeue a failed task for a retry. */
    fun retry(trackId: Long): DownloadQueue = update(trackId) {
        if (it.state == DownloadState.FAILED) it.copy(state = DownloadState.QUEUED, progress = 0f) else it
    }

    private fun update(trackId: Long, transform: (DownloadTask) -> DownloadTask): DownloadQueue =
        copy(tasks = tasks.map { if (it.trackId == trackId) transform(it) else it })
}
