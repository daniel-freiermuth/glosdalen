package com.glosdalen.app.domain.template

import com.glosdalen.app.backend.deepl.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Locale

class DeckNameTemplateResolverTest {

    private val resolver = DeckNameTemplateResolver()

    // --- Plain string passthrough (no template variables) ---

    @Test
    fun `plain string without variables is returned unchanged`() {
        val result = resolver.resolveDeckName("MyDeck::Vocabulary", Language.GERMAN)
        assertEquals("MyDeck::Vocabulary", result)
    }

    @Test
    fun `empty template string returns empty string`() {
        val result = resolver.resolveDeckName("", Language.GERMAN)
        assertEquals("", result)
    }

    // --- Language variable substitution ---

    @Test
    fun `foreign_native resolves to lowercase native name`() {
        val result = resolver.resolveDeckName("{foreign_native}", Language.GERMAN)
        assertEquals("deutsch", result)
    }

    @Test
    fun `foreign_english resolves to lowercase display name`() {
        val result = resolver.resolveDeckName("{foreign_english}", Language.FRENCH)
        assertEquals("french", result)
    }

    @Test
    fun `Foreign_native resolves to capitalized native name`() {
        val result = resolver.resolveDeckName("{Foreign_native}", Language.GERMAN)
        assertEquals("Deutsch", result)
    }

    @Test
    fun `Foreign_english resolves to capitalized display name`() {
        val result = resolver.resolveDeckName("{Foreign_english}", Language.FRENCH)
        assertEquals("French", result)
    }

    // --- Case sensitivity between lowercase and capitalized variants ---

    @Test
    fun `lowercase and capitalized variants resolve independently`() {
        val template = "{foreign_native} vs {Foreign_native}"
        val result = resolver.resolveDeckName(template, Language.GERMAN)
        assertEquals("deutsch vs Deutsch", result)
    }

    @Test
    fun `variables are case-sensitive - wrong case is not substituted`() {
        // {FOREIGN_NATIVE} is not a recognized variable and should pass through
        val result = resolver.resolveDeckName("{FOREIGN_NATIVE}", Language.GERMAN)
        assertEquals("{FOREIGN_NATIVE}", result)
    }

    // --- Multiple variables in one template ---

    @Test
    fun `template with multiple language variables`() {
        val template = "Glosdalen::{Foreign_native}::{foreign_english}"
        val result = resolver.resolveDeckName(template, Language.GERMAN)
        assertEquals("Glosdalen::Deutsch::german", result)
    }

    @Test
    fun `template with language and date variables`() {
        val now = LocalDate.now()
        val template = "Glosdalen::{foreign_native}::{year}"
        val result = resolver.resolveDeckName(template, Language.GERMAN)
        assertEquals("Glosdalen::deutsch::${now.year}", result)
    }

    // --- Unknown or removed template variables pass through literally ---

    @Test
    fun `unknown variable passes through as literal text`() {
        val result = resolver.resolveDeckName("{foreign_local}", Language.GERMAN)
        assertEquals("{foreign_local}", result)
    }

    @Test
    fun `removed variable foreign_code_native passes through as literal text`() {
        val result = resolver.resolveDeckName("{foreign_code_native}", Language.GERMAN)
        assertEquals("{foreign_code_native}", result)
    }

    @Test
    fun `mix of known and unknown variables - known resolved, unknown literal`() {
        val template = "{Foreign_native}::{unknown_var}"
        val result = resolver.resolveDeckName(template, Language.GERMAN)
        assertEquals("Deutsch::{unknown_var}", result)
    }

    // --- Date template substitution ---

    @Test
    fun `year variable resolves to current year`() {
        val now = LocalDate.now()
        val result = resolver.resolveDeckName("{year}", Language.GERMAN)
        assertEquals(now.year.toString(), result)
    }

    @Test
    fun `month variable resolves to zero-padded current month`() {
        val now = LocalDate.now()
        val result = resolver.resolveDeckName("{month}", Language.GERMAN)
        assertEquals(String.format("%02d", now.monthValue), result)
    }

    @Test
    fun `day variable resolves to zero-padded current day`() {
        val now = LocalDate.now()
        val result = resolver.resolveDeckName("{day}", Language.GERMAN)
        assertEquals(String.format("%02d", now.dayOfMonth), result)
    }

    @Test
    fun `week variable resolves to week of year for the given date`() {
        val result = resolver.resolveDeckName("{week}", Language.GERMAN, LocalDate.of(2025, 10, 1), Locale.GERMANY)
        assertEquals("40", result)
    }

    @Test
    fun `week is never zero for early-January days before the first full week`() {
        // 2027-01-01 (Fri) to 2027-01-03 (Sun) precede the first full week in a Monday-first, minDays=4 locale
        for (day in 1..4) {
            val result = resolver.resolveDeckName("{week}", Language.GERMAN, LocalDate.of(2027, 1, day), Locale.GERMANY)
            assertEquals("1", result, "2027-01-0$day")
        }
    }

    @Test
    fun `year and week stay in the same calendar year at the year boundary`() {
        val template = "{year}-W{week}"
        // Late December stays in the closing year's last week instead of colliding with January's week 1
        assertEquals("2025-W53", resolver.resolveDeckName(template, Language.GERMAN, LocalDate.of(2025, 12, 29), Locale.GERMANY))
        assertEquals("2025-W53", resolver.resolveDeckName(template, Language.GERMAN, LocalDate.of(2025, 12, 28), Locale.US))
        // Early January belongs to the new year's week 1 instead of colliding with the next December's last week
        assertEquals("2028-W1", resolver.resolveDeckName(template, Language.GERMAN, LocalDate.of(2028, 1, 1), Locale.GERMANY))
        assertEquals("2028-W52", resolver.resolveDeckName(template, Language.GERMAN, LocalDate.of(2028, 12, 25), Locale.GERMANY))
    }

    @Test
    fun `week numbering follows the locale's first day of week`() {
        // 2027-01-10 is a Sunday: still week 1 in Germany (Monday-first), already week 3 in the US (Sunday-first)
        val date = LocalDate.of(2027, 1, 10)
        assertEquals("1", resolver.resolveDeckName("{week}", Language.GERMAN, date, Locale.GERMANY))
        assertEquals("3", resolver.resolveDeckName("{week}", Language.GERMAN, date, Locale.US))
    }

    @Test
    fun `all date variables resolve in one template`() {
        val template = "{year}-{month}-{day} W{week}"
        val result = resolver.resolveDeckName(template, Language.GERMAN, LocalDate.of(2025, 3, 7), Locale.GERMANY)
        assertEquals("2025-03-07 W10", result)
    }

    // --- Whitespace handling ---

    @Test
    fun `template with leading and trailing whitespace is trimmed`() {
        val result = resolver.resolveDeckName("  Glosdalen::Deck  ", Language.GERMAN)
        assertEquals("Glosdalen::Deck", result)
    }

    @Test
    fun `template with whitespace around separator is preserved internally`() {
        // Internal whitespace around :: is not stripped by resolveDeckName;
        // downstream AnkiApiRepository validates this
        val result = resolver.resolveDeckName("Glosdalen :: {Foreign_native}", Language.GERMAN)
        assertEquals("Glosdalen :: Deutsch", result)
    }

    // --- Non-ASCII language names ---

    @Test
    fun `non-ASCII native name resolves correctly`() {
        val result = resolver.resolveDeckName("{Foreign_native}", Language.JAPANESE)
        assertEquals("日本語", result)
    }

    @Test
    fun `non-ASCII native name lowercased resolves correctly`() {
        val result = resolver.resolveDeckName("{foreign_native}", Language.JAPANESE)
        assertEquals("日本語", result)
    }

    // --- Repeated variable in same template ---

    @Test
    fun `same variable used twice is resolved in both positions`() {
        val template = "{foreign_native}::{foreign_native}"
        val result = resolver.resolveDeckName(template, Language.GERMAN)
        assertEquals("deutsch::deutsch", result)
    }
}
