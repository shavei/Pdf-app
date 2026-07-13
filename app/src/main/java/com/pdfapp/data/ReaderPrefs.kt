package com.pdfapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * The app's single Preferences DataStore, shared by [RecentFilesStore] and
 * [ViewerPrefsStore] (DataStore requires exactly one instance per file).
 */
val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(name = "reader_prefs")
