package com.micasong.player.wear

import com.micasong.player.data.wear.WearConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearConfigTest {

    @Test
    fun `sensible defaults`() {
        val c = WearConfig()
        assertEquals(128, c.downloadQualityKbps)
        assertTrue(c.wifiOnlyDownloads)
        assertTrue(c.tileEnabled)
        assertTrue(c.rotaryVolume)
    }

    @Test
    fun `json round trip`() {
        val c = WearConfig(downloadQualityKbps = 320, secretMode = true, wifiOnlyDownloads = false)
        assertEquals(c, WearConfig.fromJson(WearConfig.toJson(c)))
    }

    @Test
    fun `invalid json falls back to defaults`() {
        assertEquals(WearConfig(), WearConfig.fromJson("not json"))
        assertEquals(WearConfig(), WearConfig.fromJson(null))
    }
}
