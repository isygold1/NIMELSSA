package com.nimelssa.vault.ui.screens

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import java.io.File

/**
 * Native in-app PDF reader backed by Android's built-in PdfRenderer.
 * No WebView, no network, no third-party dependency — works fully offline.
 *
 * Pages render lazily (only visible pages exist in memory) at ~1.5x screen
 * width for crisp text, displayed at fit-width.
 */
@Composable
fun PdfReaderScreen(
    file: File,
    title: String,
    onClose: () -> Unit,
    onOpenBrowser: (() -> Unit)? = null
) {
    // Open the renderer once per file; close it when leaving this screen.
    val rendererResult = remember(file) {
        runCatching { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) }
    }

    DisposableEffect(file) {
        onDispose { rendererResult.getOrNull()?.close() }
    }

    val renderer = rendererResult.getOrNull()
    if (renderer == null) {
        // Unreadable / corrupt / encrypted PDF
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "🔒📄", fontSize = MaterialTheme.typography.headlineLarge.fontSize)
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
                    Text("↗ Open in browser")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            TextButton(onClick = onClose) { Text("← Back to resources") }
        }
        return
    }

    val pageCount = remember(file) { renderer.pageCount }

    Column(modifier = Modifier.fillMaxSize()) {
        // Page indicator + title
        Text(
            text = "📄 $title",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 8.dp, end = 8.dp, bottom = 24.dp
            )
        ) {
            items(pageCount) { pageIndex ->
                PdfPage(file = file, renderer = renderer, pageIndex = pageIndex)
            }
        }
    }
}

/** Renders one PDF page into a bitmap at ~1.5x screen width (lazy, cached per page). */
@Composable
private fun PdfPage(file: File, renderer: PdfRenderer, pageIndex: Int) {
    val context = LocalContext.current
    val bitmap = remember(file, pageIndex) {
        runCatching {
            val page = renderer.openPage(pageIndex)
            try {
                val targetWidth =
                    (context.resources.displayMetrics.widthPixels * 1.5f).coerceAtLeast(1f)
                val scale = (targetWidth / page.width).coerceIn(1f, 4f)
                val bmp = Bitmap.createBitmap(
                    (page.width * scale).toInt().coerceAtLeast(1),
                    (page.height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                page.render(
                    bmp,
                    null,
                    Matrix().apply { postScale(scale, scale) },
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )
                bmp
            } finally {
                page.close()
            }
        }.getOrNull()
    }

    if (bitmap != null) {
        ZoomablePageImage(
            bitmap = bitmap.asImageBitmap(),
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⚠ Page ${pageIndex + 1} could not be rendered",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One rendered page with pinch-zoom + two-finger pan and double-tap
 * 1x ↔ 2.5x toggle. Single-finger swipe still scrolls the page list.
 * Zoom clamps to 1x..5x; pan resets when zoom returns to 1x.
 *
 * Pinch zooms around the centroid (the point between the two fingers)
 * so the content under your fingers stays fixed. Double-tap zooms into
 * the exact tap point. Pan is clamped to image boundaries.
 *
 * Everything runs in a single pointerInput block so taps, pinch, and
 * pan never compete for pointer events.
 */
@Composable
private fun ZoomablePageImage(
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { viewSize = it }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    // ── Double-tap detection ──
                    var lastTapTime = 0L
                    var lastTapX = 0f
                    var lastTapY = 0f
                    val doubleTapSlop = viewSize.width * 0.15f // 15% of screen width
                    val doubleTapTimeout = 300L // ms

                    awaitFirstDown(requireUnconsumed = false)
                    val down1 = currentEvent.changes[0]
                    val down1X = down1.position.x
                    val down1Y = down1.position.y
                    val down1Time = System.currentTimeMillis()

                    // Wait for finger up (or second finger down for pinch)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.any { !it.pressed }) break
                    } while (true)

                    // Check for second tap (double-tap)
                    val upTime = System.currentTimeMillis()
                    if (upTime - down1Time < 250) {
                        // First tap was quick — wait for second DOWN
                        val secondDown = awaitPointerEvent(
                            pass = PointerEventPass.Initial
                        )
                        val secondPointer = secondDown.changes.firstOrNull { it.pressed }
                        if (secondPointer != null) {
                            val dist = Offset(
                                secondPointer.position.x - down1X,
                                secondPointer.position.y - down1Y
                            ).getDistance()
                            val elapsed = System.currentTimeMillis() - down1Time
                            if (dist < doubleTapSlop && elapsed < doubleTapTimeout) {
                                // Double-tap detected — toggle zoom
                                secondDown.changes.forEach { it.consume() }
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    val newScale = 2.5f
                                    val pivotX = viewSize.width / 2f
                                    val pivotY = viewSize.height / 2f
                                    val cx = secondPointer.position.x - pivotX
                                    val cy = secondPointer.position.y - pivotY
                                    offset = Offset(
                                        cx * (scale - newScale),
                                        cy * (scale - newScale)
                                    )
                                    scale = newScale
                                }
                                // Drain remaining events so nothing leaks to LazyColumn
                                do {
                                    val e = awaitPointerEvent()
                                    e.changes.forEach { it.consume() }
                                } while (e.changes.any { it.pressed })
                                return@awaitEachGesture
                            }
                        }
                        // Not a valid double-tap — fall through to single-finger
                    }

                    // ── Single-finger / pinch gesture ──
                    // Re-enter: single finger is already down (down1).
                    // We need a fresh gesture loop from here.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var handled = false
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            val pointerCount = pressed.size

                            val zoomChange = if (pointerCount >= 2 && event.changes.size >= 2) {
                                val a = event.changes[0].position
                                val b = event.changes[1].position
                                val pa = event.changes[0].previousPosition
                                val pb = event.changes[1].previousPosition
                                val cur = (a - b).getDistance()
                                val prev = (pa - pb).getDistance()
                                if (prev > 0f) cur / prev else 1f
                            } else 1f

                            val panChange = if (pointerCount >= 2 && event.changes.isNotEmpty()) {
                                val sum = event.changes.fold(Offset.Zero) { acc, c ->
                                    acc + (c.position - c.previousPosition)
                                }
                                Offset(sum.x / event.changes.size, sum.y / event.changes.size)
                            } else Offset.Zero

                            // Single finger at 1× — let LazyColumn scroll
                            if (!handled && pointerCount < 2 && scale <= 1f) {
                                break
                            }
                            handled = true
                            event.changes.forEach { it.consume() }

                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            if (newScale > 1f && event.changes.size >= 2) {
                                val cx = (event.changes[0].position.x + event.changes[1].position.x) / 2f
                                val cy = (event.changes[0].position.y + event.changes[1].position.y) / 2f
                                val pivotX = viewSize.width / 2f
                                val pivotY = viewSize.height / 2f
                                val cxP = cx - pivotX
                                val cyP = cy - pivotY
                                val newTX = cxP * (scale - newScale) + offset.x + panChange.x
                                val newTY = cyP * (scale - newScale) + offset.y + panChange.y
                                val maxPanX = viewSize.width * (newScale - 1f) / 2f
                                val maxPanY = viewSize.height * (newScale - 1f) / 2f
                                offset = Offset(
                                    newTX.coerceIn(-maxPanX, maxPanX),
                                    newTY.coerceIn(-maxPanY, maxPanY)
                                )
                            } else {
                                offset = Offset.Zero
                            }
                            scale = newScale
                        } while (event.changes.any { it.pressed })
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth
        )
    }
}
