package com.tamersarioglu.clipcatch.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sealed class representing download progress states with JSON serialization support
 */
@Serializable
sealed class DownloadProgressDto {
    
    /**
     * Download is in progress with a specific percentage
     */
    @Serializable
    @SerialName("progress")
    data class Progress(
        @SerialName("percentage")
        val percentage: Int
    ) : DownloadProgressDto()
    
    /**
     * Download completed successfully with the file path
     */
    @Serializable
    @SerialName("success")
    data class Success(
        @SerialName("file_path")
        val filePath: String
    ) : DownloadProgressDto()
    
    /**
     * Download failed with an error
     */
    @Serializable
    @SerialName("error")
    data class Error(
        @SerialName("error_type")
        val errorType: String,
        
        @SerialName("message")
        val message: String
    ) : DownloadProgressDto()
}