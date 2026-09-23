package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.QuizMode
import com.example.engine.AnswerState
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
fun GlassFillBlankInput(
    userAnswerText: String,
    onAnswerChange: (String) -> Unit,
    mode: QuizMode,
    isAnswered: Boolean,
    answerState: AnswerState,
    isLocked: Boolean,
    correctAnswerText: String,
    onSubmit: () -> Unit,
    isBengali: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)

    val isPractice = mode == QuizMode.PRACTICE
    val isCorrect = isAnswered && answerState == AnswerState.CORRECT
    val isIncorrect = isAnswered && answerState == AnswerState.INCORRECT

    // Background color
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isPractice && isCorrect -> CorrectGreenBg
            isPractice && isIncorrect -> WrongRedBg
            !isPractice && userAnswerText.isNotBlank() -> Color(0x3D2563EB)
            else -> GlassCardSurface
        },
        animationSpec = tween(durationMillis = 180),
        label = "fill_blank_bg"
    )

    // Border stroke
    val borderStroke = when {
        isPractice && isCorrect -> BorderStroke(2.dp, CorrectGreenBorder)
        isPractice && isIncorrect -> BorderStroke(2.dp, WrongRedBorder)
        !isPractice && userAnswerText.isNotBlank() -> BorderStroke(
            2.dp,
            Brush.linearGradient(listOf(Color(0xFF60A5FA), Color(0xFF2563EB)))
        )
        else -> BorderStroke(1.dp, GlassBorderBrush)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Main glass input container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(backgroundColor)
                .border(borderStroke, shape)
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header row: prompt label + answer status pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isBengali) "আপনার উত্তর লিখুন" else "Type your answer",
                            color = AccentCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Status Pill
                    if (isPractice && isAnswered) {
                        val pillBg = if (isCorrect) CorrectGreenBg else WrongRedBg
                        val pillBorder = if (isCorrect) CorrectGreenBorder else WrongRedBorder
                        val pillTextColor = if (isCorrect) Color(0xFF6EE7B7) else Color(0xFFFCA5A5)
                        val statusText = if (isCorrect) {
                            if (isBengali) "✓ সঠিক উত্তর" else "✓ Correct"
                        } else {
                            if (isBengali) "✕ ভুল উত্তর" else "✕ Incorrect"
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(pillBg)
                                .border(1.dp, pillBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isCorrect) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (isCorrect) CorrectGreen else WrongRed,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = statusText,
                                    color = pillTextColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (!isPractice && userAnswerText.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x333B82F6))
                                .border(1.dp, Color(0xFF60A5FA), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isBengali) "✓ সংরক্ষিত" else "✓ Recorded",
                                color = Color(0xFF93C5FD),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // User answer text field
                OutlinedTextField(
                    value = userAnswerText,
                    onValueChange = {
                        if (!isLocked) {
                            onAnswerChange(it)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fill_blank_input"),
                    enabled = !isLocked,
                    readOnly = isLocked,
                    placeholder = {
                        Text(
                            text = if (isBengali) "এখানে উত্তর লিখুন..." else "Type your answer here...",
                            color = TextMuted,
                            fontSize = 15.sp
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (isPractice && !isLocked) ImeAction.Done else ImeAction.Default
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (isPractice && !isLocked) {
                                onSubmit()
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isPractice) AccentCyan else Color(0xFF60A5FA),
                        unfocusedBorderColor = Color(0x30FFFFFF),
                        disabledBorderColor = Color(0x20FFFFFF),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        disabledTextColor = TextPrimary,
                        cursorColor = AccentCyan
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Practice Mode: Submit button when unlocked
        if (isPractice && !isLocked) {
            GlassPrimaryButton(
                text = if (isBengali) "উত্তর জমা দিন" else "Submit Answer",
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("submit_answer_button")
            )
        }

        // Practice Mode: When answered & incorrect, show the correct answer immediately
        if (isPractice && isIncorrect) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CorrectGreenBg.copy(alpha = 0.25f))
                    .border(1.dp, CorrectGreenBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CorrectGreen.copy(alpha = 0.2f))
                            .border(1.dp, CorrectGreen, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = CorrectGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = if (isBengali) "সঠিক উত্তর:" else "Correct Answer:",
                            color = Color(0xFF6EE7B7),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = correctAnswerText,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
