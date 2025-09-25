package com.swedishvocab.app.data.model

data class AnkiCard(
    val modelName: String = "Basic",
    val fields: Map<String, String>,
    val tags: List<String> = emptyList()
)

data class GermanSwedishCard(
    val germanWord: String,
    val swedishTranslation: String,
    val exampleSentence: String? = null,
    val notes: String? = null,
    val cardType: CardType = CardType.UNIDIRECTIONAL
) {
    fun toAnkiCards(): AnkiCard {
        val backContent = buildString {
            append(swedishTranslation)
            exampleSentence?.let { 
                append("<br><br><i>$it</i>") 
            }
            notes?.let { 
                append("<br><br>$it") 
            }
        }
        
        // German -> Swedish card
        return AnkiCard(
                fields = mapOf(
                    "Front" to germanWord,
                    "Back" to backContent
                ),
                tags = listOf("german", "swedish", "vocab-app", "de-sv")
            )
    }
}

enum class CardType {
    UNIDIRECTIONAL,
    BIDIRECTIONAL
}
