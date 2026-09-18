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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.abs

/**
 * Native in-app PDF reader backed by Android's built-in PdfRenderer.
 * No WebView, no network, no third-party dependency — works fully offline.
 *
 * Pages render lazily (only visible pages exist in memory) at ~1.5x screen
 * width for crisp text, displayed at fit-width.
 *
 * Zoom behavior: pinch / one-finger pan / double-tap. When a gesture
 * settles, the page re-renders its bitmap at the settled zoom so text
 * stays crisp (capped at [MAX_DETAIL_ZOOM]x to bound memory).
 */
@Composable
fun PdfReaderScreen(
    file: File,
    title: String,
    onClose: () -> Unit,
    onOpenBrowser: (() -> Unit)? = null
) {
    val rendererResult = remember(file) {
        runCatching { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) }
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

/** Cap for the post-gesture re-render zoom — bounds per-page bitmap memory. */
private const val MAX_DETAIL_ZOOM = 2f

/**
 * Renders one PDF page into a bitmap at ~1.5x screen width (lazy, cached per
 * page and per [detailScale]). When a zoom gesture settles the page
 * re-renders at 1.5x * detailScale so magnified text stays sharp.
 */
@Composable
private fun PdfPage(file: File, renderer: PdfRenderer, pageIndex: Int) {
    val context = LocalContext.current

    // Extra resolution multiplier applied after a pinch/double-tap settles.
    var detailScale by remember(file, pageIndex) { mutableFloatStateOf(1f) }

    val bitmap = remember(file, pageIndex, detailScale) {
        runCatching {
            val page = renderer.openPage(pageIndex)
            try {
                val targetWidth =
                    (context.resources.displayMetrics.widthPixels * 1.5f * detailScale)
                        .coerceAtLeast(1f)
                val scale = (targetWidth / page.width).coerceIn(1f, 6f)
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
            modifier = Modifier.fillMaxWidth(),
            onZoomSettled = { zoom ->
                val capped = zoom.coerceIn(1f, MAX_DETAIL_ZOOM)
                // Ignore small settle jitter to avoid re-render churn.
                if (abs(capped - detailScale) > 0.15f) detailScale = capped
            }
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
 * One rendered page with pinch-zoom, ONE-FINGER pan (when zoomed) and
 * double-tap 1x <-> 2.5x toggle. Single-finger swipe still scrolls the
 * page list when zoomed out. Zoom clamps to 1x..5x; pan clamps to image
 * boundaries and resets when zoom returns to 1x.
 *
 * Pan/zoom math anchors the content point under the finger centroid each
 * frame, so the content under your fingers stays fixed while pinching and
 * follows the centroid 1:1 while panning — with one or two fingers.
 *
 * [onZoomSettled] fires after any gesture ends so the page can re-render
 * its bitmap at the settled zoom for crisp text. When a fresh bitmap
 * arrives the transform resets to 1x (the bitmap already carries the
 * target resolution).
 */
@Composable
private fun ZoomablePageImage(
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    modifier: Modifier = Modifier,
    onZoomSettled: (Float) -> Unit = {}
) {
    // Keyed by bitmap: a re-rendered (higher-res) bitmap arrives as a new
    // object, so the transform resets — display it at its native scale.
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    // Double-tap tracking — persists across awaitEachGesture iterations.
    var pendingTapX by remember { mutableFloatStateOf(0f) }
    var pendingTapY by remember { mutableFloatStateOf(0f) }
    var pendingTapTime by remember { mutableLongStateOf(0L) }
    var hasPendingTap by remember { mutableStateOf(false) }

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
                    // ── Phase 1: detect tap vs drag ──
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    val downX = down.position.x
                    val downY = down.position.y
                    var moved = false

                    // Track until all fingers lift.
                    do {
                        val event = awaitPointerEvent()
                        for (c in event.changes) {
                            if (c.pressed) {
                                val dx = c.position.x - c.previousPosition.x
                                val dy = c.position.y - c.previousPosition.y
                                if (dx * dx + dy * dy > 64f) moved = true
                            }
                        }
                        if (event.changes.all { !it.pressed }) break
                    } while (true)

                    val upTime = System.currentTimeMillis()

                    if (!moved) {
                        // ── Tap detected — check for double-tap ──
                        val slop = viewSize.width * 0.15f
                        if (hasPendingTap) {
                            val dx = downX - pendingTapX
                            val dy = downY - pendingTapY
                            val dt = upTime - pendingTapTime
                            if (dx * dx + dy * dy < slop * slop && dt < 350) {
                                // Double-tap confirmed — toggle zoom.
                                hasPendingTap = false
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    val s = 2.5f
                                    val cx = downX - viewSize.width / 2f
                                    val cy = downY - viewSize.height / 2f
                                    val maxPanX = viewSize.width * (s - 1f) / 2f
                                    val maxPanY = viewSize.height * (s - 1f) / 2f
                                    // Zoom toward the tap point, clamped so
                                    // the page edge never crosses center.
                                    offset = Offset(
                                        (cx * (1f - s)).coerceIn(-maxPanX, maxPanX),
                                        (cy * (1f - s)).coerceIn(-maxPanY, maxPanY)
                                    )
                                    scale = s
                                }
                                onZoomSettled(scale)
                                return@awaitEachGesture
                            }
                        }
                        // First tap — record and wait for the next gesture.
                        pendingTapX = downX
                        pendingTapY = downY
                        pendingTapTime = upTime
                        hasPendingTap = true
                        return@awaitEachGesture
                    }

                    // Movement happened — not a double-tap.
                    hasPendingTap = false

                    // ── Phase 2: pinch-zoom / pan gesture ──
                    // First finger is already down. Continue tracking.
                    var handled = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }

                        // Fully zoomed out + single finger → hand control
                        // back to the LazyColumn for list scrolling.
                        if (!handled && pressed.size < 2 && scale <= 1f) break
                        handled = true
                        event.changes.forEach { it.consume() }

                        // Zoom: distance ratio between the first two fingers.
                        val zoomChange =
                            if (pressed.size >= 2) {
                                val a = pressed[0].position
                                val b = pressed[1].position
                                val pa = pressed[0].previousPosition
                                val pb = pressed[1].previousPosition
                                val cur = (a - b).getDistance()
                                val prev = (pa - pb).getDistance()
                                if (prev > 0f) cur / prev else 1f
                            } else 1f

                        if (pressed.isNotEmpty()) {
                            // Centroid of ALL pressed fingers — with a single
                            // finger this is just that finger, so one-finger
                            // panning works while zoomed.
                            val sum = pressed.fold(Offset.Zero) { acc, c ->
                                acc + c.position
                            }
                            val centroid = Offset(
                                sum.x / pressed.size,
                                sum.y / pressed.size
                            )

                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            if (newScale > 1f) {
                                val pivotX = viewSize.width / 2f
                                val pivotY = viewSize.height / 2f
                                // Centroid relative to the composable center.
                                val relX = centroid.x - pivotX
                                val relY = centroid.y - pivotY
                                // Anchor the content point under the centroid:
                                // it stays fixed under the fingers while
                                // pinching and follows them 1:1 while panning.
                                val newTX =
                                    relX - newScale * (relX - offset.x) / scale
                                val newTY =
                                    relY - newScale * (relY - offset.y) / scale
                                // Clamp to image boundaries.
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
                        }
                    } while (event.changes.any { it.pressed })

                    if (handled) onZoomSettled(scale)
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
