package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.QuizEntity
import com.example.data.model.QuizMode
import com.example.ui.AuditProgressState
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassConfirmDialog
import com.example.ui.components.GlassDifficultyBadge
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassSecondaryButton
import com.example.ui.components.GlassTopBar
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
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun QuizDetailScreen(
    quiz: QuizEntity,
    auditProgress: AuditProgressState = AuditProgressState(),
    onBackClick: () -> Unit,
    onStartMode: (mode: QuizMode) -> Unit,
    onToggleFavorite: () -> Unit,
    onEditClick: () -> Unit,
    onExportClick: () -> Unit,
    onAuditQuiz: () -> Unit = {},
    onRevertAudit: () -> Unit = {},
    onCancelAudit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedMode by remember { mutableStateOf(QuizMode.PRACTICE) }
    var showAuditLogDialog by remember { mutableStateOf(false) }
    var showRevertConfirmDialog by remember { mutableStateOf(false) }

    val isThisQuizAuditing = auditProgress.isAuditing && auditProgress.quizId == quiz.id

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        GlassTopBar(
            title = "Quiz Details",
            onBackClick = onBackClick,
            actions = {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (quiz.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (quiz.isFavorite) Color(0xFFF43F5E) else Color.White
                    )
                }
                IconButton(onClick = onExportClick) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Color.White
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Main Quiz Header Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                backgroundColor = Color(0x351E293B),
                borderBrush = GlassBorderBrush
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = quiz.category,
                            color = AccentCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        GlassDifficultyBadge(difficulty = quiz.difficulty)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = quiz.title,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (quiz.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = quiz.description,
                            color = TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Stats row: Questions, Time Limit, Best Score
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        MetaStatPill(
                            icon = Icons.Default.Assignment,
                            label = "Questions",
                            value = "${quiz.questionCount}"
                        )
                        MetaStatPill(
                            icon = Icons.Default.Timer,
                            label = "Time Limit",
                            value = if (quiz.timeLimit > 0) "${quiz.timeLimit / 60}m" else "Untimed"
                        )
                        MetaStatPill(
                            icon = Icons.Default.BarChart,
                            label = "Best Score",
                            value = if (quiz.bestScore != null) "${quiz.bestScore}/${quiz.maxPossibleScore}" else "—"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI Question Auditor Card
            if (isThisQuizAuditing) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = Color(0x351E3A8A),
                    borderBrush = Brush.linearGradient(listOf(Color(0xFF60A5FA), AccentBluePrimary))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = AccentCyan,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AI is verifying your quiz...",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Verified ${auditProgress.current} / ${auditProgress.total}",
                                    color = AccentCyan,
                                    fontSize = 13.sp
                                )
                            }
                            Text(
                                text = "Cancel",
                                color = WrongRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable { onCancelAudit() }
                                    .padding(6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val progressFraction = if (auditProgress.total > 0) {
                            (auditProgress.current.toFloat() / auditProgress.total).coerceIn(0f, 1f)
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = AccentCyan,
                            trackColor = Color(0x30FFFFFF)
                        )

                        if (auditProgress.currentQuestionText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = auditProgress.currentQuestionText,
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            } else if (quiz.isAiVerified) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = if (quiz.correctedQuestionCount > 0) Color(0x280D9488) else Color(0x2810B981),
                    borderBrush = Brush.linearGradient(
                        if (quiz.correctedQuestionCount > 0) listOf(AccentCyan, Color(0xFF14B8A6))
                        else listOf(CorrectGreen, Color(0xFF059669))
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = if (quiz.correctedQuestionCount > 0) AccentCyan else CorrectGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AI Quality Auditor: Verified",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = onAuditQuiz, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Re-verify",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val summaryMsg = if (quiz.correctedQuestionCount > 0) {
                            "AI detected issues in original quiz data and auto-corrected ${quiz.correctedQuestionCount} question(s) with high confidence."
                        } else {
                            "All questions, options, and stored answers were fact-checked and verified accurate by AI."
                        }
                        Text(
                            text = summaryMsg,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!quiz.auditLogJson.isNullOrBlank()) {
                                GlassSecondaryButton(
                                    text = "View Audit Records",
                                    onClick = { showAuditLogDialog = true },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (quiz.correctedQuestionCount > 0) {
                                GlassSecondaryButton(
                                    text = "Restore Original",
                                    onClick = { showRevertConfirmDialog = true },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            } else {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = Color(0x281E293B),
                    borderBrush = GlassBorderBrush
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Audit",
                                tint = AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI Question Auditor",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Audits every question for wrong options, incorrect answer indices, or flawed JSON data before you play.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        GlassSecondaryButton(
                            text = "Verify Quiz with AI",
                            onClick = onAuditQuiz,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Select Mode Section
            Text(
                text = "Select Mode",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Practice Mode Card
            ModeOptionCard(
                title = "Practice Mode",
                subtitle = "Learn as you go with real-time feedback, explanations, and voice cheers.",
                icon = Icons.Default.School,
                accentColor = Color(0xFF10B981),
                isSelected = selectedMode == QuizMode.PRACTICE,
                onClick = { selectedMode = QuizMode.PRACTICE }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Exam Mode Card
            ModeOptionCard(
                title = "Exam Mode",
                subtitle = "Real test conditions. No live answers, full question navigator, and results after final submission.",
                icon = Icons.Default.Timer,
                accentColor = Color(0xFF3B82F6),
                isSelected = selectedMode == QuizMode.EXAM,
                onClick = { selectedMode = QuizMode.EXAM }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Start Quiz Button
            GlassPrimaryButton(
                text = if (selectedMode == QuizMode.PRACTICE) "Start Practice" else "Start Exam",
                onClick = { onStartMode(selectedMode) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    if (showAuditLogDialog && !quiz.auditLogJson.isNullOrBlank()) {
        val records = remember(quiz.auditLogJson) {
            try {
                val obj = JSONObject(quiz.auditLogJson)
                val arr = obj.optJSONArray("records") ?: JSONArray()
                val list = mutableListOf<Map<String, Any>>()
                for (i in 0 until arr.length()) {
                    val r = arr.getJSONObject(i)
                    list.add(
                        mapOf(
                            "questionId" to r.optString("questionId"),
                            "questionText" to r.optString("questionText"),
                            "originalAnswer" to r.optString("originalAnswer"),
                            "correctedAnswer" to r.optString("correctedAnswer"),
                            "changed" to r.optBoolean("changed"),
                            "reason" to r.optString("reason"),
                            "confidence" to r.optDouble("confidence", 0.95),
                            "needsReview" to r.optBoolean("needsReview", false)
                        )
                    )
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }

        Dialog(onDismissRequest = { showAuditLogDialog = false }) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                shape = RoundedCornerShape(26.dp),
                backgroundColor = Color(0xF00F172A),
                borderBrush = GlassBorderBrush
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI Audit Records",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showAuditLogDialog = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }

                    val correctedCount = records.count { it["changed"] == true }
                    Text(
                        text = "$correctedCount question(s) corrected out of ${records.size}",
                        color = AccentCyan,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(records.size) { idx ->
                            val r = records[idx]
                            val isChanged = r["changed"] == true
                            val needsRev = r["needsReview"] == true
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                backgroundColor = if (isChanged) Color(0x350F766E) else Color(0x201E293B),
                                borderBrush = if (isChanged) Brush.linearGradient(listOf(AccentCyan, Color(0xFF14B8A6))) else GlassBorderBrush
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Question ${idx + 1}",
                                            color = AccentCyan,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val confVal = (((r["confidence"] as? Double) ?: 0.95) * 100).toInt()
                                        Text(
                                            text = if (isChanged) "Corrected ($confVal%)" else if (needsRev) "Needs Review" else "Verified ($confVal%)",
                                            color = if (isChanged) CorrectGreen else if (needsRev) Color(0xFFFBBF24) else TextMuted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = (r["questionText"] as? String).orEmpty(),
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (isChanged) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "Original: ", color = WrongRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(text = (r["originalAnswer"] as? String).orEmpty(), color = Color(0xFFFCA5A5), fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "Corrected: ", color = CorrectGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(text = (r["correctedAnswer"] as? String).orEmpty(), color = Color(0xFF6EE7B7), fontSize = 12.sp)
                                        }
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "Answer: ", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(text = (r["correctedAnswer"] as? String).orEmpty(), color = TextPrimary, fontSize = 12.sp)
                                        }
                                    }

                                    val reason = r["reason"] as? String
                                    if (!reason.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Reason: $reason",
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    GlassPrimaryButton(
                        text = "Close",
                        onClick = { showAuditLogDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showRevertConfirmDialog) {
        GlassConfirmDialog(
            title = "Restore Original Answers?",
            message = "This will remove AI corrections and revert all questions back to the original answers specified in the imported quiz JSON.",
            confirmText = "Restore",
            dismissText = "Cancel",
            isDestructive = true,
            onConfirm = {
                showRevertConfirmDialog = false
                onRevertAudit()
            },
            onDismiss = { showRevertConfirmDialog = false }
        )
    }
}

@Composable
fun MetaStatPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = AccentBlueLight,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = TextMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
fun ModeOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = if (isSelected) Color(0x351E3A8A) else Color(0x221E293B),
        borderBrush = if (isSelected) {
            Brush.linearGradient(listOf(Color(0xFF60A5FA), AccentBluePrimary))
        } else GlassBorderBrush,
        borderWidth = if (isSelected) 1.8.dp else 1.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            // Radio Circle indicator
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) AccentBluePrimary else Color(0x30FFFFFF)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }
    }
}
