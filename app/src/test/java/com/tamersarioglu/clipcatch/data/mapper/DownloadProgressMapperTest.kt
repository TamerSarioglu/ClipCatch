package com.tamersarioglu.clipcatch.data.mapper

import com.tamersarioglu.clipcatch.data.dto.DownloadProgressDto
import com.tamersarioglu.clipcatch.domain.model.DownloadError
import com.tamersarioglu.clipcatch.domain.model.DownloadProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadProgressMapperTest {
    
    private lateinit var mapper: DownloadProgressMapper
    
    @Before
    fun setUp() {
        mapper = DownloadProgressMapper()
    }
    
    @Test
    fun `mapToDomain should convert Progress DTO to domain correctly`() {
        // Given
        val dto = DownloadProgressDto.Progress(percentage = 75)
        
        // When
        val result = mapper.mapToDomain(dto)
        
        // Then
        assertTrue(result is DownloadProgress.Progress)
        assertEquals(75, (result as DownloadProgress.Progress).percentage)
    }
    
    @Test
    fun `mapToDomain should convert Success DTO to domain correctly`() {
        // Given
        val dto = DownloadProgressDto.Success(filePath = "/storage/video.mp4")
        
        // When
        val result = mapper.mapToDomain(dto)
        
        // Then
        assertTrue(result is DownloadProgress.Success)
        assertEquals("/storage/video.mp4", (result as DownloadProgress.Success).filePath)
    }
    
    @Test
    fun `mapToDomain should convert Error DTO to domain correctly`() {
        // Given
        val dto = DownloadProgressDto.Error(
            errorType = "NETWORK_ERROR",
            message = "Connection failed"
        )
        
        // When
        val result = mapper.mapToDomain(dto)
        
        // Then
        assertTrue(result is DownloadProgress.Error)
        assertEquals(DownloadError.NETWORK_ERROR, (result as DownloadProgress.Error).error)
    }
    
    @Test
    fun `mapToDomain should default to UNKNOWN_ERROR for invalid error type`() {
        // Given
        val dto = DownloadProgressDto.Error(
            errorType = "INVALID_ERROR_TYPE",
            message = "Unknown error"
        )
        
        // When
        val result = mapper.mapToDomain(dto)
        
        // Then
        assertTrue(result is DownloadProgress.Error)
        assertEquals(DownloadError.UNKNOWN_ERROR, (result as DownloadProgress.Error).error)
    }
    
    @Test
    fun `mapToDto should convert Progress domain to DTO correctly`() {
        // Given
        val domain = DownloadProgress.Progress(percentage = 50)
        
        // When
        val result = mapper.mapToDto(domain)
        
        // Then
        assertTrue(result is DownloadProgressDto.Progress)
        assertEquals(50, (result as DownloadProgressDto.Progress).percentage)
    }
    
    @Test
    fun `mapToDto should convert Success domain to DTO correctly`() {
        // Given
        val domain = DownloadProgress.Success(filePath = "/storage/video.mp4")
        
        // When
        val result = mapper.mapToDto(domain)
        
        // Then
        assertTrue(result is DownloadProgressDto.Success)
        assertEquals("/storage/video.mp4", (result as DownloadProgressDto.Success).filePath)
    }
    
    @Test
    fun `mapToDto should convert Error domain to DTO correctly`() {
        // Given
        val domain = DownloadProgress.Error(error = DownloadError.STORAGE_ERROR)
        
        // When
        val result = mapper.mapToDto(domain)
        
        // Then
        assertTrue(result is DownloadProgressDto.Error)
        val errorDto = result as DownloadProgressDto.Error
        assertEquals("STORAGE_ERROR", errorDto.errorType)
        assertEquals("Storage related error occurred", errorDto.message)
    }
    
    @Test
    fun `mapToDto should provide correct error messages for all error types`() {
        val errorTests = mapOf(
            DownloadError.INVALID_URL to "The provided URL is not a valid YouTube URL",
            DownloadError.NETWORK_ERROR to "Network connection error or timeout",
            DownloadError.STORAGE_ERROR to "Storage related error occurred",
            DownloadError.PERMISSION_DENIED to "Required permissions were denied",
            DownloadError.VIDEO_UNAVAILABLE to "Video is private, deleted, or unavailable",
            DownloadError.INSUFFICIENT_STORAGE to "Not enough storage space available",
            DownloadError.AGE_RESTRICTED to "Video is age-restricted and cannot be downloaded",
            DownloadError.GEO_BLOCKED to "Video is geo-blocked in your region",
            DownloadError.UNKNOWN_ERROR to "An unknown error occurred"
        )
        
        errorTests.forEach { (error, expectedMessage) ->
            // Given
            val domain = DownloadProgress.Error(error = error)
            
            // When
            val result = mapper.mapToDto(domain)
            
            // Then
            assertTrue(result is DownloadProgressDto.Error)
            val errorDto = result as DownloadProgressDto.Error
            assertEquals(error.name, errorDto.errorType)
            assertEquals(expectedMessage, errorDto.message)
        }
    }
    
    @Test
    fun `mapping should be bidirectional for Progress`() {
        // Given
        val originalDomain = DownloadProgress.Progress(percentage = 85)
        
        // When
        val dto = mapper.mapToDto(originalDomain)
        val resultDomain = mapper.mapToDomain(dto)
        
        // Then
        assertEquals(originalDomain, resultDomain)
    }
    
    @Test
    fun `mapping should be bidirectional for Success`() {
        // Given
        val originalDomain = DownloadProgress.Success(filePath = "/storage/test.mp4")
        
        // When
        val dto = mapper.mapToDto(originalDomain)
        val resultDomain = mapper.mapToDomain(dto)
        
        // Then
        assertEquals(originalDomain, resultDomain)
    }
    
    @Test
    fun `mapping should be bidirectional for Error`() {
        // Given
        val originalDomain = DownloadProgress.Error(error = DownloadError.VIDEO_UNAVAILABLE)
        
        // When
        val dto = mapper.mapToDto(originalDomain)
        val resultDomain = mapper.mapToDomain(dto)
        
        // Then
        assertEquals(originalDomain, resultDomain)
    }
}