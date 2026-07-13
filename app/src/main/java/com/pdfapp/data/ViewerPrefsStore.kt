package com.pdfapp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Reading-comfort settings that persist across documents (plan 2.6). */
class ViewerPrefsStore(
    private val dataStore: DataStore<Preferences>,
) {
    /** Dark-page rendering: invert page bitmaps for night reading. */
    val nightMode: Flow<Boolean> = dataStore.data.map { it[NIGHT_MODE] ?: false }

    /** Keep the screen awake while a document is open. */
    val keepScreenOn: Flow<Boolean> = dataStore.data.map { it[KEEP_SCREEN_ON] ?: false }

    suspend fun setNightMode(enabled: Boolean) {
        dataStore.edit { it[NIGHT_MODE] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[KEEP_SCREEN_ON] = enabled }
    }

    private companion object {
        val NIGHT_MODE = booleanPreferencesKey("night_mode")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }
}
