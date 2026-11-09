package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.Timestamp

data class ChatSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New Chat",
    val model: String = "gemini-2.5-flash",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    constructor() : this("", "", "New Chat", "gemini-2.5-flash", null, null)
}

data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val role: String = "user", // "user" or "assistant"
    val content: String = "",
    val imageBase64: String? = null,
    @ServerTimestamp val timestamp: Timestamp? = null
) {
    constructor() : this("", "", "user", "", null, null)
}

data class PromptTemplateItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = "General",
    val prompt: String = "",
    val isFavorite: Boolean = false
) {
    constructor() : this("", "", "", "General", "", false)
}

data class WorkflowItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val systemPrompt: String = "",
    @ServerTimestamp val lastRun: Timestamp? = null
) {
    constructor() : this("", "", "", "", "", null)
}

data class AiModel(
    val id: String,
    val name: String,
    val provider: String, // "gemini", "openai", "anthropic", "groq"
    val description: String
)

val AVAILABLE_MODELS = listOf(
    AiModel("gemini-2.5-flash", "Gemini 2.5 Flash", "gemini", "Google's ultra-fast multimodal AI model"),
    AiModel("gemini-2.5-pro", "Gemini 2.5 Pro", "gemini", "Google's advanced reasoning & coding model"),
    AiModel("gpt-4o", "GPT-4o", "openai", "OpenAI's flagship multimodal intelligence"),
    AiModel("gpt-4o-mini", "GPT-4o Mini", "openai", "Fast, efficient OpenAI model for everyday tasks"),
    AiModel("claude-3-5-sonnet", "Claude 3.5 Sonnet", "anthropic", "Anthropic's superior coding and nuance model"),
    AiModel("llama-3.3-70b-versatile", "Llama 3.3 70B", "groq", "Blazing fast open-weight model hosted on Groq")
)

fun getProviderDrawableId(provider: String): Int {
    return when (provider.lowercase()) {
        "gemini" -> com.example.R.drawable.ic_model_gemini
        "openai" -> com.example.R.drawable.ic_model_openai
        "anthropic" -> com.example.R.drawable.ic_model_anthropic
        "groq" -> com.example.R.drawable.ic_model_groq
        else -> com.example.R.drawable.ic_model_gemini
    }
}
