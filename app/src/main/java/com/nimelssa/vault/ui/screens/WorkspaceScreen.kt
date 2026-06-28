package com.nimelssa.vault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nimelssa.vault.data.Course
import com.nimelssa.vault.data.CourseRepository
import com.nimelssa.vault.data.LevelTextbookRepository
import com.nimelssa.vault.data.UserRole
import com.nimelssa.vault.data.UserSession
import com.nimelssa.vault.ui.components.CourseCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    onOpenViewer: (Course) -> Unit,
    modifier: Modifier = Modifier
) {
    val userState by UserSession.state.collectAsState()
    val initialLevel = if (userState.role == UserRole.REP) userState.repLevel else userState.level
    var selectedLevel by remember { mutableStateOf(initialLevel) }
    var selectedSemester by remember { mutableIntStateOf(1) }
    var levelExpanded by remember { mutableStateOf(false) }
    val levels = listOf("100", "200", "300", "400")

    val courses = CourseRepository.getFilteredMerged(selectedLevel, selectedSemester)
    val categories = CourseRepository.getCategories(selectedLevel, selectedSemester)
    val levelTextbooks by LevelTextbookRepository.textbooks.collectAsState()
    val textbooksForLevel = levelTextbooks[selectedLevel] ?: emptyList()

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Level selector
        ExposedDropdownMenuBox(
            expanded = levelExpanded,
            onExpandedChange = { levelExpanded = it }
        ) {
            OutlinedTextField(
                value = "${selectedLevel} Level",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                textStyle = MaterialTheme.typography.labelLarge.copy(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedContainerColor = MaterialTheme.colorScheme.background
                ),
                shape = RoundedCornerShape(8.dp)
            )
            ExposedDropdownMenu(
                expanded = levelExpanded,
                onDismissRequest = { levelExpanded = false }
            ) {
                levels.forEach { level ->
                    DropdownMenuItem(
                        text = { Text("${level} Level") },
                        onClick = {
                            selectedLevel = level
                            levelExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Semester filter chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedSemester == 1,
                onClick = { selectedSemester = 1 },
                label = { Text("1st Semester") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            FilterChip(
                selected = selectedSemester == 2,
                onClick = { selectedSemester = 2 },
                label = { Text("2nd Semester") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Course list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            if (courses.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 50.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        Text(text = "🗄️", fontSize = MaterialTheme.typography.headlineLarge.fontSize)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No approved library links catalogued here yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            categories.forEach { category ->
                item {
                    Text(
                        text = "📁 $category",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                val categoryCourses = courses.filter { it.category == category }
                items(categoryCourses) { course ->
                    CourseCard(
                        course = course,
                        onStudyNotes = { onOpenViewer(course) },
                        onPastQuestions = { onOpenViewer(course) },
                        onTextbook = { onOpenViewer(course) }
                    )
                }
            }

            // ── Level-wide Textbooks section ──
            if (textbooksForLevel.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "📚 Level Textbooks",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                textbooksForLevel.forEach { tb ->
                    item {
                        val tbCourse = Course(
                            code = "TEXTBOOK",
                            name = tb.label.ifBlank { "Reference Textbooks" },
                            category = "TEXTBOOKS",
                            level = selectedLevel,
                            semester = 1,
                            textbookUrl = tb.masterFolderUrl,
                            notes = tb.notes
                        )
                        CourseCard(
                            course = tbCourse,
                            onStudyNotes = {},
                            onPastQuestions = {},
                            onTextbook = { onOpenViewer(tbCourse) }
                        )
                    }
                }
            }
        }
    }
}
