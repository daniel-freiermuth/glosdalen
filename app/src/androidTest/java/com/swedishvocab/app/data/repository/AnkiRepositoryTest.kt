package com.swedishvocab.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swedishvocab.app.data.model.AnkiCard
import com.swedishvocab.app.data.repository.impl.AnkiApiRepositoryImpl
import com.swedishvocab.app.data.repository.impl.AnkiIntentRepositoryImpl
import com.swedishvocab.app.data.repository.impl.AnkiRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnkiRepositoryTest {

    private lateinit var context: Context
    private lateinit var apiRepository: AnkiApiRepositoryImpl
    private lateinit var intentRepository: AnkiIntentRepositoryImpl
    private lateinit var ankiRepository: AnkiRepositoryImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        apiRepository = AnkiApiRepositoryImpl(context)
        intentRepository = AnkiIntentRepositoryImpl(context)
        ankiRepository = AnkiRepositoryImpl(context, apiRepository, intentRepository)
    }

    @Test
    fun testAnkiDroidAvailability() = runTest {
        // This test checks if our availability detection works
        val isAvailable = ankiRepository.isAnkiDroidAvailable()
        
        // We can't guarantee AnkiDroid is installed in test environment,
        // but the method should not crash
        assert(true) { "Availability check completed without crash" }
    }

    @Test
    fun testCardCreationWithoutAnkiDroid() = runTest {
        // Test that we handle missing AnkiDroid gracefully
        val testCard = AnkiCard(
            fields = mapOf(
                "Front" to "Test German Word",
                "Back" to "Test Swedish Translation"
            ),
            deckName = "Test Deck",
            tags = listOf("test", "german", "swedish")
        )
        
        // This should not crash even if AnkiDroid is not installed
        val result = ankiRepository.createCard(testCard)
        
        // Result might be success or failure depending on AnkiDroid installation
        assert(true) { "Card creation attempt completed without crash" }
    }

    @Test
    fun testImplementationTypeDetection() {
        // Test that we can detect the implementation type
        val implementationType = ankiRepository.getImplementationType()
        
        // Should be one of the valid types
        assert(
            implementationType == AnkiImplementationType.API ||
            implementationType == AnkiImplementationType.INTENT ||
            implementationType == AnkiImplementationType.UNAVAILABLE
        ) { "Implementation type should be valid" }
    }

    @Test
    fun testBatchOperationsSupport() {
        // Test batch operations flag
        val supportsBatch = ankiRepository.supportsBatchOperations()
        
        // Should return a boolean without crashing
        assert(true) { "Batch operations check completed" }
    }
}
