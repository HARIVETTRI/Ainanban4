package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.repository.VirtualFriendRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: VirtualFriendRepository

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _activeSession = MutableStateFlow<ChatSession?>(null)
    val activeSession: StateFlow<ChatSession?> = _activeSession.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _lastDetectedEmotion = MutableStateFlow("Neutral")
    val lastDetectedEmotion: StateFlow<String> = _lastDetectedEmotion.asStateFlow()

    private val _currentPromptEngine = MutableStateFlow("None")
    val currentPromptEngine: StateFlow<String> = _currentPromptEngine.asStateFlow()

    private val _speechEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val speechEvent: SharedFlow<String> = _speechEvent.asSharedFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = VirtualFriendRepository(database.userDao(), database.chatDao())
    }

    fun setDetectedEmotion(emotion: String) {
        _lastDetectedEmotion.value = emotion
    }

    fun loadSessions(userId: Int) {
        viewModelScope.launch {
            repository.getSessionsForUser(userId).collect { list ->
                _sessions.value = list
            }
        }
    }

    fun startNewSession(userId: Int, emotion: String, defaultTitle: String) {
        viewModelScope.launch {
            val sessionTitle = "$defaultTitle ($emotion)"
            val newSession = ChatSession(
                userId = userId,
                title = sessionTitle,
                initialEmotion = emotion
            )
            val sessionId = repository.insertSession(newSession)
            val createdSession = newSession.copy(id = sessionId.toInt())
            _activeSession.value = createdSession
            _lastDetectedEmotion.value = emotion

            // Load messages hook
            loadMessages(createdSession.id)

            // Let the virtual companion speak first, reacting to the detected emotion!
            _isGenerating.value = true
            val response = repository.generateAIResponse(
                messages = emptyList(),
                friendName = "Aura",
                personality = "Supportive",
                userDisplayName = "Friend",
                currentEmotion = emotion
            )
            
            repository.insertMessage(
                ChatMessage(
                    sessionId = createdSession.id,
                    sender = "friend",
                    content = response.replyText,
                    detectedEmotion = emotion,
                    apiUsed = response.apiUsed
                )
            )
            _isGenerating.value = false
            _speechEvent.emit(response.replyText)
        }
    }

    fun selectSession(session: ChatSession) {
        _activeSession.value = session
        _lastDetectedEmotion.value = session.initialEmotion
        loadMessages(session.id)
    }

    private fun loadMessages(sessionId: Int) {
        viewModelScope.launch {
            repository.getMessagesForSession(sessionId).collect { list ->
                _messages.value = list
            }
        }
    }

    fun deleteSession(sessionId: Int) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_activeSession.value?.id == sessionId) {
                _activeSession.value = null
                _messages.value = emptyList()
            }
        }
    }

    private fun autoDetectEmotionFromText(text: String): String? {
        val normalized = text.lowercase()
        return when {
            normalized.contains("happy") || normalized.contains("excited") || normalized.contains("joy") ||
            normalized.contains("glad") || normalized.contains("smiling") || normalized.contains("great") ||
            normalized.contains("good") || normalized.contains("love") || normalized.contains("wonderful") ||
            normalized.contains("awesome") || normalized.contains("proud") || normalized.contains("fun") ||
            normalized.contains("smile") || normalized.contains("cheer") || normalized.contains("laugh") ||
            normalized.contains("blessed") || normalized.contains("thankful") || normalized.contains("perfect") -> "Happy"

            normalized.contains("sad") || normalized.contains("cry") || normalized.contains("lonely") ||
            normalized.contains("hurt") || normalized.contains("tear") || normalized.contains("disappointed") ||
            normalized.contains("sorry") || normalized.contains("pain") || normalized.contains("bad") ||
            normalized.contains("low") || normalized.contains("upset") || normalized.contains("miss") ||
            normalized.contains("sigh") || normalized.contains("hopeless") || normalized.contains("grief") ||
            normalized.contains("broken") || normalized.contains("empty") || normalized.contains("down") -> "Sad"

            normalized.contains("angry") || normalized.contains("mad") || normalized.contains("hate") ||
            normalized.contains("furious") || normalized.contains("annoyed") || normalized.contains("frustrated") ||
            normalized.contains("stupid") || normalized.contains("idiot") || normalized.contains("rage") ||
            normalized.contains("irritated") || normalized.contains("annoy") || normalized.contains("pissed") -> "Angry"

            normalized.contains("wow") || normalized.contains("surprise") || normalized.contains("shocked") ||
            normalized.contains("really") || normalized.contains("omg") || normalized.contains("wait") ||
            normalized.contains("incredible") || normalized.contains("unbelievable") || normalized.contains("unexpected") ||
            normalized.contains("shock") || normalized.contains("amaze") || normalized.contains("astound") -> "Surprised"

            else -> null
        }
    }

    fun sendMessage(
        content: String,
        friendName: String,
        personality: String,
        userDisplayName: String,
        customKeys: Map<String, String>
    ) {
        val session = _activeSession.value ?: return
        if (content.isBlank()) return

        viewModelScope.launch {
            // Automatically detect user emotion from message content text if found
            val detected = autoDetectEmotionFromText(content)
            if (detected != null) {
                _lastDetectedEmotion.value = detected
            }
            
            // Save user message
            val userEmotion = _lastDetectedEmotion.value
            val userMsg = ChatMessage(
                sessionId = session.id,
                sender = "user",
                content = content,
                detectedEmotion = userEmotion
            )
            repository.insertMessage(userMsg)

            _isGenerating.value = true
            
            // Generate response using repository's primary-to-fallback REST chain
            val response = repository.generateAIResponse(
                messages = _messages.value + userMsg,
                friendName = friendName,
                personality = personality,
                userDisplayName = userDisplayName,
                currentEmotion = userEmotion,
                customKeys = customKeys
            )

            // Save friend response
            repository.insertMessage(
                ChatMessage(
                    sessionId = session.id,
                    sender = "friend",
                    content = response.replyText,
                    detectedEmotion = userEmotion,
                    apiUsed = response.apiUsed
                )
            )

            _currentPromptEngine.value = response.apiUsed
            _isGenerating.value = false
            _speechEvent.emit(response.replyText)
        }
    }

    fun triggerInactivityMessage(
        userId: Int,
        username: String,
        friendName: String,
        personality: String,
        customKeys: Map<String, String>
    ) {
        viewModelScope.launch {
            val newSession = ChatSession(
                userId = userId,
                title = "Thinking of you... ❤️",
                initialEmotion = "Neutral"
            )
            val sessionId = repository.insertSession(newSession)
            val createdSession = newSession.copy(id = sessionId.toInt())
            _activeSession.value = createdSession
            _lastDetectedEmotion.value = "Neutral"

            // Load empty message list
            _messages.value = emptyList()

            _isGenerating.value = true

            // Formulate standard instructions for generating inactivity dialog
            val response = repository.generateAIResponse(
                messages = emptyList(),
                friendName = friendName,
                personality = personality,
                userDisplayName = username,
                currentEmotion = "Neutral",
                customKeys = customKeys
            )

            // Let's make sure the response is a warm question about why the user was gone
            var finalReply = response.replyText
            if (finalReply.isBlank() || finalReply.length < 5) {
                finalReply = "Hey $username! I noticed you haven't been here for a few days. I was starting to miss your company and was slightly worried... Is everything okay with you?"
            }

            // Insert into the local DB
            repository.insertMessage(
                ChatMessage(
                    sessionId = createdSession.id,
                    sender = "friend",
                    content = finalReply,
                    detectedEmotion = "Neutral",
                    apiUsed = response.apiUsed
                )
            )

            // Reload and update
            loadMessages(createdSession.id)
            _isGenerating.value = false
            _speechEvent.emit(finalReply)
        }
    }
}
