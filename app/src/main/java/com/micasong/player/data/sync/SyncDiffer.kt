package com.micasong.player.data.sync

/** The result of diffing two library snapshots (spec §9 differential sync). */
data class SyncDiff<T>(
    val added: List<T>,
    val removed: List<T>,
    val changed: List<T>,
    val unchangedCount: Int,
) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty() && changed.isEmpty()
    val touchedCount: Int get() = added.size + removed.size + changed.size
}

/**
 * Differential sync engine (spec §9). Compares the previous library snapshot with a freshly
 * fetched one and reports exactly what to upsert (added + changed) and delete (removed), so a
 * server re-sync touches only what actually changed instead of clearing and rewriting the whole
 * catalog. Identity is by a stable key; "changed" is decided by a content comparator (e.g. a
 * timestamp or hash) so unchanged rows are skipped cheaply.
 */
object SyncDiffer {

    fun <T, K> diff(
        old: List<T>,
        new: List<T>,
        keyOf: (T) -> K,
        contentEquals: (old: T, new: T) -> Boolean = { a, b -> a == b },
    ): SyncDiff<T> {
        val oldByKey = old.associateBy(keyOf)
        val newKeys = HashSet<K>(new.size)

        val added = ArrayList<T>()
        val changed = ArrayList<T>()
        var unchanged = 0
        for (item in new) {
            val key = keyOf(item)
            newKeys.add(key)
            val previous = oldByKey[key]
            when {
                previous == null -> added += item
                contentEquals(previous, item) -> unchanged++
                else -> changed += item
            }
        }
        val removed = old.filter { keyOf(it) !in newKeys }
        return SyncDiff(added, removed, changed, unchanged)
    }
}
