package com.micasong.player.queue

import com.micasong.player.data.queue.QueueBook
import com.micasong.player.data.queue.QueueMediaType
import com.micasong.player.data.queue.SavedQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueBookTest {

    private fun q(id: Long, name: String = "Q$id", type: QueueMediaType = QueueMediaType.MUSIC) =
        SavedQueue(id = id, name = name, trackIds = listOf(id * 10, id * 10 + 1), mediaType = type)

    @Test
    fun `add makes queue active`() {
        val book = QueueBook().add(q(1)).add(q(2))
        assertEquals(2, book.queues.size)
        assertEquals(2L, book.activeId)
        assertEquals("Q2", book.active?.name)
    }

    @Test
    fun `switch changes active queue only if it exists`() {
        val book = QueueBook().add(q(1)).add(q(2)).switchTo(1)
        assertEquals(1L, book.activeId)
        assertEquals(1L, book.switchTo(99).activeId)   // unknown id is a no-op
    }

    @Test
    fun `capacity evicts the oldest inactive queue`() {
        var book = QueueBook()
        for (i in 1..QueueBook.MAX_QUEUES) book = book.add(q(i.toLong()))
        assertTrue(book.isFull)
        // Active is the last added (id 15). Adding a 16th evicts the oldest inactive (id 1).
        book = book.add(q(100))
        assertEquals(QueueBook.MAX_QUEUES, book.queues.size)
        assertFalse(book.queues.any { it.id == 1L })
        assertTrue(book.queues.any { it.id == 100L })
        assertEquals(100L, book.activeId)
    }

    @Test
    fun `music and audiobook queues coexist`() {
        val book = QueueBook()
            .add(q(1, type = QueueMediaType.MUSIC))
            .add(q(2, type = QueueMediaType.AUDIOBOOK))
        assertEquals(QueueMediaType.MUSIC, book.queues.first { it.id == 1L }.mediaType)
        assertEquals(QueueMediaType.AUDIOBOOK, book.queues.first { it.id == 2L }.mediaType)
    }

    @Test
    fun `remove active falls back to another queue`() {
        val book = QueueBook().add(q(1)).add(q(2)).remove(2)
        assertEquals(1, book.queues.size)
        assertEquals(1L, book.activeId)
    }

    @Test
    fun `remove last queue clears active`() {
        val book = QueueBook().add(q(1)).remove(1)
        assertTrue(book.queues.isEmpty())
        assertNull(book.activeId)
    }

    @Test
    fun `update active mutates only the active queue state`() {
        val book = QueueBook().add(q(1)).add(q(2))
            .updateActive { it.copy(positionMs = 5000, shuffle = true, repeatMode = 1) }
        assertEquals(5000L, book.active?.positionMs)
        assertTrue(book.active?.shuffle == true)
        assertEquals(0L, book.queues.first { it.id == 1L }.positionMs)   // untouched
    }

    @Test
    fun `re-adding same id replaces without growing`() {
        val book = QueueBook().add(q(1)).add(q(1, name = "Renamed"))
        assertEquals(1, book.queues.size)
        assertEquals("Renamed", book.active?.name)
    }
}
