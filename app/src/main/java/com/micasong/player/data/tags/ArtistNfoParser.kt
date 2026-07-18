package com.micasong.player.data.tags

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/** A thumbnail reference from an artist.nfo, tagged by Kodi's `aspect` (thumb / fanart). */
data class NfoThumb(val aspect: String, val url: String)

/** Parsed contents of a Kodi-compatible `artist.nfo` (spec §8). */
data class ArtistNfo(
    val name: String? = null,
    val sortName: String? = null,
    val musicBrainzId: String? = null,
    val type: String? = null,
    val gender: String? = null,
    val biography: String? = null,
    val genres: List<String> = emptyList(),
    val styles: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val instruments: List<String> = emptyList(),
    val thumbs: List<NfoThumb> = emptyList(),
) {
    fun thumbUrl(aspect: String): String? = thumbs.firstOrNull { it.aspect.equals(aspect, true) }?.url
}

/**
 * Parser for the Artist Information Folder (spec §8): `ArtistInfoFolder/ArtistName/artist.nfo`
 * plus `fanart.*` / `thumb.*` images. Reads the NFO XML into [ArtistNfo], which the sync engine
 * matches to library artists by name + MusicBrainz ID (or name alone). Uses JAXP DOM so it is
 * unit-testable on the JVM, with external entities disabled to prevent XXE.
 */
object ArtistNfoParser {

    fun parse(xml: String?): ArtistNfo? {
        if (xml.isNullOrBlank()) return null
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                isExpandEntityReferences = false
            }
            val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            val root = doc.documentElement ?: return null

            ArtistNfo(
                name = root.firstText("name"),
                sortName = root.firstText("sortname"),
                musicBrainzId = root.firstText("musicBrainzArtistID", "musicbrainzartistid"),
                type = root.firstText("type"),
                gender = root.firstText("gender"),
                biography = root.firstText("biography"),
                genres = root.allText("genre"),
                styles = root.allText("style"),
                moods = root.allText("mood"),
                tags = root.allText("tag"),
                instruments = root.allText("instrument"),
                thumbs = root.thumbs(),
            )
        }.getOrNull()
    }

    private fun Element.firstText(vararg tags: String): String? =
        tags.firstNotNullOfOrNull { tag ->
            val nodes = getElementsByTagName(tag)
            (0 until nodes.length).firstNotNullOfOrNull { i ->
                nodes.item(i).textContent?.trim()?.takeIf { it.isNotEmpty() }
            }
        }

    private fun Element.allText(tag: String): List<String> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).mapNotNull { nodes.item(it).textContent?.trim()?.takeIf { t -> t.isNotEmpty() } }
    }

    private fun Element.thumbs(): List<NfoThumb> {
        val nodes = getElementsByTagName("thumb")
        return (0 until nodes.length).mapNotNull { i ->
            val el = nodes.item(i) as? Element ?: return@mapNotNull null
            val url = el.textContent?.trim().orEmpty()
            if (url.isEmpty()) null
            else NfoThumb(aspect = el.getAttribute("aspect").ifBlank { "thumb" }, url = url)
        }
    }
}
