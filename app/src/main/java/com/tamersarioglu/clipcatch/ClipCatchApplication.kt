package com.tamersarioglu.clipcatch

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class ClipCatchApplication : Application() {

    companion object {
        @Volatile
        var isYoutubeDLInitialized = false
            private set

        @Volatile
        private var initializationAttempted = false
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        preloadNativeLibraries()
        initializeYoutubeDLAsync()
    }

    private fun preloadNativeLibraries() {
        try {
            arrayOf("python", "python.zip", "ssl", "crypto").forEach { libName ->
                try {
                    System.loadLibrary(libName)
                    Log.d("ClipCatchApplication", "Successfully preloaded library: $libName")
                } catch (e: UnsatisfiedLinkError) {
                    Log.d(
                        "ClipCatchApplication",
                        "Library $libName not available or already loaded"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("ClipCatchApplication", "Error during library preload", e)
        }
    }

    private fun initializeYoutubeDLAsync() {
        applicationScope.launch { initializeYoutubeDLSync() }
    }

    private fun initializeYoutubeDLSync() {
        if (initializationAttempted) {
            return
        }

        initializationAttempted = true

        try {
            Log.d("ClipCatchApplication", "Starting YouTube-DL initialization...")

            // Check if Python libraries exist in the APK
            checkPythonLibraries()

            // Initialize YouTube-DL with proper context and error handling
            val youtubeDLInstance = YoutubeDL.getInstance()
            youtubeDLInstance.init(this@ClipCatchApplication)

            // Verify initialization was successful
            verifyYoutubeDLInitialization()

            isYoutubeDLInitialized = true
            Log.i("ClipCatchApplication", "YouTube-DL initialized successfully")
        } catch (e: YoutubeDLException) {
            Log.e("ClipCatchApplication", "Failed to initialize YouTube-DL", e)
            handleYoutubeDLException(e)
            isYoutubeDLInitialized = false
        } catch (e: UnsatisfiedLinkError) {
            Log.e(
                "ClipCatchApplication",
                "Native library loading failed - missing or incompatible libraries",
                e
            )
            isYoutubeDLInitialized = false
        } catch (e: Exception) {
            Log.e("ClipCatchApplication", "Unexpected error during YouTube-DL initialization", e)
            isYoutubeDLInitialized = false
        }
    }

    fun ensureYoutubeDLInitialized(): Boolean {
        if (!isYoutubeDLInitialized && !initializationAttempted) {
            initializeYoutubeDLSync()
        }
        return isYoutubeDLInitialized
    }

    private fun checkPythonLibraries() {
        try {
            Log.d("ClipCatchApplication", "Checking Python libraries...")

            // Check native library directory
            val nativeLibraryDir = File(applicationInfo.nativeLibraryDir)
            if (nativeLibraryDir.exists()) {
                Log.d(
                    "ClipCatchApplication",
                    "Native library directory: ${nativeLibraryDir.absolutePath}"
                )
                val pythonLibs =
                    nativeLibraryDir.listFiles()?.filter {
                        it.name.contains("python") ||
                                it.name.contains("ssl") ||
                                it.name.contains("crypto")
                    }

                if (pythonLibs.isNullOrEmpty()) {
                    Log.w(
                        "ClipCatchApplication",
                        "No Python-related libraries found in native directory"
                    )
                } else {
                    pythonLibs.forEach { file ->
                        Log.d(
                            "ClipCatchApplication",
                            "Found library: ${file.name} (${file.length()} bytes)"
                        )
                    }
                }
            } else {
                Log.w("ClipCatchApplication", "Native library directory does not exist")
            }

            // Check APK structure
            try {
                val apkFile = File(applicationInfo.sourceDir)
                Log.d("ClipCatchApplication", "APK path: ${apkFile.absolutePath}")
                Log.d(
                    "ClipCatchApplication",
                    "APK exists: ${apkFile.exists()}, size: ${apkFile.length()}"
                )
            } catch (e: Exception) {
                Log.w("ClipCatchApplication", "Could not check APK structure", e)
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

            // Check if the version indicates Python compatibility issues
            if (version != null && version.contains("python", ignoreCase = true)) {
                Log.d("ClipCatchApplication", "YouTube-DL Python integration verified")
            }
        } catch (e: Exception) {
            Log.w("ClipCatchApplication", "Could not verify YouTube-DL version", e)

            // Don't fail initialization for version check issues in newer versions
            val errorMessage = e.message?.lowercase() ?: ""
            if (errorMessage.contains("python") || errorMessage.contains("version")) {
                Log.w(
                    "ClipCatchApplication",
                    "YouTube-DL version check failed but continuing initialization"
                )
                return
            }

            throw YoutubeDLException("YouTube-DL verification failed", e)
        }
    }

    private fun handleYoutubeDLException(e: YoutubeDLException) {
        val errorMessage = e.message ?: "Unknown error"
        when {
            errorMessage.contains("failed to initialize") -> {
                Log.e(
                    "ClipCatchApplication",
                    "YouTube-DL initialization failed - missing native libraries or corrupted installation"
                )
            }

            errorMessage.contains("libpython") -> {
                Log.e(
                    "ClipCatchApplication",
                    "YouTube-DL initialization failed - Python libraries missing or not accessible"
                )
            }

            errorMessage.contains("NoSuchFileException") -> {
                Log.e(
                    "ClipCatchApplication",
                    "YouTube-DL initialization failed - required files not found in APK"
                )
            }

            errorMessage.contains("ZipException") -> {
                Log.e(
                    "ClipCatchApplication",
                    "YouTube-DL initialization failed - corrupted library archives"
                )
            }

            else -> {
                Log.e(
                    "ClipCatchApplication",
                    "YouTube-DL initialization failed with error: $errorMessage"
                )
            }
        }
    }
}
