package com.glosdalen.app.ui.search

import com.glosdalen.app.libs.copilot.CopilotChat
import com.glosdalen.app.libs.copilot.models.CopilotModel
import com.glosdalen.app.ui.search.components.CopilotSearchUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Loads Copilot's model list into a Copilot search screen's [MutableStateFlow].
 *
 * [withModels] copies the screen's state with the given model-list fields. A failure is
 * reported as `modelsError`, never as the query `error`, so it does not offer a query retry.
 */
internal suspend fun <S : CopilotSearchUiState<*, *>> MutableStateFlow<S>.loadCopilotModels(
    copilot: CopilotChat,
    withModels: S.(models: List<CopilotModel>, isLoading: Boolean, error: String?) -> S
) {
    update { it.withModels(it.availableModels, true, it.modelsError) }
    copilot.getModels().fold(
        onSuccess = { models -> update { it.withModels(models, false, null) } },
        onFailure = { error ->
            update { it.withModels(it.availableModels, false, "Failed to load models: ${error.message}") }
        }
    )
}
