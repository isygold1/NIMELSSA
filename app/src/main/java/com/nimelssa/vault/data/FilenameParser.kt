package com.nimelssa.vault.data

import java.util.Locale

/**
 * Parses filenames to extract course code and resource type.
 *
 * Primary convention (strict):  CourseCode_Type.ext
 *   e.g., CSC101_LN.pdf, MTH201_PQ_v2.pdf, MLS301_TB.pdf
 *
 * Type codes:
 *   LN = Lecture Notes / Slides
 *   PQ = Past Questions / Test Papers
 *   TB = Textbook / Reference
 *   OT = Other / Miscellaneous
 *
 * If strict regex fails, fuzzy matching is attempted:
 *   - "CSC 101 Notes.pdf"      → CSC101, LN
 *   - "Maths 201 Past Q.pdf"   → MTH201, PQ
 *   - "MLS 301 Lecture.pdf"    → MLS301, LN
 */
object FilenameParser {

    // Strict regex: 3 uppercase letters + 3 digits + underscore + type code
    private val STRICT_REGEX = Regex("^([A-Z]{3})(\\d{3})_(LN|PQ|TB|OT)[._].*$", RegexOption.IGNORE_CASE)

    // Loose regex: captures any 2-4 uppercase letters followed by optional space and 3 digits
    private val LOOSE_CODE_REGEX = Regex("([A-Z]{2,4})\\s*(\\d{3})", RegexOption.IGNORE_CASE)

    // Known MLS course code prefixes mapped to full codes
    private val KNOWN_PREFIXES = mapOf(
        "BIO" to "BIO",
        "CHM" to "CHM",
        "PHY" to "PHY",
        "MLS" to "MLS",
        "GST" to "GST",
        "CSC" to "CSC",
        "STA" to "STA",
        "BCH" to "BCH",
        "MTH" to "MTH",  // Mathematics sometimes uses MTH
        "MAT" to "MTH",  // or MAT
        "ENG" to "GST",  // English → GST
        "HIS" to "GST",  // History → GST
        "PLS" to "GST",  // Pol Science → GST
        "ECO" to "GST",  // Economics → GST (general studies if 1xx level)
    )

    // Keywords that hint at resource type
    private val LN_KEYWORDS = listOf(
        "note", "lecture", "slide", "classnote", "lesson", "module", "tutorial", "handout"
    )
    private val PQ_KEYWORDS = listOf(
        "past", "question", "exam", "test", "pq", "practice", "quiz", "assignment"
    )
    private val TB_KEYWORDS = listOf(
        "textbook", "book", "reference", "tb", "manual", "guide", "handbook", "scholar"
    )

    // Semester hints in filename
    private val SEMESTER_1_KEYWORDS = listOf("sem1", "semester1", "firstsem", "1stsem")
    private val SEMESTER_2_KEYWORDS = listOf("sem2", "semester2", "secondsem", "2ndsem")

    /**
     * Result of parsing a single filename.
     */
    data class ParseResult(
        val courseCode: String?,
        val resourceType: String?,    // "LN", "PQ", "TB", "OT"
        val semester: Int?,           // 1 or 2, null if unknown
        val confidence: Confidence,
        val reason: String = ""
    )

    enum class Confidence { HIGH, MEDIUM, LOW, NONE }

    /**
     * Parse a single filename and return the best guess.
     */
    fun parse(fileName: String): ParseResult {
        val name = fileName.trim()

        // 1. Try strict regex first
        STRICT_REGEX.matchEntire(name)?.let { m ->
            val prefix = m.groupValues[1].uppercase()
            val number = m.groupValues[2]
            val type = m.groupValues[3].uppercase()
            val code = "$prefix$number"
            return ParseResult(
                courseCode = code,
                resourceType = type,
                semester = inferSemester(name, code),
                confidence = Confidence.HIGH,
                reason = "Strict regex match: $code, type=$type"
            )
        }

        // 2. Try loose regex with prefix lookup
        val codeMatch = LOOSE_CODE_REGEX.find(name)
        if (codeMatch != null) {
            val prefix = codeMatch.groupValues[1].uppercase()
            val number = codeMatch.groupValues[2]
            val mappedPrefix = KNOWN_PREFIXES[prefix] ?: prefix
            val code = "$mappedPrefix$number"
            val type = inferType(name)
            val confidence = if (prefix in KNOWN_PREFIXES) Confidence.MEDIUM else Confidence.LOW
            return ParseResult(
                courseCode = code,
                resourceType = type,
                semester = inferSemester(name, code),
                confidence = confidence,
                reason = "Fuzzy match: $code, type=$type, confidence=$confidence"
            )
        }

        // 3. Nothing found
        return ParseResult(
            courseCode = null,
            resourceType = null,
            semester = null,
            confidence = Confidence.NONE,
            reason = "Could not extract course code from filename"
        )
    }

    /**
     * Infer resource type from filename keywords.
     */
    private fun inferType(name: String): String {
        val lower = name.lowercase(Locale.ROOT)
        // Check for explicit type codes embedded in name
        if ("_ln" in lower || "_ln_" in lower) return "LN"
        if ("_pq" in lower || "_pq_" in lower) return "PQ"
        if ("_tb" in lower || "_tb_" in lower) return "TB"

        // Check keywords
        val lnScore = LN_KEYWORDS.count { lower.contains(it) }
        val pqScore = PQ_KEYWORDS.count { lower.contains(it) }
        val tbScore = TB_KEYWORDS.count { lower.contains(it) }

        return when {
            lnScore > pqScore && lnScore > tbScore -> "LN"
            pqScore > lnScore && pqScore > tbScore -> "PQ"
            tbScore > lnScore && tbScore > pqScore -> "TB"
            lnScore > 0 -> "LN"
            pqScore > 0 -> "PQ"
            tbScore > 0 -> "TB"
            else -> "OT"   // Other/unknown
        }
    }

    /**
     * Try to infer semester from filename or course code.
     */
    private fun inferSemester(name: String, courseCode: String): Int? {
        val lower = name.lowercase(Locale.ROOT)

        // Check explicit semester keywords
        if (SEMESTER_1_KEYWORDS.any { lower.contains(it) }) return 1
        if (SEMESTER_2_KEYWORDS.any { lower.contains(it) }) return 2

        // Check the last digit of the course number:
        // Courses ending in odd (101, 201, 301) → semester 1 typically
        // Courses ending in even + not 0 (102, 202, 302) → semester 2 typically
        val numberPart = courseCode.filter { it.isDigit() }
        if (numberPart.length >= 3) {
            val lastDigit = numberPart.last().digitToIntOrNull() ?: return null
            return when {
                lastDigit == 1 -> 1
                lastDigit == 2 -> 2
                else -> null
            }
        }

        return null
    }
}
