package com.example.ui.theme

import androidx.compose.ui.graphics.Color

data class EmotionTheme(
    val name: String,
    val emoji: String,
    val color: Color,
    val glowingColor: Color,
    val description: String,
    val baselineQuote: String
)

object EmotionStyles {
    val Happy = EmotionTheme(
        name = "Happy",
        emoji = "😊",
        color = Color(0xFF4CAF50),       // Emerald Green
        glowingColor = Color(0xFF81C784),
        description = "Radiant & Positively Energized",
        baselineQuote = "Let's amplify this joy together!"
    )

    val Sad = EmotionTheme(
        name = "Sad",
        emoji = "🥺",
        color = Color(0xFF1E88E5),       // Calming Royal Blue
        glowingColor = Color(0xFF64B5F6),
        description = "Gentle, Reflective & Quiet",
        baselineQuote = "I am right here besides you. It's safe to rest."
    )

    val Angry = EmotionTheme(
        name = "Angry",
        emoji = "😤",
        color = Color(0xFFD81B60),       // Crimson Pink/Red
        glowingColor = Color(0xFFFF5252),
        description = "Intense, Piercing & Frustrated",
        baselineQuote = "Take your time. Let's rant or breathe it out."
    )

    val Surprised = EmotionTheme(
        name = "Surprised",
        emoji = "😮",
        color = Color(0xFF8E24AA),       // Electric Velvet Amethyst
        glowingColor = Color(0xFFE040FB),
        description = "Astonished, Vibrant & Unveiled",
        baselineQuote = "Oh wow! Tell me everything!"
    )

    val Neutral = EmotionTheme(
        name = "Neutral",
        emoji = "😐",
        color = Color(0xFF78909C),       // Slate Gray
        glowingColor = Color(0xFFB0BEC5),
        description = "Balanced, Calm & Centered",
        baselineQuote = "A beautiful tranquil space. How is everything?"
    )

    fun getTheme(emotionName: String?): EmotionTheme {
        return when (emotionName?.lowercase()?.trim()) {
            "happy" -> Happy
            "sad" -> Sad
            "angry" -> Angry
            "surprised" -> Surprised
            else -> Neutral
        }
    }
}
