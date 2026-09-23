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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.local.entity.QuizEntity
import com.example.data.model.QuizMode
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDifficultyBadge
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassTopBar
import com.example.ui.theme.AccentBlueLight
import com.example.ui.theme.AccentBluePrimary
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun QuizDetailScreen(
    quiz: QuizEntity,
    onBackClick: () -> Unit,
    onStartMode: (mode: QuizMode) -> Unit,
    onToggleFavorite: () -> Unit,
    onEditClick: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMode by remember { mutableStateOf(QuizMode.PRACTICE) }

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
