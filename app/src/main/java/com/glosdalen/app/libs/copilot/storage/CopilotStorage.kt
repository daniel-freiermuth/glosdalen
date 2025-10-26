package com.glosdalen.app.libs.copilot.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
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
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            
            EncryptedSharedPreferences.create(
                ENCRYPTED_PREFS_NAME,
                masterKeyAlias,
                context,
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

    // ================================
    // User Preferences
    // ================================

    suspend fun saveUserPreferences(prefs: UserPreferences) = withContext(Dispatchers.IO) {
        try {
            val prefsJson = json.encodeToString(prefs)
            regularPrefs.edit()
                .putString(KEY_USER_PREFERENCES, prefsJson)
                .apply()
        } catch (e: Exception) {
            throw StorageException.SaveFailed(KEY_USER_PREFERENCES, e)
        }
    }

    suspend fun loadUserPreferences(): UserPreferences? = withContext(Dispatchers.IO) {
        try {
            val prefsJson = regularPrefs.getString(KEY_USER_PREFERENCES, null)
            if (prefsJson != null) {
                json.decodeFromString<UserPreferences>(prefsJson)
            } else {
                null
            }
        } catch (e: Exception) {
            throw StorageException.LoadFailed(KEY_USER_PREFERENCES, e)
        }
    }

    // ================================
    // Data Management
    // ================================

    suspend fun clearOAuthToken() = withContext(Dispatchers.IO) {
        encryptedPrefs.edit()
            .remove(KEY_OAUTH_TOKEN)
            .remove(KEY_OAUTH_TOKEN_SAVED_AT)
            .apply()
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

    suspend fun clearModelCache() = withContext(Dispatchers.IO) {
        try {
            regularPrefs.edit()
                .remove(KEY_MODELS_CACHE)
                .remove(KEY_MODELS_CACHE_TIMESTAMP)
                .apply()
        } catch (e: Exception) {
            throw StorageException.SaveFailed("clear_model_cache", e)
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

    // ================================
    // Storage Statistics
    // ================================

    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val hasOAuthToken = encryptedPrefs.contains(KEY_OAUTH_TOKEN)
        val hasCopilotToken = encryptedPrefs.contains(KEY_COPILOT_TOKEN)
        val hasModelCache = regularPrefs.contains(KEY_MODELS_CACHE)
        val hasUserPrefs = regularPrefs.contains(KEY_USER_PREFERENCES)

        val oauthTokenAge = if (hasOAuthToken) {
            val savedAt = encryptedPrefs.getLong(KEY_OAUTH_TOKEN_SAVED_AT, 0)
            if (savedAt > 0) timeProvider.currentTimeMillis() - savedAt else null
        } else null

        val copilotTokenAge = if (hasCopilotToken) {
            val savedAt = encryptedPrefs.getLong(KEY_COPILOT_TOKEN_SAVED_AT, 0)
            if (savedAt > 0) timeProvider.currentTimeMillis() - savedAt else null
        } else null

        val modelCacheAge = if (hasModelCache) {
            val timestamp = regularPrefs.getLong(KEY_MODELS_CACHE_TIMESTAMP, 0)
            if (timestamp > 0) timeProvider.currentTimeMillis() - timestamp else null
        } else null

        StorageInfo(
            hasOAuthToken = hasOAuthToken,
            hasCopilotToken = hasCopilotToken,
            hasModelCache = hasModelCache,
            hasUserPreferences = hasUserPrefs,
            oauthTokenAge = oauthTokenAge,
            copilotTokenAge = copilotTokenAge,
            modelCacheAge = modelCacheAge,
            isModelCacheValid = modelCacheAge?.let { it < MODEL_CACHE_DURATION } ?: false
        )
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
        private const val KEY_USER_PREFERENCES = "user_preferences"

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

data class UserPreferences(
    val preferredModel: String? = null,
    val preferFreeModels: Boolean = true,
    val maxTokens: Int = 150,
    val temperature: Double = 0.1,
    val enableDebugLogging: Boolean = false
)

data class StorageInfo(
    val hasOAuthToken: Boolean,
    val hasCopilotToken: Boolean,
    val hasModelCache: Boolean,
    val hasUserPreferences: Boolean,
    val oauthTokenAge: Long? = null,
    val copilotTokenAge: Long? = null,
    val modelCacheAge: Long? = null,
    val isModelCacheValid: Boolean
)

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