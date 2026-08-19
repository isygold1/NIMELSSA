package com.nimelssa.vault.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.nimelssa.vault.data.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
    private const val KEY_ACCESS_TIMES = "access_times_json"
    private const val KEY_USER_FOLDER_URI = "user_folder_uri"
    private const val KEY_USER_FOLDER_NAME = "user_folder_name"

    /** Maximum disk space for offline course files (300 MB). */
    private const val MAX_CACHE_BYTES = 300L * 1024 * 1024

    private var prefs: android.content.SharedPreferences? = null
    private var cacheBase: File? = null
    private var appContext: Context? = null

    /** Initialise with a Context (call from Application.onCreate()) */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cacheBase = File(context.cacheDir, "offline").also { it.mkdirs() }
        appContext = context.applicationContext
        Log.d(TAG, "Initialised. ${getSavedCodes().size} courses saved.")
    }

    // ── Public API ─────────────────────────────────────────────────────

    /** Returns the set of course codes the user has saved for offline. */
    fun getSavedCodes(): Set<String> =
        prefs?.getStringSet(KEY_OFFLINE_CODES, emptySet()) ?: emptySet()

    // ── User-chosen download folder (SAF) ─────────────────────────────

    /** Uri string of the folder the user picked in Settings, or null (default = app cache). */
    fun getUserFolderUri(): String? = prefs?.getString(KEY_USER_FOLDER_URI, null)

    /** Display name of the user-picked folder, or null when using the default. */
    fun getUserFolderName(): String? = prefs?.getString(KEY_USER_FOLDER_NAME, null)

    /** Persist the folder picked via ACTION_OPEN_DOCUMENT_TREE. */
    fun setUserFolder(uri: String, displayName: String) {
        prefs?.edit()
            ?.putString(KEY_USER_FOLDER_URI, uri)
            ?.putString(KEY_USER_FOLDER_NAME, displayName)
            ?.apply()
    }

    /** Revert to the default internal storage location. */
    fun clearUserFolder() {
        prefs?.edit()
            ?.remove(KEY_USER_FOLDER_URI)
            ?.remove(KEY_USER_FOLDER_NAME)
            ?.apply()
    }

    /**
     * Saves a single resource for a course (per-file download button).
     * The internal copy lands in the offline store; if the user picked a
     * download folder in Settings, a mirror copy is written there too.
     */
    suspend fun saveSingleResource(courseCode: String, resource: Resource) =
        withContext(Dispatchers.IO) {
            val dir = File(cacheBase, courseCode).also { it.mkdirs() }
            try {
                saveOneInto(dir, resource)
                Log.d(TAG, "Saved single resource for $courseCode")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save single resource for $courseCode", e)
            }
            touchAccessTime(courseCode)
            val codes = getSavedCodes().toMutableSet()
            codes.add(courseCode)
            prefs?.edit()?.putStringSet(KEY_OFFLINE_CODES, codes)?.apply()
            enforceCacheLimit()
        }

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
            ?.putString(KEY_ACCESS_TIMES, mapToJson(times))
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
            ?.putString(KEY_ACCESS_TIMES, null)
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
                try {
                    saveOneInto(dir, resource)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download ${resource.resourceType} for $courseCode", e)
                }
            }
        }

    /** Downloads one resource into [dir] if not already present, then mirrors to the user folder. */
    private fun saveOneInto(dir: File, resource: Resource) {
        if (resource.masterUrl.isBlank()) return
        val prefix = when (resource.resourceType) {
            "LN" -> "lecture_notes"
            "PQ" -> "past_questions"
            "TB" -> "textbook"
            else -> "resource"
        }
        val file = File(dir, "$prefix${getExtension(resource.masterUrl)}")
        if (!file.exists()) {
            downloadFile(resource.masterUrl, file)
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
            mirrorToUserFolder(dest)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Copies a freshly downloaded file into the folder the user chose in
     * Settings (SAF tree Uri). Purely a user-visible mirror — the app still
     * reads from its internal store, so no permission prompts are needed.
     */
    private fun mirrorToUserFolder(src: File) {
        val context = appContext ?: return
        val uriStr = getUserFolderUri() ?: return
        if (uriStr.isBlank()) return
        runCatching {
            val treeUri = Uri.parse(uriStr)
            val docs = DocumentFile.fromTreeUri(context, treeUri) ?: return
            val mime = when (src.extension.lowercase()) {
                "pdf" -> "application/pdf"
                "html", "htm" -> "text/html"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                else -> "application/octet-stream"
            }
            val target = docs.findFile(src.name) ?: docs.createFile(mime, src.name) ?: return
            context.contentResolver.openOutputStream(target.uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            }
        }.onFailure { Log.e(TAG, "Failed to mirror ${src.name} to user folder", it) }
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
            ?.putString(KEY_ACCESS_TIMES, mapToJson(times))
            ?.apply()
    }

    /** Read the stored access-time map. */
    private fun getAccessTimes(): Map<String, Long> {
        val raw = prefs?.getString(KEY_ACCESS_TIMES, null) ?: return emptyMap()
        return jsonToMap(raw)
    }

    /** Serialise a map to a JSON string. */
    private fun mapToJson(map: Map<String, Long>): String {
        return JSONObject(map as Map<*, *>).toString()
    }

    /** Deserialise a JSON string back to a map. */
    private fun jsonToMap(json: String): Map<String, Long> {
        val obj = JSONObject(json)
        val map = mutableMapOf<String, Long>()
        for (key in obj.keys()) {
            map[key] = obj.optLong(key, 0L)
        }
        return map
    }

    private fun bytesToMb(bytes: Long): String = String.format("%.1f", bytes / (1024.0 * 1024.0))
}
