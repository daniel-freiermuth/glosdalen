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
