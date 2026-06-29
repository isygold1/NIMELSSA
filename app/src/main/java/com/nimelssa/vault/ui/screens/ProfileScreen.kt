package com.nimelssa.vault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nimelssa.vault.data.UserRole
import com.nimelssa.vault.data.UserSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val user by UserSession.state.collectAsState()

    // Delete account state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteSuccess by remember { mutableStateOf(false) }

    // Show success screen after deletion
    if (deleteSuccess) {
        DeleteSuccessScreen(onDone = onClose)
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = "Personal Information",
                    style = MaterialTheme.typography.titleSmall
                )
            },
            navigationIcon = {
                TextButton(onClick = onClose) {
                    Text(
                        "Close",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Avatar / Initials placeholder
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "👤",
                        fontSize = MaterialTheme.typography.headlineLarge.fontSize
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = user.name.ifEmpty { "NIMELSSA Member" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = when (user.role) {
                            UserRole.ADMIN -> "Administrator • Full Access"
                            UserRole.REP -> "${user.repLevel}L Class Representative"
                            UserRole.STUDENT -> "Student • Active Member"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Details card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow(label = "Full Name", value = user.name)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    DetailRow(label = "Email Address", value = user.email)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    DetailRow(label = "Role", value = user.role.name)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    DetailRow(label = "Academic Level", value = "${user.level} Level")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    DetailRow(label = "Account Status", value = "Active • Verified")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Danger zone ──
            Text(
                text = "⚠️ Danger Zone",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEF4444)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF450A0A).copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Delete Account",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Permanently removes your profile, saved data, and all associated content. This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFCA5A5)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626)
                        )
                    ) {
                        Text("🗑️ Delete My Account", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // ── Delete confirmation dialog ──
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) {
                    showDeleteDialog = false
                    deleteError = null
                    deletePassword = ""
                }
            },
            title = {
                Text("⚠️ Confirm Account Deletion", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "This will permanently delete your account and all associated data. " +
                                "All your saved offline courses will be removed.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Enter your password to confirm:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it; deleteError = null },
                        label = { Text("Current Password") },
                        placeholder = { Text("••••••••") },
                        singleLine = true,
                        enabled = !isDeleting,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = deleteError != null,
                        supportingText = if (deleteError != null) {
                            { Text(deleteError!!, color = Color(0xFFEF4444)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFEF4444),
                            unfocusedBorderColor = Color(0xFF450A0A),
                            unfocusedContainerColor = Color(0xFF1C273E),
                            focusedContainerColor = Color(0xFF1C273E),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (deletePassword.isBlank()) {
                            deleteError = "Please enter your password."
                            return@Button
                        }
                        isDeleting = true
                        deleteError = null
                        UserSession.deleteAccount(deletePassword) { success, error ->
                            isDeleting = false
                            if (success) {
                                showDeleteDialog = false
                                deleteSuccess = true
                            } else {
                                deleteError = error ?: "Deletion failed"
                            }
                        }
                    },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    if (isDeleting) {
                        Row {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .width(16.dp)
                                    .height(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Text("Deleting...", color = Color.White)
                        }
                    } else {
                        Text("Yes, Delete Everything", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        deleteError = null
                        deletePassword = ""
                    },
                    enabled = !isDeleting
                ) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF1C1917),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFD1D5DB)
        )
    }
}

@Composable
private fun DeleteSuccessScreen(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(32.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Account Deleted",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Your account and all associated data have been permanently removed.\n" +
                    "Thank you for being part of NIMELSSA Vault.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onDone,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Return to Login", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value.ifEmpty { "—" },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
