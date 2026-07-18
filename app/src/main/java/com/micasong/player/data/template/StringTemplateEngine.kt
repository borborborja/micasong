package com.micasong.player.data.template

/**
 * String-template engine (spec §27). Renders user templates against a field map, supporting:
 *  - `%field%` placeholders,
 *  - `{ ... }` conditional blocks — emitted only if their first placeholder is non-empty,
 *  - `%lb%` and `\n` line breaks,
 *  - inline formatting markers `^^CAPS^^` (upper-cased) and `**bold** __underline__ ~~strike~~
 *    //italics//` (markers stripped for plain-text output).
 *
 * Nested blocks are resolved innermost-first. The full `%field|condition%` operator grammar
 * (`==`, `>`, `&&`, `||`, …) is a documented extension point; the core visibility model — a
 * block hides when its data is missing — is implemented and tested here.
 */
object StringTemplateEngine {

    private val PLACEHOLDER = Regex("%([a-zA-Z0-9_.]+)%")
    private val INNERMOST_BLOCK = Regex("\\{([^{}]*)\\}")
    private val CAPS = Regex("\\^\\^(.+?)\\^\\^")

    fun render(template: String, fields: Map<String, String?>): String {
        var text = template.replace("%lb%", "\n")
        text = resolveBlocks(text, fields)
        text = substitute(text, fields)
        text = applyFormatting(text)
        return text.trim()
    }

    /** Repeatedly collapse the innermost `{...}` blocks until none remain. */
    private fun resolveBlocks(input: String, fields: Map<String, String?>): String {
        var text = input
        while (true) {
            val match = INNERMOST_BLOCK.find(text) ?: break
            val inner = match.groupValues[1]
            val replacement = if (blockVisible(inner, fields)) substitute(inner, fields) else ""
            text = text.replaceRange(match.range, replacement)
        }
        return text
    }

    /** A block shows when its first referenced placeholder resolves to a non-empty value. */
    private fun blockVisible(block: String, fields: Map<String, String?>): Boolean {
        val first = PLACEHOLDER.find(block) ?: return block.isNotBlank()
        val key = first.groupValues[1]
        return !resolve(key, fields).isNullOrEmpty()
    }

    private fun substitute(input: String, fields: Map<String, String?>): String =
        PLACEHOLDER.replace(input) { m -> resolve(m.groupValues[1], fields).orEmpty() }

    private fun resolve(key: String, fields: Map<String, String?>): String? =
        if (key.startsWith("string.")) localizedLabel(key) else fields[key]

    /** Localized `string.*` labels (spec §27). A small built-in set; extendable per locale. */
    private fun localizedLabel(key: String): String = when (key) {
        "string.hires" -> "Hi-Res"
        "string.lossless" -> "Lossless"
        "string.lossy" -> "Lossy"
        else -> ""
    }

    private fun applyFormatting(input: String): String {
        var text = CAPS.replace(input) { it.groupValues[1].uppercase() }
        // Plain-text output: strip the remaining rich-text markers.
        for (marker in listOf("**", "__", "~~", "//")) {
            text = text.replace(marker, "")
        }
        return text
    }
}
