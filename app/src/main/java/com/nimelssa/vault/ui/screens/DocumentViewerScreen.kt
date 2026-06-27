package com.nimelssa.vault.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nimelssa.vault.data.Course
import com.nimelssa.vault.data.CourseRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    course: Course?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (course == null) return

    var currentPage by remember { mutableIntStateOf(1) }
    val totalPages = 12

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = {
                Text(
                    text = "${course.code} - ${course.name}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )
            },
            navigationIcon = {
                TextButton(onClick = onClose) {
                    Text("Close", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            actions = {
                TextButton(onClick = {
                    CourseRepository.toggleOffline(course.code)
                }) {
                    Text(
                        text = if (course.isOffline) "✓ Offline" else "Save Offline",
                        color = if (course.isOffline) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        // Content area
        Box(
            modifier = Modifier
                .weight(1f)
                .background(Color(0xFFE2E8F0))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                // Meta strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = course.code,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Page $currentPage of $totalPages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sample document content (same as MLS.html)
                Text(
                    text = "CHAPTER 1: INTRODUCTION TO MEDICAL LABORATORY SCIENCE",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "1.1 Definition and Scope",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Medical Laboratory Science (MLS) is the health profession concerned with the " +
                            "performance of laboratory analyses that aid in the diagnosis, treatment, and " +
                            "monitoring of patients. It encompasses disciplines including Hematology, " +
                            "Clinical Chemistry, Microbiology, Immunology, Blood Banking, and Histopathology.",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Key point box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "KEY POINT: The MLSCN governs practice standards and professional " +
                                "registration for all MLS practitioners in Nigeria.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "— End of page $currentPage — Scroll for more —",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Navigation buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { if (currentPage > 1) currentPage-- },
                modifier = Modifier.weight(1f),
                enabled = currentPage > 1,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("← Previous")
            }
            Button(
                onClick = { if (currentPage < totalPages) currentPage++ },
                modifier = Modifier.weight(1f),
                enabled = currentPage < totalPages,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Next Page →")
            }
        }
    }
}
