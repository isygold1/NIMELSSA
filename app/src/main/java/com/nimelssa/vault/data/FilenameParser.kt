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
        "note", "lecture", "slide", "classnote", "lesson", "module", "tutorial", "handout",
        // Lecture venues at University of Ilorin — files named by venue are
        // lecture materials: SLT/NSLT (Science Lecture Theatre), LT1/LT2
        // (Lecture Theatre), LhA/LhB (Lecture Hall A/B).
        "slt", "nslt", "nlst", "lt1", "lt2", "lha", "lhb", "lecture hall", "lecture theatre"
    )
    private val PQ_KEYWORDS = listOf(
        "past", "question", "exam", "test", "pq", "practice", "quiz", "assignment",
        "mcq", "q&a"
    )
    private val TB_KEYWORDS = listOf(
        "textbook", "book", "reference", "tb", "manual", "guide", "handbook", "scholar"
    )

    // Semester hints in filename
    private val SEMESTER_1_KEYWORDS = listOf("sem1", "semester1", "firstsem", "1stsem")
    private val SEMESTER_2_KEYWORDS = listOf("sem2", "semester2", "secondsem", "2ndsem")

    /**
     * Result of parsing a single filename (with optional path context).
     */
    data class ParseResult(
        val courseCode: String?,
        val resourceType: String?,    // "LN", "PQ", "TB", "OT"
        val level: String? = null,    // "100", "200", etc. (inferred from path or code)
        val semester: Int? = null,    // 1 or 2, null if unknown
        val confidence: Confidence,
        val reason: String = ""
    )

    enum class Confidence { HIGH, MEDIUM, LOW, NONE }

    /**
     * Parse a single filename (no path context).
     */
    fun parse(fileName: String): ParseResult {
        return parseWithPath(fileName, "")
    }

    /**
     * Parse a filename **with folder path context**.
     *
     * The path contains the full folder hierarchy e.g.:
     *   "NIMELSSA Academic Hub / 200 level / MLS 201"
     *
     * This lets the AI extract:
     *   - Level from "200 level" folder name
     *   - Course code from "MLS 201" folder name
     *   - Resource type from "Lecture Notes" folder name
     *
     * Strategy (in order of precedence):
     *   1. Path level + path course code + filename type   (HIGH)
     *   2. Path level + filename course code + type        (MEDIUM)
     *   3. Filename only                                  (LOW / NONE)
     */
    fun parseWithPath(fileName: String, path: String): ParseResult {
        val name = fileName.trim()

        // ── Extract level from path ──
        // Looks for "100 level", "200L", "300 Level" in the path
        val levelFromPath = extractLevelFromPath(path)

        // ── Extract resource type from path ──
        // Only segments at-or-after the level folder are considered, so a root
        // folder like "TEXTBOOKS*" can't force TB onto everything beneath it.
        val typeFromPath = extractTypeFromPath(path)

        // ── Extract semester from path ──
        // Looks for "first semester", "2nd semester" etc. in the path
        val semesterFromPath = extractSemesterFromPath(path)

        // ── Extract course code from path ──
        // Looks for "MLS 201", "CSC 101" in path segments
        val codeFromPath = extractCodeFromPath(path)

        // ── Now try filename parsing ──
        val strictResult = tryStrictMatch(name)
        if (strictResult != null) {
            // Strict filename match trumps path
            val level = levelFromPath ?: inferLevelFromCode(strictResult.code)
            return ParseResult(
                courseCode = strictResult.code,
                resourceType = typeFromPath ?: strictResult.type,
                level = level,
                semester = semesterFromPath ?: inferSemester(name, strictResult.code),
                confidence = Confidence.HIGH,
                reason = "Strict filename: ${strictResult.code}, type=${strictResult.type}" +
                        (if (levelFromPath != null) ", level from path" else "")
            )
        }

        // Try loose filename match
        val looseResult = tryLooseMatch(name)
        if (looseResult != null) {
            val level = levelFromPath ?: inferLevelFromCode(looseResult.code)

            // Folder agrees with the filename code → the curated folder name
            // confirms the match even when the filename prefix is unrecognized
            // (e.g. "COS101 CBT CA Questions.pdf" inside the COS101 folder).
            if (codeFromPath == looseResult.code) {
                return ParseResult(
                    courseCode = looseResult.code,
                    resourceType = typeFromPath ?: looseResult.type,
                    level = level,
                    semester = semesterFromPath ?: inferSemester(name, looseResult.code),
                    confidence = Confidence.MEDIUM,
                    reason = "Loose filename + folder agree: ${looseResult.code}"
                )
            }

            if (codeFromPath != null) {
                // Filename and folder disagree about the course.
                if (looseResult.confidence == Confidence.LOW) {
                    // Unknown-prefix filename code (e.g. "WA000" from
                    // "DOC-20251102-WA0003..pptx") — the curated folder name is
                    // the stronger signal; trust it over the filename.
                    return ParseResult(
                        courseCode = codeFromPath,
                        resourceType = typeFromPath ?: looseResult.type,
                        level = level,
                        semester = semesterFromPath ?: inferSemester(name, codeFromPath),
                        confidence = if (levelFromPath != null) Confidence.MEDIUM else Confidence.LOW,
                        reason = "Folder name $codeFromPath wins over unrecognized filename code ${looseResult.code}"
                    )
                }
                // Known-prefix disagreement is truly ambiguous (e.g. "MLS 210"
                // inside "MLS 201") — don't guess, surface both candidates.
                return ParseResult(
                    courseCode = null,
                    resourceType = typeFromPath ?: looseResult.type,
                    level = level,
                    semester = semesterFromPath ?: inferSemester(name, looseResult.code),
                    confidence = Confidence.NONE,
                    reason = "Ambiguous: filename says ${looseResult.code} but folder says $codeFromPath"
                )
            }

            return ParseResult(
                courseCode = looseResult.code,
                resourceType = typeFromPath ?: looseResult.type,
                level = level,
                semester = semesterFromPath ?: inferSemester(name, looseResult.code),
                confidence = looseResult.confidence,
                reason = "Loose filename: ${looseResult.code}" +
                        (if (looseResult.confidence == Confidence.LOW) " (unrecognized code prefix)" else "") +
                        (if (typeFromPath != null) ", type from path" else "") +
                        (if (levelFromPath != null) ", level from path" else "")
            )
        }

        // Try path-only match (folder named after course)
        if (codeFromPath != null) {
            return ParseResult(
                courseCode = codeFromPath,
                resourceType = typeFromPath ?: "OT",
                level = levelFromPath ?: inferLevelFromCode(codeFromPath),
                semester = semesterFromPath ?: inferSemester(name, codeFromPath),
                confidence = if (levelFromPath != null) Confidence.MEDIUM else Confidence.LOW,
                reason = "Course code from folder name: $codeFromPath" +
                        (if (levelFromPath != null) ", level from path" else "")
            )
        }

        // Nothing found
        return ParseResult(
            courseCode = null,
            resourceType = typeFromPath,
            level = levelFromPath,
            semester = semesterFromPath,
            confidence = Confidence.NONE,
            reason = "Could not extract course code from filename or path"
        )
    }

    /**
     * Try the strict CourseCode_Type.ext pattern.
     */
    private fun tryStrictMatch(name: String): StrictMatch? {
        STRICT_REGEX.matchEntire(name)?.let { m ->
            val prefix = m.groupValues[1].uppercase()
            val number = m.groupValues[2]
            val type = m.groupValues[3].uppercase()
            return StrictMatch(code = "$prefix$number", type = type)
        }
        return null
    }

    /**
     * Try the loose fuzzy match.
     *
     * Word-boundary guard: skips matches that are glued to the middle of a word
     * (e.g. "TION" inside "Nutrition 300l" → rejects phantom codes like TION300).
     */
    private fun tryLooseMatch(name: String): LooseMatch? {
        for (codeMatch in LOOSE_CODE_REGEX.findAll(name)) {
            // The letters must start at the beginning of the name or after a non-letter
            val start = codeMatch.range.first
            val precededByLetter = start > 0 && name[start - 1].isLetter()
            if (precededByLetter) continue

            val prefix = codeMatch.groupValues[1].uppercase()
            val number = codeMatch.groupValues[2]
            val mappedPrefix = KNOWN_PREFIXES[prefix] ?: prefix
            val code = "$mappedPrefix$number"
            val type = inferType(name)
            val confidence = if (prefix in KNOWN_PREFIXES) Confidence.MEDIUM else Confidence.LOW
            return LooseMatch(code = code, type = type, confidence = confidence)
        }
        return null
    }

    /**
     * Extract academic level from a folder path.
     * Matches "100 level", "200L", "300 Level", "400l" etc.
     */
    private fun extractLevelFromPath(path: String): String? {
        val lower = path.lowercase(Locale.ROOT)
        val regex = Regex("""(\d{3})\s*(level|l)\b""")
        return regex.find(lower)?.groupValues?.get(1)?.let { "${it[0]}00" }
    }

    /**
     * Extract resource type from folder path segments.
     * Matches "Lecture Notes", "Past Questions", "Textbooks", etc.
     *
     * Only segments at-or-after the level folder (e.g. "200 level") are
     * inspected. This prevents a root-level folder named "TEXTBOOKS*" from
     * tagging EVERY file beneath it as TB when the real type is said by the
     * filename (e.g. ".../TEXTBOOKS/300 level/MLS 301/Notes.pdf" → LN).
     */
    private fun extractTypeFromPath(path: String): String? {
        val lower = path.lowercase(Locale.ROOT)
        val segments = lower.split("/")
        val levelRegex = Regex("""(\d{3})\s*(level|l)\b""")

        // Find the first segment containing a level marker; scan from there.
        var startIndex = 0
        for ((i, seg) in segments.withIndex()) {
            if (levelRegex.containsMatchIn(seg.trim())) {
                startIndex = i
                break
            }
        }

        for (i in startIndex until segments.size) {
            val trimmed = segments[i].trim()
            if (trimmed.contains("lecture") || trimmed.contains("note")) return "LN"
            if (trimmed.contains("past") || trimmed.contains("question") || trimmed.contains("exam")) return "PQ"
            if (trimmed.contains("textbook") || trimmed.contains("book") || trimmed.contains("reference")) return "TB"
        }
        return null
    }

    /**
     * Extract semester (1 or 2) from folder path segments.
     * Matches "first semester", "1st semester", "second semester", "2nd semester".
     */
    private fun extractSemesterFromPath(path: String): Int? {
        val lower = path.lowercase(Locale.ROOT)
        val segments = lower.split("/")
        for (seg in segments) {
            val trimmed = seg.trim()
            when {
                trimmed.contains("first semester") ||
                    trimmed.contains("1st semester") ||
                    trimmed.contains("semester 1") -> return 1
                trimmed.contains("second semester") ||
                    trimmed.contains("2nd semester") ||
                    trimmed.contains("semester 2") -> return 2
            }
        }
        return null
    }

    /**
     * Extract a course code from folder path (e.g., "MLS 201" → MLS201).
     */
    private fun extractCodeFromPath(path: String): String? {
        val segments = path.split("/")
        for (seg in segments) {
            val trimmed = seg.trim()
            val match = LOOSE_CODE_REGEX.find(trimmed) ?: continue
            val prefix = match.groupValues[1].uppercase()
            val number = match.groupValues[2]
            val mappedPrefix = KNOWN_PREFIXES[prefix] ?: prefix
            // Make sure the course code is the MAIN part of this segment
            // (e.g., "MLS 201" is the folder name)
            if (trimmed.uppercase().startsWith(prefix)) {
                return "$mappedPrefix$number"
            }
        }
        return null
    }

    private data class StrictMatch(val code: String, val type: String)
    private data class LooseMatch(val code: String, val type: String, val confidence: Confidence)

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

    /**
     * Infers the academic level (e.g., "100", "200", "300") from a course code.
     * The first digit of the numeric part determines the level.
     * E.g., "CSC101" → "100", "MLS301" → "300", "BIO201" → "200".
     */
    private fun inferLevelFromCode(courseCode: String): String {
        val digits = courseCode.filter { it.isDigit() }
        if (digits.length >= 3) {
            val hundreds = digits.first().digitToIntOrNull()
            if (hundreds != null && hundreds in 1..5) {
                return "${hundreds}00"
            }
        }
        // Fallback: try to infer from known prefix
        return when {
            courseCode.startsWith("GST") -> "100"
            courseCode.startsWith("CSC") -> "100"
            courseCode.startsWith("BIO") -> "100"
            courseCode.startsWith("CHM") -> "100"
            courseCode.startsWith("PHY") -> "100"
            courseCode.startsWith("MLS") -> "100"
            else -> "100"
        }
    }
}
