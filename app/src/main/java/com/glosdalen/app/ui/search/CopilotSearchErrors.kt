package com.glosdalen.app.ui.search

import com.glosdalen.app.libs.copilot.CopilotException

/**
 * User-facing error messages shared by the Copilot search modes (Chat and Knowledge).
 */
internal object CopilotSearchErrors {

    /**
     * Message for a failed Copilot chat request.
     */
    fun queryFailure(error: Throwable): String = when (error) {
        is CopilotException.AuthException.InvalidToken ->
            "Please sign in to GitHub Copilot in Settings"
        is CopilotException.AuthException.TokenExpired ->
            "Session expired. Please sign in again in Settings"
        is CopilotException.NetworkException.NoConnection ->
            "No internet connection. Please check your network."
        is CopilotException.NetworkException.Timeout ->
            "Request timed out. Please try again."
        is CopilotException.NetworkException.RateLimited ->
            "Rate limited. Please try again later."
        else -> error.message ?: "Failed to get response from Copilot"
    }

    /**
     * Message for a failed Anki card creation.
     */
    fun ankiFailure(error: Throwable): String {
        val message = error.message ?: return "Failed to create Anki card"
        return when {
            message.contains("permission", ignoreCase = true) ->
                "AnkiDroid permission required. Please grant access in settings."
            message.contains("not installed", ignoreCase = true) ->
                "AnkiDroid is not installed. Please install it from the Play Store."
            message.contains("deck", ignoreCase = true) ->
                "Failed to create deck. Please check AnkiDroid settings."
            message.contains("model", ignoreCase = true) ||
            message.contains("reversed", ignoreCase = true) ->
                "Card type not found. Please open AnkiDroid first to initialize note types."
            else -> "Error when creating card: $message"
        }
    }
}
