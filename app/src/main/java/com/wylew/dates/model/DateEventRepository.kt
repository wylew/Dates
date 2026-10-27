package com.wylew.dates.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "events")

class DateEventRepository(private val context: Context) {
    private val EVENTS_KEY = stringPreferencesKey("events_list")

    val events: Flow<List<DateEvent>> = context.dataStore.data.map { preferences ->
        val json = preferences[EVENTS_KEY] ?: "[]"
        Json.decodeFromString(json)
    }

    suspend fun saveEvents(events: List<DateEvent>) {
        context.dataStore.edit { preferences ->
            preferences[EVENTS_KEY] = Json.encodeToString(events)
        }
    }
}
