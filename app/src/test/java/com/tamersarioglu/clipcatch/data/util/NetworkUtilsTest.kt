package com.tamersarioglu.clipcatch.data.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
        // Given
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        
        // When
        val result = networkUtils.isNetworkAvailable()
        
        // Then
        assertTrue(result)
    }
    
    @Test
    fun `isNetworkAvailable returns false when no active network`() {
        // Given
        every { connectivityManager.activeNetwork } returns null
        
        // When
        val result = networkUtils.isNetworkAvailable()
        
        // Then
        assertFalse(result)
    }
    
    @Test
    fun `isNetworkAvailable returns false when network capabilities are null`() {
        // Given
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns null
        
        // When
        val result = networkUtils.isNetworkAvailable()
        
        // Then
        assertFalse(result)
    }
    
    @Test
    fun `isNetworkAvailable returns false when network is not validated`() {
        // Given
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false
        
        // When
        val result = networkUtils.isNetworkAvailable()
        
        // Then
        assertFalse(result)
    }
    
    @Test
    fun `isWifiConnected returns true when connected to WiFi`() {
        // Given
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        
        // When
        val result = networkUtils.isWifiConnected()
        
        // Then
        assertTrue(result)
    }
    
    @Test
    fun `isMobileDataConnected returns true when connected to cellular`() {
        // Given
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        
        // When
        val result = networkUtils.isMobileDataConnected()
        
        // Then
        assertTrue(result)
    }
    
    @Test
    fun `getConnectionType returns WiFi when connected to WiFi`() {
        // Given
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        
        // When
        val result = networkUtils.getConnectionType()
        
        // Then
        assertEquals("WiFi", result)
    }
    
    @Test
    fun `getConnectionType returns Mobile Data when connected to cellular`() {
        // Given
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        
        // When
        val result = networkUtils.getConnectionType()
        
        // Then
        assertEquals("Mobile Data", result)
    }
    
    @Test
    fun `getConnectionType returns No Connection when no network available`() {
        // Given
        every { connectivityManager.activeNetwork } returns null
        
        // When
        val result = networkUtils.getConnectionType()
        
        // Then
        assertEquals("No Connection", result)
    }
}