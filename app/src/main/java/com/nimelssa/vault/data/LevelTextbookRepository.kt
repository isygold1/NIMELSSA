package com.nimelssa.vault.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Manages level-wide textbooks (general references not tied to a specific course).
 *
 * Collection: `level_textbooks/{level}`
 * Each document stores a list of textbook entries under a `textbooks` array field.
 */
object LevelTextbookRepository {
    private const val TAG = "LevelTextbookRepository"
    private const val COLLECTION = "level_textbooks"
    private val firestore get() = FirebaseFirestore.getInstance()

    private val _textbooks = MutableStateFlow<Map<String, List<LevelTextbook>>>(emptyMap())
    val textbooks: StateFlow<Map<String, List<LevelTextbook>>> = _textbooks.asStateFlow()

    /** Load all level textbooks from Firestore on app start. */
    suspend fun loadAll() {
        try {
            val snap = firestore.collection(COLLECTION).get().await()
            val map = mutableMapOf<String, List<LevelTextbook>>()
            for (doc in snap.documents) {
                val level = doc.id
                val list = doc.get("textbooks") as? List<Map<String, Any>> ?: continue
                val textbooks = list.map { entry ->
                    LevelTextbook(
                        level = level,
                        masterFolderUrl = (entry["masterFolderUrl"] as? String) ?: "",
                        label = (entry["label"] as? String) ?: "",
                        submittedBy = (entry["submittedBy"] as? String) ?: "",
                        notes = (entry["notes"] as? String) ?: ""
                    )
                }
                map[level] = textbooks
            }
            _textbooks.value = map
            Log.d(TAG, "Loaded level textbooks for ${map.size} levels")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load level textbooks", e)
        }
    }

    /** Add a textbook to a specific level's list. */
    suspend fun add(level: String, textbook: LevelTextbook) {
        val entry = mapOf(
            "masterFolderUrl" to textbook.masterFolderUrl,
            "label" to textbook.label,
            "submittedBy" to textbook.submittedBy,
            "notes" to textbook.notes
        )

        try {
            val docRef = firestore.collection(COLLECTION).document(level)
            // Append to the array
            docRef.update("textbooks", com.google.firebase.firestore.FieldValue.arrayUnion(entry)).await()
            Log.d(TAG, "Added textbook to level $level")
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                // Document doesn't exist yet — create it
                try {
                    firestore.collection(COLLECTION).document(level)
                        .set(mapOf("textbooks" to listOf(entry)))
                        .await()
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to create level textbook doc", e2)
                }
            } else {
                Log.e(TAG, "Firestore error adding textbook to level $level", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add textbook to level $level", e)
        }

        // Update in-memory
        val current = _textbooks.value.toMutableMap()
        val list = (current[level] ?: emptyList()).toMutableList()
        list.add(textbook)
        current[level] = list
        _textbooks.value = current
    }

    /** Remove a textbook from a level. */
    suspend fun remove(level: String, label: String, masterFolderUrl: String) {
        val entry = mapOf(
            "masterFolderUrl" to masterFolderUrl,
            "label" to label
        )

        try {
            firestore.collection(COLLECTION).document(level)
                .update("textbooks", com.google.firebase.firestore.FieldValue.arrayRemove(entry))
                .await()
            Log.d(TAG, "Removed textbook from level $level")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove textbook", e)
        }

        // Update in-memory
        val current = _textbooks.value.toMutableMap()
        val list = (current[level] ?: emptyList()).filter {
            !(it.label == label && it.masterFolderUrl == masterFolderUrl)
        }
        current[level] = list
        _textbooks.value = current
    }

    /** Get textbooks for a specific level. */
    fun getForLevel(level: String): List<LevelTextbook> {
        return _textbooks.value[level] ?: emptyList()
    }
}
