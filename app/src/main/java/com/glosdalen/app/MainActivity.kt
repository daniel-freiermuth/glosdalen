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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
 import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.glosdalen.app.ui.anki.AnkiApiInfoDialog
import com.glosdalen.app.ui.anki.AnkiApiInfoViewModel
import com.glosdalen.app.ui.search.deepl.DeepLSearchScreen
import com.glosdalen.app.ui.search.copilot_chat.CopilotChatSearchScreen
import com.glosdalen.app.ui.settings.SettingsScreen
import com.glosdalen.app.ui.theme.GlosdalenTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ankiInfoViewModel: AnkiApiInfoViewModel = hiltViewModel()
    val ankiInfoState by ankiInfoViewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Observe current route as state
    val currentRoute by navController.currentBackStackEntryFlow
        .collectAsState(initial = navController.currentBackStackEntry)
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                AppDrawerContent(
                    currentRoute = currentRoute?.destination?.route,
                    onNavigate = { route ->
                        scope.launch { drawerState.close() }
                        navController.navigate(route) {
                            // Clear back stack and navigate to the selected item
                            popUpTo(0) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = "deepl-search"
        ) {
            composable("deepl-search") {
                DeepLSearchScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNavigateToSettings = {
                        if (navController.currentDestination?.route == "deepl-search") {
                            navController.navigate("settings") {
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
            
            composable("copilot-chat") {
                CopilotChatSearchScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNavigateToSettings = {
                        if (navController.currentDestination?.route == "copilot-chat") {
                            navController.navigate("settings") {
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
            
            composable("settings") {
                SettingsScreen(
                    onNavigateBack = {
                        if (navController.currentDestination?.route == "settings") {
                            navController.popBackStack()
                        }
                    }
                )
            }
        }

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
    currentRoute: String?,
    onNavigate: (String) -> Unit,
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
            selected = currentRoute == "deepl-search",
            onClick = { onNavigate("deepl-search") },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Star, contentDescription = null) },
            label = { Text("Copilot Chat") },
            selected = currentRoute == "copilot-chat",
            onClick = { onNavigate("copilot-chat") },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        
        // Placeholder for future search providers
        // NavigationDrawerItem(
        //     icon = { Icon(Icons.Default.Translate, contentDescription = null) },
        //     label = { Text("Google Translate") },
        //     selected = currentRoute == "google-search",
        //     onClick = { onNavigate("google-search") },
        //     modifier = Modifier.padding(horizontal = 12.dp),
        //     badge = { Text("Soon", style = MaterialTheme.typography.labelSmall) }
        // )
    }
}
