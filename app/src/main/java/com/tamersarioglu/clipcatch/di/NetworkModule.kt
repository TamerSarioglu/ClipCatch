package com.tamersarioglu.clipcatch.di

import android.content.Context
import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.tamersarioglu.clipcatch.data.util.NetworkException
import com.tamersarioglu.clipcatch.data.util.NetworkUtils
import com.tamersarioglu.clipcatch.data.util.toNetworkException
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
            encodeDefaults = true
        }
    }
    
    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            Log.d("NetworkModule", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }
    
    @Provides
    @Singleton
    fun provideNetworkUtils(@ApplicationContext context: Context): NetworkUtils {
        return NetworkUtils(context)
    }
    
    @Provides
    @Singleton
    fun provideNetworkConnectionInterceptor(
        networkUtils: NetworkUtils
    ): Interceptor {
        return Interceptor { chain ->
            if (!networkUtils.isNetworkAvailable()) {
                throw NetworkException.NoInternetException()
            }
            chain.proceed(chain.request())
        }
    }
    
    @Provides
    @Singleton
    fun provideErrorHandlingInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            try {
                val response = chain.proceed(request)
                
                // Log response details
                Log.d("NetworkModule", "Response for ${request.url}: ${response.code}")
                
                if (!response.isSuccessful) {
                    Log.e("NetworkModule", "HTTP Error: ${response.code} - ${response.message}")
                    
                    // Convert HTTP errors to appropriate NetworkExceptions
                    when (response.code) {
                        in 400..499 -> throw NetworkException.ClientException(response.code, response.message)
                        in 500..599 -> throw NetworkException.ServerException(response.code, response.message)
                    }
                }
                
                response
            } catch (e: Exception) {
                Log.e("NetworkModule", "Network request failed", e)
                throw e.toNetworkException()
            }
        }
    }
    
    @Provides
    @Singleton
    fun provideRetryInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            var response: Response? = null
            var exception: IOException? = null
            
            // Retry logic with exponential backoff
            val maxRetries = 3
            var retryCount = 0
            
            while (retryCount < maxRetries) {
                try {
                    response?.close() // Close previous response if exists
                    response = chain.proceed(request)
                    
                    if (response.isSuccessful) {
                        return@Interceptor response
                    }
                    
                    // Only retry on server errors (5xx) or specific client errors
                    if (response.code in 500..599 || response.code == 429) {
                        Log.w("NetworkModule", "Retrying request due to ${response.code}, attempt ${retryCount + 1}")
                        retryCount++
                        if (retryCount < maxRetries) {
                            Thread.sleep(1000L * retryCount) // Exponential backoff
                            continue
                        }
                    }
                    
                    return@Interceptor response
                    
                } catch (e: Exception) {
                    Log.w("NetworkModule", "Network error on attempt ${retryCount + 1}", e)
                    exception = e.toNetworkException()
                    retryCount++
                    
                    if (retryCount < maxRetries) {
                        Thread.sleep(1000L * retryCount) // Exponential backoff
                    }
                }
            }
            
            // If we've exhausted retries, throw the last exception or return the last response
            exception?.let { throw it }
            response ?: throw NetworkException.GenericNetworkException("Failed to get response after $maxRetries attempts")
        }
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        networkConnectionInterceptor: Interceptor,
        errorHandlingInterceptor: Interceptor,
        retryInterceptor: Interceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS) // Longer read timeout for video downloads
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS) // Overall call timeout
            .addInterceptor(networkConnectionInterceptor)
            .addInterceptor(errorHandlingInterceptor)
            .addInterceptor(retryInterceptor)
            .addInterceptor(loggingInterceptor)
            .retryOnConnectionFailure(true)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://www.youtube.com/") // Base URL for YouTube API calls
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

}