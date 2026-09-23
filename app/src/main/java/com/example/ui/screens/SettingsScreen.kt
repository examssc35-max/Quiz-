package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppSettings
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassConfirmDialog
import com.example.ui.components.GlassFilterChip
import com.example.ui.theme.AccentBlueLight
import com.example.ui.theme.AccentBluePrimary
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WrongRed

@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    soundEnabled: Boolean,
    voiceEnabled: Boolean,
    vibrationEnabled: Boolean,
    showExplanations: Boolean,
    blurIntensity: String,
    onResetStats: () -> Unit,
    onRestoreDefaultQuizzes: () -> Unit,
    onClearUnfinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetStatsDialog by remember { mutableStateOf(false) }
    var showRestoreQuizzesDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
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

        // Section 1: Audio & Appreciation matching screen 8
        SettingsSection(title = "Audio & Feedback") {
            SettingsSwitchRow(
                icon = Icons.Default.RecordVoiceOver,
                title = "AI Voice Appreciation",
                subtitle = "Encouraging voice cheers in Practice Mode",
                checked = voiceEnabled,
                onCheckedChange = { appSettings.setVoiceEnabled(it) }
            )

            SettingsSwitchRow(
                icon = Icons.Default.VolumeUp,
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

        // Section 2: Quiz & Visual Appearance
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

        // Section 3: Data & Offline Storage
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

        // Section 4: About
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
                    tint = AccentCyan,
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

        Spacer(modifier = Modifier.height(90.dp))
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
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            color = AccentCyan,
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
                tint = AccentBlueLight,
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
                checkedTrackColor = AccentBluePrimary
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
            tint = if (textColor == WrongRed) WrongRed else AccentBlueLight,
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
