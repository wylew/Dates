package com.wylew.dates.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** App-wide preferences that persist across launches */
object AppSettings {
    private val DEFAULT_EMOJI_LIBRARY = stringPreferencesKey("default_emoji_library")

    /** The emoji style the picker opens on and new/imported events start with */
    fun defaultEmojiLibrary(context: Context): Flow<EmojiLibrary> =
        context.settingsDataStore.data.map { prefs ->
            prefs[DEFAULT_EMOJI_LIBRARY]?.let { name -> EmojiLibrary.entries.find { it.name == name } } ?: EmojiLibrary.SYSTEM
        }

    suspend fun currentDefaultEmojiLibrary(context: Context): EmojiLibrary = defaultEmojiLibrary(context).first()

    suspend fun setDefaultEmojiLibrary(context: Context, library: EmojiLibrary) {
        context.settingsDataStore.edit { it[DEFAULT_EMOJI_LIBRARY] = library.name }
    }
}
