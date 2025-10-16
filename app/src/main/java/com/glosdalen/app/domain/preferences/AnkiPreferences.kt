package com.glosdalen.app.domain.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.glosdalen.app.backend.anki.CardDirection
import com.glosdalen.app.backend.anki.CardType
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
        private val DEFAULT_CARD_TYPE = stringPreferencesKey("default_card_type")
        private val DEFAULT_CARD_DIRECTION = stringPreferencesKey("default_card_direction")
        private val PREFERRED_ANKI_METHOD = stringPreferencesKey("preferred_anki_method")
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
    
    fun getDefaultCardDirection(): Flow<CardDirection> {
        return dataStore.data.map { preferences ->
            val directionString = preferences[DEFAULT_CARD_DIRECTION] ?: CardDirection.NATIVE_TO_FOREIGN.name
            CardDirection.valueOf(directionString)
        }
    }
    
    suspend fun setDefaultCardDirection(direction: CardDirection) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_CARD_DIRECTION] = direction.name
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
