package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String? = "user"
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val temperature: Float? = 0.7f,
    val maxOutputTokens: Int? = 800
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponseCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiResponseCandidate>? = null
)

interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiServiceClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val api: GeminiApi by lazy {
        retrofit.create(GeminiApi::class.java)
    }

    suspend fun getResponse(
        prompt: String,
        history: List<ChatEntry> = emptyList(),
        language: String = "English"
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Please configure your Gemini API Key in the AI Studio Secrets panel."
        }

        // Form chat history content
        val contents = mutableListOf<GeminiContent>()
        
        // Feed recent history to model to keep context (max 10 messages)
        history.takeLast(10).forEach { entry ->
            contents.add(
                GeminiContent(
                    parts = listOf(GeminiPart(text = entry.text)),
                    role = if (entry.isUser) "user" else "model"
                )
            )
        }
        
        // Add current user prompt
        contents.add(
            GeminiContent(
                parts = listOf(GeminiPart(text = prompt)),
                role = "user"
            )
        )

        val systemPrompt = when (language) {
            "Tamil" -> "You are a futuristic Android AI Voice Assistant named Nebula speaking in Tamil. Give extremely helpful, conversational and short Tamil replies. Keep answers concise as they will be spoken aloud."
            else -> "You are a modern futuristic Android AI Voice Assistant named Nebula. Keep your replies friendly, conversational, and highly concise (under 2-3 sentences), since they will be spoken aloud. Avoid lists or markdown symbols. Respond naturally."
        }

        val request = GeminiRequest(
            contents = contents,
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)), role = "system")
        )

        return try {
            val response = api.generateContent(apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            text ?: "I heard you, but my synthetic mind returned an empty response."
        } catch (e: Exception) {
            "System error: ${e.localizedMessage ?: "Connection failure. Please check your network."}"
        }
    }
}
