package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.data.PreferencesManager
import com.example.data.model.AVAILABLE_MODELS
import com.example.data.model.ChatMessage
import com.example.data.model.getProviderDrawableId
import com.example.data.network.AiClient
import com.example.data.repository.ChatRepository
import com.example.ui.theme.*
import com.example.util.ImageUtils
import com.example.util.NotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    repository: ChatRepository,
    initialPrompt: String? = null,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsManager = remember { PreferencesManager(context) }
    val aiClient = remember { AiClient() }

    val chats by repository.observeChats().collectAsState(initial = emptyList())
    var currentChatId by remember { mutableStateOf<String?>(null) }
    val messages by repository.observeMessages(currentChatId ?: "").collectAsState(initial = emptyList())

    val savedModelPreference by prefsManager.selectedModel.collectAsState(initial = "gemini-2.5-flash")
    var selectedModelId by remember { mutableStateOf("gemini-2.5-flash") }

    LaunchedEffect(savedModelPreference) {
        if (savedModelPreference.isNotBlank()) {
            selectedModelId = savedModelPreference
        }
    }

    var inputMessage by remember { mutableStateOf(initialPrompt ?: "") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageBase64 by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var historySheetOpen by remember { mutableStateOf(false) }
    var modelSheetOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        selectedImageUri = uri
        if (uri != null) {
            selectedImageBase64 = ImageUtils.uriToBase64(context, uri)
        } else {
            selectedImageBase64 = null
        }
    }

    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrBlank()) {
            inputMessage = initialPrompt
        }
    }

    LaunchedEffect(chats) {
        if (currentChatId == null && chats.isNotEmpty()) {
            currentChatId = chats.first().id
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val currentModel = AVAILABLE_MODELS.find { it.id == selectedModelId } ?: AVAILABLE_MODELS.first()

    val providerColor = when (currentModel.provider) {
        "gemini" -> GeminiColor
        "openai" -> OpenAiColor
        "anthropic" -> ClaudeColor
        "groq" -> GroqColor
        else -> IndigoPrimary
    }

    fun sendMessage(promptText: String) {
        if ((promptText.isBlank() && selectedImageBase64 == null) || isGenerating) return
        val messageText = promptText.ifBlank { "Analyze this image in detail." }
        val attachedImage = selectedImageBase64
        inputMessage = ""
        selectedImageUri = null
        selectedImageBase64 = null
        isGenerating = true
        errorMessage = null

        scope.launch {
            try {
                var chatId = currentChatId
                if (chatId == null) {
                    val title = if (messageText.length > 32) messageText.take(30) + "…" else messageText
                    chatId = repository.createChat(title, selectedModelId)
                    currentChatId = chatId
                }

                repository.addMessage(chatId, "user", messageText, attachedImage)

                val apiKey = when (currentModel.provider) {
                    "gemini" -> prefsManager.geminiKey.first()
                    "openai" -> prefsManager.openaiKey.first()
                    "anthropic" -> prefsManager.anthropicKey.first()
                    "groq" -> prefsManager.groqKey.first()
                    else -> ""
                }

                if (apiKey.isBlank()) {
                    throw IllegalArgumentException(
                        "Please configure your ${currentModel.provider.replaceFirstChar { it.uppercase() }} API key in Settings to use ${currentModel.name}."
                    )
                }

                val sysPrompt = prefsManager.systemInstructions.first()
                val historyPairs = messages.map { it.role to it.content }

                val responseText = aiClient.callModel(
                    provider = currentModel.provider,
                    model = currentModel.id,
                    apiKey = apiKey,
                    systemInstruction = sysPrompt,
                    promptHistory = historyPairs,
                    userPrompt = messageText,
                    imageBase64 = attachedImage
                )

                repository.addMessage(chatId, "assistant", responseText)

                // Send notification if enabled
                if (prefsManager.notificationsEnabled.first()) {
                    NotificationHelper.sendNotification(
                        context,
                        "OmniChat: ${currentModel.name}",
                        responseText.take(120)
                    )
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Failed to generate AI response."
            } finally {
                isGenerating = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Surface(
                        onClick = { modelSheetOpen = true },
                        shape = RoundedCornerShape(20.dp),
                        color = SurfaceVariantDark,
                        border = BorderStroke(1.dp, providerColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Image(
                                painter = painterResource(id = getProviderDrawableId(currentModel.provider)),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = currentModel.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Switch Model",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { historySheetOpen = true }) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Chat History",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val newId = repository.createChat("New Conversation", selectedModelId)
                                currentChatId = newId
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.AddComment,
                            contentDescription = "New Chat",
                            tint = IndigoPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianDark)
            )
        },
        containerColor = ObsidianDark
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 88.dp)
            ) {
                // Horizontal Quick Model Selector Carousel with REAL icons
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AVAILABLE_MODELS) { model ->
                        val isSelected = model.id == selectedModelId
                        val chipColor = when (model.provider) {
                            "gemini" -> GeminiColor
                            "openai" -> OpenAiColor
                            "anthropic" -> ClaudeColor
                            "groq" -> GroqColor
                            else -> IndigoPrimary
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedModelId = model.id
                                scope.launch { prefsManager.saveSelectedModel(model.id) }
                            },
                            leadingIcon = {
                                Image(
                                    painter = painterResource(id = getProviderDrawableId(model.provider)),
                                    contentDescription = model.name,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = model.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = chipColor.copy(alpha = 0.2f),
                                selectedLabelColor = chipColor,
                                containerColor = SurfaceVariantDark,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) chipColor else Color.Transparent,
                                selectedBorderColor = chipColor,
                                borderWidth = 1.5.dp
                            )
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 1.dp)

                if (currentChatId == null || messages.isEmpty()) {
                    // Gemini-Style Welcome & Starter Suggestions with Real Model Icon
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = providerColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.5.dp, providerColor.copy(alpha = 0.4f)),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(id = getProviderDrawableId(currentModel.provider)),
                                    contentDescription = currentModel.name,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "OmniChat Intelligence",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Active: ${currentModel.name} • ${currentModel.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = providerColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Quick Starter Prompt Cards
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StarterCard(
                                icon = "💻",
                                title = "Code Architect",
                                prompt = "Analyze this code for architecture, concurrency hazards, and performance optimizations:"
                            ) { sendMessage(it) }

                            StarterCard(
                                icon = "⚡",
                                title = "Workflow Automation",
                                prompt = "Extract all key decisions, action items, assignees, and deadlines from this text:"
                            ) { sendMessage(it) }

                            StarterCard(
                                icon = "📊",
                                title = "Data & Analysis",
                                prompt = "Parse this text and extract all quantitative metrics into a structured markdown table:"
                            ) { sendMessage(it) }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
                    ) {
                        items(messages) { msg ->
                            val isUser = msg.role == "user"
                            MessageBubble(
                                message = msg,
                                isUser = isUser,
                                modelName = currentModel.name,
                                provider = currentModel.provider,
                                providerColor = providerColor,
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("OmniChat", msg.content))
                                },
                                onShare = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, msg.content)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share OmniChat Response"))
                                },
                                onRegenerate = {
                                    val lastUserMessage = messages.findLast { it.role == "user" }?.content
                                    if (!lastUserMessage.isNullOrBlank()) {
                                        sendMessage(lastUserMessage)
                                    }
                                }
                            )
                        }

                        // Animated Typing Indicator during generation
                        if (isGenerating) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = providerColor.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, providerColor.copy(alpha = 0.4f)),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Image(
                                                painter = painterResource(id = getProviderDrawableId(currentModel.provider)),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    TypingIndicator(providerColor = providerColor)
                                }
                            }
                        }

                        // Smart Follow-up Suggestion Chips after last assistant message
                        if (!isGenerating && messages.isNotEmpty() && messages.last().role == "assistant") {
                            item {
                                Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                                    Text(
                                        "Follow-up Suggestions",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextTertiary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SuggestionChip(
                                            onClick = { sendMessage("Explain this in simpler terms.") },
                                            label = { Text("✨ Explain simply") }
                                        )
                                        SuggestionChip(
                                            onClick = { sendMessage("Provide practical code implementation examples.") },
                                            label = { Text("💻 Show code") }
                                        )
                                        SuggestionChip(
                                            onClick = { sendMessage("Summarize this into 3 actionable bullet points.") },
                                            label = { Text("📋 Bullet summary") }
                                        )
                                        SuggestionChip(
                                            onClick = { sendMessage("What are the potential drawbacks or risks?") },
                                            label = { Text("⚠️ Risks & edge cases") }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Error Banner with Direct Settings Action
                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            if (onNavigateToSettings != null && errorMessage!!.contains("API key", ignoreCase = true)) {
                                TextButton(onClick = onNavigateToSettings) {
                                    Text("Settings", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Message Input Bar
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = SurfaceDark,
                tonalElevation = 10.dp,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    if (selectedImageUri != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, providerColor.copy(alpha = 0.5f)),
                            color = SurfaceVariantDark,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(6.dp)
                            ) {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = "Attached image",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Image Attached",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        "Vision prompt ready",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = providerColor
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        selectedImageUri = null
                                        selectedImageBase64 = null
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove attached image",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(
                                    if (selectedImageUri != null) providerColor.copy(alpha = 0.2f) else SurfaceVariantDark,
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.AddPhotoAlternate,
                                contentDescription = "Attach Image",
                                tint = if (selectedImageUri != null) providerColor else TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedTextField(
                            value = inputMessage,
                            onValueChange = { inputMessage = it },
                            placeholder = {
                                Text(
                                    if (selectedImageUri != null) "Ask about this image..." else "Ask ${currentModel.name}...",
                                    color = TextTertiary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp, max = 130.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceVariantDark,
                                unfocusedContainerColor = SurfaceVariantDark,
                                focusedBorderColor = providerColor.copy(alpha = 0.7f),
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        val canSend = (inputMessage.isNotBlank() || selectedImageBase64 != null) && !isGenerating
                        IconButton(
                            onClick = { sendMessage(inputMessage) },
                            modifier = Modifier
                                .size(52.dp)
                                .background(
                                    if (canSend) providerColor else SurfaceVariantDark,
                                    shape = CircleShape
                                ),
                            enabled = canSend
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Message",
                                    tint = if (canSend) Color.White else TextTertiary
                                )
                            }
                        }
                    }
                }
            }

            // Chat History Bottom Sheet
            if (historySheetOpen) {
                ModalBottomSheet(
                    onDismissRequest = { historySheetOpen = false },
                    containerColor = SurfaceDark
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .heightIn(max = 550.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Conversations",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        val newId = repository.createChat("New Conversation", selectedModelId)
                                        currentChatId = newId
                                        historySheetOpen = false
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New")
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search chats...", color = TextTertiary) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextTertiary) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceVariantDark,
                                unfocusedContainerColor = SurfaceVariantDark,
                                focusedBorderColor = IndigoPrimary,
                                unfocusedBorderColor = CardBorder
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        val filteredChats = if (searchQuery.isBlank()) chats else chats.filter {
                            it.title.contains(searchQuery, ignoreCase = true)
                        }

                        if (filteredChats.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No conversations found.", color = TextSecondary)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(filteredChats) { chat ->
                                    val isSelected = chat.id == currentChatId
                                    Surface(
                                        onClick = {
                                            currentChatId = chat.id
                                            historySheetOpen = false
                                        },
                                        color = if (isSelected) IndigoPrimary.copy(alpha = 0.2f) else SurfaceVariantDark,
                                        shape = RoundedCornerShape(14.dp),
                                        border = if (isSelected) BorderStroke(1.dp, IndigoPrimary) else null,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = chat.title,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = chat.model,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextTertiary
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    scope.launch { repository.deleteChat(chat.id) }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Model Switcher Details Sheet with REAL Brand Icons
            if (modelSheetOpen) {
                ModalBottomSheet(
                    onDismissRequest = { modelSheetOpen = false },
                    containerColor = SurfaceDark
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            "Select AI Intelligence Model",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Choose the optimal reasoning, speed, or coding engine for your workflow.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(AVAILABLE_MODELS) { model ->
                                val isSelected = model.id == selectedModelId
                                val mColor = when (model.provider) {
                                    "gemini" -> GeminiColor
                                    "openai" -> OpenAiColor
                                    "anthropic" -> ClaudeColor
                                    "groq" -> GroqColor
                                    else -> IndigoPrimary
                                }

                                Surface(
                                    onClick = {
                                        selectedModelId = model.id
                                        scope.launch { prefsManager.saveSelectedModel(model.id) }
                                        modelSheetOpen = false
                                    },
                                    color = if (isSelected) mColor.copy(alpha = 0.15f) else SurfaceVariantDark,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) mColor else CardBorder
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = mColor.copy(alpha = 0.2f),
                                            modifier = Modifier.size(42.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Image(
                                                    painter = painterResource(id = getProviderDrawableId(model.provider)),
                                                    contentDescription = model.name,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = model.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    color = mColor.copy(alpha = 0.25f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = model.provider.uppercase(),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = mColor,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = model.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = mColor,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator(providerColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Surface(
        color = CardDark,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer(scaleX = dot1Scale, scaleY = dot1Scale)
                    .background(providerColor, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer(scaleX = dot2Scale, scaleY = dot2Scale)
                    .background(providerColor, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer(scaleX = dot3Scale, scaleY = dot3Scale)
                    .background(providerColor, CircleShape)
            )
        }
    }
}

@Composable
private fun StarterCard(
    icon: String,
    title: String,
    prompt: String,
    onSelect: (String) -> Unit
) {
    Surface(
        onClick = { onSelect(prompt) },
        shape = RoundedCornerShape(16.dp),
        color = SurfaceVariantDark,
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 22.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isUser: Boolean,
    modelName: String,
    provider: String,
    providerColor: Color,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit
) {
    val context = LocalContext.current
    var copiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(copiedFeedback) {
        if (copiedFeedback) {
            kotlinx.coroutines.delay(2000)
            copiedFeedback = false
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Surface(
                shape = CircleShape,
                color = providerColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, providerColor.copy(alpha = 0.35f)),
                modifier = Modifier
                    .size(34.dp)
                    .align(Alignment.Top)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = getProviderDrawableId(provider)),
                        contentDescription = modelName,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            color = if (isUser) IndigoPrimary else CardDark,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            border = if (!isUser) BorderStroke(1.dp, CardBorder) else null,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = getProviderDrawableId(provider)),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = modelName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = providerColor
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Render attached image if present
                if (!message.imageBase64.isNullOrBlank()) {
                    val bitmap = remember(message.imageBase64) {
                        ImageUtils.base64ToBitmap(message.imageBase64)
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Uploaded image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .padding(bottom = 8.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // Render content with code block parsing
                MessageContentFormatted(
                    content = message.content,
                    isUser = isUser,
                    onCopyCode = { code ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Code", code))
                    }
                )

                if (!isUser) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Regenerate Response",
                                modifier = Modifier.size(16.dp),
                                tint = TextSecondary
                            )
                        }

                        IconButton(
                            onClick = {
                                onCopy()
                                copiedFeedback = true
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                if (copiedFeedback) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy Response",
                                modifier = Modifier.size(16.dp),
                                tint = if (copiedFeedback) SuccessColor else TextSecondary
                            )
                        }

                        IconButton(
                            onClick = onShare,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                modifier = Modifier.size(16.dp),
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageContentFormatted(
    content: String,
    isUser: Boolean,
    onCopyCode: (String) -> Unit
) {
    val parts = remember(content) { parseMarkdownCodeBlocks(content) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEach { part ->
            when (part) {
                is ContentPart.Text -> {
                    Text(
                        text = part.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) Color.White else TextPrimary,
                        lineHeight = 22.sp
                    )
                }
                is ContentPart.Code -> {
                    CodeBlock(
                        language = part.language,
                        code = part.code,
                        onCopyCode = onCopyCode
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(
    language: String,
    code: String,
    onCopyCode: (String) -> Unit
) {
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }

    Surface(
        color = CodeBackground,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceVariantDark)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" }.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TealAccent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = {
                        onCopyCode(code)
                        copied = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (copied) SuccessColor else TextSecondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (copied) "Copied!" else "Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (copied) SuccessColor else TextSecondary
                    )
                }
            }

            Text(
                text = code,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier
                    .padding(12.dp)
                    .horizontalScroll(rememberScrollState())
            )
        }
    }
}

sealed class ContentPart {
    data class Text(val text: String) : ContentPart()
    data class Code(val language: String, val code: String) : ContentPart()
}

fun parseMarkdownCodeBlocks(raw: String): List<ContentPart> {
    val results = mutableListOf<ContentPart>()
    val codeRegex = Regex("```([a-zA-Z0-9_-]*)\\n?([\\s\\S]*?)```")
    var lastIndex = 0

    for (match in codeRegex.findAll(raw)) {
        val matchStart = match.range.first
        val matchEnd = match.range.last + 1

        if (matchStart > lastIndex) {
            val textBefore = raw.substring(lastIndex, matchStart).trim()
            if (textBefore.isNotEmpty()) {
                results.add(ContentPart.Text(textBefore))
            }
        }

        val lang = match.groupValues[1]
        val code = match.groupValues[2].trimEnd()
        results.add(ContentPart.Code(lang, code))

        lastIndex = matchEnd
    }

    if (lastIndex < raw.length) {
        val remaining = raw.substring(lastIndex).trim()
        if (remaining.isNotEmpty()) {
            results.add(ContentPart.Text(remaining))
        }
    }

    return if (results.isEmpty()) listOf(ContentPart.Text(raw)) else results
}
