package com.nimelssa.vault.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages downloading course resource files (PDFs, documents) to the device's
 * internal cache so they can be viewed offline. Tracks which courses have
 * been saved via SharedPreferences.
 *
 * Files are stored in: `{cacheDir}/offline/{courseCode}/`
 */
object OfflineManager {
    private const val TAG = "OfflineManager"
    private const val PREFS_NAME = "offline_courses"
    private const val KEY_OFFLINE_CODES = "saved_codes"

    private var prefs: android.content.SharedPreferences? = null
    private var cacheBase: File? = null

    /** Initialise with a Context (call from Application.onCreate()) */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cacheBase = File(context.cacheDir, "offline").also { it.mkdirs() }

        // Restore offline flags into CourseRepository
        val codes = getSavedCodes()
        for (code in codes) {
            CourseRepository.mergeOfflineFlag(code, true)
        }
        Log.d(TAG, "Initialised. ${codes.size} courses marked offline.")
    }

    /** Returns the list of course codes the user has saved for offline. */
    fun getSavedCodes(): Set<String> {
        return prefs?.getStringSet(KEY_OFFLINE_CODES, emptySet()) ?: emptySet()
    }

    /**
     * Downloads all resource files for a course to local cache.
     * Returns true if any file was downloaded.
     */
    suspend fun downloadCourseResources(course: Course): Boolean =
        withContext(Dispatchers.IO) {
            val dir = File(cacheBase, course.code).also { it.mkdirs() }
            var downloaded = false

            if (course.lectureNotesUrl.isNotBlank()) {
                try {
                    val file = File(dir, "lecture_notes${getExtension(course.lectureNotesUrl)}")
                    if (!file.exists()) {
                        downloadFile(course.lectureNotesUrl, file)
                        downloaded = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download lecture notes for ${course.code}", e)
                }
            }

            if (course.pastQuestionsUrl.isNotBlank()) {
                try {
                    val file = File(dir, "past_questions${getExtension(course.pastQuestionsUrl)}")
                    if (!file.exists()) {
                        downloadFile(course.pastQuestionsUrl, file)
                        downloaded = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download past questions for ${course.code}", e)
                }
            }

            if (course.textbookUrl.isNotBlank()) {
                try {
                    val file = File(dir, "textbook${getExtension(course.textbookUrl)}")
                    if (!file.exists()) {
                        downloadFile(course.textbookUrl, file)
                        downloaded = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download textbook for ${course.code}", e)
                }
            }

            downloaded
        }

    /**
     * Returns the local file path for a resource, or null if not downloaded.
     */
    fun getLocalFile(courseCode: String, resourceType: String): String? {
        val dir = File(cacheBase, courseCode)
        if (!dir.exists()) return null

        val prefix = when (resourceType) {
            "lectureNotes" -> "lecture_notes"
            "pastQuestions" -> "past_questions"
            "textbook" -> "textbook"
            else -> return null
        }

        val files = dir.listFiles { f -> f.name.startsWith(prefix) }
        return files?.firstOrNull()?.absolutePath
    }

    /**
     * Marks a course as saved offline: downloads files and persists the flag.
     */
    suspend fun saveOffline(course: Course) {
        // Download the actual files
        downloadCourseResources(course)

        // Persist the flag
        val codes = getSavedCodes().toMutableSet()
        codes.add(course.code)
        prefs?.edit()?.putStringSet(KEY_OFFLINE_CODES, codes)?.apply()

        // Update in-memory
        CourseRepository.mergeOfflineFlag(course.code, true)
        Log.d(TAG, "Saved offline: ${course.code}")
    }

    /**
     * Removes a course from offline storage.
     */
    suspend fun removeOffline(courseCode: String) {
        // Delete downloaded files
        val dir = File(cacheBase, courseCode)
        if (dir.exists()) {
            dir.deleteRecursively()
        }

        // Remove from prefs
        val codes = getSavedCodes().toMutableSet()
        codes.remove(courseCode)
        prefs?.edit()?.putStringSet(KEY_OFFLINE_CODES, codes)?.apply()

        // Update in-memory
        CourseRepository.mergeOfflineFlag(courseCode, false)
        Log.d(TAG, "Removed offline: $courseCode")
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

    // ── Private helpers ─────────────────────────────────────────────────

    private fun downloadFile(urlStr: String, dest: File) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.instanceFollowRedirects = true

        try {
            conn.connect()
            val inputStream = conn.inputStream
            FileOutputStream(dest).use { output ->
                inputStream.copyTo(output)
            }
            Log.d(TAG, "Downloaded: ${dest.name} (${dest.length()} bytes)")
        } finally {
            conn.disconnect()
        }
    }

    private fun getExtension(url: String): String {
        // Try to extract extension from URL path
        val path = URL(url).path
        val dot = path.lastIndexOf('.')
        if (dot >= 0) {
            val ext = path.substring(dot)
            if (ext.length in 2..6) return ext
        }
        // Default based on common patterns
        return when {
            url.contains("drive.google.com") -> ".html"
            else -> ".pdf"
        }
    }

    /**
     * Deletes all locally cached offline files.
     */
    fun clearAll() {
        cacheBase?.deleteRecursively()
        cacheBase?.mkdirs()
        prefs?.edit()?.putStringSet(KEY_OFFLINE_CODES, emptySet())?.apply()
        Log.d(TAG, "Cleared all offline data")
    }
}
