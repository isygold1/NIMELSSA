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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.nimelssa.vault.data.Course
import com.nimelssa.vault.data.CourseRepository
import com.nimelssa.vault.data.Levels
import com.nimelssa.vault.data.ProposalRepository
import com.nimelssa.vault.data.Resource
import com.nimelssa.vault.data.ResourceRepository
import com.nimelssa.vault.data.UserRole
import com.nimelssa.vault.data.UserSession
import com.nimelssa.vault.ui.components.CourseCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    onOpenViewer: (Course, String?) -> Unit,
    onNavigateToPropose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val userState by UserSession.state.collectAsState()
    val initialLevel = if (userState.role == UserRole.REP) userState.repLevel else userState.level
    var selectedLevel by remember { mutableStateOf(initialLevel) }
    var selectedSemester by remember { mutableIntStateOf(1) }
    var levelExpanded by remember { mutableStateOf(false) }
    var pendingExpanded by remember { mutableStateOf(false) }
    val levels = Levels.ALL

    val allCourses by CourseRepository.courses.collectAsState()
    val courses = allCourses.filter { it.level == selectedLevel && it.semester == selectedSemester }
    val categories = courses.map { it.category }.distinct()
    val resourceMap by ResourceRepository.resources.collectAsState()
    val levelTextbooks = ResourceRepository.getLevelTextbooks(selectedLevel)

    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // ── Student's own proposals (pending tracker) ──
    val myProposals = if (userState.role == UserRole.STUDENT) {
        ProposalRepository.getBySubmitter(userState.email)
    } else emptyList()
    val pendingCount = myProposals.count { it.status == "pending" }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // ── Pending proposals tracker (students only) ──
        if (pendingCount > 0 && userState.role == UserRole.STUDENT) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { pendingExpanded = !pendingExpanded },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "📋 $pendingCount Proposal${if (pendingCount != 1) "s" else ""} Pending Review",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (pendingExpanded) {
                Spacer(modifier = Modifier.height(4.dp))
                myProposals.filter { it.status == "pending" }.forEach { prop ->
                    androidx.compose.material3.Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "• ${prop.targetLevel}L — ${prop.notes.ifBlank { prop.driveLink.take(40) }}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

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

        // Semester filter chips + refresh button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            // Refresh button
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(onClick = {
                    isRefreshing = true
                    scope.launch {
                        CourseRepository.loadAll()
                        ResourceRepository.loadAll()
                        isRefreshing = false
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
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
                            text = "No courses available for this level and semester.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToPropose,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Be the first to contribute")
                        }
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
                    val courseResources = resourceMap[course.code] ?: emptyList()
            CourseCard(
                course = course,
                resources = courseResources,
                onClick = { onOpenViewer(course, null) },
                onStudyNotes = { onOpenViewer(course, "LN") },
                onPastQuestions = { onOpenViewer(course, "PQ") },
                onTextbook = { onOpenViewer(course, "TB") }
            )
                }
            }

            // ── Level-wide Textbooks section ──
            if (levelTextbooks.isNotEmpty()) {
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
                levelTextbooks.forEach { tb ->
                    item {
                        val tbCourse = Course(
                            code = "TEXTBOOK",
                            name = tb.label.ifBlank { "Reference Textbooks" },
                            category = "TEXTBOOKS",
                            level = selectedLevel,
                            semester = 1
                        )
                        CourseCard(
                            course = tbCourse,
                            resources = listOf(tb),
                            onClick = { onOpenViewer(tbCourse, null) },
                            onTextbook = { onOpenViewer(tbCourse, "TB") }
                        )
                    }
                }
            }
        }
    }
}
