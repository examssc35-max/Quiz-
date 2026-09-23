package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.local.entity.QuizEntity
import com.example.data.model.QuestionSchema
import com.example.data.model.QuestionType
import com.example.data.model.QuizJsonParser
import com.example.data.model.QuizSchema
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassFilterChip
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassTopBar
import com.example.ui.theme.AccentBlueLight
import com.example.ui.theme.AccentBluePrimary
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CorrectGreen
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.GlassCardSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WrongRed
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizEditorScreen(
    existingQuiz: QuizEntity?,
    onBackClick: () -> Unit,
    onSaveQuiz: suspend (schema: QuizSchema) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf("Visual") } // "Visual" or "Raw JSON"

    // Form states
    var title by remember { mutableStateOf(existingQuiz?.title ?: "") }
    var description by remember { mutableStateOf(existingQuiz?.description ?: "") }
    var category by remember { mutableStateOf(existingQuiz?.category ?: "General Knowledge") }
    var difficulty by remember { mutableStateOf(existingQuiz?.difficulty ?: "Medium") }
    var timeLimit by remember { mutableIntStateOf(existingQuiz?.timeLimit ?: 120) }
    var shuffleQuestions by remember { mutableStateOf(existingQuiz?.shuffleQuestions ?: false) }
    var shuffleOptions by remember { mutableStateOf(existingQuiz?.shuffleOptions ?: false) }

    // Initial questions parsing
    val initialQuestions = remember {
        existingQuiz?.let {
            QuizJsonParser.validateAndParse(it.jsonContent).getOrNull()?.questions
        } ?: listOf(
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
    }

    val questions = remember { mutableStateListOf<QuestionSchema>().apply { addAll(initialQuestions) } }

    var rawJsonText by remember {
        mutableStateOf(
            existingQuiz?.jsonContent ?: QuizJsonParser.toJsonString(
                QuizSchema(
                    title = "New Quiz",
                    description = "Custom quiz description",
                    category = "General Knowledge",
                    difficulty = "Medium",
                    timeLimit = 120,
                    shuffleQuestions = false,
                    shuffleOptions = false,
                    questions = initialQuestions
                )
            )
        )
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

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
        val res = QuizJsonParser.validateAndParse(rawJsonText)
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
            return true
        } else {
            errorMessage = res.exceptionOrNull()?.message ?: "Invalid JSON syntax"
            return false
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(
            title = if (existingQuiz == null) "Create Quiz" else "Edit Quiz",
            onBackClick = onBackClick
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
                Text(text = errorMessage ?: "", color = WrongRed, fontSize = 13.sp)
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
                                syncFromRawToVisual()
                            } else {
                                errorMessage = parseResult.exceptionOrNull()?.message ?: "Invalid JSON"
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    GlassPrimaryButton(
                        text = "Save Quiz",
                        onClick = {
                            val parseResult = QuizJsonParser.validateAndParse(rawJsonText)
                            if (parseResult.isSuccess) {
                                val schema = parseResult.getOrThrow()
                                scope.launch { onSaveQuiz(schema) }
                            } else {
                                errorMessage = parseResult.exceptionOrNull()?.message ?: "Cannot save: Invalid JSON"
                            }
                        },
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

                        EditorTextField(label = "Title", value = title, onValueChange = { title = it })
                        Spacer(modifier = Modifier.height(10.dp))
                        EditorTextField(label = "Description", value = description, onValueChange = { description = it })
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                EditorTextField(label = "Category", value = category, onValueChange = { category = it })
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                EditorTextField(label = "Difficulty", value = difficulty, onValueChange = { difficulty = it })
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        EditorTextField(
                            label = "Time Limit (seconds, 0 = untimed)",
                            value = "$timeLimit",
                            onValueChange = { timeLimit = it.toIntOrNull() ?: 0 }
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
                                onCheckedChange = { shuffleQuestions = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentBluePrimary)
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
                                onCheckedChange = { shuffleOptions = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentBluePrimary)
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
                                        question = "The capital of Bangladesh is _____.",
                                        options = emptyList(),
                                        answer = 0,
                                        fillBlankAnswer = "Dhaka",
                                        acceptedAnswers = listOf("Dhaka"),
                                        points = 1,
                                        explanation = ""
                                    )
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Question Cards
                questions.forEachIndexed { qIndex, question ->
                    QuestionEditorCard(
                        index = qIndex,
                        question = question,
                        onUpdate = { updated -> questions[qIndex] = updated },
                        onDelete = { questions.removeAt(qIndex) }
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                Spacer(modifier = Modifier.height(20.dp))

                GlassPrimaryButton(
                    text = "Save Quiz",
                    onClick = {
                        if (title.isBlank()) {
                            errorMessage = "Title cannot be empty"
                            return@GlassPrimaryButton
                        }
                        if (questions.isEmpty()) {
                            errorMessage = "Must have at least one question"
                            return@GlassPrimaryButton
                        }
                        val invalidQ = questions.find { q ->
                            if (q.question.isBlank()) true
                            else if (q.type == QuestionType.MCQ && (q.options.size < 2 || q.options.any { it.isBlank() })) true
                            else if (q.type == QuestionType.FILL_BLANK && q.fillBlankAnswer.isBlank() && q.acceptedAnswers.isEmpty()) true
                            else false
                        }
                        if (invalidQ != null) {
                            errorMessage = "Please complete all fields for every question"
                            return@GlassPrimaryButton
                        }

                        val schema = QuizSchema(
                            title = title.trim(),
                            description = description.trim(),
                            category = category.trim(),
                            difficulty = difficulty.trim(),
                            timeLimit = timeLimit,
                            shuffleQuestions = shuffleQuestions,
                            shuffleOptions = shuffleOptions,
                            questions = questions.toList()
                        )
                        scope.launch { onSaveQuiz(schema) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(50.dp))
            }
        }
    }
}

@Composable
fun QuestionEditorCard(
    index: Int,
    question: QuestionSchema,
    onUpdate: (QuestionSchema) -> Unit,
    onDelete: () -> Unit
) {
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

                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Question",
                        tint = WrongRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                            onUpdate(
                                question.copy(
                                    type = QuestionType.MCQ,
                                    options = if (question.options.size >= 2) question.options else listOf("Option A", "Option B", "Option C", "Option D"),
                                    answer = 0
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
                            val defaultAns = question.options.getOrNull(question.answer) ?: "Answer"
                            onUpdate(
                                question.copy(
                                    type = QuestionType.FILL_BLANK,
                                    fillBlankAnswer = question.fillBlankAnswer.ifEmpty { defaultAns },
                                    acceptedAnswers = if (question.acceptedAnswers.isNotEmpty()) question.acceptedAnswers else listOf(defaultAns)
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

                question.options.forEachIndexed { optIdx, optText ->
                    val isCorrect = (optIdx == question.answer)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (isCorrect) CorrectGreen else Color(0x30FFFFFF))
                                .clickable { onUpdate(question.copy(answer = optIdx)) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCorrect) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            } else {
                                Text(text = "${('A' + optIdx)}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        EditorTextField(
                            label = "Option ${('A' + optIdx)}",
                            value = optText,
                            onValueChange = { newText ->
                                val newOptions = question.options.toMutableList().apply { set(optIdx, newText) }
                                onUpdate(question.copy(options = newOptions))
                            },
                            modifier = Modifier.weight(1f)
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
