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
        val dto = DownloadProgressDto.Progress(percentage = 75)
        val result = mapper.mapToDomain(dto)
        assertTrue(result is DownloadProgress.Progress)
        assertEquals(75, (result as DownloadProgress.Progress).percentage)
    }
    
    @Test
    fun `mapToDomain should convert Success DTO to domain correctly`() {
        val dto = DownloadProgressDto.Success(filePath = "/storage/video.mp4")
        val result = mapper.mapToDomain(dto)
        assertTrue(result is DownloadProgress.Success)
        assertEquals("/storage/video.mp4", (result as DownloadProgress.Success).filePath)
    }
    
    @Test
    fun `mapToDomain should convert Error DTO to domain correctly`() {
        val dto = DownloadProgressDto.Error(
            errorType = "NETWORK_ERROR",
            message = "Connection failed"
        )
        val result = mapper.mapToDomain(dto)
        assertTrue(result is DownloadProgress.Error)
        assertEquals(DownloadError.NETWORK_ERROR, (result as DownloadProgress.Error).error)
    }
    
    @Test
    fun `mapToDomain should default to UNKNOWN_ERROR for invalid error type`() {
        val dto = DownloadProgressDto.Error(
            errorType = "INVALID_ERROR_TYPE",
            message = "Unknown error"
        )
        val result = mapper.mapToDomain(dto)
        assertTrue(result is DownloadProgress.Error)
        assertEquals(DownloadError.UNKNOWN_ERROR, (result as DownloadProgress.Error).error)
    }
    
    @Test
    fun `mapToDto should convert Progress domain to DTO correctly`() {
        val domain = DownloadProgress.Progress(percentage = 50)
        val result = mapper.mapToDto(domain)
        assertTrue(result is DownloadProgressDto.Progress)
        assertEquals(50, (result as DownloadProgressDto.Progress).percentage)
    }
    
    @Test
    fun `mapToDto should convert Success domain to DTO correctly`() {
        val domain = DownloadProgress.Success(filePath = "/storage/video.mp4")
        val result = mapper.mapToDto(domain)
        assertTrue(result is DownloadProgressDto.Success)
        assertEquals("/storage/video.mp4", (result as DownloadProgressDto.Success).filePath)
    }
    
    @Test
    fun `mapToDto should convert Error domain to DTO correctly`() {
        val domain = DownloadProgress.Error(error = DownloadError.STORAGE_ERROR)
        val result = mapper.mapToDto(domain)
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
            val domain = DownloadProgress.Error(error = error)
            val result = mapper.mapToDto(domain)
            assertTrue(result is DownloadProgressDto.Error)
            val errorDto = result as DownloadProgressDto.Error
            assertEquals(error.name, errorDto.errorType)
            assertEquals(expectedMessage, errorDto.message)
        }
    }
    
    @Test
    fun `mapping should be bidirectional for Progress`() {
        val originalDomain = DownloadProgress.Progress(percentage = 85)
        val dto = mapper.mapToDto(originalDomain)
        val resultDomain = mapper.mapToDomain(dto)
        assertEquals(originalDomain, resultDomain)
    }
    
    @Test
    fun `mapping should be bidirectional for Success`() {
        val originalDomain = DownloadProgress.Success(filePath = "/storage/test.mp4")
        val dto = mapper.mapToDto(originalDomain)
        val resultDomain = mapper.mapToDomain(dto)
        assertEquals(originalDomain, resultDomain)
    }
    
    @Test
    fun `mapping should be bidirectional for Error`() {
        val originalDomain = DownloadProgress.Error(error = DownloadError.VIDEO_UNAVAILABLE)
        val dto = mapper.mapToDto(originalDomain)
        val resultDomain = mapper.mapToDomain(dto)
        assertEquals(originalDomain, resultDomain)
    }
}