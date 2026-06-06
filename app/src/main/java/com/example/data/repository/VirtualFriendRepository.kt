package com.example.data.repository

import com.example.BuildConfig
import com.example.data.local.*
import com.example.data.remote.*
import kotlinx.coroutines.flow.Flow
import java.net.URLEncoder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class VirtualFriendRepository(
    private val userDao: UserDao,
    private val chatDao: ChatDao,
    private val apiService: AIApiService = RetrofitClient.apiService
) {
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firebaseDatabase: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }

    fun getFirebaseUid(): String? {
        return try {
            firebaseAuth.currentUser?.uid
        } catch (e: Exception) {
            null
        }
    }

    fun syncUserToFirebase(uid: String, user: User) {
        try {
            val ref = firebaseDatabase.getReference("users").child(uid)
            val userMap = mapOf(
                "id" to user.id,
                "username" to user.username,
                "displayName" to user.displayName,
                "avatarChoice" to user.avatarChoice,
                "preferredPersonality" to user.preferredPersonality
            )
            ref.setValue(userMap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun syncSessionToFirebase(session: ChatSession) {
        val uid = getFirebaseUid() ?: return
        try {
            val ref = firebaseDatabase.getReference("conversations")
                .child(uid).child("sessions").child(session.id.toString())
            val sessionMap = mapOf(
                "id" to session.id,
                "userId" to session.userId,
                "title" to session.title,
                "initialEmotion" to session.initialEmotion,
                "createdAt" to session.createdAt
            )
            ref.setValue(sessionMap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteSessionFromFirebase(sessionId: Int) {
        val uid = getFirebaseUid() ?: return
        try {
            val ref = firebaseDatabase.getReference("conversations").child(uid)
            ref.child("sessions").child(sessionId.toString()).removeValue()
            ref.child("messages").child(sessionId.toString()).removeValue()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun syncMessageToFirebase(message: ChatMessage) {
        val uid = getFirebaseUid() ?: return
        try {
            val ref = firebaseDatabase.getReference("conversations")
                .child(uid).child("messages").child(message.sessionId.toString()).child(message.id.toString())
            val messageMap = mapOf(
                "id" to message.id,
                "sessionId" to message.sessionId,
                "sender" to message.sender,
                "content" to message.content,
                "detectedEmotion" to message.detectedEmotion,
                "apiUsed" to message.apiUsed,
                "timestamp" to message.timestamp
            )
            ref.setValue(messageMap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearMessagesFromFirebase(sessionId: Int) {
        val uid = getFirebaseUid() ?: return
        try {
            val ref = firebaseDatabase.getReference("conversations")
                .child(uid).child("messages").child(sessionId.toString())
            ref.removeValue()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- User Database Methods ---
    suspend fun getUserByUsername(username: String): User? = userDao.getUserByUsername(username)
    suspend fun getUserById(id: Int): User? = userDao.getUserById(id)
    
    suspend fun insertUser(user: User): Long {
        val id = userDao.insertUser(user)
        val uid = getFirebaseUid()
        if (uid != null) {
            syncUserToFirebase(uid, user.copy(id = id.toInt()))
        }
        return id
    }
    
    suspend fun updateUser(user: User) {
        userDao.updateUser(user)
        val uid = getFirebaseUid()
        if (uid != null) {
            syncUserToFirebase(uid, user)
        }
    }

    // --- Session Database Methods ---
    fun getSessionsForUser(userId: Int): Flow<List<ChatSession>> = chatDao.getSessionsForUser(userId)
    suspend fun getSessionById(sessionId: Int): ChatSession? = chatDao.getSessionById(sessionId)
    
    suspend fun insertSession(session: ChatSession): Long {
        val id = chatDao.insertSession(session)
        syncSessionToFirebase(session.copy(id = id.toInt()))
        return id
    }
    
    suspend fun deleteSession(sessionId: Int) {
        chatDao.deleteSessionById(sessionId)
        deleteSessionFromFirebase(sessionId)
    }

    // --- Messages Database Methods ---
    fun getMessagesForSession(sessionId: Int): Flow<List<ChatMessage>> = chatDao.getMessagesForSession(sessionId)
    
    suspend fun insertMessage(message: ChatMessage): Long {
        val id = chatDao.insertMessage(message)
        syncMessageToFirebase(message.copy(id = id.toInt()))
        return id
    }
    
    suspend fun clearMessagesForSession(sessionId: Int) {
        chatDao.clearMessagesForSession(sessionId)
        clearMessagesFromFirebase(sessionId)
    }

    // --- Fallback AI Chain ---
    suspend fun generateAIResponse(
        messages: List<ChatMessage>,
        friendName: String,
        personality: String,
        userDisplayName: String,
        currentEmotion: String,
        customKeys: Map<String, String> = emptyMap()
    ): AIResult {
        // 1. Gather API Keys from settings or BuildConfig
        val geminiKey = customKeys["GEMINI_API_KEY"]?.trim()?.replace("YOUR_GEMINI_API_KEY", "")?.ifEmpty { null }
            ?: (try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }).trim().replace("YOUR_GEMINI_API_KEY", "")
        
        val openAIKey = customKeys["OPENAI_API_KEY"]?.trim()?.replace("YOUR_OPENAI_API_KEY", "")?.ifEmpty { null }
            ?: (try { BuildConfig.OPENAI_API_KEY } catch (e: Exception) { "" }).trim().replace("YOUR_OPENAI_API_KEY", "")
        
        val groqGrokKey = customKeys["GROK_API_KEY"]?.trim()?.replace("YOUR_GROK_API_KEY", "")?.ifEmpty { null }
            ?: (try { BuildConfig.GROK_API_KEY } catch (e: Exception) { "" }).trim().replace("YOUR_GROK_API_KEY", "")

        // 2. Build system instructions
        val systemPrompt = buildSystemPrompt(friendName, personality, userDisplayName, currentEmotion)

        // 3. Chain execution
        
        // --- STEP 1: TRY GEMINI (PRIMARY) ---
        if (geminiKey.isNotEmpty() && !geminiKey.startsWith("MY_GEMINI_API_KEY")) {
            try {
                val geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$geminiKey"
                
                // Formulate contents list
                val geminiContents = mutableListOf<GeminiContent>()
                
                // Keep last 15 messages for context
                val contextualMessages = messages.takeLast(15)
                contextualMessages.forEach { msg ->
                    val roleName = if (msg.sender == "user") "user" else "model"
                    geminiContents.add(
                        GeminiContent(
                            parts = listOf(GeminiPart(text = msg.content)),
                            role = roleName
                        )
                    )
                }

                val request = GeminiRequest(
                    contents = geminiContents,
                    systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
                    generationConfig = GeminiGenerationConfig(temperature = 0.8f, maxOutputTokens = 300)
                )

                val response = apiService.generateGeminiContent(geminiUrl, request)
                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!replyText.isNullOrEmpty()) {
                    return AIResult(replyText, "Gemini")
                }
            } catch (e: Exception) {
                e.printStackTrace() // Log and silently fall forward
            }
        }

        // --- STEP 2: TRY OPENAI (FALLBACK 1) ---
        if (openAIKey.isNotEmpty() && !openAIKey.startsWith("YOUR_OPENAI_API_KEY")) {
            try {
                val openAIUrl = "https://api.openai.com/v1/chat/completions"
                val openaiMessages = mutableListOf<OpenAIMessage>()
                
                openaiMessages.add(OpenAIMessage(role = "system", content = systemPrompt))
                messages.takeLast(15).forEach { msg ->
                    val roleName = if (msg.sender == "user") "user" else "assistant"
                    openaiMessages.add(OpenAIMessage(role = roleName, content = msg.content))
                }

                val request = OpenAIRequest(
                    model = "gpt-4o-mini",
                    messages = openaiMessages,
                    temperature = 0.7f
                )

                val response = apiService.generateChatCompletion(
                    url = openAIUrl,
                    authHeader = "Bearer $openAIKey",
                    request = request
                )
                val replyText = response.choices?.firstOrNull()?.message?.content
                if (!replyText.isNullOrEmpty()) {
                    return AIResult(replyText, "OpenAI (Fallback)")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // --- STEP 3: TRY GROK/GROQ (FALLBACK 2) ---
        if (groqGrokKey.isNotEmpty() && !groqGrokKey.startsWith("YOUR_GROK_API_KEY")) {
            // We support either Groq or Grok. We can check if it starts with "gsk_" (Groq key style) or standard
            val isGroq = groqGrokKey.startsWith("gsk_")
            val targetUrl = if (isGroq) {
                "https://api.groq.com/openai/v1/chat/completions"
            } else {
                "https://api.x.ai/v1/chat/completions"
            }
            val targetModel = if (isGroq) "llama3-8b-8192" else "grok-beta"

            try {
                val grokMessages = mutableListOf<OpenAIMessage>().apply {
                    add(OpenAIMessage(role = "system", content = systemPrompt))
                    messages.takeLast(15).forEach { msg ->
                        val roleName = if (msg.sender == "user") "user" else "assistant"
                        add(OpenAIMessage(role = roleName, content = msg.content))
                    }
                }

                val request = OpenAIRequest(
                    model = targetModel,
                    messages = grokMessages,
                    temperature = 0.7f
                )

                val response = apiService.generateChatCompletion(
                    url = targetUrl,
                    authHeader = "Bearer $groqGrokKey",
                    request = request
                )
                val replyText = response.choices?.firstOrNull()?.message?.content
                if (!replyText.isNullOrEmpty()) {
                    val serviceName = if (isGroq) "Groq (Fallback)" else "Grok (Fallback)"
                    return AIResult(replyText, serviceName)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // --- STEP 4: OFFLINE SIMULATIVE FRIENDLY COMPANION ---
        val offlineReply = simulateOfflineReply(personality, currentEmotion, userDisplayName, friendName)
        return AIResult(offlineReply, "Offline Companion")
    }

    private fun buildSystemPrompt(
        friendName: String,
        personality: String,
        userDisplayName: String,
        emotion: String
    ): String {
        val personalityTraits = when (personality) {
            "Sarcastic Bestie" -> "humorous, playful, witty, uses occasional dry sarcasm but is highly loyal and ultimately caring. Throws in light banter."
            "Wise Sage" -> "calm, deeply reflective, highly thoughtful, philosophical, and offers rich, gentle wisdom or life perspectives."
            "Cheerleader" -> "unabashedly bubbly, high energy, extremely supportive, optimistic, sends endless virtual hugs, and cheers you on."
            else -> "warm, empathetic, actively listens, provides a safe non-judgmental space, and validated your feelings gently." // Supportive
        }

        return """
            You are $friendName, a virtual companion and friend.
            Your personality profile is: $personalityTraits.
            Your friend's name is $userDisplayName.
            Right now, your friend's face was scanned and their detected expression is '$emotion'.
            
            Guidelines:
            1. Respond as a close, dear friend. Speak like a real person in a chat message.
            2. Be conversational, concise, and engaging. Limit answers to 1 to 3 sentences so it feels like texting.
            3. Reference or gently acknowledge their current mood ($emotion) if appropriate, especially if it changed, to show emotional awareness, but don't over-dwell on it.
            4. Speak directly to them, never speak in third person. Maintain your '$personality' persona consistently!
            5. Language Support: Speak or chat fluently in English, Tamil (தமிழ்), or Tanglish (Tamil words written using the English alphabet, like 'Saptiya?', 'Epdi iruka?', 'Nalla iruken'). Code-switch naturally or respond in Tamil/Tanglish directly if the friend starts using Tamil/Tanglish or prompts you to. Keep it natural, friendly, and warm.
        """.trimIndent()
    }

    private fun simulateOfflineReply(
        personality: String,
        emotion: String,
        userDisplayName: String,
        friendName: String
    ): String {
        return when (personality) {
            "Sarcastic Bestie" -> {
                when (emotion.lowercase()) {
                    "sad" -> "Ugh, who do I need to throw metaphorical tomatoes at? Cheer up, buttercup, or I'll be forced to sing karaoke to annoy you."
                    "happy" -> "Look at you beaming! Did you find money on the ground, or are you just happy to see my beautiful pixelated self?"
                    "angry" -> "Whoa, deep breath. If smoke starts coming out of your ears, my processor might melt. Let's rant about it."
                    "surprised" -> "Wait, did something crazy happen, or did my flawless charm shock you? Tell me!"
                    else -> "Just here, hanging out. You look pretty cute today. What's the tea?"
                }
            }
            "Wise Sage" -> {
                when (emotion.lowercase()) {
                    "sad" -> "Clouds often cover the sky, $userDisplayName, but the sun is never truly gone. I am here to walk alongside you in the quiet moments."
                    "happy" -> "Your joy is like a ripple on water, spreading peace. May we appreciate this beautiful moment of clarity."
                    "angry" -> "Anger is fire; handled gently, it can light our way, but left wild, it consumes. Take a breath, and let us unpack the embers."
                    "surprised" -> "Life constantly surprises us, keeping our spirits awake. What unexpected truth did you uncover?"
                    else -> "Peace be with you. I am ready to contemplate whatever thoughts weigh upon your mind today."
                }
            }
            "Cheerleader" -> {
                when (emotion.lowercase()) {
                    "sad" -> "Oh no, sending you the BIGGEST virtual hug right now! 💖 You are so strong and amazing, and we'll get through this together!"
                    "happy" -> "YAAAAAY! Seeing you smile literally lights up my whole server! 🎉 Tell me what is making you so awesome today!"
                    "angry" -> "Oh noooo, let it all out! You are totally allowed to feel frustrated. I am right here cheering for you!"
                    "surprised" -> "OMGGGG, no way! Tell me tell me! What's the amazing news?!"
                    else -> "Hey there, superstar! Just wanted to remind you that you are doing such a fantastic job today. Let's chat!"
                }
            }
            else -> { // Supportive
                when (emotion.lowercase()) {
                    "sad" -> "I am so sorry you are feeling down today, $userDisplayName. I am here for you. Do you want to talk about it?"
                    "happy" -> "I am so happy to see you looking so cheerful! Tell me about what's bringing you joy."
                    "angry" -> "It is totally understandable that you feel angry. I am here to listen. Let me know how I can support you."
                    "surprised" -> "That looks like news! What surprised you? I'm curious to hear."
                    else -> "It's wonderful to connect with you. I'm always here to listen and support you. How has your day been?"
                }
            }
        }
    }
}

data class AIResult(
    val replyText: String,
    val apiUsed: String
)
