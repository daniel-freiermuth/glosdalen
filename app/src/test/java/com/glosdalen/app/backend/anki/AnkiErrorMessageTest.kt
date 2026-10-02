package com.glosdalen.app.backend.anki

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnkiErrorMessageTest {

    @Test
    fun `model creation failure surfaces note type and its reason`() {
        val message = AnkiError.ModelCreationFailed("Reversed card model not found")
            .toCardCreationErrorMessage()
        assertTrue(message.contains("note type"), message)
        assertTrue(message.contains("Reversed card model not found"), message)
    }

    @Test
    fun `permission denied maps to permission guidance`() {
        assertEquals(
            "AnkiDroid permission required. Please grant access in settings.",
            AnkiError.PermissionDenied("Permission denied: x").toCardCreationErrorMessage()
        )
    }

    @Test
    fun `deck creation failure shows its specific reason`() {
        val reason = "Invalid deck name: whitespace not allowed around '::' separator."
        assertEquals(reason, AnkiError.DeckCreationFailed(reason).toCardCreationErrorMessage())
    }

    @Test
    fun `AnkiError carries its reason as exception message for logs`() {
        assertEquals("Batch API error: boom", AnkiError.ApiError("Batch API error: boom").message)
    }

    @Test
    fun `non-Anki throwable without message falls back to unknown error`() {
        assertEquals("Error when creating card: Unknown error", RuntimeException().toCardCreationErrorMessage())
    }
}
