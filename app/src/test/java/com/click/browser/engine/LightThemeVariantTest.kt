package com.click.browser.engine

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class LightThemeVariantTest {
    @Test
    fun variantsHaveUniqueStableIdsAndLabels() {
        val variants = LightThemeVariant.values().toList()

        assertEquals(
            listOf(LightThemeVariant.CREAM, LightThemeVariant.SAGE, LightThemeVariant.SKY, LightThemeVariant.ROSE),
            variants
        )
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
    fun allSelectableSurfacePalettesRemainWarmLightAndDistinct() {
        // The selectable warm variants now drive the ADVANCED profile (the
        // standard premium look); other modes have fixed identities.
        val variants = LightThemeVariant.values().toList()
        val themes = variants.map { variant ->
            ModeThemes.forMode(BrowserMode.ADVANCED, dark = true, lightVariant = variant)
        }
        val expectedBackgrounds = listOf(
            Color(0xFFFBF8F1),
            Color(0xFFF8F7F0),
            Color(0xFFF7F7F2),
            Color(0xFFFAF6F0)
        )
        val expectedSurfaces = listOf(
            Color(0xFFFFFEFB),
            Color(0xFFFFFEFA),
            Color(0xFFFFFEFA),
            Color(0xFFFFFEFB)
        )
        val expectedSurfaceVariants = listOf(
            Color(0xFFF2EBDD),
            Color(0xFFEFEEE4),
            Color(0xFFEEEDE5),
            Color(0xFFF1E8DE)
        )

        assertEquals(expectedBackgrounds, themes.map { it.background })
        assertEquals(expectedSurfaces, themes.map { it.surface })
        assertEquals(expectedSurfaceVariants, themes.map { it.surfaceVariant })
        assertEquals(variants.size, themes.map { it.surfaceVariant }.distinct().size)
        themes.forEach { theme ->
            assertFalse("${theme.mode} remains light", theme.dark)
            assertTrue(theme.background.red > 0.9f)
            assertTrue(theme.background.green > 0.9f)
            assertTrue(theme.background.blue > 0.9f)
            assertTrue(theme.surface.red > 0.97f)
            assertTrue(theme.surface.green > 0.97f)
            assertTrue(theme.surface.blue > 0.97f)
            assertTrue(theme.surfaceVariant.red > 0.9f)
            assertTrue(theme.surfaceVariant.green > 0.88f)
            assertTrue(theme.surfaceVariant.blue > 0.85f)
            assertEquals(Color(0xFF302A22), theme.onBackground)
            assertEquals(Color(0xFF393228), theme.onSurface)
        }
    }

    @Test
    fun everyModeHasAGenuinelyDistinctVisualIdentity() {
        // Prince's 2026-11-11 directive supersedes the old "all modes share
        // one light palette" design: each mode now has its own palette,
        // accent pair, address-bar style and (for Developer) dark surfaces.
        val modes = BrowserMode.values().toList()
        val themes = modes.map { mode ->
            ModeThemes.forMode(mode, dark = false, lightVariant = LightThemeVariant.CREAM)
        }
        val expectedBackgrounds = listOf(
            Color(0xFFF4F9FC), // Simple: clean sky light
            Color(0xFF0D1117), // Developer: dark technical
            Color(0xFFEDF0F5), // Power: cool slate desktop
            Color(0xFFFBF8F1)  // Advance: warm cream (selected variant)
        )
        val expectedPrimaries = listOf(
            Color(0xFF0B6E8C),
            Color(0xFF3FB950),
            Color(0xFF4338CA),
            Color(0xFF594738)
        )
        val expectedSecondaries = listOf(
            Color(0xFF2563EB),
            Color(0xFF58A6FF),
            Color(0xFF0F766E),
            Color(0xFF80684E)
        )
        val expectedStyles = listOf(
            ModeAddressBarStyle.PILL_LARGE,
            ModeAddressBarStyle.TECHNICAL,
            ModeAddressBarStyle.DESKTOP,
            ModeAddressBarStyle.STANDARD
        )

        assertEquals(expectedBackgrounds, themes.map { it.background })
        assertEquals(expectedPrimaries, themes.map { it.primary })
        assertEquals(expectedSecondaries, themes.map { it.secondary })
        assertEquals(expectedStyles, themes.map { it.addressBarStyle })
        assertEquals(modes.size, themes.map { it.background }.distinct().size)
        assertEquals(modes.size, themes.map { it.primary }.distinct().size)
        assertEquals(modes.size, themes.map { it.modePillText }.distinct().size)

        modes.zip(themes).forEach { (mode, theme) ->
            assertEquals(mode, theme.mode)
            // Developer is the one dark mode; the rest stay light.
            if (mode == BrowserMode.DEVELOPER) {
                assertTrue("Developer is dark", theme.dark)
            } else {
                assertFalse("$mode stays light", theme.dark)
            }
            assertEquals(theme.primary, theme.modeAccent)
            assertTrue("$mode primary contrast", contrastRatio(theme.primary, theme.background) >= 4.5)
            assertTrue("$mode secondary contrast", contrastRatio(theme.secondary, theme.background) >= 4.5)
            assertTrue(theme.modePillText.isNotBlank())
        }
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val lighter = maxOf(relativeLuminance(foreground), relativeLuminance(background))
        val darker = minOf(relativeLuminance(foreground), relativeLuminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearize(color.red) + 0.7152 * linearize(color.green) + 0.0722 * linearize(color.blue)

    private fun linearize(channel: Float): Double {
        val value = channel.toDouble()
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
}
