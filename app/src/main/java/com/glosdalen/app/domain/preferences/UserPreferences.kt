package com.glosdalen.app.domain.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.glosdalen.app.data.model.CardType
import com.glosdalen.app.data.model.CardDirection
import com.glosdalen.app.data.model.DeepLModelType
import com.glosdalen.app.data.model.Language
import com.glosdalen.app.data.repository.AnkiImplementationType
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
        private val DEFAULT_CARD_DIRECTION = stringPreferencesKey("default_card_direction")
        private val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        private val NATIVE_LANGUAGE = stringPreferencesKey("native_language")
        private val FOREIGN_LANGUAGE = stringPreferencesKey("foreign_language")
        private val DEEPL_MODEL_TYPE = stringPreferencesKey("deepl_model_type")
        private val ENABLE_MULTIPLE_FORMALITIES = booleanPreferencesKey("enable_multiple_formalities")
        private val PREFERRED_ANKI_METHOD = stringPreferencesKey("preferred_anki_method")
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
    
    fun getDeepLModelType(): Flow<DeepLModelType> {
        return dataStore.data.map { preferences ->
            val modelTypeValue = preferences[DEEPL_MODEL_TYPE] ?: ""
            DeepLModelType.values().find { it.value == modelTypeValue } ?: DeepLModelType.QUALITY_OPTIMIZED
        }
    }
    
    suspend fun setDeepLModelType(modelType: DeepLModelType) {
        dataStore.edit { preferences ->
            preferences[DEEPL_MODEL_TYPE] = modelType.value
        }
    }
    
    fun getEnableMultipleFormalities(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[ENABLE_MULTIPLE_FORMALITIES] ?: true // Default to enabled
        }
    }
    
    suspend fun setEnableMultipleFormalities(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[ENABLE_MULTIPLE_FORMALITIES] = enabled
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
