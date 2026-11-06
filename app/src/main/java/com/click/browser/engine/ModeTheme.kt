package com.click.browser.engine

import androidx.compose.ui.graphics.Color

/**
 * Mode-aware light appearance. The selected light palette remains the user's base;
 * each browser profile adds a restrained identity tint and its own readable accent.
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
    /** Dark mode remains unsupported; each mode keeps the chosen light palette as its base. */
    @Suppress("UNUSED_PARAMETER")
    fun forMode(
        mode: BrowserMode,
        dark: Boolean,
        lightVariant: LightThemeVariant = LightThemeVariant.CREAM
    ): ModeTheme {
        val selectedPalette = when (lightVariant) {
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
        val palette = paletteForMode(selectedPalette, mode)
        val accent = when (mode) {
            BrowserMode.SIMPLE -> palette.primary
            BrowserMode.DEVELOPER -> Color(0xFF365F87)
            BrowserMode.HACK -> Color(0xFFB76F21)
            BrowserMode.ADVANCED -> Color(0xFF2F705B)
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
            modeAccent = accent,
            glow = palette.glow,
            modePillText = when (mode) {
                BrowserMode.SIMPLE -> "SIMPLE MODE"
                BrowserMode.DEVELOPER -> "DEVELOPER MODE"
                BrowserMode.HACK -> "POWER MODE • ACTIVE"
                BrowserMode.ADVANCED -> "ADVANCE MODE"
            }
        )
    }

    private fun paletteForMode(base: LightPalette, mode: BrowserMode): LightPalette = when (mode) {
        BrowserMode.SIMPLE -> base
        BrowserMode.DEVELOPER -> base.copy(
            background = blend(base.background, Color(0xFFE1ECF7), 0.68f),
            surface = blend(base.surface, Color(0xFFF5FAFF), 0.26f),
            surfaceVariant = blend(base.surfaceVariant, Color(0xFFD7E6F4), 0.46f),
            primary = Color(0xFF365F87),
            secondary = Color(0xFF638AB2),
            onBackground = Color(0xFF243647),
            onSurface = Color(0xFF2B3D50),
            topBarBg = blend(base.topBarBg, Color(0xFFEAF2FB), 0.24f),
            onTopBar = Color(0xFF355675),
            glow = Color(0xFFB3CDE4)
        )
        BrowserMode.HACK -> base.copy(
            background = blend(base.background, Color(0xFFF7E7D0), 0.68f),
            surface = blend(base.surface, Color(0xFFFFF8EB), 0.26f),
            surfaceVariant = blend(base.surfaceVariant, Color(0xFFEED9BC), 0.46f),
            primary = Color(0xFF8D5A22),
            secondary = Color(0xFFB77D3B),
            onBackground = Color(0xFF3B2B1A),
            onSurface = Color(0xFF45331F),
            topBarBg = blend(base.topBarBg, Color(0xFFFFF3DF), 0.24f),
            onTopBar = Color(0xFF67461F),
            glow = Color(0xFFDFC29B)
        )
        BrowserMode.ADVANCED -> base.copy(
            background = blend(base.background, Color(0xFFDFECE4), 0.68f),
            surface = blend(base.surface, Color(0xFFF3FAF5), 0.26f),
            surfaceVariant = blend(base.surfaceVariant, Color(0xFFD1E4D8), 0.46f),
            primary = Color(0xFF2F705B),
            secondary = Color(0xFF60977D),
            onBackground = Color(0xFF263A30),
            onSurface = Color(0xFF2D4337),
            topBarBg = blend(base.topBarBg, Color(0xFFEEF7F1), 0.24f),
            onTopBar = Color(0xFF315E4E),
            glow = Color(0xFFB2D0BE)
        )
    }

    private fun blend(from: Color, to: Color, amount: Float): Color {
        val fraction = amount.coerceIn(0f, 1f)
        val inverse = 1f - fraction
        return Color(
            red = from.red * inverse + to.red * fraction,
            green = from.green * inverse + to.green * fraction,
            blue = from.blue * inverse + to.blue * fraction,
            alpha = from.alpha * inverse + to.alpha * fraction
        )
    }
}
