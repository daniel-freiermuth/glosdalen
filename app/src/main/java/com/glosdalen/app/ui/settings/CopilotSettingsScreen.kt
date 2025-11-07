@file:OptIn(ExperimentalMaterial3Api::class)

package com.glosdalen.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import com.glosdalen.app.domain.preferences.CopilotPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopilotSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLanguageInstructions: () -> Unit = {},
    viewModel: CopilotSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedModelId by viewModel.selectedModel.collectAsState()
    val generalInstructions by viewModel.generalInstructions.collectAsState()
    val temperature = uiState.temperature
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Automatically resume polling when app comes back to foreground
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME, lifecycleOwner) {
        viewModel.onAppResumed()
    }
    
    // Handle Android's native back button/gesture
    BackHandler {
        onNavigateBack()
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("GitHub Copilot") },
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
            // Error message
            uiState.errorMessage?.let { error ->
                ErrorCard(
                    message = error,
                    onDismiss = viewModel::clearError
                )
            }
            
            // Main content based on auth state
            when (val state = uiState.authState) {
                is AuthState.NotAuthenticated -> {
                    NotAuthenticatedSection(
                        onSignIn = viewModel::startAuthentication
                    )
                }
                
                is AuthState.RequestingDeviceCode -> {
                    LoadingSection(message = "Requesting device code...")
                }
                
                is AuthState.WaitingForUser -> {
                    DeviceCodeSection(
                        userCode = state.userCode,
                        verificationUri = state.verificationUri,
                        expiresIn = state.expiresIn,
                        isPolling = state.isPolling,
                        onCancel = viewModel::cancelAuthentication,
                        onOpenGitHub = viewModel::startPolling
                    )
                }
                
                is AuthState.Authenticated -> {
                    AuthenticatedSection(
                        onLogout = viewModel::logout,
                        onRefresh = viewModel::checkAuthenticationStatus
                    )
                    
                    // General Instructions Section
                    GeneralInstructionsSection(
                        instructions = generalInstructions,
                        onInstructionsChange = viewModel::updateGeneralInstructions,
                        onResetToDefault = viewModel::resetToDefaultInstructions
                    )
                    
                    // Language-Specific Instructions Section
                    LanguageInstructionsNavigationCard(
                        onNavigate = onNavigateToLanguageInstructions
                    )
                    
                    // Temperature Section
                    TemperatureSection(
                        temperature = temperature,
                        onTemperatureChange = viewModel::updateTemperature
                    )
                    
                    // Model Selection Section
                    ModelSelectionSection(
                        availableModels = uiState.availableModels,
                        selectedModelId = selectedModelId,
                        isLoading = uiState.isLoadingModels,
                        onModelSelect = viewModel::selectModel,
                        onRefreshModels = viewModel::refreshModels
                    )
                }
                
                is AuthState.Error -> {
                    ErrorSection(
                        onRetry = viewModel::startAuthentication,
                        onCancel = viewModel::cancelAuthentication
                    )
                }
            }
            
            // Info section
            CopilotInfoSection()
        }
    }
}

@Composable
private fun NotAuthenticatedSection(
    onSignIn: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "GitHub Copilot",
                style = MaterialTheme.typography.titleMedium
            )
            
            Text(
                text = "Sign in to use GitHub Copilot AI assistance for vocabulary translation suggestions and explanations.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Button(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign in with GitHub")
            }
        }
    }
}

@Composable
private fun DeviceCodeSection(
    userCode: String,
    verificationUri: String,
    expiresIn: Int,
    isPolling: Boolean = false,
    onCancel: () -> Unit,
    onOpenGitHub: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var codeCopied by remember { mutableStateOf(false) }
    
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Authorize GitHub Copilot",
                style = MaterialTheme.typography.titleMedium
            )
            
            Text(
                text = "1. Copy the code below",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            
            // User code with copy button
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = userCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(userCode))
                            codeCopied = true
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        if (codeCopied) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Copied",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copied")
                        } else {
                            Text("Copy Code")
                        }
                    }
                }
            }
            
            Text(
                text = "2. Open GitHub and paste the code",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(verificationUri))
                    context.startActivity(intent)
                    onOpenGitHub() // Start polling after opening browser
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open GitHub")
            }
            
            // Show polling indicator when waiting for authorization
            if (isPolling) {
                HorizontalDivider()
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Waiting for authorization...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            HorizontalDivider()
            
            Text(
                text = "Code expires in: ${expiresIn / 60} minutes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun LoadingSection(message: String) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AuthenticatedSection(
    onLogout: () -> Unit,
    onRefresh: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Connected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "You are authenticated with GitHub Copilot",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Authenticated",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            HorizontalDivider()
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refresh")
                }
                
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Sign Out")
                }
            }
        }
    }
}

@Composable
private fun ErrorSection(
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Authentication Failed",
                style = MaterialTheme.typography.titleMedium
            )
            
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Try Again")
            }
            
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun CopilotInfoSection() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "About GitHub Copilot",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "GitHub Copilot uses AI to provide intelligent suggestions and help with vocabulary translation. You need a GitHub account with Copilot access to use this feature.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LanguageInstructionsNavigationCard(
    onNavigate: () -> Unit
) {
    Card(
        onClick = onNavigate,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Language-Specific Instructions",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Customize instructions for each language pair to control translation style, formality, and context.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Navigate to language instructions",
                modifier = Modifier.rotate(180f)
            )
        }
    }
}

@Composable
private fun GeneralInstructionsSection(
    instructions: String,
    onInstructionsChange: (String) -> Unit,
    onResetToDefault: () -> Unit
) {
    var textFieldValue by remember(instructions) { 
        mutableStateOf(TextFieldValue(text = instructions, selection = TextRange(instructions.length)))
    }
    
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "General Instructions",
                    style = MaterialTheme.typography.titleMedium
                )
                
                TextButton(onClick = {
                    val defaultText = CopilotPreferences.DEFAULT_INSTRUCTIONS
                    val currentText = textFieldValue.text
                    val newText = if (currentText.isBlank()) {
                        defaultText
                    } else {
                        "$currentText\n\n$defaultText"
                    }
                    textFieldValue = TextFieldValue(
                        text = newText,
                        selection = TextRange(newText.length)
                    )
                    onInstructionsChange(newText)
                }) {
                    Text("Add Default")
                }
            }
            
            Text(
                text = "Provide general instructions that will be included in every Copilot query. This helps tailor responses to your learning style and preferences.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { 
                    textFieldValue = it
                    onInstructionsChange(it.text)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Instructions") },
                placeholder = { Text("E.g., \"Focus on formal usage\" or \"Include example sentences\"") },
                minLines = 3,
                maxLines = 6,
                supportingText = {
                    Text(
                        text = "These instructions will guide how Copilot responds to your translation queries.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            )
        }
    }
}

@Composable
private fun TemperatureSection(
    temperature: Float,
    onTemperatureChange: (Float) -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Temperature",
                style = MaterialTheme.typography.titleMedium
            )
            
            Text(
                text = "Controls randomness in AI responses. Lower values (0.0) produce deterministic, focused answers. Higher values (1.0) produce creative, varied responses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
                
                Slider(
                    value = temperature,
                    onValueChange = onTemperatureChange,
                    modifier = Modifier.weight(1f),
                    valueRange = 0f..1f,
                    steps = 9 // Creates 11 total positions (0.0, 0.1, 0.2, ... 1.0)
                )
                
                Text(
                    text = "1.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            Text(
                text = "Current: %.1f".format(temperature),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Text(
                text = when {
                    temperature < 0.3f -> "Very focused and deterministic"
                    temperature < 0.6f -> "Balanced with slight variation"
                    temperature < 0.8f -> "Creative with good consistency (recommended)"
                    else -> "Highly creative and varied"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ModelSelectionSection(
    availableModels: List<com.glosdalen.app.libs.copilot.models.CopilotModel>,
    selectedModelId: String,
    isLoading: Boolean,
    onModelSelect: (String) -> Unit,
    onRefreshModels: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Model",
                    style = MaterialTheme.typography.titleMedium
                )
                
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onRefreshModels) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh models",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            Text(
                text = "Select which AI model to use for vocabulary assistance",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Model Dropdown
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && availableModels.isNotEmpty()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val modelName = when (selectedModelId) {
                                com.glosdalen.app.domain.preferences.CopilotPreferences.AUTO_MODEL -> "Auto (Recommended)"
                                else -> availableModels.find { it.id == selectedModelId }?.getDisplayName() ?: selectedModelId
                            }
                            
                            Text(
                                text = modelName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            
                            if (selectedModelId != com.glosdalen.app.domain.preferences.CopilotPreferences.AUTO_MODEL) {
                                val model = availableModels.find { it.id == selectedModelId }
                                model?.let {
                                    Text(
                                        text = "${it.vendor} • ${it.getCostIndicator()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Text(
                                    text = "Automatically select the best available model",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select model"
                        )
                    }
                }
                
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    // Auto option
                    DropdownMenuItem(
                        text = {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Auto (Recommended)",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (selectedModelId == com.glosdalen.app.domain.preferences.CopilotPreferences.AUTO_MODEL) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    if (selectedModelId == com.glosdalen.app.domain.preferences.CopilotPreferences.AUTO_MODEL) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Let the system choose the best model",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onModelSelect(com.glosdalen.app.domain.preferences.CopilotPreferences.AUTO_MODEL)
                            expanded = false
                        }
                    )
                    
                    if (availableModels.isNotEmpty()) {
                        HorizontalDivider()
                    }
                    
                    // Available models
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = model.getDisplayName(),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (model.id == selectedModelId) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        if (model.id == selectedModelId) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${model.vendor} • ${model.getCostIndicator()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onModelSelect(model.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
