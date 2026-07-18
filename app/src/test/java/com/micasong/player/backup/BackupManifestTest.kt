package com.micasong.player.backup

import com.micasong.player.data.backup.BackupContent
import com.micasong.player.data.backup.BackupManifest
import com.micasong.player.data.backup.BackupSelection
import com.micasong.player.data.backup.BackupValidation
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManifestTest {

    @Test
    fun `api flags round trip`() {
        val selection = setOf(BackupContent.SETTINGS, BackupContent.PROVIDERS, BackupContent.PLAYLISTS)
        val flags = BackupSelection.toApiFlags(selection)
        assertTrue(flags["SETTINGS"]!!)
        assertTrue(flags["PROVIDERS"]!!)
        assertFalse(flags["TAG_CACHE"]!!)
        assertEquals(selection, BackupSelection.fromApiFlags(flags))
    }

    @Test
    fun `from api flags ignores absent and false keys`() {
        val selection = BackupSelection.fromApiFlags(mapOf("PLAYLISTS" to true, "SETTINGS" to false))
        assertEquals(setOf(BackupContent.PLAYLISTS), selection)
    }

    @Test
    fun `manifest reports secrets when providers included`() {
        val withProviders = BackupManifest(appVersion = "1.0", createdAtEpochMs = 0, contents = setOf("PROVIDERS"))
        val withoutProviders = BackupManifest(appVersion = "1.0", createdAtEpochMs = 0, contents = setOf("PLAYLISTS"))
        assertTrue(withProviders.containsSecrets)
        assertFalse(withoutProviders.containsSecrets)
    }

    @Test
    fun `validation requires password and content`() {
        assertTrue(BackupSelection.validate(BackupSelection.DEFAULT, "secret") is BackupValidation.Ok)
        assertTrue(BackupSelection.validate(emptySet(), "secret") is BackupValidation.Invalid)
        assertTrue(BackupSelection.validate(BackupSelection.DEFAULT, "") is BackupValidation.Invalid)
        assertTrue(BackupSelection.validate(BackupSelection.DEFAULT, null) is BackupValidation.Invalid)
    }

    @Test
    fun `default selection excludes heavy caches`() {
        assertFalse(BackupContent.TAG_CACHE in BackupSelection.DEFAULT)
        assertFalse(BackupContent.CUSTOM_IMAGES in BackupSelection.DEFAULT)
        assertTrue(BackupContent.SETTINGS in BackupSelection.DEFAULT)
    }

    @Test
    fun `manifest json round trip`() {
        val manifest = BackupManifest(
            appVersion = "0.1.0",
            createdAtEpochMs = 1_700_000_000_000,
            contents = setOf("SETTINGS", "PLAYLISTS"),
        )
        val json = Json.encodeToString(BackupManifest.serializer(), manifest)
        val restored = Json.decodeFromString(BackupManifest.serializer(), json)
        assertEquals(manifest, restored)
        assertEquals("micabkp", BackupManifest.EXTENSION)
    }
}
