package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ai.AIManager
import com.example.ai.ChatMessage
import com.example.ai.QuestionAiContext
import com.example.data.model.QuestionReviewItem
import com.example.data.model.QuestionType
import com.example.data.model.QuizResultSummary
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassFilterChip
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassTopBar
import com.example.ui.theme.AccentBluePrimary
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CorrectGreen
import com.example.ui.theme.CorrectGreenBg
import com.example.ui.theme.CorrectGreenBorder
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.PrimaryButtonGradient
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WrongRed
import com.example.ui.theme.WrongRedBg
import com.example.ui.theme.WrongRedBorder
import kotlinx.coroutines.launch

@Composable
fun ReviewAnswersScreen(
    summary: QuizResultSummary,
    aiManager: AIManager? = null,
    onUpdateQuestionAnswer: ((questionId: String, newAnswer: String, newAcceptedAnswers: List<String>, newOptionIndex: Int) -> Unit)? = null,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("All") }
    var activeChatQuestion by remember { mutableStateOf<QuestionReviewItem?>(null) }
    var activeEditQuestion by remember { mutableStateOf<QuestionReviewItem?>(null) }

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
                ReviewQuestionCard(
                    item = item,
                    onAskAiClick = { activeChatQuestion = item },
                    onEditAnswerClick = { activeEditQuestion = item }
                )
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // AI Follow-up Chat BottomSheet
    activeChatQuestion?.let { item ->
        val context = QuestionAiContext(
            questionId = item.questionId.ifEmpty { item.questionNumber.toString() },
            questionText = item.questionText,
            type = item.questionType,
            options = item.options,
            storedAnswer = item.correctAnswerText,
            acceptedAnswers = item.acceptedAnswers,
            storedCorrectOptionIndex = item.correctAnswerIndex,
            userAnswerText = item.userAnswerText,
            userOptionIndex = item.userAnswerIndex,
            explanation = item.explanation ?: item.banglaExplanation,
            quizTitle = summary.quizTitle
        )

        AiFollowUpBottomSheet(
            context = context,
            item = item,
            aiManager = aiManager,
            onDismiss = { activeChatQuestion = null },
            onOpenEditDialog = {
                activeChatQuestion = null
                activeEditQuestion = item
            }
        )
    }

    // Edit Stored Question Answer in Room Database
    activeEditQuestion?.let { item ->
        FixStoredAnswerDialog(
            item = item,
            quizId = summary.quizId,
            onDismiss = { activeEditQuestion = null },
            onSave = { newAns, newAccepted, newOptIdx ->
                val qId = item.questionId.ifEmpty { item.questionNumber.toString() }
                onUpdateQuestionAnswer?.invoke(qId, newAns, newAccepted, newOptIdx)
                activeEditQuestion = null
            }
        )
    }
}

@Composable
fun ReviewQuestionCard(
    item: QuestionReviewItem,
    onAskAiClick: () -> Unit,
    onEditAnswerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSkipped = if (item.questionType == QuestionType.FILL_BLANK) {
        item.userAnswerText.isNullOrBlank()
    } else {
        item.userAnswerIndex == null
    }

    val isAnswerNotSet = item.isAnswerNotSet || (item.questionType == QuestionType.FILL_BLANK && item.acceptedAnswers.isEmpty() && (item.correctAnswerText.isEmpty() || item.correctAnswerText == "Answer not available"))

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Question ${item.questionNumber}",
                        color = AccentCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (item.questionType == QuestionType.FILL_BLANK) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x3338BDF8))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Fill in Blank",
                                color = AccentCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                val (badgeBg, badgeBorder, badgeText, badgeColor) = when {
                    isAnswerNotSet -> Quadruple(
                        Color(0x2538BDF8),
                        Color(0x5038BDF8),
                        "Unanswered/Answer Not Set",
                        Color(0xFFBAE6FD)
                    )
                    item.isCorrect -> Quadruple(
                        CorrectGreenBg,
                        CorrectGreenBorder,
                        "Correct (+${item.pointsEarned})",
                        CorrectGreen
                    )
                    isSkipped -> Quadruple(
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

            if (item.questionType == QuestionType.FILL_BLANK) {
                // User's typed answer
                val userText = item.userAnswerText?.trim()
                val (userBg, userBorder, userIconTint) = when {
                    isAnswerNotSet -> Triple(Color(0x1838BDF8), Color(0x3538BDF8), AccentCyan)
                    item.isCorrect -> Triple(CorrectGreenBg, CorrectGreenBorder, CorrectGreen)
                    isSkipped -> Triple(Color(0x18FFFFFF), Color(0x25FFFFFF), TextMuted)
                    else -> Triple(WrongRedBg, WrongRedBorder, WrongRed)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(userBg)
                        .border(1.dp, userBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                isAnswerNotSet -> Icons.Default.Info
                                item.isCorrect -> Icons.Default.Check
                                isSkipped -> Icons.Default.Edit
                                else -> Icons.Default.Close
                            },
                            contentDescription = null,
                            tint = userIconTint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Your Answer:",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (userText.isNullOrEmpty()) "(No answer entered)" else userText,
                                color = if (userText.isNullOrEmpty()) TextMuted else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // If answer is not configured, show "Answer not available"
                if (isAnswerNotSet) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x1838BDF8))
                            .border(1.dp, Color(0x3538BDF8), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Answer not available (Not judged)",
                                color = Color(0xFFBAE6FD),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else if (!item.isCorrect) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CorrectGreenBg.copy(alpha = 0.35f))
                            .border(1.dp, CorrectGreenBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = CorrectGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                val hasMultiple = item.acceptedAnswers.size > 1
                                Text(
                                    text = if (hasMultiple) "Accepted Answers:" else "Correct Answer:",
                                    color = Color(0xFF6EE7B7),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (hasMultiple) item.acceptedAnswers.joinToString(", ") else item.correctAnswerText,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                // MCQ Options List
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
            }

            // Bangla Explanation (if available)
            if (!item.banglaExplanation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = if (item.isCorrect) CorrectGreenBg.copy(alpha = 0.20f) else Color(0x221E293B),
                    borderBrush = if (item.isCorrect) SolidColor(CorrectGreenBorder) else GlassBorderBrush
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = if (item.isCorrect) Icons.Default.Check else Icons.Default.Info,
                            contentDescription = "Explanation",
                            tint = if (item.isCorrect) CorrectGreen else AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "বাংলা ব্যাখ্যা (Bangla Explanation)",
                                color = if (item.isCorrect) Color(0xFF6EE7B7) else AccentCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = item.banglaExplanation,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            }

            // General / Stored Explanation
            if (!item.explanation.isNullOrBlank() && item.explanation != item.banglaExplanation) {
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

            // Action Row: "Ask AI Tutor" & "Fix Answer in Quiz"
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ask AI Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF2563EB).copy(alpha = 0.35f), Color(0xFF06B6D4).copy(alpha = 0.35f))
                            )
                        )
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .clickable { onAskAiClick() }
                        .padding(vertical = 9.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Ask AI",
                            tint = AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ask AI Tutor",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Edit Stored Answer Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x18FFFFFF))
                        .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(12.dp))
                        .clickable { onEditAnswerClick() }
                        .padding(vertical = 9.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Fix Answer",
                            tint = Color(0xCCFFFFFF),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Fix Answer",
                            color = Color(0xDDFFFFFF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interactive Modal Bottom Sheet allowing conversational questions and deep doubts
 * regarding this specific question with the active AI Provider (Gemini / OpenAI / Claude).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiFollowUpBottomSheet(
    context: QuestionAiContext,
    item: QuestionReviewItem,
    aiManager: AIManager?,
    onDismiss: () -> Unit,
    onOpenEditDialog: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var inputQuery by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                role = "assistant",
                content = "নমস্কার! এই প্রশ্নটি নিয়ে তোমার যেকোনো প্রশ্ন বা দ্বিধা থাকলে আমাকে নির্দ্বিধায় জিজ্ঞেস করতে পারো। কেন তোমার উত্তরটি ঠিক বা ভুল হলো, এর পেছনের ব্যাকরণ, তথ্য বা নিয়ম আমি সহজ বাংলায় বুঝিয়ে দেব।"
            )
        )
    }

    val listState = rememberLazyListState()

    fun sendMessage(queryText: String) {
        val trimmed = queryText.trim()
        if (trimmed.isEmpty() || isThinking) return

        messages.add(ChatMessage(role = "user", content = trimmed))
        inputQuery = ""
        isThinking = true

        coroutineScope.launch {
            val history = messages.toList()
            val replyResult = if (aiManager != null) {
                aiManager.chatFollowUp(context, history, trimmed)
            } else {
                Result.success("প্রশ্নটির সঠিক উত্তর হলো ‘${context.storedAnswer}’। অনুশীলনে মনোযোগ দিন!")
            }

            val reply = replyResult.getOrElse {
                "দুঃখিত, এই মুহূর্তে উত্তর তৈরিতে সাময়িক সমস্যা হচ্ছে। অনুগ্রহ করে পুনরায় চেষ্টা করুন।"
            }

            messages.add(ChatMessage(role = "assistant", content = reply))
            isThinking = false
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB).copy(alpha = 0.3f))
                            .border(1.dp, AccentCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AI Question Tutor",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "এআই শিক্ষক ও ব্যাকরণ সহায়িকা",
                            color = AccentCyan,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Context snippet preview
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                backgroundColor = Color(0x221E293B),
                borderBrush = GlassBorderBrush
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "প্রশ্ন: ${context.questionText}",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val userAns = context.userAnswerText ?: context.options.getOrNull(context.userOptionIndex ?: -1)
                        Text(
                            text = "তোমার উত্তর: ${userAns ?: "(কিছুই নয়)"}",
                            color = if (item.isCorrect) CorrectGreen else WrongRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "সংরক্ষিত উত্তর: ${context.storedAnswer}",
                            color = Color(0xFF93C5FD),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Prompt Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickChatChip(label = "কেন ভুল হলো?") { sendMessage("কেন আমার উত্তরটি ভুল হলো ব্যাখ্যা করো?") }
                QuickChatChip(label = "সহজ ভাষায় বুঝাও") { sendMessage("এই প্রশ্নটি এবং এর উত্তর সহজ ভাষায় বুঝিয়ে দাও।") }
                QuickChatChip(label = "অন্য কী হতে পারে?") { sendMessage("এখানে অন্য আর কী কী উত্তর গ্রহণযোগ্য হতে পারে?") }
                QuickChatChip(label = "JSON কি ঠিক?") { sendMessage("এই প্রশ্নের সংরক্ষিত সঠিক উত্তরটি কি সঠিক? নাকি প্রশ্নে কোনো ভুল আছে?") }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chat Message Thread
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(message = msg)
                }

                if (isThinking) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x221E293B))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = AccentCyan,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "এআই শিক্ষক বিশ্লেষণ করছে...",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Input bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputQuery,
                    onValueChange = { inputQuery = it },
                    placeholder = {
                        Text(
                            text = "বাংলা বা ইংরেজিতে প্রশ্ন করো...",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = Color(0x35FFFFFF),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendMessage(inputQuery) })
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(PrimaryButtonGradient)
                        .clickable(enabled = inputQuery.isNotBlank() && !isThinking) {
                            sendMessage(inputQuery)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick button to fix answer in database
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "সংরক্ষিত উত্তরে ভুল থাকলে কুইজে সংশোধন করুন",
                    color = AccentCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { onOpenEditDialog() }
                        .padding(4.dp)
                )
            }
        }
    }
}

@Composable
fun QuickChatChip(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x25FFFFFF))
            .border(1.dp, Color(0x35FFFFFF), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFFE2E8F0),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) {
                        Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF0284C7)))
                    } else {
                        SolidColor(Color(0x351E293B))
                    }
                )
                .border(
                    1.dp,
                    if (isUser) Color(0xFF60A5FA).copy(alpha = 0.5f) else Color(0x3538BDF8),
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.content,
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

/**
 * Dialog to adjust or add accepted answers for a question in Room database.
 */
@Composable
fun FixStoredAnswerDialog(
    item: QuestionReviewItem,
    quizId: String,
    onDismiss: () -> Unit,
    onSave: (newAnswer: String, newAcceptedAnswers: List<String>, newOptionIndex: Int) -> Unit
) {
    val context = LocalContext.current
    var primaryAnswer by remember { mutableStateOf(item.correctAnswerText.ifEmpty { item.acceptedAnswers.firstOrNull().orEmpty() }) }
    var acceptedAnswersText by remember { mutableStateOf(item.acceptedAnswers.joinToString(", ")) }
    var selectedOptIndex by remember { mutableStateOf(item.correctAnswerIndex) }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            backgroundColor = Color(0xFF0F172A)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Text(
                    text = "Fix Question Answer in Quiz",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Update the stored answer in your local quiz library so future attempts recognize it.",
                    color = TextMuted,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Question: ${item.questionText}",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (item.questionType == QuestionType.FILL_BLANK) {
                    Text(
                        text = "Primary Stored Answer:",
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = primaryAnswer,
                        onValueChange = { primaryAnswer = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = Color(0x35FFFFFF),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Accepted Alternatives (comma-separated):",
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = acceptedAnswersText,
                        onValueChange = { acceptedAnswersText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = Color(0x35FFFFFF),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text("e.g. harm, damage, loss", color = TextMuted) }
                    )
                } else {
                    Text(
                        text = "Select Correct Option:",
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    item.options.forEachIndexed { idx, opt ->
                        val isSelected = (idx == selectedOptIndex)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFF2563EB).copy(alpha = 0.4f) else Color(0x18FFFFFF))
                                .border(1.dp, if (isSelected) AccentCyan else Color(0x25FFFFFF), RoundedCornerShape(10.dp))
                                .clickable { selectedOptIndex = idx }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${('A' + idx)}. $opt",
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x20FFFFFF))
                            .clickable { onDismiss() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Cancel", color = TextSecondary, fontSize = 14.sp)
                    }

                    GlassPrimaryButton(
                        text = "Save Fix",
                        onClick = {
                            val acceptedList = acceptedAnswersText
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .toMutableList()

                            if (primaryAnswer.isNotBlank() && primaryAnswer !in acceptedList) {
                                acceptedList.add(0, primaryAnswer)
                            }

                            onSave(primaryAnswer, acceptedList, selectedOptIndex)
                            Toast.makeText(context, "Quiz answer updated successfully!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
data class BorderStroke(val width: androidx.compose.ui.unit.Dp, val brush: androidx.compose.ui.graphics.Brush) {
    constructor(width: androidx.compose.ui.unit.Dp, color: Color) : this(width, androidx.compose.ui.graphics.SolidColor(color))
}
