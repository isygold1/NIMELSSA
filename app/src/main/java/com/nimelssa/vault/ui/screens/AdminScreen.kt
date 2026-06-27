package com.nimelssa.vault.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nimelssa.vault.data.Course
import com.nimelssa.vault.data.CourseRepository
import com.nimelssa.vault.data.FirestoreCourseSync
import com.nimelssa.vault.data.UserRole
import com.nimelssa.vault.data.UserSession
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    repLevel: String,
    onPreview: (Course) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val user by UserSession.state.collectAsState()
    val isAdmin = user.role == UserRole.ADMIN
    val canManage = isAdmin || user.role == UserRole.REP
    val allCourses by CourseRepository.courses.collectAsState()

    var showAddForm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Pending proposals for this rep's level (all, not just first)
    val allPending = CourseRepository.getPendingCourses()
    val pendingForLevel = if (isAdmin) allPending
                          else allPending.filter { it.level == repLevel }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = if (isAdmin) "🛡️ Admin Console — Full Authority"
                   else "🛡️ ${repLevel}L Rep Desk",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (isAdmin) "Manage all courses and approve proposals across all levels."
                   else "Review proposals and manage courses for your level.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── All pending proposals ──
        if (pendingForLevel.isNotEmpty()) {
            Text(
                text = "⏳ Pending Proposals (${pendingForLevel.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFBBF24)
            )
            Spacer(modifier = Modifier.height(8.dp))

            pendingForLevel.forEach { pending ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1917))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Course code + name
                        Text(
                            text = "${pending.code} — ${pending.name}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        // Meta info
                        Text(
                            text = "${pending.category} • ${pending.displaySemester}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                        // Submitted by
                        if (pending.submittedBy.isNotBlank()) {
                            Text(
                                text = "Submitted by: ${pending.submittedBy}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF6B7280)
                            )
                        }

                        // ── Resource info ──
                        Spacer(modifier = Modifier.height(6.dp))
                        if (pending.lectureNotesUrl.isNotBlank()) {
                            Text(
                                text = "📖 Notes: ${truncateUrl(pending.lectureNotesUrl)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF93C5FD),
                                maxLines = 1
                            )
                        }
                        if (pending.pastQuestionsUrl.isNotBlank()) {
                            Text(
                                text = "📝 PQs: ${truncateUrl(pending.pastQuestionsUrl)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF93C5FD),
                                maxLines = 1
                            )
                        }
                        if (pending.notes.isNotBlank()) {
                            Text(
                                text = "📌 ${pending.notes}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFD1D5DB),
                                maxLines = 2
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // ── Action buttons ──
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Preview button
                            if (pending.hasResources) {
                                Button(
                                    onClick = { onPreview(pending) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF6366F1)
                                    )
                                ) { Text("👁️ Preview", fontWeight = FontWeight.Bold) }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            // Approve button
                            Button(
                                onClick = {
                                    CourseRepository.approveCourse(pending.code)
                                    scope.launch {
                                        FirestoreCourseSync.saveResources(
                                            code = pending.code,
                                            lectureNotesUrl = pending.lectureNotesUrl,
                                            pastQuestionsUrl = pending.pastQuestionsUrl,
                                            submittedBy = pending.submittedBy,
                                            notes = pending.notes
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                            ) { Text("✅ Approve", fontWeight = FontWeight.Bold) }
                            Spacer(modifier = Modifier.width(6.dp))
                            // Reject button
                            Button(
                                onClick = {
                                    scope.launch {
                                        FirestoreCourseSync.removeResources(pending.code)
                                    }
                                    CourseRepository.rejectCourse(pending.code)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                            ) { Text("❌ Reject", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── Add New Course (admin & reps) ──
        if (canManage) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Use this form to pre-populate a course entry with its name, category, and level. " +
                       "Students can then submit resources against it via the Propose tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { showAddForm = !showAddForm },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    if (showAddForm) "▾ Close Add Course Form"
                    else "➕ Add New Course to Vault",
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (showAddForm) {
                AddCourseForm(
                    initialLevel = if (isAdmin) "100" else repLevel,
                    lockLevel = !isAdmin, // reps can only add to their level
                    onAdded = { showAddForm = false }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // ── Manage Courses ──
        Text(
            text = "📚 Course Inventory ${if (!isAdmin) "(Level $repLevel only)" else ""}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        val displayCourses = if (isAdmin) allCourses
                             else allCourses.filter { it.level == repLevel }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(displayCourses) { course ->
                CourseManageRow(
                    course = course,
                    canDelete = isAdmin || course.level == repLevel
                )
            }
        }
    }
}

@Composable
private fun CourseManageRow(course: Course, canDelete: Boolean) {
    val scope = rememberCoroutineScope()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.code,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${course.displayLevel} • ${course.displaySemester} • ${course.category}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canDelete) {
                Text(
                    text = "🗑️",
                    modifier = Modifier.clickable {
                        scope.launch {
                            FirestoreCourseSync.removeResources(course.code)
                        }
                        CourseRepository.removeCourse(course.code)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCourseForm(
    initialLevel: String,
    lockLevel: Boolean,
    onAdded: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(initialLevel) }
    var semester by remember { mutableIntStateOf(1) }
    var levelExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("New Course Entry", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                label = { Text("Course Code") },
                placeholder = { Text("e.g., MLS 301") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Course Title") },
                placeholder = { Text("e.g., Clinical Chemistry I") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category") },
                placeholder = { Text("e.g., CHEMICAL PATHOLOGY") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                if (lockLevel) {
                    // Rep: show level as read-only
                    OutlinedTextField(
                        value = "${level} Level",
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    // Admin: pick any level
                    ExposedDropdownMenuBox(
                        expanded = levelExpanded,
                        onExpandedChange = { levelExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = "${level} Level",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded) },
                            modifier = Modifier.weight(1f).menuAnchor(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = levelExpanded,
                            onDismissRequest = { levelExpanded = false }
                        ) {
                            listOf("100","200","300","400").forEach { l ->
                                DropdownMenuItem(
                                    text = { Text("${l} Level") },
                                    onClick = { level = l; levelExpanded = false }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))

                val shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = semester == 1,
                        onClick = { semester = 1 },
                        shape = shape,
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) { Text("1st") }
                    SegmentedButton(
                        selected = semester == 2,
                        onClick = { semester = 2 },
                        shape = shape,
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) { Text("2nd") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (code.isNotBlank() && name.isNotBlank()) {
                        CourseRepository.addCourse(
                            Course(code, name, category.ifBlank { "GENERAL" }, level, semester, 0)
                        )
                        onAdded()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = code.isNotBlank() && name.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Add to Vault", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Truncate a URL for display in the proposal card. */
private fun truncateUrl(url: String): String {
    return try {
        val u = java.net.URL(url)
        val host = u.host.removePrefix("www.")
        val path = u.path
        if (path.length > 25) "${host}...${path.takeLast(15)}"
        else "${host}${path}"
    } catch (_: Exception) {
        if (url.length > 35) url.take(32) + "..." else url
    }
}
