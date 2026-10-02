package com.glosdalen.app.ui.search.components

import com.glosdalen.app.libs.copilot.models.CopilotModel

/** A flashcard proposed by Copilot. */
interface CopilotFlashCardContent {
    val frontSide: String
    val backSide: String
    val note: String
}

/** A parsed Copilot response with proposed flashcards of type [C]. */
interface CopilotParsedResponse<out C : CopilotFlashCardContent> {
    val directAnswer: String
    val cards: List<C>
    val additionalInfo: String
}

/**
 * UI state rendered by [CopilotSearchScreenContent].
 *
 * @param R parsed response type
 * @param D card direction type
 */
interface CopilotSearchUiState<out R : CopilotParsedResponse<*>, out D> {
    val query: String
    val contextQuery: String
    val isContextExpanded: Boolean
    val response: String
    val parsedResponse: R?
    val isLoading: Boolean
    val error: String?
    val isAuthenticated: Boolean
    val isAdditionalInfoExpanded: Boolean
    val isCreatingCard: Boolean
    val createdCardIndices: Set<Int>
    val isAnkiDroidAvailable: Boolean
    val selectedCardDirection: D
    val availableModels: List<CopilotModel>
    val selectedModelId: String
    val showIntroDialog: Boolean
}

/** ViewModel actions invoked by [CopilotSearchScreenContent]. */
interface CopilotSearchActions<in D> {
    fun updateQuery(query: String)
    fun updateContextQuery(context: String)
    fun toggleContextExpanded()
    fun sendQuery()
    fun cancelQuery()
    fun selectModel(modelId: String)
    fun toggleAdditionalInfo()
    fun clearResponse()
    fun dismissIntroDialog(showAgain: Boolean)
    fun recheckAuthenticationStatus()
    fun createAnkiCard(cardIndex: Int)
    fun updateCardDirection(direction: D)
}
