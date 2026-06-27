package com.nimelssa.vault.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Manages pending proposals (Drive links submitted by students).
 * Stores in Firestore for persistence, maintains an in-memory cache.
 *
 * Collection: `proposals/{proposalId}`
 */
object ProposalRepository {
    private const val TAG = "ProposalRepository"
    private const val COLLECTION = "proposals"
    private val firestore get() = FirebaseFirestore.getInstance()

    private val _proposals = MutableStateFlow<List<Proposal>>(emptyList())
    val proposals: StateFlow<List<Proposal>> = _proposals.asStateFlow()

    /** Load all pending proposals from Firestore on app start. */
    suspend fun loadAll() {
        try {
            val snap = firestore.collection(COLLECTION)
                .whereEqualTo("status", "pending")
                .orderBy("submittedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()

            val list = snap.documents.mapNotNull { doc ->
                doc.toObject(Proposal::class.java)?.copy(id = doc.id)
            }
            _proposals.value = list
            Log.d(TAG, "Loaded ${list.size} pending proposals from Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load proposals", e)
        }
    }

    /** Submit a new proposal. Returns the generated ID. */
    suspend fun submit(driveLink: String, notes: String, submittedBy: String, submittedByName: String): String {
        val proposal = Proposal(
            id = "",  // Firestore will generate
            submittedBy = submittedBy,
            submittedByName = submittedByName,
            submittedAt = System.currentTimeMillis(),
            driveLink = driveLink,
            notes = notes,
            status = "pending"
        )

        try {
            val docRef = firestore.collection(COLLECTION).add(proposal).await()
            val newProposal = proposal.copy(id = docRef.id)
            _proposals.value = listOf(newProposal) + _proposals.value
            Log.d(TAG, "Proposal submitted: ${docRef.id}")
            return docRef.id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit proposal", e)
            // Fall back to in-memory only
            val localId = UUID.randomUUID().toString()
            val localProposal = proposal.copy(id = localId)
            _proposals.value = listOf(localProposal) + _proposals.value
            return localId
        }
    }

    /** Get all pending proposals. */
    fun getPending(): List<Proposal> {
        return _proposals.value.filter { it.status == "pending" }
    }

    /** Get a specific proposal by ID. */
    fun getById(id: String): Proposal? {
        return _proposals.value.find { it.id == id }
    }

    /** Update a proposal's AI preview after scanning. */
    fun updateAiPreview(proposalId: String, preview: AiPreview) {
        _proposals.value = _proposals.value.map {
            if (it.id == proposalId) it.copy(aiPreview = preview) else it
        }
    }

    /**
     * Approve a proposal. Removes from pending list and returns the
     * AiPreview for the caller to process into course resources.
     */
    suspend fun approve(proposalId: String, reviewedBy: String): Proposal? {
        val proposal = _proposals.value.find { it.id == proposalId } ?: return null

        val updated = proposal.copy(
            status = "approved",
            reviewedBy = reviewedBy,
            reviewedAt = System.currentTimeMillis()
        )

        // Update in-memory
        _proposals.value = _proposals.value.map {
            if (it.id == proposalId) updated else it
        }

        // Update Firestore
        try {
            firestore.collection(COLLECTION).document(proposalId)
                .update(
                    mapOf(
                        "status" to "approved",
                        "reviewedBy" to reviewedBy,
                        "reviewedAt" to System.currentTimeMillis()
                    )
                ).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update Firestore for approval", e)
        }

        return updated
    }

    /**
     * Reject a proposal. Removes from the list entirely.
     */
    suspend fun reject(proposalId: String) {
        _proposals.value = _proposals.value.filter { it.id != proposalId }

        try {
            firestore.collection(COLLECTION).document(proposalId).delete().await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete rejected proposal from Firestore", e)
        }
    }

    /** Count of pending proposals. */
    fun pendingCount(): Int = _proposals.value.count { it.status == "pending" }
}
