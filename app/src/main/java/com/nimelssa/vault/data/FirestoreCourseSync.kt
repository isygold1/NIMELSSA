package com.nimelssa.vault.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Syncs course resources (lecture notes, past questions, notes, submittedBy)
 * with a Firestore collection so they survive app restarts and are available
 * offline via Firestore's built-in disk persistence.
 *
 * Collection: `course_resources/{courseCode}`
 */
object FirestoreCourseSync {
    private const val TAG = "FirestoreCourseSync"
    private const val COLLECTION = "course_resources"
    private val firestore get() = FirebaseFirestore.getInstance()

    /**
     * Called once on app startup. Loads all `course_resources` docs from Firestore
     * and merges them into the hardcoded [CourseRepository] courses.
     */
    suspend fun loadAll() {
        try {
            val snapshots = firestore.collection(COLLECTION).get().await()
            for (doc in snapshots.documents) {
                val code = doc.id
                val lectureNotesUrl = doc.getString("lectureNotesUrl") ?: ""
                val pastQuestionsUrl = doc.getString("pastQuestionsUrl") ?: ""
                val submittedBy = doc.getString("submittedBy") ?: ""
                val notes = doc.getString("notes") ?: ""

                // Merge resource data into the existing hardcoded course
                CourseRepository.mergeCourseResources(
                    code = code,
                    lectureNotesUrl = lectureNotesUrl,
                    pastQuestionsUrl = pastQuestionsUrl,
                    submittedBy = submittedBy,
                    notes = notes
                )
            }
            Log.d(TAG, "Loaded ${snapshots.size()} course resources from Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load course resources from Firestore", e)
        }
    }

    /**
     * Saves or updates a course's resource fields in Firestore.
     * Called when a proposal is approved.
     */
    suspend fun saveResources(
        code: String,
        lectureNotesUrl: String,
        pastQuestionsUrl: String,
        submittedBy: String,
        notes: String
    ) {
        try {
            val data = mapOf(
                "lectureNotesUrl" to lectureNotesUrl,
                "pastQuestionsUrl" to pastQuestionsUrl,
                "submittedBy" to submittedBy,
                "notes" to notes,
                "lastUpdated" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            firestore.collection(COLLECTION).document(code).set(data).await()
            Log.d(TAG, "Saved resources for $code")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save resources for $code", e)
        }
    }

    /**
     * Removes a course's resource document from Firestore.
     * Called when a course is deleted or a proposal is rejected.
     */
    suspend fun removeResources(code: String) {
        try {
            firestore.collection(COLLECTION).document(code).delete().await()
            Log.d(TAG, "Removed resources for $code")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove resources for $code", e)
        }
    }

    /**
     * Clears the lectureNotesUrl or pastQuestionsUrl field.
     */
    suspend fun clearField(code: String, field: String) {
        try {
            firestore.collection(COLLECTION).document(code)
                .update(field, "")
                .await()
            Log.d(TAG, "Cleared field $field for $code")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear field $field for $code", e)
        }
    }
}
