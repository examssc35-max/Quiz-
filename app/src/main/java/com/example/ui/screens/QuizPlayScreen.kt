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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AppSoundManager
import com.example.data.model.QuestionType
import com.example.data.model.QuizMode
import com.example.data.model.QuizResultSummary
import com.example.engine.AnswerState
import com.example.engine.QuizEngine
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassConfirmDialog
import com.example.ui.components.GlassFillBlankInput
import com.example.ui.components.GlassOption
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassProgressBar
import com.example.ui.components.OptionVisualState
import com.example.ui.theme.AccentBlueLight
import com.example.ui.theme.AccentBluePrimary
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CorrectGreen
import com.example.ui.theme.CorrectGreenBg
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.PrimaryButtonGradient
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WrongRed
import com.example.ui.theme.WrongRedBg
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizPlayScreen(
    engine: QuizEngine,
    soundManager: AppSoundManager,
    soundEnabled: Boolean,
    voiceEnabled: Boolean,
    vibrationEnabled: Boolean,
    showExplanationsSetting: Boolean,
    onQuizCompleted: (summary: QuizResultSummary) -> Unit,
    onExitQuiz: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentQIndex by remember { mutableIntStateOf(engine.currentQuestionIndex) }
    var secondsLeft by remember { mutableIntStateOf(engine.timeRemainingSeconds) }
    var showSubmitConfirmDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var showNavigatorSheet by remember { mutableStateOf(false) }

    // Recomposition trigger on answer update
    var answersVersion by remember { mutableIntStateOf(0) }

    val hasTimer = engine.quizSchema.timeLimit > 0
    val isBengali = engine.quizSchema.category.contains("বাংলাদেশ") ||
            engine.quizSchema.title.contains("বাংলাদেশ") ||
            engine.quizSchema.questions.firstOrNull()?.question?.any { it in '\u0980'..'\u09FF' } == true

    // Timer countdown effect
    LaunchedEffect(hasTimer, secondsLeft) {
        if (hasTimer && secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
            engine.updateTimer(secondsLeft)
            if (secondsLeft == 0) {
                // Time's up!
                soundManager.playWrongSound(soundEnabled)
                val summary = engine.submitExam()
                onQuizCompleted(summary)
            }
        }
    }

    val currentQ = engine.currentQuestion
    val isAnswered = engine.isCurrentQuestionAnswered
    val isLocked = engine.isCurrentQuestionLocked

    val progress = (currentQIndex + 1).toFloat() / engine.totalQuestions

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Top Bar matching reference screen 4
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Close button (X)
                GlassCard(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    backgroundColor = Color(0x28FFFFFF),
                    borderBrush = GlassBorderBrush,
                    onClick = { showExitConfirmDialog = true }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Question X of Y
                Text(
                    text = "Question ${currentQIndex + 1} of ${engine.totalQuestions}",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Timer Pill (e.g. "00:27" or "Practice")
                val isUrgent = hasTimer && secondsLeft <= 30
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(
                            if (isUrgent) Color(0x55EF4444) else Color(0x28FFFFFF)
                        )
                        .border(
                            1.dp,
                            if (isUrgent) Color(0xFFEF4444) else Color(0x35FFFFFF),
                            RoundedCornerShape(50.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = if (isUrgent) Color(0xFFF87171) else Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val timerText = if (hasTimer) {
                            val mins = secondsLeft / 60
                            val secs = secondsLeft % 60
                            "%02d:%02d".format(mins, secs)
                        } else {
                            if (engine.mode == QuizMode.PRACTICE) "Practice" else "Exam"
                        }
                        Text(
                            text = timerText,
                            color = if (isUrgent) Color(0xFFF87171) else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Progress Bar
            GlassProgressBar(progress = progress)

            Spacer(modifier = Modifier.height(20.dp))

            // Main Question Scroll Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                if (currentQ != null) {
                    // Question Card matching reference
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
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
                                Text(
                                    text = "${currentQ.points} Point${if (currentQ.points > 1) "s" else ""}",
                                    color = AccentCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Icon(
                                    imageVector = Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = Color(0x88FFFFFF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = currentQ.questionText,
                                color = TextPrimary,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 28.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Question Content: Fill-in-the-Blank or MCQ Options
                    val qState = engine.getQuestionState(currentQIndex)

                    if (currentQ.type == QuestionType.FILL_BLANK) {
                        var typedAnswer by remember(currentQIndex, qState.userTextAnswer) {
                            mutableStateOf(qState.userTextAnswer ?: engine.userTextAnswers[currentQIndex] ?: "")
                        }

                        GlassFillBlankInput(
                            userAnswerText = typedAnswer,
                            onAnswerChange = { newText ->
                                typedAnswer = newText
                                if (engine.mode == QuizMode.EXAM) {
                                    engine.updateExamTextAnswer(newText)
                                }
                            },
                            mode = engine.mode,
                            isAnswered = qState.isAnswered,
                            answerState = qState.answerState,
                            isLocked = if (engine.mode == QuizMode.PRACTICE) qState.isLocked else false,
                            correctAnswerText = currentQ.fillBlankAnswer,
                            acceptedAnswers = currentQ.acceptedAnswers,
                            hasConfiguredAnswer = currentQ.hasConfiguredAnswer,
                            onSubmit = {
                                if (engine.mode == QuizMode.PRACTICE) {
                                    val feedback = engine.submitTextAnswer(typedAnswer)
                                    if (feedback != null) {
                                        if (feedback.isAnswerNotSet) {
                                            soundManager.playClickSound(soundEnabled)
                                        } else if (feedback.isCorrect) {
                                            soundManager.playCorrectSound(soundEnabled)
                                            soundManager.vibrate(vibrationEnabled, isSuccess = true)
                                            soundManager.speakAppreciation(
                                                isCorrect = true,
                                                streak = feedback.streak,
                                                voiceEnabled = voiceEnabled,
                                                isBengaliQuiz = isBengali
                                            )
                                        } else {
                                            soundManager.playWrongSound(soundEnabled)
                                            soundManager.vibrate(vibrationEnabled, isSuccess = false)
                                            soundManager.speakAppreciation(
                                                isCorrect = false,
                                                streak = feedback.streak,
                                                voiceEnabled = voiceEnabled,
                                                isBengaliQuiz = isBengali
                                            )
                                        }
                                    }
                                }
                            },
                            isBengali = isBengali
                        )
                    } else {
                        // Options A, B, C, D for MCQ
                        currentQ.options.forEachIndexed { optIndex, optText ->
                            val letter = ('A' + optIndex).toString()

                            val (visualState, statusText) = if (engine.mode == QuizMode.PRACTICE) {
                                if (qState.isAnswered) {
                                    when {
                                        qState.answerState == AnswerState.CORRECT && optIndex == currentQ.correctAnswerIndex -> {
                                            Pair(OptionVisualState.CORRECT_GREEN, if (isBengali) "✓ সঠিক উত্তর" else "✓ Correct")
                                        }
                                        qState.answerState == AnswerState.INCORRECT && optIndex == qState.selectedOptionIndex -> {
                                            Pair(OptionVisualState.WRONG_RED, if (isBengali) "✕ ভুল উত্তর" else "✕ Incorrect")
                                        }
                                        qState.answerState == AnswerState.INCORRECT && optIndex == currentQ.correctAnswerIndex -> {
                                            Pair(OptionVisualState.CORRECT_GREEN, if (isBengali) "✓ সঠিক উত্তর" else "✓ Correct")
                                        }
                                        else -> Pair(OptionVisualState.DEFAULT, null)
                                    }
                                } else {
                                    Pair(OptionVisualState.DEFAULT, null)
                                }
                            } else {
                                // Exam Mode: editable blue active state, no spoilers, no green/red
                                if (qState.selectedOptionIndex == optIndex) {
                                    Pair(OptionVisualState.SELECTED_BLUE, if (isBengali) "✓ নির্বাচিত" else "✓ Selected")
                                } else {
                                    Pair(OptionVisualState.DEFAULT, null)
                                }
                            }

                            GlassOption(
                                letter = letter,
                                text = optText,
                                state = visualState,
                                statusText = statusText,
                                enabled = if (engine.mode == QuizMode.PRACTICE) !qState.isLocked else true,
                                onClick = {
                                    val feedback = engine.selectOption(optIndex)

                                    if (engine.mode == QuizMode.PRACTICE && feedback != null) {
                                        if (feedback.isCorrect) {
                                            soundManager.playCorrectSound(soundEnabled)
                                            soundManager.vibrate(vibrationEnabled, isSuccess = true)
                                        } else {
                                            soundManager.playWrongSound(soundEnabled)
                                            soundManager.vibrate(vibrationEnabled, isSuccess = false)
                                        }
                                        soundManager.speakAppreciation(
                                            isCorrect = feedback.isCorrect,
                                            streak = feedback.streak,
                                            voiceEnabled = voiceEnabled,
                                            isBengaliQuiz = isBengali
                                        )
                                    } else if (engine.mode == QuizMode.EXAM) {
                                        soundManager.playClickSound(soundEnabled)
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // Practice Mode Feedback Pill & Explanation Card
                    if (engine.mode == QuizMode.PRACTICE && qState.isAnswered) {
                        val isCorrect = qState.answerState == AnswerState.CORRECT

                        if (currentQ.type == QuestionType.MCQ) {
                            Spacer(modifier = Modifier.height(8.dp))

                            // Feedback Banner for MCQ
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isCorrect) CorrectGreenBg else WrongRedBg)
                                    .border(
                                        1.dp,
                                        if (isCorrect) CorrectGreen else WrongRed,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isCorrect) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        tint = if (isCorrect) CorrectGreen else WrongRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (isCorrect) {
                                            if (isBengali) "সঠিক উত্তর! চমৎকার!" else "Correct Answer! Well done!"
                                        } else {
                                            if (isBengali) "ভুল উত্তর! সঠিক উত্তরটি সবুজ চিহ্নিত।" else "Incorrect! Correct answer highlighted in green."
                                        },
                                        color = if (isCorrect) Color(0xFF6EE7B7) else Color(0xFFFCA5A5),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Explanation Card for both MCQ and Fill Blank
                        if (showExplanationsSetting && !currentQ.explanation.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                backgroundColor = Color(0x221E293B),
                                borderBrush = GlassBorderBrush
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Explanation",
                                        tint = AccentCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = if (isBengali) "ব্যাখ্যা" else "Explanation",
                                            color = AccentCyan,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = currentQ.explanation,
                                            color = TextSecondary,
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Bottom Navigation Controls matching reference screen 4
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous Button (circular glass card)
                GlassCard(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    backgroundColor = Color(0x28FFFFFF),
                    borderBrush = GlassBorderBrush,
                    onClick = {
                        if (engine.previousQuestion()) {
                            currentQIndex = engine.currentQuestionIndex
                            soundManager.playClickSound(soundEnabled)
                        }
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous",
                            tint = if (currentQIndex > 0) Color.White else Color(0x44FFFFFF),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // In Exam Mode: Question Navigator Sheet Button
                if (engine.mode == QuizMode.EXAM) {
                    GlassCard(
                        modifier = Modifier
                            .height(52.dp)
                            .padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(26.dp),
                        backgroundColor = Color(0x25FFFFFF),
                        borderBrush = GlassBorderBrush,
                        onClick = { showNavigatorSheet = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = "Navigator",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val answeredCount = (0 until engine.totalQuestions).count { engine.getQuestionState(it).isAnswered }
                            Text(
                                text = "$answeredCount/${engine.totalQuestions}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Next or Submit Pill Button
                val isLastQuestion = currentQIndex == engine.totalQuestions - 1

                if (isLastQuestion || (engine.mode == QuizMode.EXAM && isLastQuestion)) {
                    GlassPrimaryButton(
                        text = if (engine.mode == QuizMode.EXAM) "Submit Exam" else "Finish",
                        onClick = {
                            if (engine.mode == QuizMode.EXAM) {
                                showSubmitConfirmDialog = true
                            } else {
                                val summary = engine.submitExam()
                                onQuizCompleted(summary)
                            }
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                } else {
                    GlassPrimaryButton(
                        text = "Next",
                        onClick = {
                            if (engine.nextQuestion()) {
                                currentQIndex = engine.currentQuestionIndex
                                soundManager.playClickSound(soundEnabled)
                            }
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }

        // Exam Question Navigator BottomSheet
        if (showNavigatorSheet) {
            ModalBottomSheet(
                onDismissRequest = { showNavigatorSheet = false },
                containerColor = Color(0xFA0F172A),
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "Question Navigator",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Jump directly to any question. Answered questions are highlighted.",
                        color = TextMuted,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(engine.totalQuestions) { qIdx ->
                            val isCurrent = (qIdx == currentQIndex)
                            val isQAnswered = engine.getQuestionState(qIdx).isAnswered

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isCurrent -> AccentBluePrimary
                                            isQAnswered -> Color(0xFF2563EB).copy(alpha = 0.5f)
                                            else -> Color(0x25FFFFFF)
                                        }
                                    )
                                    .border(
                                        if (isCurrent) 2.dp else 1.dp,
                                        when {
                                            isCurrent -> Color.White
                                            isQAnswered -> Color(0xFF60A5FA).copy(alpha = 0.7f)
                                            else -> Color(0x35FFFFFF)
                                        },
                                        CircleShape
                                    )
                                    .clickable {
                                        engine.jumpToQuestion(qIdx)
                                        currentQIndex = qIdx
                                        showNavigatorSheet = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${qIdx + 1}",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    val totalAnswered = (0 until engine.totalQuestions).count { engine.getQuestionState(it).isAnswered }
                    GlassPrimaryButton(
                        text = "Submit Exam ($totalAnswered/${engine.totalQuestions} answered)",
                        onClick = {
                            showNavigatorSheet = false
                            showSubmitConfirmDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }

        // Submit Exam Confirmation Dialog
        if (showSubmitConfirmDialog) {
            val totalAnswered = (0 until engine.totalQuestions).count { engine.getQuestionState(it).isAnswered }
            val unansweredCount = engine.totalQuestions - totalAnswered
            val msg = if (unansweredCount > 0) {
                "You have $unansweredCount unanswered question${if (unansweredCount > 1) "s" else ""}. Are you sure you want to submit your exam now?"
            } else {
                "Are you ready to submit your exam and view your score?"
            }

            GlassConfirmDialog(
                title = "Submit Exam?",
                message = msg,
                confirmText = "Submit",
                onConfirm = {
                    showSubmitConfirmDialog = false
                    val summary = engine.submitExam()
                    onQuizCompleted(summary)
                },
                onDismiss = { showSubmitConfirmDialog = false }
            )
        }

        // Exit / Save Progress Confirmation Dialog
        if (showExitConfirmDialog) {
            GlassConfirmDialog(
                title = "Leave Quiz?",
                message = "Your unfinished progress is saved automatically. You can continue anytime from the Home screen.",
                confirmText = "Leave & Save",
                dismissText = "Keep Playing",
                onConfirm = {
                    showExitConfirmDialog = false
                    onExitQuiz()
                },
                onDismiss = { showExitConfirmDialog = false }
            )
        }
    }
}
