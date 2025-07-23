package com.tamersarioglu.clipcatch.ui.viewmodel

import com.tamersarioglu.clipcatch.data.util.ErrorRecoveryAction
import com.tamersarioglu.clipcatch.domain.model.DownloadError
import com.tamersarioglu.clipcatch.domain.model.VideoInfo

data class DownloadUiState(
    val url: String = "",
    val isValidatingUrl: Boolean = false,
    val isUrlValid: Boolean = false,
    val urlErrorMessage: String? = null,
    val isLoadingVideoInfo: Boolean = false,
    val videoInfo: VideoInfo? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val isDownloadComplete: Boolean = false,
    val downloadedFilePath: String? = null,
    val error: DownloadError? = null,
    val errorMessage: String? = null,
    val canStartDownload: Boolean = false,
    val recoveryAction: ErrorRecoveryAction? = null
) {
    val isLoading: Boolean
        get() = isValidatingUrl || isLoadingVideoInfo || isDownloading
    
    val hasError: Boolean
        get() = error != null || urlErrorMessage != null
    
    val displayErrorMessage: String?
        get() = errorMessage ?: urlErrorMessage
}