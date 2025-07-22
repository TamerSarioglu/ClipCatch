package com.tamersarioglu.clipcatch.data.service

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of FileManagerService for file system operations
 * Supports both legacy storage and scoped storage (Android 10+)
 */
@Singleton
class FileManagerServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : FileManagerService {

    companion object {
        private const val TAG = "FileManagerService"
        private const val APP_FOLDER_NAME = "ClipCatch"
        private const val MIN_FREE_SPACE_BUFFER = 100 * 1024 * 1024 // 100MB buffer
        
        // Map to store MediaStore URIs for files (used for Android 10+)
        private val mediaStoreUris = mutableMapOf<String, Uri>()
    }

    /**
     * Creates a file for download with the given name
     * Uses MediaStore API for Android 10+ (scoped storage)
     * Uses direct file access for older Android versions
     */
    override suspend fun createDownloadFile(fileName: String, customPath: String?): File {
        return withContext(Dispatchers.IO) {
            // First check if we have enough storage space
            val estimatedFileSize = 100 * 1024 * 1024L // Default estimate of 100MB if unknown
            if (!hasEnoughStorageSpace(estimatedFileSize)) {
                throw IOException("Insufficient storage space for download")
            }
            
            val sanitizedFileName = sanitizeFileName(fileName)
            val uniqueFileName = getUniqueFileName(sanitizedFileName)
            
            return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+): Use MediaStore
                createFileWithMediaStore(uniqueFileName)
            } else {
                // Pre-Android 10: Direct file access
                createFileWithDirectAccess(uniqueFileName, customPath)
            }
        }
    }

    /**
     * Gets the default downloads directory
     * For Android 10+, this is just a placeholder as MediaStore is used
     */
    override fun getDownloadsDirectory(): File {
        val downloadsDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10+, we'll use MediaStore, but return this for compatibility
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), APP_FOLDER_NAME)
        } else {
            // For pre-Android 10, use public Downloads directory
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(publicDownloads, APP_FOLDER_NAME).apply {
                if (!exists()) {
                    mkdirs()
                }
            }
        }
        
        return downloadsDir
    }

    /**
     * Checks if there is sufficient storage space for a file of the given size
     */
    override fun hasEnoughStorageSpace(requiredBytes: Long): Boolean {
        val stat = StatFs(getDownloadsDirectory().path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        
        // Add buffer to required bytes to ensure some free space remains
        return availableBytes > (requiredBytes + MIN_FREE_SPACE_BUFFER)
    }

    /**
     * Opens an output stream for writing to a file
     */
    override fun openFileOutputStream(file: File): OutputStream {
        // For Android 10+, we need to use ContentResolver if this is a MediaStore file
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaStoreUris.containsKey(file.absolutePath)) {
            val uri = mediaStoreUris[file.absolutePath]
                ?: throw IOException("No MediaStore URI found for file: ${file.name}")
            context.contentResolver.openOutputStream(uri)
                ?: throw IOException("Failed to open output stream for URI: $uri")
        } else {
            FileOutputStream(file)
        }
    }

    /**
     * Closes an output stream safely
     */
    override fun closeOutputStream(stream: OutputStream?) {
        try {
            stream?.flush()
            stream?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing output stream", e)
        }
    }
    
    /**
     * Finalizes a file in MediaStore after writing is complete (Android 10+)
     * This makes the file visible to other apps
     */
    override fun finalizeMediaStoreFile(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = mediaStoreUris[file.absolutePath] ?: return
            
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            
            try {
                context.contentResolver.update(uri, contentValues, null, null)
                // Remove the URI from our map after finalizing
                mediaStoreUris.remove(file.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "Error finalizing MediaStore file", e)
            }
        }
    }
    
    /**
     * Deletes a file, handling both direct file access and MediaStore
     */
    override fun deleteFile(file: File): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaStoreUris.containsKey(file.absolutePath)) {
            val uri = mediaStoreUris[file.absolutePath] ?: return false
            try {
                val deleted = context.contentResolver.delete(uri, null, null) > 0
                if (deleted) {
                    mediaStoreUris.remove(file.absolutePath)
                }
                deleted
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting MediaStore file", e)
                false
            }
        } else {
            file.delete()
        }
    }
    
    /**
     * Gets total storage space in bytes
     */
    override fun getTotalStorageSpace(): Long {
        val stat = StatFs(getDownloadsDirectory().path)
        return stat.totalBytes
    }
    
    /**
     * Gets available storage space in bytes
     */
    override fun getAvailableStorageSpace(): Long {
        val stat = StatFs(getDownloadsDirectory().path)
        return stat.availableBytes
    }
    
    /**
     * Creates a descriptive file name based on video title and current date
     */
    override fun createDescriptiveFileName(videoTitle: String, format: String): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val dateString = dateFormat.format(Date())
        
        // Sanitize the video title
        val sanitizedTitle = sanitizeFileName(videoTitle)
        
        // Truncate title if it's too long (max 100 chars)
        val truncatedTitle = if (sanitizedTitle.length > 100) {
            sanitizedTitle.substring(0, 100)
        } else {
            sanitizedTitle
        }
        
        // Ensure format has a dot prefix
        val formatWithDot = if (format.startsWith(".")) format else ".$format"
        
        return "${truncatedTitle}_${dateString}${formatWithDot}"
    }

    /**
     * Creates a file using MediaStore API (for Android 10+)
     */
    private fun createFileWithMediaStore(fileName: String): File {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw IllegalStateException("MediaStore Downloads API requires Android 10 (API 29) or higher")
        }
        
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, getMimeType(fileName))
            put(MediaStore.Downloads.IS_PENDING, 1)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$APP_FOLDER_NAME")
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IOException("Failed to create MediaStore entry")

        // Create a temporary file that will be used to write content
        val tempFile = File(context.cacheDir, fileName)
        tempFile.deleteOnExit() // Mark for deletion when app exits
        
        // Store the URI for later use
        mediaStoreUris[tempFile.absolutePath] = uri
        
        return tempFile
    }

    /**
     * Creates a file using direct file access (for pre-Android 10)
     */
    private fun createFileWithDirectAccess(fileName: String, customPath: String?): File {
        val directory = if (customPath != null) {
            File(customPath).apply {
                if (!exists()) {
                    mkdirs()
                }
            }
        } else {
            getDownloadsDirectory()
        }

        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Failed to create directory: ${directory.absolutePath}")
        }

        val file = File(directory, fileName)
        if (!file.createNewFile() && !file.exists()) {
            throw IOException("Failed to create file: ${file.absolutePath}")
        }

        return file
    }

    /**
     * Sanitizes a file name to remove invalid characters
     */
    private fun sanitizeFileName(fileName: String): String {
        // Replace invalid file name characters with underscores
        val sanitized = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        
        // Replace multiple spaces with a single space
        return sanitized.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Generates a unique file name if a file with the same name already exists
     */
    private fun getUniqueFileName(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "")
        val nameWithoutExtension = fileName.substringBeforeLast('.', fileName)
        
        var uniqueName = fileName
        var counter = 1
        
        // For Android 10+, check MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(MediaStore.Downloads.DISPLAY_NAME)
            
            while (true) {
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(uniqueName)
                
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        // No duplicate found
                        break
                    }
                    
                    // File exists, create a new name
                    uniqueName = if (extension.isEmpty()) {
                        "${nameWithoutExtension}_${counter++}"
                    } else {
                        "${nameWithoutExtension}_${counter++}.${extension}"
                    }
                }
            }
        } else {
            // For pre-Android 10, check file system directly
            var file = File(getDownloadsDirectory(), uniqueName)
            
            while (file.exists()) {
                uniqueName = if (extension.isEmpty()) {
                    "${nameWithoutExtension}_${counter++}"
                } else {
                    "${nameWithoutExtension}_${counter++}.${extension}"
                }
                file = File(getDownloadsDirectory(), uniqueName)
            }
        }
        
        return uniqueName
    }

    /**
     * Determines the MIME type based on file extension
     */
    private fun getMimeType(fileName: String): String {
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