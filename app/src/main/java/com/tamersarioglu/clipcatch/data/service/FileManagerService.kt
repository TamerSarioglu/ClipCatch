package com.tamersarioglu.clipcatch.data.service

import java.io.File
import java.io.OutputStream

/**
 * Service interface for file system operations
 * Note: This is a minimal implementation to support the DownloadManagerService.
 * A more complete implementation will be done in task 4.3.
 */
interface FileManagerService {
    
    /**
     * Creates a file for download with the given name
     * 
     * @param fileName The name of the file to create
     * @param customPath Optional custom path (null for default Downloads folder)
     * @return The created File object
     * @throws IOException if file creation fails
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
}