package com.click.browser.engine

import androidx.compose.ui.graphics.Color

/**
 * Per-mode premium theme, adapted from the three approved design references:
 * - SIMPLE: clean white + royal blue
 * - DEVELOPER: dark + electric purple DevTools
 * - HACK: matrix cyber black + neon red/green HUD
 *
 * Every mode has a light and a dark variant; the global Day/Night toggle
 * in Settings picks the variant.
 */
data class ModeTheme(
    val mode: BrowserMode,
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val secondary: Color,
    val onBackground: Color,
    val onSurface: Color,
    val topBarBg: Color,
    val onTopBar: Color,
    val glow: Color,
    val modePillText: String
)

object ModeThemes {

    fun forMode(mode: BrowserMode, dark: Boolean): ModeTheme = when (mode) {
        BrowserMode.SIMPLE -> if (dark) simpleDark() else simpleLight()
        BrowserMode.DEVELOPER -> if (dark) developerDark() else developerLight()
        BrowserMode.HACK -> if (dark) hackDark() else hackLight()
    }

    private fun simpleLight() = ModeTheme(
        mode = BrowserMode.SIMPLE, dark = false,
        background = Color(0xFFF7FAFF),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFEAF1FF),
        primary = Color(0xFF1D4ED8),
        secondary = Color(0xFF3B82F6),
        onBackground = Color(0xFF0F1E3D),
        onSurface = Color(0xFF1E3A8A),
        topBarBg = Color(0xFFFFFFFF),
        onTopBar = Color(0xFF1D4ED8),
        glow = Color(0xFF3B82F6),
        modePillText = "SIMPLE MODE"
    )

    private fun simpleDark() = ModeTheme(
        mode = BrowserMode.SIMPLE, dark = true,
        background = Color(0xFF0A1128),
        surface = Color(0xFF101A36),
        surfaceVariant = Color(0xFF16224A),
        primary = Color(0xFF5B8CFF),
        secondary = Color(0xFF3B82F6),
        onBackground = Color(0xFFEAF1FF),
        onSurface = Color(0xFFDCE7FF),
        topBarBg = Color(0xFF0A1128),
        onTopBar = Color(0xFF5B8CFF),
        glow = Color(0xFF3B82F6),
        modePillText = "SIMPLE MODE"
    )

    private fun developerDark() = ModeTheme(
        mode = BrowserMode.DEVELOPER, dark = true,
        background = Color(0xFF0B0B12),
        surface = Color(0xFF14141F),
        surfaceVariant = Color(0xFF1C1C2A),
        primary = Color(0xFF8B5CF6),
        secondary = Color(0xFF7C3AED),
        onBackground = Color(0xFFF1EDFF),
        onSurface = Color(0xFFE2D9FF),
        topBarBg = Color(0xFF0B0B12),
        onTopBar = Color(0xFFFFFFFF),
        glow = Color(0xFF8B5CF6),
        modePillText = "DEVELOPER MODE"
    )

    private fun developerLight() = ModeTheme(
        mode = BrowserMode.DEVELOPER, dark = false,
        background = Color(0xFFFAF8FF),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF0E9FF),
        primary = Color(0xFF7C3AED),
        secondary = Color(0xFF8B5CF6),
        onBackground = Color(0xFF1E1B2E),
        onSurface = Color(0xFF2E2A4A),
        topBarBg = Color(0xFFFFFFFF),
        onTopBar = Color(0xFF7C3AED),
        glow = Color(0xFF8B5CF6),
        modePillText = "DEVELOPER MODE"
    )

    private fun hackDark() = ModeTheme(
        mode = BrowserMode.HACK, dark = true,
        background = Color(0xFF050505),
        surface = Color(0xFF0D0F0D),
        surfaceVariant = Color(0xFF141714),
        primary = Color(0xFFFF2D2D),
        secondary = Color(0xFF39FF14),
        onBackground = Color(0xFFF2FFF2),
        onSurface = Color(0xFFD6FFD6),
        topBarBg = Color(0xFF050505),
        onTopBar = Color(0xFFFF2D2D),
        glow = Color(0xFF39FF14),
        modePillText = "HACK MODE • ACTIVE"
    )

    private fun hackLight() = ModeTheme(
        mode = BrowserMode.HACK, dark = false,
        background = Color(0xFFF6FFF6),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE9F5E9),
        primary = Color(0xFFDC2626),
        secondary = Color(0xFF16A34A),
        onBackground = Color(0xFF0A0F0A),
        onSurface = Color(0xFF142114),
        topBarBg = Color(0xFFFFFFFF),
        onTopBar = Color(0xFFDC2626),
        glow = Color(0xFF16A34A),
        modePillText = "HACK MODE • ACTIVE"
    )
}
