@file:OptIn(ExperimentalMaterial3Api::class)

package com.glosdalen.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.glosdalen.app.backend.deepl.DeepLModelType
import com.glosdalen.app.backend.deepl.Language

@Composable
fun DeepLSettingsSection(
    uiState: SettingsUiState,
    onApiKeyChange: (String) -> Unit,
    onValidateApiKey: () -> Unit,
    onModelTypeChange: (DeepLModelType) -> Unit,
    onEnableMultipleFormalitiesChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "DeepL API Configuration",
                style = MaterialTheme.typography.titleMedium
            )
            
            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("DeepL API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = uiState.apiKeyError != null,
                supportingText = {
                    uiState.apiKeyError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                trailingIcon = {
                    if (uiState.apiKeyValidated) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Validated",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
            
            Button(
                onClick = onValidateApiKey,
                enabled = !uiState.isValidatingApiKey && uiState.apiKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isValidatingApiKey) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Validating...")
                    }
                } else {
                    Text("Validate API Key")
                }
            }
            
            // Clickable API key help link
            val linkText = buildAnnotatedString {
                append("Get your free API key at ")
                
                withLink(
                    LinkAnnotation.Url("https://www.deepl.com/pro-api")
                ) {
                    withStyle(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append("deepl.com/pro-api")
                    }
                }
            }
            
            Text(
                text = linkText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // DeepL Model Type
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Translation Quality:",
                    style = MaterialTheme.typography.bodyMedium
                )
                ModelTypeDropdown(
                    selectedModelType = uiState.deepLModelType,
                    onModelTypeSelected = onModelTypeChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Multiple Formalities Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Multiple Translation Suggestions",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Query different formality levels for supported languages (slower but more options)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = uiState.enableMultipleFormalities,
                    onCheckedChange = onEnableMultipleFormalitiesChange
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelTypeDropdown(
    selectedModelType: DeepLModelType,
    onModelTypeSelected: (DeepLModelType) -> Unit,
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
            value = selectedModelType.displayName,
            onValueChange = { },
            label = { Text("Quality") },
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
            DeepLModelType.values().forEach { modelType ->
                DropdownMenuItem(
                    onClick = {
                        onModelTypeSelected(modelType)
                        expanded = false
                    },
                    text = { 
                        Column {
                            Text(
                                text = modelType.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = modelType.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }
    }
}

