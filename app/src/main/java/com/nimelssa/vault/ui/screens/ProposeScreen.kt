package com.nimelssa.vault.ui.screens

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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nimelssa.vault.data.Course
import com.nimelssa.vault.data.CourseRepository
import com.nimelssa.vault.data.UserSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposeScreen(
    onProposed: (Course) -> Unit,
    modifier: Modifier = Modifier
) {
    val user by UserSession.state.collectAsState()
    var courseCode by remember { mutableStateOf("") }
    var selectedSemester by remember { mutableStateOf(1) }
    var resourceType by remember { mutableStateOf("Lecture Notes") }
    var notes by remember { mutableStateOf("") }
    var driveLink by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    /** Determine category from course code prefix */
    fun inferCategory(code: String): String = when {
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

    /** Determine level from course code (first digit) */
    fun inferLevel(code: String): String {
        val firstDigit = code.firstOrNull { it.isDigit() }
        return if (firstDigit != null) "${firstDigit}00" else "200"
    }

    /** Build course name from code and resource type */
    fun buildCourseName(code: String): String = when {
        resourceType == "Lecture Notes" -> "$code - Lecture Notes"
        resourceType == "Past Questions" -> "$code - Past Questions"
        else -> "$code - Reference Material"
    }

    /** Submit the proposal */
    fun submitProposal(resourceUrl: String) {
        val code = courseCode.uppercase().trim()
        if (code.isBlank()) return

        val newCourse = Course(
            code = code,
            name = buildCourseName(code),
            category = inferCategory(code),
            level = inferLevel(code),
            semester = selectedSemester,
            progress = 0,
            isPending = true,
            lectureNotesUrl = if (resourceType == "Lecture Notes") resourceUrl else "",
            pastQuestionsUrl = if (resourceType == "Past Questions") resourceUrl else "",
            submittedBy = user.email,
            notes = notes
        )

        CourseRepository.addCourse(newCourse)
        onProposed(newCourse)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "📤 Propose Academic Resource",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Submit a resource link and match it to a course. It goes to Rep/Admin for approval.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Course code & semester
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = courseCode,
                onValueChange = { courseCode = it.uppercase() },
                label = { Text("Target Course Code") },
                placeholder = { Text("e.g., MLS 301") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            var semExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = semExpanded,
                onExpandedChange = { semExpanded = it }
            ) {
                OutlinedTextField(
                    value = if (selectedSemester == 1) "1st Sem" else "2nd Sem",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .weight(1f)
                        .menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = semExpanded) },
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = semExpanded,
                    onDismissRequest = { semExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("1st Semester") },
                        onClick = { selectedSemester = 1; semExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("2nd Semester") },
                        onClick = { selectedSemester = 2; semExpanded = false }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Resource type
        var typeExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = typeExpanded,
            onExpandedChange = { typeExpanded = it }
        ) {
            OutlinedTextField(
                value = resourceType,
                onValueChange = {},
                readOnly = true,
                label = { Text("Resource Classification Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                shape = RoundedCornerShape(8.dp)
            )
            ExposedDropdownMenu(
                expanded = typeExpanded,
                onDismissRequest = { typeExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Official Lecture Notes / Slides") },
                    onClick = { resourceType = "Lecture Notes"; typeExpanded = false }
                )
                DropdownMenuItem(
                    text = { Text("Past Examination / Test Questions (PQs)") },
                    onClick = { resourceType = "Past Questions"; typeExpanded = false }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Resource URL — the only input mode now
        OutlinedTextField(
            value = driveLink,
            onValueChange = { driveLink = it },
            label = { Text("Google Drive / Direct Resource URL") },
            placeholder = { Text("https://drive.google.com/... or https://...pdf") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Notes
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Useful Context / Contributor Notes") },
            placeholder = { Text("e.g., Contains 2023/2024 session past questions...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Message feedback
        if (message != null) {
            Text(
                text = message!!,
                style = MaterialTheme.typography.bodySmall,
                color = if (message!!.startsWith("✅")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Submit button
        Button(
            onClick = {
                val code = courseCode.uppercase().trim()
                if (code.isBlank()) {
                    message = "❌ Please enter a course code."
                    return@Button
                }
                if (driveLink.isBlank()) {
                    message = "❌ Please enter a resource URL."
                    return@Button
                }
                message = null
                submitProposal(driveLink.trim())
                message = "✅ Proposal submitted for review!"
                // Clear form
                courseCode = ""
                driveLink = ""
                notes = ""
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Submit to Verification Staging", fontWeight = FontWeight.Bold)
        }
    }
}
