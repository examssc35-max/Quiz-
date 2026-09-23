package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.QuizEntity
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassConfirmDialog
import com.example.ui.components.GlassFilterChip
import com.example.ui.theme.AccentBlueLight
import com.example.ui.theme.AccentBluePrimary
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.GlassCardSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WrongRed

@Composable
fun QuizListScreen(
    quizzes: List<QuizEntity>,
    initialFilter: String? = null,
    onQuizClick: (quizId: String) -> Unit,
    onEditQuiz: (quizId: String) -> Unit,
    onDuplicateQuiz: (quizId: String) -> Unit,
    onDeleteQuiz: (quizId: String) -> Unit,
    onToggleFavorite: (quizId: String, currentFavorite: Boolean) -> Unit,
    onExportQuiz: (quizId: String) -> Unit,
    onCreateQuizClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(initialFilter ?: "All") }
    var quizToDelete by remember { mutableStateOf<QuizEntity?>(null) }

    // Filter quizzes
    val filteredQuizzes = quizzes.filter { quiz ->
        val matchesSearch = quiz.title.contains(searchQuery, ignoreCase = true) ||
                quiz.description.contains(searchQuery, ignoreCase = true) ||
                quiz.category.contains(searchQuery, ignoreCase = true)

        val matchesTab = when (selectedTab) {
            "Favorites" -> quiz.isFavorite
            "Recent" -> quiz.lastPlayedAt != null
            "All" -> true
            else -> quiz.category.equals(selectedTab, ignoreCase = true)
        }

        matchesSearch && matchesTab
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Bar
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Quizzes",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    GlassCard(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        backgroundColor = Color(0x28FFFFFF),
                        borderBrush = GlassBorderBrush,
                        onClick = {
                            selectedTab = "All"
                            searchQuery = ""
                        }
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Reset Filters",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Filter Tabs matching screen 7
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassFilterChip(
                        label = "All",
                        isSelected = selectedTab == "All",
                        onClick = { selectedTab = "All" }
                    )
                    GlassFilterChip(
                        label = "Favorites",
                        isSelected = selectedTab == "Favorites",
                        onClick = { selectedTab = "Favorites" }
                    )
                    GlassFilterChip(
                        label = "Recent",
                        isSelected = selectedTab == "Recent",
                        onClick = { selectedTab = "Recent" }
                    )
                }
            }

            // Search Bar
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    backgroundColor = Color(0x251E293B),
                    borderBrush = GlassBorderBrush
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0x99FFFFFF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text("Search quizzes...", color = Color(0x88FFFFFF), fontSize = 15.sp)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = AccentBlueLight
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Quiz Cards List
            if (filteredQuizzes.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(40.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0x44FFFFFF),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No quizzes found",
                                color = TextMuted,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Try changing your search or filter",
                                color = Color(0x66FFFFFF),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(filteredQuizzes, key = { it.id }) { quiz ->
                    QuizListItemCard(
                        quiz = quiz,
                        onPlayClick = { onQuizClick(quiz.id) },
                        onFavoriteClick = { onToggleFavorite(quiz.id, quiz.isFavorite) },
                        onEditClick = { onEditQuiz(quiz.id) },
                        onDuplicateClick = { onDuplicateQuiz(quiz.id) },
                        onDeleteClick = { quizToDelete = quiz },
                        onExportClick = { onExportQuiz(quiz.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        // Floating Action Button (+) to create quiz matching reference
        FloatingActionButton(
            onClick = onCreateQuizClick,
            shape = CircleShape,
            containerColor = AccentBluePrimary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 96.dp)
                .size(58.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create Quiz",
                modifier = Modifier.size(28.dp)
            )
        }

        // Delete Confirmation Dialog
        if (quizToDelete != null) {
            GlassConfirmDialog(
                title = "Delete Quiz?",
                message = "Are you sure you want to delete '${quizToDelete?.title}'? This action cannot be undone.",
                confirmText = "Delete",
                isDestructive = true,
                onConfirm = {
                    quizToDelete?.id?.let { onDeleteQuiz(it) }
                    quizToDelete = null
                },
                onDismiss = { quizToDelete = null }
            )
        }
    }
}

@Composable
fun QuizListItemCard(
    quiz: QuizEntity,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onEditClick: () -> Unit,
    onDuplicateClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val (categoryIcon, categoryColor) = when (quiz.category.lowercase()) {
        "science" -> Icons.Default.Science to Color(0xFF10B981)
        "mathematics", "math" -> Icons.Default.Calculate to Color(0xFF8B5CF6)
        "ict", "technology" -> Icons.Default.Computer to Color(0xFF06B6D4)
        "english" -> Icons.Default.MenuBook to Color(0xFFA855F7)
        else -> Icons.Default.Public to Color(0xFF3B82F6)
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = Color(0x301E293B),
        borderBrush = GlassBorderBrush,
        onClick = onPlayClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(categoryColor.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = quiz.category,
                    tint = categoryColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Quiz Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quiz.title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${quiz.questionCount} Questions • ${quiz.difficulty}",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }

            // Play Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x35FFFFFF))
                    .clickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Favorite Button
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (quiz.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (quiz.isFavorite) Color(0xFFF43F5E) else Color(0x99FFFFFF),
                    modifier = Modifier.size(20.dp)
                )
            }

            // More Options Menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color(0x99FFFFFF),
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit Quiz") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onEditClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onDuplicateClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export JSON") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onExportClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = WrongRed) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = WrongRed) },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}
