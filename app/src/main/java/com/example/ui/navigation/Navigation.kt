package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object CameraDetection : Screen("camera_detection")
    object Chat : Screen("chat")
    object Settings : Screen("settings")
}
