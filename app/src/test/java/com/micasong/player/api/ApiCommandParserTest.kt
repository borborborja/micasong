package com.micasong.player.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiCommandParserTest {

    private fun parse(action: String, extras: Map<String, Any?> = emptyMap()) =
        ApiCommandParser.parse("com.micasong.api.$action", MapExtras(extras))

    @Test
    fun `media command with int parameter`() {
        val cmd = parse("MEDIA_COMMAND", mapOf("COMMAND" to "seek", "INT_PARAMETER" to 30))
        assertEquals(ApiCommand.MediaControl("seek", 30), cmd)
    }

    @Test
    fun `media command without parameter`() {
        assertEquals(ApiCommand.MediaControl("next", null), parse("MEDIA_COMMAND", mapOf("COMMAND" to "next")))
    }

    @Test
    fun `select renderer parses type and identifier`() {
        val cmd = parse("SELECT_RENDERER", mapOf("TYPE" to 3, "IDENTIFIER" to "cc-1"))
        assertEquals(ApiCommand.SelectRenderer(3, "cc-1"), cmd)
    }

    @Test
    fun `select renderer defaults type to local`() {
        assertEquals(ApiCommand.SelectRenderer(0, null), parse("SELECT_RENDERER"))
    }

    @Test
    fun `media start parses all fields`() {
        val cmd = parse(
            "MEDIA_START",
            mapOf("MEDIA_TYPE" to "album", "ALBUM" to "Viva La Vida", "SHUFFLE" to true, "QUEUE" to 2),
        )
        val start = cmd as ApiCommand.MediaStart
        assertEquals("album", start.mediaType)
        assertEquals("Viva La Vida", start.album)
        assertTrue(start.shuffle)
        assertEquals(2, start.queue)
    }

    @Test
    fun `media sync parses provider id`() {
        assertEquals(ApiCommand.MediaSync(5), parse("MEDIA_SYNC", mapOf("PROVIDER_ID" to 5)))
    }

    @Test
    fun `custom action force provider connection`() {
        val cmd = parse("CUSTOM_ACTION", mapOf("ACTION" to "force_provider_connection", "PROVIDER_ID" to 1001, "ACTIVE_CONNECTION" to 2))
        assertEquals(ApiCommand.CustomAction("force_provider_connection", 1001, 2), cmd)
    }

    @Test
    fun `change settings parses setting and value`() {
        assertEquals(
            ApiCommand.ChangeSetting("wifi_transcode", 3),
            parse("CHANGE_SETTINGS", mapOf("SETTING" to "wifi_transcode", "INT_PARAMETER" to 3)),
        )
    }

    @Test
    fun `playlist import parses provider and type`() {
        assertEquals(ApiCommand.PlaylistImport(1001, 2), parse("PLAYLIST_IMPORT", mapOf("PROVIDER_ID" to 1001, "PLAYLIST_TYPE" to 2)))
    }

    @Test
    fun `missing required extras yield unknown`() {
        assertEquals(ApiCommand.Unknown, parse("MEDIA_COMMAND"))       // no COMMAND
        assertEquals(ApiCommand.Unknown, parse("MEDIA_START"))         // no MEDIA_TYPE
        assertEquals(ApiCommand.Unknown, ApiCommandParser.parse("com.other.ACTION", MapExtras(emptyMap())))
    }
}
