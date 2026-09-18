package com.example.whatsapp.data.ai

import com.example.whatsapp.models.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Service for interacting with Groq API
 * 
 * Groq provides fast, free AI models with OpenAI-compatible API
 * Get your API key from: https://console.groq.com/
 * 
 * Note: Replace GROQ_API_KEY with your actual API key
 * You can store it in local.properties or use BuildConfig for production
 */
object GroqApiService {
    
    // TODO: Replace with your actual Groq API key
    // Get your free API key from: https://console.groq.com/keys
    private const val GROQ_API_KEY = "YOUR_GROQ_API_KEY_HERE"

    private const val GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"
    // Available Groq models:
    // - llama-3.1-8b-instant (fast, free, good for most tasks)
    // - llama-3.1-70b-versatile (more capable, still fast)
    // - mixtral-8x7b-32768 (excellent quality)
    private const val MODEL = "llama-3.1-8b-instant"
    
    @Serializable
    data class GroqRequest(
        val model: String,  // Remove default to ensure it's always serialized
        val messages: List<GroqMessage>,
        val temperature: Double = 0.7,
        val max_tokens: Int = 1024
    )
    
    @Serializable
    data class GroqMessage(
        val role: String, // "user", "assistant", or "system"
        val content: String
    )
    
    @Serializable
    data class GroqResponse(
        val choices: List<GroqChoice>
    )
    
    @Serializable
    data class GroqChoice(
        val message: GroqMessage
    )
    
    /**
     * Sends a message to Groq API and returns the response
     * 
     * @param userMessage The message from the user
     * @param conversationHistory Previous messages in the conversation
     * @return Result with the AI response or error message
     */
    suspend fun sendMessage(
        userMessage: String,
        conversationHistory: List<Pair<String, Message>> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Build conversation history for context
            val messages = mutableListOf<GroqMessage>()
            
            // Add conversation history (last 10 messages for context)
            conversationHistory.takeLast(10).forEach { (_, message) ->
                val role = if (message.senderPhoneNumber == "user") "user" else "assistant"
                messages.add(GroqMessage(role = role, content = message.message))
            }
            
            // Add current user message
            messages.add(GroqMessage(role = "user", content = userMessage))
            
            val request = GroqRequest(
                model = MODEL,
                messages = messages,
                temperature = 0.7,
                max_tokens = 1024
            )
            
            // Make HTTP request using OkHttp
            val client = OkHttpClient()
            val json = Json { 
                ignoreUnknownKeys = true
                encodeDefaults = true  // Ensure all fields including defaults are encoded
            }
            val jsonString = json.encodeToString(GroqRequest.serializer(), request)
            
            // Debug: Log the request JSON to verify model is included
            android.util.Log.d("GroqApiService", "Request JSON: $jsonString")
            
            val requestBody = jsonString.toRequestBody("application/json; charset=utf-8".toMediaType())
            
            val httpRequest = Request.Builder()
                .url(GROQ_API_URL)
                .addHeader("Authorization", "Bearer $GROQ_API_KEY")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            
            val response = client.newCall(httpRequest).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                val groqResponse = json.decodeFromString<GroqResponse>(responseBody)
                
                val aiMessage = groqResponse.choices.firstOrNull()?.message?.content
                    ?: "I'm sorry, I couldn't generate a response."
                
                Result.success(aiMessage)
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Result.failure(Exception("API Error: ${response.code} - $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}", e))
        }
    }
}

