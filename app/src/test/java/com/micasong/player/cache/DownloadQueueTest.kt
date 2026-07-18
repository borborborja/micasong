package com.micasong.player.cache

import com.micasong.player.data.cache.DownloadQueue
import com.micasong.player.data.cache.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueueTest {

    @Test
    fun `enqueue avoids duplicates`() {
        val q = DownloadQueue().enqueue(1).enqueue(1)
        assertEquals(1, q.tasks.size)
    }

    @Test
    fun `next to start respects the concurrency limit`() {
        val q = DownloadQueue(maxConcurrent = 2)
            .enqueue(1, enqueuedAt = 1).enqueue(2, enqueuedAt = 2).enqueue(3, enqueuedAt = 3)
        assertEquals(listOf(1L, 2L), q.nextToStart().map { it.trackId })
    }

    @Test
    fun `priority beats FIFO`() {
        val q = DownloadQueue(maxConcurrent = 1)
            .enqueue(1, priority = 0, enqueuedAt = 1)
            .enqueue(2, priority = 5, enqueuedAt = 2)
        assertEquals(listOf(2L), q.nextToStart().map { it.trackId })
    }

    @Test
    fun `active downloads consume slots`() {
        val q = DownloadQueue(maxConcurrent = 2)
            .enqueue(1, enqueuedAt = 1).enqueue(2, enqueuedAt = 2).enqueue(3, enqueuedAt = 3)
            .markDownloading(1)
        // one slot left → only the next pending (2) should start
        assertEquals(listOf(2L), q.nextToStart().map { it.trackId })
    }

    @Test
    fun `no slots when at capacity`() {
        val q = DownloadQueue(maxConcurrent = 1).enqueue(1).enqueue(2).markDownloading(1)
        assertTrue(q.nextToStart().isEmpty())
    }

    @Test
    fun `state transitions`() {
        var q = DownloadQueue().enqueue(1).markDownloading(1).setProgress(1, 0.5f)
        assertEquals(DownloadState.DOWNLOADING, q.tasks.first().state)
        assertEquals(0.5f, q.tasks.first().progress, 1e-6f)
        q = q.markCompleted(1)
        assertEquals(DownloadState.COMPLETED, q.tasks.first().state)
        assertEquals(1f, q.tasks.first().progress, 1e-6f)
    }

    @Test
    fun `retry requeues a failed task only`() {
        val q = DownloadQueue().enqueue(1).markDownloading(1).markFailed(1).retry(1)
        assertEquals(DownloadState.QUEUED, q.tasks.first().state)
        // retry on a completed task is a no-op
        val q2 = DownloadQueue().enqueue(2).markCompleted(2).retry(2)
        assertEquals(DownloadState.COMPLETED, q2.tasks.first().state)
    }

    @Test
    fun `remove drops the task`() {
        val q = DownloadQueue().enqueue(1).enqueue(2).remove(1)
        assertEquals(listOf(2L), q.tasks.map { it.trackId })
        assertFalse(q.tasks.any { it.trackId == 1L })
    }
}
