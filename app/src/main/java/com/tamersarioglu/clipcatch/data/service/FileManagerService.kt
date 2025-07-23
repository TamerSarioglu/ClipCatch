package com.tamersarioglu.clipcatch.data.service

import java.io.File
import java.io.OutputStream

interface FileManagerService {

    suspend fun createDownloadFile(fileName: String, customPath: String? = null): File
    fun getDownloadsDirectory(): File
    fun hasEnoughStorageSpace(requiredBytes: Long): Boolean
    fun openFileOutputStream(file: File): OutputStream
    fun closeOutputStream(stream: OutputStream?)
    fun finalizeMediaStoreFile(file: File)
    fun deleteFile(file: File): Boolean
    fun getTotalStorageSpace(): Long
    fun getAvailableStorageSpace(): Long
    fun createDescriptiveFileName(videoTitle: String, format: String): String
}