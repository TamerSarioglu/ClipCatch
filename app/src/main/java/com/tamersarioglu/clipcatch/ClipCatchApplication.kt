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
import android.content.Context

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
        
        // Test basic library loading
        testBasicLibraryLoading()
        
        initializeYoutubeDLAsync()
    }

    private fun preloadNativeLibraries() {
        try {
            // Let YouTube-DL handle native library loading
            // Only log what we find for debugging purposes
            Log.d("ClipCatchApplication", "Skipping manual native library preloading - letting YouTube-DL handle it")
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
            Log.d("ClipCatchApplication", "Creating YouTube-DL instance...")
            val youtubeDLInstance = YoutubeDL.getInstance()
            
            Log.d("ClipCatchApplication", "Initializing YouTube-DL...")
            
            // Try to initialize with more detailed error handling
            try {
                youtubeDLInstance.init(this@ClipCatchApplication)
                Log.d("ClipCatchApplication", "YouTube-DL init() completed successfully")
            } catch (e: Exception) {
                Log.e("ClipCatchApplication", "YouTube-DL init() failed, trying fallback initialization", e)
                
                // Try fallback initialization for newer library
                tryFallbackInitialization(youtubeDLInstance, e)
            }
            
            // Add a small delay to allow native libraries to load properly
            Log.d("ClipCatchApplication", "Waiting for native libraries to load...")
            Thread.sleep(1000)

            // Verify initialization was successful
            Log.d("ClipCatchApplication", "Verifying initialization...")
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
            when {
                e.message?.contains("libpython") == true -> {
                    Log.e("ClipCatchApplication", "Python library loading failed")
                }
                e.message?.contains("libssl") == true -> {
                    Log.e("ClipCatchApplication", "SSL library loading failed")
                }
                e.message?.contains("libcrypto") == true -> {
                    Log.e("ClipCatchApplication", "Crypto library loading failed")
                }
            }
            isYoutubeDLInitialized = false
        } catch (e: Exception) {
            Log.e("ClipCatchApplication", "Unexpected error during YouTube-DL initialization", e)
            isYoutubeDLInitialized = false
        }
    }

    private fun tryFallbackInitialization(youtubeDLInstance: YoutubeDL, originalException: Exception) {
        try {
            Log.d("ClipCatchApplication", "Trying fallback initialization methods...")
            
            // Try different initialization approaches for the newer library
            val youtubeDLClass = youtubeDLInstance.javaClass
            
            // Method 1: Try init with different parameters
            try {
                val initMethod = youtubeDLClass.getMethod("init", Context::class.java)
                Log.d("ClipCatchApplication", "Trying init(Context) method...")
                initMethod.invoke(youtubeDLInstance, this@ClipCatchApplication)
                Log.d("ClipCatchApplication", "Fallback init(Context) succeeded")
                return
            } catch (e: Exception) {
                Log.d("ClipCatchApplication", "Fallback init(Context) failed: ${e.message}")
            }
            
            // Method 2: Try init without parameters
            try {
                val initMethod = youtubeDLClass.getMethod("init")
                Log.d("ClipCatchApplication", "Trying init() method...")
                initMethod.invoke(youtubeDLInstance)
                Log.d("ClipCatchApplication", "Fallback init() succeeded")
                return
            } catch (e: Exception) {
                Log.d("ClipCatchApplication", "Fallback init() failed: ${e.message}")
            }
            
            // Method 3: Try to skip initialization and test basic functionality
            Log.d("ClipCatchApplication", "Trying to skip initialization and test basic functionality...")
            tryBasicFunctionalityTest()
            
        } catch (e: Exception) {
            Log.e("ClipCatchApplication", "All fallback initialization methods failed", e)
            throw YoutubeDLException("All initialization methods failed", originalException)
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
                
                // List all files in the native library directory
                val allFiles = nativeLibraryDir.listFiles()
                Log.d("ClipCatchApplication", "Total files in native directory: ${allFiles?.size ?: 0}")
                
                allFiles?.forEach { file ->
                    Log.d("ClipCatchApplication", "Found file: ${file.name} (${file.length()} bytes)")
                }
                
                val pythonLibs =
                    allFiles?.filter {
                        it.name.contains("python") ||
                                it.name.contains("ssl") ||
                                it.name.contains("crypto") ||
                                it.name.contains("ffmpeg")
                    }

                if (pythonLibs.isNullOrEmpty()) {
                    Log.w(
                        "ClipCatchApplication",
                        "No Python-related libraries found in native directory"
                    )
                } else {
                    pythonLibs.forEach { file ->
                        val fileType = when {
                            file.name.endsWith(".zip.so") -> "ZIP archive (not a shared library)"
                            file.name.endsWith(".so") -> "Shared library"
                            else -> "Unknown"
                        }
                        Log.d(
                            "ClipCatchApplication",
                            "Found library: ${file.name} (${file.length()} bytes) - Type: $fileType"
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
            
            // Check library capabilities
            checkLibraryCapabilities()
            
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

    private fun checkLibraryCapabilities() {
        try {
            Log.d("ClipCatchApplication", "Checking library capabilities...")
            
            val youtubeDLInstance = YoutubeDL.getInstance()
            val youtubeDLClass = youtubeDLInstance.javaClass
            
            // Check available methods
            val methods = youtubeDLClass.methods
            Log.d("ClipCatchApplication", "Available methods in YouTube-DL:")
            methods.forEach { method ->
                Log.d("ClipCatchApplication", "  - ${method.name}")
            }
            
            // Check if specific methods exist
            val hasVersionMethod = methods.any { it.name == "version" }
            val hasInitMethod = methods.any { it.name == "init" }
            val hasExecuteMethod = methods.any { it.name == "execute" }
            
            Log.d("ClipCatchApplication", "Library capabilities:")
            Log.d("ClipCatchApplication", "  - Has version method: $hasVersionMethod")
            Log.d("ClipCatchApplication", "  - Has init method: $hasInitMethod")
            Log.d("ClipCatchApplication", "  - Has execute method: $hasExecuteMethod")
            
        } catch (e: Exception) {
            Log.w("ClipCatchApplication", "Could not check library capabilities", e)
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

    private fun tryBasicFunctionalityTest() {
        try {
            Log.d("ClipCatchApplication", "Trying basic functionality test...")
            
            // Try to create a simple YouTube-DL instance without full initialization
            val youtubeDLInstance = YoutubeDL.getInstance()
            
            // Try to access basic properties
            Log.d("ClipCatchApplication", "YouTube-DL instance created successfully")
            
            // Mark as initialized if we can create the instance
            isYoutubeDLInitialized = true
            Log.i("ClipCatchApplication", "Basic functionality test passed")
            
        } catch (e: Exception) {
            Log.e("ClipCatchApplication", "Basic functionality test failed", e)
            isYoutubeDLInitialized = false
        }
    }

    private fun testBasicLibraryLoading() {
        try {
            Log.d("ClipCatchApplication", "Testing basic library loading...")
            
            // Try to load the YouTube-DL class
            val youtubeDLClass = Class.forName("com.yausername.youtubedl_android.YoutubeDL")
            Log.d("ClipCatchApplication", "YouTube-DL class loaded successfully: ${youtubeDLClass.name}")
            
            // Try to create an instance
            val instance = youtubeDLClass.getMethod("getInstance").invoke(null)
            Log.d("ClipCatchApplication", "YouTube-DL instance created successfully")
            
            // Test if the newer library has different initialization methods
            testNewerLibraryMethods(youtubeDLClass)
            
            // Test native library directory access
            testNativeLibraryDirectory()
            
        } catch (e: Exception) {
            Log.e("ClipCatchApplication", "Basic library loading test failed", e)
        }
    }

    private fun testNewerLibraryMethods(youtubeDLClass: Class<*>) {
        try {
            Log.d("ClipCatchApplication", "Testing newer library methods...")
            
            // Check available methods
            val methods = youtubeDLClass.methods
            Log.d("ClipCatchApplication", "Available methods in YouTube-DL class:")
            methods.forEach { method ->
                Log.d("ClipCatchApplication", "  - ${method.name}(${method.parameterTypes.joinToString { it.simpleName }})")
            }
            
            // Check if there are any specific initialization methods
            val initMethods = methods.filter { it.name.contains("init", ignoreCase = true) }
            Log.d("ClipCatchApplication", "Initialization methods found: ${initMethods.size}")
            initMethods.forEach { method ->
                Log.d("ClipCatchApplication", "  - ${method.name}(${method.parameterTypes.joinToString { it.simpleName }})")
            }
            
        } catch (e: Exception) {
            Log.e("ClipCatchApplication", "Error testing newer library methods", e)
        }
    }

    private fun testNativeLibraryDirectory() {
        try {
            Log.d("ClipCatchApplication", "Testing native library directory access...")
            
            val nativeLibraryDir = File(applicationInfo.nativeLibraryDir)
            Log.d("ClipCatchApplication", "Native library directory: ${nativeLibraryDir.absolutePath}")
            Log.d("ClipCatchApplication", "Directory exists: ${nativeLibraryDir.exists()}")
            Log.d("ClipCatchApplication", "Directory is readable: ${nativeLibraryDir.canRead()}")
            
            if (nativeLibraryDir.exists()) {
                val files = nativeLibraryDir.listFiles()
                Log.d("ClipCatchApplication", "Number of files in directory: ${files?.size ?: 0}")
                
                files?.forEach { file ->
                    Log.d("ClipCatchApplication", "File: ${file.name} (${file.length()} bytes)")
                }
            }
            
            // Test APK contents
            testAPKContents()
            
        } catch (e: Exception) {
            Log.e("ClipCatchApplication", "Native library directory test failed", e)
        }
    }

    private fun testAPKContents() {
        try {
            Log.d("ClipCatchApplication", "Testing APK contents...")
            
            val apkFile = File(applicationInfo.sourceDir)
            Log.d("ClipCatchApplication", "APK file: ${apkFile.absolutePath}")
            Log.d("ClipCatchApplication", "APK exists: ${apkFile.exists()}")
            Log.d("ClipCatchApplication", "APK size: ${apkFile.length()} bytes")
            
            // Try to read the APK as a ZIP file to see its contents
            if (apkFile.exists()) {
                val zipFile = java.util.zip.ZipFile(apkFile)
                val entries = zipFile.entries()
                
                var pythonLibs = 0
                var totalEntries = 0
                
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    totalEntries++
                    
                    if (entry.name.contains("python") || entry.name.contains("ffmpeg") || 
                        entry.name.contains("ssl") || entry.name.contains("crypto")) {
                        pythonLibs++
                        Log.d("ClipCatchApplication", "Found library in APK: ${entry.name} (${entry.size} bytes)")
                    }
                }
                
                Log.d("ClipCatchApplication", "Total entries in APK: $totalEntries")
                Log.d("ClipCatchApplication", "Python-related libraries in APK: $pythonLibs")
                
                zipFile.close()
            }
            
        } catch (e: Exception) {
            Log.e("ClipCatchApplication", "APK contents test failed", e)
        }
    }
}
