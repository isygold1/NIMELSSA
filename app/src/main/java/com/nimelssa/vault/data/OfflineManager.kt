package com.nimelssa.vault.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.nimelssa.vault.data.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages downloading course resource files to the device's internal cache
 * for offline viewing. Uses an LRU eviction policy to keep total storage
 * under [MAX_CACHE_BYTES] so you can save hundreds of courses without
 * filling up the device.
 *
 * Files stored in: `{cacheDir}/offline/{courseCode}/`
 *
 * Eviction: when a new save would exceed the limit, the least recently
 * accessed course(s) are automatically removed until space is freed.
 */
object OfflineManager {
    private const val TAG = "OfflineManager"
    private const val PREFS_NAME = "offline_courses"
    private const val KEY_OFFLINE_CODES = "saved_codes"
    private const val KEY_ACCESS_TIMES = "access_times"

    /** Maximum disk space for offline course files (300 MB). */
    private const val MAX_CACHE_BYTES = 300L * 1024 * 1024

    private var prefs: android.content.SharedPreferences? = null
    private var cacheBase: File? = null

    /** Initialise with a Context (call from Application.onCreate()) */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cacheBase = File(context.cacheDir, "offline").also { it.mkdirs() }
        Log.d(TAG, "Initialised. ${getSavedCodes().size} courses saved.")
    }

    // ── Public API ─────────────────────────────────────────────────────

    /** Returns the set of course codes the user has saved for offline. */
    fun getSavedCodes(): Set<String> =
        prefs?.getStringSet(KEY_OFFLINE_CODES, emptySet()) ?: emptySet()

    /**
     * Downloads all resources for a course, then enforces the cache limit
     * (evicts oldest courses if over limit).
     */
    suspend fun saveOfflineResources(courseCode: String, resources: List<Resource>) {
        downloadCourseResources(courseCode, resources)
        touchAccessTime(courseCode)

        val codes = getSavedCodes().toMutableSet()
        codes.add(courseCode)
        prefs?.edit()?.putStringSet(KEY_OFFLINE_CODES, codes)?.apply()

        // Evict if over the limit
        enforceCacheLimit()

        Log.d(TAG, "Saved offline: $courseCode (${resources.size} resources)")
    }

    /**
     * Removes a course from offline storage — deletes its files and
     * clears the saved flag.
     */
    suspend fun removeOffline(courseCode: String) {
        val dir = File(cacheBase, courseCode)
        if (dir.exists()) dir.deleteRecursively()

        val codes = getSavedCodes().toMutableSet()
        codes.remove(courseCode)
        val times = getAccessTimes().toMutableMap()
        times.remove(courseCode)
        prefs?.edit()
            ?.putStringSet(KEY_OFFLINE_CODES, codes)
            ?.putStringSet(KEY_ACCESS_TIMES, times.entries.joinToString("\n") { "${it.key}=${it.value}" }
                .takeIf { it.isNotEmpty() }?.split("\n")?.toSet() ?: emptySet())
            ?.apply()

        Log.d(TAG, "Removed offline: $courseCode")
    }

    /**
     * Returns the local file path for a saved resource, or null if not
     * downloaded yet. Also updates the access timestamp (LRU).
     */
    fun getLocalFile(courseCode: String, resourceType: String): String? {
        val dir = File(cacheBase, courseCode)
        if (!dir.exists()) return null

        // Record this access for LRU
        touchAccessTime(courseCode)

        val prefix = when (resourceType.lowercase()) {
            "ln", "lecturenotes" -> "lecture_notes"
            "pq", "pastquestions" -> "past_questions"
            "tb", "textbook" -> "textbook"
            else -> "resource"
        }
        return dir.listFiles { f -> f.name.startsWith(prefix) }
            ?.firstOrNull()?.absolutePath
    }

    /** Total bytes currently used by all offline course files. */
    fun getUsedBytes(): Long {
        val base = cacheBase ?: return 0L
        if (!base.exists()) return 0L
        return base.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** Max cache size in bytes (300 MB). */
    fun getMaxBytes(): Long = MAX_CACHE_BYTES

    /** Deletes all offline course files and resets all saved flags. */
    fun clearAll() {
        cacheBase?.deleteRecursively()
        cacheBase?.mkdirs()
        prefs?.edit()
            ?.putStringSet(KEY_OFFLINE_CODES, emptySet())
            ?.putStringSet(KEY_ACCESS_TIMES, emptySet())
            ?.apply()
        Log.d(TAG, "Cleared all offline data")
    }

    /**
     * Checks whether the device currently has internet access.
     */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ── Download helpers ───────────────────────────────────────────────

    private suspend fun downloadCourseResources(courseCode: String, resources: List<Resource>) =
        withContext(Dispatchers.IO) {
            val dir = File(cacheBase, courseCode).also { it.mkdirs() }
            for (resource in resources) {
                if (resource.masterUrl.isBlank()) continue
                val prefix = when (resource.resourceType) {
                    "LN" -> "lecture_notes"
                    "PQ" -> "past_questions"
                    "TB" -> "textbook"
                    else -> "resource"
                }
                try {
                    val file = File(dir, "$prefix${getExtension(resource.masterUrl)}")
                    if (!file.exists()) {
                        downloadFile(resource.masterUrl, file)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download ${resource.resourceType} for $courseCode", e)
                }
            }
        }

    private fun downloadFile(urlStr: String, dest: File) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.instanceFollowRedirects = true
        try {
            conn.connect()
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            Log.d(TAG, "Downloaded: ${dest.name} (${dest.length()} bytes)")
        } finally {
            conn.disconnect()
        }
    }

    private fun getExtension(url: String): String {
        val path = URL(url).path
        val dot = path.lastIndexOf('.')
        if (dot >= 0) {
            val ext = path.substring(dot)
            if (ext.length in 2..6) return ext
        }
        return when {
            url.contains("drive.google.com") -> ".html"
            else -> ".pdf"
        }
    }

    // ── Cache limit enforcement (LRU eviction) ────────────────────────

    /**
     * Checks total cache size. If it exceeds [MAX_CACHE_BYTES], removes
     * the least recently accessed course(s) until under the limit.
     */
    private fun enforceCacheLimit() {
        val base = cacheBase ?: return
        if (!base.exists()) return

        // Calculate current size
        var totalBytes = base.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        if (totalBytes <= MAX_CACHE_BYTES) return

        Log.w(TAG, "Cache ${bytesToMb(totalBytes)}MB exceeds limit ${bytesToMb(MAX_CACHE_BYTES)}MB — evicting oldest")

        val accessTimes = getAccessTimes()
        val savedCodes = getSavedCodes().toMutableSet()

        // Sort courses by last-access time (oldest first)
        val sorted = savedCodes.sortedBy { accessTimes[it] ?: 0L }

        for (courseCode in sorted) {
            if (totalBytes <= MAX_CACHE_BYTES) break

            val dir = File(base, courseCode)
            val removed = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            dir.deleteRecursively()
            savedCodes.remove(courseCode)
            totalBytes -= removed
            Log.d(TAG, "Evicted $courseCode (freed ${bytesToMb(removed)}MB)")
        }

        // Persist updated set
        prefs?.edit()
            ?.putStringSet(KEY_OFFLINE_CODES, savedCodes)
            ?.apply()

        Log.i(TAG, "Cache now ${bytesToMb(totalBytes)}MB / ${bytesToMb(MAX_CACHE_BYTES)}MB")
    }

    /** Record or update the access timestamp for a course (used for LRU). */
    private fun touchAccessTime(courseCode: String) {
        val times = getAccessTimes().toMutableMap()
        times[courseCode] = System.currentTimeMillis()
        prefs?.edit()
            ?.putStringSet(KEY_ACCESS_TIMES,
                times.entries.joinToString("\n") { "${it.key}=${it.value}" }
                    .split("\n").toSet())
            ?.apply()
    }

    /** Read the stored access-time map. */
    private fun getAccessTimes(): Map<String, Long> {
        val raw = prefs?.getStringSet(KEY_ACCESS_TIMES, emptySet()) ?: emptySet()
        return raw.mapNotNull { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) parts[0] to (parts[1].toLongOrNull() ?: 0L) else null
        }.toMap()
    }

    private fun bytesToMb(bytes: Long): String = String.format("%.1f", bytes / (1024.0 * 1024.0))
}
