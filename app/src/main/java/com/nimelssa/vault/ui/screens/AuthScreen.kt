package com.nimelssa.vault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nimelssa.vault.data.AuthMode
import com.nimelssa.vault.data.UserRole
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    authMode: AuthMode,
    isLoading: Boolean,
    errorMessage: String?,
    onToggleMode: (AuthMode) -> Unit,
    onLogin: (email: String, password: String) -> Unit,
    onSignup: (name: String, email: String, password: String, role: UserRole, repLevel: String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.REP) }
    var repLevel by remember { mutableStateOf("200") }
    var repExpanded by remember { mutableStateOf(false) }

    val repLevels = listOf("100", "200", "300", "400")

    // Auto-dismiss error after 5 seconds
    if (errorMessage != null) {
        LaunchedEffect(errorMessage) {
            delay(5000)
            onClearError()
        }
    }

    // Local validation error (displayed inline, not from Firebase)
    var localError by remember { mutableStateOf<String?>(null) }

    // Clear local error when mode changes
    LaunchedEffect(authMode) {
        localError = null
    }

    val displayError = localError ?: errorMessage

    fun validateAndSubmit() {
        localError = null

        if (authMode == AuthMode.SIGNUP) {
            if (name.isBlank()) {
                localError = "Please enter your full name."
                return
            }
            if (email.isBlank() || !email.contains("@")) {
                localError = "Please enter a valid email address."
                return
            }
            if (password.length < 6) {
                localError = "Password must be at least 6 characters."
                return
            }
            onSignup(name.trim(), email.trim(), password, selectedRole, repLevel)
        } else {
            if (email.isBlank() || !email.contains("@")) {
                localError = "Please enter a valid email address."
                return
            }
            if (password.isBlank()) {
                localError = "Please enter your password."
                return
            }
            onLogin(email.trim(), password)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo section
            Text(text = "🛡️", fontSize = MaterialTheme.typography.headlineLarge.fontSize)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "NIMELSSA VAULT",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (authMode == AuthMode.LOGIN) "Access Central Academic Repository"
                       else "Register Account Verification Pipeline",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Segmented control
            val authShape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = authMode == AuthMode.LOGIN,
                    onClick = { onToggleMode(AuthMode.LOGIN) },
                    shape = authShape,
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Color.White,
                        activeContentColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text("Sign In", fontWeight = FontWeight.Bold) }
                SegmentedButton(
                    selected = authMode == AuthMode.SIGNUP,
                    onClick = { onToggleMode(AuthMode.SIGNUP) },
                    shape = authShape,
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Color.White,
                        activeContentColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text("Sign Up", fontWeight = FontWeight.Bold) }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Error message
            if (displayError != null) {
                Text(
                    text = "❌ $displayError",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFEF4444),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF450A0A).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Name field (signup only)
            if (authMode == AuthMode.SIGNUP) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; localError = null },
                    label = { Text("Your Full Name") },
                    placeholder = { Text("Israel Oluwagbogo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color(0xFF23314F),
                        unfocusedContainerColor = Color(0xFF1C273E),
                        focusedContainerColor = Color(0xFF1C273E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFFCBD5E1),
                        unfocusedLabelColor = Color(0xFFCBD5E1),
                        cursorColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; localError = null },
                label = { Text("Active Personal Email Address") },
                placeholder = { Text("yourname@gmail.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color(0xFF23314F),
                    unfocusedContainerColor = Color(0xFF1C273E),
                    focusedContainerColor = Color(0xFF1C273E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color(0xFFCBD5E1),
                    unfocusedLabelColor = Color(0xFFCBD5E1),
                    cursorColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Role selector
            val roleShape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedRole == UserRole.STUDENT,
                    onClick = { selectedRole = UserRole.STUDENT },
                    shape = roleShape,
                    enabled = !isLoading,
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Color.White,
                        activeContentColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text("Student", fontWeight = FontWeight.Bold) }
                SegmentedButton(
                    selected = selectedRole == UserRole.REP,
                    onClick = { selectedRole = UserRole.REP },
                    shape = roleShape,
                    enabled = !isLoading,
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Color.White,
                        activeContentColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text("Class Rep", fontWeight = FontWeight.Bold) }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Rep level selector
            if (selectedRole == UserRole.REP) {
                ExposedDropdownMenuBox(
                    expanded = repExpanded,
                    onExpandedChange = { if (!isLoading) repExpanded = it }
                ) {
                    OutlinedTextField(
                        value = "${repLevel}L Representative",
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isLoading,
                        label = { Text("Assigned Class Rep Jurisdiction") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = repExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF23314F),
                            unfocusedContainerColor = Color(0xFF1C273E),
                            focusedContainerColor = Color(0xFF1C273E),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = repExpanded,
                        onDismissRequest = { repExpanded = false }
                    ) {
                        repLevels.forEach { level ->
                            DropdownMenuItem(
                                text = { Text("${level}L Representative") },
                                onClick = {
                                    repLevel = level
                                    repExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; localError = null },
                label = { Text("Secure Password") },
                placeholder = { Text("••••••••") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color(0xFF23314F),
                    unfocusedContainerColor = Color(0xFF1C273E),
                    focusedContainerColor = Color(0xFF1C273E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color(0xFFCBD5E1),
                    unfocusedLabelColor = Color(0xFFCBD5E1),
                    cursorColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Submit button
            Button(
                onClick = { validateAndSubmit() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isLoading,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0C826B)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (authMode == AuthMode.LOGIN) "Authenticate Session Link"
                               else "Create Verified Account",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
