package com.nimelssa.vault.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Tiny disk cache of course + resource metadata, so offline cold starts are
 * self-sufficient even when Firestore's local persistence cache misses.
 *
 * Two plaintext JSON files under `filesDir/offline_cache/`:
 *  - courses.json   → course list + alias table (variant → canonical)
 *  - resources.json → full resource map keyed by canonical course code (+ "__LEVEL__")
 *
 * Written on every successful Firestore sync; read only when a fetch fails.
 * A cache snapshot reflects the last successful sync — post-sync mutations
 * (add/remove) are not backfilled here by design.
 *
 * No credentials or tokens are ever stored — metadata only.
 */
object LocalCache {
    private const val TAG = "LocalCache"
    private const val DIR = "offline_cache"
    private const val COURSES_FILE = "courses.json"
    private const val RESOURCES_FILE = "resources.json"
    private const val MAX_CACHE_META_BYTES = 8L * 1024 * 1024 // sanity cap: 8 MB

    private val appContext: Context
        get() = FirebaseApp.getInstance().applicationContext

    private fun dir(): File = File(appContext.filesDir, DIR).also { it.mkdirs() }

    // ── Courses + aliases ──────────────────────────────────────────────

    /** Persist the last-known course list and alias table (snapshot). */
    fun cacheCourses(courses: List<Course>, aliases: Map<String, String>) {
        try {
            val arr = JSONArray()
            for (c in courses) {
                arr.put(
                    JSONObject()
                        .put("code", c.code)
                        .put("name", c.name)
                        .put("category", c.category)
                        .put("level", c.level)
                        .put("semester", c.semester)
                )
            }
            val aliasObj = JSONObject()
            for ((v, c) in aliases) aliasObj.put(v, c)

            val root = JSONObject().put("courses", arr).put("aliases", aliasObj)
            File(dir(), COURSES_FILE).writeText(root.toString())
            Log.d(TAG, "Cached ${courses.size} courses + ${aliases.size} aliases")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache courses", e)
        }
    }

    /** Read the cached courses + aliases, or null when absent/corrupt. */
    fun readCourses(): Pair<List<Course>, Map<String, String>>? {
        return runCatching {
            val file = File(dir(), COURSES_FILE)
            if (!file.exists() || file.length() !in 1..MAX_CACHE_META_BYTES) return null
            val root = JSONObject(file.readText())
            val aliases = mutableMapOf<String, String>()
            val aliasObj = root.optJSONObject("aliases")
            if (aliasObj != null) {
                val iter = aliasObj.keys()
                while (iter.hasNext()) {
                    val k = iter.next()
                    aliases[k] = aliasObj.getString(k)
                }
            }
            val arr = root.getJSONArray("courses")
            val courses = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Course(
                    code = o.optString("code"),
                    name = o.optString("name"),
                    category = o.optString("category"),
                    level = o.optString("level"),
                    semester = o.optInt("semester", 1)
                )
            }.filter { it.code.isNotBlank() }
            courses to aliases
        }.getOrElse { e ->
            Log.e(TAG, "Failed to read cached courses", e)
            null
        }
    }

    // ── Resources ──────────────────────────────────────────────────────

    /** Persist the resource map snapshot (key → resource list). */
    fun cacheResources(map: Map<String, List<Resource>>) {
        try {
            val root = JSONObject()
            for ((key, list) in map) {
                val arr = JSONArray()
                for (r in list) {
                    arr.put(
                        JSONObject()
                            .put("id", r.id)
                            .put("courseCode", r.courseCode)
                            .put("resourceType", r.resourceType)
                            .put("level", r.level)
                            .put("masterUrl", r.masterUrl)
                            .put("fileId", r.fileId)
                            .put("md5Checksum", r.md5Checksum)
                            .put("label", r.label)
                            .put("fileName", r.fileName)
                            .put("submittedBy", r.submittedBy)
                            .put("notes", r.notes)
                            .put("approvedBy", r.approvedBy)
                            .put("approvedAt", r.approvedAt)
                    )
                }
                root.put(key, arr)
            }
            File(dir(), RESOURCES_FILE).writeText(root.toString())
            Log.d(TAG, "Cached resources: ${map.values.sumOf { it.size }} in ${map.size} bucket(s)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache resources", e)
        }
    }

    /** Read the cached resource map, or null when absent/corrupt. */
    fun readResources(): Map<String, List<Resource>>? {
        return runCatching {
            val file = File(dir(), RESOURCES_FILE)
            if (!file.exists() || file.length() !in 1..MAX_CACHE_META_BYTES) return null
            val root = JSONObject(file.readText())
            val result = mutableMapOf<String, List<Resource>>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val arr = root.getJSONArray(key)
                val list = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Resource(
                        id = o.optString("id"),
                        courseCode = o.optString("courseCode"),
                        resourceType = o.optString("resourceType", "LN"),
                        level = o.optString("level"),
                        masterUrl = o.optString("masterUrl"),
                        fileId = o.optString("fileId"),
                        md5Checksum = o.optString("md5Checksum"),
                        label = o.optString("label"),
                        fileName = o.optString("fileName"),
                        submittedBy = o.optString("submittedBy"),
                        notes = o.optString("notes"),
                        approvedBy = o.optString("approvedBy"),
                        approvedAt = o.optLong("approvedAt", 0L)
                    )
                }
                result[key] = list
            }
            result
        }.getOrElse { e ->
            Log.e(TAG, "Failed to read cached resources", e)
            null
        }
    }
}