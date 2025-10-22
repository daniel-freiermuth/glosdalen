@file:OptIn(ExperimentalMaterial3Api::class)

package com.glosdalen.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("DeepL Settings") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // DeepL API Configuration Section
            DeepLSettingsSection(
                uiState = uiState,
                onApiKeyChange = viewModel::updateApiKey,
                onValidateApiKey = viewModel::validateAndSaveApiKey,
                onModelTypeChange = viewModel::updateDeepLModelType,
                onEnableMultipleFormalitiesChange = viewModel::updateEnableMultipleFormalities
            )
        }
    }
}
