package com.micasong.player.data.smart

import com.micasong.player.data.model.Track
import kotlin.random.Random

/**
 * Smart Flow modes (spec §16). Modes marked *(sonic)* normally require the provider's sonic
 * analysis (Plex Transition Maestro / Echo Match / Steady Vibes). Without it MiCaSong falls back
 * to genre/era similarity, which the UI can hide behind the "requires sonic analysis" flag.
 */
enum class SmartFlowMode(val requiresSonicAnalysis: Boolean) {
    SHUFFLE_SPECIALIST(false),
    TRANSITION_MAESTRO(true),
    DOUBLE_SHOT(false),
    ARTIST_FAN(false),
    ECHO_MATCH(true),
    ERA_ENTHUSIAST(false),
    STEADY_VIBES(true),
}

/**
 * Smart Flow (spec §16): edits the queue in real time by inserting tracks after the current one.
 * Returns the tracks to insert (bounded by [maxInsertions], default 12 per spec). Pure and
 * deterministic for a fixed [random], so it is unit-testable.
 */
object SmartFlow {

    const val DEFAULT_MAX_INSERTIONS = 12

    fun nextInsertions(
        currentTrack: Track,
        library: List<Track>,
        mode: SmartFlowMode,
        maxInsertions: Int = DEFAULT_MAX_INSERTIONS,
        excludeIds: Set<Long> = setOf(currentTrack.id),
        random: Random = Random.Default,
    ): List<Track> {
        val n = maxInsertions.coerceAtLeast(0)
        if (n == 0) return emptyList()
        val pool = library.filter { it.id !in excludeIds && !it.excludedFromMixes }
        if (pool.isEmpty()) return emptyList()

        return when (mode) {
            SmartFlowMode.SHUFFLE_SPECIALIST ->
                pool.shuffled(random).take(1)

            SmartFlowMode.DOUBLE_SHOT ->
                pool.filter { it.artistId == currentTrack.artistId }.shuffled(random).take(1)

            SmartFlowMode.ARTIST_FAN ->
                pool.filter { it.artistId == currentTrack.artistId }.shuffled(random).take(n)

            SmartFlowMode.ERA_ENTHUSIAST -> {
                val decade = currentTrack.year?.let { it / 10 }
                pool.filter { it.year?.div(10) == decade }.ifEmpty { pool }.shuffled(random).take(1)
            }

            // Sonic modes — fall back to genre similarity when no analysis is available.
            SmartFlowMode.TRANSITION_MAESTRO,
            SmartFlowMode.ECHO_MATCH,
            SmartFlowMode.STEADY_VIBES -> {
                val genre = currentTrack.genre?.lowercase()
                pool.filter { it.genre?.lowercase() == genre }.ifEmpty { pool }.shuffled(random).take(1)
            }
        }
    }
}
