package com.tamersarioglu.clipcatch.data.service

import android.content.Context
import android.os.StatFs
import com.tamersarioglu.clipcatch.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton


interface StorageMonitorService {

    fun getCurrentStorageInfo(): StorageInfo
    fun storageInfoFlow(): StateFlow<StorageInfo>
    fun hasEnoughSpaceForDownload(requiredBytes: Long): Boolean
    fun getCleanupRecommendations(): List<CleanupRecommendation>
    suspend fun performAutomaticCleanup(): CleanupResult
    fun startMonitoring()
    fun stopMonitoring()
}

@Singleton
class StorageMonitorServiceImpl @Inject constructor(
    private val context: Context,
    private val fileManagerService: FileManagerService,
    private val logger: Logger
) : StorageMonitorService {
    
    companion object {
        private const val TAG = "StorageMonitorService"
        private const val MONITORING_INTERVAL_MS = 30000L
        private const val LOW_STORAGE_THRESHOLD = 500 * 1024 * 1024L
        private const val CRITICAL_STORAGE_THRESHOLD = 100 * 1024 * 1024L
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _storageInfo = MutableStateFlow(getCurrentStorageInfo())
    private val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()
    private var isMonitoring = false
    
    override fun getCurrentStorageInfo(): StorageInfo {
        return try {
            val downloadsDir = fileManagerService.getDownloadsDirectory()
            val stat = StatFs(downloadsDir.path)
            
            val totalBytes = stat.totalBytes
            val availableBytes = stat.availableBytes
            val usedBytes = totalBytes - availableBytes
            val usagePercentage = ((usedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt()
            
            val status = when {
                availableBytes < CRITICAL_STORAGE_THRESHOLD -> StorageStatus.CRITICAL
                availableBytes < LOW_STORAGE_THRESHOLD -> StorageStatus.LOW
                else -> StorageStatus.NORMAL
            }
            
            StorageInfo(
                totalBytes = totalBytes,
                availableBytes = availableBytes,
                usedBytes = usedBytes,
                usagePercentage = usagePercentage,
                status = status,
                path = downloadsDir.absolutePath
            )
            
        } catch (e: Exception) {
            logger.e(TAG, "Error getting storage info", e)
            StorageInfo(
                totalBytes = 0L,
                availableBytes = 0L,
                usedBytes = 0L,
                usagePercentage = 100,
                status = StorageStatus.ERROR,
                path = "Unknown"
            )
        }
    }
    
    override fun storageInfoFlow(): StateFlow<StorageInfo> {
        return storageInfo
    }
    
    override fun hasEnoughSpaceForDownload(requiredBytes: Long): Boolean {
        val currentInfo = getCurrentStorageInfo()
        val hasSpace = currentInfo.availableBytes > (requiredBytes + CRITICAL_STORAGE_THRESHOLD)
        
        logger.d(TAG, "Storage check: Required=${formatBytes(requiredBytes)}, " +
                "Available=${formatBytes(currentInfo.availableBytes)}, HasSpace=$hasSpace")
        
        return hasSpace
    }
    
    override fun getCleanupRecommendations(): List<CleanupRecommendation> {
        val recommendations = mutableListOf<CleanupRecommendation>()
        val currentInfo = getCurrentStorageInfo()
        
        if (currentInfo.status == StorageStatus.LOW || currentInfo.status == StorageStatus.CRITICAL) {
            recommendations.add(
                CleanupRecommendation(
                    type = CleanupType.CLEAR_CACHE,
                    description = "Clear app cache to free up space",
                    estimatedSpaceSaved = getCacheSize()
                )
            )
            
            recommendations.add(
                CleanupRecommendation(
                    type = CleanupType.DELETE_OLD_DOWNLOADS,
                    description = "Delete old downloaded videos",
                    estimatedSpaceSaved = getOldDownloadsSize()
                )
            )
            
            recommendations.add(
                CleanupRecommendation(
                    type = CleanupType.MOVE_TO_EXTERNAL,
                    description = "Move downloads to external storage",
                    estimatedSpaceSaved = 0L
                )
            )
        }
        
        return recommendations
    }
    
    override suspend fun performAutomaticCleanup(): CleanupResult {
        logger.i(TAG, "Performing automatic cleanup")
        
        var totalSpaceFreed = 0L
        val cleanupActions = mutableListOf<String>()
        
        try {
            val cacheSpaceFreed = cleanupCacheDirectory()
            totalSpaceFreed += cacheSpaceFreed
            if (cacheSpaceFreed > 0) {
                cleanupActions.add("Cleared ${formatBytes(cacheSpaceFreed)} from cache")
            }
            
            val tempSpaceFreed = cleanupTemporaryFiles()
            totalSpaceFreed += tempSpaceFreed
            if (tempSpaceFreed > 0) {
                cleanupActions.add("Cleared ${formatBytes(tempSpaceFreed)} from temporary files")
            }
            
            logger.i(TAG, "Automatic cleanup completed. Total space freed: ${formatBytes(totalSpaceFreed)}")
            
            return CleanupResult(
                success = true,
                spaceFreed = totalSpaceFreed,
                actions = cleanupActions,
                error = null
            )
            
        } catch (e: Exception) {
            logger.e(TAG, "Error during automatic cleanup", e)
            return CleanupResult(
                success = false,
                spaceFreed = totalSpaceFreed,
                actions = cleanupActions,
                error = e.message
            )
        }
    }
    
    override fun startMonitoring() {
        if (isMonitoring) {
            logger.d(TAG, "Storage monitoring already started")
            return
        }
        
        logger.i(TAG, "Starting storage monitoring")
        isMonitoring = true
        
        serviceScope.launch {
            while (isActive && isMonitoring) {
                try {
                    val currentInfo = getCurrentStorageInfo()
                    _storageInfo.value = currentInfo
                    
                    logger.d(TAG, "Storage info updated: ${formatBytes(currentInfo.availableBytes)} available, Status: ${currentInfo.status}")
                    
                    when (currentInfo.status) {
                        StorageStatus.LOW -> {
                            logger.w(TAG, "Low storage warning: ${formatBytes(currentInfo.availableBytes)} remaining")
                        }
                        StorageStatus.CRITICAL -> {
                            logger.e(TAG, "Critical storage warning: ${formatBytes(currentInfo.availableBytes)} remaining")
                        }
                        else -> {  }
                    }
                    
                } catch (e: Exception) {
                    logger.e(TAG, "Error during storage monitoring", e)
                }
                
                delay(MONITORING_INTERVAL_MS)
            }
        }
    }
    
    override fun stopMonitoring() {
        if (!isMonitoring) {
            logger.d(TAG, "Storage monitoring already stopped")
            return
        }
        
        logger.i(TAG, "Stopping storage monitoring")
        isMonitoring = false
    }

    private fun cleanupCacheDirectory(): Long {
        return try {
            val cacheDir = context.cacheDir
            val initialSize = getDirSize(cacheDir)
            
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".tmp")) {
                    file.delete()
                }
            }
            
            val finalSize = getDirSize(cacheDir)
            initialSize - finalSize
            
        } catch (e: Exception) {
            logger.e(TAG, "Error cleaning cache directory", e)
            0L
        }
    }

    private fun cleanupTemporaryFiles(): Long {
        return try {
            val tempDir = File(context.filesDir, "temp")
            if (!tempDir.exists()) return 0L
            
            val initialSize = getDirSize(tempDir)
            
            tempDir.listFiles()?.forEach { file ->
                if (file.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000) {
                    file.delete()
                }
            }
            
            val finalSize = getDirSize(tempDir)
            initialSize - finalSize
            
        } catch (e: Exception) {
            logger.e(TAG, "Error cleaning temporary files", e)
            0L
        }
    }

    private fun getDirSize(dir: File): Long {
        return try {
            dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        } catch (e: Exception) {
            0L
        }
    }

    private fun getCacheSize(): Long {
        return getDirSize(context.cacheDir)
    }

    private fun getOldDownloadsSize(): Long {
        return try {
            val downloadsDir = fileManagerService.getDownloadsDirectory()
            val cutoffTime = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L
            
            downloadsDir.walkTopDown()
                .filter { it.isFile && it.lastModified() < cutoffTime }
                .map { it.length() }
                .sum()
                
        } catch (e: Exception) {
            logger.e(TAG, "Error calculating old downloads size", e)
            0L
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

data class StorageInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long,
    val usagePercentage: Int,
    val status: StorageStatus,
    val path: String
)

enum class StorageStatus {
    NORMAL,
    LOW,
    CRITICAL,
    ERROR
}

data class CleanupRecommendation(
    val type: CleanupType,
    val description: String,
    val estimatedSpaceSaved: Long
)

enum class CleanupType {
    CLEAR_CACHE,
    DELETE_OLD_DOWNLOADS,
    MOVE_TO_EXTERNAL,
    DELETE_TEMP_FILES
}

data class CleanupResult(
    val success: Boolean,
    val spaceFreed: Long,
    val actions: List<String>,
    val error: String?
)