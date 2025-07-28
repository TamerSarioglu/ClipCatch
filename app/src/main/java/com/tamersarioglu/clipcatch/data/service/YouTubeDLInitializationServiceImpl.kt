package com.tamersarioglu.clipcatch.data.service

import android.content.Context
import com.tamersarioglu.clipcatch.domain.model.InitializationError
import com.tamersarioglu.clipcatch.domain.model.InitializationResult
import com.tamersarioglu.clipcatch.domain.model.InitializationStatus
import com.tamersarioglu.clipcatch.util.Logger
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of YouTubeDLInitializationService that handles YouTube-DL initialization
 * with proper dependency management, error handling, and retry mechanisms.
 */
@Singleton
class YouTubeDLInitializationServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nativeLibraryManager: NativeLibraryManager,
    private val pythonEnvironmentManager: PythonEnvironmentManager,
    private val errorHandler: InitializationErrorHandler,
    private val logger: Logger
) : YouTubeDLInitializationService {

    companion object {
        private const val TAG = "YouTubeDLInitService"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L
    }

    private val initializationMutex = Mutex()
    
    @Volatile
    private var currentStatus: InitializationStatus = InitializationStatus.NotStarted
    
    @Volatile
    private var isYoutubeDLInitialized = false
    
    @Volatile
    private var initializationAttempted = false
    
    private var retryCount = 0

    override suspend fun initialize(): InitializationResult {
        logger.enter(TAG, "initialize")
        
        return initializationMutex.withLock {
            try {
                if (isYoutubeDLInitialized) {
                    logger.d(TAG, "YouTube-DL already initialized")
                    return@withLock InitializationResult.Success
                }

                if (currentStatus is InitializationStatus.InProgress) {
                    logger.d(TAG, "Initialization already in progress")
                    return@withLock InitializationResult.Failure(
                        InitializationError.YouTubeDLError("Initialization already in progress")
                    )
                }

                currentStatus = InitializationStatus.InProgress
                initializationAttempted = true
                
                logger.i(TAG, "Starting YouTube-DL initialization...")

                // Step 1: Ensure native libraries are ready
                val nativeLibResult = nativeLibraryManager.extractNativeLibraries()
                if (!nativeLibResult.success) {
                    val error = InitializationError.NativeLibraryError(
                        "Failed to extract native libraries: ${nativeLibResult.error?.message ?: "Unknown error"}",
                        nativeLibResult.error?.cause
                    )
                    return@withLock handleInitializationFailure(error)
                }

                // Step 2: Ensure Python environment is ready
                val pythonResult = pythonEnvironmentManager.setupPythonEnvironment()
                if (!pythonResult.success) {
                    val error = InitializationError.PythonEnvironmentError(
                        "Failed to setup Python environment: ${pythonResult.error?.message ?: "Unknown error"}",
                        pythonResult.error?.cause
                    )
                    return@withLock handleInitializationFailure(error)
                }

                // Step 3: Initialize YouTube-DL
                val youtubeDLResult = initializeYoutubeDLCore()
                if (youtubeDLResult is InitializationResult.Success) {
                    // Step 4: Verify initialization
                    val verificationPassed = verifyInitialization()
                    if (verificationPassed) {
                        isYoutubeDLInitialized = true
                        currentStatus = InitializationStatus.Completed
                        retryCount = 0
                        logger.i(TAG, "YouTube-DL initialization completed successfully")
                        return@withLock InitializationResult.Success
                    } else {
                        val error = InitializationError.YouTubeDLError(
                            "YouTube-DL initialization verification failed"
                        )
                        return@withLock handleInitializationFailure(error)
                    }
                } else {
                    return@withLock youtubeDLResult
                }

            } catch (e: Exception) {
                logger.e(TAG, "Unexpected error during YouTube-DL initialization", e)
                val error = InitializationError.YouTubeDLError(
                    "Unexpected initialization error: ${e.message}",
                    e
                )
                return@withLock handleInitializationFailure(error)
            }
        }
    }

    override fun isInitialized(): Boolean {
        return isYoutubeDLInitialized
    }

    override fun getInitializationStatus(): InitializationStatus {
        return currentStatus
    }

    override fun getStatusMessage(): String {
        return when (val status = currentStatus) {
            is InitializationStatus.NotStarted -> "YouTube-DL Not Started"
            is InitializationStatus.InProgress -> "YouTube-DL Initializing..."
            is InitializationStatus.Completed -> "YouTube-DL Ready"
            is InitializationStatus.Failed -> "YouTube-DL Failed - ${status.error.message}"
        }
    }

    override suspend fun ensureInitialized(): Boolean {
        logger.enter(TAG, "ensureInitialized")
        
        if (isYoutubeDLInitialized) {
            return true
        }

        val result = if (!initializationAttempted) {
            initialize()
        } else {
            retryInitialization()
        }

        return result is InitializationResult.Success
    }

    override suspend fun retryInitialization(): InitializationResult {
        logger.enter(TAG, "retryInitialization", "attempt=${retryCount + 1}")
        
        if (retryCount >= MAX_RETRY_ATTEMPTS) {
            logger.w(TAG, "Maximum retry attempts reached")
            val error = InitializationError.YouTubeDLError(
                "Maximum retry attempts ($MAX_RETRY_ATTEMPTS) exceeded"
            )
            return handleInitializationFailure(error)
        }

        retryCount++
        
        // Add delay between retries
        if (retryCount > 1) {
            logger.d(TAG, "Waiting ${RETRY_DELAY_MS}ms before retry attempt $retryCount")
            kotlinx.coroutines.delay(RETRY_DELAY_MS)
        }

        // Reset state for retry
        currentStatus = InitializationStatus.NotStarted
        isYoutubeDLInitialized = false

        return initialize()
    }

    override suspend fun resetAndReinitialize(): InitializationResult {
        logger.enter(TAG, "resetAndReinitialize")
        
        return initializationMutex.withLock {
            // Reset all state
            currentStatus = InitializationStatus.NotStarted
            isYoutubeDLInitialized = false
            initializationAttempted = false
            retryCount = 0
            
            logger.i(TAG, "Reset initialization state, starting fresh initialization")
            
            // Perform fresh initialization
            initialize()
        }
    }

    override suspend fun verifyInitialization(): Boolean {
        logger.enter(TAG, "verifyInitialization")
        
        return try {
            // Test basic YouTube-DL functionality
            val version = getVersion()
            if (version != null) {
                logger.d(TAG, "YouTube-DL verification passed, version: $version")
                true
            } else {
                logger.w(TAG, "YouTube-DL verification failed - could not get version")
                false
            }
        } catch (e: Exception) {
            logger.w(TAG, "YouTube-DL verification failed with exception", e)
            false
        }
    }

    override suspend fun getVersion(): String? {
        return try {
            if (!isYoutubeDLInitialized) {
                logger.d(TAG, "Cannot get version - YouTube-DL not initialized")
                return null
            }
            
            val youtubeDL = YoutubeDL.getInstance()
            val version = youtubeDL.version(context)
            logger.d(TAG, "Retrieved YouTube-DL version: $version")
            version
        } catch (e: Exception) {
            logger.w(TAG, "Failed to get YouTube-DL version", e)
            null
        }
    }

    /**
     * Core YouTube-DL initialization logic extracted from the original ClipCatchApplication.
     */
    private suspend fun initializeYoutubeDLCore(): InitializationResult {
        logger.enter(TAG, "initializeYoutubeDLCore")
        
        return try {
            logger.d(TAG, "Creating YouTube-DL instance...")
            val youtubeDLInstance = YoutubeDL.getInstance()
            
            logger.d(TAG, "Initializing YouTube-DL...")
            
            // Try the primary initialization method
            try {
                youtubeDLInstance.init(context)
                logger.d(TAG, "YouTube-DL init() completed successfully")
                
                // Add delay to allow native libraries to load properly
                logger.d(TAG, "Waiting for native libraries to load...")
                kotlinx.coroutines.delay(RETRY_DELAY_MS)
                
                InitializationResult.Success
                
            } catch (e: Exception) {
                logger.w(TAG, "Standard init() failed, trying alternative methods", e)
                tryAlternativeInitialization(youtubeDLInstance)
            }
            
        } catch (e: YoutubeDLException) {
            logger.e(TAG, "YouTube-DL initialization failed", e)
            val error = InitializationError.YouTubeDLError(
                "YouTube-DL initialization failed: ${e.message}",
                e
            )
            InitializationResult.Failure(error)
            
        } catch (e: UnsatisfiedLinkError) {
            logger.e(TAG, "Native library loading failed", e)
            val error = when {
                e.message?.contains("libpython") == true -> 
                    InitializationError.NativeLibraryError("Python library loading failed", e)
                e.message?.contains("libssl") == true -> 
                    InitializationError.NativeLibraryError("SSL library loading failed", e)
                e.message?.contains("libcrypto") == true -> 
                    InitializationError.NativeLibraryError("Crypto library loading failed", e)
                else -> 
                    InitializationError.NativeLibraryError("Native library loading failed: ${e.message}", e)
            }
            InitializationResult.Failure(error)
            
        } catch (e: Exception) {
            logger.e(TAG, "Unexpected error during YouTube-DL core initialization", e)
            val error = InitializationError.YouTubeDLError(
                "Unexpected YouTube-DL initialization error: ${e.message}",
                e
            )
            InitializationResult.Failure(error)
        }
    }

    /**
     * Tries alternative YouTube-DL initialization methods.
     */
    private suspend fun tryAlternativeInitialization(youtubeDLInstance: YoutubeDL): InitializationResult {
        logger.enter(TAG, "tryAlternativeInitialization")
        
        return try {
            val youtubeDLClass = youtubeDLInstance.javaClass
            
            // Try init_ytdlp method first (newer library)
            try {
                val initYtdlpMethod = youtubeDLClass.getMethod("init_ytdlp", Context::class.java, File::class.java)
                logger.d(TAG, "Trying init_ytdlp(Context, File) method...")
                val pythonDir = pythonEnvironmentManager.getPythonDirectory()
                initYtdlpMethod.invoke(youtubeDLInstance, context, pythonDir)
                logger.d(TAG, "Alternative init_ytdlp(Context, File) succeeded")
                
                kotlinx.coroutines.delay(RETRY_DELAY_MS)
                return InitializationResult.Success
                
            } catch (e: Exception) {
                logger.d(TAG, "Alternative init_ytdlp(Context, File) failed: ${e.message}")
            }
            
            // Try initPython method
            try {
                val initPythonMethod = youtubeDLClass.getMethod("initPython", Context::class.java, File::class.java)
                logger.d(TAG, "Trying initPython(Context, File) method...")
                val pythonDir = pythonEnvironmentManager.getPythonDirectory()
                initPythonMethod.invoke(youtubeDLInstance, context, pythonDir)
                logger.d(TAG, "Alternative initPython(Context, File) succeeded")
                
                kotlinx.coroutines.delay(RETRY_DELAY_MS)
                return InitializationResult.Success
                
            } catch (e: Exception) {
                logger.d(TAG, "Alternative initPython(Context, File) failed: ${e.message}")
            }
            
            // Try fallback initialization
            tryFallbackInitialization(youtubeDLInstance)
            
        } catch (e: Exception) {
            logger.e(TAG, "Alternative initialization failed", e)
            val error = InitializationError.YouTubeDLError(
                "All alternative initialization methods failed: ${e.message}",
                e
            )
            InitializationResult.Failure(error)
        }
    }

    /**
     * Tries fallback YouTube-DL initialization methods.
     */
    private suspend fun tryFallbackInitialization(youtubeDLInstance: YoutubeDL): InitializationResult {
        logger.enter(TAG, "tryFallbackInitialization")
        
        return try {
            val youtubeDLClass = youtubeDLInstance.javaClass
            
            // Method 1: Try init with Context parameter only
            try {
                val initMethod = youtubeDLClass.getMethod("init", Context::class.java)
                logger.d(TAG, "Trying init(Context) method...")
                initMethod.invoke(youtubeDLInstance, context)
                logger.d(TAG, "Fallback init(Context) succeeded")
                
                kotlinx.coroutines.delay(RETRY_DELAY_MS)
                return InitializationResult.Success
                
            } catch (e: Exception) {
                logger.d(TAG, "Fallback init(Context) failed: ${e.message}")
            }
            
            // Method 2: Try init without parameters
            try {
                val initMethod = youtubeDLClass.getMethod("init")
                logger.d(TAG, "Trying init() method...")
                initMethod.invoke(youtubeDLInstance)
                logger.d(TAG, "Fallback init() succeeded")
                
                kotlinx.coroutines.delay(RETRY_DELAY_MS)
                return InitializationResult.Success
                
            } catch (e: Exception) {
                logger.d(TAG, "Fallback init() failed: ${e.message}")
            }
            
            // Method 3: Try to create a new instance and initialize it differently
            try {
                logger.d(TAG, "Trying alternative instance initialization...")
                val newInstance = YoutubeDL.getInstance()
                newInstance.init(context)
                logger.d(TAG, "Alternative instance initialization succeeded")
                
                kotlinx.coroutines.delay(RETRY_DELAY_MS)
                return InitializationResult.Success
                
            } catch (e: Exception) {
                logger.d(TAG, "Alternative instance initialization failed: ${e.message}")
            }
            
            // All fallback methods failed
            val error = InitializationError.YouTubeDLError("All fallback initialization methods failed")
            InitializationResult.Failure(error)
            
        } catch (e: Exception) {
            logger.e(TAG, "Fallback initialization failed", e)
            val error = InitializationError.YouTubeDLError(
                "Fallback initialization failed: ${e.message}",
                e
            )
            InitializationResult.Failure(error)
        }
    }

    /**
     * Handles initialization failure by updating status and processing the error.
     */
    private fun handleInitializationFailure(error: InitializationError): InitializationResult {
        logger.enter(TAG, "handleInitializationFailure", error.message)
        
        currentStatus = InitializationStatus.Failed(error)
        isYoutubeDLInitialized = false
        
        // Process error through error handler
        val handlingResult = errorHandler.handleError(error)
        logger.d(TAG, "Error handling result: $handlingResult")
        
        return InitializationResult.Failure(error)
    }
}