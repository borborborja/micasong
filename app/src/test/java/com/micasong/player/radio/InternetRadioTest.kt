package com.micasong.player.radio

import com.micasong.player.data.radio.InternetRadio
import com.micasong.player.data.radio.RadioStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class InternetRadioTest {

    private val stations = listOf(
        RadioStation(1, "One", "http://a"),
        RadioStation(2, "Two", "http://b"),
        RadioStation(3, "Three", "http://c"),
        RadioStation(4, "Four", "http://d"),
    )

    @Test
    fun `clicking a station queues all rotated to start there`() {
        val queue = InternetRadio.orderedQueue(stations, clickedId = 3)
        assertEquals(listOf(3L, 4L, 1L, 2L), queue.map { it.id })
    }

    @Test
    fun `clicking the first station keeps original order`() {
        assertEquals(stations.map { it.id }, InternetRadio.orderedQueue(stations, clickedId = 1).map { it.id })
    }

    @Test
    fun `unknown clicked id keeps original order`() {
        assertEquals(stations.map { it.id }, InternetRadio.orderedQueue(stations, clickedId = 99).map { it.id })
    }

    @Test
    fun `extracts embedded basic auth`() {
        val auth = InternetRadio.extractBasicAuth("http://user:pass@radio.example.com:8000/stream")
        assertEquals("http://radio.example.com:8000/stream", auth.url)
        assertEquals("user", auth.username)
        assertEquals("pass", auth.password)
        assertTrue(auth.hasAuth)
    }

    @Test
    fun `url without credentials is unchanged`() {
        val auth = InternetRadio.extractBasicAuth("https://radio.example.com/stream")
        assertEquals("https://radio.example.com/stream", auth.url)
        assertNull(auth.username)
        assertFalse(auth.hasAuth)
    }

    @Test
    fun `random pls entry is deterministic for a seed`() {
        val pls = """
            [playlist]
            File1=http://a/stream1
            File2=http://a/stream2
            File3=http://a/stream3
        """.trimIndent()
        val a = InternetRadio.randomEntry(pls, Random(5))
        val b = InternetRadio.randomEntry(pls, Random(5))
        assertEquals(a, b)
        assertTrue(a in listOf("http://a/stream1", "http://a/stream2", "http://a/stream3"))
    }

    @Test
    fun `empty pls yields null`() {
        assertNull(InternetRadio.randomEntry("[playlist]\n", Random(1)))
    }
}
