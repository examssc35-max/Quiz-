package com.example.ui.screens

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.QuizJsonParser
import com.example.data.model.QuizSchema
import com.example.data.samples.SampleQuizzes
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPrimaryButton
import com.example.ui.components.GlassSecondaryButton
import com.example.ui.components.GlassTopBar
import com.example.ui.theme.AccentBlueLight
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CorrectGreen
import com.example.ui.theme.CorrectGreenBg
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WrongRed
import com.example.ui.theme.WrongRedBg
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Custom ActivityResultContract using native ACTION_OPEN_DOCUMENT with CATEGORY_OPENABLE.
 * Configured with broad MIME types so that Android DocumentsUI / Android Files does NOT
 * grey out .json files (e.g. from Downloads, Termux, internal storage, SD card, external file managers).
 */
class OpenJsonDocumentContract : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // Base type */* to prevent DocumentsUI from restricting before EXTRA_MIME_TYPES is evaluated
            type = "*/*"
            // Support common JSON MIME types, octet-stream, text/plain, and wildcard fallback
            val mimeTypes = arrayOf(
                "application/json",
                "text/json",
                "text/plain",
                "application/octet-stream",
                "*/*"
            )
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}

@Composable
fun ImportQuizScreen(
    onBackClick: () -> Unit,
    onImportSuccess: (quizId: String) -> Unit,
    onSaveQuiz: suspend (schema: QuizSchema) -> String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var jsonText by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var validatedSchema by remember { mutableStateOf<QuizSchema?>(null) }
    var importedFileName by remember { mutableStateOf<String?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }

    // Native ACTION_OPEN_DOCUMENT file picker launcher with broad MIME support
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = OpenJsonDocumentContract()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Preserve persistable URI permission when supported (Requirement 12)
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (ignored: Exception) {
                    // Ignored if provider does not support persistable permissions
                }

                // Query file display name for extension validation & user feedback
                val queriedName: String? = if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                    try {
                        context.contentResolver.query(
                            uri,
                            arrayOf(OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (nameIdx >= 0) cursor.getString(nameIdx) else null
                            } else null
                        }
                    } catch (ignored: Exception) {
                        null
                    }
                } else null
                val resolvedFileName = queriedName ?: uri.lastPathSegment?.substringAfterLast('/')

                // Validate file extension case-insensitively (.json or .JSON) (Requirement 5)
                if (resolvedFileName != null && !resolvedFileName.endsWith(".json", ignoreCase = true)) {
                    validationError = "Selected file '$resolvedFileName' must have a .json extension."
                    validatedSchema = null
                    return@rememberLauncherForActivityResult
                }

                // Read file content via ContentResolver.openInputStream(uri) with UTF-8 encoding (Requirement 7, 11)
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                } ?: throw IllegalStateException("Could not open input stream for selected file")

                jsonText = content
                importedFileName = resolvedFileName

                // Parse and validate against Quiz Explore schema (Requirement 8, 9)
                val result = QuizJsonParser.validateAndParse(content)
                if (result.isSuccess) {
                    validatedSchema = result.getOrNull()
                    validationError = null
                } else {
                    validatedSchema = null
                    validationError = result.exceptionOrNull()?.message ?: "Invalid Quiz JSON"
                }
            } catch (e: Exception) {
                validationError = "Failed to read file: ${e.message}"
                validatedSchema = null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        GlassTopBar(
            title = "Import Quiz",
            onBackClick = onBackClick,
            actions = {
                IconButton(onClick = { showHelpDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = "Help",
                        tint = Color.White
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // File Upload Drop Area matching screen 3
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                backgroundColor = Color(0x301E293B),
                borderBrush = GlassBorderBrush,
                onClick = { filePickerLauncher.launch(Unit) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color(0x333B82F6)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Upload",
                            tint = AccentBlueLight,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (importedFileName != null) "Selected: $importedFileName" else "Choose JSON File",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (importedFileName != null) "Tap to choose a different JSON file" else "Tap to browse files on your device",
                        color = if (importedFileName != null) AccentCyan else TextMuted,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Load Sample Quick Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Or Paste JSON Code",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Load Sample",
                    color = AccentCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable {
                            val sample = SampleQuizzes.BENGALI_QUIZ
                            jsonText = QuizJsonParser.toJsonString(sample)
                            validatedSchema = sample
                            validationError = null
                            importedFileName = "bangla.json (Sample)"
                        }
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Raw JSON Input Area
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                backgroundColor = Color(0x221E293B),
                borderBrush = GlassBorderBrush
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = {
                            jsonText = it
                            if (it.isNotBlank()) {
                                val res = QuizJsonParser.validateAndParse(it)
                                if (res.isSuccess) {
                                    validatedSchema = res.getOrNull()
                                    validationError = null
                                } else {
                                    validatedSchema = null
                                    validationError = res.exceptionOrNull()?.message
                                }
                            } else {
                                validatedSchema = null
                                validationError = null
                            }
                        },
                        placeholder = {
                            Text(
                                "{\n  \"title\": \"My Quiz\",\n  \"category\": \"Science\",\n  \"difficulty\": \"Medium\",\n  \"questions\": [...]\n}",
                                color = Color(0x55FFFFFF),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = AccentBlueLight
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${jsonText.length} characters",
                            color = TextMuted,
                            fontSize = 11.sp
                        )

                        if (jsonText.isNotEmpty()) {
                            Text(
                                text = "Clear",
                                color = WrongRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable {
                                        jsonText = ""
                                        validatedSchema = null
                                        validationError = null
                                        importedFileName = null
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Validation Status / Error Banner
            if (validationError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(WrongRedBg)
                        .border(1.dp, WrongRed, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = WrongRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Validation Error",
                                color = WrongRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = validationError ?: "",
                                color = Color(0xFFFCA5A5),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            } else if (validatedSchema != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CorrectGreenBg)
                        .border(1.dp, CorrectGreen, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Valid",
                            tint = CorrectGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Valid Quiz JSON: \"${validatedSchema?.title}\"",
                                color = CorrectGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val fileLabel = if (importedFileName != null) "$importedFileName • " else ""
                            Text(
                                text = "$fileLabel${validatedSchema?.questions?.size} questions • Category: ${validatedSchema?.category}",
                                color = Color(0xFF6EE7B7),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Import Button
            GlassPrimaryButton(
                text = "Import Quiz",
                enabled = validatedSchema != null,
                onClick = {
                    val schema = validatedSchema
                    if (schema != null) {
                        scope.launch {
                            val newId = onSaveQuiz(schema)
                            onImportSuccess(newId)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
