package com.nimelssa.vault.data

data class Course(
    val code: String,
    val name: String,
    val category: String,
    val level: String,
    val semester: Int,
    val progress: Int = 0,
    val isOffline: Boolean = false,
    val isPending: Boolean = false
) {
    val displayLevel: String get() = "${level} Level"
    val displaySemester: String get() = if (semester == 1) "1st Semester" else "2nd Semester"
}
