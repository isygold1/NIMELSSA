package com.nimelssa.vault.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CourseRepository {
    private val _courses = MutableStateFlow(
        listOf(
            // ═══════════════════════════════════════════
            // 100 LEVEL — 1st Semester
            // ═══════════════════════════════════════════
            Course("BIO 101",  "General Biology I",              "BIOLOGY",              "100", 1, 80),
            Course("BIO 107",  "Practical Biology I",            "BIOLOGY",              "100", 1, 65),
            Course("CHM 101",  "General Chemistry I",            "CHEMISTRY",            "100", 1, 75),
            Course("CHM 107",  "Practical Chemistry I",          "CHEMISTRY",            "100", 1, 60),
            Course("PHY 101",  "General Physics I",              "PHYSICS",              "100", 1, 70),
            Course("PHY 107",  "Practical Physics I",            "PHYSICS",              "100", 1, 55),
            Course("MLS 101",  "Intro to Medical Lab Science",   "MEDICAL LABORATORY SCIENCE", "100", 1, 50),
            Course("GST 101",  "Use of English I",               "GENERAL STUDIES",      "100", 1, 85),
            Course("GST 111",  "Logic, Philosophy & Existence",  "GENERAL STUDIES",      "100", 1, 70),

            // ═══════════════════════════════════════════
            // 100 LEVEL — 2nd Semester
            // ═══════════════════════════════════════════
            Course("BIO 102",  "General Biology II",             "BIOLOGY",              "100", 2, 78),
            Course("BIO 108",  "Practical Biology II",           "BIOLOGY",              "100", 2, 62),
            Course("CHM 102",  "General Chemistry II",           "CHEMISTRY",            "100", 2, 72),
            Course("CHM 108",  "Practical Chemistry II",         "CHEMISTRY",            "100", 2, 58),
            Course("PHY 102",  "General Physics II",             "PHYSICS",              "100", 2, 68),
            Course("PHY 108",  "Practical Physics II",           "PHYSICS",              "100", 2, 52),
            Course("MLS 102",  "Elementary Lab Techniques",      "MEDICAL LABORATORY SCIENCE", "100", 2, 45),
            Course("GST 102",  "Use of English II",              "GENERAL STUDIES",      "100", 2, 82),
            Course("GST 122",  "Nigerian Peoples & Culture",     "GENERAL STUDIES",      "100", 2, 75),

            // ═══════════════════════════════════════════
            // 200 LEVEL — 1st Semester
            // ═══════════════════════════════════════════
            Course("MLS 201",  "Intro to Medical Lab Science II","MEDICAL LABORATORY SCIENCE", "200", 1, 55),
            Course("BIO 201",  "General Zoology (Invertebrates)","BIOLOGY",              "200", 1, 72),
            Course("BIO 211",  "General Botany",                 "BIOLOGY",              "200", 1, 68),
            Course("CHM 211",  "Organic Chemistry I",            "CHEMISTRY",            "200", 1, 65),
            Course("BCH 201",  "Basic Biochemistry I",           "BIOCHEMISTRY",         "200", 1, 60),
            Course("STA 201",  "Biostatistics",                  "MEDICAL LABORATORY SCIENCE", "200", 1, 40),
            Course("GST 211",  "Intro to Entrepreneurship",      "GENERAL STUDIES",      "200", 1, 80),

            // ═══════════════════════════════════════════
            // 200 LEVEL — 2nd Semester
            // ═══════════════════════════════════════════
            Course("BIO 221",  "General Ecology",                "BIOLOGY",              "200", 2, 66),
            Course("CHM 221",  "Physical Chemistry I",           "CHEMISTRY",            "200", 2, 62),
            Course("BCH 211",  "Basic Biochemistry II",          "BIOCHEMISTRY",         "200", 2, 58),
            Course("MLS 211",  "Basic Haematology & Blood Transfusion", "HAEMATOLOGY",     "200", 2, 48),
            Course("MLS 221",  "Basic Histopathology & Cytology","HISTOPATHOLOGY",       "200", 2, 45),
            Course("MLS 231",  "Basic Medical Microbiology",     "MICROBIOLOGY",         "200", 2, 50),
            Course("GST 222",  "Environment & Sustainability",   "GENERAL STUDIES",      "200", 2, 75),

            // ═══════════════════════════════════════════
            // 300 LEVEL — 1st Semester
            // ═══════════════════════════════════════════
            Course("MLS 301",  "Clinical Chemistry I",           "CHEMICAL PATHOLOGY",   "300", 1, 42),
            Course("MLS 311",  "Haematology I",                  "HAEMATOLOGY",          "300", 1, 45),
            Course("MLS 321",  "Histopathology I",               "HISTOPATHOLOGY",       "300", 1, 40),
            Course("MLS 331",  "Medical Microbiology I (Bacteriology)", "MICROBIOLOGY",    "300", 1, 48),
            Course("MLS 341",  "Immunology I",                   "IMMUNOLOGY",           "300", 1, 38),
            Course("MLS 351",  "Blood Transfusion Science I",    "BLOOD TRANSFUSION",    "300", 1, 42),

            // ═══════════════════════════════════════════
            // 300 LEVEL — 2nd Semester
            // ═══════════════════════════════════════════
            Course("MLS 302",  "Clinical Chemistry II",          "CHEMICAL PATHOLOGY",   "300", 2, 40),
            Course("MLS 312",  "Haematology II",                 "HAEMATOLOGY",          "300", 2, 43),
            Course("MLS 322",  "Histopathology II",              "HISTOPATHOLOGY",       "300", 2, 38),
            Course("MLS 332",  "Medical Microbiology II (Parasitology)", "PARASITOLOGY",   "300", 2, 45),
            Course("MLS 342",  "Immunology II",                  "IMMUNOLOGY",           "300", 2, 36),
            Course("MLS 352",  "Blood Transfusion Science II",   "BLOOD TRANSFUSION",    "300", 2, 40),

            // ═══════════════════════════════════════════
            // 400 LEVEL — 1st Semester
            // ═══════════════════════════════════════════
            Course("MLS 401",  "Chemical Pathology I",           "CHEMICAL PATHOLOGY",   "400", 1, 35),
            Course("MLS 411",  "Haematology III",                "HAEMATOLOGY",          "400", 1, 38),
            Course("MLS 421",  "Histopathology III",             "HISTOPATHOLOGY",       "400", 1, 33),
            Course("MLS 431",  "Medical Microbiology III (Virology/Mycology)", "MICROBIOLOGY", "400", 1, 40),
            Course("MLS 441",  "Parasitology & Entomology",      "PARASITOLOGY",         "400", 1, 42),
            Course("MLS 451",  "Research Methodology & Biostatistics", "MEDICAL LABORATORY SCIENCE", "400", 1, 30),

            // ═══════════════════════════════════════════
            // 400 LEVEL — 2nd Semester
            // ═══════════════════════════════════════════
            Course("MLS 402",  "Chemical Pathology II",          "CHEMICAL PATHOLOGY",   "400", 2, 32),
            Course("MLS 412",  "Advanced Haematology",           "HAEMATOLOGY",          "400", 2, 35),
            Course("MLS 422",  "Advanced Histopathology & Cytology", "HISTOPATHOLOGY",    "400", 2, 30),
            Course("MLS 432",  "Advanced Medical Microbiology",  "MICROBIOLOGY",         "400", 2, 38),
            Course("MLS 442",  "Project / Thesis",               "MEDICAL LABORATORY SCIENCE", "400", 2, 20),
            Course("MLS 452",  "Health Mgmt & Lab Ethics",       "MEDICAL LABORATORY SCIENCE", "400", 2, 40),
        )
    )

    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    fun getFiltered(level: String, semester: Int): List<Course> {
        return _courses.value.filter {
            it.level == level && it.semester == semester && !it.isPending
        }
    }

    /**
     * Returns courses for the given level/semester, with duplicate course codes
     * merged into a single entry. Multiple resources (lecture notes, past questions)
     * submitted separately for the same course code are combined into one card.
     */
    fun getFilteredMerged(level: String, semester: Int): List<Course> {
        val filtered = _courses.value.filter {
            it.level == level && it.semester == semester && !it.isPending
        }
        // Group by course code and merge
        return filtered.groupBy { it.code }.map { (code, entries) ->
            entries.reduce { merged, next ->
                merged.copy(
                    lectureNotesUrl = merged.lectureNotesUrl.ifBlank { next.lectureNotesUrl },
                    pastQuestionsUrl = merged.pastQuestionsUrl.ifBlank { next.pastQuestionsUrl },
                    submittedBy = listOfNotNull(
                        merged.submittedBy.takeIf { it.isNotBlank() },
                        next.submittedBy.takeIf { it.isNotBlank() }
                    ).joinToString(", "),
                    notes = listOfNotNull(
                        merged.notes.takeIf { it.isNotBlank() },
                        next.notes.takeIf { it.isNotBlank() }
                    ).joinToString("; "),
                    isOffline = merged.isOffline || next.isOffline
                )
            }
        }
    }

    fun addCourse(course: Course) {
        _courses.value = _courses.value + course
    }

    fun removeCourse(code: String) {
        _courses.value = _courses.value.filter { it.code != code }
    }

    fun findCourse(code: String): Course? {
        return _courses.value.find { it.code == code }
    }

    /**
     * Toggles the offline flag. The actual file download / deletion
     * is handled by [OfflineManager.saveOffline] / [OfflineManager.removeOffline].
     * This just flips the in-memory flag optimistically.
     */
    fun toggleOfflineFlag(code: String) {
        _courses.value = _courses.value.map {
            if (it.code == code) it.copy(isOffline = !it.isOffline) else it
        }
    }

    fun getCategories(level: String, semester: Int): List<String> {
        return _courses.value
            .filter { it.level == level && it.semester == semester }
            .map { it.category }
            .distinct()
    }

    // ── Pending proposal management ──

    fun getPendingCourses(): List<Course> {
        return _courses.value.filter { it.isPending }
    }

    fun approveCourse(code: String) {
        _courses.value = _courses.value.map {
            if (it.code == code) it.copy(isPending = false) else it
        }
    }

    fun rejectCourse(code: String) {
        _courses.value = _courses.value.filter { it.code != code }
    }

    /**
     * Merges Firestore-synced resource data into the in-memory course list.
     * If the course doesn't exist yet (e.g., a newly proposed one), it adds it.
     */
    fun mergeCourseResources(
        code: String,
        lectureNotesUrl: String,
        pastQuestionsUrl: String,
        submittedBy: String,
        notes: String
    ) {
        val existing = _courses.value.find { it.code == code }
        if (existing != null) {
            _courses.value = _courses.value.map {
                if (it.code == code) {
                    it.copy(
                        lectureNotesUrl = lectureNotesUrl.ifBlank { it.lectureNotesUrl },
                        pastQuestionsUrl = pastQuestionsUrl.ifBlank { it.pastQuestionsUrl },
                        submittedBy = submittedBy.ifBlank { it.submittedBy },
                        notes = notes.ifBlank { it.notes }
                    )
                } else it
            }
        } else {
            // Course not in hardcoded list — add it (e.g., a proposed course)
            val inferredLevel = "${code.firstOrNull { it.isDigit() } ?: '2'}00"
            _courses.value = _courses.value + Course(
                code = code,
                name = code,
                category = "MEDICAL LABORATORY SCIENCE",
                level = inferredLevel,
                semester = 1,
                progress = 0,
                isPending = false,
                lectureNotesUrl = lectureNotesUrl,
                pastQuestionsUrl = pastQuestionsUrl,
                submittedBy = submittedBy,
                notes = notes
            )
        }
    }

    /**
     * Returns the list of course codes that have been saved offline
     * (stored in-memory; persisted via [OfflineManager]).
     */
    fun getOfflineCourses(): List<Course> {
        return _courses.value.filter { it.isOffline }
    }

    /**
     * Sets the offline flag on a course (used by [OfflineManager] on init).
     */
    fun mergeOfflineFlag(code: String, isOffline: Boolean) {
        _courses.value = _courses.value.map {
            if (it.code == code) it.copy(isOffline = isOffline) else it
        }
    }

    /**
     * Returns true if any course has an offline flag set to true.
     */
    fun hasOfflineCourses(): Boolean {
        return _courses.value.any { it.isOffline }
    }
}
