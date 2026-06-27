package com.nimelssa.vault.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    val context = LocalContext.current

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = {
                Text(
                    text = "${course.code}",
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Course info
            Text(
                text = course.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${course.displayLevel} • ${course.displaySemester} • ${course.category}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Submitted by
            if (course.submittedBy.isNotBlank()) {
                Text(
                    text = "Submitted by: ${course.submittedBy}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Materials section
            Text(
                text = "📚 Available Resources",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Lecture Notes
            if (course.lectureNotesUrl.isNotBlank()) {
                ResourceCard(
                    title = "📖 Study Notes / Lecture Slides",
                    url = course.lectureNotesUrl,
                    notes = if (resourceTypeMatches(course.notes, "Lecture")) course.notes else "",
                    onOpen = { openUrl(course.lectureNotesUrl) }
                )
            }

            // Past Questions
            if (course.pastQuestionsUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                ResourceCard(
                    title = "📝 Past Questions & Test Papers",
                    url = course.pastQuestionsUrl,
                    notes = if (resourceTypeMatches(course.notes, "Past")) course.notes else "",
                    onOpen = { openUrl(course.pastQuestionsUrl) }
                )
            }

            // No resources yet
            if (course.lectureNotesUrl.isBlank() && course.pastQuestionsUrl.isBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF9C3))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📭",
                            fontSize = MaterialTheme.typography.headlineLarge.fontSize
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No materials uploaded yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Use the Propose tab to submit lecture notes or past questions for this course.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Contributor notes
            if (course.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "📌 Contributor Notes",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = course.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ResourceCard(
    title: String,
    url: String,
    notes: String,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2
            )
            if (notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Open Resource ↗", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Simple check if the notes relate to this resource type */
private fun resourceTypeMatches(notes: String, type: String): Boolean {
    if (notes.isBlank()) return false
    val lower = notes.lowercase()
    return when (type) {
        "Lecture" -> lower.contains("lecture") || lower.contains("note") || lower.contains("slide")
        "Past" -> lower.contains("past") || lower.contains("question") || lower.contains("exam")
        else -> true
    }
}
