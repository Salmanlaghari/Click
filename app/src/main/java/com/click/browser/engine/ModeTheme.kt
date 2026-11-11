package com.click.browser.engine

import androidx.compose.ui.graphics.Color

/**
 * How a mode's address bar / chrome is styled (Prince's 2026-11-11 rule:
 * every mode must LOOK different — bar style, colours, visible features).
 */
enum class ModeAddressBarStyle {
    /** Simple: extra-tall pill at the top, minimal buttons, large touch targets. */
    PILL_LARGE,
    /** Advance: the standard premium floating bar. */
    STANDARD,
    /** Power: desktop-style chrome (tab strip stays visible while browsing). */
    DESKTOP,
    /** Developer: dark technical bar with extra info (dev tools entry). */
    TECHNICAL
}

/**
 * Mode-aware appearance. Since Prince's 2026-11-11 directive each mode has a
 * genuinely distinct visual identity (previously all modes shared one light
 * palette with near-identical bronze accents, which he rejected):
 * - SIMPLE: clean light sky/teal, top pill bar, minimal.
 * - DEVELOPER: dark technical (the one dark mode), green/blue accents.
 * - HACK (Power): cool slate desktop chrome, indigo accent, tab strip.
 * - ADVANCED: the previous warm-cream premium look, still driven by the
 *   selectable [LightThemeVariant] palettes.
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
    val modePillText: String,
    val addressBarStyle: ModeAddressBarStyle
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
    /**
     * Per-mode palettes (Prince, 2026-11-11: modes must be visibly different).
     * Only ADVANCED still follows the selectable warm [LightThemeVariant]
     * palettes; the other modes have fixed identities so switching modes is
     * obvious at a glance. The legacy [dark] input is ignored — darkness is
     * now a property of the Developer mode itself.
     */
    @Suppress("UNUSED_PARAMETER")
    fun forMode(
        mode: BrowserMode,
        dark: Boolean,
        lightVariant: LightThemeVariant = LightThemeVariant.CREAM
    ): ModeTheme {
        when (mode) {
            BrowserMode.SIMPLE -> return ModeTheme(
                mode = mode,
                dark = false,
                background = Color(0xFFF4F9FC),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFE1EDF5),
                primary = Color(0xFF0B6E8C),
                secondary = Color(0xFF2563EB),
                onBackground = Color(0xFF16303C),
                onSurface = Color(0xFF1D3A47),
                topBarBg = Color(0xFFFFFFFF),
                onTopBar = Color(0xFF17455C),
                modeAccent = Color(0xFF0B6E8C),
                glow = Color(0xFF7FB8D8),
                modePillText = "SIMPLE MODE",
                addressBarStyle = ModeAddressBarStyle.PILL_LARGE
            )
            BrowserMode.DEVELOPER -> return ModeTheme(
                mode = mode,
                dark = true,
                background = Color(0xFF0D1117),
                surface = Color(0xFF151B24),
                surfaceVariant = Color(0xFF1E2732),
                primary = Color(0xFF3FB950),
                secondary = Color(0xFF58A6FF),
                onBackground = Color(0xFFE6EDF3),
                onSurface = Color(0xFFD7E0E8),
                topBarBg = Color(0xFF0A0E14),
                onTopBar = Color(0xFFE6EDF3),
                modeAccent = Color(0xFF3FB950),
                glow = Color(0xFF1F6FEB),
                modePillText = "DEVELOPER MODE",
                addressBarStyle = ModeAddressBarStyle.TECHNICAL
            )
            BrowserMode.HACK -> return ModeTheme(
                mode = mode,
                dark = false,
                background = Color(0xFFEDF0F5),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFDCE3EE),
                primary = Color(0xFF4338CA),
                secondary = Color(0xFF0F766E),
                onBackground = Color(0xFF1C2733),
                onSurface = Color(0xFF243447),
                topBarBg = Color(0xFFDFE6F0),
                onTopBar = Color(0xFF1E293B),
                modeAccent = Color(0xFF4338CA),
                glow = Color(0xFFA5B4FC),
                modePillText = "POWER MODE • ACTIVE",
                addressBarStyle = ModeAddressBarStyle.DESKTOP
            )
            BrowserMode.ADVANCED -> Unit // handled below with the variant palettes
        }
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
        // Only ADVANCED reaches here (the other modes returned above): it
        // keeps the original warm-bronze premium accent on the selected
        // variant palette.
        val accent = ModeAccent(
            primary = Color(0xFF594738),
            secondary = Color(0xFF80684E)
        )

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
            modePillText = "ADVANCE MODE",
            addressBarStyle = ModeAddressBarStyle.STANDARD
        )
    }
}
