package com.click.browser.engine

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
    fun everyModeHasItsOwnLightIdentityAndHonorsTheSelectedPalette() {
        val variants = LightThemeVariant.values().toList()

        variants.forEach { variant ->
            val themes = BrowserMode.values().map { mode ->
                ModeThemes.forMode(mode, dark = false, lightVariant = variant)
            }
            assertEquals("Each mode needs a distinct background for ${variant.label}", themes.size, themes.map { it.background }.distinct().size)
            assertEquals("Each mode needs a distinct accent for ${variant.label}", themes.size, themes.map { it.modeAccent }.distinct().size)
            assertEquals("Each mode needs a distinct label for ${variant.label}", themes.size, themes.map { it.modePillText }.distinct().size)
            themes.forEach { theme -> assertFalse("${theme.mode} stays light", theme.dark) }
        }

        BrowserMode.values().forEach { mode ->
            val backgrounds = variants.map {
                ModeThemes.forMode(mode, dark = false, lightVariant = it).background
            }.distinct()
            assertEquals("${mode} should still reflect the user's selected palette", variants.size, backgrounds.size)
        }
    }
}
