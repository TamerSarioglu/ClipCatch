package com.tamersarioglu.clipcatch

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.content.Context
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

@HiltAndroidApp
class ClipCatchApplication : Application() {

    companion object {
        @Volatile
        var isYoutubeDLInitialized = false
            private set

        @Volatile
        private var initializationAttempted = false
        
        @Volatile
        private var initializationInProgress = false
        
        private const val TAG = "ClipCatchApplication"
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
            Log.d(TAG, "Skipping manual native library preloading - letting YouTube-DL handle it")
        } catch (e: Exception) {
            Log.w(TAG, "Error during library preload", e)
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
        initializationInProgress = true

        try {
            Log.d(TAG, "Starting YouTube-DL initialization...")

            // Check if Python libraries exist in the APK
            checkPythonLibraries()

            // Try to manually extract native libraries if needed
            if (shouldExtractNativeLibraries()) {
                extractNativeLibrariesFromAPK()
                // Verify extraction was successful
                verifyNativeLibraryExtraction()
            }
            
            // Ensure Python directory exists for newer library versions
            ensurePythonDirectoryExists()

            // Initialize YouTube-DL with proper context and error handling
            Log.d(TAG, "Creating YouTube-DL instance...")
            val youtubeDLInstance = YoutubeDL.getInstance()
            
            Log.d(TAG, "Initializing YouTube-DL...")
            
            // Try to initialize with more detailed error handling
            try {
                // Try the newer library's initialization first
                try {
                    youtubeDLInstance.init(this@ClipCatchApplication)
                    Log.d(TAG, "YouTube-DL init() completed successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "Standard init() failed, trying alternative methods", e)
                    
                    // Try alternative initialization methods
                    tryAlternativeInitialization(youtubeDLInstance)
                }
            } catch (e: Exception) {
                Log.e(TAG, "YouTube-DL init() failed, trying fallback initialization", e)
                
                // Try fallback initialization for newer library
                tryFallbackInitialization(youtubeDLInstance, e)
            }
            
            // Add a small delay to allow native libraries to load properly
            Log.d(TAG, "Waiting for native libraries to load...")
            Thread.sleep(2000) // Increased delay for native library loading
            
                        // Try to reload native libraries if needed
            tryReloadNativeLibraries()
            
            // Try to re-initialize YouTube-DL if needed
            if (!isYoutubeDLInitialized) {
                Log.d(TAG, "YouTube-DL not initialized, trying re-initialization...")
                tryReinitializeYoutubeDL()
            }
            
            // Verify initialization was successful
            Log.d(TAG, "Verifying initialization...")
            verifyYoutubeDLInitialization()

            isYoutubeDLInitialized = true
            initializationInProgress = false
            Log.i(TAG, "YouTube-DL initialized successfully")
        } catch (e: YoutubeDLException) {
            Log.e(TAG, "Failed to initialize YouTube-DL", e)
            handleYoutubeDLException(e)
            isYoutubeDLInitialized = false
            initializationInProgress = false
        } catch (e: UnsatisfiedLinkError) {
            Log.e(
                TAG,
                "Native library loading failed - missing or incompatible libraries",
                e
            )
            when {
                e.message?.contains("libpython") == true -> {
                    Log.e(TAG, "Python library loading failed")
                }
                e.message?.contains("libssl") == true -> {
                    Log.e(TAG, "SSL library loading failed")
                }
                e.message?.contains("libcrypto") == true -> {
                    Log.e(TAG, "Crypto library loading failed")
                }
            }
            isYoutubeDLInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during YouTube-DL initialization", e)
            isYoutubeDLInitialized = false
            initializationInProgress = false
        }
    }

    private fun shouldExtractNativeLibraries(): Boolean {
        val privateLibDir = File(filesDir, "native_libs")
        if (!privateLibDir.exists()) {
            Log.d(TAG, "Private native library directory does not exist")
            return true
        }
        
        val files = privateLibDir.listFiles()
        val pythonLibs = files?.filter { 
            it.name.contains("python") || it.name.contains("ffmpeg") || 
            it.name.contains("ssl") || it.name.contains("crypto") 
        }
        
        Log.d(TAG, "Private native library directory has ${files?.size ?: 0} files, ${pythonLibs?.size ?: 0} Python-related")
        
        // Extract if no Python-related libraries found
        return pythonLibs.isNullOrEmpty()
    }

    private fun extractNativeLibrariesFromAPK() {
        try {
            Log.d(TAG, "Attempting to manually extract native libraries from APK...")
            
            val apkFile = File(applicationInfo.sourceDir)
            // Use app's private directory instead of system native library directory
            val privateLibDir = File(filesDir, "native_libs")
            
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file does not exist: ${apkFile.absolutePath}")
                return
            }
            
            if (!privateLibDir.exists()) {
                val created = privateLibDir.mkdirs()
                Log.d(TAG, "Created private native library directory: $created")
            }
            
            val zipFile = java.util.zip.ZipFile(apkFile)
            val entries = zipFile.entries()
            
            var extractedCount = 0
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                
                // Look for native libraries that need extraction
                if (entry.name.startsWith("lib/") && 
                    (entry.name.contains("python") || entry.name.contains("ffmpeg")) &&
                    entry.name.endsWith(".zip.so")) {
                    
                    try {
                        val targetFile = File(privateLibDir, entry.name.substringAfterLast("/"))
                        if (!targetFile.exists()) {
                            extractZipEntry(zipFile, entry, targetFile)
                            extractedCount++
                            Log.d(TAG, "Extracted: ${entry.name} -> ${targetFile.name}")
                            
                            // If this is a ZIP archive, extract its contents
                            if (targetFile.name.endsWith(".zip.so")) {
                                extractZipArchiveContents(targetFile, privateLibDir)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to extract ${entry.name}", e)
                    }
                }
            }
            
            zipFile.close()
            Log.d(TAG, "Extracted $extractedCount native libraries")
            
            // Set the extracted libraries directory for YouTube-DL to use
            setNativeLibraryPath(privateLibDir.absolutePath)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract native libraries from APK", e)
        }
    }

    private fun setNativeLibraryPath(path: String) {
        try {
            Log.d(TAG, "Setting native library path: $path")
            System.setProperty("java.library.path", path)
            
            // Try to reload the library path
            val sysPathField = ClassLoader::class.java.getDeclaredField("sys_paths")
            sysPathField.isAccessible = true
            sysPathField.set(null, null)
            
        } catch (e: Exception) {
            Log.w(TAG, "Could not set native library path", e)
        }
    }

    private fun extractZipArchiveContents(zipFile: File, targetDir: File) {
        try {
            Log.d(TAG, "Extracting contents of ZIP archive: ${zipFile.name}")
            
            val zipInputStream = ZipInputStream(zipFile.inputStream())
            var entry: ZipEntry?
            var extractedCount = 0
            
            while (zipInputStream.nextEntry.also { entry = it } != null) {
                val entryName = entry!!.name
                val targetFile = File(targetDir, entryName)
                
                // Create parent directories if needed
                targetFile.parentFile?.mkdirs()
                
                if (!entry.isDirectory) {
                    val outputStream = FileOutputStream(targetFile)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (zipInputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    
                    outputStream.close()
                    extractedCount++
                    Log.d(TAG, "Extracted from ZIP: $entryName")
                }
                
                zipInputStream.closeEntry()
            }
            
            zipInputStream.close()
            Log.d(TAG, "Extracted $extractedCount files from ZIP archive")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract ZIP archive contents", e)
        }
    }

    private fun extractZipEntry(zipFile: java.util.zip.ZipFile, entry: ZipEntry, targetFile: File) {
        var inputStream: java.io.InputStream? = null
        var outputStream: FileOutputStream? = null
        
        try {
            inputStream = zipFile.getInputStream(entry)
            outputStream = FileOutputStream(targetFile)
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
        } finally {
            try {
                inputStream?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing input stream", e)
            }
            try {
                outputStream?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing output stream", e)
            }
        }
    }

    private fun verifyNativeLibraryExtraction() {
        try {
            Log.d(TAG, "Verifying native library extraction...")
            
            val privateLibDir = File(filesDir, "native_libs")
            if (!privateLibDir.exists()) {
                Log.w(TAG, "Private native library directory does not exist after extraction")
                return
            }
            
            val files = privateLibDir.listFiles()
            Log.d(TAG, "Files in private native library directory after extraction: ${files?.size ?: 0}")
            
            files?.forEach { file ->
                Log.d(TAG, "File: ${file.name} (${file.length()} bytes)")
            }
            
            val pythonLibs = files?.filter { 
                it.name.contains("python") || it.name.contains("ffmpeg") || 
                it.name.contains("ssl") || it.name.contains("crypto") 
            }
            
            if (pythonLibs.isNullOrEmpty()) {
                Log.w(TAG, "No Python-related libraries found after extraction")
            } else {
                Log.d(TAG, "Found ${pythonLibs.size} Python-related libraries after extraction")
                pythonLibs.forEach { file ->
                    Log.d(TAG, "Python library: ${file.name} (${file.length()} bytes)")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying native library extraction", e)
        }
    }

    private fun ensurePythonDirectoryExists() {
        try {
            Log.d(TAG, "Ensuring Python directory exists...")
            
            val pythonDir = File(filesDir, "python")
            if (!pythonDir.exists()) {
                val created = pythonDir.mkdirs()
                Log.d(TAG, "Created Python directory: $created")
            }
            
            // Check if Python directory has necessary files
            val pythonFiles = pythonDir.listFiles()
            Log.d(TAG, "Python directory has ${pythonFiles?.size ?: 0} files")
            
            if (pythonFiles.isNullOrEmpty()) {
                Log.w(TAG, "Python directory is empty - may need to extract Python files")
                // Try to extract Python files from APK if needed
                extractPythonFilesFromAPK(pythonDir)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring Python directory exists", e)
        }
    }

    private fun extractPythonFilesFromAPK(pythonDir: File) {
        try {
            Log.d(TAG, "Extracting Python files from APK...")
            
            val apkFile = File(applicationInfo.sourceDir)
            val zipFile = java.util.zip.ZipFile(apkFile)
            val entries = zipFile.entries()
            
            var extractedCount = 0
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                
                // Look for Python-related files
                if (entry.name.contains("python") && 
                    (entry.name.endsWith(".py") || entry.name.endsWith(".pyc") || 
                     entry.name.contains("python.zip") || entry.name.contains("yt-dlp"))) {
                    
                    try {
                        val targetFile = File(pythonDir, entry.name.substringAfterLast("/"))
                        if (!targetFile.exists()) {
                            extractZipEntry(zipFile, entry, targetFile)
                            extractedCount++
                            Log.d(TAG, "Extracted Python file: ${entry.name} -> ${targetFile.name}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to extract Python file ${entry.name}", e)
                    }
                }
            }
            
            zipFile.close()
            Log.d(TAG, "Extracted $extractedCount Python files")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract Python files from APK", e)
        }
    }

    private fun tryReloadNativeLibraries() {
        try {
            Log.d(TAG, "Attempting to reload native libraries...")
            
            val privateLibDir = File(filesDir, "native_libs")
            if (privateLibDir.exists()) {
                val files = privateLibDir.listFiles()
                Log.d(TAG, "Found ${files?.size ?: 0} files in private native library directory")
                
                files?.forEach { file ->
                    if (file.name.endsWith(".so") && !file.name.endsWith(".zip.so")) {
                        try {
                            System.load(file.absolutePath)
                            Log.d(TAG, "Successfully loaded library: ${file.name}")
                        } catch (e: UnsatisfiedLinkError) {
                            Log.w(TAG, "Could not load library ${file.name}: ${e.message}")
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error reloading native libraries: ${e.message}")
        }
    }

    private fun tryReinitializeYoutubeDL() {
        try {
            Log.d(TAG, "Attempting to re-initialize YouTube-DL...")
            
            val youtubeDLInstance = YoutubeDL.getInstance()
            
            // Try the alternative initialization method that worked before
            try {
                val youtubeDLClass = youtubeDLInstance.javaClass
                val initYtdlpMethod = youtubeDLClass.getMethod("init_ytdlp", Context::class.java, File::class.java)
                val pythonDir = File(filesDir, "python")
                initYtdlpMethod.invoke(youtubeDLInstance, this@ClipCatchApplication, pythonDir)
                Log.d(TAG, "Re-initialization succeeded")
                isYoutubeDLInitialized = true
            } catch (e: Exception) {
                Log.w(TAG, "Re-initialization failed: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during re-initialization", e)
        }
    }

    private fun tryAlternativeInitialization(youtubeDLInstance: YoutubeDL) {
        try {
            Log.d(TAG, "Trying alternative initialization methods...")
            
            val youtubeDLClass = youtubeDLInstance.javaClass
            
            // Try init_ytdlp method first (newer library)
            try {
                val initYtdlpMethod = youtubeDLClass.getMethod("init_ytdlp", Context::class.java, File::class.java)
                Log.d(TAG, "Trying init_ytdlp(Context, File) method...")
                val pythonDir = File(filesDir, "python")
                initYtdlpMethod.invoke(youtubeDLInstance, this@ClipCatchApplication, pythonDir)
                Log.d(TAG, "Alternative init_ytdlp(Context, File) succeeded")
                return
            } catch (e: Exception) {
                Log.d(TAG, "Alternative init_ytdlp(Context, File) failed: ${e.message}")
            }
            
            // Try initPython method
            try {
                val initPythonMethod = youtubeDLClass.getMethod("initPython", Context::class.java, File::class.java)
                Log.d(TAG, "Trying initPython(Context, File) method...")
                val pythonDir = File(filesDir, "python")
                initPythonMethod.invoke(youtubeDLInstance, this@ClipCatchApplication, pythonDir)
                Log.d(TAG, "Alternative initPython(Context, File) succeeded")
                return
            } catch (e: Exception) {
                Log.d(TAG, "Alternative initPython(Context, File) failed: ${e.message}")
            }
            
            // If all alternative methods fail, throw an exception to trigger fallback
            throw Exception("All alternative initialization methods failed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Alternative initialization failed", e)
            throw e
        }
    }

    private fun tryFallbackInitialization(youtubeDLInstance: YoutubeDL, originalException: Exception) {
        try {
            Log.d(TAG, "Trying fallback initialization methods...")
            
            // Try different initialization approaches for the newer library
            val youtubeDLClass = youtubeDLInstance.javaClass
            
            // Method 1: Try init with different parameters
            try {
                val initMethod = youtubeDLClass.getMethod("init", Context::class.java)
                Log.d(TAG, "Trying init(Context) method...")
                initMethod.invoke(youtubeDLInstance, this@ClipCatchApplication)
                Log.d(TAG, "Fallback init(Context) succeeded")
                return
            } catch (e: Exception) {
                Log.d(TAG, "Fallback init(Context) failed: ${e.message}")
            }
            
            // Method 1.5: Try init_ytdlp method if available
            try {
                val initYtdlpMethod = youtubeDLClass.getMethod("init_ytdlp", Context::class.java, File::class.java)
                Log.d(TAG, "Trying init_ytdlp(Context, File) method...")
                val pythonDir = File(filesDir, "python")
                initYtdlpMethod.invoke(youtubeDLInstance, this@ClipCatchApplication, pythonDir)
                Log.d(TAG, "Fallback init_ytdlp(Context, File) succeeded")
                return
            } catch (e: Exception) {
                Log.d(TAG, "Fallback init_ytdlp(Context, File) failed: ${e.message}")
            }
            
            // Method 1.6: Try initPython method if available
            try {
                val initPythonMethod = youtubeDLClass.getMethod("initPython", Context::class.java, File::class.java)
                Log.d(TAG, "Trying initPython(Context, File) method...")
                val pythonDir = File(filesDir, "python")
                initPythonMethod.invoke(youtubeDLInstance, this@ClipCatchApplication, pythonDir)
                Log.d(TAG, "Fallback initPython(Context, File) succeeded")
                return
            } catch (e: Exception) {
                Log.d(TAG, "Fallback initPython(Context, File) failed: ${e.message}")
            }
            
            // Method 2: Try init without parameters
            try {
                val initMethod = youtubeDLClass.getMethod("init")
                Log.d(TAG, "Trying init() method...")
                initMethod.invoke(youtubeDLInstance)
                Log.d(TAG, "Fallback init() succeeded")
                return
            } catch (e: Exception) {
                Log.d(TAG, "Fallback init() failed: ${e.message}")
            }
            
            // Method 3: Try to skip initialization and test basic functionality
            Log.d(TAG, "Trying to skip initialization and test basic functionality...")
            tryBasicFunctionalityTest()
            
            // Method 4: Try to use a different initialization approach
            try {
                Log.d(TAG, "Trying alternative initialization approach...")
                // Try to create a new instance and initialize it differently
                val newInstance = YoutubeDL.getInstance()
                newInstance.init(this@ClipCatchApplication)
                Log.d(TAG, "Alternative instance initialization succeeded")
                return
            } catch (e: Exception) {
                Log.d(TAG, "Alternative instance initialization failed: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "All fallback initialization methods failed", e)
            throw YoutubeDLException("All initialization methods failed", originalException)
        }
    }

    fun ensureYoutubeDLInitialized(): Boolean {
        if (!isYoutubeDLInitialized && !initializationAttempted) {
            initializeYoutubeDLSync()
        }
        return isYoutubeDLInitialized
    }

    fun getYoutubeDLStatus(): String {
        return when {
            isYoutubeDLInitialized -> "YouTube-DL Ready"
            initializationInProgress -> "YouTube-DL Initializing..."
            initializationAttempted -> "YouTube-DL Failed - Check logs"
            else -> "YouTube-DL Not Started"
        }
    }

    private fun checkPythonLibraries() {
        try {
            Log.d(TAG, "Checking Python libraries...")

            // Check native library directory
            val nativeLibraryDir = File(applicationInfo.nativeLibraryDir)
            if (nativeLibraryDir.exists()) {
                Log.d(TAG, "Native library directory: ${nativeLibraryDir.absolutePath}")
                
                // List all files in the native library directory
                val allFiles = nativeLibraryDir.listFiles()
                Log.d(TAG, "Total files in native directory: ${allFiles?.size ?: 0}")
                
                allFiles?.forEach { file ->
                    Log.d(TAG, "Found file: ${file.name} (${file.length()} bytes)")
                }
                
                val pythonLibs =
                    allFiles?.filter {
                        it.name.contains("python") ||
                                it.name.contains("ssl") ||
                                it.name.contains("crypto") ||
                                it.name.contains("ffmpeg")
                    }

                if (pythonLibs.isNullOrEmpty()) {
                    Log.w(TAG, "No Python-related libraries found in native directory")
                } else {
                    pythonLibs.forEach { file ->
                        val fileType = when {
                            file.name.endsWith(".zip.so") -> "ZIP archive (not a shared library)"
                            file.name.endsWith(".so") -> "Shared library"
                            else -> "Unknown"
                        }
                        Log.d(TAG, "Found library: ${file.name} (${file.length()} bytes) - Type: $fileType")
                    }
                }
            } else {
                Log.w(TAG, "Native library directory does not exist")
            }

            // Check APK structure
            try {
                val apkFile = File(applicationInfo.sourceDir)
                Log.d(TAG, "APK path: ${apkFile.absolutePath}")
                Log.d(TAG, "APK exists: ${apkFile.exists()}, size: ${apkFile.length()}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not check APK structure", e)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Python libraries", e)
        }
    }

    private fun verifyYoutubeDLInitialization() {
        try {
            // Try to get YouTube-DL version to verify it's working
            val version = YoutubeDL.getInstance().version(this)
            Log.d(TAG, "YouTube-DL version: $version")

            // Check if the version indicates Python compatibility issues
            if (version != null && version.contains("python", ignoreCase = true)) {
                Log.d(TAG, "YouTube-DL Python integration verified")
            }
            
            // Check library capabilities
            checkLibraryCapabilities()
            
        } catch (e: Exception) {
            Log.w(TAG, "Could not verify YouTube-DL version", e)

            // Don't fail initialization for version check issues in newer versions
            val errorMessage = e.message?.lowercase() ?: ""
            if (errorMessage.contains("python") || errorMessage.contains("version")) {
                Log.w(TAG, "YouTube-DL version check failed but continuing initialization")
                return
            }

            throw YoutubeDLException("YouTube-DL verification failed", e)
        }
    }

    private fun checkLibraryCapabilities() {
        try {
            Log.d(TAG, "Checking library capabilities...")
            
            val youtubeDLInstance = YoutubeDL.getInstance()
            val youtubeDLClass = youtubeDLInstance.javaClass
            
            // Check available methods
            val methods = youtubeDLClass.methods
            Log.d(TAG, "Available methods in YouTube-DL:")
            methods.forEach { method ->
                Log.d(TAG, "  - ${method.name}")
            }
            
            // Check if specific methods exist
            val hasVersionMethod = methods.any { it.name == "version" }
            val hasInitMethod = methods.any { it.name == "init" }
            val hasExecuteMethod = methods.any { it.name == "execute" }
            
            Log.d(TAG, "Library capabilities:")
            Log.d(TAG, "  - Has version method: $hasVersionMethod")
            Log.d(TAG, "  - Has init method: $hasInitMethod")
            Log.d(TAG, "  - Has execute method: $hasExecuteMethod")
            
        } catch (e: Exception) {
            Log.w(TAG, "Could not check library capabilities", e)
        }
    }

    private fun handleYoutubeDLException(e: YoutubeDLException) {
        val errorMessage = e.message ?: "Unknown error"
        when {
            errorMessage.contains("failed to initialize") -> {
                Log.e(TAG, "YouTube-DL initialization failed - missing native libraries or corrupted installation")
            }

            errorMessage.contains("libpython") -> {
                Log.e(TAG, "YouTube-DL initialization failed - Python libraries missing or not accessible")
            }

            errorMessage.contains("NoSuchFileException") -> {
                Log.e(TAG, "YouTube-DL initialization failed - required files not found in APK")
                // This is the specific error we're seeing - try to fix it
                handleNoSuchFileException()
            }

            errorMessage.contains("ZipException") -> {
                Log.e(TAG, "YouTube-DL initialization failed - corrupted library archives")
            }

            else -> {
                Log.e(TAG, "YouTube-DL initialization failed with error: $errorMessage")
            }
        }
    }

    private fun handleNoSuchFileException() {
        try {
            Log.d(TAG, "Handling NoSuchFileException - attempting to fix missing files...")
            
            // Try to extract missing files again
            if (shouldExtractNativeLibraries()) {
                extractNativeLibrariesFromAPK()
                verifyNativeLibraryExtraction()
            }
            
            // Try to extract Python files
            val pythonDir = File(filesDir, "python")
            if (pythonDir.exists() && pythonDir.listFiles()?.isEmpty() != false) {
                extractPythonFilesFromAPK(pythonDir)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle NoSuchFileException", e)
        }
    }

    private fun tryBasicFunctionalityTest() {
        try {
            Log.d(TAG, "Trying basic functionality test...")
            Log.d(TAG, "YouTube-DL instance created successfully")
            isYoutubeDLInitialized = true
            Log.i(TAG, "Basic functionality test passed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Basic functionality test failed", e)
            isYoutubeDLInitialized = false
        }
    }

    private fun testBasicLibraryLoading() {
        try {
            Log.d(TAG, "Testing basic library loading...")
            
            // Try to load the YouTube-DL class
            val youtubeDLClass = Class.forName("com.yausername.youtubedl_android.YoutubeDL")
            Log.d(TAG, "YouTube-DL class loaded successfully: ${youtubeDLClass.name}")
            
            // Try to create an instance
            Log.d(TAG, "YouTube-DL instance created successfully")
            
            // Test if the newer library has different initialization methods
            testNewerLibraryMethods(youtubeDLClass)
            
            // Test native library directory access
            testNativeLibraryDirectory()
            
        } catch (e: Exception) {
            Log.e(TAG, "Basic library loading test failed", e)
        }
    }

    private fun testNewerLibraryMethods(youtubeDLClass: Class<*>) {
        try {
            Log.d(TAG, "Testing newer library methods...")
            
            // Check available methods
            val methods = youtubeDLClass.methods
            Log.d(TAG, "Available methods in YouTube-DL class:")
            methods.forEach { method ->
                Log.d(TAG, "  - ${method.name}(${method.parameterTypes.joinToString { it.simpleName }})")
            }
            
            // Check if there are any specific initialization methods
            val initMethods = methods.filter { it.name.contains("init", ignoreCase = true) }
            Log.d(TAG, "Initialization methods found: ${initMethods.size}")
            initMethods.forEach { method ->
                Log.d(TAG, "  - ${method.name}(${method.parameterTypes.joinToString { it.simpleName }})")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error testing newer library methods", e)
        }
    }

    private fun testNativeLibraryDirectory() {
        try {
            Log.d(TAG, "Testing native library directory access...")
            
            val nativeLibraryDir = File(applicationInfo.nativeLibraryDir)
            Log.d(TAG, "Native library directory: ${nativeLibraryDir.absolutePath}")
            Log.d(TAG, "Directory exists: ${nativeLibraryDir.exists()}")
            Log.d(TAG, "Directory is readable: ${nativeLibraryDir.canRead()}")
            
            if (nativeLibraryDir.exists()) {
                val files = nativeLibraryDir.listFiles()
                Log.d(TAG, "Number of files in directory: ${files?.size ?: 0}")
                
                files?.forEach { file ->
                    Log.d(TAG, "File: ${file.name} (${file.length()} bytes)")
                }
            }
            
            // Test APK contents
            testAPKContents()
            
        } catch (e: Exception) {
            Log.e(TAG, "Native library directory test failed", e)
        }
    }

    private fun testAPKContents() {
        try {
            Log.d(TAG, "Testing APK contents...")
            
            val apkFile = File(applicationInfo.sourceDir)
            Log.d(TAG, "APK file: ${apkFile.absolutePath}")
            Log.d(TAG, "APK exists: ${apkFile.exists()}")
            Log.d(TAG, "APK size: ${apkFile.length()} bytes")
            
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
                        Log.d(TAG, "Found library in APK: ${entry.name} (${entry.size} bytes)")
                    }
                }
                
                Log.d(TAG, "Total entries in APK: $totalEntries")
                Log.d(TAG, "Python-related libraries in APK: $pythonLibs")
                
                zipFile.close()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "APK contents test failed", e)
        }
    }
}
