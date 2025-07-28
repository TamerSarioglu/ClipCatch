package com.tamersarioglu.clipcatch.util

import org.junit.Assert.*
import org.junit.Test

class DefaultLoggingConfigurationTest {

    @Test
    fun `LoggingSettings should have correct default values`() {
        val settings = LoggingSettings()
        
        assertEquals(LogLevel.INFO, settings.globalLevel)
        assertTrue(settings.componentLevels.isEmpty())
        assertTrue(settings.extractionSummaryOnly)
    }

    @Test
    fun `LoggingSettings getEffectiveLevel should return global level when no component override`() {
        val settings = LoggingSettings(globalLevel = LogLevel.DEBUG)
        
        assertEquals(LogLevel.DEBUG, settings.getEffectiveLevel("SomeComponent"))
    }

    @Test
    fun `LoggingSettings getEffectiveLevel should return component level when override exists`() {
        val componentLevels = mapOf("TestComponent" to LogLevel.ERROR)
        val settings = LoggingSettings(
            globalLevel = LogLevel.DEBUG,
            componentLevels = componentLevels
        )
        
        assertEquals(LogLevel.ERROR, settings.getEffectiveLevel("TestComponent"))
        assertEquals(LogLevel.DEBUG, settings.getEffectiveLevel("OtherComponent"))
    }

    @Test
    fun `LoggingSettings withGlobalLevel should create new instance with updated global level`() {
        val original = LoggingSettings(globalLevel = LogLevel.INFO)
        val updated = original.withGlobalLevel(LogLevel.ERROR)
        
        assertEquals(LogLevel.INFO, original.globalLevel)
        assertEquals(LogLevel.ERROR, updated.globalLevel)
        assertNotSame(original, updated)
    }

    @Test
    fun `LoggingSettings withComponentLevel should create new instance with updated component level`() {
        val original = LoggingSettings()
        val updated = original.withComponentLevel("TestComponent", LogLevel.DEBUG)
        
        assertTrue(original.componentLevels.isEmpty())
        assertEquals(LogLevel.DEBUG, updated.componentLevels["TestComponent"])
        assertNotSame(original, updated)
    }

    @Test
    fun `LoggingSettings validate should remove invalid component names`() {
        val invalidComponents = mapOf(
            "InvalidComponent" to LogLevel.DEBUG,
            LoggingComponents.FILE_EXTRACTION to LogLevel.ERROR,
            "AnotherInvalid" to LogLevel.WARN,
            LoggingComponents.NETWORK to LogLevel.INFO
        )
        
        val settings = LoggingSettings(componentLevels = invalidComponents)
        val validated = settings.validate()
        
        assertEquals(2, validated.componentLevels.size)
        assertEquals(LogLevel.ERROR, validated.componentLevels[LoggingComponents.FILE_EXTRACTION])
        assertEquals(LogLevel.INFO, validated.componentLevels[LoggingComponents.NETWORK])
        assertNull(validated.componentLevels["InvalidComponent"])
        assertNull(validated.componentLevels["AnotherInvalid"])
    }

}