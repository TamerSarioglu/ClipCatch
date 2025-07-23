package com.tamersarioglu.clipcatch

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

@HiltAndroidApp
class ClipCatchApplication : Application() {
    
    companion object {
        @Volatile
        var isYoutubeDLInitialized = false
            private set
    }
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        preloadNativeLibraries()
        initializeYoutubeDLAsync()
    }
    
    private fun preloadNativeLibraries() {
        try {
            // Preload critical libraries if available
            arrayOf("python", "python.zip", "ssl", "crypto").forEach { libName ->
                try {
                    System.loadLibrary(libName)
                    Log.d("ClipCatchApplication", "Successfully preloaded library: $libName")
                } catch (e: UnsatisfiedLinkError) {
                    Log.d("ClipCatchApplication", "Library $libName not available or already loaded")
                }
            }
        } catch (e: Exception) {
            Log.w("ClipCatchApplication", "Error during library preload", e)
        }
    }
    
    private fun initializeYoutubeDLAsync() {
        applicationScope.launch {
            try {
                Log.d("ClipCatchApplication", "Starting YouTube-DL initialization...")
                
                // Check if Python libraries exist in the APK
                checkPythonLibraries()
                
                // Initialize YouTube-DL with proper context
                YoutubeDL.getInstance().init(this@ClipCatchApplication)
                
                // Verify initialization was successful
                verifyYoutubeDLInitialization()
                
                isYoutubeDLInitialized = true
                Log.d("ClipCatchApplication", "YouTube-DL initialized successfully")
                
            } catch (e: YoutubeDLException) {
                Log.e("ClipCatchApplication", "Failed to initialize YouTube-DL", e)
                handleYoutubeDLException(e)
                isYoutubeDLInitialized = false
                
            } catch (e: Exception) {
                Log.e("ClipCatchApplication", "Unexpected error during YouTube-DL initialization", e)
                isYoutubeDLInitialized = false
            }
        }
    }
    
    private fun checkPythonLibraries() {
        try {
            val libraryPaths = listOf(
                "lib/arm64-v8a/libpython.zip.so",
                "lib/armeabi-v7a/libpython.zip.so"
            )
            
            val filesDir = File(filesDir, "lib")
            if (filesDir.exists()) {
                Log.d("ClipCatchApplication", "Files directory exists: ${filesDir.absolutePath}")
                filesDir.listFiles()?.forEach { file ->
                    Log.d("ClipCatchApplication", "Found file: ${file.name}")
                }
            }
            
            // Check if native libraries are accessible
            val nativeLibraryDir = File(applicationInfo.nativeLibraryDir)
            if (nativeLibraryDir.exists()) {
                Log.d("ClipCatchApplication", "Native library directory: ${nativeLibraryDir.absolutePath}")
                nativeLibraryDir.listFiles()?.forEach { file ->
                    if (file.name.contains("python")) {
                        Log.d("ClipCatchApplication", "Found Python library: ${file.name}")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.w("ClipCatchApplication", "Error checking Python libraries", e)
        }
    }
    
    private fun verifyYoutubeDLInitialization() {
        try {
            // Try to get YouTube-DL version to verify it's working
            val version = YoutubeDL.getInstance().version(this)
            Log.d("ClipCatchApplication", "YouTube-DL version: $version")
        } catch (e: Exception) {
            Log.w("ClipCatchApplication", "Could not verify YouTube-DL version", e)
            throw YoutubeDLException("YouTube-DL verification failed", e)
        }
    }
    
    private fun handleYoutubeDLException(e: YoutubeDLException) {
        val errorMessage = e.message ?: "Unknown error"
        when {
            errorMessage.contains("failed to initialize") -> {
                Log.e("ClipCatchApplication", "YouTube-DL initialization failed - missing native libraries or corrupted installation")
            }
            errorMessage.contains("libpython") -> {
                Log.e("ClipCatchApplication", "YouTube-DL initialization failed - Python libraries missing or not accessible")
            }
            errorMessage.contains("NoSuchFileException") -> {
                Log.e("ClipCatchApplication", "YouTube-DL initialization failed - required files not found in APK")
            }
            errorMessage.contains("ZipException") -> {
                Log.e("ClipCatchApplication", "YouTube-DL initialization failed - corrupted library archives")
            }
            else -> {
                Log.e("ClipCatchApplication", "YouTube-DL initialization failed with error: $errorMessage")
            }
        }
    }
}