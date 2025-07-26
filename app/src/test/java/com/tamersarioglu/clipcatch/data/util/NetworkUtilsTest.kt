package com.tamersarioglu.clipcatch.data.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.tamersarioglu.clipcatch.util.NetworkUtils
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NetworkUtilsTest {
    
    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var network: Network
    private lateinit var networkCapabilities: NetworkCapabilities
    private lateinit var networkUtils: NetworkUtils
    
    @Before
    fun setUp() {
        context = mockk()
        connectivityManager = mockk()
        network = mockk()
        networkCapabilities = mockk()
        
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        
        networkUtils = NetworkUtils(context)
    }
    
    @Test
    fun `isNetworkAvailable returns true when network is available and validated`() {
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        
        val result = networkUtils.isNetworkAvailable()
        
        assertTrue(result)
    }
    
    @Test
    fun `isNetworkAvailable returns false when no active network`() {
        every { connectivityManager.activeNetwork } returns null
        
        val result = networkUtils.isNetworkAvailable()
        
        assertFalse(result)
    }
    
    @Test
    fun `isNetworkAvailable returns false when network capabilities are null`() {
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns null
        
        val result = networkUtils.isNetworkAvailable()
        
        assertFalse(result)
    }
    
    @Test
    fun `isNetworkAvailable returns false when network is not validated`() {
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false
        
        val result = networkUtils.isNetworkAvailable()
        
        assertFalse(result)
    }
    
    @Test
    fun `isWifiConnected returns true when connected to WiFi`() {
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        
        val result = networkUtils.isWifiConnected()
        
        assertTrue(result)
    }
    
    @Test
    fun `isMobileDataConnected returns true when connected to cellular`() {
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        
        val result = networkUtils.isMobileDataConnected()
        
        assertTrue(result)
    }
    
    @Test
    fun `getConnectionType returns WiFi when connected to WiFi`() {
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        
        val result = networkUtils.getConnectionType()
        
        assertEquals("WiFi", result)
    }
    
    @Test
    fun `getConnectionType returns Mobile Data when connected to cellular`() {
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        
        val result = networkUtils.getConnectionType()
        
        assertEquals("Mobile Data", result)
    }
    
    @Test
    fun `getConnectionType returns No Connection when no network available`() {
        every { connectivityManager.activeNetwork } returns null
        
        val result = networkUtils.getConnectionType()
        
        assertEquals("No Connection", result)
    }
}