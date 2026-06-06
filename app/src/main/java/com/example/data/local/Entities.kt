package com.example.data.local

import androidx.room.*

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val passwordHash: String,
    val displayName: String = "User",
    val avatarChoice: String = "avatar_1",
    val preferredPersonality: String = "Supportive" // Supportive, Wise, Sarcastic, Cheerleader
)

@Entity(
    tableName = "chat_sessions",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"])]
)
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val title: String,
    val initialEmotion: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val sender: String, // "friend" or "user"
    val content: String,
    val detectedEmotion: String = "Neutral", // The user's active emotion during this message turn
    val apiUsed: String = "Gemini", // Gemini, OpenAI, Grok, Offline-Simulation
    val timestamp: Long = System.currentTimeMillis()
)
