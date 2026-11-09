package com.click.browser.engine

import androidx.compose.ui.graphics.Color

/**
 * Mode-aware, light-only appearance. Browser modes keep distinct warm accents while sharing
 * the selected light surface palette.
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
    val modeAccent: Color,
    val glow: Color,
    val modePillText: String
)

enum class LightThemeVariant(val id: String, val label: String) {
    CREAM("cream", "Cream"),
    SAGE("sage", "Sage"),
    SKY("sky", "Sky"),
    ROSE("rose", "Rose");

    companion object {
        /** Unknown or legacy palette IDs safely fall back to the original Cream palette. */
        fun fromId(id: String?): LightThemeVariant =
            values().firstOrNull { it.id == id?.trim()?.lowercase() } ?: CREAM
    }
}

private data class LightPalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val onSurface: Color,
    val topBarBg: Color,
    val onTopBar: Color,
    val glow: Color
)

private data class ModeAccent(
    val primary: Color,
    val secondary: Color
)

object ModeThemes {
    /** All selectable palettes remain light and warm; mode accents stay in the bronze/brown family. */
    @Suppress("UNUSED_PARAMETER")
    fun forMode(
        mode: BrowserMode,
        dark: Boolean,
        lightVariant: LightThemeVariant = LightThemeVariant.CREAM
    ): ModeTheme {
        val palette = when (lightVariant) {
            LightThemeVariant.CREAM -> LightPalette(
                background = Color(0xFFFBF8F1),
                surface = Color(0xFFFFFEFB),
                surfaceVariant = Color(0xFFF2EBDD),
                onBackground = Color(0xFF302A22),
                onSurface = Color(0xFF393228),
                topBarBg = Color(0xFFFFFEFB),
                onTopBar = Color(0xFF514534),
                glow = Color(0xFFC7AE8A)
            )
            // Keep the existing saved IDs/options while bringing their surfaces into the warm-ivory family.
            LightThemeVariant.SAGE -> LightPalette(
                background = Color(0xFFF8F7F0),
                surface = Color(0xFFFFFEFA),
                surfaceVariant = Color(0xFFEFEEE4),
                onBackground = Color(0xFF302A22),
                onSurface = Color(0xFF393228),
                topBarBg = Color(0xFFFFFEFA),
                onTopBar = Color(0xFF514534),
                glow = Color(0xFFC5B492)
            )
            LightThemeVariant.SKY -> LightPalette(
                background = Color(0xFFF7F7F2),
                surface = Color(0xFFFFFEFA),
                surfaceVariant = Color(0xFFEEEDE5),
                onBackground = Color(0xFF302A22),
                onSurface = Color(0xFF393228),
                topBarBg = Color(0xFFFFFEFA),
                onTopBar = Color(0xFF514534),
                glow = Color(0xFFC2B69A)
            )
            LightThemeVariant.ROSE -> LightPalette(
                background = Color(0xFFFAF6F0),
                surface = Color(0xFFFFFEFB),
                surfaceVariant = Color(0xFFF1E8DE),
                onBackground = Color(0xFF302A22),
                onSurface = Color(0xFF393228),
                topBarBg = Color(0xFFFFFEFB),
                onTopBar = Color(0xFF514534),
                glow = Color(0xFFD0B098)
            )
        }
        val accent = when (mode) {
            BrowserMode.SIMPLE -> ModeAccent(
                primary = Color(0xFF66533F),
                secondary = Color(0xFF8B6A46)
            )
            BrowserMode.DEVELOPER -> ModeAccent(
                primary = Color(0xFF70563A),
                secondary = Color(0xFF8D6B46)
            )
            BrowserMode.HACK -> ModeAccent(
                primary = Color(0xFF81552F),
                secondary = Color(0xFF935F33)
            )
            BrowserMode.ADVANCED -> ModeAccent(
                primary = Color(0xFF594738),
                secondary = Color(0xFF80684E)
            )
        }

        return ModeTheme(
            mode = mode,
            dark = false,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceVariant,
            primary = accent.primary,
            secondary = accent.secondary,
            onBackground = palette.onBackground,
            onSurface = palette.onSurface,
            topBarBg = palette.topBarBg,
            onTopBar = palette.onTopBar,
            modeAccent = accent.primary,
            glow = palette.glow,
            modePillText = when (mode) {
                BrowserMode.SIMPLE -> "SIMPLE MODE"
                BrowserMode.DEVELOPER -> "DEVELOPER MODE"
                BrowserMode.HACK -> "POWER MODE • ACTIVE"
                BrowserMode.ADVANCED -> "ADVANCE MODE"
            }
        )
    }
}
