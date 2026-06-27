package com.nimelssa.vault.data

import java.util.Date

data class Report(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val category: String = "",   // "app_bug", "course_issue", "other"
    val courseCode: String = "",
    val message: String = "",
    val timestamp: Date = Date(),
    val status: String = "pending"  // "pending", "resolved"
) {
    val displayCategory: String get() = when (category) {
        "app_bug" -> "🐛 App Bug"
        "course_issue" -> "📚 Course Issue"
        "other" -> "📋 Other"
        else -> category
    }
}
