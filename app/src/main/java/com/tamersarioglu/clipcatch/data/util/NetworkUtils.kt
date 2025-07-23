package com.tamersarioglu.clipcatch.data.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkUtils @Inject constructor(
    private val context: Context,
    private val logger: Logger
) {
    
    companion object {
        private const val TAG = "NetworkUtils"
        private const val CONNECTIVITY_CHECK_TIMEOUT = 5000
        private const val GOOGLE_DNS = "8.8.8.8"
        private const val GOOGLE_DNS_PORT = 53
    }
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    fun isWifiConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    fun isMobileDataConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
    
    fun getConnectionType(): String {
        return when {
            isWifiConnected() -> "WiFi"
            isMobileDataConnected() -> "Mobile Data"
            else -> "No Connection"
        }
    }
    
    fun networkConnectivityFlow(): Flow<Boolean> = callbackFlow {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                logger.d(TAG, "Network available: $network")
                trySend(true)
            }
            
            override fun onLost(network: android.net.Network) {
                logger.d(TAG, "Network lost: $network")
                trySend(false)
            }
            
            override fun onCapabilitiesChanged(
                network: android.net.Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                logger.d(TAG, "Network capabilities changed: hasInternet=$hasInternet")
                trySend(hasInternet)
            }
        }
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        
        // Send initial state
        val initialState = isNetworkAvailable()
        logger.d(TAG, "Initial network state: $initialState")
        trySend(initialState)
        
        awaitClose {
            logger.d(TAG, "Unregistering network callback")
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }.distinctUntilChanged()
    
    suspend fun performConnectivityTest(): NetworkConnectivityResult {
        logger.d(TAG, "Performing connectivity test")
        
        return try {
            if (!isNetworkAvailable()) {
                logger.w(TAG, "System reports no network available")
                return NetworkConnectivityResult.NoConnection
            }
            
            val socket = Socket()
            socket.connect(InetSocketAddress(GOOGLE_DNS, GOOGLE_DNS_PORT), CONNECTIVITY_CHECK_TIMEOUT)
            socket.close()
            
            logger.i(TAG, "Connectivity test successful")
            NetworkConnectivityResult.Connected(getConnectionType())
            
        } catch (e: IOException) {
            logger.w(TAG, "Connectivity test failed", e)
            NetworkConnectivityResult.Limited
        } catch (e: Exception) {
            logger.e(TAG, "Unexpected error during connectivity test", e)
            NetworkConnectivityResult.Error(e.message ?: "Unknown connectivity error")
        }
    }
    
    suspend fun canReachHost(host: String, port: Int = 80, timeoutMs: Int = CONNECTIVITY_CHECK_TIMEOUT): Boolean {
        return try {
            logger.d(TAG, "Testing connectivity to $host:$port")
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.close()
            logger.d(TAG, "Successfully connected to $host:$port")
            true
        } catch (e: Exception) {
            logger.w(TAG, "Failed to connect to $host:$port", e)
            false
        }
    }
    
    fun getNetworkInfo(): NetworkInfo {
        val isAvailable = isNetworkAvailable()
        val connectionType = getConnectionType()
        val isWifi = isWifiConnected()
        val isMobile = isMobileDataConnected()
        
        return NetworkInfo(
            isAvailable = isAvailable,
            connectionType = connectionType,
            isWifi = isWifi,
            isMobile = isMobile,
            isMetered = isMeteredConnection()
        )
    }
    
    fun isMeteredConnection(): Boolean {
        return connectivityManager.isActiveNetworkMetered
    }
    
    fun getSignalStrength(): Int? {
        val network = connectivityManager.activeNetwork ?: return null
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
        
        return networkCapabilities.signalStrength.takeIf { it != Int.MIN_VALUE }
    }
    
    fun isNetworkSuitableForDownload(): NetworkSuitability {
        val networkInfo = getNetworkInfo()
        
        return when {
            !networkInfo.isAvailable -> NetworkSuitability.NotAvailable
            networkInfo.isWifi -> NetworkSuitability.Excellent
            networkInfo.isMobile && !networkInfo.isMetered -> NetworkSuitability.Good
            networkInfo.isMobile && networkInfo.isMetered -> NetworkSuitability.Limited
            else -> NetworkSuitability.Unknown
        }
    }
}

sealed class NetworkConnectivityResult {
    object NoConnection : NetworkConnectivityResult()
    data class Connected(val connectionType: String) : NetworkConnectivityResult()
    object Limited : NetworkConnectivityResult()
    data class Error(val message: String) : NetworkConnectivityResult()
}

data class NetworkInfo(
    val isAvailable: Boolean,
    val connectionType: String,
    val isWifi: Boolean,
    val isMobile: Boolean,
    val isMetered: Boolean
)

enum class NetworkSuitability {
    NotAvailable,
    Limited,
    Good,
    Excellent,
    Unknown
}