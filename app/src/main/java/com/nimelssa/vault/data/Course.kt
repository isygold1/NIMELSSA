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
    val semester: Int = 1,
    val progress: Int = 0
) {
    val displayLevel: String get() = "${level} Level"
    val displaySemester: String get() = if (semester == 1) "1st Semester" else "2nd Semester"
}
