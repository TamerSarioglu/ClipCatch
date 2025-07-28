package com.tamersarioglu.clipcatch.util

import org.junit.Assert.*
import org.junit.Test

class LoggingSettingsTest {

    @Test
    fun `default settings should have INFO level and empty component overrides`() {
        val settings = LoggingSettings()
        
        assertEquals(LogLevel.INFO, settings.globalLevel)
        assertTrue(settings.componentLevels.isEmpty())
        assertTrue(settings.extractionSummaryOnly)
    }

    @Test
    fun `getEffectiveLevel should return global level when no component override`() {
        val settings = LoggingSettings(globalLevel = LogLevel.DEBUG)
        
        assertEquals(LogLevel.DEBUG, settings.getEffectiveLevel("SomeComponent"))
    }

    @Test
    fun `getEffectiveLevel should return component level when override exists`() {
        val componentLevels = mapOf("TestComponent" to LogLevel.ERROR)
        val settings = LoggingSettings(
            globalLevel = LogLevel.DEBUG,
            componentLevels = componentLevels
        )
        
        assertEquals(LogLevel.ERROR, settings.getEffectiveLevel("TestComponent"))
        assertEquals(LogLevel.DEBUG, settings.getEffectiveLevel("OtherComponent"))
    }

    @Test
    fun `withGlobalLevel should create new instance with updated global level`() {
        val original = LoggingSettings(globalLevel = LogLevel.INFO)
        val updated = original.withGlobalLevel(LogLevel.ERROR)
        
        assertEquals(LogLevel.INFO, original.globalLevel)
        assertEquals(LogLevel.ERROR, updated.globalLevel)
        assertNotSame(original, updated)
    }

    @Test
    fun `withComponentLevel should create new instance with updated component level`() {
        val original = LoggingSettings()
        val updated = original.withComponentLevel("TestComponent", LogLevel.DEBUG)
        
        assertTrue(original.componentLevels.isEmpty())
        assertEquals(LogLevel.DEBUG, updated.componentLevels["TestComponent"])
        assertNotSame(original, updated)
    }

    @Test
    fun `withComponentLevel should update existing component level`() {
        val original = LoggingSettings(
            componentLevels = mapOf("TestComponent" to LogLevel.INFO)
        )
        val updated = original.withComponentLevel("TestComponent", LogLevel.ERROR)
        
        assertEquals(LogLevel.INFO, original.componentLevels["TestComponent"])
        assertEquals(LogLevel.ERROR, updated.componentLevels["TestComponent"])
    }

    @Test
    fun `withoutComponentLevel should remove component override`() {
        val original = LoggingSettings(
            componentLevels = mapOf(
                "Component1" to LogLevel.DEBUG,
                "Component2" to LogLevel.ERROR
            )
        )
        val updated = original.withoutComponentLevel("Component1")
        
        assertEquals(2, original.componentLevels.size)
        assertEquals(1, updated.componentLevels.size)
        assertNull(updated.componentLevels["Component1"])
        assertEquals(LogLevel.ERROR, updated.componentLevels["Component2"])
    }

    @Test
    fun `withoutComponentLevel should handle non-existent component gracefully`() {
        val original = LoggingSettings(
            componentLevels = mapOf("Component1" to LogLevel.DEBUG)
        )
        val updated = original.withoutComponentLevel("NonExistent")
        
        assertEquals(original.componentLevels, updated.componentLevels)
    }

    @Test
    fun `validate should remove invalid component names`() {
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

    @Test
    fun `validate should preserve valid components`() {
        val validComponents = mapOf(
            LoggingComponents.FILE_EXTRACTION to LogLevel.DEBUG,
            LoggingComponents.NETWORK to LogLevel.ERROR,
            LoggingComponents.YOUTUBE_EXTRACTOR to LogLevel.WARN,
            LoggingComponents.MAIN_ACTIVITY to LogLevel.INFO
        )
        
        val settings = LoggingSettings(componentLevels = validComponents)
        val validated = settings.validate()
        
        assertEquals(validComponents, validated.componentLevels)
    }

    @Test
    fun `validate should handle empty component levels`() {
        val settings = LoggingSettings()
        val validated = settings.validate()
        
        assertEquals(settings, validated)
    }
}