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
}
