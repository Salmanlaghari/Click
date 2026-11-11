package com.click.browser.extensions

import androidx.compose.ui.graphics.Color
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.LightThemeVariant
import com.click.browser.engine.ModeThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ExtensionManagerPaletteTest {
    @Test
    fun paletteMatchesTheLightCreamModeWithBronzeAndCharcoalRoles() {
        val cream = ModeThemes.forMode(
            mode = BrowserMode.ADVANCED,
            dark = false,
            lightVariant = LightThemeVariant.CREAM
        )
        val colors = ExtensionManagerPalette.colorScheme

        assertEquals(cream.background, colors.background)
        assertEquals(cream.surface, colors.surface)
        assertEquals(cream.primary, colors.primary)
        assertEquals(cream.secondary, colors.secondary)
        assertEquals(cream.onBackground, colors.onBackground)
        assertEquals(Color(0xFF6C6255), colors.onSurfaceVariant)
        assertEquals(Color(0xFF241D16), colors.onSecondary)
        assertEquals(cream.surface, colors.surfaceBright)
        assertEquals(Color(0xFFE8E1D6), colors.surfaceDim)
        assertEquals(cream.surface, colors.surfaceContainerLowest)
        assertEquals(Color(0xFFFCFAF5), colors.surfaceContainerLow)
        assertEquals(Color(0xFFF7F2E9), colors.surfaceContainer)
        assertEquals(cream.surfaceVariant, colors.surfaceContainerHigh)
        assertEquals(Color(0xFFECE5DA), colors.surfaceContainerHighest)
        assertNotEquals(Color(0xFF6750A4), colors.primary)
    }
}
