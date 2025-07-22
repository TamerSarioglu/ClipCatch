package com.tamersarioglu.clipcatch.data.service

import java.io.File
import java.io.OutputStream

/**
 * Service interface for file system operations
 * Handles file creation, storage space validation, and file management
 * with support for both legacy storage and Android 10+ scoped storage
 */
interface FileManagerService {
    
    /**
     * Creates a file for download with the given name
     * 
     * @param fileName The name of the file to create
     * @param customPath Optional custom path (null for default Downloads folder)
     * @return The created File object
     * @throws IOException if file creation fails or if there's insufficient storage
     * @throws SecurityException if permission is denied
     */
    suspend fun createDownloadFile(fileName: String, customPath: String? = null): File
    
    /**
     * Gets the default downloads directory
     * 
     * @return File object representing the downloads directory
     */
    fun getDownloadsDirectory(): File
    
    /**
     * Checks if there is sufficient storage space for a file of the given size
     * 
     * @param requiredBytes The number of bytes needed
     * @return true if sufficient space is available, false otherwise
     */
    fun hasEnoughStorageSpace(requiredBytes: Long): Boolean
    
    /**
     * Opens an output stream for writing to a file
     * 
     * @param file The file to write to
     * @return OutputStream for the file
     * @throws IOException if stream creation fails
     */
    fun openFileOutputStream(file: File): OutputStream
    
    /**
     * Closes an output stream safely
     * 
     * @param stream The stream to close
     */
    fun closeOutputStream(stream: OutputStream?)
    
    /**
     * Finalizes a file in MediaStore after writing is complete (Android 10+)
     * This makes the file visible to other apps
     * 
     * @param file The file to finalize
     */
    fun finalizeMediaStoreFile(file: File)
    
    /**
     * Deletes a file, handling both direct file access and MediaStore
     * 
     * @param file The file to delete
     * @return true if deletion was successful, false otherwise
     */
    fun deleteFile(file: File): Boolean
    
    /**
     * Gets total storage space in bytes
     * 
     * @return Total storage space in bytes
     */
    fun getTotalStorageSpace(): Long
    
    /**
     * Gets available storage space in bytes
     * 
     * @return Available storage space in bytes
     */
    fun getAvailableStorageSpace(): Long
    
    /**
     * Creates a descriptive file name based on video title and current date
     * 
     * @param videoTitle The title of the video
     * @param format The file format/extension (e.g., "mp4")
     * @return A descriptive file name with format
     */
    fun createDescriptiveFileName(videoTitle: String, format: String): String
}