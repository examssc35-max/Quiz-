package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.QuestionSchema
import com.example.data.model.QuestionType
import com.example.data.model.QuizJsonParser
import com.example.data.model.QuizSchema
import com.example.data.repository.QuizRepository
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassConfirmDialog
import com.example.ui.components.GlassFilterChip
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassSecondaryButton
import com.example.ui.components.GlassTopBar
import com.example.ui.theme.AccentBlueLight
import com.example.ui.theme.AccentBluePrimary
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CorrectGreen
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WrongRed
import kotlinx.coroutines.launch
import java.util.UUID

sealed class EditorUiState {
    object Loading : EditorUiState()
    data class Loaded(
        val existingQuizId: String?,
        val initialSchema: QuizSchema,
        val initialRawJson: String
    ) : EditorUiState()
    data class Error(val message: String) : EditorUiState()
}

@Composable
fun QuizEditorScreen(
    existingQuizId: String?,
    repository: QuizRepository,
    onBackClick: () -> Unit,
    onSaveQuiz: suspend (schema: QuizSchema, quizId: String?) -> Unit,
    aiManager: com.example.ai.AIManager = com.example.ai.AIManager.defaultManager,
    modifier: Modifier = Modifier
) {
    var uiState by remember { mutableStateOf<EditorUiState>(EditorUiState.Loading) }

    LaunchedEffect(existingQuizId) {
        if (existingQuizId == null) {
            val template = QuizSchema(
                title = "New Quiz",
                description = "",
                category = "General Knowledge",
                difficulty = "Medium",
                timeLimit = 120,
                shuffleQuestions = false,
                shuffleOptions = false,
                questions = listOf(
                    QuestionSchema(
                        id = UUID.randomUUID().toString().take(8),
                        type = QuestionType.MCQ,
                        question = "What is the capital of Bangladesh?",
                        options = listOf("Chittagong", "Dhaka", "Sylhet", "Rajshahi"),
                        answer = 1,
                        points = 1,
                        explanation = "Dhaka has been the capital of Bangladesh since independence in 1971."
                    )
                )
            )
            uiState = EditorUiState.Loaded(
                existingQuizId = null,
                initialSchema = template,
                initialRawJson = QuizJsonParser.toJsonString(template)
            )
        } else {
            uiState = EditorUiState.Loading
            try {
                val quizEntity = repository.getQuizById(existingQuizId)
                if (quizEntity == null) {
                    uiState = EditorUiState.Error("Quiz not found")
                } else {
                    val parseResult = QuizJsonParser.parseLenient(quizEntity.jsonContent)
                    val schema = parseResult.getOrElse {
                        QuizSchema(
                            title = quizEntity.title,
                            description = quizEntity.description,
                            category = quizEntity.category,
                            difficulty = quizEntity.difficulty,
                            timeLimit = quizEntity.timeLimit,
                            shuffleQuestions = quizEntity.shuffleQuestions,
                            shuffleOptions = quizEntity.shuffleOptions,
                            questions = emptyList()
                        )
                    }
                    uiState = EditorUiState.Loaded(
                        existingQuizId = existingQuizId,
                        initialSchema = schema,
                        initialRawJson = quizEntity.jsonContent
                    )
                }
            } catch (e: Exception) {
                uiState = EditorUiState.Error("Failed to load quiz: ${e.message ?: "Unknown error"}")
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is EditorUiState.Loading -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    GlassTopBar(
                        title = if (existingQuizId == null) "Create Quiz" else "Edit Quiz",
                        onBackClick = onBackClick
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AccentCyan, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading quiz data...",
                                color = TextSecondary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            is EditorUiState.Error -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    GlassTopBar(
                        title = "Edit Quiz",
                        onBackClick = onBackClick
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            backgroundColor = Color(0x351E293B),
                            borderBrush = GlassBorderBrush
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = WrongRed,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = state.message,
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "The requested quiz could not be opened for editing.",
                                    color = TextMuted,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                GlassPrimaryButton(
                                    text = "Back to Quizzes",
                                    onClick = onBackClick
                                )
                            }
                        }
                    }
                }
            }
            is EditorUiState.Loaded -> {
                LoadedEditorContent(
                    loadedState = state,
                    onBackClick = onBackClick,
                    onSaveQuiz = onSaveQuiz,
                    aiManager = aiManager
                )
            }
        }
    }
}

@Composable
private fun LoadedEditorContent(
    loadedState: EditorUiState.Loaded,
    onBackClick: () -> Unit,
    onSaveQuiz: suspend (schema: QuizSchema, quizId: String?) -> Unit,
    aiManager: com.example.ai.AIManager
) {
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf("Visual") } // "Visual" or "Raw JSON"

    // Form states initialized safely from loaded schema
    var title by remember { mutableStateOf(loadedState.initialSchema.title) }
    var description by remember { mutableStateOf(loadedState.initialSchema.description) }
    var category by remember { mutableStateOf(loadedState.initialSchema.category) }
    var difficulty by remember { mutableStateOf(loadedState.initialSchema.difficulty) }
    var timeLimit by remember { mutableIntStateOf(loadedState.initialSchema.timeLimit) }
    var shuffleQuestions by remember { mutableStateOf(loadedState.initialSchema.shuffleQuestions) }
    var shuffleOptions by remember { mutableStateOf(loadedState.initialSchema.shuffleOptions) }

    val questions = remember {
        mutableStateListOf<QuestionSchema>().apply {
            addAll(loadedState.initialSchema.questions)
        }
    }

    var rawJsonText by remember { mutableStateOf(loadedState.initialRawJson) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var validationErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var isModified by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Intercept Back navigation if changes are pending
    BackHandler {
        if (isModified) {
            showDiscardDialog = true
        } else {
            onBackClick()
        }
    }

    if (showDiscardDialog) {
        GlassConfirmDialog(
            title = "Discard Changes?",
            message = "You have unsaved changes in this quiz. Are you sure you want to discard them?",
            confirmText = "Discard",
            dismissText = "Keep Editing",
            isDestructive = true,
            onConfirm = {
                showDiscardDialog = false
                onBackClick()
            },
            onDismiss = { showDiscardDialog = false }
        )
    }

    fun syncFromVisualToRaw() {
        val schema = QuizSchema(
            title = title.ifBlank { "Untitled Quiz" },
            description = description,
            category = category,
            difficulty = difficulty,
            timeLimit = timeLimit,
            shuffleQuestions = shuffleQuestions,
            shuffleOptions = shuffleOptions,
            questions = questions.toList()
        )
        rawJsonText = QuizJsonParser.toJsonString(schema)
    }

    fun syncFromRawToVisual(): Boolean {
        val res = QuizJsonParser.parseLenient(rawJsonText)
        if (res.isSuccess) {
            val s = res.getOrThrow()
            title = s.title
            description = s.description
            category = s.category
            difficulty = s.difficulty
            timeLimit = s.timeLimit
            shuffleQuestions = s.shuffleQuestions
            shuffleOptions = s.shuffleOptions
            questions.clear()
            questions.addAll(s.questions)
            errorMessage = null
            validationErrors = emptyList()
            isModified = true
            return true
        } else {
            errorMessage = "Invalid JSON syntax: ${res.exceptionOrNull()?.message}"
            return false
        }
    }

    fun validateBeforeSave(): List<String> {
        val errors = mutableListOf<String>()
        if (title.isBlank()) {
            errors.add("Quiz title cannot be empty.")
        }
        if (timeLimit < 0) {
            errors.add("Time limit cannot be negative.")
        }
        if (questions.isEmpty()) {
            errors.add("Quiz must contain at least one question.")
        }
        questions.forEachIndexed { idx, q ->
            val num = idx + 1
            if (q.question.isBlank()) {
                errors.add("Question #$num: Question text cannot be empty.")
            }
            if (q.points < 1) {
                errors.add("Question #$num: Points must be at least 1.")
            }
            if (q.type == QuestionType.MCQ) {
                if (q.options.size < 2) {
                    errors.add("Question #$num: Multiple choice question must have at least 2 options.")
                } else if (q.options.any { it.isBlank() }) {
                    errors.add("Question #$num: All options must contain text.")
                }
                if (q.options.isNotEmpty() && q.answer !in q.options.indices) {
                    errors.add("Question #$num: Selected answer index (${q.answer}) is out of range (0..${q.options.lastIndex}). Please tap an option to select the correct answer.")
                }
            } else if (q.type == QuestionType.FILL_BLANK) {
                if (q.fillBlankAnswer.isBlank() && q.acceptedAnswers.isEmpty()) {
                    errors.add("Question #$num: Fill-in-the-blank question requires at least one accepted answer.")
                }
            }
        }
        return errors
    }

    fun handleSave() {
        if (isSaving) return

        if (activeTab == "Raw JSON") {
            val parseRes = QuizJsonParser.validateAndParse(rawJsonText)
            if (parseRes.isFailure) {
                errorMessage = "Cannot save: Invalid JSON syntax (${parseRes.exceptionOrNull()?.message})"
                return
            }
            val schema = parseRes.getOrThrow()
            val errors = QuizJsonParser.validateSchemaForSaving(schema)
            if (errors.isNotEmpty()) {
                validationErrors = errors
                errorMessage = "Please fix the ${errors.size} error(s) below before saving."
                return
            }
            isSaving = true
            scope.launch {
                try {
                    onSaveQuiz(schema, loadedState.existingQuizId)
                } catch (e: Exception) {
                    errorMessage = "Error saving quiz: ${e.message}"
                } finally {
                    isSaving = false
                }
            }
        } else {
            val errors = validateBeforeSave()
            if (errors.isNotEmpty()) {
                validationErrors = errors
                errorMessage = "Please fix the ${errors.size} error(s) below before saving."
                return
            }
            val cleanQuestions = questions.map { q ->
                if (q.type == QuestionType.MCQ) {
                    q.copy(
                        options = q.options.map { it.trim() },
                        fillBlankAnswer = "",
                        acceptedAnswers = emptyList()
                    )
                } else {
                    val primary = q.fillBlankAnswer.trim()
                    val allAccepted = (listOf(primary) + q.acceptedAnswers.map { it.trim() })
                        .filter { it.isNotEmpty() }
                        .distinct()
                    q.copy(
                        options = emptyList(),
                        answer = 0,
                        fillBlankAnswer = primary.ifEmpty { allAccepted.firstOrNull() ?: "" },
                        acceptedAnswers = allAccepted
                    )
                }
            }
            val schema = QuizSchema(
                title = title.trim(),
                description = description.trim(),
                category = category.trim().ifEmpty { "General Knowledge" },
                difficulty = difficulty.trim().ifEmpty { "Medium" },
                timeLimit = timeLimit.coerceAtLeast(0),
                shuffleQuestions = shuffleQuestions,
                shuffleOptions = shuffleOptions,
                questions = cleanQuestions
            )
            isSaving = true
            scope.launch {
                try {
                    onSaveQuiz(schema, loadedState.existingQuizId)
                } catch (e: Exception) {
                    errorMessage = "Error saving quiz: ${e.message}"
                } finally {
                    isSaving = false
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GlassTopBar(
            title = if (loadedState.existingQuizId == null) "Create Quiz" else "Edit Quiz",
            onBackClick = {
                if (isModified) {
                    showDiscardDialog = true
                } else {
                    onBackClick()
                }
            }
        )

        // Tab Selector: Visual vs Raw JSON
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassFilterChip(
                label = "Visual Editor",
                isSelected = activeTab == "Visual",
                onClick = {
                    if (activeTab == "Raw JSON") {
                        if (syncFromRawToVisual()) {
                            activeTab = "Visual"
                        }
                    } else {
                        activeTab = "Visual"
                    }
                }
            )

            GlassFilterChip(
                label = "Raw JSON",
                isSelected = activeTab == "Raw JSON",
                onClick = {
                    if (activeTab == "Visual") {
                        syncFromVisualToRaw()
                    }
                    activeTab = "Raw JSON"
                }
            )
        }

        // Error message banner
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(WrongRed.copy(alpha = 0.2f))
                    .border(1.dp, WrongRed, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = errorMessage ?: "",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (validationErrors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        validationErrors.forEach { err ->
                            Text(
                                text = "• $err",
                                color = Color(0xFFFFCCCC),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        if (activeTab == "Raw JSON") {
            // RAW JSON TAB
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                OutlinedTextField(
                    value = rawJsonText,
                    onValueChange = {
                        rawJsonText = it
                        errorMessage = null
                        validationErrors = emptyList()
                        isModified = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlueLight,
                        unfocusedBorderColor = Color(0x30FFFFFF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = AccentBlueLight
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    placeholder = { Text("Paste valid Quiz Explore JSON...", color = TextMuted) }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassPrimaryButton(
                        text = "Validate JSON",
                        onClick = {
                            val parseResult = QuizJsonParser.validateAndParse(rawJsonText)
                            if (parseResult.isSuccess) {
                                errorMessage = null
                                validationErrors = emptyList()
                                syncFromRawToVisual()
                            } else {
                                errorMessage = parseResult.exceptionOrNull()?.message ?: "Invalid JSON"
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    GlassPrimaryButton(
                        text = if (isSaving) "Saving..." else "Save Quiz",
                        onClick = { handleSave() },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            // VISUAL TAB
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Quiz Metadata Card
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = Color(0x301E293B),
                    borderBrush = GlassBorderBrush
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "Quiz Information",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        EditorTextField(
                            label = "Title",
                            value = title,
                            onValueChange = {
                                title = it
                                isModified = true
                                errorMessage = null
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        EditorTextField(
                            label = "Description",
                            value = description,
                            onValueChange = {
                                description = it
                                isModified = true
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                EditorTextField(
                                    label = "Category",
                                    value = category,
                                    onValueChange = {
                                        category = it
                                        isModified = true
                                    }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                EditorTextField(
                                    label = "Difficulty",
                                    value = difficulty,
                                    onValueChange = {
                                        difficulty = it
                                        isModified = true
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        EditorTextField(
                            label = "Time Limit (seconds, 0 = untimed)",
                            value = "$timeLimit",
                            onValueChange = {
                                timeLimit = it.toIntOrNull() ?: 0
                                isModified = true
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Toggle shuffle settings
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Shuffle Questions", color = TextSecondary, fontSize = 14.sp)
                            Switch(
                                checked = shuffleQuestions,
                                onCheckedChange = {
                                    shuffleQuestions = it
                                    isModified = true
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentBluePrimary
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Shuffle Options", color = TextSecondary, fontSize = 14.sp)
                            Switch(
                                checked = shuffleOptions,
                                onCheckedChange = {
                                    shuffleOptions = it
                                    isModified = true
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentBluePrimary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Questions Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Questions (${questions.size})",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassFilterChip(
                            label = "+ MCQ",
                            isSelected = true,
                            onClick = {
                                questions.add(
                                    QuestionSchema(
                                        id = UUID.randomUUID().toString().take(8),
                                        type = QuestionType.MCQ,
                                        question = "New Question ${questions.size + 1}",
                                        options = listOf("Option A", "Option B", "Option C", "Option D"),
                                        answer = 0,
                                        points = 1,
                                        explanation = ""
                                    )
                                )
                                isModified = true
                                errorMessage = null
                            }
                        )

                        GlassFilterChip(
                            label = "+ Fill Blank",
                            isSelected = false,
                            onClick = {
                                questions.add(
                                    QuestionSchema(
                                        id = UUID.randomUUID().toString().take(8),
                                        type = QuestionType.FILL_BLANK,
                                        question = "New Fill-in-the-blank Question ${questions.size + 1}",
                                        options = emptyList(),
                                        answer = 0,
                                        fillBlankAnswer = "Answer",
                                        acceptedAnswers = listOf("Answer"),
                                        points = 1,
                                        explanation = ""
                                    )
                                )
                                isModified = true
                                errorMessage = null
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Question Cards
                if (questions.isEmpty()) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = Color(0x201E293B),
                        borderBrush = GlassBorderBrush
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No questions yet",
                                color = TextSecondary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap '+ MCQ' or '+ Fill Blank' above to add your first question.",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    questions.forEach { question ->
                        key(question.id) {
                            val qIndex = questions.indexOfFirst { it.id == question.id }
                            QuestionEditorCard(
                                index = if (qIndex != -1) qIndex else 0,
                                question = question,
                                aiManager = aiManager,
                                onUpdate = { updated ->
                                    val idx = questions.indexOfFirst { it.id == question.id }
                                    if (idx in questions.indices) {
                                        questions[idx] = updated
                                        isModified = true
                                        errorMessage = null
                                    }
                                },
                                onDelete = {
                                    questions.removeAll { it.id == question.id }
                                    isModified = true
                                    errorMessage = null
                                }
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                GlassPrimaryButton(
                    text = if (isSaving) "Saving Quiz..." else "Save Quiz",
                    onClick = { handleSave() },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

@Composable
fun QuestionEditorCard(
    index: Int,
    question: QuestionSchema,
    aiManager: com.example.ai.AIManager = com.example.ai.AIManager.defaultManager,
    onUpdate: (QuestionSchema) -> Unit,
    onDelete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isVerifying by remember { mutableStateOf(false) }
    var auditResult by remember { mutableStateOf<com.example.ai.QuestionAuditResult?>(null) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0x241E293B),
        borderBrush = GlassBorderBrush
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Q${index + 1}",
                    color = AccentCyan,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = AccentCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verifying...", color = AccentCyan, fontSize = 12.sp)
                    } else {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x2538BDF8))
                                .clickable {
                                    scope.launch {
                                        isVerifying = true
                                        try {
                                            val res = aiManager.auditQuestion(question)
                                            if (res.isSuccess) {
                                                val audit = res.getOrThrow()
                                                auditResult = audit
                                                if (audit.answerChanged && audit.confidence >= 0.90) {
                                                    if (question.type == QuestionType.MCQ && audit.verifiedAnswerIndex != null) {
                                                        onUpdate(
                                                            question.copy(
                                                                verifiedAnswerIndex = audit.verifiedAnswerIndex,
                                                                isVerified = true,
                                                                verificationConfidence = audit.confidence,
                                                                verificationReason = audit.reason,
                                                                needsReview = audit.needsReview,
                                                                verifiedExplanation = audit.correctedExplanation ?: question.explanation
                                                            )
                                                        )
                                                    } else if (question.type == QuestionType.FILL_BLANK && !audit.verifiedAnswerText.isNullOrBlank()) {
                                                        onUpdate(
                                                            question.copy(
                                                                verifiedFillBlankAnswer = audit.verifiedAnswerText,
                                                                verifiedAcceptedAnswers = if (audit.acceptedAnswers.isNotEmpty()) audit.acceptedAnswers else question.acceptedAnswers,
                                                                isVerified = true,
                                                                verificationConfidence = audit.confidence,
                                                                verificationReason = audit.reason,
                                                                needsReview = audit.needsReview,
                                                                verifiedExplanation = audit.correctedExplanation ?: question.explanation
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            // Safely ignore verification error
                                        } finally {
                                            isVerifying = false
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Verify",
                                tint = AccentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (question.isVerified) "Re-verify" else "AI Verify",
                                color = AccentCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Question",
                            tint = WrongRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // AI verification feedback banner
            val curAudit = auditResult
            if (question.wasAnswerCorrected || (curAudit != null && curAudit.answerChanged)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x280D9488))
                        .border(1.dp, AccentCyan, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val conf = (((curAudit?.confidence ?: question.verificationConfidence)) * 100).toInt()
                            Text(
                                text = "AI Auto-Corrected ($conf% confidence)",
                                color = AccentCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        val r = curAudit?.reason ?: question.verificationReason
                        if (!r.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = r, color = Color(0xFFCCFBF1), fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else if (question.isVerified || curAudit != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x2010B981))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = CorrectGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val conf = (((curAudit?.confidence ?: question.verificationConfidence)) * 100).toInt()
                    Text(
                        text = "Answer confirmed accurate by AI ($conf%)",
                        color = Color(0xFF6EE7B7),
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Question Type Toggle: MCQ vs Fill in Blank
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Type:", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                GlassFilterChip(
                    label = "Multiple Choice (MCQ)",
                    isSelected = question.type == QuestionType.MCQ,
                    onClick = {
                        if (question.type != QuestionType.MCQ) {
                            val defaultOptions = if (question.options.size >= 2) {
                                question.options
                            } else {
                                val optA = question.fillBlankAnswer.ifBlank { "Option A" }
                                listOf(optA, "Option B", "Option C", "Option D")
                            }
                            onUpdate(
                                question.copy(
                                    type = QuestionType.MCQ,
                                    options = defaultOptions,
                                    answer = 0,
                                    fillBlankAnswer = "",
                                    acceptedAnswers = emptyList()
                                )
                            )
                        }
                    }
                )
                GlassFilterChip(
                    label = "Fill in Blank",
                    isSelected = question.type == QuestionType.FILL_BLANK,
                    onClick = {
                        if (question.type != QuestionType.FILL_BLANK) {
                            val defaultAns = question.options.getOrNull(question.answer)
                                ?: question.options.firstOrNull()
                                ?: "Answer"
                            onUpdate(
                                question.copy(
                                    type = QuestionType.FILL_BLANK,
                                    options = emptyList(),
                                    answer = 0,
                                    fillBlankAnswer = question.fillBlankAnswer.ifEmpty { defaultAns },
                                    acceptedAnswers = if (question.acceptedAnswers.isNotEmpty()) {
                                        question.acceptedAnswers
                                    } else {
                                        listOf(defaultAns)
                                    }
                                )
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            EditorTextField(
                label = "Question Text",
                value = question.question,
                onValueChange = { onUpdate(question.copy(question = it)) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (question.type == QuestionType.FILL_BLANK) {
                // Fill in the blank fields
                Text(
                    text = "Fill-in-the-Blank Answer Configuration",
                    color = AccentCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                EditorTextField(
                    label = "Primary Correct Answer",
                    value = question.fillBlankAnswer,
                    onValueChange = { newAns ->
                        val updatedAccepted = if (question.acceptedAnswers.size <= 1) {
                            if (newAns.isNotBlank()) listOf(newAns) else emptyList()
                        } else {
                            listOf(newAns) + question.acceptedAnswers.drop(1)
                        }
                        onUpdate(question.copy(fillBlankAnswer = newAns, acceptedAnswers = updatedAccepted))
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                val additionalAccepted = question.acceptedAnswers.drop(1).joinToString(", ")
                EditorTextField(
                    label = "Additional Accepted Variations (comma-separated)",
                    value = additionalAccepted,
                    onValueChange = { raw ->
                        val extras = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val allAccepted = (listOf(question.fillBlankAnswer).filter { it.isNotEmpty() } + extras).distinct()
                        onUpdate(question.copy(acceptedAnswers = allAccepted))
                    }
                )
            } else {
                // MCQ options
                Text(
                    text = "Options (Tap circle to set correct answer)",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Answer index safety warning (Requirement 8)
                val isAnswerIndexValid = question.options.isNotEmpty() && question.answer in question.options.indices
                if (!isAnswerIndexValid) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(WrongRed.copy(alpha = 0.2f))
                            .border(1.dp, WrongRed, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "⚠️ Invalid Answer Index (${question.answer})",
                                color = WrongRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "The correct answer index is out of bounds (valid range: 0..${question.options.lastIndex.coerceAtLeast(0)}). Tap one of the option letters below to select the correct answer.",
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                question.options.forEachIndexed { optIdx, optText ->
                    val isCorrect = (optIdx == question.answer)
                    val optLetter = if (optIdx in 0..25) "${'A' + optIdx}" else "${optIdx + 1}"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isCorrect) CorrectGreen else Color(0x30FFFFFF))
                                .clickable {
                                    onUpdate(question.copy(answer = optIdx))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCorrect) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Correct Answer",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Text(
                                    text = optLetter,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        EditorTextField(
                            label = "Option $optLetter",
                            value = optText,
                            onValueChange = { newText ->
                                val newOptions = question.options.toMutableList()
                                if (optIdx in newOptions.indices) {
                                    newOptions[optIdx] = newText
                                    onUpdate(question.copy(options = newOptions))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        // Option delete button if more than 2 options
                        if (question.options.size > 2) {
                            IconButton(
                                onClick = {
                                    val newOptions = question.options.toMutableList().apply { removeAt(optIdx) }
                                    val newAnswer = when {
                                        question.answer == optIdx -> 0
                                        question.answer > optIdx -> question.answer - 1
                                        else -> question.answer
                                    }.coerceIn(0, newOptions.lastIndex)
                                    onUpdate(question.copy(options = newOptions, answer = newAnswer))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove Option",
                                    tint = Color(0x99FFFFFF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Add Option button
                if (question.options.size < 8) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x18FFFFFF))
                            .clickable {
                                val nextLetter = if (question.options.size in 0..25) "${'A' + question.options.size}" else "${question.options.size + 1}"
                                val newOptions = question.options + "Option $nextLetter"
                                onUpdate(question.copy(options = newOptions))
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = AccentBlueLight,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add Option",
                            color = AccentBlueLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            EditorTextField(
                label = "Explanation (Optional)",
                value = question.explanation ?: "",
                onValueChange = { onUpdate(question.copy(explanation = it.ifBlank { null })) }
            )
        }
    }
}

@Composable
fun EditorTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color(0x88FFFFFF), fontSize = 12.sp) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentBlueLight,
            unfocusedBorderColor = Color(0x30FFFFFF),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = AccentBlueLight
        ),
        singleLine = false,
        modifier = modifier.fillMaxWidth()
    )
}
