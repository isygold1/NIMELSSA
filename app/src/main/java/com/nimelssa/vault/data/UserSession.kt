package com.nimelssa.vault.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AuthMode { LOGIN, SIGNUP }
enum class UserRole { STUDENT, REP }

data class UserState(
    val isLoggedIn: Boolean = false,
    val name: String = "",
    val email: String = "",
    val role: UserRole = UserRole.STUDENT,
    val repLevel: String = "200",
    val authMode: AuthMode = AuthMode.LOGIN
)

object UserSession {
    private val _state = MutableStateFlow(UserState())
    val state: StateFlow<UserState> = _state.asStateFlow()

    fun setAuthMode(mode: AuthMode) {
        _state.value = _state.value.copy(authMode = mode)
    }

    fun login(name: String, email: String, role: UserRole, repLevel: String = "200") {
        _state.value = UserState(
            isLoggedIn = true,
            name = name,
            email = email,
            role = role,
            repLevel = repLevel
        )
    }

    fun logout() {
        _state.value = UserState()
    }
}
