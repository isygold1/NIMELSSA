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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nimelssa.vault.data.Report
import com.nimelssa.vault.data.ReportRepository
import com.nimelssa.vault.data.UserSession
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val user by UserSession.state.collectAsState()
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf("app_bug") }
    var catExpanded by remember { mutableStateOf(false) }
    var courseCode by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val categories = listOf(
        "app_bug" to "🐛 App Bug (crashes, glitches, feature requests)",
        "course_issue" to "📚 Course Issue (wrong material, missing content)",
        "other" to "📋 Other / General Complaint"
    )

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Report Issue / Complaint", style = MaterialTheme.typography.titleSmall) },
            navigationIcon = {
                TextButton(onClick = onClose) {
                    Text("Close", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Help us improve NIMELSSA Vault!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select a category and describe the issue. App bugs go to Admin, course issues go to Rep.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category dropdown
            ExposedDropdownMenuBox(
                expanded = catExpanded,
                onExpandedChange = { catExpanded = it }
            ) {
                OutlinedTextField(
                    value = categories.find { it.first == category }?.second ?: category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Issue Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(8.dp)
                )
                ExposedDropdownMenu(
                    expanded = catExpanded,
                    onDismissRequest = { catExpanded = false }
                ) {
                    categories.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { category = key; catExpanded = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Course code (only for course issues)
            if (category == "course_issue") {
                OutlinedTextField(
                    value = courseCode,
                    onValueChange = { courseCode = it.uppercase() },
                    label = { Text("Affected Course Code") },
                    placeholder = { Text("e.g., MLS 301") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Message
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Describe the issue / complaint") },
                placeholder = { Text("Please provide as much detail as possible...") },
                modifier = Modifier.fillMaxWidth().height(200.dp),
                minLines = 5,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Status message
            if (statusMsg != null) {
                Text(
                    text = statusMsg!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (statusMsg!!.startsWith("✅")) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Submit button
            Button(
                onClick = {
                    if (message.isBlank()) {
                        statusMsg = "❌ Please describe the issue."
                        return@Button
                    }
                    isSending = true
                    statusMsg = null
                    scope.launch {
                        try {
                            ReportRepository.submitReport(
                                Report(
                                    userId = user.uid,
                                    userEmail = user.email,
                                    category = category,
                                    courseCode = courseCode,
                                    message = message
                                )
                            )
                            statusMsg = "✅ Report submitted! Thank you for your feedback."
                            // Clear form
                            message = ""
                            courseCode = ""
                        } catch (e: Exception) {
                            statusMsg = "❌ Failed to submit: ${e.message}"
                        } finally {
                            isSending = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isSending,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    if (isSending) "Submitting..." else "Submit Report",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
