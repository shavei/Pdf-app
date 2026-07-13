package com.pdfapp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Recently opened documents, most recent first, capped at [MAX_ENTRIES].
 * Also remembers the last-read page per document so reopening a PDF resumes
 * where the user left off (plan 2.1/2.7).
 */
class RecentFilesStore(
    private val dataStore: DataStore<Preferences>,
) {
    /** The recents list, newest first. */
    val recents: Flow<List<RecentFile>> =
        dataStore.data.map { prefs -> RecentFilesCodec.decode(prefs[KEY] ?: "[]") }

    /** Move [file] to the front of the list (inserting or updating it). */
    suspend fun recordOpen(file: RecentFile) {
        update { current ->
            (listOf(file) + current.filterNot { it.uri == file.uri }).take(MAX_ENTRIES)
        }
    }

    /** Remember [pageIndex] as the last-read page of [uri], if it is listed. */
    suspend fun updateLastPage(
        uri: String,
        pageIndex: Int,
    ) {
        update { current ->
            current.map { if (it.uri == uri) it.copy(lastPageIndex = pageIndex) else it }
        }
    }

    /** Drop [uri] from the list (e.g. its permission grant died). */
    suspend fun remove(uri: String) {
        update { current -> current.filterNot { it.uri == uri } }
    }

    private suspend fun update(transform: (List<RecentFile>) -> List<RecentFile>) {
        dataStore.edit { prefs ->
            val current = RecentFilesCodec.decode(prefs[KEY] ?: "[]")
            prefs[KEY] = RecentFilesCodec.encode(transform(current))
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("recent_files")
        const val MAX_ENTRIES = 10
    }
}
