package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ai.AIConfig
import com.example.ai.AIManager
import com.example.ai.AIProviderType
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassConfirmDialog
import com.example.ui.components.GlassFilterChip
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.theme.AccentBlueLight
import com.example.ui.theme.AccentBluePrimary
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CorrectGreen
import com.example.ui.theme.CorrectGreenBg
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WrongRed
import com.example.ui.theme.WrongRedBg
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AiSettingsScreen(
    aiManager: AIManager,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentConfig by remember { mutableStateOf(aiManager.getCurrentConfig()) }

    var aiEnabled by remember { mutableStateOf(currentConfig.enabled) }
    var selectedProvider by remember { mutableStateOf(currentConfig.providerType) }
    var apiKey by remember { mutableStateOf(currentConfig.apiKey) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var modelName by remember { mutableStateOf(currentConfig.model) }
    var baseUrl by remember { mutableStateOf(currentConfig.baseUrl) }
    var temperature by remember { mutableFloatStateOf(currentConfig.temperature) }

    // Test connection state
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultSuccess by remember { mutableStateOf<String?>(null) }
    var testResultError by remember { mutableStateOf<String?>(null) }

    var showResetConfirmDialog by remember { mutableStateOf(false) }

    // Synchronize defaults when provider switches
    fun onProviderSelected(newProvider: AIProviderType) {
        selectedProvider = newProvider
        if (modelName.isBlank() || modelName == selectedProvider.defaultModel) {
            modelName = newProvider.defaultModel
        }
        if (newProvider.requiresBaseUrl && baseUrl.isBlank()) {
            baseUrl = newProvider.defaultBaseUrl
        }
        testResultSuccess = null
        testResultError = null
    }

    val currentDraftConfig = remember(
        aiEnabled,
        selectedProvider,
        apiKey,
        modelName,
        baseUrl,
        temperature
    ) {
        AIConfig(
            enabled = aiEnabled,
            providerType = selectedProvider,
            apiKey = apiKey,
            model = modelName.ifBlank { selectedProvider.defaultModel },
            baseUrl = baseUrl,
            temperature = temperature
        )
    }

    val hasSystemGeminiKey = remember {
        BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Top Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassCard(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                backgroundColor = Color(0x28FFFFFF),
                borderBrush = GlassBorderBrush,
                onClick = onBackClick
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "AI Evaluation Settings",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Semantic fill-in-the-blank grading engine",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        // Section 1: AI Master Switch
        SettingsSection(title = "AI Engine Status") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (aiEnabled) AccentCyan else TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "AI Answer Evaluation",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (aiEnabled) "Real-time semantic & grammar analysis active" else "Using smart local rule-based normalizer",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }

                Switch(
                    checked = aiEnabled,
                    onCheckedChange = { aiEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentBluePrimary
                    ),
                    modifier = Modifier.testTag("ai_enabled_switch")
                )
            }
        }

        // Section 2: Provider Selection
        SettingsSection(title = "AI Provider") {
            Text(
                text = "Choose your LLM provider for contextual answer evaluation:",
                color = TextSecondary,
                fontSize = 13.sp
            )

            // Horizontal chips for providers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AIProviderType.entries.forEach { provider ->
                    GlassFilterChip(
                        label = provider.displayName,
                        isSelected = selectedProvider == provider,
                        onClick = { onProviderSelected(provider) },
                        modifier = Modifier.testTag("provider_chip_${provider.id}")
                    )
                }
            }
        }

        // Section 3: Credentials & Configuration
        SettingsSection(title = "${selectedProvider.displayName} Configuration") {

            // API Key Field
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "API Key",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (selectedProvider == AIProviderType.GEMINI && apiKey.isBlank() && hasSystemGeminiKey) {
                        Text(
                            text = "✓ Default Key Active",
                            color = CorrectGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        testResultSuccess = null
                        testResultError = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input"),
                    placeholder = {
                        Text(
                            text = if (selectedProvider == AIProviderType.GEMINI && hasSystemGeminiKey) {
                                "Using configured project Gemini key (or enter custom key)"
                            } else {
                                "Enter ${selectedProvider.displayName} API Key"
                            },
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    },
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                            Icon(
                                imageVector = if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isApiKeyVisible) "Hide key" else "Show key",
                                tint = AccentBlueLight
                            )
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = Color(0x40FFFFFF),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                Text(
                    text = "Keys are securely stored on device and never logged in Logcat.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }

            // Model Name Field
            Column {
                Text(
                    text = "Model Name",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = modelName,
                    onValueChange = {
                        modelName = it
                        testResultSuccess = null
                        testResultError = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("model_name_input"),
                    placeholder = {
                        Text(text = selectedProvider.defaultModel, color = TextMuted)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = Color(0x40FFFFFF),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                // Quick chips for popular models
                if (selectedProvider.popularModels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedProvider.popularModels.forEach { popularModel ->
                            GlassFilterChip(
                                label = popularModel,
                                isSelected = (modelName == popularModel || (modelName.isBlank() && popularModel == selectedProvider.defaultModel)),
                                onClick = {
                                    modelName = popularModel
                                    testResultSuccess = null
                                    testResultError = null
                                }
                            )
                        }
                    }
                }
            }

            // Base URL Field (shown for OpenAI-Compatible, Custom, or if user customized)
            if (selectedProvider.requiresBaseUrl || baseUrl.isNotBlank()) {
                Column {
                    Text(
                        text = "API Base URL / Endpoint",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            testResultSuccess = null
                            testResultError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("base_url_input"),
                        placeholder = {
                            Text(
                                text = selectedProvider.defaultBaseUrl.ifEmpty { "https://api.example.com/v1" },
                                color = TextMuted
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = Color(0x40FFFFFF),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            // Temperature Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Temperature",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = String.format("%.2f", temperature),
                        color = AccentCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = temperature,
                    onValueChange = { temperature = ((it * 20).roundToInt() / 20f) },
                    valueRange = 0.0f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentBluePrimary,
                        inactiveTrackColor = Color(0x30FFFFFF)
                    ),
                    modifier = Modifier.testTag("temperature_slider")
                )
                Text(
                    text = "Lower values (0.0 - 0.2) give deterministic, accurate grammatical and factual evaluations.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Test Connection Feedback Box
        AnimatedVisibility(visible = testResultSuccess != null || testResultError != null) {
            if (testResultSuccess != null) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = CorrectGreenBg,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = CorrectGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = testResultSuccess.orEmpty(),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else if (testResultError != null) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = WrongRedBg,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = WrongRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = testResultError.orEmpty(),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Section 4: Action Buttons
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // 1. Test Connection Button
            GlassPrimaryButton(
                text = if (isTestingConnection) "Testing Connection..." else "Test Connection",
                leadingIcon = if (isTestingConnection) null else {
                    { Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                },
                enabled = !isTestingConnection,
                onClick = {
                    isTestingConnection = true
                    testResultSuccess = null
                    testResultError = null
                    coroutineScope.launch {
                        try {
                            val res = aiManager.testConnection(currentDraftConfig)
                            if (res.isSuccess) {
                                testResultSuccess = res.getOrThrow()
                            } else {
                                testResultError = res.exceptionOrNull()?.message ?: "Connection failed"
                            }
                        } catch (e: Exception) {
                            testResultError = e.message ?: "Connection test error"
                        } finally {
                            isTestingConnection = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("test_ai_connection_button")
            )

            // 2. Save Configuration Button
            GlassPrimaryButton(
                text = "Save Configuration",
                leadingIcon = {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                },
                onClick = {
                    aiManager.saveConfig(currentDraftConfig)
                    currentConfig = currentDraftConfig
                    Toast.makeText(context, "AI Configuration saved successfully!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_ai_config_button")
            )

            // 3. Reset Configuration Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showResetConfirmDialog = true }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = WrongRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Reset to Default Configuration",
                    color = WrongRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(90.dp))
    }

    if (showResetConfirmDialog) {
        GlassConfirmDialog(
            title = "Reset AI Configuration?",
            message = "This will restore the default Gemini configuration and clear all custom keys and endpoints.",
            confirmText = "Reset",
            isDestructive = true,
            onConfirm = {
                showResetConfirmDialog = false
                aiManager.resetConfig()
                val reset = aiManager.getCurrentConfig()
                currentConfig = reset
                aiEnabled = reset.enabled
                selectedProvider = reset.providerType
                apiKey = reset.apiKey
                modelName = reset.model
                baseUrl = reset.baseUrl
                temperature = reset.temperature
                testResultSuccess = null
                testResultError = null
                Toast.makeText(context, "AI settings reset to default", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showResetConfirmDialog = false }
        )
    }
}
