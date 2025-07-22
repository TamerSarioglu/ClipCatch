package com.tamersarioglu.clipcatch.data.service

import com.tamersarioglu.clipcatch.data.dto.DownloadProgressDto
import kotlinx.coroutines.flow.Flow

/**
 * Service interface for managing video downloads
 */
interface DownloadManagerService {
    
    /**
     * Downloads a video from the specified URL with progress tracking
     * 
     * @param url The direct download URL for the video
     * @param fileName The name to save the file as
     * @param destinationPath Optional custom destination path (null for default Downloads folder)
     * @return Flow emitting download progress updates
     */
    suspend fun downloadVideo(
        url: String, 
        fileName: String,
        destinationPath: String? = null
    ): Flow<DownloadProgressDto>
    
    /**
     * Cancels an ongoing download
     * 
     * @param url The URL of the download to cancel
     * @return true if download was successfully canceled, false otherwise
     */
    suspend fun cancelDownload(url: String): Boolean
    
    /**
     * Checks if a download is currently in progress
     * 
     * @param url The URL to check
     * @return true if download is in progress, false otherwise
     */
    fun isDownloadInProgress(url: String): Boolean
}