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
    fun powerAccentIsDistinctWithoutOverridingTheSharedLightPalette() {
        val variants = LightThemeVariant.values().toList()

        variants.forEach { variant ->
            val simple = ModeThemes.forMode(BrowserMode.SIMPLE, dark = false, lightVariant = variant)
            val power = ModeThemes.forMode(BrowserMode.HACK, dark = false, lightVariant = variant)

            assertEquals(simple.background, power.background)
            assertEquals(simple.surface, power.surface)
            assertEquals(simple.surfaceVariant, power.surfaceVariant)
            assertEquals(simple.primary, power.primary)
            assertEquals(simple.secondary, power.secondary)
            assertEquals(simple.onBackground, power.onBackground)
            assertEquals(simple.onSurface, power.onSurface)
            assertEquals(simple.topBarBg, power.topBarBg)
            assertEquals(simple.onTopBar, power.onTopBar)
            assertEquals(simple.glow, power.glow)
            assertNotEquals("Power should have a distinct identity accent for ${variant.label}", simple.modeAccent, power.modeAccent)
            assertFalse("Power stays light for ${variant.label}", power.dark)
        }

        val powerAccents = variants.map {
            ModeThemes.forMode(BrowserMode.HACK, dark = false, lightVariant = it).modeAccent
        }.distinct()
        assertEquals("The Power identity accent is stable across global light palettes", 1, powerAccents.size)
    }
}
