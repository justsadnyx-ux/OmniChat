package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.data.PreferencesManager
import com.example.data.network.AiClient
import com.example.ui.theme.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.util.NotificationHelper
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onSignOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsManager = remember { PreferencesManager(context) }
    val aiClient = remember { AiClient() }

    val geminiKeyFlow = prefsManager.geminiKey.collectAsState(initial = "")
    val openaiKeyFlow = prefsManager.openaiKey.collectAsState(initial = "")
    val anthropicKeyFlow = prefsManager.anthropicKey.collectAsState(initial = "")
    val groqKeyFlow = prefsManager.groqKey.collectAsState(initial = "")
    val sysPromptFlow = prefsManager.systemInstructions.collectAsState(initial = "")

    var geminiKey by remember { mutableStateOf("") }
    var openaiKey by remember { mutableStateOf("") }
    var anthropicKey by remember { mutableStateOf("") }
    var groqKey by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var savedFeedback by remember { mutableStateOf(false) }

    val notificationsEnabled by prefsManager.notificationsEnabled.collectAsState(initial = true)
    var testNotificationSent by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            NotificationHelper.sendNotification(
                context,
                "OmniChat Intelligence",
                "Notifications enabled! You'll be alerted when AI responses complete."
            )
            testNotificationSent = true
        }
    }

    LaunchedEffect(geminiKeyFlow.value, openaiKeyFlow.value, anthropicKeyFlow.value, groqKeyFlow.value, sysPromptFlow.value) {
        if (geminiKey.isEmpty()) geminiKey = geminiKeyFlow.value
        if (openaiKey.isEmpty()) openaiKey = openaiKeyFlow.value
        if (anthropicKey.isEmpty()) anthropicKey = anthropicKeyFlow.value
        if (groqKey.isEmpty()) groqKey = groqKeyFlow.value
        if (systemPrompt.isEmpty()) systemPrompt = sysPromptFlow.value
    }

    val currentUser = Firebase.auth.currentUser

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings & API Keys",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianDark)
            )
        },
        containerColor = ObsidianDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // User Profile Card
            Surface(
                color = CardDark,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = IndigoPrimary.copy(alpha = 0.2f),
                            modifier = Modifier.size(50.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = currentUser?.displayName?.take(1) ?: currentUser?.email?.take(1)?.uppercase() ?: "U",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = IndigoPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentUser?.displayName ?: "Google User",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = currentUser?.email ?: "Authenticated",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(SuccessColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Synced with Firestore Cloud",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SuccessColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                withContext(NonCancellable) {
                                    try {
                                        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                                    } catch (_: Exception) {}
                                    Firebase.auth.signOut()
                                    onSignOut()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }

            // API Keys Section
            Text(
                "Bring Your Own API Keys (BYOK)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                "Keys are securely stored in on-device encrypted preferences and never sent to third-party tracking servers.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            // Google Gemini Card
            ApiKeyProviderCard(
                providerName = "Google Gemini",
                modelBadge = "Gemini 2.5 Flash & Pro",
                brandColor = GeminiColor,
                iconRes = R.drawable.ic_model_gemini,
                currentKey = geminiKey,
                onKeyChange = { geminiKey = it },
                onTestConnection = { key -> aiClient.testConnection("gemini", key) }
            )

            // OpenAI Card
            ApiKeyProviderCard(
                providerName = "OpenAI",
                modelBadge = "GPT-4o & GPT-4o Mini",
                brandColor = OpenAiColor,
                iconRes = R.drawable.ic_model_openai,
                currentKey = openaiKey,
                onKeyChange = { openaiKey = it },
                onTestConnection = { key -> aiClient.testConnection("openai", key) }
            )

            // Anthropic Card
            ApiKeyProviderCard(
                providerName = "Anthropic",
                modelBadge = "Claude 3.5 Sonnet",
                brandColor = ClaudeColor,
                iconRes = R.drawable.ic_model_anthropic,
                currentKey = anthropicKey,
                onKeyChange = { anthropicKey = it },
                onTestConnection = { key -> aiClient.testConnection("anthropic", key) }
            )

            // Groq Card
            ApiKeyProviderCard(
                providerName = "Groq",
                modelBadge = "Llama 3.3 70B Versatile",
                brandColor = GroqColor,
                iconRes = R.drawable.ic_model_groq,
                currentKey = groqKey,
                onKeyChange = { groqKey = it },
                onTestConnection = { key -> aiClient.testConnection("groq", key) }
            )

            // System Instructions & Persona
            Text(
                "Default AI System Persona",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            // Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Expert Engineer" to "You are OmniChat, a Principal Software Engineer and expert AI assistant. Deliver rigorous, production-grade solutions.",
                    "Concise" to "You are OmniChat. Be extremely direct, concise, and structured. Avoid conversational filler.",
                    "Creative" to "You are OmniChat, an imaginative, insightful, and strategic creative partner."
                ).forEach { (label, preset) ->
                    SuggestionChip(
                        onClick = { systemPrompt = preset },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("System Instructions") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceVariantDark,
                    unfocusedContainerColor = SurfaceVariantDark,
                    focusedBorderColor = IndigoPrimary,
                    unfocusedBorderColor = CardBorder
                )
            )

            // Notifications Card
            Text(
                "Device Notifications",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Surface(
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
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = CircleShape,
                                color = IndigoPrimary.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = IndigoPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Response Alerts",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    "Alert when model responses complete",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { isChecked ->
                                scope.launch { prefsManager.saveNotificationsEnabled(isChecked) }
                                if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = IndigoPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (testNotificationSent) "Test alert sent to device ✓" else "Verify notification channel",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (testNotificationSent) SuccessColor else TextSecondary
                        )

                        FilledTonalButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    NotificationHelper.sendNotification(
                                        context,
                                        "OmniChat Intelligence",
                                        "Test notification: OmniChat is ready to deliver fast AI responses!"
                                    )
                                    testNotificationSent = true
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send Test", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Save Button
            Button(
                onClick = {
                    scope.launch {
                        prefsManager.saveKey("gemini", geminiKey)
                        prefsManager.saveKey("openai", openaiKey)
                        prefsManager.saveKey("anthropic", anthropicKey)
                        prefsManager.saveKey("groq", groqKey)
                        prefsManager.saveSystemInstructions(systemPrompt)
                        savedFeedback = true
                        kotlinx.coroutines.delay(2500)
                        savedFeedback = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                Icon(
                    if (savedFeedback) Icons.Default.Check else Icons.Default.Save,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (savedFeedback) "All Settings Saved ✓" else "Save All Keys & Settings",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ApiKeyProviderCard(
    providerName: String,
    modelBadge: String,
    brandColor: Color,
    iconRes: Int,
    currentKey: String,
    onKeyChange: (String) -> Unit,
    onTestConnection: suspend (String) -> Pair<Boolean, String>
) {
    val scope = rememberCoroutineScope()
    var showPassword by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Surface(
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = brandColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = iconRes),
                                contentDescription = providerName,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        providerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Surface(
                    color = brandColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = modelBadge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = brandColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = currentKey,
                onValueChange = {
                    onKeyChange(it)
                    testResult = null
                },
                label = { Text("$providerName API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Visibility",
                            tint = TextSecondary
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceVariantDark,
                    unfocusedContainerColor = SurfaceVariantDark,
                    focusedBorderColor = brandColor,
                    unfocusedBorderColor = CardBorder
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (testResult != null) {
                    val (success, msg) = testResult!!
                    Text(
                        text = if (success) "🟢 Valid Key" else "🔴 Invalid",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (success) SuccessColor else ErrorColor,
                        fontWeight = FontWeight.Bold
                    )
                } else if (currentKey.isNotBlank()) {
                    Text(
                        text = "Key entered",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                } else {
                    Text(
                        text = "No key configured",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }

                FilledTonalButton(
                    onClick = {
                        if (currentKey.isNotBlank() && !isTesting) {
                            isTesting = true
                            testResult = null
                            scope.launch {
                                val res = onTestConnection(currentKey)
                                testResult = res
                                isTesting = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    enabled = currentKey.isNotBlank() && !isTesting,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = brandColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Test Connection", fontSize = 12.sp)
                }
            }
        }
    }
}
