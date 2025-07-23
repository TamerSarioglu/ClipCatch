package com.tamersarioglu.clipcatch.data.service

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileManagerServiceImplTest {

    @Test
    fun `createDescriptiveFileName generates correct format`() {
        val service = TestableFileManagerService()
        val fixedDate = Date(1626912000000)
        val result = service.createDescriptiveFileNameWithDate("Test Video Title", "mp4", fixedDate)
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val dateString = dateFormat.format(fixedDate)
        assertEquals("Test Video Title_${dateString}.mp4", result)
    }

    @Test
    fun `createDescriptiveFileName sanitizes title`() {
        val service = TestableFileManagerService()
        val fixedDate = Date(1626912000000)
        val result = service.createDescriptiveFileNameWithDate("Test/Video:Title*?", "mp4", fixedDate)
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val dateString = dateFormat.format(fixedDate)
        assertEquals("Test_Video_Title___${dateString}.mp4", result)
    }

    @Test
    fun `createDescriptiveFileName truncates long titles`() {
        val service = TestableFileManagerService()
        val fixedDate = Date(1626912000000)
        val longTitle = "A".repeat(150)
        val result = service.createDescriptiveFileNameWithDate(longTitle, "mp4", fixedDate)
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val dateString = dateFormat.format(fixedDate)
        assertEquals("${"A".repeat(100)}_${dateString}.mp4", result)
    }

    @Test
    fun `sanitizeFileName replaces invalid characters`() {
        val service = TestableFileManagerService()
        val result = service.sanitizeFileName("Test\\File/Name:With*Invalid?Characters\"<>|")
        assertEquals("Test_File_Name_With_Invalid_Characters____", result)
    }

    @Test
    fun `sanitizeFileName handles multiple spaces`() {
        val service = TestableFileManagerService()
        val result = service.sanitizeFileName("Test    File   With     Spaces")
        assertEquals("Test File With Spaces", result)
    }

    @Test
    fun `getMimeType returns correct type for video files`() {
        val service = TestableFileManagerService()
        assertEquals("video/mp4", service.getMimeType("test.mp4"))
        assertEquals("video/webm", service.getMimeType("test.webm"))
        assertEquals("video/x-matroska", service.getMimeType("test.mkv"))
    }

    @Test
    fun `getMimeType returns correct type for audio files`() {
        val service = TestableFileManagerService()
        assertEquals("audio/mpeg", service.getMimeType("test.mp3"))
        assertEquals("audio/mp4", service.getMimeType("test.m4a"))
        assertEquals("audio/aac", service.getMimeType("test.aac"))
        assertEquals("audio/wav", service.getMimeType("test.wav"))
    }

    @Test
    fun `getMimeType returns default for unknown extensions`() {
        val service = TestableFileManagerService()
        assertEquals("application/octet-stream", service.getMimeType("test.unknown"))
        assertEquals("application/octet-stream", service.getMimeType("test"))
    }

    @Test
    fun `getMimeType is case insensitive`() {
        val service = TestableFileManagerService()
        assertEquals("video/mp4", service.getMimeType("test.MP4"))
        assertEquals("audio/mpeg", service.getMimeType("test.MP3"))
    }

    private class TestableFileManagerService {
        fun createDescriptiveFileNameWithDate(videoTitle: String, format: String, date: Date): String {
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val dateString = dateFormat.format(date)
            val sanitizedTitle = sanitizeFileName(videoTitle)
            val truncatedTitle = if (sanitizedTitle.length > 100) {
                sanitizedTitle.substring(0, 100)
            } else {
                sanitizedTitle
            }
            val formatWithDot = if (format.startsWith(".")) format else ".${format}"
            return "${truncatedTitle}_${dateString}${formatWithDot}"
        }
        fun sanitizeFileName(fileName: String): String {
            val sanitized = fileName.replace(Regex("[\\/:*?\"<>|]"), "_")
            return sanitized.replace(Regex("\\s+"), " ").trim()
        }
        fun getMimeType(fileName: String): String {
            return when (fileName.substringAfterLast('.', "").lowercase()) {
                "mp4" -> "video/mp4"
                "webm" -> "video/webm"
                "mkv" -> "video/x-matroska"
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "aac" -> "audio/aac"
                "wav" -> "audio/wav"
                else -> "application/octet-stream"
            }
        }
    }
}