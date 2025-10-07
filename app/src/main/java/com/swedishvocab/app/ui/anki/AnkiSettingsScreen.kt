package com.swedishvocab.app.ui.anki

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.swedishvocab.app.data.model.CardDirection
import com.swedishvocab.app.data.repository.AnkiImplementationType
import com.swedishvocab.app.domain.preferences.AnkiMethodPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnkiIntegrationStatusSection(
    isUsingApi: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isUsingApi) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isUsingApi) Icons.Default.Settings else Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = if (isUsingApi) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isUsingApi) "AnkiDroid API" else "Intent Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isUsingApi) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = if (isUsingApi) 
                            "Full features available including deck selection and bidirectional cards" 
                        else 
                            "Basic card creation using system intents - some features limited",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUsingApi) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun AnkiSettingsScreen(
    navController: NavController,
    viewModel: AnkiSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Anki Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Integration Status
            item {
                AnkiIntegrationStatusSection(
                    isUsingApi = uiState.isUsingApiImplementation
                )
            }
            
            // Method Selection (if both methods are available)
            if (uiState.bothMethodsAvailable) {
                item {
                    MethodSelectionSection(
                        selectedMethod = uiState.selectedMethod,
                        availableMethods = uiState.availableMethods,
                        onMethodSelected = viewModel::selectMethod
                    )
                }
            }
            
            // Deck Selection
            item {
                DeckSelectionSection(
                    currentDeck = uiState.selectedDeckName,
                    availableDecks = uiState.availableDecks,
                    isLoading = uiState.isLoadingDecks,
                    isUsingApi = uiState.isUsingApiImplementation,
                    onDeckSelected = viewModel::selectDeck,
                    onRefreshDecks = viewModel::loadAvailableDecks
                )
            }

            // Status Info
            item {
                AnkiStatusSection(
                    ankiAvailable = uiState.isAnkiAvailable,
                    implementationType = uiState.implementationType,
                    supportsBatch = uiState.supportsBatchOperations
                )
            }
        }
    }
}

@Composable
private fun DeckSelectionSection(
    currentDeck: String,
    availableDecks: Map<Long, String>,
    isLoading: Boolean,
    isUsingApi: Boolean,
    onDeckSelected: (String) -> Unit,
    onRefreshDecks: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = if (!isUsingApi) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .alpha(if (isUsingApi) 1.0f else 0.6f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Anki Deck",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!isUsingApi) {
                        Text(
                            text = "Only available with AnkiDroid API",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (availableDecks.isNotEmpty() && isUsingApi) {
                    TextButton(onClick = onRefreshDecks) {
                        Text("Refresh")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                !isUsingApi -> {
                    Column {
                        Text(
                            text = "Using Intent Mode",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Deck selection happens in AnkiDroid when you create cards. Grant API permission to enable automatic deck management.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                isLoading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Loading decks...")
                    }
                }
                availableDecks.isEmpty() -> {
                    Column {
                        Text("No decks found. Create a deck in AnkiDroid or:")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onRefreshDecks,
                            enabled = isUsingApi
                        ) {
                            Text("Load Available Decks")
                        }
                    }
                }
                else -> {
                    // Current deck display
                    OutlinedTextField(
                        value = currentDeck,
                        onValueChange = onDeckSelected,
                        label = { Text("Deck Name") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Type custom deck name or select below") },
                        enabled = isUsingApi
                    )
                    
                    if (availableDecks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Available Decks:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        availableDecks.values.take(5).forEach { deckName ->
                            TextButton(
                                onClick = { onDeckSelected(deckName) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = isUsingApi
                            ) {
                                Text(
                                    text = deckName,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        
                        if (availableDecks.size > 5) {
                            Text(
                                text = "... and ${availableDecks.size - 5} more decks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnkiStatusSection(
    ankiAvailable: Boolean,
    implementationType: String,
    supportsBatch: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "AnkiDroid Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusColor = if (ankiAvailable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
                
                Text(
                    text = if (ankiAvailable) "✓ Available" else "✗ Not Available",
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }

            if (ankiAvailable) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Implementation: $implementationType",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Batch operations: ${if (supportsBatch) "Supported" else "Not supported"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun MethodSelectionSection(
    selectedMethod: AnkiMethodPreference,
    availableMethods: List<AnkiImplementationType>,
    onMethodSelected: (AnkiMethodPreference) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .selectableGroup()
        ) {
            Text(
                text = "AnkiDroid Integration Method",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = "Both methods are available. Choose your preferred integration method.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // AUTO option
            MethodOption(
                method = AnkiMethodPreference.AUTO,
                selectedMethod = selectedMethod,
                onMethodSelected = onMethodSelected,
                title = "Automatic",
                description = "Let the app choose the best available method (recommended)"
            )

            // API option (if available)
            if (availableMethods.contains(AnkiImplementationType.API)) {
                MethodOption(
                    method = AnkiMethodPreference.API,
                    selectedMethod = selectedMethod,
                    onMethodSelected = onMethodSelected,
                    title = "AnkiDroid API",
                    description = "Direct integration with advanced features like deck selection and batch operations"
                )
            }

            // INTENT option (if available)
            if (availableMethods.contains(AnkiImplementationType.INTENT)) {
                MethodOption(
                    method = AnkiMethodPreference.INTENT,
                    selectedMethod = selectedMethod,
                    onMethodSelected = onMethodSelected,
                    title = "Intent Method",
                    description = "Uses Android sharing system. User can review cards before creation"
                )
            }
        }
    }
}

@Composable
private fun MethodOption(
    method: AnkiMethodPreference,
    selectedMethod: AnkiMethodPreference,
    onMethodSelected: (AnkiMethodPreference) -> Unit,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = (selectedMethod == method),
                onClick = { onMethodSelected(method) }
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = (selectedMethod == method),
            onClick = { onMethodSelected(method) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
