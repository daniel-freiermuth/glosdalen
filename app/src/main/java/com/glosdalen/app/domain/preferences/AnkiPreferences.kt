package com.glosdalen.app.domain.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnkiPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    
    companion object {
        private val DEFAULT_DECK_NAME = stringPreferencesKey("default_deck_name")
        private val PREFERRED_ANKI_METHOD = stringPreferencesKey("preferred_anki_method")
    }
    
    fun getDefaultDeckName(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[DEFAULT_DECK_NAME] ?: "Glosdalen::{foreign_native}"
        }
    }
    
    suspend fun setDefaultDeckName(deckName: String) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_DECK_NAME] = deckName
        }
    }
    
    fun getPreferredAnkiMethod(): Flow<AnkiMethodPreference> {
        return dataStore.data.map { preferences ->
            val methodString = preferences[PREFERRED_ANKI_METHOD] ?: "AUTO"
            try {
                AnkiMethodPreference.valueOf(methodString)
            } catch (e: IllegalArgumentException) {
                AnkiMethodPreference.AUTO
            }
        }
    }
    
    suspend fun setPreferredAnkiMethod(method: AnkiMethodPreference) {
        dataStore.edit { preferences ->
            preferences[PREFERRED_ANKI_METHOD] = method.name
        }
    }
}

enum class AnkiMethodPreference {
    AUTO,   // Automatically choose the best available method
    API,    // Prefer AnkiDroid API
    INTENT  // Prefer Intent method
}
