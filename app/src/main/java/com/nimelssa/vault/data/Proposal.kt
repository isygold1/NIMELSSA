package com.nimelssa.vault.data

/**
 * A proposal submitted by a student containing a Google Drive link.
 * The student selects which level the resource is for (targetLevel),
 * so the correct rep sees it. The AI scans the link to identify
 * course codes and resource types before approval.
 */
data class Proposal(
    val id: String = "",
    val submittedBy: String = "",           // student email
    val submittedByName: String = "",       // student display name
    val submittedAt: Long = System.currentTimeMillis(),
    val driveLink: String = "",             // the Google Drive link (file or folder)
    val notes: String = "",
    val targetLevel: String = "",           // the level this concerns (e.g. "200"); rep sees proposals where targetLevel == their repLevel
    val status: String = "pending",         // pending / approved / rejected
    val aiPreview: AiPreview? = null,       // populated after AI scan
    val reviewedBy: String? = null,
    val reviewedAt: Long? = null
)

/**
 * Result of an AI scan on a Drive link.
 * Contains matched resources and any files that couldn't be parsed.
 */
data class AiPreview(
    val scanStatus: String = "pending",     // scanning / success / partial / failed
    val matchedItems: List<AiMatchedItem> = emptyList(),
    val unmatchedFiles: List<AiUnmatchedFile> = emptyList(),
    val totalFilesScanned: Int = 0,
    val sourceFolderName: String = "",
    val errorMessage: String = ""
)

/**
 * A file that was successfully matched to a course code and resource type.
 */
data class AiMatchedItem(
    val courseCode: String,
    val courseName: String = "",            // looked up from CourseRepository
    val level: String = "",                 // e.g., "200"
    val semester: Int = 1,
    val resourceType: String = "",          // "LN" or "PQ"
    val resourceLabel: String = "",         // "Lecture Notes" / "Past Questions"
    val fileName: String = "",
    val fileId: String = ""                 // Drive file ID
)

/**
 * A file that the AI could not match to any course code.
 */
data class AiUnmatchedFile(
    val fileName: String = "",
    val reason: String = "",                // "no course code found", "ambiguous name"
    val fileId: String = ""
)
