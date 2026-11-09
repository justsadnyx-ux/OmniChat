package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import com.example.data.model.getProviderDrawableId
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PreferencesManager
import com.example.data.model.AVAILABLE_MODELS
import com.example.data.model.WorkflowItem
import com.example.data.network.AiClient
import com.example.data.repository.ChatRepository
import com.example.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PrebuiltWorkflow(
    val id: String,
    val name: String,
    val icon: String,
    val tag: String,
    val description: String,
    val systemPrompt: String,
    val inputPlaceholder: String
)

val CURATED_WORKFLOWS = listOf(
    PrebuiltWorkflow(
        id = "wf_code_review",
        name = "Senior Code Review & Security Audit",
        icon = "🔍",
        tag = "DevOps",
        description = "Analyzes source code for bugs, race conditions, memory leaks, security vulnerabilities, and provides an optimized refactor.",
        systemPrompt = "You are a Principal Software Engineer & AppSec Auditor. Review the provided code thoroughly. Highlight critical bugs, security vulnerabilities (OWASP), performance bottlenecks, and provide a production-ready refactored version.",
        inputPlaceholder = "Paste code snippet or function to review..."
    ),
    PrebuiltWorkflow(
        id = "wf_meeting_extract",
        name = "Meeting Notes to Action Matrix",
        icon = "📋",
        tag = "Productivity",
        description = "Converts meeting transcripts or messy notes into prioritized executive decisions, clear action items, owners, and strict deadlines.",
        systemPrompt = "You are an Executive Chief of Staff. Process the meeting notes into: 1) Executive Summary (3 sentences), 2) Key Decisions Made, 3) Action Item Matrix (Task, Assignee, Priority, Deadline).",
        inputPlaceholder = "Paste raw meeting notes, transcript, or bullet points..."
    ),
    PrebuiltWorkflow(
        id = "wf_data_json",
        name = "Raw Text to Structured JSON",
        icon = "📊",
        tag = "Data AI",
        description = "Extracts unstructured data, logs, receipts, or customer inquiries into validated JSON schemas with type-safe properties.",
        systemPrompt = "You are an automated Data Extraction Pipeline. Extract all entities, amounts, dates, and metrics from the input text and format strictly as a valid JSON object with keys and values.",
        inputPlaceholder = "Paste unstructured text, email, log file, or document..."
    ),
    PrebuiltWorkflow(
        id = "wf_release_notes",
        name = "Release Notes & Social Launch Pack",
        icon = "🚀",
        tag = "Growth",
        description = "Transforms git commit messages and PR titles into user-facing release notes, customer changelog, and a social media announcement.",
        systemPrompt = "You are a Product Marketing Lead. Turn the following commit log into: 1) What's New bullet points for users, 2) Technical Changelog for developers, 3) Viral Twitter/LinkedIn launch thread with hooks and emojis.",
        inputPlaceholder = "Paste commit logs or feature descriptions..."
    ),
    PrebuiltWorkflow(
        id = "wf_sql_builder",
        name = "Natural Language to Optimized SQL",
        icon = "⚡",
        tag = "Database",
        description = "Translates natural language analytical questions into high-performance, indexed SQL queries with query plan explanations.",
        systemPrompt = "You are a Database Architect. Write optimized, injection-safe SQL queries for the user's question. Include indexing recommendations and execution plan tips.",
        inputPlaceholder = "Describe what data you want to query or aggregate..."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowsScreen(
    repository: ChatRepository,
    onRunWorkflow: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsManager = remember { PreferencesManager(context) }
    val aiClient = remember { AiClient() }

    val userWorkflows by repository.observeWorkflows().collectAsState(initial = emptyList())

    var activeWorkflowForRun by remember { mutableStateOf<PrebuiltWorkflow?>(null) }
    var workflowInput by remember { mutableStateOf("") }
    var workflowOutput by remember { mutableStateOf<String?>(null) }
    var isExecuting by remember { mutableStateOf(false) }
    var executionError by remember { mutableStateOf<String?>(null) }
    var selectedWorkflowModelId by remember { mutableStateOf("gemini-2.5-flash") }

    var showCreateDialog by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var customDesc by remember { mutableStateOf("") }
    var customPrompt by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "AI Workflows & Automations",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianDark),
                actions = {
                    FilledTonalButton(
                        onClick = { showCreateDialog = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Workflow")
                    }
                }
            )
        },
        containerColor = ObsidianDark
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            // Header Banner
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        TealAccent.copy(alpha = 0.15f),
                                        IndigoPrimary.copy(alpha = 0.15f)
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = TealAccent.copy(alpha = 0.2f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("⚡", fontSize = 18.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Autonomous Task Pipelines",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "One-tap AI automations configured with tailored system instructions. Run them on demand with instant model selection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // Prebuilt Workflows
            item {
                Text(
                    "Built-in Enterprise Workflows",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TealAccent,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(CURATED_WORKFLOWS) { wf ->
                WorkflowCard(
                    title = wf.name,
                    icon = wf.icon,
                    tag = wf.tag,
                    description = wf.description,
                    onRun = {
                        activeWorkflowForRun = wf
                        workflowInput = ""
                        workflowOutput = null
                        executionError = null
                    }
                )
            }

            // Custom User Workflows
            if (userWorkflows.isNotEmpty()) {
                item {
                    Text(
                        "Custom Cloud Workflows",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = IndigoPrimary,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                items(userWorkflows) { customWf ->
                    WorkflowCard(
                        title = customWf.name,
                        icon = "✨",
                        tag = "Custom",
                        description = customWf.description,
                        onRun = {
                            activeWorkflowForRun = PrebuiltWorkflow(
                                id = customWf.id,
                                name = customWf.name,
                                icon = "✨",
                                tag = "Custom",
                                description = customWf.description,
                                systemPrompt = customWf.systemPrompt,
                                inputPlaceholder = "Enter input data for this workflow..."
                            )
                            workflowInput = ""
                            workflowOutput = null
                            executionError = null
                        }
                    )
                }
            }
        }

        // Run Workflow Modal Bottom Sheet
        if (activeWorkflowForRun != null) {
            val currentWf = activeWorkflowForRun!!
            ModalBottomSheet(
                onDismissRequest = {
                    if (!isExecuting) activeWorkflowForRun = null
                },
                containerColor = SurfaceDark
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(currentWf.icon, fontSize = 26.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                currentWf.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                currentWf.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Model Selection for Workflow
                    Text("Select Execution Model", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("gemini-2.5-flash", "gpt-4o-mini", "llama-3.3-70b-versatile").forEach { mId ->
                            val m = AVAILABLE_MODELS.find { it.id == mId } ?: return@forEach
                            val isSel = selectedWorkflowModelId == m.id
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedWorkflowModelId = m.id },
                                leadingIcon = {
                                    Image(
                                        painter = painterResource(id = getProviderDrawableId(m.provider)),
                                        contentDescription = m.name,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                label = { Text(m.name, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TealAccent.copy(alpha = 0.2f),
                                    selectedLabelColor = TealAccent,
                                    containerColor = SurfaceVariantDark,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = workflowInput,
                        onValueChange = { workflowInput = it },
                        placeholder = { Text(currentWf.inputPlaceholder, color = TextTertiary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 220.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceVariantDark,
                            unfocusedContainerColor = SurfaceVariantDark,
                            focusedBorderColor = TealAccent,
                            unfocusedBorderColor = CardBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (workflowInput.isNotBlank() && !isExecuting) {
                                isExecuting = true
                                executionError = null
                                workflowOutput = null

                                scope.launch {
                                    try {
                                        val model = AVAILABLE_MODELS.find { it.id == selectedWorkflowModelId } ?: AVAILABLE_MODELS.first()
                                        val apiKey = when (model.provider) {
                                            "gemini" -> prefsManager.geminiKey.first()
                                            "openai" -> prefsManager.openaiKey.first()
                                            "anthropic" -> prefsManager.anthropicKey.first()
                                            "groq" -> prefsManager.groqKey.first()
                                            else -> ""
                                        }

                                        if (apiKey.isBlank()) {
                                            throw IllegalArgumentException("Please configure your ${model.provider.replaceFirstChar { it.uppercase() }} API key in Settings first.")
                                        }

                                        val result = aiClient.callModel(
                                            provider = model.provider,
                                            model = model.id,
                                            apiKey = apiKey,
                                            systemInstruction = currentWf.systemPrompt,
                                            promptHistory = emptyList(),
                                            userPrompt = workflowInput
                                        )

                                        workflowOutput = result
                                    } catch (e: Exception) {
                                        executionError = e.localizedMessage ?: "Workflow execution failed."
                                    } finally {
                                        isExecuting = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                        enabled = !isExecuting && workflowInput.isNotBlank()
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Running Pipeline...", color = Color.Black, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Execute Workflow", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (executionError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ $executionError",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (workflowOutput != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Output Result", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = CodeBackground,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = workflowOutput!!,
                                    color = TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Workflow Output", workflowOutput))
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy Output")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            val prompt = "Workflow: ${currentWf.name}\n\nInput:\n$workflowInput\n\nResult:\n$workflowOutput"
                                            activeWorkflowForRun = null
                                            onRunWorkflow(prompt)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                                    ) {
                                        Text("Send to Chat")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Create Custom Workflow Dialog
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                containerColor = SurfaceDark,
                title = { Text("Create AI Workflow", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("Workflow Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = customDesc,
                            onValueChange = { customDesc = it },
                            label = { Text("Short Description") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = customPrompt,
                            onValueChange = { customPrompt = it },
                            label = { Text("System Instruction & Automation Logic") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (customName.isNotBlank() && customPrompt.isNotBlank()) {
                                scope.launch {
                                    repository.saveWorkflow(customName, customDesc, customPrompt)
                                    customName = ""
                                    customDesc = ""
                                    customPrompt = ""
                                    showCreateDialog = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                    ) {
                        Text("Save Workflow", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun WorkflowCard(
    title: String,
    icon: String,
    tag: String,
    description: String,
    onRun: () -> Unit
) {
    Surface(
        onClick = onRun,
        color = CardDark,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = SurfaceVariantDark,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(icon, fontSize = 20.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Surface(
                    color = TealAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = tag,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = TealAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onRun,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Execute", color = Color.Black, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
