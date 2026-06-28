package com.nimelssa.vault.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Handles all Firestore reads/writes for the `proposals` collection.
 *
 * Collection structure: proposals/{autoId}
 * {
 *   driveUrl, courseCode, courseName, category, level, semester,
 *   type, notes, submittedBy (uid), submittedByName,
 *   status: "pending" | "approved" | "rejected",
 *   aiClassified: Boolean,
 *   createdAt: Timestamp
 * }
 */
object ProposalRepository {
    private const val TAG = "ProposalRepository"
    private const val COLLECTION = "proposals"
    private val db get() = FirebaseFirestore.getInstance()

    /**
     * Real-time stream of pending proposals for a given level.
     * Pass level = null to get ALL levels (admin use).
     */
    fun pendingProposals(level: String?): Flow<List<Proposal>> = callbackFlow {
        var query: Query = db.collection(COLLECTION)
            .whereEqualTo("status", "pending")

        // Scope to level if provided (rep view); admins pass null for all
        if (level != null) {
            query = query.whereEqualTo("level", level)
        }

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Proposals snapshot error", error)
                return@addSnapshotListener
            }
            val proposals = snapshot?.documents?.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                Proposal(
                    id = doc.id,
                    driveUrl = data["driveUrl"] as? String ?: "",
                    courseCode = data["courseCode"] as? String ?: "",
                    courseName = data["courseName"] as? String ?: "",
                    category = data["category"] as? String ?: "",
                    level = data["level"] as? String ?: "",
                    semester = (data["semester"] as? Long)?.toInt() ?: 1,
                    type = data["type"] as? String ?: "Lecture Notes",
                    notes = data["notes"] as? String ?: "",
                    submittedBy = data["submittedBy"] as? String ?: "",
                    submittedByName = data["submittedByName"] as? String ?: "",
                    status = data["status"] as? String ?: "pending",
                    aiClassified = data["aiClassified"] as? Boolean ?: false
                )
            } ?: emptyList()
            trySend(proposals)
        }
        awaitClose { listener.remove() }
    }

    /**
     * Submits a new proposal to Firestore.
     * Returns the generated document ID on success.
     */
    suspend fun submitProposal(proposal: Proposal): String {
        val data = hashMapOf(
            "driveUrl" to proposal.driveUrl,
            "courseCode" to proposal.courseCode,
            "courseName" to proposal.courseName,
            "category" to proposal.category,
            "level" to proposal.level,
            "semester" to proposal.semester,
            "type" to proposal.type,
            "notes" to proposal.notes,
            "submittedBy" to proposal.submittedBy,
            "submittedByName" to proposal.submittedByName,
            "status" to "pending",
            "aiClassified" to proposal.aiClassified,
            "createdAt" to FieldValue.serverTimestamp()
        )
        val ref = db.collection(COLLECTION).add(data).await()
        Log.d(TAG, "Proposal submitted: ${ref.id}")
        return ref.id
    }

    /**
     * Approves a proposal:
     * 1. Updates proposal status to "approved"
     * 2. Writes the resource to course_resources/{courseCode}
     */
    suspend fun approveProposal(proposal: Proposal, approvedByUid: String) {
        // 1. Update proposal status
        db.collection(COLLECTION).document(proposal.id)
            .update(
                mapOf(
                    "status" to "approved",
                    "reviewedBy" to approvedByUid,
                    "reviewedAt" to FieldValue.serverTimestamp()
                )
            ).await()

        // 2. Write to course_resources so it appears in Workspace
        val isLectureNotes = proposal.type == "Lecture Notes"
        val resourceData = mapOf(
            "courseCode" to proposal.courseCode,
            "courseName" to proposal.courseName,
            "category" to proposal.category,
            "level" to proposal.level,
            "semester" to proposal.semester,
            "lectureNotesUrl" to if (isLectureNotes) proposal.driveUrl else "",
            "pastQuestionsUrl" to if (!isLectureNotes) proposal.driveUrl else "",
            "submittedBy" to proposal.submittedByName,
            "notes" to proposal.notes,
            "approvedBy" to approvedByUid,
            "approvedAt" to FieldValue.serverTimestamp()
        )

        // WHY: Use courseCode as document ID so multiple resources for the same
        // course merge rather than create duplicate entries.
        db.collection("course_resources").document(proposal.courseCode)
            .set(resourceData, com.google.firebase.firestore.SetOptions.merge())
            .await()

        Log.d(TAG, "Proposal approved and resource published: ${proposal.courseCode}")
    }

    /**
     * Rejects a proposal — updates status only, keeps record for audit.
     */
    suspend fun rejectProposal(proposalId: String, rejectedByUid: String) {
        db.collection(COLLECTION).document(proposalId)
            .update(
                mapOf(
                    "status" to "rejected",
                    "reviewedBy" to rejectedByUid,
                    "reviewedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Log.d(TAG, "Proposal rejected: $proposalId")
    }
}
