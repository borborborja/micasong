package com.micasong.player.data.queue

import kotlinx.serialization.Serializable

/** Media kind a queue holds — lets music and audiobook queues coexist (spec §16). */
@Serializable
enum class QueueMediaType { MUSIC, AUDIOBOOK, PODCAST }

/**
 * One independent, persistable playback queue (spec §16). Each queue keeps its own position,
 * repeat, shuffle and speed so switching between music and audiobooks never loses state.
 */
@Serializable
data class SavedQueue(
    val id: Long,
    val name: String,
    val trackIds: List<Long>,
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    val repeatMode: Int = 0,          // 0 off · 1 all · 2 one
    val shuffle: Boolean = false,
    val speed: Float = 1f,
    val mediaType: QueueMediaType = QueueMediaType.MUSIC,
)

/**
 * Holds the set of independent queues, capped per the spec ("up to ~15", §16). Adding a queue
 * when full evicts the oldest *inactive* one so the queue you're listening to is never dropped.
 * Immutable: every mutation returns a new [QueueBook], which keeps it trivially unit-testable and
 * safe to expose as StateFlow.
 */
data class QueueBook(
    val queues: List<SavedQueue> = emptyList(),
    val activeId: Long? = null,
    val maxQueues: Int = MAX_QUEUES,
) {
    val active: SavedQueue? get() = queues.firstOrNull { it.id == activeId }
    val isFull: Boolean get() = queues.size >= maxQueues

    /** Add a queue and make it active, evicting the oldest inactive queue if at capacity. */
    fun add(queue: SavedQueue): QueueBook {
        val trimmed = if (isFull) {
            val evictId = queues.firstOrNull { it.id != activeId }?.id
            queues.filterNot { it.id == evictId }
        } else queues
        // Replace any existing queue with the same id.
        val next = trimmed.filterNot { it.id == queue.id } + queue
        return copy(queues = next, activeId = queue.id)
    }

    fun switchTo(id: Long): QueueBook =
        if (queues.any { it.id == id }) copy(activeId = id) else this

    fun remove(id: Long): QueueBook {
        val next = queues.filterNot { it.id == id }
        val newActive = when {
            activeId != id -> activeId
            else -> next.lastOrNull()?.id
        }
        return copy(queues = next, activeId = newActive)
    }

    fun rename(id: Long, name: String): QueueBook =
        copy(queues = queues.map { if (it.id == id) it.copy(name = name) else it })

    /** Update the active queue's transient state (position/repeat/shuffle/speed). */
    fun updateActive(transform: (SavedQueue) -> SavedQueue): QueueBook {
        val id = activeId ?: return this
        return copy(queues = queues.map { if (it.id == id) transform(it) else it })
    }

    companion object {
        const val MAX_QUEUES = 15
    }
}
