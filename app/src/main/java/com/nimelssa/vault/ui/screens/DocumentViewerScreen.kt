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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nimelssa.vault.data.CourseRepository
import com.nimelssa.vault.data.OfflineManager
import com.nimelssa.vault.data.Resource
import com.nimelssa.vault.data.ResourceRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    courseCode: String,
    onClose: () -> Unit,
    initialResourceType: String? = null,  // "LN", "PQ", "TB", or null to show all
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allCourses by CourseRepository.courses.collectAsState()
    val resourceMap by ResourceRepository.resources.collectAsState()
    val course = allCourses.find { it.code == courseCode }
    val allResources = resourceMap[courseCode] ?: emptyList()
    val resources = if (initialResourceType != null)
        allResources.filter { it.resourceType == initialResourceType }
    else allResources

    if (course == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Course not found: $courseCode", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    // null = resource list view, non-null = WebView showing this URL
    var activeUrl by remember { mutableStateOf<String?>(null) }
    var activeTitle by remember { mutableStateOf("") }
    var webViewLoading by remember { mutableStateOf(false) }
    var webViewProgress by remember { mutableIntStateOf(0) }
    var webViewError by remember { mutableStateOf<String?>(null) }
    var isOnline by remember { mutableStateOf(OfflineManager.isOnline(context)) }
    var isSaving by remember { mutableStateOf(false) }

    // Refresh online status
    LaunchedEffect(Unit) {
        isOnline = OfflineManager.isOnline(context)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Top bar ──
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = if (activeUrl != null) activeTitle else course.code,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                    if (activeUrl == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isOnline) "● Online" else "○ Offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOnline) Color(0xFF22C55E) else Color(0xFFEF4444)
                            )
                        }
                    }
                }
            },
            navigationIcon = {
                TextButton(onClick = {
                    if (activeUrl != null) {
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
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(
                            onClick = {
                                isSaving = true
                                scope.launch {
                                    if (course.code in OfflineManager.getSavedCodes()) {
                                        OfflineManager.removeOffline(course.code)
                                    } else {
                                        OfflineManager.saveOfflineResources(course.code, resources)
                                    }
                                    isSaving = false
                                }
                            },
                            enabled = !isSaving
                        ) {
                            Text(
                                text = if (course.code in OfflineManager.getSavedCodes()) "✓ Saved Offline"
                                       else "Save Offline",
                                color = if (course.code in OfflineManager.getSavedCodes())
                                        MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.secondary
                            )
                        }
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

        if (webViewError != null) {
            // ── Broken link error card ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "🔗💔", fontSize = MaterialTheme.typography.headlineLarge.fontSize)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = webViewError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        webViewError = null
                        activeUrl = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("← Back to resources")
                }
            }
        } else if (activeUrl != null) {
            // ── WebView mode ──
            val resolvedUrl = resolveResourceUrl(activeUrl!!)
            ResourceWebView(
                url = resolvedUrl,
                onLoadingChanged = { loading -> webViewLoading = loading },
                onProgressChanged = { progress -> webViewProgress = progress },
                onError = { errorDesc ->
                    webViewError = errorDesc
                }
            )
        } else {
            // ── Resource list mode ──
            ResourceListView(
                course = course,
                resources = resources,
                isOnline = isOnline,
                onOpenUrl = { url, title ->
                    activeUrl = url
                    activeTitle = title
                }
            )
        }
    }
}

// ─────────────── Resource list ───────────────

@Composable
private fun ResourceListView(
    course: com.nimelssa.vault.data.Course,
    resources: List<Resource>,
    isOnline: Boolean,
    onOpenUrl: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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

        // Offline badge
        if (course.code in OfflineManager.getSavedCodes()) {
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "📥 Available offline — files saved to device",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF166534)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Prominent offline save banner ──
        if (isOnline && resources.isNotEmpty() && course.code !in OfflineManager.getSavedCodes()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📥 Save for offline access",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF075985)
                        )
                        Text(
                            text = "Open without internet anytime",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF075985)
                        )
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                OfflineManager.saveOfflineResources(course.code, resources)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0369A1)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save All", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = "📚 Available Resources",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (resources.isNotEmpty()) {
            resources.forEach { resource ->
                val localFile = OfflineManager.getLocalFile(course.code, resource.resourceType.lowercase())
                ResourceCard(
                    title = "${resource.icon} ${resource.resourceLabel}",
                    subtitle = resource.label.ifBlank { resource.masterUrl },
                    url = resource.masterUrl,
                    notes = resource.notes,
                    isAvailableOffline = localFile != null,
                    isOnline = isOnline,
                    onOpen = { onOpenUrl(resource.masterUrl, "${course.code} — ${resource.resourceLabel}") }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
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
                        text = "Use the Propose tab to submit resources for this course.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Contributor notes (collect from all resources)
        val allNotes = resources.map { it.notes }.filter { it.isNotBlank() }
        if (allNotes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "📌 Contributor Notes",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            allNotes.forEach { note ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Submitted by info
        val submitters = resources.map { it.submittedBy }.filter { it.isNotBlank() }.distinct()
        if (submitters.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Submitted by: ${submitters.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ─────────────── Resource card ───────────────

@Composable
private fun ResourceCard(
    title: String,
    subtitle: String,
    url: String,
    notes: String,
    isAvailableOffline: Boolean,
    isOnline: Boolean,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (isAvailableOffline) {
                    Text(
                        text = "📥",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = displayUrl(url),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
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

            val canOpen = isOnline || isAvailableOffline
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                enabled = canOpen,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = when {
                        !canOpen -> "Offline — No saved copy"
                        isAvailableOffline -> "Open from device ↗"
                        else -> "Open in App ↗"
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (canOpen) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────── WebView with PDF support ───────────────

/**
 * Resolves a resource URL for display in the WebView.
 * For PDF links, wraps them in Google Docs viewer for inline rendering.
 * For Google Drive links, extracts the file/folder ID and uses the correct format.
 * For local files (offline), returns a file:// URI.
 */
private fun resolveResourceUrl(url: String): String {
    return when {
        // Already a local file
        url.startsWith("file://") -> url
        url.startsWith("/") -> "file://$url"

        // Google Drive file link — extract file ID and use /preview
        url.contains("drive.google.com/file/d/") -> {
            val id = url.substringAfter("/file/d/").substringBefore("/").substringBefore("?")
            "https://drive.google.com/file/d/$id/preview"
        }

        // Google Drive folder link — handles both /drive/folders/ and /drive/mobile/folders/
        Regex("drive\\.google\\.com/drive/[^/]*/folders/").containsMatchIn(url) -> {
            val id = url.substringAfter("folders/").substringBefore("?").substringBefore("/")
            "https://drive.google.com/drive/folders/$id"
        }

        // Other Google Drive links — add embedded mode
        url.contains("drive.google.com") && !url.contains("preview") -> {
            "$url&embedded=true".replace("?&", "?").replace("&&", "&")
        }

        // PDF URL — wrap in Google Docs viewer
        url.contains(".pdf", ignoreCase = true) ||
        url.contains("pdf?", ignoreCase = true) -> {
            "https://docs.google.com/viewer?url=${android.net.Uri.encode(url)}&embedded=true"
        }

        // Everything else — load directly
        else -> url
    }
}

/**
 * Shortens a URL for display in the card subtitle.
 */
private fun displayUrl(url: String): String {
    return try {
        val u = java.net.URL(url)
        val host = u.host.removePrefix("www.")
        val path = u.path
        if (path.length > 30) "$host/...${path.takeLast(20)}"
        else "$host$path"
    } catch (_: Exception) {
        if (url.length > 40) url.take(37) + "..." else url
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ResourceWebView(
    url: String,
    onLoadingChanged: (Boolean) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onError: (String) -> Unit = {}
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var hadError by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Reload if URL changes
        LaunchedEffect(url) {
            hadError = false
            webView?.loadUrl(url)
        }

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
                        // Allow mixed content for Drive docs
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            hadError = false
                            onLoadingChanged(true)
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (!hadError) onLoadingChanged(false)
                        }
                        override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            // Only treat main-frame errors as fatal
                            if (request?.isForMainFrame == true) {
                                hadError = true
                                val description = error?.description?.toString()
                                    ?: "Failed to load resource"
                                onLoadingChanged(false)
                                onError("Could not open this resource.\n$description")
                            }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            onProgressChanged(newProgress)
                            if (newProgress == 100 && !hadError) onLoadingChanged(false)
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
