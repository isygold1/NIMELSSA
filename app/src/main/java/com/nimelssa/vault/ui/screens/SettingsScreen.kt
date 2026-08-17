package com.nimelssa.vault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nimelssa.vault.ui.theme.ThemeManager
import com.nimelssa.vault.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeMode by ThemeManager.mode.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings", style = MaterialTheme.typography.titleSmall) },
            navigationIcon = {
                TextButton(onClick = onClose) {
                    Text("Close", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "APPEARANCE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    ThemeOptionRow(
                        label = "Auto (System)",
                        description = "Follow your device's light or dark setting",
                        selected = themeMode == ThemeMode.AUTO,
                        onClick = { ThemeManager.setMode(ThemeMode.AUTO) }
                    )
                    ThemeOptionRow(
                        label = "Dark",
                        description = "Always use the dark theme",
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { ThemeManager.setMode(ThemeMode.DARK) }
                    )
                    ThemeOptionRow(
                        label = "Light",
                        description = "Always use the light theme",
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { ThemeManager.setMode(ThemeMode.LIGHT) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "The theme applies instantly. Your choice is saved on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}