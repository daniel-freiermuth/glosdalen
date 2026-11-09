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

    // --- Real stock names from Anki's translations (anki-core-i18n core/<lang>/notetypes.ftl) ---

    @Test
    fun `real Spanish Italian and Portuguese names are found`() {
        assertEquals(2L, reversedModelId(1L to "Básico", 2L to "Básico (y tarjeta invertida)"))
        assertEquals(2L, reversedModelId(1L to "Basilare", 2L to "Basilare (e carta inversa)"))
        assertEquals(2L, reversedModelId(1L to "Básico", 2L to "Básico (e cartão invertido)"))
    }

    @Test
    fun `real names without latin keywords are found`() {
        assertEquals(2L, reversedModelId(1L to "Enkel", 2L to "Enkel (och omvänt kort)"))
        assertEquals(2L, reversedModelId(1L to "Простая", 2L to "Простая (с обратной карточкой)"))
        assertEquals(2L, reversedModelId(1L to "基本", 2L to "基本 (表面と裏面を反転したカードも作成)"))
        assertEquals(2L, reversedModelId(1L to "问答题", 2L to "问答题（附翻转卡片）"))
    }

    @Test
    fun `French reversed model is found and the optional reversed model is not taken for it`() {
        assertEquals(
            3L,
            reversedModelId(
                1L to "Basique",
                2L to "Basique (carte inversée optionnelle)",
                3L to "Généralités (deux sens)"
            )
        )
        assertNull(reversedModelId(1L to "Basique", 2L to "Basique (carte inversée optionnelle)"))
    }

    @Test
    fun `German and Dutch optional reversed models listed first are not picked`() {
        assertEquals(
            2L,
            reversedModelId(
                1L to "Einfach (und wahlweise die umgekehrte Richtung)",
                2L to "Einfach (und die umgekehrte Richtung)"
            )
        )
        assertEquals(
            2L,
            reversedModelId(1L to "Basis (omgekeerde kaart optioneel)", 2L to "Basis (en omgekeerde kaart)")
        )
    }

    @Test
    fun `user model containing reversed listed first is not picked`() {
        assertEquals(
            2L,
            reversedModelId(1L to "Reversed Engineering", 2L to "Einfach (und die umgekehrte Richtung)")
        )
        assertNull(reversedModelId(1L to "Basic", 2L to "Reversed Engineering"))
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
