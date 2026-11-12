package com.glosdalen.app.libs.copilot.storage

import android.content.ContextWrapper
import android.content.SharedPreferences
import com.glosdalen.app.libs.copilot.models.CopilotModel
import com.glosdalen.app.libs.copilot.models.CostType
import com.glosdalen.app.libs.copilot.models.ModelCapabilities
import com.glosdalen.app.libs.copilot.models.ModelCategory
import com.glosdalen.app.libs.copilot.models.ModelSupports
import com.glosdalen.app.libs.copilot.models.TokenLimits
import com.glosdalen.app.libs.copilot.util.TimeProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the model cache, which lives in plain SharedPreferences.
 * Token storage needs the real Android Keystore and is covered by the instrumented
 * CopilotStorageTest under androidTest.
 */
class CopilotStorageTest {

    private class FakeClock(var now: Long) : TimeProvider {
        override fun currentTimeMillis(): Long = now
    }

    private val clock = FakeClock(now = 1_000_000L)
    private val context = FakeContext()
    private val storage = CopilotStorage(context, clock)

    private val models = listOf(
        CopilotModel(
            id = "gpt-4o",
            name = "GPT-4o",
            vendor = "Azure OpenAI",
            category = ModelCategory.VERSATILE,
            costType = CostType.FREE,
            capabilities = ModelCapabilities(
                limits = TokenLimits(maxContextTokens = 128_000, maxOutputTokens = 4_096),
                supports = ModelSupports(streaming = true, toolCalls = true)
            ),
            description = "Fast multimodal model"
        ),
        CopilotModel(
            id = "claude-sonnet-4",
            name = "Claude Sonnet 4",
            vendor = "Anthropic",
            category = ModelCategory.POWERFUL,
            costType = CostType.PAID,
            capabilities = ModelCapabilities(supports = ModelSupports()),
            preview = true,
            enabled = false
        )
    )

    @Test
    fun `saved models round-trip with the save time from the time provider`() = runBlocking {
        storage.saveModelCache(models)
        clock.now += 5_000L

        val cached = storage.loadModelCache()

        assertEquals(models, cached?.models)
        assertEquals(1_000_000L, cached?.cachedAt)
    }

    @Test
    fun `empty prefs load as no cache`() = runBlocking {
        assertNull(storage.loadModelCache())
        assertFalse(storage.isModelCacheValid())
    }

    @Test
    fun `models without a timestamp load as no cache`() = runBlocking {
        regularPrefs().edit().putString(KEY_MODELS_CACHE, "[]").commit()

        assertNull(storage.loadModelCache())
    }

    @Test
    fun `cache is valid strictly before max age`() = runBlocking {
        storage.saveModelCache(models)
        val cached = storage.loadModelCache()!!

        clock.now += MAX_AGE_MS - 1
        assertTrue(storage.isModelCacheValid(MAX_AGE_MS))
        assertTrue(cached.isValid(clock, MAX_AGE_MS))

        clock.now += 1
        assertFalse(storage.isModelCacheValid(MAX_AGE_MS))
        assertFalse(cached.isValid(clock, MAX_AGE_MS))
    }

    @Test
    fun `clearAllData drops the model cache`() = runBlocking {
        storage.saveModelCache(models)

        storage.clearAllData()

        assertNull(storage.loadModelCache())
        assertTrue(regularPrefs().all.isEmpty())
    }

    @Test
    fun `cache written by an incompatible schema is discarded instead of failing every load`() = runBlocking {
        // A model persisted before a required field (here: vendor) existed.
        regularPrefs().edit()
            .putString(KEY_MODELS_CACHE, """[{"id":"gpt-4o","name":"GPT-4o"}]""")
            .putLong(KEY_MODELS_CACHE_TIMESTAMP, clock.now)
            .commit()

        assertNull(storage.loadModelCache())
        assertTrue(regularPrefs().all.isEmpty())
        assertFalse(storage.isModelCacheValid())

        storage.saveModelCache(models)
        assertEquals(models, storage.loadModelCache()?.models)
    }

    private fun regularPrefs() = context.getSharedPreferences(REGULAR_PREFS_NAME, 0)

    private companion object {
        const val REGULAR_PREFS_NAME = "copilot_regular_prefs"
        const val KEY_MODELS_CACHE = "models_cache"
        const val MAX_AGE_MS = 60 * 60 * 1000L
        const val KEY_MODELS_CACHE_TIMESTAMP = "models_cache_timestamp"
    }
}

/** Only the SharedPreferences surface CopilotStorage uses, backed by in-memory maps. */
private class FakeContext : ContextWrapper(null) {
    private val files = mutableMapOf<String, InMemoryPrefs>()

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        files.getOrPut(name) { InMemoryPrefs() }

    override fun deleteSharedPreferences(name: String): Boolean = files.remove(name) != null
}

private class InMemoryPrefs : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = values.toMap()
    override fun getString(key: String, defValue: String?): String? = values[key] as String? ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        values[key] as Set<String>? ?: defValues
    override fun getInt(key: String, defValue: Int): Int = values[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as Boolean? ?: defValue
    override fun contains(key: String): Boolean = key in values
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { pending[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { clear = true }

        override fun commit(): Boolean {
            if (clear) values.clear()
            removed.forEach(values::remove)
            pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
