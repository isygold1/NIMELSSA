package com.nimelssa.vault.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Unified repository for course resources and level-wide textbooks.
 *
 * Collections:
 *   - `resources/{autoId}`        — resource documents (LN/PQ/TB/OT per course)
 *   - `level_textbooks/{level}`   — legacy format (kept for backward compatibility)
 *
 * On load, both collections are read and merged into a single in-memory cache.
 * New writes go only to `resources/`.
 */
object ResourceRepository {
    private const val TAG = "ResourceRepository"
    private const val RESOURCES_COL = "resources"
    private const val TEXTBOOKS_COL = "level_textbooks"
    private val firestore get() = FirebaseFirestore.getInstance()

    /** Map: courseCode → list of resources. Special key "__LEVEL__" for level-wide. */
    private val _resources = MutableStateFlow<Map<String, List<Resource>>>(emptyMap())
    val resources: StateFlow<Map<String, List<Resource>>> = _resources.asStateFlow()

    /** All resources as a flat list — convenience for listings. */
    val allResources: List<Resource> get() = _resources.value.values.flatten()

    /** Load all resources from Firestore on app start. Merges old and new formats. */
    suspend fun loadAll() {
        // Alias table must be ready before grouping: variant codes (old CCMAS)
        // resolve to their canonical course so merged shelves form correctly.
        CourseRepository.loadAliases()

        // Last-known shelves: if the primary fetch fails (offline, rules, etc.)
        // we must NOT clobber what the UI is already showing — that produced
        // "materials vanish a split second after opening" when offline.
        val previous = _resources.value
        var resourcesFetchOk = false

        val map = mutableMapOf<String, MutableList<Resource>>()

        // 1. Load new-format resources/{autoId}
        try {
            val snap = firestore.collection(RESOURCES_COL).get().await()
            for (doc in snap.documents) {
                val r = doc.toObject<Resource>()?.copy(id = doc.id) ?: continue
                // Canonical key: "MLS 201" and "MLS201" must map to the same
                // bucket; variant codes resolve to their canonical course.
                val key = if (r.courseCode.isNotBlank()) CourseRepository.resolveCode(r.courseCode) else "__LEVEL__"
                map.getOrPut(key) { mutableListOf() }.add(r)
            }
            resourcesFetchOk = true
            Log.d(TAG, "Loaded ${snap.size()} resources from $RESOURCES_COL")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load resources", e)
        }

        // 2. Migrate old-format course_resources/{courseCode} (legacy)
        try {
            val legacySnap = firestore.collection("course_resources").get().await()
            for (doc in legacySnap.documents) {
                val code = doc.id
                val key = CourseRepository.resolveCode(code)
                val lnu = doc.getString("lectureNotesUrl") ?: ""
                val pqu = doc.getString("pastQuestionsUrl") ?: ""
                val tbu = doc.getString("textbookUrl") ?: ""
                val sub = doc.getString("submittedBy") ?: ""
                val notes = doc.getString("notes") ?: ""

                // Only migrate if not already migrated (no resources for this code yet)
                if (key.isNotBlank() && !map.containsKey(key)) {
                    if (lnu.isNotBlank()) {
                        map.getOrPut(key) { mutableListOf() }.add(
                            Resource(courseCode = code, resourceType = "LN", masterUrl = lnu,
                                     submittedBy = sub, notes = notes, label = "Lecture Notes")
                        )
                    }
                    if (pqu.isNotBlank()) {
                        map.getOrPut(key) { mutableListOf() }.add(
                            Resource(courseCode = code, resourceType = "PQ", masterUrl = pqu,
                                     submittedBy = sub, notes = notes, label = "Past Questions")
                        )
                    }
                    if (tbu.isNotBlank()) {
                        map.getOrPut(key) { mutableListOf() }.add(
                            Resource(courseCode = code, resourceType = "TB", masterUrl = tbu,
                                     submittedBy = sub, notes = notes, label = "Textbook")
                        )
                    }
                }
            }
            Log.d(TAG, "Migrated ${legacySnap.size()} legacy course_resources")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load legacy course_resources", e)
        }

        // 3. Load legacy level_textbooks/{level} (array format)
        try {
            val tbSnap = firestore.collection(TEXTBOOKS_COL).get().await()
            for (doc in tbSnap.documents) {
                val level = doc.id
                val list = doc.get("textbooks") as? List<Map<String, Any>> ?: continue
                for (entry in list) {
                    val url = (entry["masterFolderUrl"] as? String) ?: continue
                    val label = (entry["label"] as? String) ?: ""
                    val sub = (entry["submittedBy"] as? String) ?: ""
                    val note = (entry["notes"] as? String) ?: ""
                    map.getOrPut("__LEVEL__") { mutableListOf() }.add(
                        Resource(courseCode = "", resourceType = "TB", level = level,
                                 masterUrl = url, label = label, submittedBy = sub, notes = note)
                    )
                }
            }
            Log.d(TAG, "Loaded ${tbSnap.size()} level textbook docs")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load level_textbooks", e)
        }

        if (!resourcesFetchOk && previous.isNotEmpty()) {
            // Offline or transient fetch failure with stale data available —
            // keep the last-known shelves instead of showing empty lists.
            // Assumption: legacy/level fetch results are secondary to the main
            // catalog, so they're dropped in this rare path rather than merged.
            Log.w(TAG, "Primary resource fetch failed — keeping ${previous.size} cached shelf bucket(s)")
            _resources.value = previous
            return
        }

        _resources.value = map
        Log.d(TAG, "Total resources loaded: ${map.values.sumOf { it.size }}")
    }

    // ── Query helpers ───────────────────────────────────────────

    /** Get resources for a specific course code (canonical, alias-aware). */
    fun getForCourse(code: String): List<Resource> {
        return _resources.value[CourseRepository.resolveCode(code)] ?: emptyList()
    }

    /** Get resources for all courses in a level (including level-wide). */
    fun getForLevel(level: String): List<Resource> {
        val result = mutableListOf<Resource>()
        for ((key, list) in _resources.value) {
            if (key == "__LEVEL__") {
                result.addAll(list.filter { it.level == level })
            } else {
                // Include all resources for courses in this level
                val course = CourseRepository.findCourse(key)
                if (course?.level == level) {
                    result.addAll(list)
                }
            }
        }
        return result
    }

    /** Get level-wide textbooks for a specific level. */
    fun getLevelTextbooks(level: String): List<Resource> {
        return (_resources.value["__LEVEL__"] ?: emptyList())
            .filter { it.level == level && it.resourceType == "TB" }
    }

    /** Check if a course has a specific resource type (canonical, alias-aware). */
    fun hasType(courseCode: String, type: String): Boolean {
        return _resources.value[CourseRepository.resolveCode(courseCode)]?.any { it.resourceType == type } == true
    }

    /** Check if a course has any resources at all (canonical, alias-aware). */
    fun hasAny(courseCode: String): Boolean {
        val key = CourseRepository.resolveCode(courseCode)
        return _resources.value.containsKey(key) &&
               _resources.value[key]?.isNotEmpty() == true
    }

    /** Find existing resource(s) for a course that match the given MD5 checksum. */
    fun findByMd5(courseCode: String, md5: String): List<Resource> {
        if (md5.isBlank()) return emptyList()
        return (_resources.value[CourseRepository.resolveCode(courseCode)] ?: emptyList())
            .filter { it.md5Checksum == md5 && it.md5Checksum.isNotBlank() }
    }

    /** Find existing resource by Drive file ID. */
    fun findByFileId(fileId: String): Resource? {
        for ((_, list) in _resources.value) {
            val match = list.firstOrNull { it.fileId == fileId && it.fileId.isNotBlank() }
            if (match != null) return match
        }
        return null
    }

    // ── Mutations ───────────────────────────────────────────────

    /**
     * Add a resource. Writes to Firestore `resources/{autoId}` then updates
     * the in-memory cache.
     */
    suspend fun add(
        resource: Resource,
        approvedBy: String = ""
    ) {
        val toSave = resource.copy(approvedBy = approvedBy, approvedAt = System.currentTimeMillis())

        // Map for Firestore (exclude id field)
        val data = mapOf(
            "courseCode" to toSave.courseCode,
            "resourceType" to toSave.resourceType,
            "level" to toSave.level,
            "masterUrl" to toSave.masterUrl,
            "fileId" to toSave.fileId,
            "md5Checksum" to toSave.md5Checksum,
            "label" to toSave.label,
            "fileName" to toSave.fileName,
            "submittedBy" to toSave.submittedBy,
            "notes" to toSave.notes,
            "approvedBy" to toSave.approvedBy,
            "approvedAt" to toSave.approvedAt
        )

        try {
            val docRef = firestore.collection(RESOURCES_COL).add(data).await()
            val saved = toSave.copy(id = docRef.id)
            // Update in-memory (canonical key — variant codes resolve to their shelf)
            val key = if (saved.courseCode.isNotBlank()) CourseRepository.resolveCode(saved.courseCode) else "__LEVEL__"
            val current = _resources.value.toMutableMap()
            val list = (current[key] ?: emptyList()).toMutableList()
            list.add(saved)
            current[key] = list
            _resources.value = current
            Log.d(TAG, "Added resource ${docRef.id} for ${saved.courseCode}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add resource", e)
            // Fallback: in-memory only
            val key = if (toSave.courseCode.isNotBlank()) CourseRepository.resolveCode(toSave.courseCode) else "__LEVEL__"
            val current = _resources.value.toMutableMap()
            val list = (current[key] ?: emptyList()).toMutableList()
            list.add(toSave)
            current[key] = list
            _resources.value = current
        }
    }

    /**
     * Remove a resource by ID.
     */
    suspend fun remove(id: String) {
        try {
            firestore.collection(RESOURCES_COL).document(id).delete().await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete resource $id", e)
        }

        // Update in-memory
        val current = _resources.value.toMutableMap()
        for ((key, list) in current) {
            val filtered = list.filter { it.id != id }
            if (filtered.size != list.size) {
                current[key] = filtered
            }
        }
        _resources.value = current
    }

    /**
     * Remove all resources for a course code.
     */
    suspend fun removeAllForCourse(courseCode: String) {
        try {
            val snap = firestore.collection(RESOURCES_COL)
                .whereEqualTo("courseCode", courseCode)
                .get().await()
            for (doc in snap.documents) {
                doc.reference.delete().await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove resources for $courseCode", e)
        }

        val current = _resources.value.toMutableMap()
        current.remove(CourseRepository.resolveCode(courseCode))
        _resources.value = current
    }
}
