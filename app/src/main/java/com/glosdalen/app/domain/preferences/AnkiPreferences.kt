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
}