package com.nimelssa.vault.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nimelssa.vault.data.Course
import com.nimelssa.vault.data.CourseRepository
import com.nimelssa.vault.data.OfflineManager
import com.nimelssa.vault.data.Resource
import com.nimelssa.vault.data.ResourceRepository
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    courseCode: String,
    onClose: () -> Unit,
    initialResourceType: String? = null,  // "LN", "PQ", "TB", or null to show all
    levelHint: String? = null,  // level for the level-wide "TEXTBOOK" entry
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resourceMap by ResourceRepository.resources.collectAsState()

    // "TEXTBOOK" is the synthetic course used by the Level Textbooks cards in
    // the workspace. It has no Firestore/session course entry — build the
    // display course from the level the card was on.
    val isLevelTextbooks = courseCode == "TEXTBOOK"
    val course = if (!isLevelTextbooks) CourseRepository.findCourse(courseCode)
        else Course(
            code = "TEXTBOOK",
            name = "Reference Textbooks",
            category = "TEXTBOOKS",
            level = levelHint ?: "",
            semester = 1
        )
    val allResources = if (!isLevelTextbooks)
        resourceMap[CourseRepository.normalizeCode(courseCode)] ?: emptyList()
    else
        resourceMap["__LEVEL__"]?.filter { levelHint == null || it.level == levelHint }
            ?: emptyList()

    // Type filter chips (All / Notes / Past Qs / Textbook)
    var filterType by remember { mutableStateOf(initialResourceType) }
    val resources = if (filterType != null)
        allResources.filter { it.resourceType == filterType }
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
    var retryTick by remember { mutableIntStateOf(0) }   // bumped to re-attempt WebView load

    // Native PDF preview (PdfRenderer): file + title once opened from a saved copy
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var pdfTitle by remember { mutableStateOf("") }
    var pdfError by remember { mutableStateOf<String?>(null) }
    var pdfSourceUrl by remember { mutableStateOf<String?>(null) }  // for the reader's ↗ Browser escape
    var isOnline by remember { mutableStateOf(OfflineManager.isOnline(context)) }
    var isSaving by remember { mutableStateOf(false) }

    // Per-file offline saves: ids currently downloading + a tick bumped after
    // any save so cards/sticky bar recompose with fresh offline availability.
    var savingIds by remember { mutableStateOf(setOf<String>()) }
    var offlineTick by remember { mutableIntStateOf(0) }

    // Live connectivity: reacts to Wi-Fi/data toggles instead of a one-shot check.
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline = true
            }
            override fun onLost(network: Network) {
                isOnline = OfflineManager.isOnline(context)
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                isOnline = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }

    val previewTitle = when {
        pdfFile != null -> pdfTitle
        activeUrl != null -> activeTitle
        else -> course.code
    }
    val inListMode = pdfFile == null && activeUrl == null

    Column(modifier = modifier.fillMaxSize()) {
        // ── Top bar ──
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = previewTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                    if (inListMode) {
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
                IconButton(onClick = {
                    when {
                        pdfFile != null -> pdfFile = null
                        activeUrl != null -> activeUrl = null
                        else -> onClose()
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            actions = {
                if (activeUrl != null) {
                    // WebView mode: let the user escape to a Custom Tab
                    // (Drive previews are far more reliable in Chrome).
                    val currentUrl = activeUrl
                    if (currentUrl != null) {
                        TextButton(
                            onClick = {
                                openCustomTab(context, currentUrl)
                            }
                        ) {
                            Text("↗ Browser", color = MaterialTheme.colorScheme.primary)
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

        // ── Type filter chips (list mode only) ──
        if (inListMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = listOf(
                    null to "All",
                    "LN" to "📖 Notes",
                    "PQ" to "📝 Past Qs",
                    "TB" to "📚 Textbook"
                )
                options.forEach { (type, label) ->
                    FilterChip(
                        selected = filterType == type,
                        onClick = { filterType = type },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Box(modifier = Modifier.weight(1f)) {
        if (webViewError != null) {
            // ── Broken link error card ──
            val failedUrl = activeUrl ?: ""
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
                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            webViewError = null
                            retryTick++
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("↺ Retry")
                    }
                    if (failedUrl.isNotBlank()) {
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(failedUrl))
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("↗ Open in browser")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = {
                    webViewError = null
                    activeUrl = null
                }) {
                    Text("← Back to resources")
                }
            }
        } else if (pdfError != null) {
            // ── PDF load failed (download or render) ──
            val failedUrl = activeUrl ?: ""
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "📥💔", fontSize = MaterialTheme.typography.headlineLarge.fontSize)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = pdfError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        pdfError = null
                        pdfFile = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("← Back to resources")
                }
            }
        } else if (pdfFile != null) {
            // ── Native in-app PDF reader ──
            val file = pdfFile
            if (file != null) {
                PdfReaderScreen(
                    file = file,
                    title = pdfTitle,
                    onClose = {
                        pdfFile = null
                        pdfSourceUrl = null
                    },
                    onOpenBrowser = {
                        val currentUrl = pdfSourceUrl
                        if (currentUrl != null && currentUrl.isNotBlank()) {
                            openCustomTab(context, currentUrl)
                        }
                    }
                )
            }
        } else if (activeUrl != null) {
            // ── WebView mode ──
            val resolvedUrl = resolveResourceUrl(activeUrl!!)
            ResourceWebView(
                url = resolvedUrl,
                retryKey = retryTick,
                onLoadingChanged = { loading -> webViewLoading = loading },
                onProgressChanged = { progress -> webViewProgress = progress },
                onError = { errorDesc ->
                    webViewError = errorDesc
                }
            )
        } else {
            // ── Resource list mode ──
            ResourceListView(
                modifier = Modifier.fillMaxSize(),
                course = course,
                resources = resources,
                isOnline = isOnline,
                filterLabel = when (filterType) {
                    "LN" -> "lecture notes"
                    "PQ" -> "past questions"
                    "TB" -> "textbook"
                    else -> null
                },
                onOpenResource = { resource, url, localFile ->
                    scope.launch {
                        when {
                            // Saved copy exists → native in-app reader (works offline,
                            // no download — the ⬇️ button is the only save path).
                            localFile != null -> {
                                pdfTitle = resource.fileName.ifBlank { resource.label }
                                pdfError = null
                                pdfSourceUrl = url
                                pdfFile = localFile
                            }
                            // Online → view only: Custom Tab (Chrome handles PDFs
                            // and Drive previews; no bytes written to the app).
                            // Local file paths (edge cases) still use WebView.
                            url.startsWith("file://") || url.startsWith("/") -> {
                                activeUrl = url
                                activeTitle = "${course.code} — ${resource.resourceLabel}"
                            }
                            isOnline -> {
                                openCustomTab(context, url)
                            }
                            // Offline with no saved copy — prompt to download first.
                            else -> {
                                pdfFile = null
                                pdfTitle = ""
                                activeUrl = null
                                pdfError = "You're offline and this file has no saved copy.\n" +
                                    "Tap ⬇️ on the file card while connected to save it."
                            }
                        }
                    }
                },
                savingIds = savingIds,
                offlineTick = offlineTick,
                onSaveResource = { resource ->
                    scope.launch {
                        savingIds = savingIds + resource.id
                        OfflineManager.saveSingleResource(course.code, resource)
                        savingIds = savingIds - resource.id
                        offlineTick++
                    }
                }
            )
        }
        }

        // ── Sticky offline bar (list mode only) ──
        if (inListMode) {
            val savedOffline = course.code in OfflineManager.getSavedCodes()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            if (course.code in OfflineManager.getSavedCodes()) {
                                OfflineManager.removeOffline(course.code)
                            } else {
                                OfflineManager.saveOfflineResources(course.code, allResources)
                            }
                            isSaving = false
                        }
                    },
                    enabled = !isSaving && allResources.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (savedOffline)
                            MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.primary,
                        contentColor = if (savedOffline)
                            MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (savedOffline) "✓ Saved offline" else "💾 Save course offline",
                        fontWeight = FontWeight.Bold
                    )
                }
                val usedMb = OfflineManager.getUsedBytes() / (1024 * 1024)
                val maxMb = OfflineManager.getMaxBytes() / (1024 * 1024)
                Text(
                    text = "$usedMb / $maxMb MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────── Resource list ───────────────

/** Opens a URL in a Chrome Custom Tab (falls back to the default browser). */
private fun openCustomTab(context: Context, url: String) {
    if (url.isBlank()) return
    CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
        .launchUrl(context, Uri.parse(url))
}

@Composable
private fun ResourceListView(
    modifier: Modifier = Modifier,
    course: com.nimelssa.vault.data.Course,
    resources: List<Resource>,
    isOnline: Boolean,
    filterLabel: String? = null,
    onOpenResource: (Resource, String, File?) -> Unit,
    savingIds: Set<String> = emptySet(),
    offlineTick: Int = 0,
    onSaveResource: ((Resource) -> Unit)? = null
) {
    Column(
        modifier = modifier
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

        Spacer(modifier = Modifier.height(12.dp))

        if (resources.isNotEmpty()) {
            // CCMAS sections: files filed under a variant (old) code vs the
            // current code. Headers only render when a shelf holds both
            // generations — single-generation shelves stay clean.
            val currentSection = mutableListOf<Resource>()
            val previousSection = mutableListOf<Resource>()
            resources.forEach { resource ->
                val stored = CourseRepository.normalizeCode(resource.courseCode)
                val canonical = CourseRepository.resolveCode(resource.courseCode)
                if (stored.isNotBlank() && stored != canonical) {
                    previousSection.add(resource)
                } else {
                    currentSection.add(resource)
                }
            }
            val showSections = currentSection.isNotEmpty() && previousSection.isNotEmpty()

            if (showSections) {
                val prevCodes = previousSection.map { it.courseCode }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(", ")
                Text(
                    text = "📁 Current — ${course.code}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                ResourceListSection(
                    course = course,
                    resources = currentSection,
                    isOnline = isOnline,
                    onOpenResource = onOpenResource,
                    savingIds = savingIds,
                    offlineTick = offlineTick,
                    onSaveResource = onSaveResource
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "📁 Previous CCMAS — $prevCodes",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(4.dp))
                ResourceListSection(
                    course = course,
                    resources = previousSection,
                    isOnline = isOnline,
                    onOpenResource = onOpenResource,
                    savingIds = savingIds,
                    offlineTick = offlineTick,
                    onSaveResource = onSaveResource
                )
            } else {
                ResourceListSection(
                    course = course,
                    resources = resources,
                    isOnline = isOnline,
                    onOpenResource = onOpenResource,
                    savingIds = savingIds,
                    offlineTick = offlineTick,
                    onSaveResource = onSaveResource
                )
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
                        text = if (filterLabel != null) "No $filterLabel yet"
                               else "No materials uploaded yet",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (filterLabel != null)
                            "Propose one to your class rep — it appears here once approved."
                        else
                            "Use the Propose tab to submit resources for this course.",
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

/**
 * One CCMAS generation's files: renders the resource cards for a section of
 * the shelf (current code or previous/variant codes).
 */
@Composable
private fun ResourceListSection(
    course: com.nimelssa.vault.data.Course,
    resources: List<Resource>,
    isOnline: Boolean,
    onOpenResource: (Resource, String, File?) -> Unit,
    savingIds: Set<String> = emptySet(),
    offlineTick: Int = 0,
    onSaveResource: ((Resource) -> Unit)? = null
) {
    // offlineTick is read here so the section recomposes after any save and
    // freshly queries OfflineManager for each card's saved state.
    resources.forEach { resource ->
        val localFile = OfflineManager.getLocalFile(course.code, resource.resourceType.lowercase())
        // Approved resources carry masterUrl, but legacy or edge-case
        // docs may only have fileId — build a viewable URL either way
        // so no approved resource ever opens dead.
        val url = resourceUrl(resource)
        val saved = localFile != null
        ResourceCard(
            icon = resource.icon,
            title = resource.fileName.ifBlank { resource.label.ifBlank { resource.resourceLabel } },
            subtitle = "${resource.resourceLabel}" +
                if (resource.submittedBy.isNotBlank()) " • by ${resource.submittedBy}" else "",
            notes = resource.notes,
            isAvailableOffline = saved,
            isOnline = isOnline,
            isSaving = resource.id in savingIds,
            onSave = if (onSaveResource != null && isOnline && !saved) {
                { onSaveResource(resource) }
            } else null,
            onOpen = { onOpenResource(resource, url, localFile?.let { File(it) }) }
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ─────────────── Resource card ───────────────

/**
 * The URL to open for a resource: masterUrl when present, otherwise a Drive
 * view URL built from fileId (scan-approved resources always have fileId).
 */
private fun resourceUrl(resource: Resource): String {
    if (resource.masterUrl.isNotBlank()) return resource.masterUrl
    if (resource.fileId.isNotBlank()) return "https://drive.google.com/file/d/${resource.fileId}/view"
    return ""
}

@Composable
private fun ResourceCard(
    icon: String,
    title: String,
    subtitle: String,
    notes: String,
    isAvailableOffline: Boolean,
    isOnline: Boolean,
    isSaving: Boolean = false,
    onSave: (() -> Unit)? = null,
    onOpen: () -> Unit
) {
    val canOpen = isOnline || isAvailableOffline

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            // Icon tile
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, style = MaterialTheme.typography.titleLarge)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

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
                            isAvailableOffline -> "📥 Open from device"
                            else -> "Open ↗"
                        },
                        fontWeight = FontWeight.Bold,
                        color = if (canOpen) Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Per-file save: ⬇️ at the card's top-right, only while online and
            // not already saved — the single source of downloads now.
            if (onSave != null) {
                Column(modifier = Modifier.align(Alignment.Top)) {
                    IconButton(
                        onClick = onSave,
                        enabled = !isSaving,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            text = if (isSaving) "⏳" else "⬇️",
                            fontSize = 16.sp
                        )
                    }
                }
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
        // Blank (no masterUrl and no fileId) — nothing to load
        url.isBlank() -> ""

        // Already a local file
        url.startsWith("file://") -> url
        url.startsWith("/") -> "file://$url"

        // Google Drive file link — extract file ID and use /preview
        url.contains("drive.google.com/file/d/") -> {
            val id = url.substringAfter("/file/d/").substringBefore("/").substringBefore("?")
            "https://drive.google.com/file/d/$id/preview"
        }

        // Google Drive "open?id=" links (older share format)
        url.contains("drive.google.com/open?id=") -> {
            val id = url.substringAfter("open?id=").substringBefore("&")
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

// NOTE: displayUrl is kept for future use (resource subtitles now show
// labels/file names instead of URLs).

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ResourceWebView(
    url: String,
    retryKey: Int = 0,
    onLoadingChanged: (Boolean) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onError: (String) -> Unit = {}
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var hadError by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Reload if URL changes, or when the user hits "↺ Retry" (retryKey).
        LaunchedEffect(url, retryKey) {
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
