package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.ChatSession
import com.example.data.model.ChatMessage
import com.example.data.model.PromptTemplateItem
import com.example.data.model.WorkflowItem
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.concurrent.CancellationException

class ChatRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google.")
    }

    // Chats observation
    fun observeChats(): Flow<List<ChatSession>> = flow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("users").document(uid).collection("chats")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(ChatSession::class.java) }
                .catch { error ->
                    if (error !is CancellationException) {
                        Log.e("ChatRepo", "Error observing chats", error)
                    }
                    throw error
                }
        )
    }

    suspend fun createChat(title: String, model: String): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("chats").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to title,
            "model" to model,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        return docRef.id
    }

    suspend fun deleteChat(chatId: String) {
        val uid = requireUserId()
        db.collection("users").document(uid).collection("chats").document(chatId).delete().await()
    }

    // Messages observation
    fun observeMessages(chatId: String): Flow<List<ChatMessage>> = flow {
        val uid = auth.currentUser?.uid
        if (uid == null || chatId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("users").document(uid).collection("chats").document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(ChatMessage::class.java) }
                .catch { error ->
                    if (error !is CancellationException) {
                        Log.e("ChatRepo", "Error observing messages", error)
                    }
                    throw error
                }
        )
    }

    suspend fun addMessage(chatId: String, role: String, content: String, imageBase64: String? = null) {
        val uid = requireUserId()
        if (chatId.isBlank()) return
        val docRef = db.collection("users").document(uid).collection("chats").document(chatId)
            .collection("messages").document()
        val payload = mutableMapOf<String, Any?>(
            "chatId" to chatId,
            "role" to role,
            "content" to content,
            "timestamp" to FieldValue.serverTimestamp()
        )
        if (imageBase64 != null) {
            payload["imageBase64"] = imageBase64
        }
        docRef.set(payload).await()

        db.collection("users").document(uid).collection("chats").document(chatId)
            .update("updatedAt", FieldValue.serverTimestamp()).await()
    }

    // Prompt Library observation
    fun observePrompts(): Flow<List<PromptTemplateItem>> = flow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("users").document(uid).collection("prompts")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(PromptTemplateItem::class.java) }
                .catch { error -> throw error }
        )
    }

    suspend fun savePrompt(title: String, category: String, prompt: String, isFavorite: Boolean) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("prompts").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to title,
            "category" to category,
            "prompt" to prompt,
            "isFavorite" to isFavorite
        )
        docRef.set(payload).await()
    }

    suspend fun deletePrompt(promptId: String) {
        val uid = requireUserId()
        db.collection("users").document(uid).collection("prompts").document(promptId).delete().await()
    }

    // Workflows observation
    fun observeWorkflows(): Flow<List<WorkflowItem>> = flow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("users").document(uid).collection("workflows")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(WorkflowItem::class.java) }
                .catch { error -> throw error }
        )
    }

    suspend fun saveWorkflow(name: String, description: String, systemPrompt: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("workflows").document()
        val payload = mapOf(
            "userId" to uid,
            "name" to name,
            "description" to description,
            "systemPrompt" to systemPrompt,
            "lastRun" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
    }
}
