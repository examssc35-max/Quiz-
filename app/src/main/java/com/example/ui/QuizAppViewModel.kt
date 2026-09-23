package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.AppSoundManager
import com.example.data.local.AppSettings
import com.example.data.local.entity.QuizAttemptEntity
import com.example.data.local.entity.QuizEntity
import com.example.data.local.entity.UnfinishedQuizEntity
import com.example.data.model.QuizJsonParser
import com.example.data.model.QuizMode
import com.example.data.model.QuizResultSummary
import com.example.data.model.QuizSchema
import com.example.data.repository.QuizRepository
import com.example.engine.QuizEngine
import com.example.ui.components.NavigationTab
import com.example.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QuizAppViewModel(
    val repository: QuizRepository,
    val appSettings: AppSettings,
    val soundManager: AppSoundManager
) : ViewModel() {

    val allQuizzes: StateFlow<List<QuizEntity>> = repository.allQuizzes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteQuizzes: StateFlow<List<QuizEntity>> = repository.favoriteQuizzes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentQuizzes: StateFlow<List<QuizEntity>> = repository.recentQuizzes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestUnfinishedQuiz: StateFlow<UnfinishedQuizEntity?> = repository.latestUnfinishedQuiz
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allAttempts: StateFlow<List<QuizAttemptEntity>> = repository.allAttempts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _selectedTab = MutableStateFlow(NavigationTab.HOME)
    val selectedTab: StateFlow<NavigationTab> = _selectedTab.asStateFlow()

    private val _activeEngine = MutableStateFlow<QuizEngine?>(null)
    val activeEngine: StateFlow<QuizEngine?> = _activeEngine.asStateFlow()

    private val _quizFilter = MutableStateFlow<String?>(null)
    val quizFilter: StateFlow<String?> = _quizFilter.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
        when (screen) {
            is Screen.Home -> _selectedTab.value = NavigationTab.HOME
            is Screen.Quizzes -> _selectedTab.value = NavigationTab.QUIZZES
            is Screen.Statistics -> _selectedTab.value = NavigationTab.STATS
            is Screen.Settings -> _selectedTab.value = NavigationTab.SETTINGS
            else -> {}
        }
    }

    fun selectTab(tab: NavigationTab) {
        _selectedTab.value = tab
        when (tab) {
            NavigationTab.HOME -> _currentScreen.value = Screen.Home
            NavigationTab.QUIZZES -> {
                _quizFilter.value = null
                _currentScreen.value = Screen.Quizzes
            }
            NavigationTab.STATS -> _currentScreen.value = Screen.Statistics
            NavigationTab.SETTINGS -> _currentScreen.value = Screen.Settings
        }
    }

    fun openQuizzesWithFilter(filter: String?) {
        _quizFilter.value = filter
        _selectedTab.value = NavigationTab.QUIZZES
        _currentScreen.value = Screen.Quizzes
    }

    fun startQuiz(quizId: String, mode: QuizMode, resume: Boolean = false) {
        viewModelScope.launch {
            val quiz = repository.getQuizById(quizId) ?: return@launch
            val schema = QuizJsonParser.validateAndParse(quiz.jsonContent).getOrNull() ?: return@launch

            val engine = if (resume) {
                val unfinished = repository.getUnfinishedQuiz(quizId)
                if (unfinished != null) {
                    QuizEngine(quizId, schema, unfinished)
                } else {
                    QuizEngine(quizId, schema, mode)
                }
            } else {
                QuizEngine(quizId, schema, mode)
            }

            _activeEngine.value = engine
            _currentScreen.value = Screen.QuizPlay(quizId, mode, resume)
        }
    }

    fun saveUnfinishedCurrentQuiz() {
        val engine = _activeEngine.value ?: return
        viewModelScope.launch {
            repository.saveUnfinishedQuiz(engine.toUnfinishedEntity())
            _activeEngine.value = null
            navigateTo(Screen.Home)
        }
    }

    fun onQuizCompleted(summary: QuizResultSummary) {
        viewModelScope.launch {
            repository.recordAttempt(summary)
            _activeEngine.value = null
            _currentScreen.value = Screen.Result(summary)
        }
    }

    suspend fun saveQuiz(schema: QuizSchema, existingId: String? = null): String {
        val id = repository.insertOrUpdateQuiz(schema, existingId)
        return id
    }

    fun duplicateQuiz(quizId: String) {
        viewModelScope.launch {
            repository.duplicateQuiz(quizId)
        }
    }

    fun deleteQuiz(quizId: String) {
        viewModelScope.launch {
            repository.deleteQuiz(quizId)
        }
    }

    fun toggleFavorite(quizId: String, current: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(quizId, current)
        }
    }

    fun resetStatistics() {
        viewModelScope.launch {
            repository.resetStatistics()
        }
    }

    fun restoreSampleQuizzes() {
        viewModelScope.launch {
            repository.ensureSeeded()
        }
    }

    fun clearUnfinished() {
        viewModelScope.launch {
            repository.clearUnfinishedProgress()
        }
    }

    fun shareQuizJson(context: Context, quizId: String) {
        viewModelScope.launch {
            val quiz = repository.getQuizById(quizId) ?: return@launch
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, quiz.jsonContent)
                putExtra(Intent.EXTRA_TITLE, "${quiz.title}.json")
                type = "application/json"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Share Quiz JSON")
            context.startActivity(shareIntent)
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundManager.shutdown()
    }
}

class QuizAppViewModelFactory(
    private val repository: QuizRepository,
    private val appSettings: AppSettings,
    private val soundManager: AppSoundManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QuizAppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return QuizAppViewModel(repository, appSettings, soundManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
