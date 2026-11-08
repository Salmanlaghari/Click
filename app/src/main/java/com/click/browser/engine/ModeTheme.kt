package com.click.browser.engine

import androidx.compose.ui.graphics.Color

/**
 * Mode-aware light appearance. The selected light palette stays consistent across all modes;
 * labels and capabilities provide mode identity without changing the color system.
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
    val primary: Color,
    val secondary: Color,
    val onBackground: Color,
    val onSurface: Color,
    val topBarBg: Color,
    val onTopBar: Color,
    val glow: Color
)

object ModeThemes {
    /** Dark mode remains unsupported; every mode uses the selected light palette without a mode tint. */
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
                primary = Color(0xFF66533F),
                secondary = Color(0xFFAA865C),
                onBackground = Color(0xFF302A22),
                onSurface = Color(0xFF393228),
                topBarBg = Color(0xFFFFFEFB),
                onTopBar = Color(0xFF514534),
                glow = Color(0xFFC7AE8A)
            )
            LightThemeVariant.SAGE -> LightPalette(
                background = Color(0xFFF2F6EF),
                surface = Color(0xFFFFFEFC),
                surfaceVariant = Color(0xFFE3ECDF),
                primary = Color(0xFF46664B),
                secondary = Color(0xFF78906B),
                onBackground = Color(0xFF263329),
                onSurface = Color(0xFF303A31),
                topBarBg = Color(0xFFFFFEFC),
                onTopBar = Color(0xFF3E5A42),
                glow = Color(0xFFB7CBB1)
            )
            LightThemeVariant.SKY -> LightPalette(
                background = Color(0xFFF0F6FC),
                surface = Color(0xFFFEFFFF),
                surfaceVariant = Color(0xFFE0EBF5),
                primary = Color(0xFF3C6485),
                secondary = Color(0xFF6E9AB7),
                onBackground = Color(0xFF243240),
                onSurface = Color(0xFF2C3D4C),
                topBarBg = Color(0xFFFEFFFF),
                onTopBar = Color(0xFF355B79),
                glow = Color(0xFFB5D2E6)
            )
            LightThemeVariant.ROSE -> LightPalette(
                background = Color(0xFFFAF1F3),
                surface = Color(0xFFFFFEFE),
                surfaceVariant = Color(0xFFF1E1E5),
                primary = Color(0xFF894D5B),
                secondary = Color(0xFFB17683),
                onBackground = Color(0xFF35252A),
                onSurface = Color(0xFF403036),
                topBarBg = Color(0xFFFFFEFE),
                onTopBar = Color(0xFF784450),
                glow = Color(0xFFDAB3BC)
            )
        }

        return ModeTheme(
            mode = mode,
            dark = false,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceVariant,
            primary = palette.primary,
            secondary = palette.secondary,
            onBackground = palette.onBackground,
            onSurface = palette.onSurface,
            topBarBg = palette.topBarBg,
            onTopBar = palette.onTopBar,
            modeAccent = palette.primary,
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
