package com.tamersarioglu.clipcatch.ui.viewmodel

import com.tamersarioglu.clipcatch.domain.model.DownloadError
import com.tamersarioglu.clipcatch.domain.model.VideoInfo

/**
 * Data class representing the UI state for the download screen
 */
data class DownloadUiState(
    /**
     * Current URL input by the user
     */
    val url: String = "",
    
    /**
     * Whether URL validation is in progress
     */
    val isValidatingUrl: Boolean = false,
    
    /**
     * Whether the current URL is valid
     */
    val isUrlValid: Boolean = false,
    
    /**
     * URL validation error message, null if no error
     */
    val urlErrorMessage: String? = null,
    
    /**
     * Whether video info extraction is in progress
     */
    val isLoadingVideoInfo: Boolean = false,
    
    /**
     * Extracted video information, null if not available
     */
    val videoInfo: VideoInfo? = null,
    
    /**
     * Whether a download is currently in progress
     */
    val isDownloading: Boolean = false,
    
    /**
     * Current download progress percentage (0-100)
     */
    val downloadProgress: Int = 0,
    
    /**
     * Whether the download completed successfully
     */
    val isDownloadComplete: Boolean = false,
    
    /**
     * Path to the downloaded file, null if not available
     */
    val downloadedFilePath: String? = null,
    
    /**
     * Current error state, null if no error
     */
    val error: DownloadError? = null,
    
    /**
     * User-friendly error message, null if no error
     */
    val errorMessage: String? = null,
    
    /**
     * Whether the download can be started (URL is valid and not currently downloading)
     */
    val canStartDownload: Boolean = false
) {
    
    /**
     * Computed property to determine if any loading operation is in progress
     */
    val isLoading: Boolean
        get() = isValidatingUrl || isLoadingVideoInfo || isDownloading
    
    /**
     * Computed property to determine if there's any error to display
     */
    val hasError: Boolean
        get() = error != null || urlErrorMessage != null
    
    /**
     * Computed property to get the appropriate error message to display
     */
    val displayErrorMessage: String?
        get() = errorMessage ?: urlErrorMessage
}