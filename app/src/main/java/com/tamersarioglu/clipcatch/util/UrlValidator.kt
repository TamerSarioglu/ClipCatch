package com.tamersarioglu.clipcatch.util

import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class UrlValidator @Inject constructor() {

    companion object {
        private val YOUTUBE_DOMAINS = setOf(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "music.youtube.com",
            "youtu.be"
        )

        private val YOUTUBE_PATTERNS = listOf(
            Regex("""^(?i)https?://(www\\.|m\\.)?youtube\\.com/watch\\?.*v=([a-zA-Z0-9_-]{11})(&.*|#.*)?$"""),
            Regex("""^(?i)https?://(www\\.|m\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]{11})(\\?.*)?$"""),
            Regex("""^(?i)https?://youtu\\.be/([a-zA-Z0-9_-]{11})(\\?.*)?$"""),
            Regex("""^(?i)https?://(www\\.|m\\.)?youtube\\.com/embed/([a-zA-Z0-9_-]{11})(\\?.*)?$"""),
            Regex("""^(?i)https?://music\\.youtube\\.com/watch\\?.*v=([a-zA-Z0-9_-]{11})(&.*|#.*)?$"""),
            Regex("""^(?i)https?://(www\\.|m\\.)?youtube\\.com/live/([a-zA-Z0-9_-]{11})(\\?.*)?$""")
        )

        // Video ID pattern - YouTube video IDs are exactly 11 characters
        private val VIDEO_ID_PATTERN = Regex("""^[a-zA-Z0-9_-]{11}$""")
    }

    fun isValidYouTubeUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) {
            return false
        }
        val trimmedUrl = url.trim()
        return YOUTUBE_PATTERNS.any { pattern ->
            pattern.matches(trimmedUrl)
        }
    }

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

    fun isValidVideoId(videoId: String?): Boolean {
        if (videoId.isNullOrBlank()) {
            return false
        }
        return VIDEO_ID_PATTERN.matches(videoId.trim())
    }

    fun normalizeYouTubeUrl(url: String?): String? {
        val videoId = extractVideoId(url)
        return if (videoId != null) {
            "https://www.youtube.com/watch?v=$videoId"
        } else {
            null
        }
    }

    fun isYouTubeShorts(url: String?): Boolean {
        if (url.isNullOrBlank()) {
            return false
        }
        val trimmedUrl = url.trim()
        val shortsPattern = Regex("""^(?i)https?://(www\\.|m\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]{11})(\\?.*)?$""")
        return shortsPattern.matches(trimmedUrl)
    }

    fun isYouTuBeUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) {
            return false
        }
        val trimmedUrl = url.trim()
        val youtuBePattern = Regex("""^(?i)https?://youtu\\.be/([a-zA-Z0-9_-]{11})(\\?.*)?$""")
        return youtuBePattern.matches(trimmedUrl)
    }

    fun validateUrlWithDetails(url: String?): ValidationResult {
        if (url.isNullOrBlank()) {
            return ValidationResult(false, "URL is empty or null")
        }
        val trimmedUrl = url.trim()
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            return ValidationResult(false, "URL must start with http:// or https://")
        }
        val isYouTubeDomain = YOUTUBE_DOMAINS.any { domain ->
            trimmedUrl.contains(domain, ignoreCase = true)
        }
        if (!isYouTubeDomain) {
            return ValidationResult(false, "URL is not from a supported YouTube domain")
        }
        val matchesPattern = YOUTUBE_PATTERNS.any { pattern ->
            pattern.matches(trimmedUrl)
        }
        if (!matchesPattern) {
            return ValidationResult(false, "URL format is not supported")
        }
        val videoId = extractVideoId(trimmedUrl)
        if (videoId == null) {
            return ValidationResult(false, "Could not extract valid video ID from URL")
        }
        return ValidationResult(true, "Valid YouTube URL", videoId)
    }

    data class ValidationResult(
        val isValid: Boolean,
        val message: String,
        val videoId: String? = null
    )
}