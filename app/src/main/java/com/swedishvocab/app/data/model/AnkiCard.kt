package com.swedishvocab.app.data.model

data class AnkiCard(
    val deckName: String,
    val modelName: String = "Basic",
    val fields: Map<String, String>,
    val tags: List<String> = emptyList()
)

data class GermanSwedishCard(
    val germanWord: String,
    val swedishTranslation: String,
    val exampleSentence: String? = null,
    val notes: String? = null,
    val deckName: String,
    val cardType: CardType = CardType.UNIDIRECTIONAL
) {
    fun toAnkiCards(): List<AnkiCard> {
        val baseFields = mutableMapOf<String, String>()
        val backContent = buildString {
            append(swedishTranslation)
            exampleSentence?.let { 
                append("<br><br><i>$it</i>") 
            }
            notes?.let { 
                append("<br><br>$it") 
            }
        }
        
        val cards = mutableListOf<AnkiCard>()
        
        // German -> Swedish card
        cards.add(
            AnkiCard(
                deckName = deckName,
                fields = mapOf(
                    "Front" to germanWord,
                    "Back" to backContent
                ),
                tags = listOf("german", "swedish", "vocab-app", "de-sv")
            )
        )
        
        // Add reverse card for bidirectional
        if (cardType == CardType.BIDIRECTIONAL) {
            cards.add(
                AnkiCard(
                    deckName = deckName,
                    fields = mapOf(
                        "Front" to swedishTranslation,
                        "Back" to germanWord + (exampleSentence?.let { "<br><br><i>$it</i>" } ?: "") + (notes?.let { "<br><br>$it" } ?: "")
                    ),
                    tags = listOf("german", "swedish", "vocab-app", "sv-de")
                )
            )
        }
        
        return cards
    }
}

enum class CardType {
    UNIDIRECTIONAL,
    BIDIRECTIONAL
}
