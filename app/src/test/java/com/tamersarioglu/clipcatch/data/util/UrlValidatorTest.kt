package com.tamersarioglu.clipcatch.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UrlValidatorTest {

    private lateinit var urlValidator: UrlValidator

    @Before
    fun setUp() {
        urlValidator = UrlValidator()
    }

    @Test
    fun `isValidYouTubeUrl returns false for null or empty URLs`() {
        assertFalse(urlValidator.isValidYouTubeUrl(null))
        assertFalse(urlValidator.isValidYouTubeUrl(""))
        assertFalse(urlValidator.isValidYouTubeUrl("   "))
    }

    @Test
    fun `isValidYouTubeUrl returns true for standard YouTube watch URLs`() {
        val validUrls =
                listOf(
                        "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                        "http://www.youtube.com/watch?v=dQw4w9WgXcQ",
                        "https://youtube.com/watch?v=dQw4w9WgXcQ",
                        "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30s",
                        "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmRdnEQy8VJqQzNYaJHlx_uQyduQd",
                        "https://www.youtube.com/watch?t=30s&v=dQw4w9WgXcQ"
                )

        validUrls.forEach { url ->
            assertTrue("URL should be valid: $url", urlValidator.isValidYouTubeUrl(url))
        }
    }

    @Test
    fun `isValidYouTubeUrl returns true for mobile YouTube URLs`() {
        val validUrls =
                listOf(
                        "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
                        "http://m.youtube.com/watch?v=dQw4w9WgXcQ",
                        "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=30s"
                )

        validUrls.forEach { url ->
            assertTrue("Mobile URL should be valid: $url", urlValidator.isValidYouTubeUrl(url))
        }
    }

    @Test
    fun `isValidYouTubeUrl returns true for YouTube Shorts URLs`() {
        val validUrls =
                listOf(
                        "https://www.youtube.com/shorts/dQw4w9WgXcQ",
                        "https://youtube.com/shorts/dQw4w9WgXcQ",
                        "https://m.youtube.com/shorts/dQw4w9WgXcQ",
                        "https://www.youtube.com/shorts/dQw4w9WgXcQ?feature=share"
                )

        validUrls.forEach { url ->
            assertTrue("Shorts URL should be valid: $url", urlValidator.isValidYouTubeUrl(url))
        }
    }

    @Test
    fun `isValidYouTubeUrl returns true for youtu_be shortened URLs`() {
        val validUrls =
                listOf(
                        "https://youtu.be/dQw4w9WgXcQ",
                        "http://youtu.be/dQw4w9WgXcQ",
                        "https://youtu.be/dQw4w9WgXcQ?t=30s",
                        "https://youtu.be/dQw4w9WgXcQ?si=abc123"
                )

        validUrls.forEach { url ->
            assertTrue("youtu.be URL should be valid: $url", urlValidator.isValidYouTubeUrl(url))
        }
    }

    @Test
    fun `isValidYouTubeUrl returns true for embed URLs`() {
        val validUrls =
                listOf(
                        "https://www.youtube.com/embed/dQw4w9WgXcQ",
                        "https://youtube.com/embed/dQw4w9WgXcQ",
                        "https://www.youtube.com/embed/dQw4w9WgXcQ?autoplay=1"
                )

        validUrls.forEach { url ->
            assertTrue("Embed URL should be valid: $url", urlValidator.isValidYouTubeUrl(url))
        }
    }

    @Test
    fun `isValidYouTubeUrl returns true for YouTube Music URLs`() {
        val validUrls =
                listOf(
                        "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
                        "https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmRdnEQy8VJqQzNYaJHlx_uQyduQd"
                )

        validUrls.forEach { url ->
            assertTrue(
                    "YouTube Music URL should be valid: $url",
                    urlValidator.isValidYouTubeUrl(url)
            )
        }
    }

    @Test
    fun `isValidYouTubeUrl returns true for live URLs`() {
        val validUrls =
                listOf(
                        "https://www.youtube.com/live/dQw4w9WgXcQ",
                        "https://youtube.com/live/dQw4w9WgXcQ",
                        "https://m.youtube.com/live/dQw4w9WgXcQ"
                )

        validUrls.forEach { url ->
            assertTrue("Live URL should be valid: $url", urlValidator.isValidYouTubeUrl(url))
        }
    }

    @Test
    fun `isValidYouTubeUrl returns false for invalid URLs`() {
        val invalidUrls =
                listOf(
                        "https://www.google.com",
                        "https://vimeo.com/123456789",
                        "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw",
                        "https://www.youtube.com/user/username",
                        "https://www.youtube.com/playlist?list=PLrAXtmRdnEQy8VJqQzNYaJHlx_uQyduQd",
                        "https://www.youtube.com/watch?v=invalid",
                        "https://www.youtube.com/watch?v=dQw4w9WgXcQ123", // Too long video ID
                        "https://www.youtube.com/watch?v=dQw4w9WgX", // Too short video ID
                        "not-a-url",
                        "youtube.com/watch?v=dQw4w9WgXcQ", // Missing protocol
                        "https://youtube.co.uk/watch?v=dQw4w9WgXcQ" // Wrong domain
                )

        invalidUrls.forEach { url ->
            assertFalse("URL should be invalid: $url", urlValidator.isValidYouTubeUrl(url))
        }
    }

    @Test
    fun `extractVideoId returns correct video ID for various URL formats`() {
        val testCases =
                mapOf(
                        "https://www.youtube.com/watch?v=dQw4w9WgXcQ" to "dQw4w9WgXcQ",
                        "https://youtu.be/dQw4w9WgXcQ" to "dQw4w9WgXcQ",
                        "https://www.youtube.com/shorts/dQw4w9WgXcQ" to "dQw4w9WgXcQ",
                        "https://www.youtube.com/embed/dQw4w9WgXcQ" to "dQw4w9WgXcQ",
                        "https://music.youtube.com/watch?v=dQw4w9WgXcQ" to "dQw4w9WgXcQ",
                        "https://www.youtube.com/live/dQw4w9WgXcQ" to "dQw4w9WgXcQ",
                        "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30s" to "dQw4w9WgXcQ",
                        "https://youtu.be/dQw4w9WgXcQ?t=30s" to "dQw4w9WgXcQ"
                )

        testCases.forEach { (url, expectedVideoId) ->
            assertEquals(
                    "Video ID should match for URL: $url",
                    expectedVideoId,
                    urlValidator.extractVideoId(url)
            )
        }
    }

    @Test
    fun `extractVideoId returns null for invalid URLs`() {
        val invalidUrls =
                listOf(
                        null,
                        "",
                        "https://www.google.com",
                        "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw",
                        "not-a-url"
                )

        invalidUrls.forEach { url ->
            assertNull(
                    "Video ID should be null for invalid URL: $url",
                    urlValidator.extractVideoId(url)
            )
        }
    }

    @Test
    fun `isValidVideoId returns true for valid video IDs`() {
        val validVideoIds =
                listOf(
                        "dQw4w9WgXcQ",
                        "abc123DEF45",
                        "___________", // 11 underscores
                        "-----------", // 11 dashes
                        "abcDEF12345"
                )

        validVideoIds.forEach { videoId ->
            assertTrue("Video ID should be valid: $videoId", urlValidator.isValidVideoId(videoId))
        }
    }

    @Test
    fun `isValidVideoId returns false for invalid video IDs`() {
        val invalidVideoIds =
                listOf(
                        null,
                        "",
                        "   ",
                        "short",
                        "toolongvideoid123",
                        "invalid@char",
                        "dQw4w9WgXc", // 10 characters
                        "dQw4w9WgXcQ1" // 12 characters
                )

        invalidVideoIds.forEach { videoId ->
            assertFalse(
                    "Video ID should be invalid: $videoId",
                    urlValidator.isValidVideoId(videoId)
            )
        }
    }

    @Test
    fun `normalizeYouTubeUrl returns standard format for valid URLs`() {
        val testCases =
                mapOf(
                        "https://youtu.be/dQw4w9WgXcQ" to
                                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                        "https://www.youtube.com/shorts/dQw4w9WgXcQ" to
                                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                        "https://www.youtube.com/embed/dQw4w9WgXcQ" to
                                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                        "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30s" to
                                "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
                )

        testCases.forEach { (inputUrl, expectedUrl) ->
            assertEquals(
                    "Normalized URL should match for: $inputUrl",
                    expectedUrl,
                    urlValidator.normalizeYouTubeUrl(inputUrl)
            )
        }
    }

    @Test
    fun `normalizeYouTubeUrl returns null for invalid URLs`() {
        val invalidUrls = listOf(null, "", "https://www.google.com", "not-a-url")

        invalidUrls.forEach { url ->
            assertNull(
                    "Normalized URL should be null for invalid URL: $url",
                    urlValidator.normalizeYouTubeUrl(url)
            )
        }
    }

    @Test
    fun `isYouTubeShorts returns true only for Shorts URLs`() {
        assertTrue(urlValidator.isYouTubeShorts("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertTrue(urlValidator.isYouTubeShorts("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertTrue(urlValidator.isYouTubeShorts("https://m.youtube.com/shorts/dQw4w9WgXcQ"))

        assertFalse(urlValidator.isYouTubeShorts("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(urlValidator.isYouTubeShorts("https://youtu.be/dQw4w9WgXcQ"))
        assertFalse(urlValidator.isYouTubeShorts(null))
    }

    @Test
    fun `isYouTuBeUrl returns true only for youtu_be URLs`() {
        assertTrue(urlValidator.isYouTuBeUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(urlValidator.isYouTuBeUrl("http://youtu.be/dQw4w9WgXcQ"))
        assertTrue(urlValidator.isYouTuBeUrl("https://youtu.be/dQw4w9WgXcQ?t=30s"))

        assertFalse(urlValidator.isYouTuBeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(urlValidator.isYouTuBeUrl("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertFalse(urlValidator.isYouTuBeUrl(null))
    }

    @Test
    fun `validateUrlWithDetails returns detailed validation results`() {
        // Valid URL
        val validResult =
                urlValidator.validateUrlWithDetails("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertTrue(validResult.isValid)
        assertEquals("Valid YouTube URL", validResult.message)
        assertEquals("dQw4w9WgXcQ", validResult.videoId)

        // Empty URL
        val emptyResult = urlValidator.validateUrlWithDetails("")
        assertFalse(emptyResult.isValid)
        assertEquals("URL is empty or null", emptyResult.message)
        assertNull(emptyResult.videoId)

        // Invalid protocol
        val noProtocolResult =
                urlValidator.validateUrlWithDetails("youtube.com/watch?v=dQw4w9WgXcQ")
        assertFalse(noProtocolResult.isValid)
        assertEquals("URL must start with http:// or https://", noProtocolResult.message)

        // Non-YouTube domain
        val nonYouTubeResult = urlValidator.validateUrlWithDetails("https://www.google.com")
        assertFalse(nonYouTubeResult.isValid)
        assertEquals("URL is not from a supported YouTube domain", nonYouTubeResult.message)

        // Invalid format
        val invalidFormatResult =
                urlValidator.validateUrlWithDetails(
                        "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw"
                )
        assertFalse(invalidFormatResult.isValid)
        assertEquals("URL format is not supported", invalidFormatResult.message)
    }

    @Test
    fun `URL validation handles edge cases correctly`() {
        // URLs with extra whitespace
        assertTrue(
                urlValidator.isValidYouTubeUrl("  https://www.youtube.com/watch?v=dQw4w9WgXcQ  ")
        )

        // Mixed case domains
        assertTrue(urlValidator.isValidYouTubeUrl("https://WWW.YOUTUBE.COM/watch?v=dQw4w9WgXcQ"))

        // URLs with multiple query parameters
        assertTrue(
                urlValidator.isValidYouTubeUrl(
                        "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmRdnEQy8VJqQzNYaJHlx_uQyduQd&index=1&t=30s"
                )
        )

        // URLs with fragments
        assertTrue(
                urlValidator.isValidYouTubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ#t=30s")
        )
    }

    @Test
    fun `video ID extraction handles complex URLs correctly`() {
        // URL with video ID not as first parameter
        assertEquals(
                "dQw4w9WgXcQ",
                urlValidator.extractVideoId(
                        "https://www.youtube.com/watch?t=30s&v=dQw4w9WgXcQ&list=PLtest"
                )
        )

        // URL with similar looking parameters
        assertEquals(
                "dQw4w9WgXcQ",
                urlValidator.extractVideoId("https://www.youtube.com/watch?version=3&v=dQw4w9WgXcQ")
        )

        // Shorts URL with query parameters
        assertEquals(
                "dQw4w9WgXcQ",
                urlValidator.extractVideoId(
                        "https://www.youtube.com/shorts/dQw4w9WgXcQ?feature=share&utm_source=copy"
                )
        )
    }

    @Test
    fun `URL validation handles special characters and encoding correctly`() {
        // URLs with encoded characters
        assertTrue(
                urlValidator.isValidYouTubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30s")
        )

        // URLs with anchor fragments
        assertTrue(
                urlValidator.isValidYouTubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ#t=30s")
        )

        // URLs with both query params and fragments
        assertTrue(
                urlValidator.isValidYouTubeUrl(
                        "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLtest#t=30s"
                )
        )
    }

    @Test
    fun `URL validation handles different video ID patterns correctly`() {
        // Video IDs with different character combinations
        val validVideoIds =
                listOf(
                        "abcdefghijk", // all lowercase
                        "ABCDEFGHIJK", // all uppercase
                        "123456789ab", // numbers and letters
                        "a-b_c123456", // with dashes and underscores
                        "_-_-_-_-_-_", // alternating special chars
                        "0123456789a" // starting with numbers
                )

        validVideoIds.forEach { videoId ->
            val testUrl = "https://www.youtube.com/watch?v=$videoId"
            assertTrue(
                    "URL with video ID '$videoId' should be valid",
                    urlValidator.isValidYouTubeUrl(testUrl)
            )
            assertEquals(
                    "Should extract correct video ID",
                    videoId,
                    urlValidator.extractVideoId(testUrl)
            )
        }
    }

    @Test
    fun `URL validation rejects malformed video IDs correctly`() {
        val invalidVideoIds =
                listOf(
                        "abc@defghij", // invalid character @
                        "abc defghij", // space character
                        "abc+defghij", // invalid character +
                        "abc=defghij", // invalid character =
                        "abc.defghij", // invalid character .
                        "abc/defghij", // invalid character /
                        "abc\\defghij" // invalid character \
                )

        invalidVideoIds.forEach { videoId ->
            val testUrl = "https://www.youtube.com/watch?v=$videoId"
            assertFalse(
                    "URL with invalid video ID '$videoId' should be rejected",
                    urlValidator.isValidYouTubeUrl(testUrl)
            )
        }
    }

    @Test
    fun `URL validation handles protocol variations correctly`() {
        val baseUrl = "www.youtube.com/watch?v=dQw4w9WgXcQ"

        // Test both HTTP and HTTPS
        assertTrue("HTTPS should be valid", urlValidator.isValidYouTubeUrl("https://$baseUrl"))
        assertTrue("HTTP should be valid", urlValidator.isValidYouTubeUrl("http://$baseUrl"))

        // Test invalid protocols
        assertFalse("FTP should be invalid", urlValidator.isValidYouTubeUrl("ftp://$baseUrl"))
        assertFalse("No protocol should be invalid", urlValidator.isValidYouTubeUrl(baseUrl))
    }

    @Test
    fun `validateUrlWithDetails provides comprehensive error information`() {
        // Test null URL
        val nullResult = urlValidator.validateUrlWithDetails(null)
        assertFalse(nullResult.isValid)
        assertEquals("URL is empty or null", nullResult.message)

        // Test empty URL
        val emptyResult = urlValidator.validateUrlWithDetails("")
        assertFalse(emptyResult.isValid)
        assertEquals("URL is empty or null", emptyResult.message)

        // Test blank URL
        val blankResult = urlValidator.validateUrlWithDetails("   ")
        assertFalse(blankResult.isValid)
        assertEquals("URL is empty or null", blankResult.message)

        // Test missing protocol
        val noProtocolResult =
                urlValidator.validateUrlWithDetails("youtube.com/watch?v=dQw4w9WgXcQ")
        assertFalse(noProtocolResult.isValid)
        assertEquals("URL must start with http:// or https://", noProtocolResult.message)

        // Test non-YouTube domain
        val nonYouTubeResult =
                urlValidator.validateUrlWithDetails("https://www.google.com/watch?v=dQw4w9WgXcQ")
        assertFalse(nonYouTubeResult.isValid)
        assertEquals("URL is not from a supported YouTube domain", nonYouTubeResult.message)

        // Test unsupported format
        val unsupportedResult =
                urlValidator.validateUrlWithDetails("https://www.youtube.com/channel/UCtest")
        assertFalse(unsupportedResult.isValid)
        assertEquals("URL format is not supported", unsupportedResult.message)

        // Test invalid video ID (wrong length)
        val invalidVideoIdResult =
                urlValidator.validateUrlWithDetails("https://www.youtube.com/watch?v=invalid")
        assertFalse(invalidVideoIdResult.isValid)
        assertEquals("URL format is not supported", invalidVideoIdResult.message)
    }
}
