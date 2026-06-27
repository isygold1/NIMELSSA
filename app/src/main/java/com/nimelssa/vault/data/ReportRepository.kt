package com.nimelssa.vault.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date

object ReportRepository {
    private const val COLLECTION = "reports"
    private val firestore get() = FirebaseFirestore.getInstance()

    /** Submit a new report */
    suspend fun submitReport(report: Report) {
        val data = hashMapOf(
            "userId" to report.userId,
            "userEmail" to report.userEmail,
            "category" to report.category,
            "courseCode" to report.courseCode,
            "message" to report.message,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "status" to "pending"
        )
        firestore.collection(COLLECTION).add(data).await()
    }

    /** Fetch all reports (admin: all; rep: course issues only) */
    suspend fun getReports(isAdmin: Boolean): List<Report> {
        return try {
            val query: Query = if (isAdmin) {
                firestore.collection(COLLECTION)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
            } else {
                firestore.collection(COLLECTION)
                    .whereEqualTo("category", "course_issue")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
            }
            val snapshots = query.get().await()
            snapshots.documents.mapNotNull { doc ->
                val id = doc.id
                val userId = doc.getString("userId") ?: return@mapNotNull null
                Report(
                    id = id,
                    userId = userId,
                    userEmail = doc.getString("userEmail") ?: "",
                    category = doc.getString("category") ?: "other",
                    courseCode = doc.getString("courseCode") ?: "",
                    message = doc.getString("message") ?: "",
                    timestamp = doc.getTimestamp("timestamp")?.toDate() ?: Date(),
                    status = doc.getString("status") ?: "pending"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Mark a report as resolved */
    suspend fun resolveReport(reportId: String) {
        try {
            firestore.collection(COLLECTION).document(reportId)
                .update("status", "resolved").await()
        } catch (_: Exception) { }
    }
}
