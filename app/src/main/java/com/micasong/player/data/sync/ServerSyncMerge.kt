package com.micasong.player.data.sync

import com.micasong.player.data.db.TrackEntity

/** What to apply to the DB after a server sync: rows to upsert and ids to delete. */
data class SyncApply(
    val upsert: List<TrackEntity>,
    val deleteIds: List<Long>,
) {
    val isEmpty: Boolean get() = upsert.isEmpty() && deleteIds.isEmpty()
}

/**
 * Applies a freshly fetched server snapshot without clobbering local user state (spec §9, §10).
 * A server re-sync brings catalog metadata (title, album, artwork…) but the user's favorites,
 * ratings, play/skip counts, resume points and offline state live only on the device. This merge:
 *  - diffs by track id ignoring user-state,
 *  - keeps unchanged rows untouched,
 *  - carries the existing user-state onto changed rows before upserting,
 *  - deletes rows the server no longer has.
 * Pure and unit-testable — the actual DB writes happen in the repository.
 */
object ServerSyncMerge {

    fun merge(old: List<TrackEntity>, new: List<TrackEntity>): SyncApply {
        val diff = SyncDiffer.diff(old, new, keyOf = { it.id }, contentEquals = ::catalogEquals)
        val oldById = old.associateBy { it.id }
        val upsert = (diff.added + diff.changed).map { incoming ->
            oldById[incoming.id]?.let { incoming.withUserStateFrom(it) } ?: incoming
        }
        return SyncApply(upsert = upsert, deleteIds = diff.removed.map { it.id })
    }

    /** Two rows are the same catalog-wise when they match after zeroing local user-state. */
    private fun catalogEquals(a: TrackEntity, b: TrackEntity): Boolean =
        a.resetUserState() == b.resetUserState()

    private fun TrackEntity.resetUserState(): TrackEntity = copy(
        isFavorite = false,
        userRating = 0,
        playCount = 0,
        skipCount = 0,
        lastPlayed = 0L,
        resumePositionMs = 0L,
        excludedFromMixes = false,
        offlineState = 0,
    )

    private fun TrackEntity.withUserStateFrom(other: TrackEntity): TrackEntity = copy(
        isFavorite = other.isFavorite,
        userRating = other.userRating,
        playCount = other.playCount,
        skipCount = other.skipCount,
        lastPlayed = other.lastPlayed,
        resumePositionMs = other.resumePositionMs,
        excludedFromMixes = other.excludedFromMixes,
        offlineState = other.offlineState,
    )
}
