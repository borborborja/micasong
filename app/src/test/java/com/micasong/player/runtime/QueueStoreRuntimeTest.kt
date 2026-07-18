package com.micasong.player.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.micasong.player.data.queue.QueueStore
import com.micasong.player.data.queue.SavedQueue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verifies multiple-queue persistence (spec §16): save, switch active, cap eviction, remove. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueStoreRuntimeTest {

    private val store = QueueStore(ApplicationProvider.getApplicationContext<Context>())

    private fun q(id: Long, name: String) = SavedQueue(id = id, name = name, trackIds = listOf(id, id + 1), currentIndex = 1, positionMs = 5000)

    @Test
    fun `queues persist, switch active and survive a reload`() = runBlocking {
        store.update { it.add(q(1, "Música")) }
        store.update { it.add(q(2, "Audiolibro")) }

        var book = store.queueBook.first()
        assertEquals(listOf("Música", "Audiolibro"), book.queues.map { it.name })
        assertEquals(2L, book.activeId) // most recently added is active
        assertEquals(5000L, book.active!!.positionMs)

        store.update { it.switchTo(1) }
        assertEquals(1L, store.current().activeId)

        store.update { it.remove(1) }
        book = store.queueBook.first()
        assertEquals(listOf("Audiolibro"), book.queues.map { it.name })
        assertTrue(book.queues.none { it.id == 1L })
    }
}
