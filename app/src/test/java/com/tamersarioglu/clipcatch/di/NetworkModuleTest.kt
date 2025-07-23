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
        val json = networkModule.provideJson()
        assertNotNull(json)
    }
    
    @Test
    fun `provideHttpLoggingInterceptor returns configured interceptor`() {
        val interceptor = networkModule.provideHttpLoggingInterceptor()
        assertNotNull(interceptor)
        assertEquals(HttpLoggingInterceptor.Level.BODY, interceptor.level)
    }
    
    @Test
    fun `provideNetworkUtils returns NetworkUtils instance`() {
        val context = mockk<Context>(relaxed = true)
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        val networkUtils = networkModule.provideNetworkUtils(context)
        assertNotNull(networkUtils)
    }
    
    @Test
    fun `provideOkHttpClient returns properly configured client`() {
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
        assertNotNull(client)
        assertEquals(30, client.connectTimeoutMillis / 1000)
        assertEquals(60, client.readTimeoutMillis / 1000)
        assertEquals(60, client.writeTimeoutMillis / 1000)
        assertEquals(120, client.callTimeoutMillis / 1000)
        assertTrue(client.retryOnConnectionFailure)
        assertEquals(4, client.interceptors.size)
    }
    
    @Test
    fun `provideRetrofit returns properly configured Retrofit instance`() {
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
        val retrofit = networkModule.provideRetrofit(client, json)
        assertNotNull(retrofit)
        assertEquals("https://www.youtube.com/", retrofit.baseUrl().toString())
        assertTrue(retrofit.converterFactories().size >= 1)
    }
}