package com.nimelssa.vault.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Scans Google Drive links (files or folders) using the Drive API v3
 * with a simple API key (no OAuth required for public resources).
 *
 * ## Prerequisites (Google Cloud Console)
 * 1. Enable **Google Drive API** for your project
 * 2. Create an API key restricted to your Android app `com.nimelssa.vault`
 * 3. Set the API key in `res/values/secrets.xml` as `drive_api_key`
 *
 * ## Folder Requirements
 * The Drive folder must be set to **"Anyone with the link can view"**
 * for the API key to work without OAuth.
 */
object DriveScanner {
    private const val TAG = "DriveScanner"
    private const val API_BASE = "https://www.googleapis.com/drive/v3"
    private const val FIELDS = "files(id,name,mimeType,webViewLink,size)"

    private var apiKey: String = ""

    /** Set the API key at app startup (called from NimelssaApp). */
    fun init(key: String) {
        apiKey = key
    }

    /** Result of a Drive scan. */
    data class ScanResult(
        val isFolder: Boolean = false,
        val folderName: String = "",
        val files: List<DriveFileInfo> = emptyList(),
        val error: String = ""
    )

    data class DriveFileInfo(
        val id: String,
        val name: String,
        val mimeType: String,
        val webViewLink: String?,
        val size: Long? = null,
        /** Folder hierarchy this file lives in, e.g. "200 level / MLS 201" */
        val path: String = ""
    )

    /**
     * Scan a Drive link and return all files found.
     * Handles both single-file links and folder links.
     */
    suspend fun scanLink(link: String): ScanResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext ScanResult(error = "API key not configured. Contact admin.")
        }

        try {
            val extractedId = extractFileOrFolderId(link)
            if (extractedId == null) {
                return@withContext ScanResult(
                    error = "Could not parse Drive link. Ensure it's a valid Google Drive URL."
                )
            }

            // First, get metadata to see if this is a file or folder
            val meta = getMetadata(extractedId)
            if (meta.error != null) {
                val msg = when {
                    meta.error.contains("notFound", ignoreCase = true) ->
                        "File/folder not found or not publicly accessible. Set sharing to 'Anyone with the link'."
                    meta.error.contains("key", ignoreCase = true) ||
                    meta.error.contains("access", ignoreCase = true) ||
                    meta.error.contains("403", ignoreCase = true) ->
                        "Google Drive API error. Ensure Drive API is ENABLED in Google Cloud Console."
                    else -> meta.error
                }
                return@withContext ScanResult(error = msg)
            }

            if (meta.isFolder) {
                val files = walkFolder(extractedId, meta.name)
                return@withContext ScanResult(
                    isFolder = true,
                    folderName = meta.name,
                    files = files
                )
            } else {
                return@withContext ScanResult(
                    isFolder = false,
                    folderName = meta.name,
                    files = listOf(
                        DriveFileInfo(
                            id = extractedId,
                            name = meta.name,
                            mimeType = meta.mimeType,
                            webViewLink = meta.webViewLink,
                            path = meta.name
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed", e)
            return@withContext ScanResult(error = "Scan failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    /**
     * Get metadata for a file or folder by its Drive ID.
     */
    private fun getMetadata(id: String): MetadataResult {
        val urlStr = buildUrl("$API_BASE/files/$id", mapOf(
            "key" to apiKey,
            "fields" to "id,name,mimeType,webViewLink,size"
        ))
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        try {
            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val errorBody = readStream(conn.errorStream ?: conn.inputStream)
                val errorMsg = parseError(errorBody)
                return MetadataResult(error = "[HTTP $responseCode] $errorMsg")
            }

            val json = JSONObject(readStream(conn.inputStream))
            val mimeType = json.optString("mimeType", "")
            val isFolder = mimeType == "application/vnd.google-apps.folder"

            return MetadataResult(
                id = json.optString("id", id),
                name = json.optString("name", "Unnamed"),
                mimeType = mimeType,
                webViewLink = json.optString("webViewLink", null),
                isFolder = isFolder
            )
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Walk the folder tree **dynamically** — no hardcoded level limit,
     * no assumptions about structure. Uses folder NAMES to build a path
     * for each file, which the [FilenameParser] then uses to extract
     * level, course code, and resource type.
     *
     * Example path: "NIMELSSA Hub / 200 level / MLS 201 / histo_note.pdf"
     *                ↑ root        ↑ level    ↑ course   ↑ file
     */
    private fun walkFolder(
        folderId: String,
        currentPath: String,
        depth: Int = 0
    ): List<DriveFileInfo> {
        // Safety cap — prevents runaway on cyclic structures
        if (depth > 20) return emptyList()

        val files = mutableListOf<DriveFileInfo>()
        val subFolders = mutableListOf<Pair<String, String>>() // (folderId, folderName)
        var pageToken: String? = null

        do {
            val query = "'$folderId' in parents"

            val params = mutableMapOf(
                "q" to query,
                "fields" to "nextPageToken,$FIELDS",
                "key" to apiKey
            )
            if (pageToken != null) {
                params["pageToken"] = pageToken!!
            }

            val urlStr = buildUrl("$API_BASE/files", params)
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            try {
                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    val errorBody = readStream(conn.errorStream ?: conn.inputStream)
                    Log.e(TAG, "walkFolder($folderId) HTTP $responseCode: $errorBody")
                    break
                }

                val json = JSONObject(readStream(conn.inputStream))
                val items = json.optJSONArray("files") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val mimeType = item.optString("mimeType", "")
                    val isFolder = mimeType == "application/vnd.google-apps.folder"
                    val fileId = item.optString("id", "")
                    val fileName = item.optString("name", "Unnamed")

                    if (isFolder) {
                        subFolders.add(fileId to fileName)
                    } else {
                        files.add(
                            DriveFileInfo(
                                id = fileId,
                                name = fileName,
                                mimeType = mimeType,
                                webViewLink = item.optString("webViewLink", null),
                                size = if (item.has("size")) item.optLong("size", 0) else null,
                                path = currentPath
                            )
                        )
                    }
                }

                pageToken = json.optString("nextPageToken", null)
                if (pageToken?.isBlank() == true) pageToken = null
            } finally {
                conn.disconnect()
            }
        } while (pageToken != null)

        // Recurse into subfolders, building the path: "parent / child"
        for ((subId, subName) in subFolders) {
            val childPath = "$currentPath / $subName"
            files.addAll(walkFolder(subId, childPath, depth + 1))
        }

        return files
    }

    /**
     * Build a properly URL-encoded query string from a base URL and parameters.
     * This is critical — Drive API queries contain quotes and special chars
     * that MUST be percent-encoded.
     */
    private fun buildUrl(base: String, params: Map<String, String>): String {
        if (params.isEmpty()) return base
        val queryString = params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        return "$base?$queryString"
    }

    // ── URL / ID parsing ──

    /**
     * Extract file or folder ID from various Google Drive URL formats:
     * - https://drive.google.com/file/d/FILE_ID/view
     * - https://drive.google.com/drive/folders/FOLDER_ID
     * - https://drive.google.com/drive/mobile/folders/FOLDER_ID
     * - https://drive.google.com/open?id=ID
     * - https://docs.google.com/document/d/DOC_ID
     * - Just a raw ID (10+ alphanumeric chars)
     */
    fun extractFileOrFolderId(link: String): String? {
        val trimmed = link.trim()

        val patterns = listOf(
            Regex("""/file/d/([a-zA-Z0-9_-]+)"""),
            Regex("""/folders/([a-zA-Z0-9_-]+)"""),
            Regex("""/mobile/folders/([a-zA-Z0-9_-]+)"""),
            Regex("""\?id=([a-zA-Z0-9_-]+)"""),
            Regex("""/document/d/([a-zA-Z0-9_-]+)"""),
            Regex("""/spreadsheets/d/([a-zA-Z0-9_-]+)"""),
            Regex("""/presentation/d/([a-zA-Z0-9_-]+)"""),
        )

        for (pattern in patterns) {
            pattern.find(trimmed)?.let {
                return it.groupValues[1]
            }
        }

        // Raw ID fallback
        if (trimmed.matches(Regex("^[a-zA-Z0-9_-]{10,}$"))) {
            return trimmed
        }

        return null
    }

    /**
     * Build a human-readable WebView URL for a file or folder.
     */
    fun buildDirectUrl(fileId: String, isFolder: Boolean = false): String {
        return if (isFolder) {
            "https://drive.google.com/drive/folders/$fileId"
        } else {
            "https://drive.google.com/file/d/$fileId/preview"
        }
    }

    // ── Internal models / helpers ──

    private data class MetadataResult(
        val id: String = "",
        val name: String = "",
        val mimeType: String = "",
        val webViewLink: String? = null,
        val isFolder: Boolean = false,
        val error: String? = null
    )

    private fun readStream(stream: java.io.InputStream): String {
        val reader = BufferedReader(InputStreamReader(stream))
        return reader.readText()
    }

    private fun parseError(body: String): String {
        return try {
            val json = JSONObject(body)
            val error = json.optJSONObject("error")
            val code = error?.optInt("code", 0) ?: 0
            val msg = error?.optString("message", "Unknown API error") ?: "Unknown API error"
            val status = error?.optString("status", "") ?: ""

            when {
                status == "PERMISSION_DENIED" || code == 403 ->
                    "Permission denied. Enable Google Drive API in Google Cloud Console and make sure the folder is public."
                status == "NOT_FOUND" || code == 404 ->
                    "File/folder not found. Check that the link is correct and sharing is set to 'Anyone with the link'."
                status == "QUOTA_EXCEEDED" || code == 429 ->
                    "API rate limit exceeded. Try again later."
                else -> "$msg (HTTP $code)"
            }
        } catch (_: Exception) {
            "Failed to access Drive. Check that the folder is publicly accessible."
        }
    }
}
