package com.nimelssa.vault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nimelssa.vault.data.ProposalRepository
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
    var message by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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

                message = null
                isLoading = true

                scope.launch {
                    try {
                        ProposalRepository.submit(
                            driveLink = link,
                            notes = notes.trim(),
                            submittedBy = user.email,
                            submittedByName = user.name
                        )
                        message = "✅ Proposal submitted! AI will scan and notify Rep/Admin for review."
                        driveLink = ""
                        notes = ""
                        onProposed()
                    } catch (e: Exception) {
                        message = "❌ Failed to submit: ${e.localizedMessage}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                if (isLoading) "Submitting..." else "Submit for AI Review",
                fontWeight = FontWeight.Bold
            )
        }
    }
}
