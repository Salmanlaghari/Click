package com.click.browser.engine

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightThemeVariantTest {
    @Test
    fun variantsHaveUniqueStableIdsAndLabels() {
        val variants = LightThemeVariant.values().toList()

        assertTrue("Expected Cream plus at least three additional palettes", variants.size >= 4)
        assertEquals(variants.size, variants.map { it.id }.distinct().size)
        assertEquals(variants.size, variants.map { it.label }.distinct().size)
        variants.forEach { assertEquals(it, LightThemeVariant.fromId(it.id)) }
    }

    @Test
    fun unknownPaletteIdsFallBackToCream() {
        assertEquals(LightThemeVariant.CREAM, LightThemeVariant.fromId(null))
        assertEquals(LightThemeVariant.CREAM, LightThemeVariant.fromId("legacy-palette"))
        assertEquals(LightThemeVariant.SAGE, LightThemeVariant.fromId(" SAGE "))
    }

    @Test
    fun autoRotationAdvancesExactlyOnePaletteAndWraps() {
        val variants = LightThemeVariant.values()

        variants.forEachIndexed { index, variant ->
            val expectedNext = variants[(index + 1) % variants.size]
            assertEquals(expectedNext, ThemeController.variantForLaunch(variant.id, autoRotate = true))
        }
    }

    @Test
    fun rotationOffKeepsTheLastAppliedPalette() {
        assertEquals(
            LightThemeVariant.SKY,
            ThemeController.variantForLaunch("sky", autoRotate = false)
        )
        assertEquals(
            LightThemeVariant.CREAM,
            ThemeController.variantForLaunch(null, autoRotate = false)
        )
    }

    @Test
    fun eachPaletteIsDistinctAndRemainsLightAcrossEveryMode() {
        val variants = LightThemeVariant.values().toList()
        val primaryColors = variants.map { ModeThemes.forMode(BrowserMode.SIMPLE, dark = true, lightVariant = it).primary }
        assertEquals(variants.size, primaryColors.distinct().size)

        BrowserMode.values().forEach { mode ->
            variants.forEach { variant ->
                val theme = ModeThemes.forMode(mode, dark = true, lightVariant = variant)
                assertEquals(mode, theme.mode)
                assertFalse("$mode must remain light for ${variant.label}", theme.dark)
                assertNotEquals(theme.background, theme.onSurface)
                assertTrue(theme.modePillText.isNotBlank())
            }
        }
    }

    @Test
    fun everyModeSharesTheSelectedLightPaletteAndKeepsDistinctLabels() {
        val variants = LightThemeVariant.values().toList()
        val modes = BrowserMode.values().toList()

        variants.forEach { variant ->
            val themes = modes.map { mode ->
                ModeThemes.forMode(mode, dark = false, lightVariant = variant)
            }
            val selectedPalette = themes.first()
            themes.forEach { theme ->
                assertEquals("${theme.mode} shares ${variant.label} background", selectedPalette.background, theme.background)
                assertEquals("${theme.mode} shares ${variant.label} surface", selectedPalette.surface, theme.surface)
                assertEquals("${theme.mode} shares ${variant.label} surface variant", selectedPalette.surfaceVariant, theme.surfaceVariant)
                assertEquals("${theme.mode} shares ${variant.label} primary", selectedPalette.primary, theme.primary)
                assertEquals("${theme.mode} shares ${variant.label} secondary", selectedPalette.secondary, theme.secondary)
                assertEquals("${theme.mode} shares ${variant.label} background text", selectedPalette.onBackground, theme.onBackground)
                assertEquals("${theme.mode} shares ${variant.label} surface text", selectedPalette.onSurface, theme.onSurface)
                assertEquals("${theme.mode} shares ${variant.label} toolbar", selectedPalette.topBarBg, theme.topBarBg)
                assertEquals("${theme.mode} shares ${variant.label} toolbar text", selectedPalette.onTopBar, theme.onTopBar)
                assertEquals("${theme.mode} shares ${variant.label} glow", selectedPalette.glow, theme.glow)
                assertEquals("${theme.mode} accent uses the selected palette", theme.primary, theme.modeAccent)
                assertFalse("${theme.mode} stays light", theme.dark)
            }
            assertEquals("Mode labels remain distinct for ${variant.label}", modes.size, themes.map { it.modePillText }.distinct().size)
        }

        modes.forEach { mode ->
            val cream = ModeThemes.forMode(mode, dark = false, lightVariant = LightThemeVariant.CREAM)
            assertEquals("$mode keeps the warm cream background", Color(0xFFFBF8F1), cream.background)
            assertEquals("$mode keeps the bronze primary accent", Color(0xFF66533F), cream.primary)
            assertEquals("$mode keeps charcoal body text", Color(0xFF302A22), cream.onBackground)
            assertEquals("$mode uses bronze rather than a mode tint", cream.primary, cream.modeAccent)
        }
    }
}
