package com.click.browser.ui.screens

import androidx.compose.ui.graphics.Color
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.ModeAddressBarStyle
import com.click.browser.engine.ModeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientGradientTest {
    private fun theme(primary: Color, secondary: Color) = ModeTheme(
        mode = BrowserMode.SIMPLE,
        dark = false,
        background = Color(0xFFF4F7FA),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE8EEF4),
        primary = primary,
        secondary = secondary,
        onBackground = Color(0xFF14202E),
        onSurface = Color(0xFF14202E),
        topBarBg = Color(0xFFFFFFFF),
        onTopBar = Color(0xFF14202E),
        modeAccent = primary,
        glow = primary,
        modePillText = "Simple",
        addressBarStyle = ModeAddressBarStyle.PILL_LARGE
    )

    @Test
    fun stopsArePrimaryAndSecondaryAtTheLockedTintAlpha() {
        val primary = Color(0xFF0E7C86)
        val secondary = Color(0xFF5B8DEF)
        val stops = ambientGradientStops(theme(primary, secondary))

        assertEquals(3, stops.size)
        assertEquals(primary.copy(alpha = AMBIENT_TINT_ALPHA), stops[0])
        assertEquals(Color.Transparent, stops[1])
        assertEquals(secondary.copy(alpha = AMBIENT_TINT_ALPHA), stops[2])
    }

    @Test
    fun tintStaysSubtleAndDriftStaysSlow() {
        // Prince's rule for this polish: "halka halka" — the wash must never
        // get strong enough to fight text, and the loop must stay slow.
        assertTrue(AMBIENT_TINT_ALPHA <= 0.15f)
        assertTrue(AMBIENT_GRADIENT_PERIOD_MS >= 10_000)
    }
}
