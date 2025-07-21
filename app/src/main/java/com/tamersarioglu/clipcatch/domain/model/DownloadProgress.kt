package com.tamersarioglu.clipcatch.domain.model

/**
 * Sealed class representing the different states of a download operation
 */
sealed class DownloadProgress {
    /**
     * Download is in progress with a specific percentage
     */
    data class Progress(val percentage: Int) : DownloadProgress()
    
    /**
     * Download completed successfully with the file path
     */
    data class Success(val filePath: String) : DownloadProgress()
    
    /**
     * Download failed with an error
     */
    data class Error(val error: DownloadError) : DownloadProgress()
}