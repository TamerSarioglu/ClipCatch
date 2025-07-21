package com.tamersarioglu.clipcatch.domain.usecase

import com.tamersarioglu.clipcatch.domain.repository.VideoDownloadRepository
import javax.inject.Inject

/**
 * Use case for validating YouTube URLs.
 * Handles the business logic for URL format validation and sanitization.
 */
class ValidateUrlUseCase @Inject constructor(
    private val repository: VideoDownloadRepository
) {
    
    /**
     * Validates if the provided URL is a valid YouTube URL.
     * Supports various YouTube URL formats including:
     * - youtube.com/watch?v=
     * - youtube.com/shorts/
     * - youtu.be/
     * - m.youtube.com
     * 
     * @param url The URL to validate
     * @return ValidationResult containing validation status and sanitized URL if valid
     */
    operator fun invoke(url: String): ValidationResult {
        // Handle empty or blank URLs
        if (url.isBlank()) {
            return ValidationResult(
                isValid = false,
                sanitizedUrl = null,
                errorMessage = "URL cannot be empty"
            )
        }
        
        // Trim whitespace and convert to lowercase for validation
        val trimmedUrl = url.trim()
        
        // Validate using repository
        val isValid = repository.validateYouTubeUrl(trimmedUrl)
        
        return if (isValid) {
            ValidationResult(
                isValid = true,
                sanitizedUrl = trimmedUrl,
                errorMessage = null
            )
        } else {
            ValidationResult(
                isValid = false,
                sanitizedUrl = null,
                errorMessage = "Invalid YouTube URL format. Please enter a valid YouTube URL."
            )
        }
    }
    
    /**
     * Data class representing the result of URL validation
     */
    data class ValidationResult(
        val isValid: Boolean,
        val sanitizedUrl: String?,
        val errorMessage: String?
    )
}