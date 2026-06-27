package com.nimelssa.vault.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AuthMode { LOGIN, SIGNUP }
enum class UserRole { STUDENT, REP, ADMIN }

data class UserState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val name: String = "",
    val email: String = "",
    val role: UserRole = UserRole.STUDENT,
    val repLevel: String = "200",
    val authMode: AuthMode = AuthMode.LOGIN,
    val errorMessage: String? = null
)

object UserSession {
    private const val TAG = "UserSession"

    private val _state = MutableStateFlow(UserState())
    val state: StateFlow<UserState> = _state.asStateFlow()

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    /**
     * Called once on app startup. Checks if a Firebase Auth session already
     * exists (persistent across app restarts) and fetches the Firestore profile.
     */
    fun checkExistingSession() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "Existing session found for UID: ${currentUser.uid}")
            _state.value = UserState(isLoading = true)
            fetchUserProfile(currentUser.uid, currentUser.email ?: "")
        } else {
            Log.d(TAG, "No existing session")
        }
    }

    /**
     * Fetches the /users/{uid} Firestore document and updates state.
     */
    private fun fetchUserProfile(uid: String, fallbackEmail: String) {
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val data = document.data ?: return@addOnSuccessListener
                    val name = (data["name"] as? String) ?: ""
                    val email = (data["email"] as? String) ?: fallbackEmail
                    val roleStr = (data["role"] as? String) ?: "student"
                    val repLevel = (data["repLevel"] as? String) ?: "200"
                    val role = when (roleStr) {
                        "admin" -> UserRole.ADMIN
                        "rep" -> UserRole.REP
                        else -> UserRole.STUDENT
                    }

                    _state.value = UserState(
                        isLoggedIn = true,
                        name = name,
                        email = email,
                        role = role,
                        repLevel = repLevel
                    )
                    Log.d(TAG, "Profile loaded: $name ($role)")
                } else {
                    Log.w(TAG, "Firestore user document not found for UID: $uid")
                    _state.value = UserState()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch Firestore profile", e)
                _state.value = UserState(errorMessage = e.message)
            }
    }

    fun setAuthMode(mode: AuthMode) {
        _state.value = _state.value.copy(authMode = mode, errorMessage = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    // ───── SIGN UP ────────────────────────────────────────────────────────

    fun signUp(name: String, email: String, password: String, role: UserRole, repLevel: String) {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = task.result?.user?.uid
                    if (uid == null) {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            errorMessage = "Account created but UID is null"
                        )
                        return@addOnCompleteListener
                    }

                    // Build Firestore profile document
                    val userData = mutableMapOf<String, Any>(
                        "name" to name,
                        "email" to email,
                        "role" to when (role) {
                            UserRole.ADMIN -> "admin"
                            UserRole.REP -> "rep"
                            UserRole.STUDENT -> "student"
                        },
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    if (role == UserRole.REP) {
                        userData["repLevel"] = repLevel
                    }

                    // Write to Firestore
                    firestore.collection("users").document(uid)
                        .set(userData)
                        .addOnCompleteListener { writeTask ->
                            if (writeTask.isSuccessful) {
                                _state.value = UserState(
                                    isLoggedIn = true,
                                    name = name,
                                    email = email,
                                    role = role,
                                    repLevel = repLevel
                                )
                                Log.d(TAG, "Sign up + Firestore write complete for: $email")
                            } else {
                                // Auth succeeded but Firestore write failed
                                val msg = writeTask.exception?.message
                                    ?: "Profile save failed"
                                _state.value = _state.value.copy(
                                    isLoading = false,
                                    errorMessage = msg
                                )
                                Log.e(TAG, "Firestore write failed: $msg")
                            }
                        }
                } else {
                    val msg = task.exception?.message ?: "Sign up failed"
                    _state.value = _state.value.copy(isLoading = false, errorMessage = msg)
                    Log.e(TAG, "Sign up failed: $msg")
                }
            }
    }

    // ───── SIGN IN ────────────────────────────────────────────────────────

    fun signIn(email: String, password: String) {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    if (user != null) {
                        fetchUserProfile(user.uid, user.email ?: email)
                        Log.d(TAG, "Sign in successful for: $email")
                    }
                } else {
                    val msg = task.exception?.message ?: "Sign in failed"
                    _state.value = _state.value.copy(isLoading = false, errorMessage = msg)
                    Log.e(TAG, "Sign in failed: $msg")
                }
            }
    }

    // ───── SIGN OUT ───────────────────────────────────────────────────────

    fun signOut() {
        auth.signOut()
        _state.value = UserState()
        Log.d(TAG, "Signed out")
    }

    // ───── PASSWORD RESET ─────────────────────────────────────────────────

    fun sendPasswordReset(email: String) {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = null
                    )
                    Log.d(TAG, "Password reset email sent to: $email")
                } else {
                    val msg = task.exception?.message ?: "Failed to send reset email"
                    _state.value = _state.value.copy(isLoading = false, errorMessage = msg)
                    Log.e(TAG, "Password reset failed: $msg")
                }
            }
    }

    // ───── UPDATE EMAIL ───────────────────────────────────────────────────

    fun updateEmail(newEmail: String, onResult: (Boolean, String?) -> Unit) {
        val user = auth.currentUser ?: run {
            onResult(false, "No authenticated user")
            return
        }

        user.updateEmail(newEmail)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Also update Firestore
                    firestore.collection("users").document(user.uid)
                        .update("email", newEmail)
                        .addOnCompleteListener { firestoreTask ->
                            if (firestoreTask.isSuccessful) {
                                _state.value = _state.value.copy(email = newEmail)
                                onResult(true, null)
                                Log.d(TAG, "Email updated to: $newEmail")
                            } else {
                                val msg = firestoreTask.exception?.message
                                    ?: "Firestore email update failed"
                                onResult(false, msg)
                                Log.e(TAG, "Firestore email update failed: $msg")
                            }
                        }
                } else {
                    val msg = task.exception?.message ?: "Email update failed"
                    onResult(false, msg)
                    Log.e(TAG, "Firebase Auth email update failed: $msg")
                }
            }
    }
}
