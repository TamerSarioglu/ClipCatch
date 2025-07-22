package com.tamersarioglu.clipcatch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Status message types
 */
enum class StatusMessageType {
    SUCCESS,
    ERROR,
    WARNING,
    INFO
}

/**
 * Status message component for success/error display
 * 
 * @param message The message to display
 * @param type The type of status message (success, error, warning, info)
 * @param isVisible Whether the message should be visible
 * @param actionText Optional action button text
 * @param onActionClick Optional action button click handler
 * @param modifier Modifier for styling
 */
@Composable
fun StatusMessage(
    message: String,
    type: StatusMessageType,
    isVisible: Boolean = true,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!isVisible || message.isEmpty()) return
    
    val (backgroundColor, contentColor, icon, iconDescription) = when (type) {
        StatusMessageType.SUCCESS -> {
            Tuple4(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                Icons.Default.CheckCircle,
                "Success"
            )
        }
        StatusMessageType.ERROR -> {
            Tuple4(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                Icons.Default.Error,
                "Error"
            )
        }
        StatusMessageType.WARNING -> {
            Tuple4(
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
                Icons.Default.Warning,
                "Warning"
            )
        }
        StatusMessageType.INFO -> {
            Tuple4(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
                Icons.Default.Info,
                "Information"
            )
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(16.dp)
            .semantics {
                contentDescription = "${iconDescription}: $message"
            }
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconDescription,
                tint = contentColor,
                modifier = Modifier
                    .size(20.dp)
                    .semantics {
                        contentDescription = "$iconDescription icon"
                    }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    fontWeight = if (type == StatusMessageType.ERROR) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.semantics {
                        contentDescription = message
                    }
                )
                
                // Action button if provided
                if (actionText != null && onActionClick != null) {
                    TextButton(
                        onClick = onActionClick,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .semantics {
                                contentDescription = actionText
                            }
                    ) {
                        Text(
                            text = actionText,
                            color = contentColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Helper data class for tuple of 4 elements
 */
private data class Tuple4<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)