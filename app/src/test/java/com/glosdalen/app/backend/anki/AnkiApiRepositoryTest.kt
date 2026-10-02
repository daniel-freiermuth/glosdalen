package com.glosdalen.app.backend.anki

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AnkiApiRepositoryTest {

    private fun reversedModelId(vararg models: Pair<Long, String>): Long? =
        findReversedModel(linkedMapOf(*models))?.key

    // --- English name ---

    @Test
    fun `exact English name is found`() {
        assertEquals(2L, reversedModelId(1L to "Basic", 2L to "Basic (and reversed card)", 3L to "Cloze"))
    }

    @Test
    fun `English name is matched case-insensitively`() {
        assertEquals(2L, reversedModelId(1L to "Basic", 2L to "basic (and reversed card)"))
    }

    @Test
    fun `English name wins over earlier localized-looking variants`() {
        // "optional reversed" also contains "reversed" but must not shadow the real model
        assertEquals(
            2L,
            reversedModelId(1L to "Basic (optional reversed card)", 2L to "BASIC (AND REVERSED CARD)")
        )
    }

    // --- Localized names ---

    @Test
    fun `German name is found`() {
        assertEquals(2L, reversedModelId(1L to "Einfach", 2L to "Einfach (beide Richtungen)"))
    }

    @Test
    fun `German partial name with einfach and richtung is found`() {
        assertEquals(2L, reversedModelId(1L to "Einfach", 2L to "Einfach (Richtung umkehrbar)"))
    }

    @Test
    fun `Spanish or Italian inverso name is found`() {
        assertEquals(2L, reversedModelId(1L to "Base", 2L to "Base (con carta inverso)"))
    }

    @Test
    fun `French accented inverse name is found regardless of case`() {
        assertEquals(2L, reversedModelId(1L to "Basique", 2L to "BASIQUE (AVEC CARTE INVERSÉE)"))
    }

    @Test
    fun `Dutch omgekeerd name is found`() {
        assertEquals(2L, reversedModelId(1L to "Basis", 2L to "Basis (en omgekeerde kaart)"))
    }

    // --- No match ---

    @Test
    fun `empty model list yields no match`() {
        assertNull(reversedModelId())
    }

    @Test
    fun `only non-reversed built-in models yield no match`() {
        assertNull(reversedModelId(1L to "Basic", 2L to "Basic (type in the answer)", 3L to "Cloze"))
    }

    @Test
    fun `unrelated name containing reverse is not matched`() {
        assertNull(reversedModelId(1L to "Basic", 2L to "My Reverse Engineering Notes"))
    }
}
