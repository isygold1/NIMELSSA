package com.nimelssa.vault.data

/**
 * A general textbook / reference material that isn't tied to a specific course.
 * Stored per-level in Firestore collection `level_textbooks/{level}`.
 */
data class LevelTextbook(
    val level: String = "",          // "100", "200", etc.
    val masterFolderUrl: String = "", // the approved Drive link
    val label: String = "",          // admin-chosen label like "Clinical Chemistry Reference"
    val submittedBy: String = "",
    val notes: String = "",
    val id: String = ""              // Firestore doc ID (optional for local use)
)
