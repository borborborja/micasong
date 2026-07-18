package com.micasong.player.data.theme

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A user custom theme (spec §25): named light/dark maps of Material 3 color roles → hex colors.
 * Import/export is compatible with **Material Theme Builder**, whose export nests the schemes under
 * `"schemes": { "light": {...}, "dark": {...} }`; a flat `{ "light": {...}, "dark": {...} }` form is
 * also accepted. The parsing and hex handling are pure and unit-testable; the Compose
 * `ColorScheme` is built from these maps in the UI layer.
 */
@Serializable
data class CustomTheme(
    val name: String = "Custom",
    val light: Map<String, String> = emptyMap(),
    val dark: Map<String, String> = emptyMap(),
) {
    /** ARGB int for a role in the requested scheme, or null when absent/invalid. */
    fun colorFor(role: String, darkScheme: Boolean): Int? =
        (if (darkScheme) dark else light)[role]?.let { parseHexColor(it) }
}

object ThemeJson {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Parse a custom theme from JSON, accepting both the flat and Material-Theme-Builder shapes. */
    fun parse(text: String?): CustomTheme? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            val root = json.parseToJsonElement(text).jsonObject
            val schemes = (root["schemes"] as? JsonObject) ?: root
            val name = root["name"]?.jsonPrimitive?.contentOrNullSafe() ?: "Custom"
            CustomTheme(
                name = name,
                light = schemes.stringMap("light"),
                dark = schemes.stringMap("dark"),
            ).takeIf { it.light.isNotEmpty() || it.dark.isNotEmpty() }
        }.getOrNull()
    }

    fun toJson(theme: CustomTheme): String = json.encodeToString(CustomTheme.serializer(), theme)

    private fun JsonObject.stringMap(key: String): Map<String, String> {
        val obj = this[key] as? JsonObject ?: return emptyMap()
        return obj.mapNotNull { (role, value) ->
            value.jsonPrimitive.contentOrNullSafe()?.let { role to it }
        }.toMap()
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        runCatching { content }.getOrNull()?.takeIf { it.isNotBlank() }
}

/** Parse `#RGB`, `#RRGGBB` or `#AARRGGBB` (with or without `#`) into an ARGB int. */
fun parseHexColor(hex: String): Int? {
    val h = hex.trim().removePrefix("#")
    return when (h.length) {
        3 -> {
            val expanded = buildString { h.forEach { append(it).append(it) } }
            expanded.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
        }
        6 -> h.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
        8 -> h.toLongOrNull(16)?.toInt()
        else -> null
    }
}
