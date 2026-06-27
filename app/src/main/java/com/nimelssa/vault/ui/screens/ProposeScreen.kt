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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nimelssa.vault.data.Course
import com.nimelssa.vault.data.CourseRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposeScreen(
    onProposed: (Course) -> Unit,
    modifier: Modifier = Modifier
) {
    var courseCode by remember { mutableStateOf("") }
    var selectedSemester by remember { mutableStateOf(1) }
    var resourceType by remember { mutableStateOf("Lecture Notes") }
    var notes by remember { mutableStateOf("") }
    var uploadMode by remember { mutableStateOf("link") }

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

        Spacer(modifier = Modifier.height(16.dp))

        // Upload mode selector
        val uploadShape = SegmentedButtonDefaults.itemShape(
            index = 0, count = 2
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = uploadMode == "link",
                onClick = { uploadMode = "link" },
                shape = uploadShape,
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) { Text("Drive Folder Link") }
            SegmentedButton(
                selected = uploadMode == "file",
                onClick = { uploadMode = "file" },
                shape = uploadShape,
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) { Text("Local Document") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Course code & semester
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = courseCode,
                onValueChange = { courseCode = it.uppercase() },
                label = { Text("Target Course Code") },
                placeholder = { Text("e.g., BIO 202") },
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

        // URL / File
        if (uploadMode == "link") {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text("Google Drive Folder URL") },
                placeholder = { Text("https://drive.google.com/drive/folders/...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
        } else {
            Button(
                onClick = { /* File picker would go here */ },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("📄 Tap to load PDF or Image Scan copy")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Notes
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Useful Context / Contributor Notes") },
            placeholder = { Text("e.g., Contains missing 2024 questions...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Submit
        Button(
            onClick = {
                val firstDigit = courseCode.firstOrNull { it.isDigit() }
                val level = if (firstDigit != null) "${firstDigit}00" else "200"
                val category = when {
                    courseCode.contains("BIO") -> "BIOLOGY"
                    courseCode.contains("CHM") -> "CHEMISTRY"
                    else -> "MEDICAL LABORATORY SCIENCE"
                }
                val newCourse = Course(
                    code = courseCode.ifEmpty { "UNCODED" },
                    name = "$courseCode - Reference Material",
                    category = category,
                    level = level,
                    semester = selectedSemester,
                    progress = 0
                )
                CourseRepository.addCourse(newCourse)
                onProposed(newCourse)
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
