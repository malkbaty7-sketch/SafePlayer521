package com.example.ui.glass

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidGlassControllerTest {

    @Test
    fun `blurRadius clamps to 0f to 35f range`() {
        val controller = LiquidGlassController()

        controller.setBlurRadius(28.5f)
        assertEquals(28.5f, controller.currentBlurRadius, 0.001f)
        assertEquals("CUSTOM", controller.config.value.activePresetId)

        controller.setBlurRadius(-10f)
        assertEquals(0f, controller.currentBlurRadius, 0.001f)

        controller.setBlurRadius(60f)
        assertEquals(35f, controller.currentBlurRadius, 0.001f)
    }

    @Test
    fun `glassOpacity and surfaceAlpha clamp to 0f to 1f range`() {
        val controller = LiquidGlassController()

        controller.setGlassOpacity(0.65f)
        assertEquals(0.65f, controller.currentGlassOpacity, 0.001f)
        assertEquals(0.65f, controller.currentSurfaceAlpha, 0.001f)

        controller.setSurfaceAlpha(-0.4f)
        assertEquals(0.0f, controller.currentGlassOpacity, 0.001f)

        controller.setSurfaceAlpha(1.8f)
        assertEquals(1.0f, controller.currentGlassOpacity, 0.001f)
    }

    @Test
    fun `setTintColor and applyPreset update state accurately`() {
        val controller = LiquidGlassController()
        val customTint = Color(0xFF123456)

        controller.setTintColor(customTint)
        assertEquals(customTint, controller.currentTintColor)

        controller.applyPreset(LiquidGlassPreset.WARM_AMBER_LUXE)
        assertEquals(LiquidGlassPreset.WARM_AMBER_LUXE.id, controller.config.value.activePresetId)
        assertEquals(LiquidGlassPreset.WARM_AMBER_LUXE.blurRadius, controller.currentBlurRadius, 0.001f)
        assertEquals(LiquidGlassPreset.WARM_AMBER_LUXE.glassOpacity, controller.currentGlassOpacity, 0.001f)
        assertEquals(LiquidGlassPreset.WARM_AMBER_LUXE.tintColor, controller.currentTintColor)

        controller.resetToDefaults()
        assertEquals(LiquidGlassPreset.FROSTED_OBSIDIAN.id, controller.config.value.activePresetId)
    }
}
