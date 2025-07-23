package com.tamersarioglu.clipcatch.data.service

import com.tamersarioglu.clipcatch.data.dto.DownloadProgressDto
import kotlinx.coroutines.flow.Flow

interface DownloadManagerService {
    suspend fun downloadVideo(
        url: String, 
        fileName: String,
        destinationPath: String? = null
    ): Flow<DownloadProgressDto>

    suspend fun cancelDownload(url: String): Boolean
    fun isDownloadInProgress(url: String): Boolean
}