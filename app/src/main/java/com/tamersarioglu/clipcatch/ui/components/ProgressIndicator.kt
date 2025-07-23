package com.tamersarioglu.clipcatch.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ProgressIndicator(
    progress: Int,
    isVisible: Boolean = true,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    val animatedProgress by animateFloatAsState(
        targetValue = progress / 100f,
        animationSpec = tween(durationMillis = 300),
        label = "progress_animation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Download progress: $progress percent"
            }
    ) {
        title?.let { titleText ->
            Text(
                text = titleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .semantics {
                        contentDescription = titleText
                    }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .semantics {
                        contentDescription = "Progress bar showing $progress percent complete"
                    },
                color = MaterialTheme.colorScheme.primary,
                trackColor = ProgressIndicatorDefaults.linearTrackColor,
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            )
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                text = "$progress%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics {
                    contentDescription = "$progress percent complete"
                }
            )
        }
        if (progress > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    progress == 100 -> "Download complete"
                    progress >= 75 -> "Almost finished..."
                    progress >= 50 -> "More than halfway done"
                    progress >= 25 -> "Making good progress"
                    else -> "Starting download..."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics {
                    contentDescription = when {
                        progress == 100 -> "Download complete"
                        progress >= 75 -> "Almost finished downloading"
                        progress >= 50 -> "More than halfway done downloading"
                        progress >= 25 -> "Making good progress downloading"
                        else -> "Starting download"
                    }
                }
            )
        }
    }
}