package com.tamersarioglu.clipcatch.data.mapper

import com.tamersarioglu.clipcatch.data.dto.DownloadProgressDto
import com.tamersarioglu.clipcatch.domain.model.DownloadError
import com.tamersarioglu.clipcatch.domain.model.DownloadProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadProgressMapper @Inject constructor() {
    fun mapToDomain(dto: DownloadProgressDto): DownloadProgress {
        return when (dto) {
            is DownloadProgressDto.Progress -> DownloadProgress.Progress(dto.percentage)
            is DownloadProgressDto.Success -> DownloadProgress.Success(dto.filePath)
            is DownloadProgressDto.Error -> DownloadProgress.Error(mapDownloadError(dto.errorType))
        }
    }
    fun mapToDto(domain: DownloadProgress): DownloadProgressDto {
        return when (domain) {
            is DownloadProgress.Progress -> DownloadProgressDto.Progress(domain.percentage)
            is DownloadProgress.Success -> DownloadProgressDto.Success(domain.filePath)
            is DownloadProgress.Error -> DownloadProgressDto.Error(
                errorType = domain.error.name,
                message = getErrorMessage(domain.error)
            )
        }
    }
    private fun mapDownloadError(errorType: String?): DownloadError {
        return when (errorType?.uppercase()) {
            "INVALID_URL" -> DownloadError.INVALID_URL
            "NETWORK_ERROR" -> DownloadError.NETWORK_ERROR
            "STORAGE_ERROR" -> DownloadError.STORAGE_ERROR
            "PERMISSION_DENIED" -> DownloadError.PERMISSION_DENIED
            "VIDEO_UNAVAILABLE" -> DownloadError.VIDEO_UNAVAILABLE
            "INSUFFICIENT_STORAGE" -> DownloadError.INSUFFICIENT_STORAGE
            "AGE_RESTRICTED" -> DownloadError.AGE_RESTRICTED
            "GEO_BLOCKED" -> DownloadError.GEO_BLOCKED
            else -> DownloadError.UNKNOWN_ERROR
        }
    }
    private fun getErrorMessage(error: DownloadError): String {
        return when (error) {
            DownloadError.INVALID_URL -> "The provided URL is not a valid YouTube URL"
            DownloadError.NETWORK_ERROR -> "Network connection error or timeout"
            DownloadError.STORAGE_ERROR -> "Storage related error occurred"
            DownloadError.PERMISSION_DENIED -> "Required permissions were denied"
            DownloadError.VIDEO_UNAVAILABLE -> "Video is private, deleted, or unavailable"
            DownloadError.INSUFFICIENT_STORAGE -> "Not enough storage space available"
            DownloadError.AGE_RESTRICTED -> "Video is age-restricted and cannot be downloaded"
            DownloadError.GEO_BLOCKED -> "Video is geo-blocked in your region"
            DownloadError.UNKNOWN_ERROR -> "An unknown error occurred"
        }
    }
}