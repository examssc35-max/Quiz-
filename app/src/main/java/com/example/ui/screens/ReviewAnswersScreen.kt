package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
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
import com.example.data.model.QuestionReviewItem
import com.example.data.model.QuizResultSummary
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassFilterChip
import com.example.ui.components.GlassTopBar
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CorrectGreen
import com.example.ui.theme.CorrectGreenBg
import com.example.ui.theme.CorrectGreenBorder
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.GlassCardSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WrongRed
import com.example.ui.theme.WrongRedBg
import com.example.ui.theme.WrongRedBorder

@Composable
fun ReviewAnswersScreen(
    summary: QuizResultSummary,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("All") }

    val filteredItems = summary.reviewItems.filter { item ->
        when (selectedFilter) {
            "Incorrect" -> !item.isCorrect
            "Correct" -> item.isCorrect
            else -> true
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(
            title = "Review Answers",
            onBackClick = onBackClick
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Filter Chips
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassFilterChip(
                        label = "All (${summary.reviewItems.size})",
                        isSelected = selectedFilter == "All",
                        onClick = { selectedFilter = "All" }
                    )
                    GlassFilterChip(
                        label = "Incorrect (${summary.wrongCount + summary.unansweredCount})",
                        isSelected = selectedFilter == "Incorrect",
                        onClick = { selectedFilter = "Incorrect" }
                    )
                    GlassFilterChip(
                        label = "Correct (${summary.correctCount})",
                        isSelected = selectedFilter == "Correct",
                        onClick = { selectedFilter = "Correct" }
                    )
                }
            }

            // Question Review Cards
            items(filteredItems) { item ->
                ReviewQuestionCard(item = item)
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun ReviewQuestionCard(
    item: QuestionReviewItem,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = Color(0x301E293B),
        borderBrush = GlassBorderBrush
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Question header: Number & status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Question ${item.questionNumber}",
                    color = AccentCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                val (badgeBg, badgeBorder, badgeText, badgeColor) = when {
                    item.isCorrect -> Quadruple(
                        CorrectGreenBg,
                        CorrectGreenBorder,
                        "Correct (+${item.pointsEarned})",
                        CorrectGreen
                    )
                    item.userAnswerIndex == null -> Quadruple(
                        Color(0x25FFFFFF),
                        Color(0x40FFFFFF),
                        "Skipped (0)",
                        Color(0xAAFFFFFF)
                    )
                    else -> Quadruple(
                        WrongRedBg,
                        WrongRedBorder,
                        "Incorrect (0)",
                        WrongRed
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg)
                        .border(1.dp, badgeBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Question Text
            Text(
                text = item.questionText,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 23.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Options List
            item.options.forEachIndexed { optIdx, optText ->
                val letter = ('A' + optIdx).toString()
                val isCorrectAnswer = (optIdx == item.correctAnswerIndex)
                val isUserSelection = (optIdx == item.userAnswerIndex)

                val (optBg, optBorder, icon) = when {
                    isCorrectAnswer -> Triple(
                        CorrectGreenBg,
                        BorderStroke(1.5.dp, CorrectGreenBorder),
                        Icons.Default.Check
                    )
                    isUserSelection && !item.isCorrect -> Triple(
                        WrongRedBg,
                        BorderStroke(1.5.dp, WrongRedBorder),
                        Icons.Default.Close
                    )
                    else -> Triple(
                        Color(0x18FFFFFF),
                        BorderStroke(1.dp, Color(0x25FFFFFF)),
                        null
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(optBg)
                        .border(optBorder.width, optBorder.brush, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isCorrectAnswer -> CorrectGreen
                                        isUserSelection && !item.isCorrect -> WrongRed
                                        else -> Color(0x25FFFFFF)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (icon != null) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Text(
                                    text = letter,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = optText,
                            color = if (isCorrectAnswer || isUserSelection) TextPrimary else TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = if (isCorrectAnswer || isUserSelection) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Explanation
            if (!item.explanation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = Color(0x221E293B),
                    borderBrush = GlassBorderBrush
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Explanation",
                                color = AccentCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.explanation,
                                color = TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
data class BorderStroke(val width: androidx.compose.ui.unit.Dp, val brush: androidx.compose.ui.graphics.Brush) {
    constructor(width: androidx.compose.ui.unit.Dp, color: Color) : this(width, androidx.compose.ui.graphics.SolidColor(color))
}
