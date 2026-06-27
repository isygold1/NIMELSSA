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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.nimelssa.vault.data.AiMatchedItem
import com.nimelssa.vault.data.AiPreview
import com.nimelssa.vault.data.Course
import com.nimelssa.vault.data.CourseRepository
import com.nimelssa.vault.data.DriveScanner
import com.nimelssa.vault.data.DriveScanner.ScanResult
import com.nimelssa.vault.data.FilenameParser
import com.nimelssa.vault.data.FirestoreCourseSync
import com.nimelssa.vault.data.Proposal
import com.nimelssa.vault.data.ProposalRepository
import com.nimelssa.vault.data.UserRole
import com.nimelssa.vault.data.UserSession
import kotlinx.coroutines.launch

/**
 * Rep Desk / Admin Console with AI-powered proposal scanning.
 *
 * Reps see proposals whose AI results match their level.
 * Admins see all proposals.
 */
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
    val proposals by ProposalRepository.proposals.collectAsState()

    val scope = rememberCoroutineScope()
    var showAddForm by remember { mutableStateOf(false) }

    // ── Filter proposals for rep's level ──
    val pendingProposals = if (isAdmin) {
        proposals.filter { it.status == "pending" }
    } else {
        proposals.filter { it.status == "pending" && matchesRepLevel(it, repLevel) }
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
        // ── Header ──
        Text(
            text = if (isAdmin) "🛡️ Admin Console — Full Authority"
                   else "🛡️ ${repLevel}L Rep Desk",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isAdmin) "AI scans Drive links and auto-classifies resources."
                   else "Review proposals and manage courses for your level.",
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
                color = Color(0xFFFBBF24)
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
                    onApprove = {
                        scope.launch {
                            approveProposal(proposal, user.email)
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
        Text(
            text = "📚 Course Inventory ${if (!isAdmin) "(Level $repLevel only)" else ""}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        val displayCourses = if (isAdmin) allCourses
                             else allCourses.filter { it.level == repLevel }

        displayCourses.forEach { course ->
            CourseManageRow(
                course = course,
                canDelete = isAdmin || course.level == repLevel,
                onPreview = { onPreview(course) }
            )
            Spacer(modifier = Modifier.height(6.dp))
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
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val aiPreview = proposal.aiPreview

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

            Spacer(modifier = Modifier.height(8.dp))

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
                AiPreviewSection(aiPreview = aiPreview)

                Spacer(modifier = Modifier.height(10.dp))

                // ── Action buttons ──
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onApprove,
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
                        text = "⚠️ Some files couldn't be matched. Approve only the matched ones above, or reject the proposal.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFBBF24)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  AI PREVIEW SECTION
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AiPreviewSection(aiPreview: AiPreview) {
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
            Text(
                text = "$statusIcon ${aiPreview.matchedItems.size} resources found from ${aiPreview.totalFilesScanned} files",
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

            // Unmatched files
            if (aiPreview.unmatchedFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚠️ Unmatched (${aiPreview.unmatchedFiles.size}):",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFBBF24)
                )
                aiPreview.unmatchedFiles.forEach { uf ->
                    Text(
                        text = "   ${uf.fileName} — ${uf.reason}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFF87171),
                        maxLines = 1
                    )
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

    if (result.files.isEmpty()) {
        ProposalRepository.updateAiPreview(
            proposal.id,
            AiPreview(
                scanStatus = "failed",
                totalFilesScanned = 0,
                sourceFolderName = result.folderName,
                errorMessage = "No files found in the link."
            )
        )
        return
    }

    // Parse each file
    val matchedItems = mutableListOf<AiMatchedItem>()
    val unmatchedFiles = mutableListOf<com.nimelssa.vault.data.AiUnmatchedFile>()

    for (file in result.files) {
        val parseResult = FilenameParser.parse(file.name)

        if (parseResult.courseCode != null && parseResult.confidence != FilenameParser.Confidence.NONE) {
            val resourceLabel = when (parseResult.resourceType) {
                "LN" -> "Lecture Notes"
                "PQ" -> "Past Questions"
                "TB" -> "Textbook"
                else -> "Other"
            }

            // Look up the course in the repository for its full name/level/semester
            val existingCourse = CourseRepository.findCourse(parseResult.courseCode)
            val courseName = existingCourse?.name ?: parseResult.courseCode
            val level = existingCourse?.level ?: inferLevelFromCode(parseResult.courseCode)
            val semester = parseResult.semester ?: existingCourse?.semester ?: 1

            matchedItems.add(
                AiMatchedItem(
                    courseCode = parseResult.courseCode,
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
                    reason = parseResult.reason,
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
 * Approve a proposal: write all matched resources to Firestore and clean up.
 */
private suspend fun approveProposal(proposal: Proposal, reviewerEmail: String) {
    val preview = proposal.aiPreview ?: return

    // For each matched item, save the master folder link to course_resources
    val masterFolderUrl = proposal.driveLink

    // Group matched items by course code to combine LN and PQ into one save
    val groupedByCode = preview.matchedItems.groupBy { it.courseCode }

    for ((courseCode, items) in groupedByCode) {
        val lectureNotesUrl = if (items.any { it.resourceType == "LN" }) masterFolderUrl else ""
        val pastQuestionsUrl = if (items.any { it.resourceType == "PQ" }) masterFolderUrl else ""

        // Also check if existing course has resources we should keep
        val existing = CourseRepository.findCourse(courseCode)
        val finalLnu = lectureNotesUrl.ifBlank { existing?.lectureNotesUrl ?: "" }
        val finalPqu = pastQuestionsUrl.ifBlank { existing?.pastQuestionsUrl ?: "" }

        // Save combined notes from all items and original proposal
        val notesSummary = items.joinToString("; ") {
            "[${it.resourceLabel}] from ${it.fileName}"
        }
        val finalNotes = listOfNotNull(
            proposal.notes.takeIf { it.isNotBlank() },
            notesSummary.takeIf { it.isNotBlank() }
        ).joinToString(" | ")

        // Update CourseRepository in memory
        if (existing != null) {
            CourseRepository.mergeCourseResources(
                code = courseCode,
                lectureNotesUrl = finalLnu,
                pastQuestionsUrl = finalPqu,
                submittedBy = proposal.submittedBy,
                notes = finalNotes
            )
            // Remove pending flag if it exists
            CourseRepository.approveCourse(courseCode)
        } else {
            // Create new course entry
            val firstItem = items.first()
            CourseRepository.addCourse(
                Course(
                    code = courseCode,
                    name = firstItem.courseName,
                    category = inferCategory(courseCode),
                    level = firstItem.level,
                    semester = firstItem.semester,
                    progress = 0,
                    isPending = false,
                    lectureNotesUrl = finalLnu,
                    pastQuestionsUrl = finalPqu,
                    submittedBy = proposal.submittedBy,
                    notes = finalNotes
                )
            )
        }

        // Save to Firestore
        FirestoreCourseSync.saveResources(
            code = courseCode,
            lectureNotesUrl = finalLnu,
            pastQuestionsUrl = finalPqu,
            submittedBy = proposal.submittedBy,
            notes = finalNotes
        )
    }

    // Mark the proposal as approved
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

/** Check if a proposal's AI results match a rep's level */
private fun matchesRepLevel(proposal: Proposal, repLevel: String): Boolean {
    val preview = proposal.aiPreview ?: return false
    return preview.matchedItems.any { it.level == repLevel }
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
    onPreview: () -> Unit
) {
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
                // Show resource indicators
                if (course.lectureNotesUrl.isNotBlank() || course.pastQuestionsUrl.isNotBlank()) {
                    Text(
                        text = buildString {
                            if (course.lectureNotesUrl.isNotBlank()) append("📖 LN")
                            if (course.lectureNotesUrl.isNotBlank() && course.pastQuestionsUrl.isNotBlank()) append(" | ")
                            if (course.pastQuestionsUrl.isNotBlank()) append("📝 PQ")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row {
                if (course.hasResources) {
                    Button(
                        onClick = onPreview,
                        modifier = Modifier.padding(end = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1)
                        ),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) { Text("👁️", style = MaterialTheme.typography.labelSmall) }
                }
                if (canDelete) {
                    Text(
                        text = "🗑️",
                        modifier = Modifier.clickable {
                            scope.launch {
                                FirestoreCourseSync.removeResources(course.code)
                            }
                            CourseRepository.removeCourse(course.code)
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
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
