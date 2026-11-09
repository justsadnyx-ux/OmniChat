package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "omnichat_prefs")

class PreferencesManager(private val context: Context) {
    companion object {
        val GEMINI_KEY = stringPreferencesKey("gemini_api_key")
        val OPENAI_KEY = stringPreferencesKey("openai_api_key")
        val ANTHROPIC_KEY = stringPreferencesKey("anthropic_api_key")
        val GROQ_KEY = stringPreferencesKey("groq_api_key")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val SYSTEM_INSTRUCTIONS = stringPreferencesKey("system_instructions")
        val NOTIFICATIONS_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("notifications_enabled")
    }

    val geminiKey: Flow<String> = context.dataStore.data.map { it[GEMINI_KEY] ?: "" }
    val openaiKey: Flow<String> = context.dataStore.data.map { it[OPENAI_KEY] ?: "" }
    val anthropicKey: Flow<String> = context.dataStore.data.map { it[ANTHROPIC_KEY] ?: "" }
    val groqKey: Flow<String> = context.dataStore.data.map { it[GROQ_KEY] ?: "" }
    val selectedModel: Flow<String> = context.dataStore.data.map { it[SELECTED_MODEL] ?: "gemini-2.5-flash" }
    val systemInstructions: Flow<String> = context.dataStore.data.map { it[SYSTEM_INSTRUCTIONS] ?: "You are OmniChat, a helpful, precise, and expert AI assistant." }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: true }

    suspend fun saveNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun saveKey(provider: String, key: String) {
        context.dataStore.edit { prefs ->
            when (provider) {
                "gemini" -> prefs[GEMINI_KEY] = key
                "openai" -> prefs[OPENAI_KEY] = key
                "anthropic" -> prefs[ANTHROPIC_KEY] = key
                "groq" -> prefs[GROQ_KEY] = key
            }
        }
    }

    suspend fun saveSelectedModel(model: String) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_MODEL] = model
        }
    }

    suspend fun saveSystemInstructions(instructions: String) {
        context.dataStore.edit { prefs ->
            prefs[SYSTEM_INSTRUCTIONS] = instructions
        }
    }
}
