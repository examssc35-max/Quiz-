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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.QuizAttemptEntity
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassConfirmDialog
import com.example.ui.components.GlassScoreGauge
import com.example.ui.theme.AccentBlueLight
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CorrectGreen
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WrongRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatisticsScreen(
    attempts: List<QuizAttemptEntity>,
    onResetStatistics: () -> Unit,
    onAttemptClick: (attempt: QuizAttemptEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetDialog by remember { mutableStateOf(false) }

    val totalAttempts = attempts.size
    val totalQuestions = attempts.sumOf { it.totalQuestions }
    val totalCorrect = attempts.sumOf { it.correctCount }
    val totalWrong = attempts.sumOf { it.wrongCount }
    val totalScore = attempts.sumOf { it.score }
    val maxScore = attempts.sumOf { it.maxScore }

    val overallAccuracy = if (totalQuestions > 0) {
        (totalCorrect.toFloat() / totalQuestions) * 100f
    } else 0f

    val bestScore = attempts.maxOfOrNull { it.score } ?: 0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Statistics",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                if (attempts.isNotEmpty()) {
                    Text(
                        text = "Reset",
                        color = WrongRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { showResetDialog = true }
                            .padding(4.dp)
                    )
                }
            }
        }

        // Circular Accuracy Gauge Card
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                backgroundColor = Color(0x301E293B),
                borderBrush = GlassBorderBrush
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Overall Accuracy",
                        color = AccentCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    GlassScoreGauge(
                        score = totalCorrect,
                        maxScore = totalQuestions,
                        percentage = overallAccuracy,
                        size = 170.dp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = if (totalAttempts > 0) "$totalCorrect of $totalQuestions answers correct" else "Complete your first quiz to see stats!",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // 2x2 Metric Cards Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatSummaryCard(
                        title = "Quizzes Played",
                        value = "$totalAttempts",
                        icon = Icons.Default.AssignmentTurnedIn,
                        iconColor = Color(0xFF3B82F6),
                        modifier = Modifier.weight(1f)
                    )

                    StatSummaryCard(
                        title = "Highest Score",
                        value = "$bestScore",
                        icon = Icons.Default.EmojiEvents,
                        iconColor = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatSummaryCard(
                        title = "Correct Answers",
                        value = "$totalCorrect",
                        icon = Icons.Default.CheckCircle,
                        iconColor = CorrectGreen,
                        modifier = Modifier.weight(1f)
                    )

                    StatSummaryCard(
                        title = "Incorrect Answers",
                        value = "$totalWrong",
                        icon = Icons.Default.HelpOutline,
                        iconColor = WrongRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Recent Attempt History Section
        item {
            Text(
                text = "Recent Attempts",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (attempts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No attempts yet. Play a quiz to see your history!",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(attempts) { attempt ->
                AttemptHistoryCard(attempt = attempt, onClick = { onAttemptClick(attempt) })
            }
        }

        item {
            Spacer(modifier = Modifier.height(90.dp))
        }
    }

    if (showResetDialog) {
        GlassConfirmDialog(
            title = "Reset Statistics?",
            message = "This will erase all past quiz attempt history and reset your accuracy. Your quizzes will not be deleted.",
            confirmText = "Reset All",
            isDestructive = true,
            onConfirm = {
                showResetDialog = false
                onResetStatistics()
            },
            onDismiss = { showResetDialog = false }
        )
    }
}

@Composable
fun StatSummaryCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0x281E293B),
        borderBrush = GlassBorderBrush
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = value,
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = title,
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun AttemptHistoryCard(
    attempt: QuizAttemptEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()) }
    val formattedDate = remember(attempt.timestamp) { dateFormat.format(Date(attempt.timestamp)) }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0x241E293B),
        borderBrush = GlassBorderBrush,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = attempt.quizTitle,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x303B82F6))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = attempt.mode,
                            color = AccentBlueLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = formattedDate,
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${attempt.score}/${attempt.maxScore}",
                    color = CorrectGreen,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${attempt.accuracy.toInt()}% accuracy",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}
