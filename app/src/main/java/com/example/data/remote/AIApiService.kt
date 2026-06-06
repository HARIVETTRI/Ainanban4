package com.example.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface AIApiService {
    @POST
    suspend fun generateGeminiContent(
        @Url url: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @POST
    suspend fun generateChatCompletion(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body request: OpenAIRequest
    ): OpenAIResponse
}

object RetrofitClient {
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    val apiService: AIApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://localhost/") // Placeholder base; we use dynamic @Url
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(AIApiService::class.java)
    }
}
