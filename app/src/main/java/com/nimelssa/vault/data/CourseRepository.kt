package com.nimelssa.vault.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed course metadata repository.
 *
 * Collection: `courses/{courseCode}`
 *
 * On first launch (empty collection), seeds with 56 standard MLS courses across 100-400 levels.
 * After seeding, all mutations go through this repository to keep
 * Firestore and in-memory cache in sync.
 */
object CourseRepository {
    private const val TAG = "CourseRepository"
    private const val COLLECTION = "courses"
    private val firestore get() = FirebaseFirestore.getInstance()

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    /** Load all courses from Firestore. Seeds if empty. */
    suspend fun loadAll() {
        try {
            val snap = firestore.collection(COLLECTION).get().await()

            if (snap.isEmpty) {
                Log.d(TAG, "Courses collection empty — seeding with defaults")
                seedDefaultCourses()
                // Reload after seeding
                val snap2 = firestore.collection(COLLECTION).get().await()
                _courses.value = snap2.documents.mapNotNull {
                    it.toObject(Course::class.java)
                }
            } else {
                _courses.value = snap.documents.mapNotNull {
                    it.toObject(Course::class.java)
                }
            }

            Log.d(TAG, "Loaded ${_courses.value.size} courses from Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load courses from Firestore", e)
            // Fallback to empty list — user can add courses via AdminScreen
            _courses.value = emptyList()
        }
    }

    /** Get filtered courses for a level + semester. */
    fun getFiltered(level: String, semester: Int): List<Course> {
        return _courses.value.filter {
            it.level == level && it.semester == semester
        }
    }

    /** Find a course by code. */
    fun findCourse(code: String): Course? {
        return _courses.value.find { it.code == code }
    }

    /** Get unique categories for a level + semester. */
    fun getCategories(level: String, semester: Int): List<String> {
        return _courses.value
            .filter { it.level == level && it.semester == semester }
            .map { it.category }
            .distinct()
    }

    // ── Mutations ──

    /** Add (or overwrite) a course. Writes to Firestore first. */
    suspend fun addCourse(course: Course) {
        try {
            firestore.collection(COLLECTION).document(course.code).set(course).await()
            Log.d(TAG, "Added/updated course: ${course.code}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save course ${course.code} to Firestore", e)
        }

        // Update in-memory
        val existing = _courses.value.find { it.code == course.code }
        _courses.value = if (existing != null) {
            _courses.value.map { if (it.code == course.code) course else it }
        } else {
            _courses.value + course
        }
    }

    /** Remove a course by code. */
    suspend fun removeCourse(code: String) {
        try {
            firestore.collection(COLLECTION).document(code).delete().await()
            Log.d(TAG, "Removed course: $code")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove course $code", e)
        }

        _courses.value = _courses.value.filter { it.code != code }
    }

    /** Update progress field for a course. */
    fun updateProgress(code: String, progress: Int) {
        _courses.value = _courses.value.map {
            if (it.code == code) it.copy(progress = progress) else it
        }
        // Firestore update is fire-and-forget
        firestore.collection(COLLECTION).document(code)
            .update("progress", progress)
    }

    // ── Seeding ──

    private suspend fun seedDefaultCourses() {
        val defaults = listOf(
            // 100 LEVEL — 1st Semester
            Course("BIO 101",  "General Biology I",              "BIOLOGY",              "100", 1, 80),
            Course("BIO 107",  "Practical Biology I",            "BIOLOGY",              "100", 1, 65),
            Course("CHM 101",  "General Chemistry I",            "CHEMISTRY",            "100", 1, 75),
            Course("CHM 107",  "Practical Chemistry I",          "CHEMISTRY",            "100", 1, 60),
            Course("PHY 101",  "General Physics I",              "PHYSICS",              "100", 1, 70),
            Course("PHY 107",  "Practical Physics I",            "PHYSICS",              "100", 1, 55),
            Course("MLS 101",  "Intro to Medical Lab Science",   "MEDICAL LABORATORY SCIENCE", "100", 1, 50),
            Course("GST 101",  "Use of English I",               "GENERAL STUDIES",      "100", 1, 85),
            Course("GST 111",  "Logic, Philosophy & Existence",  "GENERAL STUDIES",      "100", 1, 70),

            // 100 LEVEL — 2nd Semester
            Course("BIO 102",  "General Biology II",             "BIOLOGY",              "100", 2, 78),
            Course("BIO 108",  "Practical Biology II",           "BIOLOGY",              "100", 2, 62),
            Course("CHM 102",  "General Chemistry II",           "CHEMISTRY",            "100", 2, 72),
            Course("CHM 108",  "Practical Chemistry II",         "CHEMISTRY",            "100", 2, 58),
            Course("PHY 102",  "General Physics II",             "PHYSICS",              "100", 2, 68),
            Course("PHY 108",  "Practical Physics II",           "PHYSICS",              "100", 2, 52),
            Course("MLS 102",  "Elementary Lab Techniques",      "MEDICAL LABORATORY SCIENCE", "100", 2, 45),
            Course("GST 102",  "Use of English II",              "GENERAL STUDIES",      "100", 2, 82),
            Course("GST 122",  "Nigerian Peoples & Culture",     "GENERAL STUDIES",      "100", 2, 75),

            // 200 LEVEL — 1st Semester
            Course("MLS 201",  "Intro to Medical Lab Science II","MEDICAL LABORATORY SCIENCE", "200", 1, 55),
            Course("BIO 201",  "General Zoology (Invertebrates)","BIOLOGY",              "200", 1, 72),
            Course("BIO 211",  "General Botany",                 "BIOLOGY",              "200", 1, 68),
            Course("CHM 211",  "Organic Chemistry I",            "CHEMISTRY",            "200", 1, 65),
            Course("BCH 201",  "Basic Biochemistry I",           "BIOCHEMISTRY",         "200", 1, 60),
            Course("STA 201",  "Biostatistics",                  "MEDICAL LABORATORY SCIENCE", "200", 1, 40),
            Course("GST 211",  "Intro to Entrepreneurship",      "GENERAL STUDIES",      "200", 1, 80),

            // 200 LEVEL — 2nd Semester
            Course("BIO 221",  "General Ecology",                "BIOLOGY",              "200", 2, 66),
            Course("CHM 221",  "Physical Chemistry I",           "CHEMISTRY",            "200", 2, 62),
            Course("BCH 211",  "Basic Biochemistry II",          "BIOCHEMISTRY",         "200", 2, 58),
            Course("MLS 211",  "Basic Haematology & Blood Transfusion", "HAEMATOLOGY",     "200", 2, 48),
            Course("MLS 221",  "Basic Histopathology & Cytology","HISTOPATHOLOGY",       "200", 2, 45),
            Course("MLS 231",  "Basic Medical Microbiology",     "MICROBIOLOGY",         "200", 2, 50),
            Course("GST 222",  "Environment & Sustainability",   "GENERAL STUDIES",      "200", 2, 75),

            // 300 LEVEL — 1st Semester
            Course("MLS 301",  "Clinical Chemistry I",           "CHEMICAL PATHOLOGY",   "300", 1, 42),
            Course("MLS 311",  "Haematology I",                  "HAEMATOLOGY",          "300", 1, 45),
            Course("MLS 321",  "Histopathology I",               "HISTOPATHOLOGY",       "300", 1, 40),
            Course("MLS 331",  "Medical Microbiology I (Bacteriology)", "MICROBIOLOGY",    "300", 1, 48),
            Course("MLS 341",  "Immunology I",                   "IMMUNOLOGY",           "300", 1, 38),
            Course("MLS 351",  "Blood Transfusion Science I",    "BLOOD TRANSFUSION",    "300", 1, 42),

            // 300 LEVEL — 2nd Semester
            Course("MLS 302",  "Clinical Chemistry II",          "CHEMICAL PATHOLOGY",   "300", 2, 40),
            Course("MLS 312",  "Haematology II",                 "HAEMATOLOGY",          "300", 2, 43),
            Course("MLS 322",  "Histopathology II",              "HISTOPATHOLOGY",       "300", 2, 38),
            Course("MLS 332",  "Medical Microbiology II (Parasitology)", "PARASITOLOGY",   "300", 2, 45),
            Course("MLS 342",  "Immunology II",                  "IMMUNOLOGY",           "300", 2, 36),
            Course("MLS 352",  "Blood Transfusion Science II",   "BLOOD TRANSFUSION",    "300", 2, 40),

            // 400 LEVEL — 1st Semester
            Course("MLS 401",  "Chemical Pathology I",           "CHEMICAL PATHOLOGY",   "400", 1, 35),
            Course("MLS 411",  "Haematology III",                "HAEMATOLOGY",          "400", 1, 38),
            Course("MLS 421",  "Histopathology III",             "HISTOPATHOLOGY",       "400", 1, 33),
            Course("MLS 431",  "Medical Microbiology III (Virology/Mycology)", "MICROBIOLOGY", "400", 1, 40),
            Course("MLS 441",  "Parasitology & Entomology",      "PARASITOLOGY",         "400", 1, 42),
            Course("MLS 451",  "Research Methodology & Biostatistics", "MEDICAL LABORATORY SCIENCE", "400", 1, 30),

            // 400 LEVEL — 2nd Semester
            Course("MLS 402",  "Chemical Pathology II",          "CHEMICAL PATHOLOGY",   "400", 2, 32),
            Course("MLS 412",  "Advanced Haematology",           "HAEMATOLOGY",          "400", 2, 35),
            Course("MLS 422",  "Advanced Histopathology & Cytology", "HISTOPATHOLOGY",    "400", 2, 30),
            Course("MLS 432",  "Advanced Medical Microbiology",  "MICROBIOLOGY",         "400", 2, 38),
            Course("MLS 442",  "Project / Thesis",               "MEDICAL LABORATORY SCIENCE", "400", 2, 20),
            Course("MLS 452",  "Health Mgmt & Lab Ethics",       "MEDICAL LABORATORY SCIENCE", "400", 2, 40),
        )

        // Batch write
        val batch = firestore.batch()
        for (course in defaults) {
            val ref = firestore.collection(COLLECTION).document(course.code)
            batch.set(ref, course)
        }
        batch.commit().await()
        Log.d(TAG, "Seeded ${defaults.size} default courses")
    }
}
