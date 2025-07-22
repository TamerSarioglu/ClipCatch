package com.tamersarioglu.clipcatch.di

import android.content.Context
import android.net.ConnectivityManager
import com.tamersarioglu.clipcatch.data.util.NetworkUtils
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit

class NetworkModuleTest {
    
    private val networkModule = NetworkModule
    
    @Test
    fun `provideJson returns properly configured Json instance`() {
        // When
        val json = networkModule.provideJson()
        
        // Then
        assertNotNull(json)
    }
    
    @Test
    fun `provideHttpLoggingInterceptor returns configured interceptor`() {
        // When
        val interceptor = networkModule.provideHttpLoggingInterceptor()
        
        // Then
        assertNotNull(interceptor)
        assertEquals(HttpLoggingInterceptor.Level.BODY, interceptor.level)
    }
    
    @Test
    fun `provideNetworkUtils returns NetworkUtils instance`() {
        // Given
        val context = mockk<Context>(relaxed = true)
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        
        // When
        val networkUtils = networkModule.provideNetworkUtils(context)
        
        // Then
        assertNotNull(networkUtils)
    }
    
    @Test
    fun `provideOkHttpClient returns properly configured client`() {
        // Given
        val loggingInterceptor = networkModule.provideHttpLoggingInterceptor()
        val context = mockk<Context>(relaxed = true)
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        val networkUtils = NetworkUtils(context)
        val networkConnectionInterceptor = networkModule.provideNetworkConnectionInterceptor(networkUtils)
        val errorHandlingInterceptor = networkModule.provideErrorHandlingInterceptor()
        val retryInterceptor = networkModule.provideRetryInterceptor()
        
        // When
        val client = networkModule.provideOkHttpClient(
            loggingInterceptor,
            networkConnectionInterceptor,
            errorHandlingInterceptor,
            retryInterceptor
        )
        
        // Then
        assertNotNull(client)
        
        // Verify timeouts
        assertEquals(30, client.connectTimeoutMillis / 1000)
        assertEquals(60, client.readTimeoutMillis / 1000)
        assertEquals(60, client.writeTimeoutMillis / 1000)
        assertEquals(120, client.callTimeoutMillis / 1000)
        
        // Verify retry on connection failure is enabled
        assertTrue(client.retryOnConnectionFailure)
        
        // Verify interceptors are added (should have 4 interceptors)
        assertEquals(4, client.interceptors.size)
    }
    
    @Test
    fun `provideRetrofit returns properly configured Retrofit instance`() {
        // Given
        val loggingInterceptor = networkModule.provideHttpLoggingInterceptor()
        val context = mockk<Context>(relaxed = true)
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        val networkUtils = NetworkUtils(context)
        val networkConnectionInterceptor = networkModule.provideNetworkConnectionInterceptor(networkUtils)
        val errorHandlingInterceptor = networkModule.provideErrorHandlingInterceptor()
        val retryInterceptor = networkModule.provideRetryInterceptor()
        val client = networkModule.provideOkHttpClient(
            loggingInterceptor,
            networkConnectionInterceptor,
            errorHandlingInterceptor,
            retryInterceptor
        )
        val json = networkModule.provideJson()
        
        // When
        val retrofit = networkModule.provideRetrofit(client, json)
        
        // Then
        assertNotNull(retrofit)
        assertEquals("https://www.youtube.com/", retrofit.baseUrl().toString())
        // Retrofit includes a built-in converter factory, so we should have at least 1
        assertTrue(retrofit.converterFactories().size >= 1)
    }
}