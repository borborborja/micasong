package com.micasong.player.data.smart

import com.micasong.player.data.model.OfflineState
import com.micasong.player.data.model.Track
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The transversal smart-filter engine (spec §30). A filter is a tree of [FilterNode]s: leaf
 * [FilterNode.Rule]s (target + operator + value) grouped by nestable [FilterNode.Group]s that
 * combine their children with ALL (AND) or ANY (OR). The same tree powers library filtering,
 * offline filters and smart playlists (§31). Everything is @Serializable so a smart playlist
 * can be persisted as JSON on its row.
 */

/** What a rule inspects (a representative subset of the documented targets, spec §30). */
enum class FilterTarget {
    TITLE, ARTIST, ALBUM, GENRE, YEAR, RATING, DURATION_MS, PLAY_COUNT, SKIP_COUNT,
    LAST_PLAYED, FAVORITE, IS_AUDIOBOOK, BPM, AVAILABLE_OFFLINE, THUMBNAIL,
}

/** Operators; the applicable set depends on the target's data type. */
enum class FilterOperator {
    EQUALS, NOT_EQUALS, CONTAINS, NOT_CONTAINS,
    GREATER, LESS, GREATER_EQUALS, LESS_EQUALS,
    IS_PRESENT, IS_MISSING,   // "Is present / Is missing" (spec §30)
}

/** Match mode for a group of rules. */
enum class MatchMode { ALL, ANY }

@Serializable
sealed interface FilterNode {
    @Serializable
    @SerialName("rule")
    data class Rule(
        val target: FilterTarget,
        val operator: FilterOperator,
        val value: String = "",
    ) : FilterNode

    @Serializable
    @SerialName("group")
    data class Group(
        val match: MatchMode = MatchMode.ALL,
        val children: List<FilterNode> = emptyList(),
    ) : FilterNode
}

/** Evaluates a filter tree against a single [Track]. Pure and side-effect free. */
object FilterEngine {

    fun evaluate(node: FilterNode, track: Track): Boolean = when (node) {
        is FilterNode.Group -> when (node.match) {
            MatchMode.ALL -> node.children.all { evaluate(it, track) }
            MatchMode.ANY -> node.children.isEmpty() || node.children.any { evaluate(it, track) }
        }
        is FilterNode.Rule -> evaluateRule(node, track)
    }

    private fun evaluateRule(rule: FilterNode.Rule, track: Track): Boolean {
        val op = rule.operator
        return when (rule.target) {
            FilterTarget.TITLE -> stringOp(track.title, op, rule.value)
            FilterTarget.ARTIST -> stringOp(track.artistName, op, rule.value)
            FilterTarget.ALBUM -> stringOp(track.albumName, op, rule.value)
            FilterTarget.GENRE -> stringOp(track.genre, op, rule.value)
            FilterTarget.YEAR -> numberOp(track.year?.toLong(), op, rule.value)
            FilterTarget.RATING -> numberOp(track.userRating.toLong(), op, rule.value)
            FilterTarget.DURATION_MS -> numberOp(track.durationMs, op, rule.value)
            FilterTarget.PLAY_COUNT -> numberOp(track.playCount.toLong(), op, rule.value)
            FilterTarget.SKIP_COUNT -> numberOp(track.skipCount.toLong(), op, rule.value)
            FilterTarget.LAST_PLAYED -> numberOp(track.lastPlayed, op, rule.value)
            FilterTarget.BPM -> numberOp(null, op, rule.value) // BPM not modelled locally yet
            FilterTarget.FAVORITE -> boolOp(track.isFavorite, op, rule.value)
            FilterTarget.IS_AUDIOBOOK -> boolOp(track.isAudiobook, op, rule.value)
            FilterTarget.AVAILABLE_OFFLINE -> boolOp(track.offlineState != OfflineState.NONE, op, rule.value)
            FilterTarget.THUMBNAIL -> presenceOp(track.artworkUri, op)
        }
    }

    private fun stringOp(field: String?, op: FilterOperator, value: String): Boolean {
        val f = field?.lowercase().orEmpty()
        val v = value.lowercase()
        return when (op) {
            FilterOperator.EQUALS -> f == v
            FilterOperator.NOT_EQUALS -> f != v
            FilterOperator.CONTAINS -> f.contains(v)
            FilterOperator.NOT_CONTAINS -> !f.contains(v)
            FilterOperator.IS_PRESENT -> !field.isNullOrBlank()
            FilterOperator.IS_MISSING -> field.isNullOrBlank()
            else -> false
        }
    }

    private fun numberOp(field: Long?, op: FilterOperator, value: String): Boolean {
        if (op == FilterOperator.IS_PRESENT) return field != null
        if (op == FilterOperator.IS_MISSING) return field == null
        val f = field ?: return false
        val v = value.toLongOrNull() ?: return false
        return when (op) {
            FilterOperator.EQUALS -> f == v
            FilterOperator.NOT_EQUALS -> f != v
            FilterOperator.GREATER -> f > v
            FilterOperator.LESS -> f < v
            FilterOperator.GREATER_EQUALS -> f >= v
            FilterOperator.LESS_EQUALS -> f <= v
            else -> false
        }
    }

    private fun boolOp(field: Boolean, op: FilterOperator, value: String): Boolean {
        val v = value.equals("true", ignoreCase = true) || value == "1"
        return when (op) {
            FilterOperator.EQUALS -> field == v
            FilterOperator.NOT_EQUALS -> field != v
            else -> false
        }
    }

    private fun presenceOp(field: String?, op: FilterOperator): Boolean = when (op) {
        FilterOperator.IS_PRESENT -> !field.isNullOrBlank()
        FilterOperator.IS_MISSING -> field.isNullOrBlank()
        else -> false
    }
}
