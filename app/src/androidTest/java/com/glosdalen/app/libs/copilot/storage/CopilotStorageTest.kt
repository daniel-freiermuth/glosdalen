package com.glosdalen.app.libs.copilot.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.glosdalen.app.libs.copilot.models.CopilotToken
import com.glosdalen.app.libs.copilot.models.OAuthToken
import com.glosdalen.app.libs.copilot.util.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/**
 * Runs against the real Android Keystore: the encryption format and key lifecycle
 * cannot be exercised on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class CopilotStorageTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val timeProvider = object : TimeProvider {
        override fun currentTimeMillis(): Long = 1_000L
    }

    private val oauthToken = OAuthToken(accessToken = "gho_secretAccessTokenValue1234567890")
    private val copilotToken = CopilotToken(token = "tid=secret-copilot-token;exp=9999999999", expiresAt = 42L)

    @Before
    fun resetState() {
        context.deleteSharedPreferences(CopilotStorage.SECURE_PREFS_NAME)
        deleteKeystoreKey()
    }

    @Test
    fun tokensRoundTripAndAreNotPersistedInPlaintext() = runBlocking {
        val storage = CopilotStorage(context, timeProvider)

        storage.saveOAuthToken(oauthToken)
        storage.saveCopilotToken(copilotToken)

        assertEquals(oauthToken, storage.loadOAuthToken())
        assertEquals(copilotToken, storage.loadCopilotToken())
        val persisted = securePrefs().all.values.joinToString()
        assertFalse(persisted.contains(oauthToken.accessToken!!))
        assertFalse(persisted.contains(copilotToken.token))
    }

    @Test
    fun tokenEncryptedWithLostKeyIsDiscardedSoItCanBeReplaced() = runBlocking {
        val storage = CopilotStorage(context, timeProvider)
        storage.saveOAuthToken(oauthToken)
        storage.saveCopilotToken(copilotToken)

        // Same state as a prefs file restored from backup onto a device without the key.
        deleteKeystoreKey()

        assertNull(storage.loadOAuthToken())
        assertNull(storage.loadCopilotToken())
        assertFalse(securePrefs().contains("oauth_token"))
        assertFalse(securePrefs().contains("copilot_token"))

        storage.saveCopilotToken(copilotToken)
        assertEquals(copilotToken, storage.loadCopilotToken())
    }

    @Test
    fun tokenWithCorruptedPayloadIsDiscarded() = runBlocking {
        val storage = CopilotStorage(context, timeProvider)
        storage.saveCopilotToken(copilotToken)
        securePrefs().edit().putString("copilot_token", "not base64 !!").commit()

        assertNull(storage.loadCopilotToken())
        assertFalse(securePrefs().contains("copilot_token"))
    }

    @Test
    fun concurrentFirstSavesAllRemainReadable() = runBlocking {
        repeat(CONCURRENCY_ROUNDS) { round ->
            resetState()
            val storages = List(PARALLEL_WRITERS) { CopilotStorage(context, timeProvider) }

            storages.mapIndexed { index, storage ->
                async(Dispatchers.IO) {
                    if (index % 2 == 0) storage.saveOAuthToken(oauthToken)
                    else storage.saveCopilotToken(copilotToken)
                }
            }.awaitAll()

            val reader = CopilotStorage(context, timeProvider)
            assertEquals("round $round", oauthToken, reader.loadOAuthToken())
            assertEquals("round $round", copilotToken, reader.loadCopilotToken())
        }
    }

    private fun securePrefs() =
        context.getSharedPreferences(CopilotStorage.SECURE_PREFS_NAME, Context.MODE_PRIVATE)

    private fun deleteKeystoreKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .deleteEntry(CopilotStorage.KEY_ALIAS)
    }

    private companion object {
        const val CONCURRENCY_ROUNDS = 20
        const val PARALLEL_WRITERS = 8
    }
}
