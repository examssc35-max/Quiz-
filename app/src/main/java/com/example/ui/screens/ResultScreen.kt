package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.QuizResultSummary
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassScoreGauge
import com.example.ui.components.GlassSecondaryButton
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CorrectGreen
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.WrongRed

@Composable
fun ResultScreen(
    summary: QuizResultSummary,
    onReviewClick: () -> Unit,
    onTryAgainClick: () -> Unit,
    onBackHomeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quote = when {
        summary.accuracy >= 80f -> "“Outstanding! Your hard work is truly paying off!”"
        summary.accuracy >= 60f -> "“Great effort! Every question makes you smarter!”"
        else -> "“Learning is a journey. Keep practicing and you will excel!”"
    }

    val title = when {
        summary.accuracy >= 90f -> "Spectacular!"
        summary.accuracy >= 70f -> "Great Job!"
        summary.accuracy >= 50f -> "Well Done!"
        else -> "Quiz Completed!"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        // Title with celebratory confetti feel
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(26.dp))

        // Large Circular Score Gauge matching screen 5
        GlassScoreGauge(
            score = summary.score,
            maxScore = summary.maxScore,
            percentage = summary.accuracy,
            size = 190.dp
        )

        Spacer(modifier = Modifier.height(30.dp))

        // 3 Stats Cards in a row: Correct, Wrong, Skipped
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ResultStatCard(
                value = "${summary.correctCount}",
                label = "Correct",
                icon = Icons.Default.Check,
                color = CorrectGreen,
                modifier = Modifier.weight(1f)
            )

            ResultStatCard(
                value = "${summary.wrongCount}",
                label = "Wrong",
                icon = Icons.Default.Close,
                color = WrongRed,
                modifier = Modifier.weight(1f)
            )

            ResultStatCard(
                value = "${summary.unansweredCount}",
                label = "Skipped",
                icon = Icons.Default.Remove,
                color = Color(0x99FFFFFF),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Motivational quote card matching screen 5
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0x251E293B),
            borderBrush = GlassBorderBrush
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = quote,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        // Primary Button: "Review Answers" with magnifying glass
        GlassPrimaryButton(
            text = "Review Answers",
            onClick = onReviewClick,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Row: "Try Again" & "Back Home"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassSecondaryButton(
                text = "Try Again",
                onClick = onTryAgainClick,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.weight(1f)
            )

            GlassSecondaryButton(
                text = "Back Home",
                onClick = onBackHomeClick,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun ResultStatCard(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0x281E293B),
        borderBrush = GlassBorderBrush
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = TextMuted,
                fontSize = 12.sp
            )
        }
    }
}
