package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PromptTemplateItem
import com.example.data.repository.ChatRepository
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*
import kotlinx.coroutines.launch

val CURATED_PROMPTS = listOf(
    PromptTemplateItem(
        id = "curated_1",
        title = "Bug Hunter & Root Cause Analysis",
        category = "Coding",
        prompt = "Act as a Principal Software Engineer. Analyze the following code snippet for edge cases, concurrency hazards, memory leaks, and null-safety issues. Provide fixed code with explanations:\n\n```\n[Insert Code]\n```"
    ),
    PromptTemplateItem(
        id = "curated_2",
        title = "Jetpack Compose M3 UI Refactor",
        category = "Coding",
        prompt = "Refactor this Android Composable to follow Material 3 design guidelines, ensure 48dp touch targets, dark theme contrast, smooth spring animations, and state hoisting with ViewModel."
    ),
    PromptTemplateItem(
        id = "curated_3",
        title = "Executive Brief & Action Matrix",
        category = "Productivity",
        prompt = "Condense the following complex document into an executive summary, top 3 strategic priorities, and an Action Matrix (Task, Owner, Deadline, Risk Level):\n\n[Insert Notes]"
    ),
    PromptTemplateItem(
        id = "curated_4",
        title = "Technical Blog Post & Hook",
        category = "Writing",
        prompt = "Write a compelling, high-signal technical article explaining the architecture and benefits of the following technology. Include a catchy hook, bulleted advantages, real-world case study, and conclusion:"
    ),
    PromptTemplateItem(
        id = "curated_5",
        title = "System Architecture Threat Model",
        category = "Architecture",
        prompt = "Perform a STRIDE threat modeling analysis on this system architecture. Identify potential attack vectors, data exposure risks, and mitigation strategies with zero-trust principles:"
    ),
    PromptTemplateItem(
        id = "curated_6",
        title = "Unit Economics & Growth Modeling",
        category = "Business",
        prompt = "Analyze the customer acquisition cost (CAC), lifetime value (LTV), payback period, and churn rate scenarios for this business model. Highlight key leverage points to maximize profit margins:"
    )
)

val PROMPT_CATEGORIES = listOf("All", "Coding", "Productivity", "Writing", "Architecture", "Business")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptLibraryScreen(repository: ChatRepository, onSelectPrompt: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userPrompts by repository.observePrompts().collectAsState(initial = emptyList())

    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    var titleInput by remember { mutableStateOf("") }
    var categoryInput by remember { mutableStateOf("Coding") }
    var promptInput by remember { mutableStateOf("") }

    val allPrompts = remember(userPrompts) {
        val userIds = userPrompts.map { it.id }.toSet()
        userPrompts + CURATED_PROMPTS.filter { it.id !in userIds }
    }

    val filteredPrompts = remember(allPrompts, selectedCategory, searchQuery) {
        allPrompts.filter { item ->
            val matchesCategory = selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.prompt.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Prompt Library",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianDark),
                actions = {
                    FilledTonalButton(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Prompt")
                    }
                }
            )
        },
        containerColor = ObsidianDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search prompt templates...", color = TextTertiary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextTertiary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextTertiary)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceVariantDark,
                    unfocusedContainerColor = SurfaceVariantDark,
                    focusedBorderColor = IndigoPrimary,
                    unfocusedBorderColor = CardBorder
                )
            )

            // Category Filter Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(PROMPT_CATEGORIES) { cat ->
                    val isSelected = cat == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IndigoPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = IndigoPrimary,
                            containerColor = SurfaceVariantDark,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) IndigoPrimary else Color.Transparent,
                            selectedBorderColor = IndigoPrimary
                        )
                    )
                }
            }

            // Prompts List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                items(filteredPrompts) { item ->
                    PromptCard(
                        item = item,
                        isCustom = !item.id.startsWith("curated_"),
                        onUse = { onSelectPrompt(item.prompt) },
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Prompt", item.prompt))
                        },
                        onDelete = {
                            scope.launch { repository.deletePrompt(item.id) }
                        }
                    )
                }
            }
        }

        // Add Prompt Dialog
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                containerColor = SurfaceDark,
                title = {
                    Text("Add Custom Prompt", fontWeight = FontWeight.Bold, color = TextPrimary)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = titleInput,
                            onValueChange = { titleInput = it },
                            label = { Text("Title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = categoryInput,
                            onValueChange = { categoryInput = it },
                            label = { Text("Category (e.g. Coding, Writing)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = promptInput,
                            onValueChange = { promptInput = it },
                            label = { Text("Prompt Template") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (titleInput.isNotBlank() && promptInput.isNotBlank()) {
                                scope.launch {
                                    repository.savePrompt(titleInput, categoryInput, promptInput, false)
                                    titleInput = ""
                                    promptInput = ""
                                    showAddDialog = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                    ) {
                        Text("Save to Cloud")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun PromptCard(
    item: PromptTemplateItem,
    isCustom: Boolean,
    onUse: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }

    Surface(
        color = CardDark,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    color = when (item.category.lowercase()) {
                        "coding" -> IndigoPrimary.copy(alpha = 0.2f)
                        "architecture" -> TealAccent.copy(alpha = 0.2f)
                        "writing" -> ClaudeColor.copy(alpha = 0.2f)
                        "business" -> OpenAiColor.copy(alpha = 0.2f)
                        else -> SurfaceVariantDark
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = item.category,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (item.category.lowercase()) {
                            "coding" -> IndigoPrimary
                            "architecture" -> TealAccent
                            "writing" -> ClaudeColor
                            "business" -> OpenAiColor
                            else -> TextSecondary
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = {
                            onCopy()
                            copied = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy Prompt",
                            modifier = Modifier.size(18.dp),
                            tint = if (copied) SuccessColor else TextSecondary
                        )
                    }

                    if (isCustom) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Button(
                    onClick = onUse,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Use in Chat", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
