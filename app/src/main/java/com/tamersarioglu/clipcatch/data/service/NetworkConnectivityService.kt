package com.tamersarioglu.clipcatch.data.service

import android.content.Context
import com.tamersarioglu.clipcatch.util.Logger
import com.tamersarioglu.clipcatch.util.NetworkConnectivityResult
import com.tamersarioglu.clipcatch.util.NetworkInfo
import com.tamersarioglu.clipcatch.util.NetworkSuitability
import com.tamersarioglu.clipcatch.util.NetworkUtils
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

interface NetworkConnectivityService {
    fun getCurrentNetworkInfo(): NetworkInfo
    fun networkConnectivityFlow(): Flow<Boolean>
    fun networkInfoFlow(): StateFlow<NetworkInfo>
    suspend fun performConnectivityTest(): NetworkConnectivityResult
    fun isNetworkSuitableForDownload(): NetworkSuitability
    suspend fun canReachHost(host: String, port: Int = 80, timeoutMs: Int = 5000): Boolean
    fun startMonitoring()
    fun stopMonitoring()
}

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

        networkUtils.networkConnectivityFlow()
            .distinctUntilChanged()
            .onEach { isConnected ->
                logger.d(TAG, "Network connectivity changed: $isConnected")
                updateNetworkInfo()
            }
            .launchIn(serviceScope)

        updateNetworkInfo()
    }

    override fun stopMonitoring() {
        if (!isMonitoring) {
            logger.d(TAG, "Network monitoring already stopped")
            return
        }

        logger.i(TAG, "Stopping network connectivity monitoring")
        isMonitoring = false
    }

    private fun updateNetworkInfo() {
        serviceScope.launch {
            try {
                val currentInfo = networkUtils.getNetworkInfo()
                _networkInfo.value = currentInfo

                logger.d(
                    TAG,
                    "Network info updated: ${currentInfo.connectionType}, Available: ${currentInfo.isAvailable}"
                )

                val suitability = networkUtils.isNetworkSuitableForDownload()
                logger.d(TAG, "Network suitability for downloads: $suitability")

            } catch (e: Exception) {
                logger.e(TAG, "Error updating network info", e)
            }
        }
    }
}

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