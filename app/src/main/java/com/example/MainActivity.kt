package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.navigation.Screen
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.UserViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VirtualAppNavigationHost()
                }
            }
        }
    }
}

@Composable
fun VirtualAppNavigationHost() {
    val navController = rememberNavController()
    val userViewModel: UserViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        // Login Screen
        composable(Screen.Login.route) {
            LoginScreen(
                userViewModel = userViewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        // Registration Screen
        composable(Screen.Register.route) {
            RegisterScreen(
                userViewModel = userViewModel,
                onNavigateBackToLogin = {
                    navController.popBackStack()
                }
            )
        }

        // Home Dashboard Logs
        composable(Screen.Home.route) {
            HomeScreen(
                userViewModel = userViewModel,
                chatViewModel = chatViewModel,
                onNavigateToScan = {
                    navController.navigate(Screen.CameraDetection.route)
                },
                onNavigateToChat = {
                    navController.navigate(Screen.Chat.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        // Face scanner view
        composable(Screen.CameraDetection.route) {
            CameraDetectionScreen(
                userViewModel = userViewModel,
                chatViewModel = chatViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToChat = {
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(Screen.CameraDetection.route) { inclusive = true }
                    }
                }
            )
        }

        // Active chat screen
        composable(Screen.Chat.route) {
            ChatScreen(
                userViewModel = userViewModel,
                chatViewModel = chatViewModel,
                onNavigateBack = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Chat.route) { inclusive = true }
                    }
                }
            )
        }

        // Personalization settings screen
        composable(Screen.Settings.route) {
            SettingsScreen(
                userViewModel = userViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
