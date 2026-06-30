package com.nimelssa.vault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.util.Log
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nimelssa.vault.data.DriveScanner
import com.nimelssa.vault.data.Levels
import com.nimelssa.vault.data.ProposalRepository
import com.nimelssa.vault.data.ResourceRepository
import com.nimelssa.vault.data.UserSession
import kotlinx.coroutines.launch

/**
 * Simplified proposal form — students just paste a Google Drive link.
 * The AI handles everything: scanning, course identification, type inference.
 */
@Composable
fun ProposeScreen(
    onProposed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val user by UserSession.state.collectAsState()
    var driveLink by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    // Auto-fill level from user profile
    val defaultLevel = when {
        user.role == com.nimelssa.vault.data.UserRole.REP -> user.repLevel
        else -> user.level
    }
    var targetLevel by remember { mutableStateOf(defaultLevel) }
    var levelExpanded by remember { mutableStateOf(false) }
    // Auto-detect semester from current month (Unilorin calendar)
    // 1st sem: Oct–Feb (MONTH >= 9 || MONTH <= 1), 2nd sem: Mar–Jul (MONTH in 2..6)
    var selectedSemester by remember { mutableIntStateOf(
        with (java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)) {
            if (this >= 9 || this <= 1) 1 else 2
        }
    ) }
    var message by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var duplicateWarning by remember { mutableStateOf<String?>(null) }
    var pendingLink by remember { mutableStateOf("") }   // Drive link being checked for duplicates
    var isSubmitting by remember { mutableStateOf(false) } // guard against double-tap
    val scope = rememberCoroutineScope()

    // Shared submit logic (lambda, not local fun — valid Kotlin)
    val submitProposal: suspend (String) -> Unit = { link ->
        try {
            ProposalRepository.submit(
                driveLink = link,
                notes = notes.trim(),
                submittedBy = user.email,
                submittedByName = user.name,
                targetLevel = targetLevel,
                semester = selectedSemester
            )
            message = "✅ Proposal submitted! The ${targetLevel}L rep will review it."
            driveLink = ""
            notes = ""
            targetLevel = ""
            selectedSemester = 1
            onProposed()
        } catch (e: Exception) {
            message = "❌ Failed to submit: ${e.localizedMessage}"
        } finally {
            isLoading = false
            isSubmitting = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "📤 Propose Resource",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Submit a Google Drive link (file or folder). Our AI scans it, identifies the courses, and sends to your Rep/Admin for approval.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── File naming tip card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "💡 For best results, name your files:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF92400E)
                )
                Text(
                    text = "CourseCode_Type.ext  (e.g., CSC101_LN.pdf, MLS301_PQ.pdf)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF92400E)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Type: LN=Lecture Notes, PQ=Past Questions, TB=Textbook",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF92400E)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Drive link input ──
        OutlinedTextField(
            value = driveLink,
            onValueChange = { driveLink = it },
            label = { Text("Google Drive Link") },
            placeholder = { Text("https://drive.google.com/drive/folders/...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Notes ──
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            placeholder = { Text("Any context for the reviewer...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Target Level selector ──
        @OptIn(ExperimentalMaterial3Api::class)
        ExposedDropdownMenuBox(
            expanded = levelExpanded,
            onExpandedChange = { levelExpanded = it }
        ) {
            OutlinedTextField(
                value = if (targetLevel.isNotBlank()) "${targetLevel} Level" else "Select target level",
                onValueChange = {},
                readOnly = true,
                label = { Text("This resource is for...") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            ExposedDropdownMenu(
                expanded = levelExpanded,
                onDismissRequest = { levelExpanded = false }
            ) {
                Levels.ALL.forEach { level ->
                    DropdownMenuItem(
                        text = { Text("${level} Level") },
                        onClick = {
                            targetLevel = level
                            levelExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Semester selector (auto-filled from current month) ──
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
        Text(
            text = "📅 Auto-filled from current month (Unilorin: Oct–Feb = 1st, Mar–Jul = 2nd)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Success notification ──
        if (message != null && message!!.startsWith("✅")) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7))
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = message!!,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF166534)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── Error message ──
        if (message != null && !message!!.startsWith("✅")) {
            Text(
                text = message!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Duplicate warning ──
        if (duplicateWarning != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF3CD)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "⚠️ Possible Duplicate",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF856404)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = duplicateWarning!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF856404)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { duplicateWarning = null },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6C757D)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("Cancel", color = Color.White) }
                        Button(
                            onClick = {
                                val link = pendingLink
                                duplicateWarning = null
                                pendingLink = ""
                                // Submit anyway after user confirms
                                scope.launch { submitProposal(link) }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC3545)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("Submit Anyway", color = Color.White) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Submit button ──
        Button(
            onClick = {
                val link = driveLink.trim()
                if (link.isBlank()) {
                    message = "❌ Please paste a Google Drive link."
                    return@Button
                }
                if (!link.contains("drive.google.com") && !link.contains("docs.google.com")) {
                    message = "❌ Please enter a valid Google Drive or Google Docs link."
                    return@Button
                }

                if (targetLevel.isBlank()) {
                    message = "❌ Please select which level this resource is for."
                    return@Button
                }

                if (isSubmitting) return@Button // guard against double-tap
                isSubmitting = true
                message = null
                isLoading = true

                scope.launch {
                    // ── Duplicate check ──
                    pendingLink = link
                    val fileId = DriveScanner.extractFileId(link)
                    var md5 = ""
                    if (fileId != null) {
                        md5 = DriveScanner.getFileMd5(fileId) ?: ""
                    }

                    // Check 1: Same fileId but different MD5 → updated version
                    if (fileId != null) {
                        val existingByFileId = ResourceRepository.findByFileId(fileId)
                        if (existingByFileId != null && md5.isNotBlank() &&
                            existingByFileId.md5Checksum.isNotBlank() &&
                            existingByFileId.md5Checksum != md5
                        ) {
                            val course = if (existingByFileId.courseCode.isNotBlank())
                                " in ${existingByFileId.courseCode}" else ""
                            Log.d("ProposeScreen", "Updated version detected for fileId=$fileId")
                            duplicateWarning = "🔄 Updated version of existing ${existingByFileId.resourceLabel}" +
                                    "$course detected.\n" +
                                    "The file on Google Drive has changed since it was last approved.\n\n" +
                                    "Submit to flag this update for the rep's review?"
                            isLoading = false
                            isSubmitting = false
                            return@launch
                        }
                    }

                    // Check 2: Same MD5 → exact duplicate
                    if (md5.isNotBlank()) {
                        val matches = ResourceRepository.allResources
                            .filter { it.md5Checksum == md5 && it.md5Checksum.isNotBlank() }

                        if (matches.isNotEmpty()) {
                            val matchInfo = matches.joinToString("\n") { r ->
                                val c = if (r.courseCode.isNotBlank()) " in ${r.courseCode}" else ""
                                "📄 ${r.resourceLabel}$c"
                            }
                            Log.d("ProposeScreen", "Exact duplicate found for md5=$md5")
                            duplicateWarning = "⚠️ This file is identical (MD5 match) to ${
                                matches.size
                            } existing resource(s):\n$matchInfo\n\nSubmit anyway if this is an update?"
                            isLoading = false
                            isSubmitting = false
                            return@launch
                        }
                    }

                    pendingLink = ""
                    submitProposal(link)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp),
            enabled = !isLoading && duplicateWarning == null,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                if (isLoading) "Scanning for duplicates..." else "Submit for AI Review",
                fontWeight = FontWeight.Bold
            )
        }
    }}
