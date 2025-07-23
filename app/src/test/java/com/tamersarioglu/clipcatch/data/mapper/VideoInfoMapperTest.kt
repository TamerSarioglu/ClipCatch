package com.tamersarioglu.clipcatch.data.mapper

import com.tamersarioglu.clipcatch.data.dto.VideoInfoDto
import com.tamersarioglu.clipcatch.domain.model.VideoFormat
import com.tamersarioglu.clipcatch.domain.model.VideoInfo
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class VideoInfoMapperTest {
    
    private lateinit var mapper: VideoInfoMapper
    
    @Before
    fun setUp() {
        mapper = VideoInfoMapper()
    }
    
    @Test
    fun `mapToDomain should convert VideoInfoDto to VideoInfo correctly`() {
        val dto = VideoInfoDto(
            id = "test123",
            title = "Test Video",
            downloadUrl = "https://example.com/video.mp4",
            thumbnailUrl = "https://example.com/thumb.jpg",
            duration = 300L,
            fileSize = 1024L,
            format = "mp4"
        )
        val result = mapper.mapToDomain(dto)
        assertEquals("test123", result.id)
        assertEquals("Test Video", result.title)
        assertEquals("https://example.com/video.mp4", result.downloadUrl)
        assertEquals("https://example.com/thumb.jpg", result.thumbnailUrl)
        assertEquals(300L, result.duration)
        assertEquals(1024L, result.fileSize)
        assertEquals(VideoFormat.MP4, result.format)
    }
    
    @Test
    fun `mapToDomain should handle null values correctly`() {
        val dto = VideoInfoDto(
            id = "test123",
            title = "Test Video",
            downloadUrl = "https://example.com/video.mp4",
            thumbnailUrl = null,
            duration = 300L,
            fileSize = null,
            format = "webm"
        )
        val result = mapper.mapToDomain(dto)
        assertEquals(null, result.thumbnailUrl)
        assertEquals(null, result.fileSize)
        assertEquals(VideoFormat.WEBM, result.format)
    }
    
    @Test
    fun `mapToDomain should default to MP4 for unknown format`() {
        val dto = VideoInfoDto(
            id = "test123",
            title = "Test Video",
            downloadUrl = "https://example.com/video.mp4",
            duration = 300L,
            format = "unknown_format"
        )
        val result = mapper.mapToDomain(dto)
        assertEquals(VideoFormat.MP4, result.format)
    }
    
    @Test
    fun `mapToDto should convert VideoInfo to VideoInfoDto correctly`() {
        val domain = VideoInfo(
            id = "test123",
            title = "Test Video",
            downloadUrl = "https://example.com/video.mp4",
            thumbnailUrl = "https://example.com/thumb.jpg",
            duration = 300L,
            fileSize = 1024L,
            format = VideoFormat.MKV
        )
        val result = mapper.mapToDto(domain)
        assertEquals("test123", result.id)
        assertEquals("Test Video", result.title)
        assertEquals("https://example.com/video.mp4", result.downloadUrl)
        assertEquals("https://example.com/thumb.jpg", result.thumbnailUrl)
        assertEquals(300L, result.duration)
        assertEquals(1024L, result.fileSize)
        assertEquals("mkv", result.format)
    }
    
    @Test
    fun `mapToDto should handle null values correctly`() {
        val domain = VideoInfo(
            id = "test123",
            title = "Test Video",
            downloadUrl = "https://example.com/video.mp4",
            thumbnailUrl = null,
            duration = 300L,
            fileSize = null,
            format = VideoFormat.WEBM
        )
        val result = mapper.mapToDto(domain)
        assertEquals(null, result.thumbnailUrl)
        assertEquals(null, result.fileSize)
        assertEquals("webm", result.format)
    }
    
    @Test
    fun `mapping should be bidirectional`() {
        val originalDomain = VideoInfo(
            id = "test123",
            title = "Test Video",
            downloadUrl = "https://example.com/video.mp4",
            thumbnailUrl = "https://example.com/thumb.jpg",
            duration = 300L,
            fileSize = 1024L,
            format = VideoFormat.MP4
        )
        val dto = mapper.mapToDto(originalDomain)
        val resultDomain = mapper.mapToDomain(dto)
        assertEquals(originalDomain, resultDomain)
    }
}