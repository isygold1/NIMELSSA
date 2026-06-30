package com.nimelssa.vault.data

/**
 * Pure course metadata backed by Firestore `courses/{code}`.
 * No resource URLs here — those live in [Resource] entities in [ResourceRepository].
 *
 * A course's available resource types (LN/PQ/TB) are determined by querying
 * [ResourceRepository.hasType] or [ResourceRepository.getForCourse].
 */
data class Course(
    val code: String = "",
    val name: String = "",
    val category: String = "",
    val level: String = "",
    val semester: Int = 1
) {
    val displayLevel: String get() = "${level} Level"
    val displaySemester: String get() = if (semester == 1) "1st Semester" else "2nd Semester"
}

/**
 * Single source of truth for academic levels used across the app.
 * Add or remove entries here and every dropdown picks up the change.
 */
object Levels {
    /** All undergraduate levels including 500. */
    val ALL = listOf("100", "200", "300", "400", "500")
}
