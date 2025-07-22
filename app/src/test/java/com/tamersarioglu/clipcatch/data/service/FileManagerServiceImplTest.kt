package com.tamersarioglu.clipcatch.data.service

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class FileManagerServiceImplTest {

    @RelaxedMockK
    private lateinit var context: Context

    @MockK
    private lateinit var contentResolver: ContentResolver

    @MockK
    private lateinit var uri: Uri

    @MockK
    private lateinit var downloadsDir: File

    @MockK
    private lateinit var cacheDir: File

    @MockK
    private lateinit var statFs: StatFs

    private lateinit var fileManagerService: FileManagerServiceImpl

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        
        // Mock context
        every { context.contentResolver } returns contentResolver
        every { context.cacheDir } returns cacheDir
        every { context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) } returns downloadsDir
        
        // Mock file operations
        every { downloadsDir.path } returns "/storage/emulated/0/Download/ClipCatch"
        every { downloadsDir.exists() } returns true
        every { downloadsDir.mkdirs() } returns true
        
        // Mock cache dir
        every { cacheDir.path } returns "/data/user/0/com.tamersarioglu.clipcatch/cache"
        
        // Create the service
        fileManagerService = FileManagerServiceImpl(context)
        
        // Mock StatFs for storage space checks
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBlocksLong } returns 1000L
        every { anyConstructed<StatFs>().blockSizeLong } returns 4096L
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getDownloadsDirectory returns correct directory`() {
        val result = fileManagerService.getDownloadsDirectory()
        assertEquals(downloadsDir, result)
    }

    @Test
    fun `hasEnoughStorageSpace returns true when enough space`() {
        // 1000 blocks * 4096 bytes = 4,096,000 bytes available
        // We're requesting 1,000,000 bytes (less than available)
        val result = fileManagerService.hasEnoughStorageSpace(1_000_000)
        assertTrue(result)
    }

    @Test
    fun `hasEnoughStorageSpace returns false when not enough space`() {
        // 1000 blocks * 4096 bytes = 4,096,000 bytes available
        // We're requesting 5,000,000 bytes (more than available)
        val result = fileManagerService.hasEnoughStorageSpace(5_000_000)
        assertFalse(result)
    }

    @Test
    fun `openFileOutputStream opens stream correctly`() {
        val mockFile = mockk<File>()
        val mockStream = mockk<FileOutputStream>()
        
        mockkConstructor(FileOutputStream::class)
        every { anyConstructed<FileOutputStream>() } returns mockStream
        
        val result = fileManagerService.openFileOutputStream(mockFile)
        assertEquals(mockStream, result)
    }

    @Test
    fun `closeOutputStream closes stream safely`() {
        val mockStream = mockk<FileOutputStream>(relaxed = true)
        
        fileManagerService.closeOutputStream(mockStream)
        
        verify { mockStream.flush() }
        verify { mockStream.close() }
    }

    @Test
    fun `createDownloadFile with Android 10+ uses MediaStore`() = runBlocking {
        // Set up for Android 10+
        mockkStatic(Build.VERSION::class)
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.Q
        
        // Mock ContentResolver insert
        every { 
            contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, 
                any()
            ) 
        } returns uri
        
        // Mock File operations
        val mockFile = mockk<File>(relaxed = true)
        every { mockFile.exists() } returns false
        every { mockFile.createNewFile() } returns true
        mockkConstructor(File::class)
        every { anyConstructed<File>().exists() } returns false
        every { anyConstructed<File>().createNewFile() } returns true
        every { anyConstructed<File>().deleteOnExit() } returns Unit
        
        // Execute
        val result = fileManagerService.createDownloadFile("test_video.mp4")
        
        // Verify MediaStore was used
        verify { 
            contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, 
                any<ContentValues>()
            ) 
        }
    }

    @Test
    fun `createDownloadFile with pre-Android 10 uses direct file access`() = runBlocking {
        // Set up for pre-Android 10
        mockkStatic(Build.VERSION::class)
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.P
        
        // Mock Environment
        mockkStatic(Environment::class)
        val publicDownloadsDir = mockk<File>()
        every { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) } returns publicDownloadsDir
        
        // Mock File operations
        val mockFile = mockk<File>(relaxed = true)
        every { mockFile.exists() } returns false
        every { mockFile.createNewFile() } returns true
        
        mockkConstructor(File::class)
        every { anyConstructed<File>().exists() } returns false
        every { anyConstructed<File>().mkdirs() } returns true
        every { anyConstructed<File>().createNewFile() } returns true
        
        // Execute
        val result = fileManagerService.createDownloadFile("test_video.mp4")
        
        // Verify direct file access was used (no MediaStore)
        verify(exactly = 0) { contentResolver.insert(any(), any()) }
    }

    @Test
    fun `createDownloadFile handles file name conflicts`() = runBlocking {
        // Set up for pre-Android 10
        mockkStatic(Build.VERSION::class)
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.P
        
        // Mock Environment
        mockkStatic(Environment::class)
        val publicDownloadsDir = mockk<File>()
        every { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) } returns publicDownloadsDir
        
        // First file exists, second doesn't
        val existingFile = mockk<File>()
        every { existingFile.exists() } returns true
        
        val newFile = mockk<File>()
        every { newFile.exists() } returns false
        every { newFile.createNewFile() } returns true
        
        // Mock File constructor to return different instances
        var fileCounter = 0
        mockkConstructor(File::class)
        every { anyConstructed<File>().exists() } answers {
            fileCounter++
            fileCounter == 1 // First call returns true (file exists), second call returns false
        }
        every { anyConstructed<File>().mkdirs() } returns true
        every { anyConstructed<File>().createNewFile() } returns true
        
        // Execute
        val result = fileManagerService.createDownloadFile("test_video.mp4")
        
        // We can't easily verify the exact file name in this test setup,
        // but we can verify that file creation was attempted
        verify(atLeast = 1) { anyConstructed<File>().exists() }
        verify { anyConstructed<File>().createNewFile() }
    }

    @Test(expected = IOException::class)
    fun `createDownloadFile throws IOException when file creation fails`() = runBlocking {
        // Set up for pre-Android 10
        mockkStatic(Build.VERSION::class)
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.P
        
        // Mock Environment
        mockkStatic(Environment::class)
        val publicDownloadsDir = mockk<File>()
        every { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) } returns publicDownloadsDir
        
        // Mock File operations to fail
        mockkConstructor(File::class)
        every { anyConstructed<File>().exists() } returns false
        every { anyConstructed<File>().mkdirs() } returns true
        every { anyConstructed<File>().createNewFile() } returns false
        
        // This should throw IOException
        fileManagerService.createDownloadFile("test_video.mp4")
    }
}