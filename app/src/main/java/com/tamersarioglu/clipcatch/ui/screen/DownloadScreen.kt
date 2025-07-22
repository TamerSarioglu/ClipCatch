package com.tamersarioglu.clipcatch.ui.screen

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tamersarioglu.clipcatch.ui.components.DownloadButton
import com.tamersarioglu.clipcatch.ui.components.ProgressIndicator
import com.tamersarioglu.clipcatch.ui.components.StatusMessage
import com.tamersarioglu.clipcatch.ui.components.StatusMessageType
import com.tamersarioglu.clipcatch.ui.components.UrlInputField
import com.tamersarioglu.clipcatch.ui.viewmodel.DownloadViewModel
import java.io.File

/**
 * Main download screen composable that integrates all UI components
 */
@Composable
fun DownloadScreen(
    modifier: Modifier = Modifier,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Permission launcher for storage access
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.downloadVideo()
        } else {
            // Handle permission denied case
            // Could show a dialog explaining why permissions are needed
        }
    }
    
    // Show snackbar for errors
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App title and description
            Text(
                text = "ClipCatch",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "ClipCatch app title"
                    }
            )
            
            Text(
                text = "Download YouTube videos directly to your device",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "App description: Download YouTube videos directly to your device"
                    }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // URL input field
            UrlInputField(
                url = uiState.url,
                onUrlChange = viewModel::onUrlChanged,
                isValid = uiState.isUrlValid,
                errorMessage = uiState.urlErrorMessage,
                isValidating = uiState.isValidatingUrl,
                enabled = !uiState.isDownloading,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Video info card (shown when URL is valid and video info is loaded)
            uiState.videoInfo?.let { videoInfo ->
                VideoInfoCard(
                    videoInfo = videoInfo,
                    isLoading = uiState.isLoadingVideoInfo,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Download button
            DownloadButton(
                onClick = {
                    // Check and request permissions before downloading
                    val permissions = getRequiredPermissions()
                    if (permissions.isNotEmpty()) {
                        permissionLauncher.launch(permissions.toTypedArray())
                    } else {
                        viewModel.downloadVideo()
                    }
                },
                enabled = uiState.canStartDownload,
                isLoading = uiState.isDownloading,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Progress indicator (shown during download)
            if (uiState.isDownloading || uiState.downloadProgress > 0) {
                ProgressIndicator(
                    progress = uiState.downloadProgress,
                    isVisible = true,
                    title = "Downloading video...",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Success message (shown when download completes)
            if (uiState.isDownloadComplete) {
                StatusMessage(
                    message = "Video downloaded successfully!",
                    type = StatusMessageType.SUCCESS,
                    isVisible = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Action buttons for completed download
                uiState.downloadedFilePath?.let { filePath ->
                    DownloadCompleteActions(
                        filePath = filePath,
                        onNewDownload = viewModel::resetDownloadState,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // Error message (shown when there's an error)
            if (uiState.hasError) {
                uiState.displayErrorMessage?.let { errorMessage ->
                    StatusMessage(
                        message = errorMessage,
                        type = StatusMessageType.ERROR,
                        isVisible = true,
                        actionText = "Try Again",
                        onActionClick = viewModel::clearError,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // Cancel button (shown during download)
            if (uiState.isDownloading) {
                OutlinedButton(
                    onClick = viewModel::cancelDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Cancel download"
                        }
                ) {
                    Text("Cancel Download")
                }
            }
        }
    }
}

/**
 * Video information card component
 */
@Composable
private fun VideoInfoCard(
    videoInfo: com.tamersarioglu.clipcatch.domain.model.VideoInfo,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.semantics {
            contentDescription = "Video information for ${videoInfo.title}"
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Video Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = videoInfo.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.semantics {
                    contentDescription = "Video title: ${videoInfo.title}"
                }
            )
            
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Duration: ${formatDuration(videoInfo.duration)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics {
                        contentDescription = "Video duration: ${formatDuration(videoInfo.duration)}"
                    }
                )
                
                videoInfo.fileSize?.let { size ->
                    Text(
                        text = "Size: ${formatFileSize(size)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics {
                            contentDescription = "File size: ${formatFileSize(size)}"
                        }
                    )
                }
            }
        }
    }
}

/**
 * Actions available after download completion
 */
@Composable
private fun DownloadCompleteActions(
    filePath: String,
    onNewDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Open file button
        OutlinedButton(
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        val uri = Uri.fromFile(File(filePath))
                        setDataAndType(uri, "video/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Open video with"))
                } catch (e: Exception) {
                    // Handle error - could show a toast or snackbar
                }
            },
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = "Open downloaded video"
                }
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play icon"
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Open")
        }
        
        // Show in folder button
        OutlinedButton(
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        val uri = Uri.fromFile(File(filePath).parentFile)
                        setDataAndType(uri, "resource/folder")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Open folder with"))
                } catch (e: Exception) {
                    // Handle error - could show a toast or snackbar
                }
            },
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = "Show file in folder"
                }
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Folder icon"
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Folder")
        }
    }
    
    Spacer(modifier = Modifier.height(8.dp))
    
    // New download button
    TextButton(
        onClick = onNewDownload,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Start new download"
            }
    ) {
        Text("Download Another Video")
    }
}

/**
 * Get required permissions based on Android version
 */
private fun getRequiredPermissions(): List<String> {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            listOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            // Android 10+ uses scoped storage, no explicit permissions needed
            emptyList()
        }
        else -> {
            listOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }
}

/**
 * Format duration in seconds to human-readable format
 */
private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
        else -> String.format("%d:%02d", minutes, secs)
    }
}

/**
 * Format file size in bytes to human-readable format
 */
private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    
    return when {
        gb >= 1 -> String.format("%.1f GB", gb)
        mb >= 1 -> String.format("%.1f MB", mb)
        kb >= 1 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}