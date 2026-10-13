package com.glosdalen.app.ui.search.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glosdalen.app.backend.anki.AnkiError
import com.glosdalen.app.backend.anki.toCardCreationErrorMessage
import com.glosdalen.app.ui.search.copilot_chat.CopilotCardDirection
import com.glosdalen.app.ui.search.copilot_chat.CopilotChatUiState
import com.glosdalen.app.ui.search.copilot_chat.FlashCard
import com.glosdalen.app.ui.search.copilot_chat.ParsedCopilotResponse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CopilotSearchScreenContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val parsedResponse = ParsedCopilotResponse(
        directAnswer = "dog",
        cards = listOf(FlashCard(frontSide = "Hund", backSide = "dog")),
        additionalInfo = ""
    )

    /** An "Add to Anki" failure must not be offered as a Copilot query retry. */
    @Test
    fun ankiFailureKeepsFlashcardsAndDoesNotOfferQueryRetry() {
        val message = AnkiError.PermissionDenied("permission denied").toCardCreationErrorMessage()
        setContent(
            CopilotChatUiState(
                query = "Hund",
                response = "{}",
                parsedResponse = parsedResponse,
                isAuthenticated = true,
                isAnkiDroidAvailable = true,
                cardError = message
            )
        )

        composeRule.onNodeWithText(message).assertExists()
        composeRule.onAllNodesWithText("Try Again", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("Re-query (Auto)").assertExists()
    }

    /** A model-list failure must not hide the Send button for a query not yet sent. */
    @Test
    fun modelListFailureKeepsSendButton() {
        val message = "Failed to load models: timeout"
        setContent(
            CopilotChatUiState(
                query = "Hund",
                isAuthenticated = true,
                modelsError = message
            )
        )

        composeRule.onNodeWithText(message).assertExists()
        composeRule.onAllNodesWithText("Try Again", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("Send (Auto)").assertExists()
    }

    private fun setContent(uiState: CopilotChatUiState) {
        composeRule.setContent {
            CopilotSearchScreenContent(
                uiState = uiState,
                actions = NoOpActions,
                title = "Copilot Language",
                introDescription = "",
                introFeatures = emptyList(),
                introDisclaimer = "",
                queryLabel = "Query",
                queryPlaceholder = "",
                contextPlaceholder = "",
                cardDirections = CopilotCardDirection.entries,
                cardDirectionButtonLabel = { it.name },
                cardDirectionItemLabel = { it.name },
                onOpenDrawer = {},
                onNavigateToSettings = {}
            )
        }
    }

    private object NoOpActions : CopilotSearchActions<CopilotCardDirection> {
        override fun updateQuery(query: String) = Unit
        override fun updateContextQuery(context: String) = Unit
        override fun toggleContextExpanded() = Unit
        override fun sendQuery() = Unit
        override fun cancelQuery() = Unit
        override fun selectModel(modelId: String) = Unit
        override fun toggleAdditionalInfo() = Unit
        override fun clearResponse() = Unit
        override fun dismissIntroDialog(showAgain: Boolean) = Unit
        override fun recheckAuthenticationStatus() = Unit
        override fun createAnkiCard(cardIndex: Int) = Unit
        override fun updateCardDirection(direction: CopilotCardDirection) = Unit
    }
}
