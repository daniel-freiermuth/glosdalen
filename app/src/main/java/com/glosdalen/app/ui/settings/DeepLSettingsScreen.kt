@file:OptIn(ExperimentalMaterial3Api::class)

package com.glosdalen.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.glosdalen.app.backend.deepl.DeepLModelType
import com.glosdalen.app.backend.deepl.Language

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepLSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DeepLSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Handle Android's native back button/gesture - navigate back
    BackHandler {
        onNavigateBack()
    }
    
    SettingsScreenScaffold(
        title = { Text("DeepL Settings") },
        onNavigateBack = onNavigateBack
    ) {
        // DeepL API Configuration Section
        DeepLSettingsSection(
            uiState = uiState,
            onApiKeyChange = viewModel::updateApiKey,
            onValidateApiKey = viewModel::validateAndSaveApiKey,
            onModelTypeChange = viewModel::updateDeepLModelType,
            onEnableMultipleFormalitiesChange = viewModel::updateEnableMultipleFormalities,
            onFrontPreferenceChange = viewModel::updateFrontPreference
        )
    }
}
