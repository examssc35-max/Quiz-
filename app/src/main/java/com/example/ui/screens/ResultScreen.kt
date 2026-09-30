package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AIManager
import com.example.ai.AiResultAnalysis
import com.example.data.model.QuizResultSummary
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassScoreGauge
import com.example.ui.components.GlassSecondaryButton
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CorrectGreen
import com.example.ui.theme.CorrectGreenBg
import com.example.ui.theme.CorrectGreenBorder
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WrongRed
import com.example.ui.theme.WrongRedBg
import com.example.ui.theme.WrongRedBorder

@Composable
fun ResultScreen(
    summary: QuizResultSummary,
    aiManager: AIManager? = null,
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

        // AI Performance Analysis Card
        Spacer(modifier = Modifier.height(20.dp))
        AiPerformanceAnalysisCard(summary = summary, aiManager = aiManager)

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiPerformanceAnalysisCard(
    summary: QuizResultSummary,
    aiManager: AIManager?,
    modifier: Modifier = Modifier
) {
    var analysis by remember { mutableStateOf<AiResultAnalysis?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = Color(0x301E293B),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AI Performance Insights",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "এআই পারফরম্যান্স বিশ্লেষণ",
                            color = AccentCyan,
                            fontSize = 11.sp
                        )
                    }
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        color = AccentCyan,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val currentAnalysis = analysis
            if (currentAnalysis != null) {
                // Overall Summary text
                Text(
                    text = currentAnalysis.overallSummary,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium
                )

                // Strengths
                if (currentAnalysis.strengths.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "সবল দিকসমূহ (Strengths):",
                        color = Color(0xFF6EE7B7),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        currentAnalysis.strengths.forEach { str ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CorrectGreenBg)
                                    .border(1.dp, CorrectGreenBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(text = "✓ $str", color = CorrectGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Weak Areas
                if (currentAnalysis.weakAreas.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "মনোযোগ দেওয়ার ক্ষেত্র (Areas to Review):",
                        color = Color(0xFFFCA5A5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        currentAnalysis.weakAreas.forEach { weak ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(WrongRedBg)
                                    .border(1.dp, WrongRedBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(text = "• $weak", color = WrongRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Recommendations
                if (currentAnalysis.recommendations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "পরামর্শ (Recommendations):",
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    currentAnalysis.recommendations.forEach { rec ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(text = "→ ", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(text = rec, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }
                }
            } else if (isLoading) {
                Text(
                    text = "Gemini AI আপনার কুইজ স্কোর, নির্ভুলতা ও ভুলের প্যাটার্ন বিশ্লেষণ করছে...",
                    color = TextMuted,
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "ক্লাউড এআই দিয়ে আপনার উত্তরগুলোর দুর্বলতা ও সবল দিক বিশ্লেষণ করতে নিচের বাটনে চাপ দিন।",
                        color = TextMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    GlassSecondaryButton(
                        text = "Generate AI Insights (পারফরম্যান্স বিশ্লেষণ)",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        onClick = {
                            if (aiManager != null) {
                                isLoading = true
                                scope.launch {
                                    try {
                                        val res = aiManager.analyzeQuizResult(summary)
                                        if (res.isSuccess) {
                                            analysis = res.getOrNull()
                                        }
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
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
