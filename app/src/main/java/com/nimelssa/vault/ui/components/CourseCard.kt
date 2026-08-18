package com.nimelssa.vault.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nimelssa.vault.data.Course
import com.nimelssa.vault.data.Resource

@Composable
fun CourseCard(
    course: Course,
    resources: List<Resource> = emptyList(),
    onClick: () -> Unit = {},
    onStudyNotes: () -> Unit = {},
    onPastQuestions: () -> Unit = {},
    onTextbook: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hasNotes = resources.any { it.resourceType == "LN" }
    val hasPqs = resources.any { it.resourceType == "PQ" }
    val hasTb = resources.any { it.resourceType == "TB" }
    val hasAny = hasNotes || hasPqs || hasTb

    // Honest status label: "Link Mode" only when master links exist; if files
    // are present (Drive fileId) but no master link, say how many are linked —
    // never "No resources yet" on a populated card.
    val linkMode = resources.any { it.masterUrl.isNotBlank() }
    val statusLabel = when {
        linkMode -> "🌐 Link Mode"
        hasAny -> "🔗 ${resources.size} file${if (resources.size == 1) "" else "s"} linked"
        else -> "📄 No resources yet"
    }

    // ── Compute honest progress from available resource types ──
    // For a regular course: % of the 3 resource types (LN/PQ/TB) available.
    // For level textbooks (special card): 100% if any exist, else 0%.
    val displayProgress = if (!hasAny) 0
        else if (course.code == "TEXTBOOK") 100
        else {
            val presentTypes = listOf(hasNotes, hasPqs, hasTb).count { it }
            (presentTypes * 100) / 3
        }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = course.code,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = course.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RadialProgress(percentage = displayProgress)
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (hasAny) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasNotes) {
                        OutlinedButton(
                            onClick = onStudyNotes,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("📖 Study Notes", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (hasPqs) {
                        OutlinedButton(
                            onClick = onPastQuestions,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("📝 Past Questions", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (hasTb) {
                        OutlinedButton(
                            onClick = onTextbook,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("📚 Textbook", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            } else {
                Text(
                    text = "📄 No resources yet — propose it to your class rep",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
