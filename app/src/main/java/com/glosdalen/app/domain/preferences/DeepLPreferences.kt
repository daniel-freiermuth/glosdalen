package com.glosdalen.app.domain.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.glosdalen.app.backend.deepl.DeepLModelType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preference for which side becomes the front when creating bidirectional cards
 */
enum class FrontPreference {
    NATIVE,   // Native language on front (e.g., German → Swedish, Swedish → German)
    FOREIGN;  // Foreign language on front (e.g., Swedish → German, German → Swedish)
}

@Singleton
class DeepLPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    
    companion object {
        private val DEEPL_API_KEY = stringPreferencesKey("deepl_api_key")
        private val DEEPL_MODEL_TYPE = stringPreferencesKey("deepl_model_type")
        private val ENABLE_MULTIPLE_FORMALITIES = booleanPreferencesKey("enable_multiple_formalities")
        private val FRONT_PREFERENCE = stringPreferencesKey("front_preference")
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
    
    fun getFrontPreference(): Flow<FrontPreference> {
        return dataStore.data.map { preferences ->
            val value = preferences[FRONT_PREFERENCE] ?: FrontPreference.NATIVE.name
            FrontPreference.values().find { it.name == value } 
                ?: FrontPreference.NATIVE
        }
    }
    
    suspend fun setFrontPreference(preference: FrontPreference) {
        dataStore.edit { preferences ->
            preferences[FRONT_PREFERENCE] = preference.name
        }
    }
}

