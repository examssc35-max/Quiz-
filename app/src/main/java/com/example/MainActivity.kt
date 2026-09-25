package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audio.AppSoundManager
import com.example.data.local.AppSettings
import com.example.data.repository.QuizRepository
import com.example.ui.QuizAppViewModel
import com.example.ui.QuizAppViewModelFactory
import com.example.ui.components.GlassBottomNavigation
import com.example.ui.components.GlassScaffold
import com.example.ui.navigation.Screen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImportQuizScreen
import com.example.ui.screens.QuizDetailScreen
import com.example.ui.screens.QuizEditorScreen
import com.example.ui.screens.QuizListScreen
import com.example.ui.screens.QuizPlayScreen
import com.example.ui.screens.ResultScreen
import com.example.ui.screens.ReviewAnswersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatisticsScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val repository = QuizRepository.getInstance(context)
            val appSettings = AppSettings.getInstance(context)
            val soundManager = AppSoundManager(context)

            val viewModel: QuizAppViewModel = viewModel(
                factory = QuizAppViewModelFactory(repository, appSettings, soundManager)
            )

            MyApplicationTheme {
                QuizAppRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun QuizAppRoot(viewModel: QuizAppViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    val allQuizzes by viewModel.allQuizzes.collectAsState()
    val favoriteQuizzes by viewModel.favoriteQuizzes.collectAsState()
    val recentQuizzes by viewModel.recentQuizzes.collectAsState()
    val unfinishedQuiz by viewModel.latestUnfinishedQuiz.collectAsState()
    val allAttempts by viewModel.allAttempts.collectAsState()
    val activeEngine by viewModel.activeEngine.collectAsState()
    val quizFilter by viewModel.quizFilter.collectAsState()

    val soundEnabled by viewModel.appSettings.soundEnabled.collectAsState()
    val voiceEnabled by viewModel.appSettings.voiceEnabled.collectAsState()
    val vibrationEnabled by viewModel.appSettings.vibrationEnabled.collectAsState()
    val showExplanations by viewModel.appSettings.showExplanations.collectAsState()
    val blurIntensity by viewModel.appSettings.blurIntensity.collectAsState()
    val operationState by viewModel.operationState.collectAsState()

    val context = LocalContext.current

    // Display user-friendly notification for repository operations
    LaunchedEffect(operationState) {
        operationState.errorMessage?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearOperationState()
        }
        operationState.successMessage?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearOperationState()
        }
    }

    // Handle system back navigation
    BackHandler(enabled = currentScreen !is Screen.Home) {
        when (currentScreen) {
            is Screen.Welcome -> Unit
            is Screen.Quizzes, is Screen.Statistics, is Screen.Settings -> {
                viewModel.navigateTo(Screen.Home)
            }
            is Screen.QuizDetail -> {
                viewModel.navigateTo(Screen.Quizzes)
            }
            is Screen.QuizPlay -> {
                viewModel.saveUnfinishedCurrentQuiz()
            }
            is Screen.Result -> {
                viewModel.navigateTo(Screen.Home)
            }
            is Screen.Review -> {
                val lastSummary = allAttempts.firstOrNull()?.let { attempt ->
                    val reviewItems = viewModel.repository.parseReviewItems(attempt.reviewDataJson)
                    com.example.data.model.QuizResultSummary(
                        quizId = attempt.quizId,
                        quizTitle = attempt.quizTitle,
                        mode = if (attempt.mode == com.example.data.model.QuizMode.EXAM.name) com.example.data.model.QuizMode.EXAM else com.example.data.model.QuizMode.PRACTICE,
                        totalQuestions = attempt.totalQuestions,
                        correctCount = attempt.correctCount,
                        wrongCount = attempt.wrongCount,
                        unansweredCount = attempt.unansweredCount,
                        score = attempt.score,
                        maxScore = attempt.maxScore,
                        accuracy = attempt.accuracy,
                        timeTakenSeconds = attempt.timeTakenSeconds,
                        reviewItems = reviewItems
                    )
                }
                if (lastSummary != null) {
                    viewModel.navigateTo(Screen.Result(lastSummary))
                } else {
                    viewModel.navigateTo(Screen.Home)
                }
            }
            is Screen.ImportQuiz -> {
                viewModel.navigateTo(Screen.Home)
            }
            is Screen.QuizEditor -> {
                viewModel.navigateTo(Screen.Quizzes)
            }
            else -> viewModel.navigateTo(Screen.Home)
        }
    }

    val isMainTabScreen = currentScreen is Screen.Home ||
            currentScreen is Screen.Quizzes ||
            currentScreen is Screen.Statistics ||
            currentScreen is Screen.Settings

    GlassScaffold(blurIntensity = blurIntensity) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Active Screen Content
            when (val screen = currentScreen) {
                is Screen.Welcome -> {
                    WelcomeScreen(
                        onStartClick = { viewModel.navigateTo(Screen.Home) }
                    )
                }
                is Screen.Home -> {
                    HomeScreen(
                        quizzes = allQuizzes,
                        favoriteCount = favoriteQuizzes.size,
                        recentCount = recentQuizzes.size,
                        unfinishedQuiz = unfinishedQuiz,
                        onImportClick = { viewModel.navigateTo(Screen.ImportQuiz) },
                        onNavigateQuizzes = { filter -> viewModel.openQuizzesWithFilter(filter) },
                        onNavigateCategory = { cat -> viewModel.openQuizzesWithFilter(cat) },
                        onNavigateStats = { viewModel.navigateTo(Screen.Statistics) },
                        onResumeQuiz = { quizId, mode ->
                            viewModel.startQuiz(quizId, mode, resume = true)
                        },
                        onStartQuiz = { quizId ->
                            viewModel.navigateTo(Screen.QuizDetail(quizId))
                        }
                    )
                }
                is Screen.Quizzes -> {
                    QuizListScreen(
                        quizzes = allQuizzes,
                        initialFilter = quizFilter,
                        onQuizClick = { quizId -> viewModel.navigateTo(Screen.QuizDetail(quizId)) },
                        onEditQuiz = { quizId -> viewModel.navigateTo(Screen.QuizEditor(quizId)) },
                        onDuplicateQuiz = { quizId -> viewModel.duplicateQuiz(quizId) },
                        onDeleteQuiz = { quizId -> viewModel.deleteQuiz(quizId) },
                        onToggleFavorite = { quizId, current -> viewModel.toggleFavorite(quizId, current) },
                        onExportQuiz = { quizId -> viewModel.shareQuizJson(context, quizId) },
                        onCreateQuizClick = { viewModel.navigateTo(Screen.QuizEditor(null)) }
                    )
                }
                is Screen.QuizDetail -> {
                    val quiz = allQuizzes.find { it.id == screen.quizId }
                    if (quiz != null) {
                        QuizDetailScreen(
                            quiz = quiz,
                            onBackClick = { viewModel.navigateTo(Screen.Quizzes) },
                            onStartMode = { mode -> viewModel.startQuiz(quiz.id, mode, resume = false) },
                            onToggleFavorite = { viewModel.toggleFavorite(quiz.id, quiz.isFavorite) },
                            onEditClick = { viewModel.navigateTo(Screen.QuizEditor(quiz.id)) },
                            onExportClick = { viewModel.shareQuizJson(context, quiz.id) }
                        )
                    }
                }
                is Screen.QuizPlay -> {
                    val engine = activeEngine
                    if (engine != null) {
                        QuizPlayScreen(
                            engine = engine,
                            soundManager = viewModel.soundManager,
                            soundEnabled = soundEnabled,
                            voiceEnabled = voiceEnabled,
                            vibrationEnabled = vibrationEnabled,
                            showExplanationsSetting = showExplanations,
                            onQuizCompleted = { summary -> viewModel.onQuizCompleted(summary) },
                            onExitQuiz = { viewModel.saveUnfinishedCurrentQuiz() }
                        )
                    }
                }
                is Screen.Result -> {
                    ResultScreen(
                        summary = screen.summary,
                        onReviewClick = { viewModel.navigateTo(Screen.Review(screen.summary)) },
                        onTryAgainClick = {
                            viewModel.startQuiz(screen.summary.quizId, screen.summary.mode, resume = false)
                        },
                        onBackHomeClick = { viewModel.navigateTo(Screen.Home) }
                    )
                }
                is Screen.Review -> {
                    ReviewAnswersScreen(
                        summary = screen.summary,
                        onBackClick = { viewModel.navigateTo(Screen.Result(screen.summary)) }
                    )
                }
                is Screen.ImportQuiz -> {
                    ImportQuizScreen(
                        onBackClick = { viewModel.navigateTo(Screen.Home) },
                        onImportSuccess = { newId -> viewModel.navigateTo(Screen.QuizDetail(newId)) },
                        onSaveQuiz = { schema -> viewModel.saveQuiz(schema) }
                    )
                }
                is Screen.QuizEditor -> {
                    val existing = screen.existingQuizId?.let { id -> allQuizzes.find { it.id == id } }
                    QuizEditorScreen(
                        existingQuiz = existing,
                        onBackClick = { viewModel.navigateTo(Screen.Quizzes) },
                        onSaveQuiz = { schema ->
                            viewModel.saveQuiz(schema, existing?.id)
                            viewModel.navigateTo(Screen.Quizzes)
                        }
                    )
                }
                is Screen.Statistics -> {
                    StatisticsScreen(
                        attempts = allAttempts,
                        onResetStatistics = { viewModel.resetStatistics() },
                        onAttemptClick = { attempt ->
                            val reviewItems = viewModel.repository.parseReviewItems(attempt.reviewDataJson)
                            val summary = com.example.data.model.QuizResultSummary(
                                quizId = attempt.quizId,
                                quizTitle = attempt.quizTitle,
                                mode = if (attempt.mode == com.example.data.model.QuizMode.EXAM.name) com.example.data.model.QuizMode.EXAM else com.example.data.model.QuizMode.PRACTICE,
                                totalQuestions = attempt.totalQuestions,
                                correctCount = attempt.correctCount,
                                wrongCount = attempt.wrongCount,
                                unansweredCount = attempt.unansweredCount,
                                score = attempt.score,
                                maxScore = attempt.maxScore,
                                accuracy = attempt.accuracy,
                                timeTakenSeconds = attempt.timeTakenSeconds,
                                reviewItems = reviewItems
                            )
                            viewModel.navigateTo(Screen.Review(summary))
                        }
                    )
                }
                is Screen.Settings -> {
                    SettingsScreen(
                        appSettings = viewModel.appSettings,
                        soundEnabled = soundEnabled,
                        voiceEnabled = voiceEnabled,
                        vibrationEnabled = vibrationEnabled,
                        showExplanations = showExplanations,
                        blurIntensity = blurIntensity,
                        onResetStats = { viewModel.resetStatistics() },
                        onRestoreDefaultQuizzes = { viewModel.restoreSampleQuizzes() },
                        onClearUnfinished = { viewModel.clearUnfinished() }
                    )
                }
            }

            // Floating Bottom Navigation Bar (Shown on Home, Quizzes, Stats, Settings)
            AnimatedVisibility(
                visible = isMainTabScreen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                GlassBottomNavigation(
                    selectedTab = selectedTab,
                    onTabSelected = { tab -> viewModel.selectTab(tab) }
                )
            }
        }
    }
}
