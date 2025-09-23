package com.swedishvocab.app.domain.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.swedishvocab.app.data.model.CardType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    
    companion object {
        private val DEEPL_API_KEY = stringPreferencesKey("deepl_api_key")
        private val DEFAULT_DECK_NAME = stringPreferencesKey("default_deck_name")
        private val DEFAULT_CARD_TYPE = stringPreferencesKey("default_card_type")
        private val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
    }
    
    fun getDeepLApiKey(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[DEEPL_API_KEY] ?: ""
        }
    }
    
    suspend fun setDeepLApiKey(apiKey: String) {
        dataStore.edit { preferences ->
            preferences[DEEPL_API_KEY] = apiKey
        }
    }
    
    fun getDefaultDeckName(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[DEFAULT_DECK_NAME] ?: "German::Swedish"
        }
    }
    
    suspend fun setDefaultDeckName(deckName: String) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_DECK_NAME] = deckName
        }
    }
    
    fun getDefaultCardType(): Flow<CardType> {
        return dataStore.data.map { preferences ->
            val typeString = preferences[DEFAULT_CARD_TYPE] ?: CardType.UNIDIRECTIONAL.name
            CardType.valueOf(typeString)
        }
    }
    
    suspend fun setDefaultCardType(cardType: CardType) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_CARD_TYPE] = cardType.name
        }
    }
    
    fun isFirstLaunch(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[IS_FIRST_LAUNCH] ?: true
        }
    }
    
    suspend fun setFirstLaunchCompleted() {
        dataStore.edit { preferences ->
            preferences[IS_FIRST_LAUNCH] = false
        }
    }
}
