package com.nimelssa.vault.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nimelssa.vault.data.*
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Real-time proposals from Firestore scoped to this rep's level (or all for admin)
    // WHY: callbackFlow in ProposalRepository keeps this list live — new proposals
    // appear automatically without the rep needing to refresh.
    val pendingProposals by ProposalRepository.pendingProposals(
        level = if (isAdmin) null else repLevel
    ).collectAsState(initial = emptyList())

    // In-memory courses for the Course Inventory section (still from CourseRepository)
    val allCourses by CourseRepository.courses.collectAsState()
    val displayCourses = if (isAdmin) allCourses else allCourses.filter { it.level == repLevel }

    var showAddForm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // ── Header ──
        Text(
            text = if (isAdmin) "🛡️ Admin Console — Overseer"
                   else "🛡️ ${repLevel} Level Rep Desk",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isAdmin)
                "You oversee all levels. AI scans Drive links and auto-classifies resources."
            else
                "Review and approve resource proposals for your level.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Pending proposals ──
        if (pendingProposals.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "✅ No pending proposals",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            Text(
                text = "⏳ Pending Proposals (${pendingProposals.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFBBF24)
            )
            Spacer(modifier = Modifier.height(8.dp))

            pendingProposals.forEach { proposal ->
                ProposalCard(
                    proposal = proposal,
                    onApprove = {
                        scope.launch {
                            try {
                                ProposalRepository.approveProposal(proposal, user.uid)
                                // Merge into local CourseRepository so Workspace updates immediately
                                CourseRepository.mergeCourseResources(
                                    code = proposal.courseCode,
                                    lectureNotesUrl = if (proposal.type == "Lecture Notes") proposal.driveUrl else "",
                                    pastQuestionsUrl = if (proposal.type == "Past Questions") proposal.driveUrl else "",
                                    submittedBy = proposal.submittedByName,
                                    notes = proposal.notes
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("AdminScreen", "Approve failed", e)
                            }
                        }
                    },
                    onReject = {
                        scope.launch {
                            try {
                                ProposalRepository.rejectProposal(proposal.id, user.uid)
                            } catch (e: Exception) {
                                android.util.Log.e("AdminScreen", "Reject failed", e)
                            }
                        }
                    },
                    onOpenLink = {
                        // Open Drive link in browser for rep to preview before approving
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(proposal.driveUrl))
                        context.startActivity(intent)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Add course form (admin & reps) ──
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
                if (showAddForm) "▾ Close Add Course Form" else "➕ Add New Course to Vault",
                fontWeight = FontWeight.Bold
            )
        }

        if (showAddForm) {
            Spacer(modifier = Modifier.height(8.dp))
            AddCourseForm(
                initialLevel = if (isAdmin) "100" else repLevel,
                lockLevel = !isAdmin,
                onAdded = { showAddForm = false }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Course inventory ──
        Text(
            text = "📚 Course Inventory ${if (!isAdmin) "(${repLevel} Level only)" else ""}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(displayCourses) { course ->
                CourseManageRow(
                    course = course,
                    canDelete = isAdmin || course.level == repLevel
                )
            }
        }
    }
}

// ── Proposal Card ─────────────────────────────────────────────────────────────

@Composable
private fun ProposalCard(
    proposal: Proposal,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onOpenLink: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1917))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Course code + name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${proposal.courseCode} — ${proposal.courseName.ifBlank { proposal.type }}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${proposal.level} Level • Semester ${proposal.semester} • ${proposal.type}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
                // AI badge
                if (proposal.aiClassified) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF4F46E5)
                    ) {
                        Text(
                            "🤖 AI",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Submitter
            if (proposal.submittedByName.isNotBlank()) {
                Text(
                    text = "Submitted by: ${proposal.submittedByName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6B7280)
                )
            }

            // Notes
            if (proposal.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📌 ${proposal.notes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFD1D5DB),
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Open Drive link button
                Button(
                    onClick = onOpenLink,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) { Text("🔗 Open", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) }

                // Approve
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) { Text("✅ Approve", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) }

                // Reject
                Button(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("❌ Reject", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

// ── Course manage row (unchanged from original) ───────────────────────────────

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
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(course.code, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(course.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${course.displayLevel} • ${course.displaySemester} • ${course.category}",
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

// ── Add Course Form (unchanged from original) ─────────────────────────────────

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
                value = code, onValueChange = { code = it.uppercase() },
                label = { Text("Course Code") }, placeholder = { Text("e.g., MLS 301") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Course Title") }, placeholder = { Text("e.g., Clinical Chemistry I") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = category, onValueChange = { category = it },
                label = { Text("Category") }, placeholder = { Text("e.g., CHEMICAL PATHOLOGY") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lockLevel) {
                    OutlinedTextField(
                        value = "$level Level", onValueChange = {}, readOnly = true, enabled = false,
                        modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    ExposedDropdownMenuBox(expanded = levelExpanded, onExpandedChange = { levelExpanded = it }) {
                        OutlinedTextField(
                            value = "$level Level", onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded) },
                            modifier = Modifier.weight(1f).menuAnchor(), singleLine = true, shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(expanded = levelExpanded, onDismissRequest = { levelExpanded = false }) {
                            listOf("100","200","300","400").forEach { l ->
                                DropdownMenuItem(text = { Text("$l Level") }, onClick = { level = l; levelExpanded = false })
                            }
                        }
                    }
                }

                val shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(selected = semester == 1, onClick = { semester = 1 }, shape = shape,
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary)
                    ) { Text("1st") }
                    SegmentedButton(selected = semester == 2, onClick = { semester = 2 }, shape = shape,
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary)
                    ) { Text("2nd") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (code.isNotBlank() && name.isNotBlank()) {
                        CourseRepository.addCourse(Course(code, name, category.ifBlank { "GENERAL" }, level, semester, 0))
                        onAdded()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = code.isNotBlank() && name.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Add to Vault", fontWeight = FontWeight.Bold) }
        }
    }
}
