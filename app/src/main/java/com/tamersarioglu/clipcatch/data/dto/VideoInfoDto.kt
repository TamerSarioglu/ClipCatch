package com.tamersarioglu.clipcatch.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object for video information with JSON serialization support
 */
@Serializable
data class VideoInfoDto(
    @SerialName("id")
    val id: String,
    
    @SerialName("title")
    val title: String,
    
    @SerialName("download_url")
    val downloadUrl: String,
    
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null,
    
    @SerialName("duration")
    val duration: Long,
    
    @SerialName("file_size")
    val fileSize: Long? = null,
    
    @SerialName("format")
    val format: String = "mp4"
)