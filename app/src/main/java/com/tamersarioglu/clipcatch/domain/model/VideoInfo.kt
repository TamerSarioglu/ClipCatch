package com.tamersarioglu.clipcatch.domain.model

data class VideoInfo(
    val id: String,
    val title: String,
    val downloadUrl: String,
    val thumbnailUrl: String?,
    val duration: Long,
    val fileSize: Long?,
    val format: VideoFormat = VideoFormat.MP4
)