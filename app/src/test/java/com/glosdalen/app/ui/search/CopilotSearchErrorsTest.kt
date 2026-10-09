package com.glosdalen.app.ui.search

import com.glosdalen.app.backend.anki.AnkiError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class CopilotSearchErrorsTest {

    // --- Real AnkiError instances as produced by the Anki repositories ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("repositoryFailures")
    fun `repository AnkiError maps to its specific user message`(error: AnkiError, expected: String) {
        assertEquals(expected, CopilotSearchErrors.ankiFailure(error))
    }

    @Test
    fun `unclassified AnkiError surfaces its reason in the fallback message`() {
        val error = AnkiError.ApiNotAvailable("AnkiDroid API not available")

        assertEquals(
            "Error when creating card: AnkiDroid API not available",
            CopilotSearchErrors.ankiFailure(error)
        )
    }

    // --- Message-based classification boundaries ---

    @Test
    fun `throwable without message returns generic failure`() {
        assertEquals("Failed to create Anki card", CopilotSearchErrors.ankiFailure(RuntimeException()))
    }

    @Test
    fun `permission takes precedence over deck when both are mentioned`() {
        val error = RuntimeException("Permission denied while creating deck")

        assertEquals(PERMISSION, CopilotSearchErrors.ankiFailure(error))
    }

    @Test
    fun `unrecognised message is included in fallback text`() {
        val error = RuntimeException("Something odd happened")

        assertEquals(
            "Error when creating card: Something odd happened",
            CopilotSearchErrors.ankiFailure(error)
        )
    }

    companion object {
        private const val PERMISSION = "AnkiDroid permission required. Please grant access in settings."
        private const val NOT_INSTALLED = "AnkiDroid is not installed. Please install it from the Play Store."
        private const val DECK = "Failed to create deck. Please check AnkiDroid settings."
        private const val MODEL = "Card type not found. Please open AnkiDroid first to initialize note types."

        // Reasons copied from the call sites in AnkiApiRepository / AnkiIntentRepository.
        @JvmStatic
        fun repositoryFailures(): Stream<Arguments> = Stream.of(
            Arguments.of(AnkiError.PermissionDenied("Permission denied: no READ_WRITE_PERMISSION"), PERMISSION),
            Arguments.of(AnkiError.AnkiDroidNotInstalled, NOT_INSTALLED),
            Arguments.of(
                AnkiError.DeckCreationFailed(
                    "Invalid deck name: whitespace not allowed around '::' separator. Please check your deck name template."
                ),
                DECK
            ),
            Arguments.of(AnkiError.DeckCreationFailed("Failed to create deck: Vocab::German"), DECK),
            Arguments.of(
                AnkiError.ModelCreationFailed(
                    "Reversed card model not found. Please open AnkiDroid and ensure default note types are available, or try creating a card manually first."
                ),
                MODEL
            ),
            Arguments.of(AnkiError.ModelCreationFailed("Failed to create model: Basic"), MODEL),
        )
    }
}
