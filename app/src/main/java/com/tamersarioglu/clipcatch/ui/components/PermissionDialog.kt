package com.tamersarioglu.clipcatch.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun PermissionDialog(
    isVisible: Boolean,
    title: String,
    message: String,
    icon: ImageVector = Icons.Default.Security,
    onGrantPermission: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = icon,
                    contentDescription = "Permission icon",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics {
                        contentDescription = "Permission dialog title: $title"
                    }
                )
            },
            text = {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics {
                        contentDescription = "Permission explanation: $message"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onGrantPermission,
                    modifier = Modifier.semantics {
                        contentDescription = "Grant permission button"
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics {
                        contentDescription = "Cancel permission request"
                    }
                ) {
                    Text("Cancel")
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun PermissionDeniedDialog(
    isVisible: Boolean,
    title: String = "Permission Required",
    message: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Security icon",
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics {
                        contentDescription = "Permission denied dialog title: $title"
                    }
                )
            },
            text = {
                Column {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics {
                            contentDescription = "Permission denied explanation: $message"
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "To enable this feature, please go to Settings and grant the required permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics {
                            contentDescription = "Instructions to enable permissions in settings"
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.semantics {
                        contentDescription = "Open app settings"
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics {
                        contentDescription = "Dismiss permission dialog"
                    }
                ) {
                    Text("Not Now")
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun StoragePermissionInfoDialog(
    isVisible: Boolean,
    onGrantPermission: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    PermissionDialog(
        isVisible = isVisible,
        title = "Storage Access Required",
        message = "ClipCatch needs storage permission to save downloaded videos to your device. " +
                "This allows the app to organize your downloads in the appropriate folders where " +
                "you can easily find and play them with your favorite video player.",
        icon = Icons.Default.Storage,
        onGrantPermission = onGrantPermission,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

@Composable
fun StoragePermissionDeniedDialog(
    isVisible: Boolean,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    PermissionDeniedDialog(
        isVisible = isVisible,
        title = "Storage Permission Denied",
        message = "Without storage permission, ClipCatch cannot save downloaded videos to your device. " +
                "This permission is essential for the app's core functionality.",
        onOpenSettings = onOpenSettings,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

@Composable
fun PermissionStatusIndicator(
    hasPermission: Boolean,
    permissionName: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = permissionName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (hasPermission) "Granted" else "Required",
            style = MaterialTheme.typography.bodySmall,
            color = if (hasPermission) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PermissionDialogPreview() {
    PermissionDialog(
        isVisible = true,
        title = "Permission Required",
        message = "This app requires permission to access your storage.",
        onGrantPermission = {},
        onDismiss = {}
    )
}