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

/**
 * Scans Google Drive links (files or folders) using the Drive API v3
 * with a simple API key (no OAuth required for public resources).
 *
 * The folder must be set to "Anyone with the link can view" for
 * the API key to work without authentication.
 */
object DriveScanner {
    private const val TAG = "DriveScanner"
    private const val API_BASE = "https://www.googleapis.com/drive/v3"
    private const val FIELDS = "files(id,name,mimeType,webViewLink,size)"

    // Populated from secrets.xml via BuildConfig
    private var apiKey: String = ""

    /** Set the API key at app startup. */
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
        val size: Long? = null
    )

    /**
     * Scan a Drive link and return all files found.
     * Handles both single file links and folder links.
     */
    suspend fun scanLink(link: String): ScanResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext ScanResult(error = "API key not configured. Contact admin.")
        }

        try {
            val extractedId = extractFileOrFolderId(link)
            if (extractedId == null) {
                return@withContext ScanResult(error = "Could not parse Drive link. Ensure it's a valid Google Drive URL.")
            }

            // First, check what this ID is (file or folder)
            val metadata = getMetadata(extractedId)
            if (metadata.error != null) {
                return@withContext ScanResult(error = metadata.error)
            }

            if (metadata.isFolder) {
                // List all files in the folder
                val files = listFolderContents(extractedId)
                return@withContext ScanResult(
                    isFolder = true,
                    folderName = metadata.name,
                    files = files
                )
            } else {
                // Single file
                return@withContext ScanResult(
                    isFolder = false,
                    folderName = metadata.name,
                    files = listOf(
                        DriveFileInfo(
                            id = extractedId,
                            name = metadata.name,
                            mimeType = metadata.mimeType,
                            webViewLink = metadata.webViewLink
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
     * Get metadata for a file or folder by ID.
     */
    private fun getMetadata(id: String): MetadataResult {
        val url = URL("$API_BASE/files/$id?key=$apiKey&fields=id,name,mimeType,webViewLink,size")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        try {
            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val errorBody = readStream(conn.errorStream ?: conn.inputStream)
                val errorMsg = parseError(errorBody)
                return MetadataResult(error = errorMsg)
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
     * List all files (non-folder) inside a folder.
     */
    private fun listFolderContents(folderId: String): List<DriveFileInfo> {
        val files = mutableListOf<DriveFileInfo>()
        var pageToken: String? = null

        do {
            val query = "'$folderId'+in+parents+and+mimeType+ne+'application/vnd.google-apps.folder'"
            val urlStr = "$API_BASE/files?q=$query&fields=nextPageToken,$FIELDS&key=$apiKey" +
                    (pageToken?.let { "&pageToken=$it" } ?: "")

            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            try {
                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    val errorBody = readStream(conn.errorStream ?: conn.inputStream)
                    Log.e(TAG, "Failed to list folder: $errorBody")
                    break
                }

                val json = JSONObject(readStream(conn.inputStream))
                val items = json.optJSONArray("files") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    files.add(
                        DriveFileInfo(
                            id = item.optString("id", ""),
                            name = item.optString("name", "Unnamed"),
                            mimeType = item.optString("mimeType", ""),
                            webViewLink = item.optString("webViewLink", null),
                            size = if (item.has("size")) item.optLong("size", 0) else null
                        )
                    )
                }

                pageToken = json.optString("nextPageToken", null)
            } finally {
                conn.disconnect()
            }
        } while (pageToken != null)

        return files
    }

    /**
     * Extract file or folder ID from various Google Drive URL formats:
     * - https://drive.google.com/file/d/FILE_ID/view
     * - https://drive.google.com/drive/folders/FOLDER_ID
     * - https://drive.google.com/drive/mobile/folders/FOLDER_ID
     * - https://drive.google.com/open?id=ID
     * - https://docs.google.com/document/d/DOC_ID
     * - Just a raw ID
     */
    fun extractFileOrFolderId(link: String): String? {
        val trimmed = link.trim()

        // Try various URL patterns
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

        // Maybe it's just a raw ID (alphanumeric + underscore + dash, at least 10 chars)
        if (trimmed.matches(Regex("^[a-zA-Z0-9_-]{10,}$"))) {
            return trimmed
        }

        return null
    }

    /**
     * Build a direct URL for a file to be used in WebView.
     * For folders, returns the standard folder URL.
     */
    fun buildDirectUrl(fileId: String, mimeType: String? = null, isFolder: Boolean = false): String {
        if (isFolder) {
            return "https://drive.google.com/drive/folders/$fileId"
        }
        // File link with /preview for better viewing
        return "https://drive.google.com/file/d/$fileId/preview"
    }

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
            error?.optString("message", "Unknown API error") ?: "Unknown API error"
        } catch (_: Exception) {
            "Failed to access Drive. Check that the folder is publicly accessible."
        }
    }
}
