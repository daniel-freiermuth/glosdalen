package com.glosdalen.app.domain.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.glosdalen.app.backend.anki.CardDirection
import com.glosdalen.app.backend.anki.CardType
import com.glosdalen.app.backend.deepl.DeepLModelType
import com.glosdalen.app.backend.deepl.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade for user preferences that manages cross-cutting concerns and delegates 
 * feature-specific preferences to specialized classes.
 */
@Singleton
class UserPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val deepLPreferences: DeepLPreferences,
    private val ankiPreferences: AnkiPreferences
) {
    companion object {
        private val NATIVE_LANGUAGE = stringPreferencesKey("native_language")
        private val FOREIGN_LANGUAGE = stringPreferencesKey("foreign_language")
    }
    
    // Language preferences (shared across features)
    fun getNativeLanguage(): Flow<Language> {
        return dataStore.data.map { preferences ->
            val languageCode = preferences[NATIVE_LANGUAGE] ?: "DE" // Default to German
            Language.values().find { it.code == languageCode } ?: Language.GERMAN
        }
    }
    
    suspend fun setNativeLanguage(language: Language) {
        dataStore.edit { preferences ->
            preferences[NATIVE_LANGUAGE] = language.code
        }
    }
    
    fun getForeignLanguage(): Flow<Language> {
        return dataStore.data.map { preferences ->
            val languageCode = preferences[FOREIGN_LANGUAGE] ?: "SV" // Default to Swedish
            Language.values().find { it.code == languageCode } ?: Language.SWEDISH
        }
    }
    
    suspend fun setForeignLanguage(language: Language) {
        dataStore.edit { preferences ->
            preferences[FOREIGN_LANGUAGE] = language.code
        }
    }
    
    // DeepL-related preferences
    fun getDeepLApiKey(): Flow<String> = deepLPreferences.getDeepLApiKey()
    suspend fun setDeepLApiKey(apiKey: String) = deepLPreferences.setDeepLApiKey(apiKey)
    
    fun getDeepLModelType(): Flow<DeepLModelType> = deepLPreferences.getDeepLModelType()
    suspend fun setDeepLModelType(modelType: DeepLModelType) = deepLPreferences.setDeepLModelType(modelType)
    
    fun getEnableMultipleFormalities(): Flow<Boolean> = deepLPreferences.getEnableMultipleFormalities()
    suspend fun setEnableMultipleFormalities(enabled: Boolean) = deepLPreferences.setEnableMultipleFormalities(enabled)
    
    // Anki-related preferences
    fun getDefaultDeckName(): Flow<String> = ankiPreferences.getDefaultDeckName()
    suspend fun setDefaultDeckName(deckName: String) = ankiPreferences.setDefaultDeckName(deckName)
    
    fun getDefaultCardType(): Flow<CardType> = ankiPreferences.getDefaultCardType()
    suspend fun setDefaultCardType(cardType: CardType) = ankiPreferences.setDefaultCardType(cardType)
    
    fun getDefaultCardDirection(): Flow<CardDirection> = ankiPreferences.getDefaultCardDirection()
    suspend fun setDefaultCardDirection(direction: CardDirection) = ankiPreferences.setDefaultCardDirection(direction)
    
    fun getPreferredAnkiMethod(): Flow<AnkiMethodPreference> = ankiPreferences.getPreferredAnkiMethod()
    suspend fun setPreferredAnkiMethod(method: AnkiMethodPreference) = ankiPreferences.setPreferredAnkiMethod(method)
}
