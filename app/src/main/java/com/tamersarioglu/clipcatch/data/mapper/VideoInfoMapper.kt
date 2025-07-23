package com.tamersarioglu.clipcatch.data.mapper

import com.tamersarioglu.clipcatch.data.dto.VideoInfoDto
import com.tamersarioglu.clipcatch.domain.model.VideoFormat
import com.tamersarioglu.clipcatch.domain.model.VideoInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoInfoMapper @Inject constructor() {

    fun mapToDomain(dto: VideoInfoDto): VideoInfo {
        return VideoInfo(
            id = dto.id,
            title = dto.title,
            downloadUrl = dto.downloadUrl,
            thumbnailUrl = dto.thumbnailUrl,
            duration = dto.duration,
            fileSize = dto.fileSize,
            format = mapVideoFormat(dto.format)
        )
    }

    fun mapToDto(domain: VideoInfo): VideoInfoDto {
        return VideoInfoDto(
            id = domain.id,
            title = domain.title,
            downloadUrl = domain.downloadUrl,
            thumbnailUrl = domain.thumbnailUrl,
            duration = domain.duration,
            fileSize = domain.fileSize,
            format = domain.format.name.lowercase()
        )
    }

    private fun mapVideoFormat(format: String?): VideoFormat {
        return when (format?.uppercase()) {
            "MP4" -> VideoFormat.MP4
            "WEBM" -> VideoFormat.WEBM
            "MKV" -> VideoFormat.MKV
            else -> VideoFormat.MP4
        }
    }
}