@file:OptIn(ExperimentalMaterial3Api::class)

package com.swedishvocab.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.swedishvocab.app.data.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val apiKey by viewModel.deepLApiKey.collectAsState("")
    
    // Handle card creation result
    uiState.cardCreationResult?.let { result ->
        LaunchedEffect(result) {
            if (result.isSuccess) {
                // TODO: Show success snackbar
                println("SUCCESS: Card created successfully!")
            } else {
                // TODO: Show error snackbar
                val error = result.exceptionOrNull()
                println("ERROR: Card creation failed - $error")
            }
            viewModel.clearCardCreationResult()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Swedish Vocab",
                style = MaterialTheme.typography.headlineMedium
            )
            
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
        
        // Configuration Warning
        if (apiKey.isBlank()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "⚠️ Configuration Required",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Please configure your DeepL API key in Settings to use translation features.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Go to Settings")
                    }
                }
            }
        }
        
        // Search Section
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Language Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(uiState.sourceLanguage.displayName)
                    
                    IconButton(
                        onClick = {
                            val newLanguage = when (uiState.sourceLanguage) {
                                Language.GERMAN -> Language.SWEDISH
                                Language.SWEDISH -> Language.GERMAN
                            }
                            viewModel.updateSourceLanguage(newLanguage)
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Swap languages")
                    }
                    
                    Text(
                        when (uiState.sourceLanguage) {
                            Language.GERMAN -> Language.SWEDISH.displayName
                            Language.SWEDISH -> Language.GERMAN.displayName
                        }
                    )
                }
                
                // Search Input
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    label = { Text("Enter ${uiState.sourceLanguage.displayName} word") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                viewModel.searchWord()
                            }
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            viewModel.searchWord()
                        }
                    )
                )
            }
        }
        
        // Results Section
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        
        uiState.error?.let { error ->
            ErrorCard(
                error = error,
                onRetry = viewModel::retrySearch,
                onDismiss = viewModel::clearError
            )
        }
        
        uiState.translationResult?.let { result ->
            TranslationCard(
                vocabularyEntry = result,
                isAnkiDroidAvailable = uiState.isAnkiDroidAvailable,
                isCreatingCard = uiState.isCreatingCard,
                onCreateCard = viewModel::createAnkiCard,
                onTranslationClick = { translationText ->
                    // Set the translation as new search query and reverse language
                    viewModel.updateSearchQuery(translationText)
                    val newLanguage = when (result.sourceLanguage) {
                        Language.GERMAN -> Language.SWEDISH
                        Language.SWEDISH -> Language.GERMAN
                    }
                    viewModel.updateSourceLanguage(newLanguage)
                    // Trigger new search
                    viewModel.searchWord()
                }
            )
        }
    }
}

@Composable
private fun ErrorCard(
    error: VocabularyError,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when (error) {
                    is VocabularyError.NetworkError -> "Network error"
                    is VocabularyError.InvalidApiKey -> "Invalid API key"
                    is VocabularyError.ApiLimitExceeded -> "API limit exceeded"
                    is VocabularyError.ApiError -> "API error"
                    is VocabularyError.UnsupportedLanguagePair -> "Unsupported language pair"
                    is VocabularyError.UnknownError -> "Unknown error"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Text(
                text = when (error) {
                    is VocabularyError.NetworkError -> "Check your internet connection"
                    is VocabularyError.InvalidApiKey -> "Please check your DeepL API key in settings"
                    is VocabularyError.ApiLimitExceeded -> "Try again later"
                    else -> "Please try again"
                },
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onRetry) {
                    Text("Retry")
                }
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun TranslationCard(
    vocabularyEntry: VocabularyEntry,
    isAnkiDroidAvailable: Boolean,
    isCreatingCard: Boolean,
    onCreateCard: () -> Unit,
    onTranslationClick: (String) -> Unit
) {
    
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Translation",
                style = MaterialTheme.typography.titleMedium
            )
            
            // Original word
            Text(
                text = vocabularyEntry.originalWord,
                style = MaterialTheme.typography.headlineSmall
            )
            
            // Translation
            vocabularyEntry.translations.forEach { translation ->
                Text(
                    text = "→ ${translation.text}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .clickable { onTranslationClick(translation.text) }
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            HorizontalDivider()
            
            // Create card button
            Button(
                onClick = { onCreateCard() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCreatingCard && isAnkiDroidAvailable
            ) {
                if (isCreatingCard) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Creating...")
                    }
                } else {
                    Text("Create Anki Card")
                }
            }
            
            if (!isAnkiDroidAvailable) {
                Text(
                    text = "AnkiDroid not installed. Please install AnkiDroid to create cards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
