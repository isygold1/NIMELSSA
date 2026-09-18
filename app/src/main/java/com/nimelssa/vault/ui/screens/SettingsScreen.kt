package com.nimelssa.vault.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.nimelssa.vault.data.OfflineManager
import com.nimelssa.vault.ui.theme.OrientationManager
import com.nimelssa.vault.ui.theme.OrientationMode
import com.nimelssa.vault.ui.theme.ThemeManager
import com.nimelssa.vault.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeMode by ThemeManager.mode.collectAsState()
    val orientationMode by OrientationManager.mode.collectAsState()
    val context = LocalContext.current

    // Current download folder display name; refreshed on pick/clear so the
    // UI always matches what OfflineManager persists.
    var folderName by remember { mutableStateOf(OfflineManager.getUserFolderName()) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                // Retain access after the screen/process dies (system grants
                // persistable permission for tree picks; some providers still
                // throw, so this must not crash the flow).
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
            val displayName = DocumentFile.fromTreeUri(context, uri)?.name
                ?: uri.lastPathSegment
                ?: "Download folder"
            OfflineManager.setUserFolder(uri.toString(), displayName)
            folderName = displayName
        }
    }

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

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ORIENTATION",
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
                        description = "Follow your device's rotation setting",
                        selected = orientationMode == OrientationMode.AUTO,
                        onClick = { OrientationManager.setMode(OrientationMode.AUTO) }
                    )
                    ThemeOptionRow(
                        label = "Portrait",
                        description = "PDF pages always render in portrait",
                        selected = orientationMode == OrientationMode.PORTRAIT,
                        onClick = { OrientationManager.setMode(OrientationMode.PORTRAIT) }
                    )
                    ThemeOptionRow(
                        label = "Landscape",
                        description = "PDF pages always render in landscape",
                        selected = orientationMode == OrientationMode.LANDSCAPE,
                        onClick = { OrientationManager.setMode(OrientationMode.LANDSCAPE) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Locks the PDF viewer orientation. Other screens follow your device setting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "STORAGE",
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
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Download location",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (folderName != null)
                            "Files saved with ⬇️ also go to: $folderName"
                        else
                            "Files saved with ⬇️ stay in the app's private storage. " +
                                "Choose a folder to make them easy to find elsewhere.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = { folderPicker.launch(null) }) {
                            Text(
                                if (folderName != null) "Change folder" else "Choose folder",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (folderName != null) {
                            TextButton(onClick = {
                                OfflineManager.clearUserFolder()
                                folderName = null
                            }) {
                                Text(
                                    "Use default",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
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