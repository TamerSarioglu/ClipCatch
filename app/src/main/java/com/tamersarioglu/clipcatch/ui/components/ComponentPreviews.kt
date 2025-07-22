package com.tamersarioglu.clipcatch.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tamersarioglu.clipcatch.ui.theme.ClipCatchTheme

@Preview(showBackground = true)
@Composable
fun UrlInputFieldPreview() {
    ClipCatchTheme {
        var url by remember { mutableStateOf("") }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Empty state
            UrlInputField(
                url = url,
                onUrlChange = { url = it },
                isValid = false
            )
            
            // Valid URL state
            UrlInputField(
                url = "https://youtube.com/watch?v=dQw4w9WgXcQ",
                onUrlChange = { },
                isValid = true
            )
            
            // Error state
            UrlInputField(
                url = "invalid-url",
                onUrlChange = { },
                isValid = false,
                errorMessage = "Please enter a valid YouTube URL"
            )
            
            // Validating state
            UrlInputField(
                url = "https://youtube.com/watch?v=test",
                onUrlChange = { },
                isValid = false,
                isValidating = true
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DownloadButtonPreview() {
    ClipCatchTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Enabled state
            DownloadButton(
                onClick = { },
                enabled = true
            )
            
            // Disabled state
            DownloadButton(
                onClick = { },
                enabled = false
            )
            
            // Loading state
            DownloadButton(
                onClick = { },
                isLoading = true
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProgressIndicatorPreview() {
    ClipCatchTheme {
        var progress by remember { mutableIntStateOf(45) }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // With title
            ProgressIndicator(
                progress = progress,
                title = "Downloading video..."
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Without title
            ProgressIndicator(
                progress = 75
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Complete
            ProgressIndicator(
                progress = 100,
                title = "Download complete"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StatusMessagePreview() {
    ClipCatchTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Success message
            StatusMessage(
                message = "Video downloaded successfully to Downloads folder",
                type = StatusMessageType.SUCCESS,
                actionText = "Open File",
                onActionClick = { }
            )
            
            // Error message
            StatusMessage(
                message = "Failed to download video. Please check your internet connection and try again.",
                type = StatusMessageType.ERROR,
                actionText = "Retry",
                onActionClick = { }
            )
            
            // Warning message
            StatusMessage(
                message = "This video is quite large (500MB). Make sure you have enough storage space.",
                type = StatusMessageType.WARNING
            )
            
            // Info message
            StatusMessage(
                message = "Video information extracted successfully. Ready to download.",
                type = StatusMessageType.INFO
            )
        }
    }
}