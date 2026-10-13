package com.glosdalen.app.backend.anki

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contract of [addNotesChecked]: hand every note to AnkiDroid with its card's tags, and report
 * success only when AnkiDroid confirms it added all of them.
 */
class AddNotesCheckedTest {

    private val cards = listOf(
        AnkiCard(
            modelName = "Basic",
            fields = mapOf("Front" to "hund", "Back" to "dog"),
            tags = listOf("glosdalen", "vocab", "sv", "en"),
            deckName = "Deck"
        ),
        AnkiCard(
            modelName = "Basic",
            fields = mapOf("Front" to "katt", "Back" to "cat"),
            tags = listOf("glosdalen", "vocab", "sv", "en", "bidirectional"),
            deckName = "Deck"
        )
    )
    private val notes = listOf(arrayOf("hund", "dog"), arrayOf("katt", "cat"))

    @Test
    fun `succeeds when every note is added`() {
        val result = addNotesChecked(cards, notes) { fields, _ -> fields.size }

        assertTrue(result.isSuccess)
    }

    @Test
    fun `fails with CardCreationFailed when AnkiDroid signals a problem`() {
        val result = addNotesChecked(cards, notes) { _, _ -> -1 }

        assertInstanceOf(AnkiError.CardCreationFailed::class.java, result.exceptionOrNull())
    }

    @Test
    fun `fails with CardCreationFailed when only some notes are added`() {
        val result = addNotesChecked(cards, notes) { _, _ -> 1 }

        val error = result.exceptionOrNull()
        assertInstanceOf(AnkiError.CardCreationFailed::class.java, error)
        assertEquals("AnkiDroid added 1 of 2 notes", (error as AnkiError.CardCreationFailed).reason)
    }

    @Test
    fun `passes each card's tags alongside its note`() {
        var submittedFields: List<Array<String>>? = null
        var submittedTags: List<Set<String>>? = null

        addNotesChecked(cards, notes) { fields, tags ->
            submittedFields = fields
            submittedTags = tags
            fields.size
        }

        assertEquals(notes.map { it.toList() }, submittedFields?.map { it.toList() })
        assertEquals(cards.map { it.tags.toSet() }, submittedTags)
    }
}
