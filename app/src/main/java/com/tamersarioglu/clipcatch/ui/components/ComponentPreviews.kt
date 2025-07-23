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
            UrlInputField(
                url = url,
                onUrlChange = { url = it },
                isValid = false
            )
            UrlInputField(
                url = "https://youtube.com/watch?v=dQw4w9WgXcQ",
                onUrlChange = { },
                isValid = true
            )
            UrlInputField(
                url = "invalid-url",
                onUrlChange = { },
                isValid = false,
                errorMessage = "Please enter a valid YouTube URL"
            )
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
            DownloadButton(
                onClick = { },
                enabled = true
            )
            DownloadButton(
                onClick = { },
                enabled = false
            )
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
            ProgressIndicator(
                progress = progress,
                title = "Downloading video..."
            )
            Spacer(modifier = Modifier.height(16.dp))
            ProgressIndicator(
                progress = 75
            )
            Spacer(modifier = Modifier.height(16.dp))
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
            StatusMessage(
                message = "Video downloaded successfully to Downloads folder",
                type = StatusMessageType.SUCCESS,
                actionText = "Open File",
                onActionClick = { }
            )
            StatusMessage(
                message = "Failed to download video. Please check your internet connection and try again.",
                type = StatusMessageType.ERROR,
                actionText = "Retry",
                onActionClick = { }
            )
            StatusMessage(
                message = "This video is quite large (500MB). Make sure you have enough storage space.",
                type = StatusMessageType.WARNING
            )
            StatusMessage(
                message = "Video information extracted successfully. Ready to download.",
                type = StatusMessageType.INFO
            )
        }
    }
}