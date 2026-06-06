package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// --- GEMINI API MODELS ---

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>,
    @Json(name = "role") val role: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "topP") val topP: Float? = null,
    @Json(name = "maxOutputTokens") val maxOutputTokens: Int? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null,
    @Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent? = null,
    @Json(name = "finishReason") val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>? = null
)


// --- OPENAI / GROK / CHAT COMPLETIONS MODELS ---

@JsonClass(generateAdapter = true)
data class OpenAIMessage(
    @Json(name = "role") val role: String, // "system", "user", "assistant"
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class OpenAIRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<OpenAIMessage>,
    @Json(name = "temperature") val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class OpenAIChoice(
    @Json(name = "index") val index: Int,
    @Json(name = "message") val message: OpenAIMessage?,
    @Json(name = "finish_reason") val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenAIResponse(
    @Json(name = "id") val id: String? = null,
    @Json(name = "object") val obj: String? = null,
    @Json(name = "created") val created: Long? = null,
    @Json(name = "model") val model: String? = null,
    @Json(name = "choices") val choices: List<OpenAIChoice>? = null
)
