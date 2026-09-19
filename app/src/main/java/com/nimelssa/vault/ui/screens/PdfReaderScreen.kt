package com.nimelssa.vault.ui.screens

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nimelssa.vault.ui.theme.OrientationManager
import com.nimelssa.vault.ui.theme.OrientationMode
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

/**
 * Native in-app PDF reader backed by Android's built-in PdfRenderer.
 * Pages are rendered to bitmaps locally (fully offline) and displayed in
 * a WebView, which provides native pinch-zoom, double-tap zoom, and
 * smooth vertical scroll — no custom gesture handling needed.
 *
 * In landscape, pages render at 1.5× screen height so the full page fits
 * vertically; the compact toolbar shows a back button, title, page count,
 * and browser button in a single row to maximize reading area.
 */
@Composable
fun PdfReaderScreen(
    file: File,
    title: String,
    onClose: () -> Unit,
    onOpenBrowser: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val orientationMode by OrientationManager.mode.collectAsState()

    // Resolve effective landscape: user preference overrides system rotation.
    val isLandscape = when (orientationMode) {
        OrientationMode.PORTRAIT -> false
        OrientationMode.LANDSCAPE -> true
        OrientationMode.AUTO -> configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    val rendererResult = remember(file) {
        runCatching {
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        }
    }

    DisposableEffect(file) {
        onDispose { rendererResult.getOrNull()?.close() }
    }

    val renderer = rendererResult.getOrNull()
    if (renderer == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "\uD83D\uDD12\uD83D\uDCC4", fontSize = MaterialTheme.typography.headlineLarge.fontSize)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Could not read this PDF",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = rendererResult.exceptionOrNull()?.message
                    ?: "The file may be corrupt, encrypted, or in an unexpected format.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (onOpenBrowser != null) {
                Button(
                    onClick = onOpenBrowser,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("\u2197 Open in browser")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            TextButton(onClick = onClose) { Text("\u2190 Back to resources") }
        }
        return
    }

    val pageCount = renderer.pageCount

    // Render all pages to base64-encoded JPEG strings (offline, no network).
    // Capped at 100 pages to bound memory for very large documents.
    // Landscape: target screen HEIGHT so the full page fits vertically.
    val pageImages: List<String> = remember(file, isLandscape) {
        val dm = context.resources.displayMetrics
        val targetSize = if (isLandscape) {
            (dm.heightPixels * 1.5f).coerceAtLeast(1f)
        } else {
            (dm.widthPixels * 1.5f).coerceAtLeast(1f)
        }
        val renderCount = min(pageCount, 100)
        (0 until renderCount).mapNotNull { pageIndex ->
            renderPageBase64(renderer, pageIndex, targetSize)
        }
    }

    // Build a single HTML document with embedded page images.
    val html: String = remember(pageImages) {
        buildPageHtml(pageImages)
    }

    if (isLandscape) {
        // ── Landscape: compact single-row toolbar + full-bleed reader ──
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$pageCount pages",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                if (onOpenBrowser != null) {
                    TextButton(onClick = onOpenBrowser) {
                        Text("\u2197", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.setSupportMultipleWindows(false)
                        settings.allowFileAccess = true
                        isVerticalScrollBarEnabled = true
                        isHorizontalScrollBarEnabled = false
                        loadDataWithBaseURL(
                            null,
                            html,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        // ── Portrait: title label + full reader ──
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "\uD83D\uDCC4 $title",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.setSupportMultipleWindows(false)
                        settings.allowFileAccess = true
                        isVerticalScrollBarEnabled = true
                        isHorizontalScrollBarEnabled = false
                        loadDataWithBaseURL(
                            null,
                            html,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Render a single PDF page into a base64-encoded JPEG string.
 * [targetWidth] is the render width in pixels — in portrait this is
 * 1.5× screen width; in landscape it is 1.5× screen height so the
 * full page fits vertically.
 * Returns null if rendering fails (e.g. page is encrypted).
 */
private fun renderPageBase64(
    renderer: PdfRenderer,
    pageIndex: Int,
    targetWidth: Float
): String? {
    return runCatching {
        val page = renderer.openPage(pageIndex)
        try {
            val scale = (targetWidth / page.width).coerceIn(1f, 6f)
            val bmp = Bitmap.createBitmap(
                (page.width * scale).toInt().coerceAtLeast(1),
                (page.height * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            page.render(
                bmp, null,
                Matrix().apply { postScale(scale, scale) },
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )
            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            bmp.recycle()
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        } finally {
            page.close()
        }
    }.getOrNull()
}

/**
 * Build a self-contained HTML page with all PDF page images embedded
 * as base64 data URIs.  The viewport meta tag enables native pinch/double-tap
 * zoom up to 5x.  Dark background matches the app theme.
 */
private fun buildPageHtml(pageImages: List<String>): String = buildString {
    append("<!DOCTYPE html><html><head>")
    append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0,maximum-scale=5.0,user-scalable=yes\">")
    append("<style>")
    append("html,body{margin:0;padding:0;background:#121212;}")
    append("img{width:100%;display:block;margin-bottom:4px;}")
    append("</style></head><body>")
    for (b64 in pageImages) {
        append("<img src=\"data:image/jpeg;base64,")
        append(b64)
        append("\" loading=\"lazy\">")
    }
    append("</body></html>")
}
