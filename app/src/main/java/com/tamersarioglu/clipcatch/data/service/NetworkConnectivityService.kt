package com.tamersarioglu.clipcatch.data.service

import android.content.Context
import com.tamersarioglu.clipcatch.data.util.Logger
import com.tamersarioglu.clipcatch.data.util.NetworkConnectivityResult
import com.tamersarioglu.clipcatch.data.util.NetworkInfo
import com.tamersarioglu.clipcatch.data.util.NetworkSuitability
import com.tamersarioglu.clipcatch.data.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for monitoring network connectivity and providing network-related utilities
 */
interface NetworkConnectivityService {
    
    /**
     * Gets current network information
     */
    fun getCurrentNetworkInfo(): NetworkInfo
    
    /**
     * Flow that emits network connectivity state changes
     */
    fun networkConnectivityFlow(): Flow<Boolean>
    
    /**
     * Flow that emits detailed network information changes
     */
    fun networkInfoFlow(): StateFlow<NetworkInfo>
    
    /**
     * Performs a connectivity test to verify actual internet access
     */
    suspend fun performConnectivityTest(): NetworkConnectivityResult
    
    /**
     * Checks if network is suitable for downloads
     */
    fun isNetworkSuitableForDownload(): NetworkSuitability
    
    /**
     * Checks if the device can reach a specific host
     */
    suspend fun canReachHost(host: String, port: Int = 80, timeoutMs: Int = 5000): Boolean
    
    /**
     * Starts monitoring network connectivity
     */
    fun startMonitoring()
    
    /**
     * Stops monitoring network connectivity
     */
    fun stopMonitoring()
}

/**
 * Implementation of NetworkConnectivityService
 */
@Singleton
class NetworkConnectivityServiceImpl @Inject constructor(
    private val context: Context,
    private val networkUtils: NetworkUtils,
    private val logger: Logger
) : NetworkConnectivityService {
    
    companion object {
        private const val TAG = "NetworkConnectivityService"
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val _networkInfo = MutableStateFlow(networkUtils.getNetworkInfo())
    private val networkInfo: StateFlow<NetworkInfo> = _networkInfo.asStateFlow()
    
    private var isMonitoring = false
    
    override fun getCurrentNetworkInfo(): NetworkInfo {
        return networkUtils.getNetworkInfo()
    }
    
    override fun networkConnectivityFlow(): Flow<Boolean> {
        return networkUtils.networkConnectivityFlow()
    }
    
    override fun networkInfoFlow(): StateFlow<NetworkInfo> {
        return networkInfo
    }
    
    override suspend fun performConnectivityTest(): NetworkConnectivityResult {
        logger.d(TAG, "Performing connectivity test")
        return networkUtils.performConnectivityTest()
    }
    
    override fun isNetworkSuitableForDownload(): NetworkSuitability {
        return networkUtils.isNetworkSuitableForDownload()
    }
    
    override suspend fun canReachHost(host: String, port: Int, timeoutMs: Int): Boolean {
        return networkUtils.canReachHost(host, port, timeoutMs)
    }
    
    override fun startMonitoring() {
        if (isMonitoring) {
            logger.d(TAG, "Network monitoring already started")
            return
        }
        
        logger.i(TAG, "Starting network connectivity monitoring")
        isMonitoring = true
        
        // Monitor network connectivity changes
        networkUtils.networkConnectivityFlow()
            .distinctUntilChanged()
            .onEach { isConnected ->
                logger.d(TAG, "Network connectivity changed: $isConnected")
                updateNetworkInfo()
            }
            .launchIn(serviceScope)
        
        // Perform initial network info update
        updateNetworkInfo()
    }
    
    override fun stopMonitoring() {
        if (!isMonitoring) {
            logger.d(TAG, "Network monitoring already stopped")
            return
        }
        
        logger.i(TAG, "Stopping network connectivity monitoring")
        isMonitoring = false
        
        // Note: CoroutineScope will be cancelled when the service is destroyed
    }
    
    /**
     * Updates the current network information
     */
    private fun updateNetworkInfo() {
        serviceScope.launch {
            try {
                val currentInfo = networkUtils.getNetworkInfo()
                _networkInfo.value = currentInfo
                
                logger.d(TAG, "Network info updated: ${currentInfo.connectionType}, Available: ${currentInfo.isAvailable}")
                
                // Log network suitability for downloads
                val suitability = networkUtils.isNetworkSuitableForDownload()
                logger.d(TAG, "Network suitability for downloads: $suitability")
                
            } catch (e: Exception) {
                logger.e(TAG, "Error updating network info", e)
            }
        }
    }
}

/**
 * Extension functions for easier network checking
 */
fun NetworkInfo.isGoodForDownload(): Boolean {
    return isAvailable && (isWifi || (isMobile && !isMetered))
}

fun NetworkInfo.getConnectionQuality(): String {
    return when {
        !isAvailable -> "No Connection"
        isWifi -> "Excellent (WiFi)"
        isMobile && !isMetered -> "Good (Mobile)"
        isMobile && isMetered -> "Limited (Metered)"
        else -> "Unknown"
    }
}