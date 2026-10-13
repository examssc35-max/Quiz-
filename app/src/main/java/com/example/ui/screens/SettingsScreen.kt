package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppSettings
import com.example.audio.AIVoiceCongratulationEngine
import com.example.ui.components.CelebrationOverlay
import com.example.ui.components.ActiveCelebration
import com.example.ui.components.CelebrationType
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassConfirmDialog
import com.example.ui.components.GlassFilterChip
import com.example.ui.theme.AccentBlueLight
import com.example.ui.theme.AccentBluePrimary
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.LocalAppTheme
import com.example.ui.theme.BACKGROUND_PRESETS
import com.example.ui.theme.SWATCH_COLORS
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThemePreset
import com.example.ui.theme.WrongRed
import com.example.ui.theme.colorToHex
import com.example.ui.theme.parseHexColor

@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    soundEnabled: Boolean,
    voiceEnabled: Boolean,
    vibrationEnabled: Boolean,
    showExplanations: Boolean,
    blurIntensity: String,
    themePresetId: String = "deep_midnight",
    customPrimaryHex: String = "#3B82F6",
    customSecondaryHex: String = "#06B6D4",
    customBgHex: String = "#070E1E",
    voiceCongratulationMode: String = "unlimited_ai",
    voiceLanguage: String = "auto",
    customCongratulationWords: String = "সাবাশ, অসাধারণ, মারহাবা, দারুণ, চমৎকার, Bravo, Outstanding, Fantastic",
    onResetStats: () -> Unit,
    onRestoreDefaultQuizzes: () -> Unit,
    onClearUnfinished: () -> Unit,
    onNavigateToAiSettings: () -> Unit = {},
    onTestVoice: (isBengali: Boolean) -> Unit = {},
    onTestCelebrationSound: ((CelebrationType) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showResetStatsDialog by remember { mutableStateOf(false) }
    var showRestoreQuizzesDialog by remember { mutableStateOf(false) }

    var primaryHexInput by remember(customPrimaryHex) { mutableStateOf(customPrimaryHex) }
    var secondaryHexInput by remember(customSecondaryHex) { mutableStateOf(customSecondaryHex) }
    var customWordsInput by remember(customCongratulationWords) { mutableStateOf(customCongratulationWords) }
    var testCelebration by remember { mutableStateOf<ActiveCelebration?>(null) }

    val activeTheme = LocalAppTheme.current

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = "Settings",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        // Section 0: AI Evaluation Engine
        SettingsSection(title = "AI Evaluation Engine") {
            SettingsActionRow(
                icon = Icons.Default.AutoAwesome,
                title = "AI Settings",
                subtitle = "Configure Gemini, OpenAI, Claude, OpenRouter, or Custom API",
                onClick = onNavigateToAiSettings
            )
        }

        // Section 1: Custom Theme Studio (Requirement 1: ইউজার নিজের ইচ্ছামত থিম ব্যবহার করতে পারবে)
        SettingsSection(title = "Custom Theme Studio (থিম স্টুডিও)") {
            Text(
                text = "Choose a glassmorphic theme preset or customize your own colors:",
                color = TextMuted,
                fontSize = 13.sp
            )

            // Horizontal Scroll of Presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemePreset.values().forEach { preset ->
                    val isSelected = (themePresetId.equals(preset.id, ignoreCase = true))
                    val pColor = parseHexColor(preset.primaryHex, AccentBlueLight)
                    val sColor = parseHexColor(preset.secondaryHex, AccentCyan)

                    Column(
                        modifier = Modifier
                            .width(115.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) activeTheme.primary.copy(alpha = 0.25f) else Color(0x1EFFFFFF))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) activeTheme.primary else Color(0x30FFFFFF),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                appSettings.setThemePresetId(preset.id)
                            }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Two-Color Swatch Preview
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(pColor)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(sColor)
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = activeTheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = preset.title,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = preset.bengaliTitle,
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // If "Custom Theme" is active or user wants to customize colors
            if (themePresetId.equals("custom", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x20000000))
                        .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Customize Colors (নিজের পছন্দের রঙ নির্বাচন করুন):",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Primary Color Swatches
                    Text(text = "Primary Accent Color:", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SWATCH_COLORS.forEach { color ->
                            val hex = colorToHex(color)
                            val isColorSelected = customPrimaryHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isColorSelected) 3.dp else 1.dp,
                                        color = if (isColorSelected) Color.White else Color(0x50FFFFFF),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        primaryHexInput = hex
                                        appSettings.setCustomColors(hex, customSecondaryHex)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isColorSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Secondary Color Swatches
                    Text(text = "Secondary Accent Color:", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SWATCH_COLORS.forEach { color ->
                            val hex = colorToHex(color)
                            val isColorSelected = customSecondaryHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isColorSelected) 3.dp else 1.dp,
                                        color = if (isColorSelected) Color.White else Color(0x50FFFFFF),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        secondaryHexInput = hex
                                        appSettings.setCustomColors(customPrimaryHex, hex)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isColorSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Direct Hex Inputs (Allows typing ANY hex code like #FF0055)
                    Text(text = "Custom Hex Color Codes (সরাসরি হেক্স কোড লিখুন):", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = primaryHexInput,
                            onValueChange = { primaryHexInput = it },
                            label = { Text("Primary", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = activeTheme.primary,
                                unfocusedBorderColor = Color(0x40FFFFFF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        OutlinedTextField(
                            value = secondaryHexInput,
                            onValueChange = { secondaryHexInput = it },
                            label = { Text("Secondary", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = activeTheme.secondary,
                                unfocusedBorderColor = Color(0x40FFFFFF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(activeTheme.primary.copy(alpha = 0.25f))
                            .border(1.dp, activeTheme.primary, RoundedCornerShape(10.dp))
                            .clickable {
                                appSettings.setCustomColors(primaryHexInput, secondaryHexInput)
                            }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Apply Hex Colors (রঙ প্রয়োগ করুন)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Background Mood Presets
                    Text(text = "Background Mood (পটভূমির রঙ):", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BACKGROUND_PRESETS.forEach { bgPreset ->
                            val isBgSelected = customBgHex.equals(bgPreset.hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isBgSelected) activeTheme.primary.copy(alpha = 0.3f) else Color(0x1EFFFFFF))
                                    .border(
                                        width = if (isBgSelected) 1.5.dp else 1.dp,
                                        color = if (isBgSelected) activeTheme.primary else Color(0x25FFFFFF),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        appSettings.setCustomBgColor(bgPreset.hex)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${bgPreset.name} (${bgPreset.bengaliName})",
                                    color = if (isBgSelected) Color.White else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isBgSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Mini Preview Box
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        parseHexColor(customPrimaryHex, AccentBlueLight).copy(alpha = 0.35f),
                                        parseHexColor(customSecondaryHex, AccentCyan).copy(alpha = 0.35f)
                                    )
                                )
                            )
                            .border(1.dp, parseHexColor(customPrimaryHex, AccentBlueLight), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Theme Live Preview ✨",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(parseHexColor(customPrimaryHex, AccentBlueLight))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(text = "Active", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section 2: Audio & AI Voice Appreciation (Requirement 2: আনলিমিটেড কংগ্রেচুলেশন ওয়ার্ড)
        SettingsSection(title = "Audio & AI Voice Appreciation (ভয়েস ও অডিও)") {
            SettingsSwitchRow(
                icon = Icons.Default.RecordVoiceOver,
                title = "AI Voice Appreciation",
                subtitle = "Unlimited dynamic voice cheers in Practice and Results",
                checked = voiceEnabled,
                onCheckedChange = { appSettings.setVoiceEnabled(it) }
            )

            if (voiceEnabled) {
                // Voice Language Selector
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = "Voice Language (ভয়েস ভাষা):",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("auto" to "Auto (স্বয়ংক্রিয়)", "bn" to "বাংলা (Bengali)", "en" to "English").forEach { (code, label) ->
                            GlassFilterChip(
                                label = label,
                                isSelected = voiceLanguage == code,
                                onClick = { appSettings.setVoiceLanguage(code) }
                            )
                        }
                    }
                }

                // Congratulation Phrasing Mode
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = "Congratulation Style (কংগ্রেচুলেশন ধরণ):",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "unlimited_ai" to "Unlimited Words (আনলিমিটেড শব্দ)",
                            "custom_words" to "Custom Words (কাস্টম শব্দ)",
                            "bilingual" to "Bilingual (উভয় ভাষা)"
                        ).forEach { (code, label) ->
                            GlassFilterChip(
                                label = label,
                                isSelected = voiceCongratulationMode == code,
                                onClick = { appSettings.setVoiceCongratulationMode(code) }
                            )
                        }
                    }
                }

                // Custom Congratulation Words Input (Requirement 1, 2, 3)
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = "Custom Congratulation Words (কাস্টম শব্দাবলী):",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "সঠিক উত্তরে (র‍্যান্ডম বা ধারাবাহিক) এই শব্দ দিয়ে প্রশংসা করবে এবং ২-৩টি ভুল উত্তরে সান্ত্বনা দিবে। ভয়েস বন্ধ থাকলেও অ্যানিমেশন ও সাউন্ড সক্রিয় থাকবে।",
                        color = activeTheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = customWordsInput,
                        onValueChange = {
                            customWordsInput = it
                            appSettings.setCustomCongratulationWords(it)
                        },
                        placeholder = {
                            Text("সাবাশ, অসাধারণ, মারহাবা, দারুণ, Bravo, Champion", color = TextMuted, fontSize = 12.sp)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeTheme.primary,
                            unfocusedBorderColor = Color(0x30FFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = activeTheme.primary
                        ),
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Add Praise Chips
                    Text(
                        text = "Quick Add (শব্দ যোগ করতে ট্যাপ করুন):",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("সাবাশ", "মারহাবা", "দারুণ", "অসাধারণ", "Bravo", "Genius", "Awesome", "Superb").forEach { suggestion ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x20FFFFFF))
                                    .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(8.dp))
                                    .clickable {
                                        val existing = customWordsInput.trim()
                                        val updated = if (existing.isEmpty()) suggestion else "$existing, $suggestion"
                                        customWordsInput = updated
                                        appSettings.setCustomCongratulationWords(updated)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(text = "+ $suggestion", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }

                    // Parsed Active Words Preview (Clickable to verify voice)
                    val parsedWords = remember(customWordsInput) {
                        AIVoiceCongratulationEngine.parseCustomWords(customWordsInput)
                    }
                    if (parsedWords.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Active Custom Words (${parsedWords.size} words active - ট্যাপ করে শুনুন):",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            parsedWords.forEach { word ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(activeTheme.primary.copy(alpha = 0.25f))
                                        .border(1.dp, activeTheme.primary.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            val isBn = word.any { it in '\u0980'..'\u09FF' }
                                            onTestVoice(isBn)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = word, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Hear",
                                            tint = activeTheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Test Voice Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(activeTheme.primary.copy(alpha = 0.2f))
                        .border(1.dp, activeTheme.primary, RoundedCornerShape(14.dp))
                        .clickable {
                            val isBn = voiceLanguage == "bn" || voiceLanguage == "auto"
                            onTestVoice(isBn)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Test Voice",
                        tint = activeTheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🔊 Test AI Voice Congratulations (ভয়েস পরীক্ষা করুন)",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Test Fireworks & Celebration Animations Button (Requirement 4)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF8B5CF6).copy(alpha = 0.35f), Color(0xFFEC4899).copy(alpha = 0.35f))))
                        .border(1.dp, Color(0xFFD946EF), RoundedCornerShape(14.dp))
                        .clickable {
                            val randomAnim = listOf(
                                CelebrationType.FIREWORKS,
                                CelebrationType.CONFETTI_RAIN,
                                CelebrationType.SHOOTING_STARS,
                                CelebrationType.GOLDEN_RINGS,
                                CelebrationType.BALLOONS_FLOAT
                            ).random()
                            val words = AIVoiceCongratulationEngine.parseCustomWords(customWordsInput)
                            val word = words.randomOrNull() ?: "সাবাশ"
                            testCelebration = ActiveCelebration(
                                id = System.currentTimeMillis(),
                                type = randomAnim,
                                title = "🎉 $word! চমৎকার উত্তর!",
                                isConsolation = false,
                                durationMs = 3200L
                            )
                            onTestCelebrationSound?.invoke(randomAnim)
                            val isBn = voiceLanguage == "bn" || voiceLanguage == "auto"
                            onTestVoice(isBn)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Test Fireworks",
                        tint = Color(0xFFF472B6),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🎆 Test Fireworks & Animations (আতশবাজি পরীক্ষা করুন)",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            SettingsSwitchRow(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = "Sound Effects",
                subtitle = "Chimes for correct & wrong answers",
                checked = soundEnabled,
                onCheckedChange = { appSettings.setSoundEnabled(it) }
            )

            SettingsSwitchRow(
                icon = Icons.Default.Vibration,
                title = "Haptic Vibration",
                subtitle = "Tactile vibration feedback on response",
                checked = vibrationEnabled,
                onCheckedChange = { appSettings.setVibrationEnabled(it) }
            )
        }

        // Section 3: Quiz & Visual Appearance
        SettingsSection(title = "Quiz & Visual Experience") {
            SettingsSwitchRow(
                icon = Icons.Default.Tune,
                title = "Show Explanations",
                subtitle = "Display answer reasons in Practice Mode",
                checked = showExplanations,
                onCheckedChange = { appSettings.setShowExplanations(it) }
            )

            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "Glass Surface Depth",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("Subtle", "Medium", "High").forEach { level ->
                        GlassFilterChip(
                            label = level,
                            isSelected = blurIntensity == level,
                            onClick = { appSettings.setBlurIntensity(level) }
                        )
                    }
                }
            }
        }

        // Section 4: Data & Offline Storage
        SettingsSection(title = "Data & Storage") {
            SettingsActionRow(
                icon = Icons.Default.Refresh,
                title = "Restore Default Sample Quizzes",
                subtitle = "Re-populates built-in science, math & GK quizzes",
                onClick = { showRestoreQuizzesDialog = true }
            )

            SettingsActionRow(
                icon = Icons.Default.Storage,
                title = "Reset All Statistics",
                subtitle = "Erases past attempt scores and resets accuracy",
                textColor = WrongRed,
                onClick = { showResetStatsDialog = true }
            )
        }

        // Section 5: About
        SettingsSection(title = "About Quiz Explore") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = activeTheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.size(14.dp))
                Column {
                    Text(
                        text = "Quiz Explore v1.0.0",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "100% Offline-First • Zero Cloud Dependency • JSON Engine",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(120.dp))
    }

    if (showResetStatsDialog) {
        GlassConfirmDialog(
            title = "Reset Statistics?",
            message = "Are you sure you want to clear your statistics history?",
            confirmText = "Reset",
            isDestructive = true,
            onConfirm = {
                showResetStatsDialog = false
                onResetStats()
            },
            onDismiss = { showResetStatsDialog = false }
        )
    }

    if (showRestoreQuizzesDialog) {
        GlassConfirmDialog(
            title = "Restore Sample Quizzes?",
            message = "This will ensure all 5 pre-built sample quizzes (General Knowledge, Science, Math, ICT, and Bangladesh GK) are present.",
            confirmText = "Restore",
            onConfirm = {
                showRestoreQuizzesDialog = false
                onRestoreDefaultQuizzes()
            },
            onDismiss = { showRestoreQuizzesDialog = false }
        )
    }

    CelebrationOverlay(
        celebration = testCelebration,
        onDismiss = { testCelebration = null }
    )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    val activeTheme = LocalAppTheme.current
    Column {
        Text(
            text = title,
            color = activeTheme.secondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            backgroundColor = Color(0x281E293B),
            borderBrush = GlassBorderBrush
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val activeTheme = LocalAppTheme.current
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
                imageVector = icon,
                contentDescription = null,
                tint = activeTheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.size(14.dp))
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = activeTheme.primary
            )
        )
    }
}

@Composable
fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    textColor: Color = TextPrimary,
    onClick: () -> Unit
) {
    val activeTheme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (textColor == WrongRed) WrongRed else activeTheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.size(14.dp))
        Column {
            Text(
                text = title,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 12.sp
            )
        }
    }
}
