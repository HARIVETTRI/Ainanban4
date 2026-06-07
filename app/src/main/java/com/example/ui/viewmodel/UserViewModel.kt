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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class UserViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: VirtualFriendRepository
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T {
        return suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    continuation.resume(task.result)
                } else {
                    continuation.resumeWithException(task.exception ?: Exception("Unknown error in Firebase connection"))
                }
            }
        }
    }
    
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
                        val cloudUser = repository.fetchUserProfileFromFirestore()
                        if (cloudUser != null) {
                            val id = repository.insertUser(cloudUser)
                            localUser = cloudUser.copy(id = id.toInt())
                        } else {
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
                // Perform sign-in via Firebase Authentication and await completion
                val trimmedUsername = username.trim()
                val sanitizedUsername = trimmedUsername.replace("\\s+".toRegex(), "")
                val firebaseEmail = if (sanitizedUsername.contains("@")) sanitizedUsername.lowercase() else "${sanitizedUsername.lowercase()}@virtualfriend.com"
                firebaseAuth.signInWithEmailAndPassword(firebaseEmail, passwordHash).awaitTask()

                // Match with local User entity for Room storage
                var user = repository.getUserByUsername(trimmedUsername)
                if (user == null) {
                    val cloudUser = repository.fetchUserProfileFromFirestore()
                    if (cloudUser != null) {
                        val restoredUser = cloudUser.copy(passwordHash = passwordHash)
                        val id = repository.insertUser(restoredUser)
                        user = restoredUser.copy(id = id.toInt())
                    } else {
                        val newUser = User(
                            username = trimmedUsername,
                            passwordHash = passwordHash,
                            displayName = trimmedUsername.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                            preferredPersonality = "Supportive"
                        )
                        val id = repository.insertUser(newUser)
                        user = newUser.copy(id = id.toInt())
                    }
                } else {
                    // Sync user profile from local database to Firebase Database & Firestore
                    val uid = firebaseAuth.currentUser?.uid
                    if (uid != null) {
                        repository.syncUserToFirebase(uid, user)
                        repository.syncUserToFirestore(uid, user)
                    }
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
            if (passwordHash.length < 6) {
                _loginError.value = "Password must be at least 6 characters"
                return@launch
            }
            val existing = repository.getUserByUsername(username.trim())
            if (existing != null) {
                _loginError.value = "Username already exists locally"
                return@launch
            }
            try {
                // Register via Firebase Authentication and await completion
                val sanitizedUsername = username.trim().replace("\\s+".toRegex(), "")
                val firebaseEmail = if (sanitizedUsername.contains("@")) sanitizedUsername.lowercase() else "${sanitizedUsername.lowercase()}@virtualfriend.com"
                firebaseAuth.createUserWithEmailAndPassword(firebaseEmail, passwordHash).awaitTask()

                val newUser = User(
                    username = username.trim(),
                    passwordHash = passwordHash,
                    displayName = displayName.trim(),
                    preferredPersonality = "Supportive"
                )
                val id = repository.insertUser(newUser)
                if (id > 0) {
                    val userWithId = newUser.copy(id = id.toInt())
                    
                    // Sync user profile to Firestore & Realtime DB instantly on sign up
                    val uid = firebaseAuth.currentUser?.uid
                    if (uid != null) {
                        repository.syncUserToFirebase(uid, userWithId)
                        repository.syncUserToFirestore(uid, userWithId)
                    }
                    
                    _currentUser.value = userWithId
                    _friendName.value = displayName.trim()
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

    fun clearErrors() {
        _loginError.value = null
    }

    fun resetRegistrationStatus() {
        _registrationSuccess.value = false
    }
}
