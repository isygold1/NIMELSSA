package com.nimelssa.vault.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nimelssa.vault.data.AiMatchedItem
import com.nimelssa.vault.data.AiPreview
import com.nimelssa.vault.data.AiUnmatchedFile
import com.nimelssa.vault.data.Course
import com.nimelssa.vault.data.CourseRepository
import com.nimelssa.vault.data.DriveScanner
import com.nimelssa.vault.data.DriveScanner.ScanResult
import com.nimelssa.vault.data.FilenameParser
import com.nimelssa.vault.data.Levels
import com.nimelssa.vault.data.Proposal
import com.nimelssa.vault.data.ProposalRepository
import com.nimelssa.vault.data.Resource
import com.nimelssa.vault.data.ResourceRepository
import com.nimelssa.vault.data.UserRole
import com.nimelssa.vault.data.UserSession
import com.nimelssa.vault.ui.components.AppScreenHeader
import kotlinx.coroutines.launch

/**
 * A rep's or admin's manual assignment for an unmatched file.
 * Converts an AiUnmatchedFile into a resource after picking
 * type, course code, level, and semester.
 */
private data class ManualAssignment(
    val fileId: String,
    val fileName: String,
    val resourceType: String = "TB",   // "LN", "PQ", "TB", "OT"
    val courseCode: String = "",        // empty allowed for TB (level-wide textbook)
    val level: String = "",
    val semester: Int = 1               // 1 = 1st semester, 2 = 2nd semester
)

/**
 * Resource Manager / Admin Console with AI-powered proposal scanning.
 *
 * Reps manage resources for their level — scan proposals, assign files,
 * build the vault. Admins oversee all levels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    repLevel: String,
    onPreview: (Course, String?) -> Unit = { _, _ -> },
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val user by UserSession.state.collectAsState()
    val isAdmin = user.role == UserRole.ADMIN
    val canManage = isAdmin || user.role == UserRole.REP
        val allCourses by CourseRepository.courses.collectAsState()
    val proposals by ProposalRepository.proposals.collectAsState()
    val resourceMap by ResourceRepository.resources.collectAsState()

    val scope = rememberCoroutineScope()
    var showAddForm by remember { mutableStateOf(false) }
    var showLinkForm by remember { mutableStateOf(false) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }

    // ── Filter proposals for rep's level ──
    // Proposals are routed by targetLevel (set by student at submission time).
    // Admins see all; reps see only proposals whose targetLevel matches their repLevel.
    // Proposals without a targetLevel fall through to admin.
    val pendingProposals = if (isAdmin) {
        proposals.filter { it.status == "pending" }
    } else {
        proposals.filter {
            it.status == "pending" &&
            (it.targetLevel == repLevel || it.targetLevel.isBlank())
        }
    }

    // Track which proposals have been scanned (in-memory during this session)
    var scannedMap by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    // Track scanning state per proposal
    var scanningId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ── Header (shared pattern: hamburger + app label + screen title) ──
        AppScreenHeader(
            title = if (isAdmin) "Admin Console" else "Rep Desk",
            onOpenDrawer = onOpenDrawer
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isAdmin) "You oversee all levels. AI scans Drive links and auto-classifies resources."
                   else "You manage resources for ${repLevel}L. Scan proposals, assign files, and build the vault.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        // ── Pending Proposals ──
        if (pendingProposals.isNotEmpty()) {
            Text(
                text = "⏳ Pending Proposals (${pendingProposals.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(8.dp))

            pendingProposals.forEach { proposal ->
                ProposalCard(
                    proposal = proposal,
                    isScanning = scanningId == proposal.id,
                    hasScanned = scannedMap[proposal.id] == true,
                    onScan = {
                        scanningId = proposal.id
                        scope.launch {
                            scanProposal(proposal)
                            scannedMap = scannedMap + (proposal.id to true)
                            scanningId = null
                        }
                    },
                    onEdit = { /* TODO: inline edit mode */ },
                    onApprove = { manualAssignments ->
                        scope.launch {
                            approveProposal(proposal, user.email, manualAssignments)
                        }
                    },
                    onReject = {
                        scope.launch {
                            ProposalRepository.reject(proposal.id)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── Add Course Form ──
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
                    lockLevel = !isAdmin,
                    onAdded = { showAddForm = false }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // ── Course Inventory ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📚 Course Inventory ${if (!isAdmin) "(Level $repLevel only)" else ""}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (canManage) {
                TextButton(onClick = { showLinkForm = true }) {
                    Text("🔗 Link Code", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        val displayCourses = if (isAdmin) allCourses
                             else allCourses.filter { it.level == repLevel }
        val coursesWithResources = displayCourses.filter { course ->
            val key = CourseRepository.normalizeCode(course.code)
            resourceMap.containsKey(key) && resourceMap[key]!!.isNotEmpty()
        }

        coursesWithResources.forEach { course ->
            CourseManageRow(
                course = course,
                canDelete = isAdmin || course.level == repLevel,
                onPreview = { onPreview(course, null) },
                onEdit = { editingCourse = course }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        // ── Edit course dialog (CCMAS re-code keeps files, links old code) ──
        editingCourse?.let { course ->
            EditCourseDialog(
                course = course,
                onDismiss = { editingCourse = null },
                onSaved = { editingCourse = null }
            )
        }

        // ── Link course-code dialog (variant → canonical) ──
        if (showLinkForm) {
            LinkCodeDialog(
                onDismiss = { showLinkForm = false },
                onLinked = { showLinkForm = false }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  PROPOSAL CARD
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ProposalCard(
    proposal: Proposal,
    isScanning: Boolean,
    hasScanned: Boolean,
    onScan: () -> Unit,
    onEdit: () -> Unit,
    onApprove: (Map<String, ManualAssignment>) -> Unit,
    onReject: () -> Unit
) {
    val aiPreview = proposal.aiPreview
    val manualAssignments = remember { mutableStateMapOf<String, ManualAssignment>() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1917))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Submitter info
            Text(
                text = "From: ${proposal.submittedByName} (${proposal.submittedBy})",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Link (truncated)
            Text(
                text = "🔗 ${truncateUrl(proposal.driveLink)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF93C5FD),
                maxLines = 1
            )
            if (proposal.notes.isNotBlank()) {
                Text(
                    text = "📌 ${proposal.notes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFD1D5DB),
                    maxLines = 2
                )
            }

            // Target level badge
            if (proposal.targetLevel.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "🎯 For: ${proposal.targetLevel} Level",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF86EFAC),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Updated-version flag ──
            val existingByFileId = DriveScanner.extractFileId(proposal.driveLink)
                ?.let { ResourceRepository.findByFileId(it) }
            if (existingByFileId != null && existingByFileId.md5Checksum.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD).copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "🔄 Updated version of ${existingByFileId.resourceLabel}" +
                                if (existingByFileId.courseCode.isNotBlank())
                                    " in ${existingByFileId.courseCode}" else "",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFD54F),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ── Scan section ──
            if (!hasScanned && !isScanning) {
                Button(
                    onClick = onScan,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    Text("🔍 Scan with AI", fontWeight = FontWeight.Bold)
                }
            }

            if (isScanning) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = Color(0xFF6366F1),
                        strokeWidth = 2.dp
                    )
                    Text(
                        "🔍 Scanning Drive link...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF93C5FD)
                    )
                }
            }

            // ── AI Preview (after scan) ──
            if (hasScanned && aiPreview != null) {
                AiPreviewSection(
                    aiPreview = aiPreview,
                    manualAssignments = manualAssignments,
                    onAssignmentChange = { fileId, assignment ->
                        if (assignment == null) manualAssignments.remove(fileId)
                        else manualAssignments[fileId] = assignment
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // ── Action buttons ──
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onApprove(manualAssignments.toMap()) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                    ) { Text("✅ Approve All", fontWeight = FontWeight.Bold) }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) { Text("❌ Reject", fontWeight = FontWeight.Bold) }
                }

                if (aiPreview.scanStatus == "partial") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "⚠️ Some files couldn't be matched. Assign them above or reject.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFBBF24)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  AI PREVIEW SECTION  (with interactive unmatched file assignment)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AiPreviewSection(
    aiPreview: AiPreview,
    manualAssignments: Map<String, ManualAssignment>,
    onAssignmentChange: (String, ManualAssignment?) -> Unit
) {
    when (aiPreview.scanStatus) {
        "failed" -> {
            Text(
                text = "❌ Scan failed: ${aiPreview.errorMessage}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFF87171)
            )
        }
        "success", "partial" -> {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "── AI Scan Results ──",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Summary
            val statusIcon = if (aiPreview.scanStatus == "success") "✅" else "⚠️"
            val assignedCount = manualAssignments.count { it.value.resourceType.isNotBlank() }
            Text(
                text = "$statusIcon ${aiPreview.matchedItems.size} matched + ${assignedCount} assigned / ${aiPreview.totalFilesScanned} files",
                style = MaterialTheme.typography.labelSmall,
                color = if (aiPreview.scanStatus == "success") Color(0xFF4ADE80) else Color(0xFFFBBF24)
            )

            // Matched items
            aiPreview.matchedItems.forEach { item ->
                val icon = when (item.resourceType) {
                    "LN" -> "📖"
                    "PQ" -> "📝"
                    "TB" -> "📚"
                    else -> "📄"
                }
                Text(
                    text = "$icon ${item.courseCode} → ${item.resourceLabel} (${item.fileName})",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFD1D5DB),
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                )
            }

            // Unmatched files — interactive assignment cards
            if (aiPreview.unmatchedFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "⚠️ Assign Unmatched (${aiPreview.unmatchedFiles.size}):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFBBF24)
                )

                aiPreview.unmatchedFiles.forEach { uf ->
                    UnmatchedAssignmentCard(
                        unmatchedFile = uf,
                        currentAssignment = manualAssignments[uf.fileId],
                        onAssign = { assignment ->
                            onAssignmentChange(uf.fileId, assignment)
                        },
                        onClear = {
                            onAssignmentChange(uf.fileId, null)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
        else -> {
            Text(
                text = "⏳ Scan pending...",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  UNMATCHED ASSIGNMENT CARD
// ═══════════════════════════════════════════════════════════════

/**
 * Interactive card for assigning an unmatched file to a course/textbook.
 *
 * Two modes:
 *   - **Textbook (TB)**: admin picks level; course code is optional.
 *   - **Other (LN/PQ/OT)**: admin types course code (with suggestions from
 *     CourseRepository) and picks level.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnmatchedAssignmentCard(
    unmatchedFile: AiUnmatchedFile,
    currentAssignment: ManualAssignment?,
    onAssign: (ManualAssignment) -> Unit,
    onClear: () -> Unit
) {
    val allCourses by CourseRepository.courses.collectAsState()

    var resourceType by remember(currentAssignment) {
        mutableStateOf(currentAssignment?.resourceType ?: "TB")
    }
    var courseCode by remember(currentAssignment) {
        mutableStateOf(currentAssignment?.courseCode ?: "")
    }
    var level by remember(currentAssignment) {
        mutableStateOf(currentAssignment?.level ?: "")
    }
    var semester by remember(currentAssignment) {
        mutableIntStateOf(currentAssignment?.semester ?: 1)
    }
    var showCourseSuggestions by remember { mutableStateOf(false) }
    var levelExpanded by remember { mutableStateOf(false) }

    // Derive suggested courses based on typed code
    val suggestions = remember(courseCode, allCourses) {
        if (courseCode.length >= 2) {
            // Compare in canonical form so "MLS201" matches seed code "MLS 201";
            // variant codes (old CCMAS) resolve to their canonical course first.
            val query = CourseRepository.resolveCode(courseCode)
            allCourses.filter { CourseRepository.normalizeCode(it.code).contains(query) }
                .map { it.code }
                .distinct()
                .take(6)
        } else emptyList()
    }

    // Determine if the typed code looks like an existing course
    val existingCourse = remember(courseCode, allCourses) {
        CourseRepository.findCourse(courseCode)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D3748)),
        border = BorderStroke(1.dp, Color(0xFF4A5568))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // File name
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📄 ${unmatchedFile.fileName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE2E8F0),
                    modifier = Modifier.weight(1f)
                )
                // MUST-HAVE: the rep must be able to view the file's content
                // before assigning it — opens the Drive preview in the browser.
                val context = LocalContext.current
                Text(
                    text = "👁 View",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF93C5FD),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(
                                        "https://drive.google.com/file/d/${unmatchedFile.fileId}/view"
                                    )
                                )
                            )
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                if (currentAssignment != null) {
                    Text(
                        text = if (resourceType == "TB") "📚 TB" else "✓ Assigned",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4ADE80),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── Resource Type selector ──
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("TB" to "📚 TB", "LN" to "📖 LN", "PQ" to "📝 PQ", "OT" to "📄 OT").forEach { (type, label) ->
                    val isSelected = resourceType == type
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { resourceType = type }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── Course Code field (optional for TB, required for others) ──
            if (resourceType != "TB") {
                ExposedDropdownMenuBox(
                    expanded = showCourseSuggestions && suggestions.isNotEmpty(),
                    onExpandedChange = { showCourseSuggestions = it }
                ) {
                    OutlinedTextField(
                        value = courseCode,
                        onValueChange = {
                            courseCode = it.uppercase()
                            showCourseSuggestions = it.length >= 2
                        },
                        label = { Text("Course Code") },
                        placeholder = { Text("e.g., MLS 301") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color(0xFF4A5568),
                            focusedBorderColor = if (existingCourse != null) Color(0xFF4ADE80) else Color(0xFF6366F1),
                            unfocusedTextColor = Color.White,
                            focusedTextColor = Color.White
                        ),
                        textStyle = MaterialTheme.typography.bodySmall
                    )

                    if (suggestions.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = showCourseSuggestions,
                            onDismissRequest = { showCourseSuggestions = false }
                        ) {
                            suggestions.forEach { code ->
                                DropdownMenuItem(
                                    text = {
                                        val c = allCourses.find { it.code == code }
                                        Text(
                                            "${code}  ${c?.name ?: ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    },
                                    onClick = {
                                        courseCode = code
                                        showCourseSuggestions = false
                                        // Auto-fill level & semester from existing course
                                        val found = allCourses.find { it.code == code }
                                        if (found != null) {
                                            if (level.isBlank()) level = found.level
                                            semester = found.semester
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                if (existingCourse != null) {
                    Text(
                        text = "✓ ${existingCourse.name} (${existingCourse.level} Level)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4ADE80)
                    )
                } else if (courseCode.length >= 3) {
                    Text(
                        text = "⚠️ New course code — will create entry",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFBBF24)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
            } else {
                // TB mode — course code is optional
                OutlinedTextField(
                    value = courseCode,
                    onValueChange = { courseCode = it.uppercase() },
                    label = { Text("Course Code (optional for textbook)") },
                    placeholder = { Text("e.g., MLS 301 or leave blank") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFF4A5568),
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedTextColor = Color.White,
                        focusedTextColor = Color.White
                    ),
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── Level picker ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Level: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )
                ExposedDropdownMenuBox(
                    expanded = levelExpanded,
                    onExpandedChange = { levelExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (level.isNotBlank()) "${level} Level" else "Select",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded) },
                        modifier = Modifier.weight(1f).menuAnchor(),
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color(0xFF4A5568),
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedTextColor = Color.White,
                            focusedTextColor = Color.White
                        ),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    ExposedDropdownMenu(
                        expanded = levelExpanded,
                        onDismissRequest = { levelExpanded = false }
                    ) {
                        Levels.ALL.forEach { l ->
                            DropdownMenuItem(
                                text = { Text("${l} Level", color = Color.White) },
                                onClick = { level = l; levelExpanded = false }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── Semester selector ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Semester: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.width(8.dp))
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = semester == 1,
                        onClick = { semester = 1 },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = 0,
                            count = 2
                        ),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) { Text("1st", style = MaterialTheme.typography.labelSmall) }
                    SegmentedButton(
                        selected = semester == 2,
                        onClick = { semester = 2 },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = 1,
                            count = 2
                        ),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) { Text("2nd", style = MaterialTheme.typography.labelSmall) }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Apply / Clear buttons ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (currentAssignment != null) {
                    Text(
                        text = "✕ Clear",
                        modifier = Modifier
                            .clickable { onClear() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFF87171)
                    )
                }
                Button(
                    onClick = {
                        onAssign(
                            ManualAssignment(
                                fileId = unmatchedFile.fileId,
                                fileName = unmatchedFile.fileName,
                                resourceType = resourceType,
                                courseCode = courseCode,
                                level = level.ifBlank {
                                    // Infer from existing course or default to 200
                                    CourseRepository.findCourse(courseCode)?.level ?: "200"
                                },
                                semester = semester
                            )
                        )
                    },
                    enabled = level.isNotBlank() && (resourceType == "TB" || courseCode.isNotBlank()),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Text(
                        text = if (currentAssignment != null) "✓ Update" else "✓ Assign",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  SCANNING LOGIC
// ═══════════════════════════════════════════════════════════════

/**
 * Scan a proposal's Drive link and update its AI preview.
 */
private suspend fun scanProposal(proposal: Proposal) {
    val result = DriveScanner.scanLink(proposal.driveLink)

    if (result.error.isNotBlank()) {
        ProposalRepository.updateAiPreview(
            proposal.id,
            AiPreview(
                scanStatus = "failed",
                errorMessage = result.error
            )
        )
        return
    }

    // Parse each file (may be empty — that's OK, it just means no matches)
    val matchedItems = mutableListOf<AiMatchedItem>()
    val unmatchedFiles = mutableListOf<com.nimelssa.vault.data.AiUnmatchedFile>()

    loop@ for (file in result.files) {
        // Use path-aware parsing — folder names (like "200 level / MLS 201")
        // give the AI much more context than filenames alone
        val parseResult = FilenameParser.parseWithPath(file.name, file.path)

        // Auto-match only on HIGH/MEDIUM confidence. LOW (e.g. unrecognized
        // prefix like "WA000" from "DOC-...-WA0003") and NONE go to the
        // rep's manual-assign list instead of silently becoming resources.
        val autoMatch = parseResult.courseCode != null &&
            (parseResult.confidence == FilenameParser.Confidence.HIGH ||
             parseResult.confidence == FilenameParser.Confidence.MEDIUM)

        if (autoMatch) {
            // Non-null because autoMatch above guarantees courseCode != null.
            // (Hoisted into a val so the compiler sees a stable non-null type;
            // continue@loop is defensive only and never actually fires.)
            val matchedCode = parseResult.courseCode ?: continue@loop

            val resourceLabel = when (parseResult.resourceType) {
                "LN" -> "Lecture Notes"
                "PQ" -> "Past Questions"
                "TB" -> "Textbook"
                else -> "Other"
            }

            // Look up the course in the repository for its full name
            val existingCourse = CourseRepository.findCourse(matchedCode)
            val courseName = existingCourse?.name ?: matchedCode
            // Use level from path first, then from existing course, then infer from code
            val level = parseResult.level
                ?: existingCourse?.level
                ?: inferLevelFromCode(matchedCode)
            val semester = parseResult.semester ?: existingCourse?.semester ?: 1

            matchedItems.add(
                AiMatchedItem(
                    courseCode = matchedCode,
                    courseName = courseName,
                    level = level,
                    semester = semester,
                    resourceType = parseResult.resourceType ?: "OT",
                    resourceLabel = resourceLabel,
                    fileName = file.name,
                    fileId = file.id
                )
            )
        } else {
            unmatchedFiles.add(
                com.nimelssa.vault.data.AiUnmatchedFile(
                    fileName = file.name,
                    reason = parseResult.reason.ifBlank { "Could not identify course from filename or folder path" },
                    fileId = file.id
                )
            )
        }
    }

    val preview = AiPreview(
        scanStatus = if (unmatchedFiles.isEmpty()) "success" else "partial",
        matchedItems = matchedItems,
        unmatchedFiles = unmatchedFiles,
        totalFilesScanned = result.files.size,
        sourceFolderName = result.folderName
    )

    ProposalRepository.updateAiPreview(proposal.id, preview)
}

/**
 * Approve a proposal: write all matched (AI + manual) resources to Firestore and clean up.
 */
private suspend fun approveProposal(
    proposal: Proposal,
    reviewerEmail: String,
    manualAssignments: Map<String, ManualAssignment> = emptyMap()
) {
    val preview = proposal.aiPreview ?: return

    // Build individual file URL from Drive file ID
    fun fileUrl(fileId: String) = "https://drive.google.com/file/d/$fileId/view"

    // ── 1. Convert manual assignments to AiMatchedItem ──
    val manualMatched = manualAssignments.values.map { assignment ->
        val existing = CourseRepository.findCourse(assignment.courseCode)
        val resourceLabel = when (assignment.resourceType) {
            "LN" -> "Lecture Notes"
            "PQ" -> "Past Questions"
            "TB" -> "Textbook"
            else -> "Other"
        }
        AiMatchedItem(
            courseCode = assignment.courseCode,
            courseName = existing?.name ?: assignment.courseCode,
            level = assignment.level.ifBlank { existing?.level ?: "200" },
            semester = assignment.semester,
            resourceType = assignment.resourceType,
            resourceLabel = resourceLabel,
            fileName = assignment.fileName,
            fileId = assignment.fileId
        )
    }

    // ── 2. Combine AI matched + manual matched ──
    val allItems = preview.matchedItems + manualMatched

    // Separate textbooks WITHOUT course code (go to level textbooks)
    val levelTextbookItems = allItems.filter {
        it.resourceType == "TB" && it.courseCode.isBlank()
    }
    val courseItems = allItems.filter {
        it.resourceType != "TB" || it.courseCode.isNotBlank()
    }

    // ── 3. Save level-wide textbooks ──
    for (tb in levelTextbookItems) {
        val tbMd5 = DriveScanner.getFileMd5(tb.fileId) ?: ""
        ResourceRepository.add(
            Resource(
                courseCode = "",
                resourceType = "TB",
                level = tb.level.ifBlank { "200" },
                masterUrl = fileUrl(tb.fileId),
                fileId = tb.fileId,
                md5Checksum = tbMd5,
                label = tb.fileName.removeSuffix(".pdf").removeSuffix(".PDF")
                    .replace("_", " ").replace("-", " ").trim(),
                fileName = tb.fileName,
                submittedBy = proposal.submittedBy,
                notes = "[Textbook] from ${tb.fileName} | ${proposal.notes}"
            ),
            approvedBy = reviewerEmail
        )
    }

    // ── 4. Group course items by course code ──
    val groupedByCode = courseItems.groupBy { it.courseCode }

    for ((courseCode, items) in groupedByCode) {
        val notesSummary = items.joinToString("; ") {
            "[${it.resourceLabel}] from ${it.fileName}"
        }
        val finalNotes = listOfNotNull(
            proposal.notes.takeIf { it.isNotBlank() },
            notesSummary.takeIf { it.isNotBlank() }
        ).joinToString(" | ")

        // Create one Resource per matched type
        val typesInGroup = items.map { it.resourceType }.distinct()
        for (type in typesInGroup) {
            val typeItems = items.filter { it.resourceType == type }
            val firstItem = typeItems.first()
            val resourceLabel = when (type) {
                "LN" -> "Lecture Notes"
                "PQ" -> "Past Questions"
                "TB" -> "Textbook"
                else -> "Other"
            }

            val md5 = DriveScanner.getFileMd5(firstItem.fileId) ?: ""
            ResourceRepository.add(
                Resource(
                    courseCode = courseCode,
                    resourceType = type,
                    level = firstItem.level.ifBlank {
                        "${courseCode.firstOrNull { it.isDigit() } ?: '2'}00"
                    },
                    masterUrl = fileUrl(firstItem.fileId),
                    fileId = firstItem.fileId,
                    md5Checksum = md5,
                    label = "$resourceLabel for $courseCode",
                    fileName = firstItem.fileName,
                    submittedBy = proposal.submittedBy,
                    notes = finalNotes
                ),
                approvedBy = reviewerEmail
            )
        }

        // Ensure course exists in CourseRepository
        val existing = CourseRepository.findCourse(courseCode)
        if (existing == null) {
            val firstItem = items.first()
            val inferredLevel = firstItem.level.ifBlank {
                "${firstItem.courseCode.firstOrNull { it.isDigit() } ?: '2'}00"
            }
            CourseRepository.addCourse(
                Course(
                    code = courseCode,
                    name = firstItem.courseName,
                    category = inferCategory(courseCode),
                    level = inferredLevel,
                    semester = firstItem.semester
                )
            )
        }
    }

    // ── 5. Mark the proposal as approved ──
    ProposalRepository.approve(proposal.id, reviewerEmail)
}

// ═══════════════════════════════════════════════════════════════
//  HELPER FUNCTIONS
// ═══════════════════════════════════════════════════════════════

/** Infer level (100, 200, 300, 400) from course code like "CSC201" */
private fun inferLevelFromCode(code: String): String {
    val digit = code.firstOrNull { it.isDigit() } ?: '2'
    return "${digit}00"
}

/** Infer category from course code prefix */
private fun inferCategory(code: String): String = when {
    code.startsWith("BIO") -> "BIOLOGY"
    code.startsWith("CHM") -> "CHEMISTRY"
    code.startsWith("PHY") -> "PHYSICS"
    code.startsWith("BCH") -> "BIOCHEMISTRY"
    code.startsWith("MLS") -> "MEDICAL LABORATORY SCIENCE"
    code.startsWith("GST") -> "GENERAL STUDIES"
    code.startsWith("STA") -> "MATHEMATICS"
    code.startsWith("CSC") -> "COMPUTER SCIENCE"
    else -> "MEDICAL LABORATORY SCIENCE"
}

/** Truncate a URL for display */
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

// ═══════════════════════════════════════════════════════════════
//  COURSE MANAGEMENT ROW
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CourseManageRow(
    course: Course,
    canDelete: Boolean,
    onPreview: () -> Unit,
    onEdit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val resourceMap by ResourceRepository.resources.collectAsState()
    val courseResources = resourceMap[CourseRepository.normalizeCode(course.code)] ?: emptyList()
    val hasNotes = courseResources.any { it.resourceType == "LN" }
    val hasPqs = courseResources.any { it.resourceType == "PQ" }
    val hasTb = courseResources.any { it.resourceType == "TB" }
    val hasAny = hasNotes || hasPqs || hasTb

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
                // Show resource indicators
                if (hasAny) {
                    Text(
                        text = buildString {
                            if (hasNotes) append("📖 LN")
                            if (hasNotes && hasPqs) append(" | ")
                            if (hasPqs) append("📝 PQ")
                            if ((hasNotes || hasPqs) && hasTb) append(" | ")
                            if (hasTb) append("📚 TB")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasAny) {
                    IconButton(
                        onClick = onPreview,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFF6366F1)
                        )
                    ) {
                        Text("👁️", fontSize = 14.sp)
                    }
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("✏️", fontSize = 14.sp)
                }
                if (canDelete) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                ResourceRepository.removeAllForCourse(course.code)
                                CourseRepository.removeCourse(course.code)
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text("🗑️", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  ADD COURSE FORM
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCourseForm(
    initialLevel: String,
    lockLevel: Boolean,
    onAdded: () -> Unit
) {
    val scope = rememberCoroutineScope()
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
                        Levels.ALL.forEach { l ->
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
                        scope.launch {
                            CourseRepository.addCourse(
                                Course(code, name, category.ifBlank { "GENERAL" }, level, semester)
                            )
                            onAdded()
                        }
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

// ═══════════════════════════════════════════════════════════════
//  EDIT COURSE DIALOG  (CCMAS re-code)
// ═══════════════════════════════════════════════════════════════

/**
 * Edit a course in place. Changing the code never deletes or re-links files:
 * the old code is recorded as an alias so existing resources keep appearing
 * on the new shelf, and the level/semester move with the record (workspace
 * level dropdown unaffected). Collision guard: the new code must not already
 * belong to a different course or be linked to one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCourseDialog(
    course: Course,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var code by remember(course) { mutableStateOf(course.code) }
    var name by remember(course) { mutableStateOf(course.name) }
    var category by remember(course) { mutableStateOf(course.category) }
    var level by remember(course) { mutableStateOf(course.level) }
    var semester by remember(course) { mutableIntStateOf(course.semester) }
    var levelExpanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Mistake-proofing (option B): unchecked = the old code is NOT auto-linked
    // as a variant, so a wrong rename doesn't pollute the alias table.
    var autoLink by remember { mutableStateOf(true) }

    val codeChanged = CourseRepository.normalizeCode(code) != CourseRepository.normalizeCode(course.code)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✏️ Edit Course") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                    ExposedDropdownMenuBox(
                        expanded = levelExpanded,
                        onExpandedChange = { levelExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = "$level Level",
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
                            Levels.ALL.forEach { l ->
                                DropdownMenuItem(
                                    text = { Text("${l} Level") },
                                    onClick = { level = l; levelExpanded = false }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = semester == 1,
                            onClick = { semester = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary,
                                activeContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) { Text("1st") }
                        SegmentedButton(
                            selected = semester == 2,
                            onClick = { semester = 2 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary,
                                activeContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) { Text("2nd") }
                    }
                }
                if (codeChanged) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "↔ Files stay put: nothing is deleted or re-filed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            autoLink = !autoLink
                        }
                    ) {
                        Checkbox(
                            checked = autoLink,
                            onCheckedChange = { autoLink = it }
                        )
                        Text(
                            text = "Remember '${course.code}' as a variant of '${code.trim().uppercase()}' " +
                                   "so its files still show on this shelf",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                error?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (code.isBlank() || name.isBlank()) {
                        error = "Course code and title are required."
                        return@TextButton
                    }
                    val oldNorm = CourseRepository.normalizeCode(course.code)
                    val newNorm = CourseRepository.normalizeCode(code)
                    val existing = CourseRepository.findCourse(code)
                    val linkedCanonical = CourseRepository.canonicalFor(code)
                    if (existing != null && CourseRepository.normalizeCode(existing.code) != oldNorm) {
                        error = "'${existing.code}' already belongs to '${existing.name}'. " +
                                "Use 'Link Code' to merge it into this course instead."
                        return@TextButton
                    }
                    if (linkedCanonical != null && linkedCanonical != oldNorm) {
                        error = "'$code' is already linked to another course. " +
                                "Unlink it in the Link Code dialog first."
                        return@TextButton
                    }
                    scope.launch {
                        saving = true
                        CourseRepository.updateCourseCode(
                            course.code,
                            Course(
                                code = code.trim().uppercase(),
                                name = name.trim(),
                                category = category.trim().ifBlank { "GENERAL" },
                                level = level,
                                semester = semester
                            ),
                            autoLink = autoLink
                        )
                        saving = false
                        onSaved()
                    }
                },
                enabled = !saving
            ) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ═══════════════════════════════════════════════════════════════
//  LINK COURSE-CODE DIALOG  (variant → canonical)
// ═══════════════════════════════════════════════════════════════

/**
 * Manually link a variant course code (e.g. an old CCMAS code that never
 * existed in the catalog) to a canonical course. Resources filed under the
 * variant code then appear on the canonical course's shelf, and new
 * submissions using the variant code auto-match — no course deletion needed.
 */
@Composable
private fun LinkCodeDialog(
    onDismiss: () -> Unit,
    onLinked: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val allCourses by CourseRepository.courses.collectAsState()
    val aliases by CourseRepository.aliases.collectAsState()
    var variant by remember { mutableStateOf("") }
    var targetCode by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val targetSuggestions = remember(targetCode, allCourses) {
        if (targetCode.length >= 2) {
            val query = CourseRepository.resolveCode(targetCode)
            allCourses.filter { CourseRepository.normalizeCode(it.code).contains(query) }
                .map { it.code }
                .distinct()
                .take(6)
        } else emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔗 Link Course Code") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Old/alternate code → course it actually belongs to. " +
                           "Files filed under the old code will appear on the target's shelf.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = variant,
                    onValueChange = { variant = it.uppercase() },
                    label = { Text("Variant code (as filed)") },
                    placeholder = { Text("e.g., MLS 301") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = targetCode,
                    onValueChange = { targetCode = it.uppercase() },
                    label = { Text("Target course code") },
                    placeholder = { Text("e.g., MLS 311") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                if (targetSuggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Suggestions: ${targetSuggestions.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                error?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Existing links",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (aliases.isEmpty()) {
                    Text(
                        text = "No links yet. Add one above to map an old CCMAS code to its current course.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    aliases.forEach { (variant, canonical) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$variant → $canonical",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    scope.launch { CourseRepository.removeAlias(variant) }
                                },
                                modifier = Modifier.size(28.dp)
                            ) { Text("✖", fontSize = 12.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (variant.isBlank() || targetCode.isBlank()) {
                        error = "Both codes are required."
                        return@TextButton
                    }
                    val target = CourseRepository.findCourse(targetCode)
                    if (target == null) {
                        error = "Target course not found — add it via 'Add New Course' first."
                        return@TextButton
                    }
                    if (CourseRepository.normalizeCode(variant) == CourseRepository.normalizeCode(target.code)) {
                        error = "A course cannot be linked to itself."
                        return@TextButton
                    }
                    scope.launch {
                        saving = true
                        CourseRepository.addAlias(variant, target.code)
                        saving = false
                        onLinked()
                    }
                },
                enabled = !saving
            ) { Text(if (saving) "Linking…" else "Link") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
