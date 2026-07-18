package com.micasong.player.data.queue

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Serializable snapshot of the whole [QueueBook] (spec §16). */
@Serializable
private data class QueueBookDto(val queues: List<SavedQueue> = emptyList(), val activeId: Long? = null)

private val Context.queueDataStore: DataStore<Preferences> by preferencesDataStore(name = "queues")

/** Persists the set of independent playback queues (spec §16) as JSON in its own DataStore. */
@Singleton
class QueueStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("queue_book")

    val queueBook: Flow<QueueBook> = context.queueDataStore.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString<QueueBookDto>(it) }.getOrNull() }
            ?.let { QueueBook(it.queues, it.activeId) } ?: QueueBook()
    }

    suspend fun current(): QueueBook = queueBook.first()

    suspend fun update(transform: (QueueBook) -> QueueBook) {
        val next = transform(current())
        context.queueDataStore.edit {
            it[key] = json.encodeToString(QueueBookDto(next.queues, next.activeId))
        }
    }
}
