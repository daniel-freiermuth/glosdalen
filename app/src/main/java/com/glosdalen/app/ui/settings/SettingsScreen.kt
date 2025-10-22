@file:OptIn(ExperimentalMaterial3Api::class)

package com.glosdalen.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.style.TextAlign
import com.glosdalen.app.BuildConfig
import com.glosdalen.app.backend.anki.AnkiImplementationType
import com.glosdalen.app.backend.anki.CardDirection
import com.glosdalen.app.domain.preferences.AnkiMethodPreference
import com.glosdalen.app.backend.deepl.DeepLModelType
import com.glosdalen.app.backend.deepl.Language
import com.glosdalen.app.backend.deepl.SearchContext
import com.glosdalen.app.domain.template.DeckNameTemplateResolver
import com.glosdalen.app.ui.anki.AnkiSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    // Internal navigation state for sub-pages
    var currentPage by remember { mutableStateOf<SettingsPage>(SettingsPage.Main) }
    
    // Each page gets its own ViewModel instance to ensure fresh state
    when (currentPage) {
        SettingsPage.Main -> {
            MainSettingsScreen(
                onNavigateBack = onNavigateBack,
                onNavigateToDeepL = { currentPage = SettingsPage.DeepL },
                onNavigateToAnki = { currentPage = SettingsPage.Anki },
                onNavigateToCopilot = { currentPage = SettingsPage.Copilot }
            )
        }
        SettingsPage.DeepL -> {
            DeepLSettingsScreen(
                onNavigateBack = { currentPage = SettingsPage.Main }
            )
        }
        SettingsPage.Anki -> {
            AnkiSettingsScreen(
                onNavigateBack = { currentPage = SettingsPage.Main }
            )
        }
        SettingsPage.Copilot -> {
            CopilotSettingsScreen(
                onNavigateBack = { currentPage = SettingsPage.Main }
            )
        }
    }
}

private enum class SettingsPage {
    Main,
    DeepL,
    Anki,
    Copilot
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDeepL: () -> Unit,
    onNavigateToAnki: () -> Unit,
    onNavigateToCopilot: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Handle Android's native back button/gesture
    BackHandler {
        onNavigateBack()
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Settings") },
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
            // Language Configuration Section (General Setting)
            LanguageConfigurationSection(
                selectedLanguage = uiState.nativeLanguage,
                onLanguageSelected = viewModel::updateNativeLanguage
            )
            
            // DeepL Settings Navigation Card
            SettingsNavigationCard(
                title = "DeepL",
                description = "API key and translation quality settings",
                onClick = onNavigateToDeepL
            )
            
            // AnkiDroid Settings Navigation Card
            SettingsNavigationCard(
                title = "AnkiDroid",
                description = "Integration method, deck selection, and card templates",
                onClick = onNavigateToAnki
            )
            
            // GitHub Copilot Settings Navigation Card
            SettingsNavigationCard(
                title = "GitHub Copilot",
                description = "AI-powered translation suggestions and assistance",
                onClick = onNavigateToCopilot
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // About section
            AboutSection()
        }
    }
}

@Composable
private fun SettingsNavigationCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun LanguageConfigurationSection(
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Language Configuration",
                style = MaterialTheme.typography.titleMedium
            )
            
            // Native Language
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Native Language:",
                    style = MaterialTheme.typography.bodyMedium
                )
                LanguageDropdown(
                    selectedLanguage = selectedLanguage,
                    onLanguageSelected = onLanguageSelected,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            readOnly = true,
            value = selectedLanguage.displayName,
            onValueChange = { },
            label = { Text("Language") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Language.values().sortedBy { it.displayName }.forEach { language ->
                DropdownMenuItem(
                    onClick = {
                        onLanguageSelected(language)
                        expanded = false
                    },
                    text = { Text(language.displayName) }
                )
            }
        }
    }
}

@Composable
private fun AboutSection() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Version info
                val versionText = remember { "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" }
                Text(
                    text = versionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Git hash
                val buildText = remember { "Build: ${BuildConfig.GIT_HASH}" }
                Text(
                    text = buildText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Build date
                val buildDateText = remember { "Built: ${BuildConfig.BUILD_DATE}" }
                Text(
                    text = buildDateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Developed with ♥ by Erik and Daniel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
