package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.User
import com.example.data.repository.VirtualFriendRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: VirtualFriendRepository
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _registrationSuccess = MutableStateFlow<Boolean>(false)
    val registrationSuccess: StateFlow<Boolean> = _registrationSuccess.asStateFlow()

    private val _apiKeys = MutableStateFlow<Map<String, String>>(emptyMap())
    val apiKeys: StateFlow<Map<String, String>> = _apiKeys.asStateFlow()

    private val _friendName = MutableStateFlow("Aura")
    val friendName: StateFlow<String> = _friendName.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = VirtualFriendRepository(database.userDao(), database.chatDao())

        // Check if there is an active Firebase user session to perform automatic direct login
        val fbUser = firebaseAuth.currentUser
        if (fbUser != null) {
            val email = fbUser.email ?: ""
            val username = if (email.endsWith("@virtualfriend.com")) {
                email.substringBefore("@virtualfriend.com")
            } else {
                email
            }
            if (username.isNotEmpty()) {
                viewModelScope.launch {
                    var localUser = repository.getUserByUsername(username)
                    if (localUser == null) {
                        val fallbackName = username.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        val newUser = User(
                            username = username,
                            passwordHash = "",
                            displayName = fallbackName,
                            preferredPersonality = "Supportive"
                        )
                        val id = repository.insertUser(newUser)
                        localUser = newUser.copy(id = id.toInt())
                    }
                    _currentUser.value = localUser
                    _friendName.value = localUser.displayName.ifEmpty { "Aura" }
                }
            }
        }
    }

    fun login(username: String, passwordHash: String) {
        viewModelScope.launch {
            _loginError.value = null
            if (username.isBlank() || passwordHash.isBlank()) {
                _loginError.value = "Username and password cannot be empty"
                return@launch
            }
            try {
                // Perform sign-in via Firebase Authentication
                val firebaseEmail = if (username.contains("@")) username else "$username@virtualfriend.com"
                firebaseAuth.signInWithEmailAndPassword(firebaseEmail, passwordHash)

                // Match with local User entity for Room storage
                var user = repository.getUserByUsername(username)
                if (user == null) {
                    val newUser = User(
                        username = username,
                        passwordHash = passwordHash,
                        displayName = username.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                        preferredPersonality = "Supportive"
                    )
                    val id = repository.insertUser(newUser)
                    user = newUser.copy(id = id.toInt())
                }
                _currentUser.value = user
                _friendName.value = user.displayName.ifEmpty { "Aura" }
            } catch (e: Exception) {
                _loginError.value = "Auth failed: ${e.localizedMessage ?: "Invalid login"}"
            }
        }
    }

    fun register(username: String, passwordHash: String, displayName: String) {
        viewModelScope.launch {
            _loginError.value = null
            _registrationSuccess.value = false
            if (username.isBlank() || passwordHash.isBlank() || displayName.isBlank()) {
                _loginError.value = "All fields are required"
                return@launch
            }
            val existing = repository.getUserByUsername(username)
            if (existing != null) {
                _loginError.value = "Username already exists locally"
                return@launch
            }
            try {
                // Register via Firebase Authentication
                val firebaseEmail = if (username.contains("@")) username else "$username@virtualfriend.com"
                firebaseAuth.createUserWithEmailAndPassword(firebaseEmail, passwordHash)

                val newUser = User(
                    username = username,
                    passwordHash = passwordHash,
                    displayName = displayName,
                    preferredPersonality = "Supportive"
                )
                val id = repository.insertUser(newUser)
                if (id > 0) {
                    _registrationSuccess.value = true
                } else {
                    _loginError.value = "Local registration failed"
                }
            } catch (e: Exception) {
                _loginError.value = "Firebase registration failed: ${e.localizedMessage ?: "Please try again"}"
            }
        }
    }

    fun logout() {
        firebaseAuth.signOut()
        _currentUser.value = null
        _registrationSuccess.value = false
    }

    fun updatePersonalization(friendName: String, personality: String, avatar: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val updated = user.copy(
                displayName = friendName,
                preferredPersonality = personality,
                avatarChoice = avatar
            )
            repository.updateUser(updated)
            _currentUser.value = updated
            _friendName.value = friendName
        }
    }

    fun setApiKeys(gemini: String, openai: String, grok: String) {
        _apiKeys.value = mapOf(
            "GEMINI_API_KEY" to gemini,
            "OPENAI_API_KEY" to openai,
            "GROK_API_KEY" to grok
        )
    }

    fun clearErrors() {
        _loginError.value = null
    }

    fun resetRegistrationStatus() {
        _registrationSuccess.value = false
    }
}
