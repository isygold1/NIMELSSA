package com.nimelssa.vault.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CourseRepository {
    private val _courses = MutableStateFlow(
        listOf(
            Course("BIO 101", "Invertebrate Zoology Notes", "BIOLOGY", "100", 1, 80),
            Course("MLS 201", "Intro to Medical Laboratory Science", "MEDICAL LABORATORY SCIENCE", "200", 1, 45),
            Course("BIO 221", "General Ecology Overview Guide", "BIOLOGY", "200", 1, 100),
            Course("CHM 212", "Basic Organic Chemistry Manuals", "CHEMISTRY", "200", 2, 0)
        )
    )

    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    fun getFiltered(level: String, semester: Int): List<Course> {
        return _courses.value.filter { it.level == level && it.semester == semester }
    }

    fun addCourse(course: Course) {
        _courses.value = _courses.value + course
    }

    fun findCourse(code: String): Course? {
        return _courses.value.find { it.code == code }
    }

    fun toggleOffline(code: String) {
        _courses.value = _courses.value.map {
            if (it.code == code) it.copy(isOffline = !it.isOffline) else it
        }
    }

    fun getCategories(level: String, semester: Int): List<String> {
        return _courses.value
            .filter { it.level == level && it.semester == semester }
            .map { it.category }
            .distinct()
    }
}
