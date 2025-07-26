package com.tamersarioglu.clipcatch.data.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.tamersarioglu.clipcatch.util.PermissionUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class PermissionUtilsTest {
    
    private lateinit var permissionUtils: PermissionUtils
    private lateinit var mockContext: Context
    
    @Before
    fun setUp() {
        permissionUtils = PermissionUtils()
        mockContext = mockk()
        mockkStatic(ContextCompat::class)
    }
    
    @After
    fun tearDown() {
        unmockkStatic(ContextCompat::class)
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `getRequiredPermissions returns media permissions for Android 13+`() {
        val permissions = permissionUtils.getRequiredPermissions()
        
        val expected = arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
        
        assertArrayEquals(expected, permissions)
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `getRequiredPermissions returns empty array for Android 10-12`() {
        val permissions = permissionUtils.getRequiredPermissions()
        
        assertTrue(permissions.isEmpty())
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `getRequiredPermissions returns storage permissions for Android 9 and below`() {
        val permissions = permissionUtils.getRequiredPermissions()
        
        val expected = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        
        assertArrayEquals(expected, permissions)
    }
    
    @Test
    fun `hasAllRequiredPermissions returns true when all permissions granted`() {
        every { 
            ContextCompat.checkSelfPermission(mockContext, any()) 
        } returns PackageManager.PERMISSION_GRANTED
        
        val result = permissionUtils.hasAllRequiredPermissions(mockContext)
        
        assertTrue(result)
    }
    
    @Test
    fun `hasAllRequiredPermissions returns false when some permissions denied`() {
        every { 
            ContextCompat.checkSelfPermission(mockContext, Manifest.permission.READ_MEDIA_VIDEO) 
        } returns PackageManager.PERMISSION_GRANTED
        
        every { 
            ContextCompat.checkSelfPermission(mockContext, Manifest.permission.READ_MEDIA_AUDIO) 
        } returns PackageManager.PERMISSION_DENIED
        
        val result = permissionUtils.hasAllRequiredPermissions(mockContext)
        
        assertFalse(result)
    }
    
    @Test
    fun `hasPermission returns true when permission granted`() {
        every { 
            ContextCompat.checkSelfPermission(mockContext, Manifest.permission.READ_MEDIA_VIDEO) 
        } returns PackageManager.PERMISSION_GRANTED
        
        val result = permissionUtils.hasPermission(mockContext, Manifest.permission.READ_MEDIA_VIDEO)
        
        assertTrue(result)
    }
    
    @Test
    fun `hasPermission returns false when permission denied`() {
        every { 
            ContextCompat.checkSelfPermission(mockContext, Manifest.permission.READ_MEDIA_VIDEO) 
        } returns PackageManager.PERMISSION_DENIED
        
        val result = permissionUtils.hasPermission(mockContext, Manifest.permission.READ_MEDIA_VIDEO)
        
        assertFalse(result)
    }
    
    @Test
    fun `getMissingPermissions returns only denied permissions`() {
        every { 
            ContextCompat.checkSelfPermission(mockContext, Manifest.permission.READ_MEDIA_VIDEO) 
        } returns PackageManager.PERMISSION_GRANTED
        
        every { 
            ContextCompat.checkSelfPermission(mockContext, Manifest.permission.READ_MEDIA_AUDIO) 
        } returns PackageManager.PERMISSION_DENIED
        
        val result = permissionUtils.getMissingPermissions(mockContext)
        
        assertArrayEquals(arrayOf(Manifest.permission.READ_MEDIA_AUDIO), result)
    }
    
    @Test
    fun `getPermissionExplanation returns correct explanation for storage permission`() {
        val explanation = permissionUtils.getPermissionExplanation(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        
        assertEquals(
            "Storage permission is needed to save downloaded videos to your device.",
            explanation
        )
    }
    
    @Test
    fun `getPermissionExplanation returns correct explanation for media video permission`() {
        val explanation = permissionUtils.getPermissionExplanation(Manifest.permission.READ_MEDIA_VIDEO)
        
        assertEquals(
            "Media permission is needed to save and access downloaded videos.",
            explanation
        )
    }
    
    @Test
    fun `getPermissionExplanation returns generic explanation for unknown permission`() {
        val explanation = permissionUtils.getPermissionExplanation("unknown.permission")
        
        assertEquals(
            "This permission is required for the app to function properly.",
            explanation
        )
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `getGeneralPermissionExplanation returns correct explanation for Android 13+`() {
        val explanation = permissionUtils.getGeneralPermissionExplanation()
        
        assertTrue(explanation.contains("media permissions"))
        assertTrue(explanation.contains("save downloaded videos"))
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `getGeneralPermissionExplanation returns correct explanation for Android 10-12`() {
        val explanation = permissionUtils.getGeneralPermissionExplanation()
        
        assertTrue(explanation.contains("scoped storage"))
        assertTrue(explanation.contains("No additional permissions"))
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `getGeneralPermissionExplanation returns correct explanation for Android 9 and below`() {
        val explanation = permissionUtils.getGeneralPermissionExplanation()
        
        assertTrue(explanation.contains("storage permissions"))
        assertTrue(explanation.contains("Downloads folder"))
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `getCriticalPermissions returns video permission for Android 13+`() {
        val permissions = permissionUtils.getCriticalPermissions()
        
        assertArrayEquals(arrayOf(Manifest.permission.READ_MEDIA_VIDEO), permissions)
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `getCriticalPermissions returns empty array for Android 10-12`() {
        val permissions = permissionUtils.getCriticalPermissions()
        
        assertTrue(permissions.isEmpty())
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `getCriticalPermissions returns write storage permission for Android 9 and below`() {
        val permissions = permissionUtils.getCriticalPermissions()
        
        assertArrayEquals(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), permissions)
    }
    
    @Test
    fun `hasCriticalPermissions returns true when critical permissions granted`() {
        every { 
            ContextCompat.checkSelfPermission(mockContext, any()) 
        } returns PackageManager.PERMISSION_GRANTED
        
        val result = permissionUtils.hasCriticalPermissions(mockContext)
        
        assertTrue(result)
    }
    
    @Test
    fun `hasCriticalPermissions returns false when critical permissions denied`() {
        every { 
            ContextCompat.checkSelfPermission(mockContext, Manifest.permission.READ_MEDIA_VIDEO) 
        } returns PackageManager.PERMISSION_DENIED
        
        val result = permissionUtils.hasCriticalPermissions(mockContext)
        
        assertFalse(result)
    }
}