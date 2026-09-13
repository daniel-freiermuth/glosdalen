package com.glosdalen.app.libs.copilot.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.glosdalen.app.libs.copilot.models.*
import com.glosdalen.app.libs.copilot.util.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage implementation for Copilot tokens and preferences
 *
 * Uses direct Android Keystore encryption (AES-256-GCM) for sensitive data.
 * Non-sensitive data (model cache, user preferences) uses regular SharedPreferences.
 *
 * Migration note: replaces the deprecated EncryptedSharedPreferences API. Existing
 * users will need to re-authenticate after this migration since the old encrypted
 * data cannot be read without the removed library. The legacy preferences file is
 * deleted on first access.
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

    // SharedPreferences for sensitive data (values encrypted via Keystore before storage)
    private val securePrefs: SharedPreferences by lazy {
        // Clean up legacy EncryptedSharedPreferences files from the deprecated library
        context.deleteSharedPreferences(LEGACY_ENCRYPTED_PREFS_NAME)
        context.deleteSharedPreferences(LEGACY_FALLBACK_PREFS_NAME)
        context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Regular preferences for non-sensitive data
    private val regularPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(REGULAR_PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ================================
    // Keystore Encryption
    // ================================

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts [plaintext] using AES-256-GCM with a Keystore-managed key.
     * Returns a Base64 string containing the 12-byte IV prepended to the ciphertext.
     */
    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts a Base64 string produced by [encrypt].
     * Throws on decryption failure (corrupt data, wrong key, tampered ciphertext).
     */
    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    // ================================
    // OAuth Token Management
    // ================================

    suspend fun saveOAuthToken(token: OAuthToken) = withContext(Dispatchers.IO) {
        try {
            val encrypted = encrypt(json.encodeToString(token))
            securePrefs.edit()
                .putString(KEY_OAUTH_TOKEN, encrypted)
                .putLong(KEY_OAUTH_TOKEN_SAVED_AT, timeProvider.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            throw StorageException.SaveFailed(KEY_OAUTH_TOKEN, e)
        }
    }

    suspend fun loadOAuthToken(): OAuthToken? = withContext(Dispatchers.IO) {
        try {
            val encrypted = securePrefs.getString(KEY_OAUTH_TOKEN, null)
                ?: return@withContext null
            val tokenJson = decrypt(encrypted)
            json.decodeFromString<OAuthToken>(tokenJson)
        } catch (e: Exception) {
            throw StorageException.LoadFailed(KEY_OAUTH_TOKEN, e)
        }
    }

    // ================================
    // Copilot Token Management
    // ================================

    suspend fun saveCopilotToken(token: CopilotToken) = withContext(Dispatchers.IO) {
        try {
            val encrypted = encrypt(json.encodeToString(token))
            securePrefs.edit()
                .putString(KEY_COPILOT_TOKEN, encrypted)
                .putLong(KEY_COPILOT_TOKEN_SAVED_AT, timeProvider.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            throw StorageException.SaveFailed(KEY_COPILOT_TOKEN, e)
        }
    }

    suspend fun loadCopilotToken(): CopilotToken? = withContext(Dispatchers.IO) {
        try {
            val encrypted = securePrefs.getString(KEY_COPILOT_TOKEN, null)
                ?: return@withContext null
            val tokenJson = decrypt(encrypted)
            json.decodeFromString<CopilotToken>(tokenJson)
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
        securePrefs.edit()
            .remove(KEY_OAUTH_TOKEN)
            .remove(KEY_OAUTH_TOKEN_SAVED_AT)
            .apply()
    }

    suspend fun clearCopilotToken() = withContext(Dispatchers.IO) {
        try {
            securePrefs.edit()
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
            securePrefs.edit().clear().apply()
            regularPrefs.edit().clear().apply()
        } catch (e: Exception) {
            throw StorageException.SaveFailed("clear_all_data", e)
        }
    }

    // ================================
    // Storage Statistics
    // ================================

    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val hasOAuthToken = securePrefs.contains(KEY_OAUTH_TOKEN)
        val hasCopilotToken = securePrefs.contains(KEY_COPILOT_TOKEN)
        val hasModelCache = regularPrefs.contains(KEY_MODELS_CACHE)
        val hasUserPrefs = regularPrefs.contains(KEY_USER_PREFERENCES)

        val oauthTokenAge = if (hasOAuthToken) {
            val savedAt = securePrefs.getLong(KEY_OAUTH_TOKEN_SAVED_AT, 0)
            if (savedAt > 0) timeProvider.currentTimeMillis() - savedAt else null
        } else null

        val copilotTokenAge = if (hasCopilotToken) {
            val savedAt = securePrefs.getLong(KEY_COPILOT_TOKEN_SAVED_AT, 0)
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
        // Android Keystore constants
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "copilot_storage_key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128

        // Storage file names
        private const val SECURE_PREFS_NAME = "copilot_keystore_prefs"
        private const val REGULAR_PREFS_NAME = "copilot_regular_prefs"

        // Legacy file names from EncryptedSharedPreferences (deleted on migration)
        private const val LEGACY_ENCRYPTED_PREFS_NAME = "copilot_secure_prefs"
        private const val LEGACY_FALLBACK_PREFS_NAME = "copilot_fallback_prefs"

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
    class SaveFailed(key: String, cause: Throwable) : StorageException("Failed to save: $key", cause)
    class LoadFailed(key: String, cause: Throwable) : StorageException("Failed to load: $key", cause)
}
