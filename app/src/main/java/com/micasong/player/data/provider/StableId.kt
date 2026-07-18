package com.micasong.player.data.provider

/**
 * Derives a stable, non-negative, non-zero 64-bit local id from a provider's string id (spec §9).
 *
 * A naive `abs(String.hashCode()) or 1` collapses the least-significant bit, so ids whose 32-bit
 * hashes are adjacent (e.g. "a1"/"a2") collide — which would silently merge distinct albums/tracks.
 * FNV-1a over 64 bits avoids that systematic collision and spreads ids across the full range.
 */
object StableId {
    fun of(id: String): Long {
        var h = 0xcbf29ce484222325uL              // FNV-1a 64-bit offset basis
        for (ch in id) {
            h = h xor ch.code.toULong()
            h *= 0x100000001b3uL                   // FNV-1a 64-bit prime
        }
        val v = h.toLong() and Long.MAX_VALUE      // non-negative
        return if (v == 0L) 1L else v
    }
}
