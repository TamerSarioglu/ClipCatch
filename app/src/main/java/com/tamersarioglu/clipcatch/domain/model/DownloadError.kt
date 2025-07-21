package com.tamersarioglu.clipcatch.domain.model

/**
 * Enum representing different types of download errors
 */
enum class DownloadError {
    /**
     * The provided URL is not a valid YouTube URL
     */
    INVALID_URL,
    
    /**
     * Network connection error or timeout
     */
    NETWORK_ERROR,
    
    /**
     * Storage related error (write permissions, disk space, etc.)
     */
    STORAGE_ERROR,
    
    /**
     * Required permissions were denied by the user
     */
    PERMISSION_DENIED,
    
    /**
     * The video is private, deleted, or otherwise unavailable
     */
    VIDEO_UNAVAILABLE,
    
    /**
     * Not enough storage space available on the device
     */
    INSUFFICIENT_STORAGE,
    
    /**
     * Video is age-restricted and cannot be downloaded
     */
    AGE_RESTRICTED,
    
    /**
     * Video is geo-blocked in the current region
     */
    GEO_BLOCKED,
    
    /**
     * Unknown or unexpected error occurred
     */
    UNKNOWN_ERROR
}