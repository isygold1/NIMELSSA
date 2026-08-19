package com.nimelssa.vault.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed course metadata repository.
 *
 * Collection: `courses/{courseCode}`
 *
 * On first launch (empty collection), seeds with 58 standard courses across 100-400 levels.
 * After seeding, all mutations go through this repository to keep
 * Firestore and in-memory cache in sync.
 */
object CourseRepository {
    private const val TAG = "CourseRepository"
    private const val COLLECTION = "courses"
    private const val ALIAS_COL = "course_aliases"
    private val firestore get() = FirebaseFirestore.getInstance()

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    /**
     * Course-code alias table (CCMAS equivalences).
     * Map: normalized VARIANT code → normalized CANONICAL code.
     * When a course's code changes (new CCMAS), the old code is registered as
     * a variant so resources filed under it still surface on the new shelf.
     * Firestore: `course_aliases/{variantNormalized}` → {variant, canonical}.
     */
    private val _aliases = MutableStateFlow<Map<String, String>>(emptyMap())
    val aliases: StateFlow<Map<String, String>> = _aliases.asStateFlow()

    /** Load all course-code aliases from Firestore. Idempotent. */
    suspend fun loadAliases() {
        try {
            val snap = firestore.collection(ALIAS_COL).get().await()
            _aliases.value = snap.documents.mapNotNull { doc ->
                val variant = doc.getString("variant") ?: return@mapNotNull null
                val canonical = doc.getString("canonical") ?: return@mapNotNull null
                normalizeCode(variant) to normalizeCode(canonical)
            }.toMap()
            Log.d(TAG, "Loaded ${_aliases.value.size} course-code aliases")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load course-code aliases", e)
            _aliases.value = emptyMap()
        }
    }

    /**
     * Resolve a code through the alias table (variant → canonical), following
     * chains (a code edited twice) with a depth + loop guard.
     */
    fun resolveCode(code: String): String {
        val normalized = normalizeCode(code)
        if (normalized.isBlank()) return normalized
        var current = normalized
        val seen = mutableSetOf<String>()
        var hops = 0
        while (hops < 5) {
            val target = _aliases.value[current] ?: break
            if (!seen.add(target)) break
            current = target
            hops++
        }
        return current
    }

    /** The canonical code a variant maps to, or null if the code is canonical. */
    fun canonicalFor(code: String): String? = _aliases.value[normalizeCode(code)]

    /** True when the code is registered as a variant of another course. */
    fun isVariant(code: String): Boolean =
        normalizeCode(code).isNotBlank() && _aliases.value.containsKey(normalizeCode(code))

    /** Add a variant → canonical alias (Firestore first, then in-memory). */
    suspend fun addAlias(variant: String, canonical: String) {
        val v = normalizeCode(variant)
        val c = normalizeCode(canonical)
        if (v.isBlank() || c.isBlank() || v == c) return
        val displayV = variant.trim().uppercase(Locale.ROOT)
        val displayC = canonical.trim().uppercase(Locale.ROOT)
        try {
            firestore.collection(ALIAS_COL).document(v).set(
                mapOf("variant" to displayV, "canonical" to displayC)
            ).await()
            Log.d(TAG, "Saved alias $displayV -> $displayC")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save alias $displayV -> $displayC", e)
        }
        _aliases.value = _aliases.value + (v to c)
    }

    /** Remove a variant → canonical alias. */
    suspend fun removeAlias(variant: String) {
        val v = normalizeCode(variant)
        try {
            firestore.collection(ALIAS_COL).document(v).delete().await()
            Log.d(TAG, "Removed alias $v")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove alias $v", e)
        }
        _aliases.value = _aliases.value - v
    }

    /**
     * Update a course in place (code/name/category/level/semester).
     * When the code changes: writes the course under the new code, deletes the
     * old doc, and (unless [autoLink] is false) records oldCode → newCode as an
     * alias so existing resources filed under the old code keep appearing on
     * the new shelf (no data loss, no re-linking). The level/semester move
     * with the record, so the workspace level dropdown is unaffected.
     * autoLink=false is for mistake-proofing: a rep who typed the wrong code
     * can rename again without polluting the alias table with a wrong mapping.
     */
    suspend fun updateCourseCode(oldCode: String, updated: Course, autoLink: Boolean = true) {
        val old = normalizeCode(oldCode)
        val new = normalizeCode(updated.code)
        if (new.isBlank()) return
        if (old == new) {
            addCourse(updated)
            return
        }
        try {
            val batch = firestore.batch()
            batch.set(firestore.collection(COLLECTION).document(updated.code), updated)
            batch.delete(firestore.collection(COLLECTION).document(oldCode))
            batch.commit().await()
            Log.d(TAG, "Updated course $oldCode -> ${updated.code}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update course $oldCode -> ${updated.code}", e)
        }
        if (autoLink) {
            addAlias(oldCode, updated.code)
        } else {
            Log.d(TAG, "Skipped auto-alias $oldCode -> ${updated.code} (autoLink off)")
        }
        _courses.value = _courses.value.filter { normalizeCode(it.code) != old } + updated
    }

    /** Load all courses from Firestore. Seeds if empty. */
    suspend fun loadAll() {
        // Alias table must be ready before resources are grouped by code
        loadAliases()
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

            // ── One-time migration: fix semester-2 courses that were stored with
            //    semester=1 by the old seed (which omitted the semester arg, defaulting to 1).
            //    Affected: all 100-level 2nd-semester courses + any others from old seed.
            val semester2Codes = setOf(
                // 100 LEVEL — 2nd Semester
                "BIO 102", "BIO 108", "CHM 102", "CHM 108",
                "PHY 102", "PHY 108", "MLS 102", "MLS 108",
                "GST 102", "GST 122"
            )
            val correctedCourses = mutableListOf<Course>()
            _courses.value = _courses.value.map { course ->
                if (course.code in semester2Codes && course.semester != 2) {
                    val fixed = course.copy(semester = 2)
                    correctedCourses.add(fixed)
                    fixed
                } else course
            }

            // Re-sync all corrected courses to Firestore
            for (fixed in correctedCourses) {
                firestore.collection(COLLECTION).document(fixed.code).set(fixed)
            }
            if (correctedCourses.isNotEmpty()) {
                Log.d(TAG, "Fixed semester for ${correctedCourses.size} courses: ${correctedCourses.joinToString { it.code }}")
            }

            Log.d(TAG, "Loaded ${_courses.value.size} courses from Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load courses from Firestore", e)
            // Fallback to in-memory default list so workspace is never empty
            _courses.value = getDefaultCourses()
            Log.d(TAG, "Using ${_courses.value.size} default courses as fallback")
        }
    }

    /** Get filtered courses for a level + semester. */
    fun getFiltered(level: String, semester: Int): List<Course> {
        return _courses.value.filter {
            it.level == level && it.semester == semester
        }
    }

    /**
     * Canonical course-code form: uppercase, no spaces.
     * ("MLS 201" → "MLS201") — lets lookups match codes written either way.
     */
    fun normalizeCode(code: String): String =
        code.uppercase(Locale.ROOT).replace(" ", "")

    /** Find a course by code (canonical, space-insensitive, alias-aware). */
    fun findCourse(code: String): Course? {
        val normalized = normalizeCode(code)
        val direct = _courses.value.find { normalizeCode(it.code) == normalized }
        if (direct != null) return direct
        // Variant code (old CCMAS): resolve to the canonical course
        val canonical = resolveCode(normalized)
        if (canonical != normalized) {
            return _courses.value.find { normalizeCode(it.code) == canonical }
        }
        return null
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

        // Update in-memory (canonical match so "MLS201" == "MLS 201")
        val existing = _courses.value.find { normalizeCode(it.code) == normalizeCode(course.code) }
        _courses.value = if (existing != null) {
            _courses.value.map { if (normalizeCode(it.code) == normalizeCode(course.code)) course else it }
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

        _courses.value = _courses.value.filter { normalizeCode(it.code) != normalizeCode(code) }
    }

    // ── Seeding ──

    /** Returns the hardcoded list of default courses. Used for both seeding and Firestore fallback. */
    private fun getDefaultCourses(): List<Course> = listOf(
            // 100 LEVEL — 1st Semester
            Course("BIO 101",  "General Biology I",              "BIOLOGY",              "100", 1),
            Course("BIO 107",  "Practical Biology I",            "BIOLOGY",              "100", 1),
            Course("CHM 101",  "General Chemistry I",            "CHEMISTRY",            "100", 1),
            Course("CHM 107",  "Practical Chemistry I",          "CHEMISTRY",            "100", 1),
            Course("PHY 101",  "General Physics I",              "PHYSICS",              "100", 1),
            Course("PHY 107",  "Practical Physics I",            "PHYSICS",              "100", 1),
            Course("MLS 101",  "Intro to Medical Lab Science",   "MEDICAL LABORATORY SCIENCE", "100", 1),
            Course("MLS 107",  "Practical MLS I",                "MEDICAL LABORATORY SCIENCE", "100", 1),
            Course("GST 101",  "Use of English I",               "GENERAL STUDIES",      "100", 1),
            Course("GST 111",  "Logic, Philosophy & Existence",  "GENERAL STUDIES",      "100", 1),

            // 100 LEVEL — 2nd Semester
            Course("BIO 102",  "General Biology II",             "BIOLOGY",              "100", 2),
            Course("BIO 108",  "Practical Biology II",           "BIOLOGY",              "100", 2),
            Course("CHM 102",  "General Chemistry II",           "CHEMISTRY",            "100", 2),
            Course("CHM 108",  "Practical Chemistry II",         "CHEMISTRY",            "100", 2),
            Course("PHY 102",  "General Physics II",             "PHYSICS",              "100", 2),
            Course("PHY 108",  "Practical Physics II",           "PHYSICS",              "100", 2),
            Course("MLS 102",  "Elementary Lab Techniques",      "MEDICAL LABORATORY SCIENCE", "100", 2),
            Course("MLS 108",  "Practical MLS II",               "MEDICAL LABORATORY SCIENCE", "100", 2),
            Course("GST 102",  "Use of English II",              "GENERAL STUDIES",      "100", 2),
            Course("GST 122",  "Nigerian Peoples & Culture",     "GENERAL STUDIES",      "100", 2),

            // 200 LEVEL — 1st Semester
            Course("MLS 201",  "Intro to Medical Lab Science II","MEDICAL LABORATORY SCIENCE", "200", 1),
            Course("BIO 201",  "General Zoology (Invertebrates)","BIOLOGY",              "200", 1),
            Course("BIO 211",  "General Botany",                 "BIOLOGY",              "200", 1),
            Course("CHM 211",  "Organic Chemistry I",            "CHEMISTRY",            "200", 1),
            Course("BCH 201",  "Basic Biochemistry I",           "BIOCHEMISTRY",         "200", 1),
            Course("STA 201",  "Biostatistics",                  "MEDICAL LABORATORY SCIENCE", "200", 1),
            Course("GST 211",  "Intro to Entrepreneurship",      "GENERAL STUDIES",      "200", 1),

            // 200 LEVEL — 2nd Semester
            Course("BIO 221",  "General Ecology",                "BIOLOGY",              "200", 2),
            Course("CHM 221",  "Physical Chemistry I",           "CHEMISTRY",            "200", 2),
            Course("BCH 211",  "Basic Biochemistry II",          "BIOCHEMISTRY",         "200", 2),
            Course("MLS 211",  "Basic Haematology & Blood Transfusion", "HAEMATOLOGY",     "200", 2),
            Course("MLS 221",  "Basic Histopathology & Cytology","HISTOPATHOLOGY",       "200", 2),
            Course("MLS 231",  "Basic Medical Microbiology",     "MICROBIOLOGY",         "200", 2),
            Course("GST 222",  "Environment & Sustainability",   "GENERAL STUDIES",      "200", 2),

            // 300 LEVEL — 1st Semester
            Course("MLS 301",  "Clinical Chemistry I",           "CHEMICAL PATHOLOGY",   "300", 1),
            Course("MLS 311",  "Haematology I",                  "HAEMATOLOGY",          "300", 1),
            Course("MLS 321",  "Histopathology I",               "HISTOPATHOLOGY",       "300", 1),
            Course("MLS 331",  "Medical Microbiology I (Bacteriology)", "MICROBIOLOGY",    "300", 1),
            Course("MLS 341",  "Immunology I",                   "IMMUNOLOGY",           "300", 1),
            Course("MLS 351",  "Blood Transfusion Science I",    "BLOOD TRANSFUSION",    "300", 1),

            // 300 LEVEL — 2nd Semester
            Course("MLS 302",  "Clinical Chemistry II",          "CHEMICAL PATHOLOGY",   "300", 2),
            Course("MLS 312",  "Haematology II",                 "HAEMATOLOGY",          "300", 2),
            Course("MLS 322",  "Histopathology II",              "HISTOPATHOLOGY",       "300", 2),
            Course("MLS 332",  "Medical Microbiology II (Parasitology)", "PARASITOLOGY",   "300", 2),
            Course("MLS 342",  "Immunology II",                  "IMMUNOLOGY",           "300", 2),
            Course("MLS 352",  "Blood Transfusion Science II",   "BLOOD TRANSFUSION",    "300", 2),

            // 400 LEVEL — 1st Semester
            Course("MLS 401",  "Chemical Pathology I",           "CHEMICAL PATHOLOGY",   "400", 1),
            Course("MLS 411",  "Haematology III",                "HAEMATOLOGY",          "400", 1),
            Course("MLS 421",  "Histopathology III",             "HISTOPATHOLOGY",       "400", 1),
            Course("MLS 431",  "Medical Microbiology III (Virology/Mycology)", "MICROBIOLOGY", "400", 1),
            Course("MLS 441",  "Parasitology & Entomology",      "PARASITOLOGY",         "400", 1),
            Course("MLS 451",  "Research Methodology & Biostatistics", "MEDICAL LABORATORY SCIENCE", "400", 1),

            // 400 LEVEL — 2nd Semester
            Course("MLS 402",  "Chemical Pathology II",          "CHEMICAL PATHOLOGY",   "400", 2),
            Course("MLS 412",  "Advanced Haematology",           "HAEMATOLOGY",          "400", 2),
            Course("MLS 422",  "Advanced Histopathology & Cytology", "HISTOPATHOLOGY",    "400", 2),
            Course("MLS 432",  "Advanced Medical Microbiology",  "MICROBIOLOGY",         "400", 2),
            Course("MLS 442",  "Project / Thesis",               "MEDICAL LABORATORY SCIENCE", "400", 2),
            Course("MLS 452",  "Health Mgmt & Lab Ethics",       "MEDICAL LABORATORY SCIENCE", "400", 2),
        )

    /** Write the default courses to Firestore (called when collection is empty). */
    private suspend fun seedDefaultCourses() {
        val defaults = getDefaultCourses()
        val batch = firestore.batch()
        for (course in defaults) {
            val ref = firestore.collection(COLLECTION).document(course.code)
            batch.set(ref, course)
        }
        batch.commit().await()
        Log.d(TAG, "Seeded ${defaults.size} default courses")
    }
}
