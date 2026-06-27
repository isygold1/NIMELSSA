package com.nimelssa.vault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nimelssa.vault.data.UserRole
import com.nimelssa.vault.data.UserState

@Composable
fun DrawerHeader(user: UserState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = user.name.ifEmpty { "User" },
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        val roleText = when (user.role) {
            UserRole.REP -> "${user.repLevel}L Class Representative"
            UserRole.STUDENT -> "Student • Active Member"
        }
        Text(
            text = roleText,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
fun DrawerMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun DrawerContent(
    user: UserState,
    onNavigateToCbt: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToEmailMod: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DrawerHeader(user = user)

        Spacer(modifier = Modifier.height(8.dp))

        DrawerMenuItem(text = "⏱️ CBT Exam Portal", onClick = onNavigateToCbt)
        DrawerMenuItem(text = "👤 Personal Information", onClick = onNavigateToProfile)
        DrawerMenuItem(text = "📧 Change Registered Email", onClick = onNavigateToEmailMod)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        DrawerMenuItem(
            text = "🚪 Logout (End Session)",
            onClick = onLogout
        )
    }
}
