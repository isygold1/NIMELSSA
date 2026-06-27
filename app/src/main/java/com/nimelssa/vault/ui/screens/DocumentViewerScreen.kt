package com.nimelssa.vault.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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

    // null = resource list view, non-null = WebView showing this URL
    var activeUrl by remember { mutableStateOf<String?>(null) }
    var activeTitle by remember { mutableStateOf("") }
    var webViewLoading by remember { mutableStateOf(false) }
    var webViewProgress by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = {
                Text(
                    text = if (activeUrl != null) activeTitle else course.code,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )
            },
            navigationIcon = {
                TextButton(onClick = {
                    if (activeUrl != null) {
                        // Go back to resource list
                        activeUrl = null
                    } else {
                        onClose()
                    }
                }) {
                    Text(
                        if (activeUrl != null) "← Back" else "Close",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            actions = {
                if (activeUrl == null) {
                    TextButton(onClick = { CourseRepository.toggleOffline(course.code) }) {
                        Text(
                            text = if (course.isOffline) "✓ Offline" else "Save Offline",
                            color = if (course.isOffline) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        // WebView loading indicator
        if (webViewLoading && activeUrl != null) {
            LinearProgressIndicator(
                progress = { webViewProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }

        if (activeUrl != null) {
            // ── WebView mode: show the resource inline ──
            ResourceWebView(
                url = activeUrl!!,
                onLoadingChanged = { loading -> webViewLoading = loading },
                onProgressChanged = { progress -> webViewProgress = progress }
            )
        } else {
            // ── Resource list mode ──
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
                if (course.submittedBy.isNotBlank()) {
                    Text(
                        text = "Submitted by: ${course.submittedBy}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

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
                        subtitle = course.lectureNotesUrl,
                        notes = course.notes,
                        onOpen = {
                            activeUrl = course.lectureNotesUrl
                            activeTitle = "$course.code — Study Notes"
                        }
                    )
                }

                // Past Questions
                if (course.pastQuestionsUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ResourceCard(
                        title = "📝 Past Questions & Test Papers",
                        subtitle = course.pastQuestionsUrl,
                        notes = course.notes,
                        onOpen = {
                            activeUrl = course.pastQuestionsUrl
                            activeTitle = "$course.code — Past Questions"
                        }
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
                            Text(text = "📭", fontSize = MaterialTheme.typography.headlineLarge.fontSize)
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
}

@Composable
private fun ResourceCard(
    title: String,
    subtitle: String,
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
                text = subtitle,
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
                Text("Open in App ↗", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ResourceWebView(
    url: String,
    onLoadingChanged: (Boolean) -> Unit,
    onProgressChanged: (Int) -> Unit
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)
                        allowFileAccess = true
                        allowContentAccess = true
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            onLoadingChanged(true)
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            onLoadingChanged(false)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            onProgressChanged(newProgress)
                            if (newProgress == 100) onLoadingChanged(false)
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
