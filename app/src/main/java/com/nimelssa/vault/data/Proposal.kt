package com.nimelssa.vault.data

data class Proposal(
    val id: String = "",
    val driveUrl: String = "",
    val courseCode: String = "",
    val courseName: String = "",
    val category: String = "",
    val level: String = "",
    val semester: Int = 1,
    val type: String = "Lecture Notes",
    val notes: String = "",
    val submittedBy: String = "",        // uid
    val submittedByName: String = "",    // display name
    val status: String = "pending",      // "pending" | "approved" | "rejected"
    val aiClassified: Boolean = false    // true if Claude auto-filled the fields
)
