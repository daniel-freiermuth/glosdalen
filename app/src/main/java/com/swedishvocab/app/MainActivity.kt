/*
 * Glosdalen - German-Swedish vocabulary lookup with Anki integration
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

package com.swedishvocab.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.swedishvocab.app.ui.search.SearchScreen
import com.swedishvocab.app.ui.settings.SettingsScreen
import com.swedishvocab.app.ui.theme.SwedishVocabAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SwedishVocabAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SwedishVocabApp()
                }
            }
        }
    }
}

@Composable
fun SwedishVocabApp() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "search"
    ) {
        composable("search") {
            SearchScreen(
                onNavigateToSettings = {
                    if (navController.currentDestination?.route == "search") {
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
}
