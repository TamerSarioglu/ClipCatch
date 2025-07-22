package com.tamersarioglu.clipcatch.data.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for handling app permissions
 */
@Singleton
class PermissionUtils @Inject constructor() {
    
    /**
     * Get required permissions based on Android version
     */
    fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10+ uses scoped storage, no explicit permissions needed for Downloads folder
                emptyArray()
            }
            else -> {
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
        }
    }
    
    /**
     * Check if all required permissions are granted
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        val requiredPermissions = getRequiredPermissions()
        if (requiredPermissions.isEmpty()) {
            return true
        }
        
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Check if a specific permission is granted
     */
    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Get permissions that are not yet granted
     */
    fun getMissingPermissions(context: Context): Array<String> {
        val requiredPermissions = getRequiredPermissions()
        return requiredPermissions.filter { permission ->
            !hasPermission(context, permission)
        }.toTypedArray()
    }
    
    /**
     * Get user-friendly explanation for why permissions are needed
     */
    fun getPermissionExplanation(permission: String): String {
        return when (permission) {
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> 
                "Storage permission is needed to save downloaded videos to your device."
            Manifest.permission.READ_EXTERNAL_STORAGE -> 
                "Storage permission is needed to access and organize your downloaded videos."
            Manifest.permission.READ_MEDIA_VIDEO -> 
                "Media permission is needed to save and access downloaded videos."
            Manifest.permission.READ_MEDIA_AUDIO -> 
                "Media permission is needed to save and access downloaded audio content."
            else -> "This permission is required for the app to function properly."
        }
    }
    
    /**
     * Get general explanation for all required permissions
     */
    fun getGeneralPermissionExplanation(): String {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                "ClipCatch needs media permissions to save downloaded videos to your device. " +
                "These permissions allow the app to organize your downloads in the appropriate folders."
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                "ClipCatch uses scoped storage to save your downloads safely. " +
                "No additional permissions are required on your Android version."
            }
            else -> {
                "ClipCatch needs storage permissions to save downloaded videos to your device. " +
                "These permissions allow the app to create and manage video files in your Downloads folder."
            }
        }
    }
    
    /**
     * Check if we should show rationale for permission request
     */
    fun shouldShowRationale(context: Context, permission: String): Boolean {
        return if (context is androidx.activity.ComponentActivity) {
            androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(context, permission)
        } else {
            false
        }
    }
    
    /**
     * Get critical permissions that are absolutely required for core functionality
     */
    fun getCriticalPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                emptyArray()
            }
            else -> {
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
    
    /**
     * Check if critical permissions are granted
     */
    fun hasCriticalPermissions(context: Context): Boolean {
        val criticalPermissions = getCriticalPermissions()
        if (criticalPermissions.isEmpty()) {
            return true
        }
        
        return criticalPermissions.all { permission ->
            hasPermission(context, permission)
        }
    }
}