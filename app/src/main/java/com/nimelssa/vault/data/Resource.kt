package com.nimelssa.vault.data

/**
 * A first-class resource entity. Each resource is an individual Firestore document
 * in the `resources/{autoId}` collection.
 *
 * This replaces the old flat URL fields on Course (lectureNotesUrl, pastQuestionsUrl, textbookUrl).
 * A course can have 0..N resources of any type.
 */
data class Resource(
    val id: String = "",
    val courseCode: String = "",       // empty for level-wide textbooks
    val resourceType: String = "LN",   // "LN", "PQ", "TB", "OT"
    val level: String = "",            // e.g. "300" — used for level-wide resources
    val masterUrl: String = "",        // the approved Drive link
    val label: String = "",            // human-readable label
    val submittedBy: String = "",
    val notes: String = "",
    val approvedBy: String = "",
    val approvedAt: Long = 0L
) {
    val resourceLabel: String get() = when (resourceType) {
        "LN" -> "Lecture Notes"
        "PQ" -> "Past Questions"
        "TB" -> "Textbook"
        else -> "Other"
    }

    val icon: String get() = when (resourceType) {
        "LN" -> "📖"
        "PQ" -> "📝"
        "TB" -> "📚"
        else -> "📄"
    }
}
