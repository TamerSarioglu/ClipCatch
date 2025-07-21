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
        // Given
        val dto = VideoInfoDto(
            id = "test123",
            title = "Test Video",
            downloadUrl = "https://example.com/video.mp4",
            thumbnailUrl = "https://example.com/thumb.jpg",
            duration = 300L,
            fileSize = 1024L,
            format = "mp4"
        )
        
        // When
        val result = mapper.mapToDomain(dto)
        
        // Then
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
        // Given
        val dto = VideoInfoDto(
            id = "test123",
            title = "Test Video",
            downloadUrl = "https://example.com/video.mp4",
            thumbnailUrl = null,
            duration = 300L,
            fileSize = null,
            format = "webm"
        )
        
        // When
        val result = mapper.mapToDomain(dto)
        
        // Then
        assertEquals(null, result.thumbnailUrl)
        assertEquals(null, result.fileSize)
        assertEquals(VideoFormat.WEBM, result.format)
    }
    
    @Test
    fun `mapToDomain should default to MP4 for unknown format`() {
        // Given
        val dto = VideoInfoDto(
            id = "test123",
            title = "Test Video",
            downloadUrl = "https://example.com/video.mp4",
            duration = 300L,
            format = "unknown_format"
        )
        
        // When
        val result = mapper.mapToDomain(dto)
        
        // Then
        assertEquals(VideoFormat.MP4, result.format)
    }
    
    @Test
    fun `mapToDto should convert VideoInfo to VideoInfoDto correctly`() {
        // Given
        val domain = VideoInfo(
            id = "test123",
            title = "Test Video",
            downloadUrl = "https://example.com/video.mp4",
            thumbnailUrl = "https://example.com/thumb.jpg",
            duration = 300L,
            fileSize = 1024L,
            format = VideoFormat.MKV
        )
        
        // When
        val result = mapper.mapToDto(domain)
        
        // Then
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
        // Given
        val domain = VideoInfo(
            id = "test123",
            title = "Test Video",
            downloadUrl = "https://example.com/video.mp4",
            thumbnailUrl = null,
            duration = 300L,
            fileSize = null,
            format = VideoFormat.WEBM
        )
        
        // When
        val result = mapper.mapToDto(domain)
        
        // Then
        assertEquals(null, result.thumbnailUrl)
        assertEquals(null, result.fileSize)
        assertEquals("webm", result.format)
    }
    
    @Test
    fun `mapping should be bidirectional`() {
        // Given
        val originalDomain = VideoInfo(
            id = "test123",
            title = "Test Video",
            downloadUrl = "https://example.com/video.mp4",
            thumbnailUrl = "https://example.com/thumb.jpg",
            duration = 300L,
            fileSize = 1024L,
            format = VideoFormat.MP4
        )
        
        // When
        val dto = mapper.mapToDto(originalDomain)
        val resultDomain = mapper.mapToDomain(dto)
        
        // Then
        assertEquals(originalDomain, resultDomain)
    }
}