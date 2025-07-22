package com.tamersarioglu.clipcatch.data.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for validating YouTube URLs
 * Supports all YouTube URL formats including edge cases
 */
@Singleton
class UrlValidator @Inject constructor() {

    companion object {
        // YouTube domain patterns
        private val YOUTUBE_DOMAINS = setOf(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "music.youtube.com",
            "youtu.be"
        )

        // YouTube URL patterns - all patterns have video ID as the last capture group
        private val YOUTUBE_PATTERNS = listOf(
            // Standard watch URLs: youtube.com/watch?v=VIDEO_ID
            Regex("""^(?i)https?://(www\.|m\.)?youtube\.com/watch\?.*v=([a-zA-Z0-9_-]{11})(&.*|#.*)?$"""),
            
            // YouTube Shorts: youtube.com/shorts/VIDEO_ID
            Regex("""^(?i)https?://(www\.|m\.)?youtube\.com/shorts/([a-zA-Z0-9_-]{11})(\?.*)?$"""),
            
            // Shortened URLs: youtu.be/VIDEO_ID
            Regex("""^(?i)https?://youtu\.be/([a-zA-Z0-9_-]{11})(\?.*)?$"""),
            
            // Embed URLs: youtube.com/embed/VIDEO_ID
            Regex("""^(?i)https?://(www\.|m\.)?youtube\.com/embed/([a-zA-Z0-9_-]{11})(\?.*)?$"""),
            
            // YouTube Music: music.youtube.com/watch?v=VIDEO_ID
            Regex("""^(?i)https?://music\.youtube\.com/watch\?.*v=([a-zA-Z0-9_-]{11})(&.*|#.*)?$"""),
            
            // Live URLs: youtube.com/live/VIDEO_ID
            Regex("""^(?i)https?://(www\.|m\.)?youtube\.com/live/([a-zA-Z0-9_-]{11})(\?.*)?$""")
        )

        // Video ID pattern - YouTube video IDs are exactly 11 characters
        private val VIDEO_ID_PATTERN = Regex("""^[a-zA-Z0-9_-]{11}$""")
    }

    /**
     * Validates if the given URL is a valid YouTube URL
     * @param url The URL to validate
     * @return true if the URL is a valid YouTube URL, false otherwise
     */
    fun isValidYouTubeUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) {
            return false
        }

        val trimmedUrl = url.trim()
        
        // Check if URL matches any YouTube pattern
        return YOUTUBE_PATTERNS.any { pattern ->
            pattern.matches(trimmedUrl)
        }
    }

    /**
     * Extracts the video ID from a YouTube URL
     * @param url The YouTube URL
     * @return The video ID if found, null otherwise
     */
    fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) {
            return null
        }

        val trimmedUrl = url.trim()

        YOUTUBE_PATTERNS.forEach { pattern ->
            val matchResult = pattern.find(trimmedUrl)
            if (matchResult != null) {
                // Find the video ID in the capture groups (it's always 11 characters)
                for (i in 1 until matchResult.groupValues.size) {
                    val group = matchResult.groupValues[i]
                    if (group.isNotEmpty() && isValidVideoId(group)) {
                        return group
                    }
                }
            }
        }

        return null
    }

    /**
     * Validates if the given string is a valid YouTube video ID
     * @param videoId The video ID to validate
     * @return true if the video ID is valid, false otherwise
     */
    fun isValidVideoId(videoId: String?): Boolean {
        if (videoId.isNullOrBlank()) {
            return false
        }
        return VIDEO_ID_PATTERN.matches(videoId.trim())
    }

    /**
     * Normalizes a YouTube URL to a standard format
     * @param url The YouTube URL to normalize
     * @return The normalized URL or null if the URL is invalid
     */
    fun normalizeYouTubeUrl(url: String?): String? {
        val videoId = extractVideoId(url)
        return if (videoId != null) {
            "https://www.youtube.com/watch?v=$videoId"
        } else {
            null
        }
    }

    /**
     * Checks if the URL is a YouTube Shorts URL
     * @param url The URL to check
     * @return true if the URL is a YouTube Shorts URL, false otherwise
     */
    fun isYouTubeShorts(url: String?): Boolean {
        if (url.isNullOrBlank()) {
            return false
        }

        val trimmedUrl = url.trim()
        val shortsPattern = Regex("""^(?i)https?://(www\.|m\.)?youtube\.com/shorts/([a-zA-Z0-9_-]{11})(\?.*)?$""")
        return shortsPattern.matches(trimmedUrl)
    }

    /**
     * Checks if the URL is a shortened youtu.be URL
     * @param url The URL to check
     * @return true if the URL is a youtu.be URL, false otherwise
     */
    fun isYouTuBeUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) {
            return false
        }

        val trimmedUrl = url.trim()
        val youtuBePattern = Regex("""^(?i)https?://youtu\.be/([a-zA-Z0-9_-]{11})(\?.*)?$""")
        return youtuBePattern.matches(trimmedUrl)
    }

    /**
     * Validates URL format and checks for common edge cases
     * @param url The URL to validate
     * @return ValidationResult with details about the validation
     */
    fun validateUrlWithDetails(url: String?): ValidationResult {
        if (url.isNullOrBlank()) {
            return ValidationResult(false, "URL is empty or null")
        }

        val trimmedUrl = url.trim()

        // Check for basic URL structure
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            return ValidationResult(false, "URL must start with http:// or https://")
        }

        // Check if it's a YouTube domain
        val isYouTubeDomain = YOUTUBE_DOMAINS.any { domain ->
            trimmedUrl.contains(domain, ignoreCase = true)
        }

        if (!isYouTubeDomain) {
            return ValidationResult(false, "URL is not from a supported YouTube domain")
        }

        // Check if it matches any valid pattern
        val matchesPattern = YOUTUBE_PATTERNS.any { pattern ->
            pattern.matches(trimmedUrl)
        }

        if (!matchesPattern) {
            return ValidationResult(false, "URL format is not supported")
        }

        // Extract and validate video ID
        val videoId = extractVideoId(trimmedUrl)
        if (videoId == null) {
            return ValidationResult(false, "Could not extract valid video ID from URL")
        }

        return ValidationResult(true, "Valid YouTube URL", videoId)
    }

    /**
     * Data class representing the result of URL validation
     */
    data class ValidationResult(
        val isValid: Boolean,
        val message: String,
        val videoId: String? = null
    )
}