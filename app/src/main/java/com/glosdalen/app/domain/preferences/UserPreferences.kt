package com.glosdalen.app.domain.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
    private val ankiPreferences: AnkiPreferences,
    private val copilotPreferences: CopilotPreferences,
    private val copilotLanguagePreferences: CopilotLanguagePreferences,
    private val copilotKnowledgePreferences: CopilotKnowledgePreferences,
    private val languageInstructionsPreferences: LanguageInstructionsPreferences
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
    
    fun getFrontPreference(): Flow<FrontPreference> = deepLPreferences.getFrontPreference()
    suspend fun setFrontPreference(preference: FrontPreference) = deepLPreferences.setFrontPreference(preference)
    
    // Anki-related preferences
    fun getDefaultDeckName(): Flow<String> = ankiPreferences.getDefaultDeckName()
    suspend fun setDefaultDeckName(deckName: String) = ankiPreferences.setDefaultDeckName(deckName)
    
    // Copilot-related preferences
    fun getCopilotSelectedModel(): Flow<String> = copilotPreferences.getSelectedModel()
    suspend fun setCopilotSelectedModel(modelId: String) = copilotPreferences.setSelectedModel(modelId)
    
    fun getCopilotTemperature(): Flow<Float> = copilotPreferences.getTemperature()
    suspend fun setCopilotTemperature(temperature: Float) = copilotPreferences.setTemperature(temperature)
    
    // Copilot Language mode preferences
    fun getCopilotLanguageInstructions(): Flow<String> = copilotLanguagePreferences.getLanguageInstructions()
    suspend fun setCopilotLanguageInstructions(instructions: String) = copilotLanguagePreferences.setLanguageInstructions(instructions)
    
    fun shouldShowCopilotLanguageIntroDialog(): Flow<Boolean> = copilotLanguagePreferences.shouldShowIntroDialog()
    suspend fun setShowCopilotLanguageIntroDialog(show: Boolean) = copilotLanguagePreferences.setShowIntroDialog(show)
    
    // Copilot Knowledge mode preferences
    fun getCopilotKnowledgeInstructions(): Flow<String> = copilotKnowledgePreferences.getKnowledgeInstructions()
    suspend fun setCopilotKnowledgeInstructions(instructions: String) = copilotKnowledgePreferences.setKnowledgeInstructions(instructions)
    
    fun shouldShowCopilotKnowledgeIntroDialog(): Flow<Boolean> = copilotKnowledgePreferences.shouldShowIntroDialog()
    suspend fun setShowCopilotKnowledgeIntroDialog(show: Boolean) = copilotKnowledgePreferences.setShowIntroDialog(show)
    
    fun getCopilotKnowledgeDeckTemplate(): Flow<String> = copilotKnowledgePreferences.getDeckTemplate()
    suspend fun setCopilotKnowledgeDeckTemplate(template: String) = copilotKnowledgePreferences.setDeckTemplate(template)
    
    // Language-specific instructions
    fun getLanguageInstructions(language: Language): Flow<String> = languageInstructionsPreferences.getInstructions(language)
    suspend fun setLanguageInstructions(language: Language, instructions: String) = languageInstructionsPreferences.setInstructions(language, instructions)
    fun getAllLanguageInstructions(): Flow<Map<Language, String>> = languageInstructionsPreferences.getAllInstructions()
    fun getDefaultLanguageInstructions(language: Language): String = languageInstructionsPreferences.getDefaultInstructions(language)
    fun hasCustomLanguageInstructions(language: Language): Flow<Boolean> = languageInstructionsPreferences.hasCustomInstructions(language)
}
