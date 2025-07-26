package com.tamersarioglu.clipcatch.data.service

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import com.tamersarioglu.clipcatch.util.Logger
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

@Singleton
class FileManagerServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
) : FileManagerService {

    companion object {
        private const val TAG = "FileManagerService"
        private const val APP_FOLDER_NAME = "ClipCatch"
        private const val MIN_FREE_SPACE_BUFFER = 100 * 1024 * 1024
        
        private val mediaStoreUris = mutableMapOf<String, Uri>()
    }

    override suspend fun createDownloadFile(fileName: String, customPath: String?): File {
        logger.enter(TAG, "createDownloadFile", fileName, customPath)
        
        return withContext(Dispatchers.IO) {
            try {
                if (fileName.isBlank()) {
                    logger.e(TAG, "File name cannot be blank")
                    throw IllegalArgumentException("File name cannot be blank")
                }
                
                val estimatedFileSize = 100 * 1024 * 1024L
                logger.d(TAG, "Checking storage space for estimated file size: ${formatBytes(estimatedFileSize)}")
                
                if (!hasEnoughStorageSpace(estimatedFileSize)) {
                    val availableSpace = getAvailableStorageSpace()
                    logger.e(TAG, "Insufficient storage space. Available: ${formatBytes(availableSpace)}, Required: ${formatBytes(estimatedFileSize)}")
                    throw IOException("Insufficient storage space. Available: ${formatBytes(availableSpace)}, Required: ${formatBytes(estimatedFileSize)}")
                }
                
                val sanitizedFileName = sanitizeFileName(fileName)
                logger.d(TAG, "Sanitized file name: $sanitizedFileName")
                
                val uniqueFileName = getUniqueFileName(sanitizedFileName)
                logger.d(TAG, "Unique file name: $uniqueFileName")
                
                val file = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    logger.d(TAG, "Using MediaStore API for Android 10+")
                    createFileWithMediaStore(uniqueFileName)
                } else {
                    logger.d(TAG, "Using direct file access for pre-Android 10")
                    createFileWithDirectAccess(uniqueFileName, customPath)
                }
                
                logger.logFileOperation(TAG, "CREATE", file.absolutePath, true)
                return@withContext file
                
            } catch (e: Exception) {
                logger.e(TAG, "Failed to create download file", e)
                throw e
            }
        }.also { file ->
            logger.exit(TAG, "createDownloadFile", file.absolutePath)
        }
    }

    override fun getDownloadsDirectory(): File {
        val downloadsDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), APP_FOLDER_NAME)
        } else {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(publicDownloads, APP_FOLDER_NAME).apply {
                if (!exists()) {
                    mkdirs()
                }
            }
        }
        
        return downloadsDir
    }

    override fun hasEnoughStorageSpace(requiredBytes: Long): Boolean {
        val stat = StatFs(getDownloadsDirectory().path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        
        return availableBytes > (requiredBytes + MIN_FREE_SPACE_BUFFER)
    }

    override fun openFileOutputStream(file: File): OutputStream {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaStoreUris.containsKey(file.absolutePath)) {
            val uri = mediaStoreUris[file.absolutePath]
                ?: throw IOException("No MediaStore URI found for file: ${file.name}")
            context.contentResolver.openOutputStream(uri)
                ?: throw IOException("Failed to open output stream for URI: $uri")
        } else {
            FileOutputStream(file)
        }
    }

    override fun closeOutputStream(stream: OutputStream?) {
        try {
            stream?.flush()
            stream?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing output stream", e)
        }
    }
    
    override fun finalizeMediaStoreFile(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = mediaStoreUris[file.absolutePath] ?: return
            
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            
            try {
                context.contentResolver.update(uri, contentValues, null, null)
                mediaStoreUris.remove(file.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "Error finalizing MediaStore file", e)
            }
        }
    }
    
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
    
    override fun getTotalStorageSpace(): Long {
        val stat = StatFs(getDownloadsDirectory().path)
        return stat.totalBytes
    }
    
    override fun getAvailableStorageSpace(): Long {
        val stat = StatFs(getDownloadsDirectory().path)
        return stat.availableBytes
    }
    
    override fun createDescriptiveFileName(videoTitle: String, format: String): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val dateString = dateFormat.format(Date())
        
        val sanitizedTitle = sanitizeFileName(videoTitle)
        
        val truncatedTitle = if (sanitizedTitle.length > 100) {
            sanitizedTitle.substring(0, 100)
        } else {
            sanitizedTitle
        }
        
        val formatWithDot = if (format.startsWith(".")) format else ".$format"
        
        return "${truncatedTitle}_${dateString}${formatWithDot}"
    }

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

        val tempFile = File(context.cacheDir, fileName)
        tempFile.deleteOnExit()
        
        mediaStoreUris[tempFile.absolutePath] = uri
        
        return tempFile
    }

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

    private fun sanitizeFileName(fileName: String): String {
        val sanitized = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        
        return sanitized.replace(Regex("\\s+"), " ").trim()
    }

    private fun getUniqueFileName(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "")
        val nameWithoutExtension = fileName.substringBeforeLast('.', fileName)
        
        var uniqueName = fileName
        var counter = 1
        
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
                        break
                    }
                    
                    uniqueName = if (extension.isEmpty()) {
                        "${nameWithoutExtension}_${counter++}"
                    } else {
                        "${nameWithoutExtension}_${counter++}.${extension}"
                    }
                }
            }
        } else {
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
    
    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return "%.1f %s".format(size, units[unitIndex])
    }
}