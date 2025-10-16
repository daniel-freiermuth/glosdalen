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

@Singleton
class DeepLPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    
    companion object {
        private val DEEPL_API_KEY = stringPreferencesKey("deepl_api_key")
        private val DEEPL_MODEL_TYPE = stringPreferencesKey("deepl_model_type")
        private val ENABLE_MULTIPLE_FORMALITIES = booleanPreferencesKey("enable_multiple_formalities")
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
}
