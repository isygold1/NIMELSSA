package com.nimelssa.vault.ui.screens

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nimelssa.vault.data.DriveScanner
import com.nimelssa.vault.data.Levels
import com.nimelssa.vault.data.ProposalRepository
import com.nimelssa.vault.data.ResourceRepository
import com.nimelssa.vault.data.UserSession
import com.nimelssa.vault.ui.components.AppScreenHeader
import com.nimelssa.vault.ui.theme.OrientationManager
import com.nimelssa.vault.ui.theme.OrientationMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Simplified proposal form -- students just paste a Google Drive link.
 * The AI handles everything: scanning, course identification, type inference.
 */
@Composable
fun ProposeScreen(
    onProposed: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val user by UserSession.state.collectAsState()
    var driveLink by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val defaultLevel = when {
        user.role == com.nimelssa.vault.data.UserRole.REP -> user.repLevel
        else -> user.level
    }
    var targetLevel by remember { mutableStateOf(defaultLevel) }
    var levelExpanded by remember { mutableStateOf(false) }
    var selectedSemester by remember { mutableIntStateOf(
        with (java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)) {
            if (this >= 9 || this <= 1) 1 else 2
        }
    ) }
    var message by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var duplicateWarning by remember { mutableStateOf<String?>(null) }
    var pendingLink by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val orientationMode by OrientationManager.mode.collectAsState()
    val isLandscape = when (orientationMode) {
        OrientationMode.PORTRAIT -> false
        OrientationMode.LANDSCAPE -> true
        OrientationMode.AUTO -> configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

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
            message = "\u2705 Proposal submitted! The ${targetLevel}L rep will review it."
            driveLink = ""
            notes = ""
            targetLevel = ""
            selectedSemester = 1
            delay(2500)
            onProposed()
        } catch (e: Exception) {
            message = "\u274C Failed to submit: ${e.localizedMessage}"
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
        AppScreenHeader(
            title = "Propose Resource",
            onOpenDrawer = onOpenDrawer
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Submit a Google Drive link (file or folder). Our AI scans it, identifies the courses, and sends to your Rep/Admin for approval.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isLandscape) {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "\uD83D\uDCA1 For best results, name your files:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "CourseCode_Type.ext  (e.g., CSC101_LN.pdf, MLS301_PQ.pdf)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Type: LN=Lecture Notes, PQ=Past Questions, TB=Textbook",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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
                        text = "\uD83D\uDCC5 Auto-filled from current month (Unilorin: Oct\u2013Feb = 1st, Mar\u2013Jul = 2nd)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                Column(Modifier.weight(1f)) {
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

                    Button(
                        onClick = {
                            val link = driveLink.trim()
                            if (link.isBlank()) {
                                message = "\u274C Please paste a Google Drive link."
                                return@Button
                            }
                            if (!link.contains("drive.google.com") && !link.contains("docs.google.com")) {
                                message = "\u274C Please enter a valid Google Drive or Google Docs link."
                                return@Button
                            }
                            if (targetLevel.isBlank()) {
                                message = "\u274C Please select which level this resource is for."
                                return@Button
                            }
                            if (isSubmitting) return@Button
                            isSubmitting = true
                            message = null
                            isLoading = true
                            scope.launch {
                                pendingLink = link
                                val fileId = DriveScanner.extractFileId(link)
                                var md5 = ""
                                if (fileId != null) {
                                    md5 = DriveScanner.getFileMd5(fileId) ?: ""
                                }
                                if (fileId != null) {
                                    val existingByFileId = ResourceRepository.findByFileId(fileId)
                                    if (existingByFileId != null && md5.isNotBlank() &&
                                        existingByFileId.md5Checksum.isNotBlank() &&
                                        existingByFileId.md5Checksum != md5
                                    ) {
                                        val course = if (existingByFileId.courseCode.isNotBlank())
                                            " in ${existingByFileId.courseCode}" else ""
                                        Log.d("ProposeScreen", "Updated version detected for fileId=$fileId")
                                        duplicateWarning = "\uD83D\uDD04 Updated version of existing ${existingByFileId.resourceLabel}" +
                                                "$course detected.\n" +
                                                "The file on Google Drive has changed since it was last approved.\n\n" +
                                                "Submit to flag this update for the rep\u2019s review?"
                                        isLoading = false
                                        isSubmitting = false
                                        return@launch
                                    }
                                }
                                if (md5.isNotBlank()) {
                                    val matches = ResourceRepository.allResources
                                        .filter { it.md5Checksum == md5 && it.md5Checksum.isNotBlank() }
                                    if (matches.isNotEmpty()) {
                                        val matchInfo = matches.joinToString("\n") { r ->
                                            val c = if (r.courseCode.isNotBlank()) " in ${r.courseCode}" else ""
                                            "\uD83D\uDCC4 ${r.resourceLabel}$c"
                                        }
                                        Log.d("ProposeScreen", "Exact duplicate found for md5=$md5")
                                        duplicateWarning = "\u26A0\uFE0F This file is identical (MD5 match) to ${
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
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (message != null && message!!.startsWith("\u2705")) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = message!!,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (message != null && !message!!.startsWith("\u2705")) {
                Text(
                    text = message!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (duplicateWarning != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "\u26A0\uFE0F Possible Duplicate",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = duplicateWarning!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { duplicateWarning = null },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Cancel") }
                            Button(
                                onClick = {
                                    val link = pendingLink
                                    duplicateWarning = null
                                    pendingLink = ""
                                    scope.launch { submitProposal(link) }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Submit Anyway", color = MaterialTheme.colorScheme.onError) }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "\uD83D\uDCA1 For best results, name your files:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "CourseCode_Type.ext  (e.g., CSC101_LN.pdf, MLS301_PQ.pdf)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Type: LN=Lecture Notes, PQ=Past Questions, TB=Textbook",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                text = "\uD83D\uDCC5 Auto-filled from current month (Unilorin: Oct\u2013Feb = 1st, Mar\u2013Jul = 2nd)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (message != null && message!!.startsWith("\u2705")) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = message!!,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (message != null && !message!!.startsWith("\u2705")) {
                Text(
                    text = message!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (duplicateWarning != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "\u26A0\uFE0F Possible Duplicate",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = duplicateWarning!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { duplicateWarning = null },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Cancel") }
                            Button(
                                onClick = {
                                    val link = pendingLink
                                    duplicateWarning = null
                                    pendingLink = ""
                                    scope.launch { submitProposal(link) }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Submit Anyway", color = MaterialTheme.colorScheme.onError) }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    val link = driveLink.trim()
                    if (link.isBlank()) {
                        message = "\u274C Please paste a Google Drive link."
                        return@Button
                    }
                    if (!link.contains("drive.google.com") && !link.contains("docs.google.com")) {
                        message = "\u274C Please enter a valid Google Drive or Google Docs link."
                        return@Button
                    }
                    if (targetLevel.isBlank()) {
                        message = "\u274C Please select which level this resource is for."
                        return@Button
                    }
                    if (isSubmitting) return@Button
                    isSubmitting = true
                    message = null
                    isLoading = true
                    scope.launch {
                        pendingLink = link
                        val fileId = DriveScanner.extractFileId(link)
                        var md5 = ""
                        if (fileId != null) {
                            md5 = DriveScanner.getFileMd5(fileId) ?: ""
                        }
                        if (fileId != null) {
                            val existingByFileId = ResourceRepository.findByFileId(fileId)
                            if (existingByFileId != null && md5.isNotBlank() &&
                                existingByFileId.md5Checksum.isNotBlank() &&
                                existingByFileId.md5Checksum != md5
                            ) {
                                val course = if (existingByFileId.courseCode.isNotBlank())
                                    " in ${existingByFileId.courseCode}" else ""
                                Log.d("ProposeScreen", "Updated version detected for fileId=$fileId")
                                duplicateWarning = "\uD83D\uDD04 Updated version of existing ${existingByFileId.resourceLabel}" +
                                        "$course detected.\n" +
                                        "The file on Google Drive has changed since it was last approved.\n\n" +
                                        "Submit to flag this update for the rep\u2019s review?"
                                isLoading = false
                                isSubmitting = false
                                return@launch
                            }
                        }
                        if (md5.isNotBlank()) {
                            val matches = ResourceRepository.allResources
                                .filter { it.md5Checksum == md5 && it.md5Checksum.isNotBlank() }
                            if (matches.isNotEmpty()) {
                                val matchInfo = matches.joinToString("\n") { r ->
                                    val c = if (r.courseCode.isNotBlank()) " in ${r.courseCode}" else ""
                                    "\uD83D\uDCC4 ${r.resourceLabel}$c"
                                }
                                Log.d("ProposeScreen", "Exact duplicate found for md5=$md5")
                                duplicateWarning = "\u26A0\uFE0F This file is identical (MD5 match) to ${
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
        }
    }
}
