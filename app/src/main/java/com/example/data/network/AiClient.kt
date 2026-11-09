package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun callModel(
        provider: String,
        model: String,
        apiKey: String,
        systemInstruction: String,
        promptHistory: List<Pair<String, String>>, // list of (role, content)
        userPrompt: String,
        imageBase64: String? = null
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("API Key for provider '$provider' is missing. Please set it in Settings.")
        }

        when (provider) {
            "gemini" -> callGemini(model, apiKey, systemInstruction, promptHistory, userPrompt, imageBase64)
            "openai" -> callOpenAI(model, apiKey, systemInstruction, promptHistory, userPrompt, imageBase64)
            "anthropic" -> callAnthropic(model, apiKey, systemInstruction, promptHistory, userPrompt, imageBase64)
            "groq" -> callGroq(model, apiKey, systemInstruction, promptHistory, userPrompt, imageBase64)
            else -> throw IllegalArgumentException("Unknown AI provider: $provider")
        }
    }

    suspend fun testConnection(provider: String, apiKey: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Pair(false, "API Key is empty")
        try {
            val response = when (provider) {
                "gemini" -> callGemini("gemini-2.5-flash", apiKey, "", emptyList(), "Say hello", null)
                "openai" -> callOpenAI("gpt-4o-mini", apiKey, "", emptyList(), "Say hello", null)
                "anthropic" -> callAnthropic("claude-3-5-sonnet-20241022", apiKey, "", emptyList(), "Say hello", null)
                "groq" -> callGroq("llama-3.3-70b-versatile", apiKey, "", emptyList(), "Say hello", null)
                else -> return@withContext Pair(false, "Unknown provider")
            }
            Pair(true, "Connected! Response: " + response.take(40) + "...")
        } catch (e: Exception) {
            Pair(false, e.localizedMessage ?: "Connection failed")
        }
    }

    private fun callGemini(
        model: String,
        apiKey: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userPrompt: String,
        imageBase64: String?
    ): String {
        // Endpoint: https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val contentsArray = JSONArray()

        // Optional system instruction prepended or included
        if (systemInstruction.isNotBlank()) {
            val sysObj = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", "System Instruction: $systemInstruction")))
            }
            contentsArray.put(sysObj)
            val ackObj = JSONObject().apply {
                put("role", "model")
                put("parts", JSONArray().put(JSONObject().put("text", "Understood.")))
            }
            contentsArray.put(ackObj)
        }

        for ((role, content) in history) {
            val geminiRole = if (role == "assistant") "model" else "user"
            val item = JSONObject().apply {
                put("role", geminiRole)
                put("parts", JSONArray().put(JSONObject().put("text", content)))
            }
            contentsArray.put(item)
        }

        val partsArray = JSONArray()
        if (!imageBase64.isNullOrBlank()) {
            val imagePart = JSONObject().apply {
                put("inline_data", JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", imageBase64)
                })
            }
            partsArray.put(imagePart)
        }
        partsArray.put(JSONObject().put("text", userPrompt.ifBlank { "Describe this image in detail." }))

        val currentItem = JSONObject().apply {
            put("role", "user")
            put("parts", partsArray)
        }
        contentsArray.put(currentItem)

        val jsonBody = JSONObject().apply {
            put("contents", contentsArray)
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBodyString = response.body?.string() ?: throw Exception("Empty response from Gemini")
            if (!response.isSuccessful) {
                throw Exception("Gemini Error (${response.code}): $responseBodyString")
            }
            val root = JSONObject(responseBodyString)
            val candidates = root.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    return parts.getJSONObject(0).optString("text", "No text generated.")
                }
            }
            return "No response generated."
        }
    }

    private fun callOpenAI(
        model: String,
        apiKey: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userPrompt: String,
        imageBase64: String?
    ): String {
        val url = "https://api.openai.com/v1/chat/completions"
        val messagesArray = JSONArray()

        if (systemInstruction.isNotBlank()) {
            messagesArray.put(JSONObject().put("role", "system").put("content", systemInstruction))
        }

        for ((role, content) in history) {
            messagesArray.put(JSONObject().put("role", role).put("content", content))
        }

        val userMessageObj = JSONObject().put("role", "user")
        if (!imageBase64.isNullOrBlank()) {
            val contentParts = JSONArray()
            contentParts.put(JSONObject().apply {
                put("type", "text")
                put("text", userPrompt.ifBlank { "Describe this image." })
            })
            contentParts.put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply {
                    put("url", "data:image/jpeg;base64,$imageBase64")
                })
            })
            userMessageObj.put("content", contentParts)
        } else {
            userMessageObj.put("content", userPrompt)
        }
        messagesArray.put(userMessageObj)

        val jsonBody = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val respStr = response.body?.string() ?: throw Exception("Empty response from OpenAI")
            if (!response.isSuccessful) {
                throw Exception("OpenAI Error (${response.code}): $respStr")
            }
            val root = JSONObject(respStr)
            val choices = root.getJSONArray("choices")
            val message = choices.getJSONObject(0).getJSONObject("message")
            return message.getString("content")
        }
    }

    private fun callAnthropic(
        model: String,
        apiKey: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userPrompt: String,
        imageBase64: String?
    ): String {
        val url = "https://api.anthropic.com/v1/messages"
        val messagesArray = JSONArray()

        for ((role, content) in history) {
            val anthropicRole = if (role == "assistant") "assistant" else "user"
            messagesArray.put(JSONObject().put("role", anthropicRole).put("content", content))
        }

        val userMsg = JSONObject().put("role", "user")
        if (!imageBase64.isNullOrBlank()) {
            val contentArray = JSONArray()
            contentArray.put(JSONObject().apply {
                put("type", "image")
                put("source", JSONObject().apply {
                    put("type", "base64")
                    put("media_type", "image/jpeg")
                    put("data", imageBase64)
                })
            })
            contentArray.put(JSONObject().apply {
                put("type", "text")
                put("text", userPrompt.ifBlank { "Describe this image." })
            })
            userMsg.put("content", contentArray)
        } else {
            userMsg.put("content", userPrompt)
        }
        messagesArray.put(userMsg)

        val jsonBody = JSONObject().apply {
            put("model", model)
            put("max_tokens", 4096)
            if (systemInstruction.isNotBlank()) {
                put("system", systemInstruction)
            }
            put("messages", messagesArray)
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val respStr = response.body?.string() ?: throw Exception("Empty response from Anthropic")
            if (!response.isSuccessful) {
                throw Exception("Anthropic Error (${response.code}): $respStr")
            }
            val root = JSONObject(respStr)
            val contentArray = root.getJSONArray("content")
            return contentArray.getJSONObject(0).getString("text")
        }
    }

    private fun callGroq(
        model: String,
        apiKey: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userPrompt: String,
        imageBase64: String?
    ): String {
        // Groq uses OpenAI-compatible API format
        val url = "https://api.groq.com/openai/v1/chat/completions"
        val messagesArray = JSONArray()

        if (systemInstruction.isNotBlank()) {
            messagesArray.put(JSONObject().put("role", "system").put("content", systemInstruction))
        }

        for ((role, content) in history) {
            messagesArray.put(JSONObject().put("role", role).put("content", content))
        }

        val userMessageObj = JSONObject().put("role", "user")
        if (!imageBase64.isNullOrBlank()) {
            val contentParts = JSONArray()
            contentParts.put(JSONObject().apply {
                put("type", "text")
                put("text", userPrompt.ifBlank { "Describe this image." })
            })
            contentParts.put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply {
                    put("url", "data:image/jpeg;base64,$imageBase64")
                })
            })
            userMessageObj.put("content", contentParts)
        } else {
            userMessageObj.put("content", userPrompt)
        }
        messagesArray.put(userMessageObj)

        val jsonBody = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val respStr = response.body?.string() ?: throw Exception("Empty response from Groq")
            if (!response.isSuccessful) {
                throw Exception("Groq Error (${response.code}): $respStr")
            }
            val root = JSONObject(respStr)
            val choices = root.getJSONArray("choices")
            val message = choices.getJSONObject(0).getJSONObject("message")
            return message.getString("content")
        }
    }
}
