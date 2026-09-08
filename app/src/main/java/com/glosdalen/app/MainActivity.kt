/*
 * Glosdalen - Vocabulary lookup with Anki integration
 * Copyright (C) 2025 Glosdalen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.glosdalen.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.glosdalen.app.ui.anki.AnkiApiInfoDialog
import com.glosdalen.app.ui.anki.AnkiApiInfoViewModel
import com.glosdalen.app.ui.search.deepl.DeepLSearchScreen
import com.glosdalen.app.ui.search.copilot_chat.CopilotChatSearchScreen
import com.glosdalen.app.ui.search.copilot_knowledge.CopilotKnowledgeSearchScreen
import com.glosdalen.app.ui.settings.SettingsScreen
import com.glosdalen.app.ui.theme.GlosdalenTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

// Navigation 3 route keys — type-safe replacements for string routes
data object DeepLSearchRoute
data object CopilotChatRoute
data object CopilotKnowledgeRoute
data object SettingsRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GlosdalenTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GlosdalenApp()
                }
            }
        }
    }
}

@Composable
fun GlosdalenApp() {
    val backStack = remember { mutableStateListOf<Any>(DeepLSearchRoute) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ankiInfoViewModel: AnkiApiInfoViewModel = hiltViewModel()
    val ankiInfoState by ankiInfoViewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Derive current route from the back stack (last item)
    val currentRoute = backStack.lastOrNull()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                AppDrawerContent(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        scope.launch { drawerState.close() }
                        // Replace the back stack with a single top-level destination
                        backStack.clear()
                        backStack.add(route)
                    }
                )
            }
        }
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider {
                entry<DeepLSearchRoute> {
                    DeepLSearchScreen(
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNavigateToSettings = {
                            if (backStack.lastOrNull() is DeepLSearchRoute) {
                                backStack.add(SettingsRoute)
                            }
                        }
                    )
                }

                entry<CopilotChatRoute> {
                    CopilotChatSearchScreen(
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNavigateToSettings = {
                            if (backStack.lastOrNull() is CopilotChatRoute) {
                                backStack.add(SettingsRoute)
                            }
                        }
                    )
                }

                entry<CopilotKnowledgeRoute> {
                    CopilotKnowledgeSearchScreen(
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNavigateToSettings = {
                            if (backStack.lastOrNull() is CopilotKnowledgeRoute) {
                                backStack.add(SettingsRoute)
                            }
                        }
                    )
                }

                entry<SettingsRoute> {
                    SettingsScreen(
                        onNavigateBack = {
                            if (backStack.lastOrNull() is SettingsRoute) {
                                backStack.removeLastOrNull()
                            }
                        }
                    )
                }
            }
        )

        // Global Anki API info dialog (shown only when needed)
        if (ankiInfoState.shouldShow) {
            AnkiApiInfoDialog(
                onDismiss = { ankiInfoViewModel.onDismiss() },
                onRemindLater = { ankiInfoViewModel.onDismiss() },
                onOpenAnkiSettings = {
                    // Open Android system app settings for Glosdalen
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
private fun AppDrawerContent(
    currentRoute: Any?,
    onNavigate: (Any) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App title
        Text(
            text = "Glosdalen",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        HorizontalDivider()
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Search providers section
        Text(
            text = "Search",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("DeepL") },
            selected = currentRoute is DeepLSearchRoute,
            onClick = { onNavigate(DeepLSearchRoute) },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Copilot modes section
        Text(
            text = "Copilot",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Star, contentDescription = null) },
            label = { Text("Copilot Language") },
            selected = currentRoute is CopilotChatRoute,
            onClick = { onNavigate(CopilotChatRoute) },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Lightbulb, contentDescription = null) },
            label = { Text("General Knowledge") },
            selected = currentRoute is CopilotKnowledgeRoute,
            onClick = { onNavigate(CopilotKnowledgeRoute) },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        
        // Placeholder for future search providers
        // NavigationDrawerItem(
        //     icon = { Icon(Icons.Default.Translate, contentDescription = null) },
        //     label = { Text("Google Translate") },
        //     selected = currentRoute is GoogleSearchRoute,
        //     onClick = { onNavigate(GoogleSearchRoute) },
        //     modifier = Modifier.padding(horizontal = 12.dp),
        //     badge = { Text("Soon", style = MaterialTheme.typography.labelSmall) }
        // )
    }
}
