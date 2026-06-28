package com.nimelssa.vault.data

data class Course(
    val code: String,
    val name: String = "",
    val category: String = "",
    val level: String = "",
    val semester: Int = 1,
    val progress: Int = 0,
    val isOffline: Boolean = false,
    val isPending: Boolean = false,
    val lectureNotesUrl: String = "",
    val pastQuestionsUrl: String = "",
    val textbookUrl: String = "",
    val submittedBy: String = "",
    val notes: String = ""
) {
    val displayLevel: String get() = "${level} Level"
    val displaySemester: String get() = if (semester == 1) "1st Semester" else "2nd Semester"

    /** Returns true if this course has at least one resource attached */
    val hasResources: Boolean get() =
        lectureNotesUrl.isNotBlank() || pastQuestionsUrl.isNotBlank() || textbookUrl.isNotBlank()
}
