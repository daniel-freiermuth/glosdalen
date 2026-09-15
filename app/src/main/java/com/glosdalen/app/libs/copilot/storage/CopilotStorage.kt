package com.glosdalen.app.libs.copilot.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.glosdalen.app.libs.copilot.models.*
import com.glosdalen.app.libs.copilot.util.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage implementation for Copilot tokens and preferences
 * 
 * Uses Android's EncryptedSharedPreferences for secure storage of sensitive data
 */
@Singleton
class CopilotStorage @Inject constructor(
    private val context: Context,
    private val timeProvider: TimeProvider
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Lazy initialization of encrypted preferences
    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to regular preferences if encryption fails
            // This should be logged and handled appropriately in production
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    // Regular preferences for non-sensitive data
    private val regularPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(REGULAR_PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ================================
    // OAuth Token Management
    // ================================

    suspend fun saveOAuthToken(token: OAuthToken) = withContext(Dispatchers.IO) {
        try {
            val tokenJson = json.encodeToString(token)
            encryptedPrefs.edit()
                .putString(KEY_OAUTH_TOKEN, tokenJson)
                .putLong(KEY_OAUTH_TOKEN_SAVED_AT, timeProvider.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            throw StorageException.SaveFailed(KEY_OAUTH_TOKEN, e)
        }
    }

    suspend fun loadOAuthToken(): OAuthToken? = withContext(Dispatchers.IO) {
        try {
            val tokenJson = encryptedPrefs.getString(KEY_OAUTH_TOKEN, null)
            if (tokenJson != null) {
                json.decodeFromString<OAuthToken>(tokenJson)
            } else {
                null
            }
        } catch (e: Exception) {
            throw StorageException.LoadFailed(KEY_OAUTH_TOKEN, e)
        }
    }

    // ================================
    // Copilot Token Management
    // ================================

    suspend fun saveCopilotToken(token: CopilotToken) = withContext(Dispatchers.IO) {
        try {
            val tokenJson = json.encodeToString(token)
            encryptedPrefs.edit()
                .putString(KEY_COPILOT_TOKEN, tokenJson)
                .putLong(KEY_COPILOT_TOKEN_SAVED_AT, timeProvider.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            throw StorageException.SaveFailed(KEY_COPILOT_TOKEN, e)
        }
    }

    suspend fun loadCopilotToken(): CopilotToken? = withContext(Dispatchers.IO) {
        try {
            val tokenJson = encryptedPrefs.getString(KEY_COPILOT_TOKEN, null)
            if (tokenJson != null) {
                json.decodeFromString<CopilotToken>(tokenJson)
            } else {
                null
            }
        } catch (e: Exception) {
            throw StorageException.LoadFailed(KEY_COPILOT_TOKEN, e)
        }
    }

    // ================================
    // Model Cache Management
    // ================================

    suspend fun saveModelCache(models: List<CopilotModel>) = withContext(Dispatchers.IO) {
        try {
            val modelsJson = json.encodeToString(models)
            regularPrefs.edit()
                .putString(KEY_MODELS_CACHE, modelsJson)
                .putLong(KEY_MODELS_CACHE_TIMESTAMP, timeProvider.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            throw StorageException.SaveFailed(KEY_MODELS_CACHE, e)
        }
    }

    suspend fun loadModelCache(): CachedModels? = withContext(Dispatchers.IO) {
        try {
            val modelsJson = regularPrefs.getString(KEY_MODELS_CACHE, null)
            val timestamp = regularPrefs.getLong(KEY_MODELS_CACHE_TIMESTAMP, 0)
            
            if (modelsJson != null && timestamp > 0) {
                val models = json.decodeFromString<List<CopilotModel>>(modelsJson)
                CachedModels(models, timestamp)
            } else {
                null
            }
        } catch (e: Exception) {
            throw StorageException.LoadFailed(KEY_MODELS_CACHE, e)
        }
    }

    suspend fun isModelCacheValid(maxAgeMs: Long = MODEL_CACHE_DURATION): Boolean = withContext(Dispatchers.IO) {
        val timestamp = regularPrefs.getLong(KEY_MODELS_CACHE_TIMESTAMP, 0)
        if (timestamp == 0L) return@withContext false
        
        val age = timeProvider.currentTimeMillis() - timestamp
        return@withContext age < maxAgeMs
    }

    suspend fun clearCopilotToken() = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.edit()
                .remove(KEY_COPILOT_TOKEN)
                .remove(KEY_COPILOT_TOKEN_SAVED_AT)
                .apply()
        } catch (e: Exception) {
            throw StorageException.SaveFailed("clear_copilot_token", e)
        }
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.edit().clear().apply()
            regularPrefs.edit().clear().apply()
        } catch (e: Exception) {
            throw StorageException.SaveFailed("clear_all_data", e)
        }
    }

    companion object {
        // Storage file names
        private const val ENCRYPTED_PREFS_NAME = "copilot_secure_prefs"
        private const val FALLBACK_PREFS_NAME = "copilot_fallback_prefs"
        private const val REGULAR_PREFS_NAME = "copilot_regular_prefs"

        // Storage keys
        private const val KEY_OAUTH_TOKEN = "oauth_token"
        private const val KEY_OAUTH_TOKEN_SAVED_AT = "oauth_token_saved_at"
        private const val KEY_COPILOT_TOKEN = "copilot_token"
        private const val KEY_COPILOT_TOKEN_SAVED_AT = "copilot_token_saved_at"
        private const val KEY_MODELS_CACHE = "models_cache"
        private const val KEY_MODELS_CACHE_TIMESTAMP = "models_cache_timestamp"

        // Cache duration - models are cached for 1 hour
        private const val MODEL_CACHE_DURATION = 60 * 60 * 1000L
    }
}

// ================================
// Supporting Data Classes
// ================================

data class CachedModels(
    val models: List<CopilotModel>,
    val cachedAt: Long
) {
    fun isValid(timeProvider: TimeProvider, maxAgeMs: Long = 60 * 60 * 1000L): Boolean {
        val age = timeProvider.currentTimeMillis() - cachedAt
        return age < maxAgeMs
    }
}

// ================================
// Storage Exceptions
// ================================

sealed class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class EncryptionFailed(cause: Throwable? = null) : StorageException("Failed to encrypt data", cause)
    class DecryptionFailed(cause: Throwable? = null) : StorageException("Failed to decrypt data", cause)
    class SaveFailed(val key: String, cause: Throwable? = null) : StorageException("Failed to save data for key: $key", cause)
    class LoadFailed(val key: String, cause: Throwable? = null) : StorageException("Failed to load data for key: $key", cause)
    class CorruptedData(val key: String) : StorageException("Corrupted data found for key: $key")
}