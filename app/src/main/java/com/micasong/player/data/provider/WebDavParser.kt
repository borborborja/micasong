package com.micasong.player.data.provider

import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/** One entry from a WebDAV PROPFIND multistatus response. */
data class WebDavEntry(val href: String, val contentType: String?, val isCollection: Boolean, val contentLength: Long?)

/**
 * Pure parser for WebDAV PROPFIND `multistatus` XML (spec §46). Extracts each `<d:response>`'s
 * href, content type, size and whether it's a collection (folder). Namespace-aware so it tolerates
 * any prefix (`d:`, `D:`, none). Testable without a live server.
 */
object WebDavParser {

    fun parse(xml: String): List<WebDavEntry> {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val responses = doc.getElementsByTagNameNS(DAV, "response")
        val out = ArrayList<WebDavEntry>(responses.length)
        for (i in 0 until responses.length) {
            val resp = responses.item(i) as? org.w3c.dom.Element ?: continue
            val href = resp.firstText(DAV, "href") ?: continue
            val contentType = resp.firstText(DAV, "getcontenttype")
            val lengthText = resp.firstText(DAV, "getcontentlength")
            val isCollection = resp.getElementsByTagNameNS(DAV, "collection").length > 0
            out += WebDavEntry(href, contentType?.ifBlank { null }, isCollection, lengthText?.toLongOrNull())
        }
        return out
    }

    private fun org.w3c.dom.Element.firstText(ns: String, local: String): String? =
        getElementsByTagNameNS(ns, local).item(0)?.textContent?.trim()

    private const val DAV = "DAV:"

    /** Whether an entry looks like a playable audio file (by content type or extension). */
    fun isAudio(entry: WebDavEntry): Boolean {
        if (entry.isCollection) return false
        if (entry.contentType?.startsWith("audio/") == true) return true
        val lower = entry.href.substringAfterLast('.', "").lowercase()
        return lower in AUDIO_EXTENSIONS
    }

    private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "aif", "aiff", "ape", "wv", "mpc")
}
