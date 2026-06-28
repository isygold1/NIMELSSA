package com.nimelssa.vault.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nimelssa.vault.data.Proposal
import com.nimelssa.vault.data.ProposalRepository
import com.nimelssa.vault.data.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

// ── AI classification state ───────────────────────────────────────────────────

private data class AiClassification(
    val courseCode: String,
    val courseName: String,
    val category: String,
    val level: String,
    val semester: Int,
    val type: String
)

/**
 * Calls the Anthropic Claude API to classify a Drive link.
 * Claude reads the URL pattern and any visible filename to infer course metadata.
 *
 * PRODUCTION NOTE: Move this call to a Firebase Cloud Function to keep the
 * API key off the client. For now it runs client-side for prototype purposes.
 */
private suspend fun classifyDriveLink(url: String): AiClassification? = withContext(Dispatchers.IO) {
    try {
        // WHY: We send the URL itself. Claude reads naming conventions like
        // "MLS301_LN.pdf" or "BIO221_PQ" to infer all metadata fields.
        // Students are prompted to name files: CourseCode_Type (e.g. MLS301_LN.pdf)
        val prompt = """
            A student submitted this Google Drive link to a university academic resource app:
            $url
            
            Based on the URL, file/folder name, and standard Nigerian university course code patterns
            (e.g. MLS = Medical Laboratory Science, BIO = Biology, CHM = Chemistry, PHY = Physics,
            BCH = Biochemistry, GST = General Studies, STA = Statistics),
            classify this resource.
            
            Rules:
            - Level is determined by the first digit of the course number (1=100L, 2=200L, 3=300L, 4=400L, 5=500L)
            - LN or "lecture notes" or "slides" = Lecture Notes type
            - PQ or "past questions" or "past exam" = Past Questions type
            - If you cannot determine a field confidently, use empty string for text or 1 for semester/level
            
            Respond with ONLY a JSON object, no explanation, no markdown:
            {
              "courseCode": "MLS 301",
              "courseName": "Clinical Chemistry I",
              "category": "CHEMICAL PATHOLOGY",
              "level": "300",
              "semester": 1,
              "type": "Lecture Notes"
            }
        """.trimIndent()

        // WHY: Groq runs on custom LPU hardware — classification response arrives
        // in ~300ms which keeps the "Scan" button feel instant for the student.
        // Model: llama-3.3-70b-versatile — reliable structured JSON output.
        //
        // ⚠️ PRODUCTION: Move this key to a Firebase Cloud Function so it is
        // never shipped inside the APK.
        // Cloud Function URL: https://YOUR_REGION-YOUR_PROJECT.cloudfunctions.net/classifyResource
        val requestBody = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("max_tokens", 300)
            put("temperature", 0.1) // WHY: Low temp = deterministic JSON, less hallucination
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a university course classifier. Always respond with valid JSON only. No explanation, no markdown, no backticks.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }.toString()

        val connection = URL("https://api.groq.com/openai/v1/chat/completions")
            .openConnection() as HttpsURLConnection
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer YOUR_GROQ_API_KEY") // ⚠️ Move to Cloud Function
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 15_000
            outputStream.write(requestBody.toByteArray())
        }

        val responseText = connection.inputStream.bufferedReader().readText()
        val responseJson = JSONObject(responseText)
        val content = responseJson
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()

        // Parse Claude's JSON response
        val result = JSONObject(content)
        AiClassification(
            courseCode = result.optString("courseCode", ""),
            courseName = result.optString("courseName", ""),
            category = result.optString("category", "MEDICAL LABORATORY SCIENCE"),
            level = result.optString("level", ""),
            semester = result.optInt("semester", 1),
            type = result.optString("type", "Lecture Notes")
        )
    } catch (e: Exception) {
        android.util.Log.e("ProposeScreen", "AI classification failed", e)
        null
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposeScreen(
    onProposed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val user by UserSession.state.collectAsState()
    val scope = rememberCoroutineScope()

    // Form state
    var driveLink by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var courseCode by remember { mutableStateOf("") }
    var courseName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("") }
    var semester by remember { mutableIntStateOf(1) }
    var resourceType by remember { mutableStateOf("Lecture Notes") }

    // UI state
    var isScanning by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var aiClassified by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Dropdowns
    var typeExpanded by remember { mutableStateOf(false) }
    var levelExpanded by remember { mutableStateOf(false) }
    var semExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ── Header ──
        Text(
            text = "📤 Propose Resource",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Submit a Google Drive link. Our AI scans it, identifies the courses, and sends to your Rep/Admin for approval.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Naming tip ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "💡 For best results, name your files:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF92400E)
                )
                Text(
                    text = "CourseCode_Type.ext  (e.g., MLS301_LN.pdf, BIO221_PQ.pdf)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF92400E)
                )
                Text(
                    text = "Type: LN=Lecture Notes, PQ=Past Questions, TB=Textbook",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB45309)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Drive link input ──
        OutlinedTextField(
            value = driveLink,
            onValueChange = {
                driveLink = it
                // Reset AI classification when link changes
                if (aiClassified) {
                    aiClassified = false
                    courseCode = ""
                    courseName = ""
                    category = ""
                    level = ""
                }
            },
            label = { Text("Google Drive Link") },
            placeholder = { Text("https://drive.google.com/...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // ── AI Scan button ──
        Button(
            onClick = {
                val trimmedUrl = driveLink.trim()
                if (trimmedUrl.isBlank()) {
                    errorMessage = "❌ Please enter a Drive link first."
                    return@Button
                }
                errorMessage = null
                isScanning = true
                scope.launch {
                    val result = classifyDriveLink(trimmedUrl)
                    isScanning = false
                    if (result != null) {
                        courseCode = result.courseCode
                        courseName = result.courseName
                        category = result.category
                        level = result.level
                        semester = result.semester
                        resourceType = result.type
                        aiClassified = true
                    } else {
                        errorMessage = "⚠️ AI couldn't classify this link. Please fill in the details manually below."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(8.dp),
            enabled = !isScanning && driveLink.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6366F1)
            )
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scanning with AI...")
            } else {
                Text("🤖 Scan & Auto-Classify Link", fontWeight = FontWeight.Bold)
            }
        }

        // ── AI result badge ──
        AnimatedVisibility(visible = aiClassified) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✅ ", style = MaterialTheme.typography.bodyMedium)
                    Column {
                        Text(
                            "AI classified: $courseCode — $courseName",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534)
                        )
                        Text(
                            "$level Level • Semester $semester • $resourceType",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF166534)
                        )
                        Text(
                            "You can correct any field below before submitting.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4B7A5A)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Divider()
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Course Details (edit if AI got it wrong)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))

        // ── Course code ──
        OutlinedTextField(
            value = courseCode,
            onValueChange = { courseCode = it.uppercase() },
            label = { Text("Course Code") },
            placeholder = { Text("e.g., MLS 301") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Target level ──
        ExposedDropdownMenuBox(expanded = levelExpanded, onExpandedChange = { levelExpanded = it }) {
            OutlinedTextField(
                value = if (level.isBlank()) "Select target level" else "$level Level",
                onValueChange = {},
                readOnly = true,
                label = { Text("This resource is for...") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                shape = RoundedCornerShape(8.dp)
            )
            ExposedDropdownMenu(expanded = levelExpanded, onDismissRequest = { levelExpanded = false }) {
                listOf("100", "200", "300", "400", "500").forEach { l ->
                    DropdownMenuItem(
                        text = { Text("$l Level") },
                        onClick = { level = l; levelExpanded = false }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Semester + Type row ──
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Semester
            ExposedDropdownMenuBox(
                expanded = semExpanded,
                onExpandedChange = { semExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = if (semester == 1) "1st Sem" else "2nd Sem",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Semester") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = semExpanded) },
                    modifier = Modifier.menuAnchor(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                ExposedDropdownMenu(expanded = semExpanded, onDismissRequest = { semExpanded = false }) {
                    DropdownMenuItem(text = { Text("1st Semester") }, onClick = { semester = 1; semExpanded = false })
                    DropdownMenuItem(text = { Text("2nd Semester") }, onClick = { semester = 2; semExpanded = false })
                }
            }

            // Resource type
            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = resourceType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier.menuAnchor(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    DropdownMenuItem(text = { Text("Lecture Notes") }, onClick = { resourceType = "Lecture Notes"; typeExpanded = false })
                    DropdownMenuItem(text = { Text("Past Questions") }, onClick = { resourceType = "Past Questions"; typeExpanded = false })
                    DropdownMenuItem(text = { Text("Textbook") }, onClick = { resourceType = "Textbook"; typeExpanded = false })
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Notes ──
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            placeholder = { Text("e.g., Contains 2023/2024 past questions...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Error / success messages ──
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
        }
        successMessage?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7))
            ) {
                Text(
                    it,
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFF166534),
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Submit button ──
        Button(
            onClick = {
                // Validate
                if (driveLink.isBlank()) { errorMessage = "❌ Please enter a Drive link."; return@Button }
                if (courseCode.isBlank()) { errorMessage = "❌ Please enter a course code."; return@Button }
                if (level.isBlank()) { errorMessage = "❌ Please select the target level."; return@Button }

                errorMessage = null
                isSubmitting = true

                scope.launch {
                    try {
                        val proposal = Proposal(
                            driveUrl = driveLink.trim(),
                            courseCode = courseCode.uppercase().trim(),
                            courseName = courseName.trim(),
                            category = category.ifBlank { inferCategory(courseCode) },
                            level = level,
                            semester = semester,
                            type = resourceType,
                            notes = notes.trim(),
                            submittedBy = user.uid,
                            submittedByName = user.name,
                            aiClassified = aiClassified
                        )
                        ProposalRepository.submitProposal(proposal)
                        isSubmitting = false
                        successMessage = "✅ Proposal submitted! The $level Level rep will review it shortly."
                        // Reset form
                        driveLink = ""
                        courseCode = ""
                        courseName = ""
                        category = ""
                        level = ""
                        notes = ""
                        aiClassified = false
                    } catch (e: Exception) {
                        isSubmitting = false
                        errorMessage = "❌ Submission failed: ${e.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(8.dp),
            enabled = !isSubmitting && !isScanning,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Submitting...")
            } else {
                Text("Submit for AI Review", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

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
