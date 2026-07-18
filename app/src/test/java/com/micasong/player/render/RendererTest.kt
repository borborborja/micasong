package com.micasong.player.render

import com.micasong.player.data.render.Renderer
import com.micasong.player.data.render.RendererRegistry
import com.micasong.player.data.render.RendererType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererTest {

    private val local = Renderer("local", "Este dispositivo", RendererType.LOCAL, supportsGapless = true)
    private val cast = Renderer("cc-1", "Salón", RendererType.CHROMECAST)
    private val upnp = Renderer("upnp-1", "Hi-Fi", RendererType.UPNP)

    @Test
    fun `api types match the spec enum`() {
        assertEquals(0, RendererType.LOCAL.apiType)
        assertEquals(1, RendererType.UPNP.apiType)
        assertEquals(3, RendererType.CHROMECAST.apiType)
        assertEquals(6, RendererType.REMOTE_MEDIA_CENTER.apiType)
    }

    @Test
    fun `dsp applies only on local`() {
        assertTrue(RendererType.LOCAL.appliesLocalDsp)
        assertFalse(RendererType.CHROMECAST.appliesLocalDsp)
        assertFalse(RendererType.UPNP.appliesLocalDsp)
    }

    @Test
    fun `registry reports dsp based on active renderer`() {
        val reg = RendererRegistry(listOf(local, cast), activeId = "local")
        assertTrue(reg.dspApplies)
        assertFalse(reg.select("cc-1").dspApplies)
    }

    @Test
    fun `select switches active only for known ids`() {
        val reg = RendererRegistry(listOf(local, cast), activeId = "local")
        assertEquals("cc-1", reg.select("cc-1").activeId)
        assertEquals("local", reg.select("ghost").activeId)   // unknown → unchanged
    }

    @Test
    fun `active falls back to local when id unknown`() {
        val reg = RendererRegistry(listOf(local, cast), activeId = "stale")
        assertEquals("local", reg.active?.id)
    }

    @Test
    fun `automatic reset when active renderer disappears`() {
        val reg = RendererRegistry(listOf(local, cast), activeId = "cc-1")
        val updated = reg.updateAvailable(listOf(local))   // Chromecast gone
        assertEquals("local", updated.activeId)
    }

    @Test
    fun `active preserved when still present after update`() {
        val reg = RendererRegistry(listOf(local, cast), activeId = "cc-1")
        val updated = reg.updateAvailable(listOf(local, cast, upnp))
        assertEquals("cc-1", updated.activeId)
    }

    @Test
    fun `select by api type resolves renderer`() {
        val reg = RendererRegistry(listOf(local, cast, upnp), activeId = "local")
        assertEquals("cc-1", reg.selectByApiType(3).activeId)      // Chromecast
        assertEquals("upnp-1", reg.selectByApiType(1).activeId)    // UPnP
        assertEquals("local", reg.selectByApiType(99).activeId)    // no match → unchanged
    }

    @Test
    fun `remote volume flag`() {
        assertTrue(RendererType.SONOS.hasRemoteVolume)
        assertTrue(RendererType.CHROMECAST.hasRemoteVolume)
        assertFalse(RendererType.LOCAL.hasRemoteVolume)
    }

    @Test
    fun `local-only factory`() {
        val reg = RendererRegistry.withLocalOnly()
        assertEquals(1, reg.available.size)
        assertTrue(reg.dspApplies)
    }
}
