package com.click.browser.engine

import androidx.compose.ui.graphics.Color

/** Shared light appearance used by every Click browsing profile. */
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

enum class LightThemeVariant(val id: String, val label: String) {
    CREAM("cream", "Cream");

    companion object {
        /** Old saved palette IDs are migrated to the single supported cream palette. */
        @Suppress("UNUSED_PARAMETER")
        fun fromId(id: String?): LightThemeVariant = CREAM
    }
}

object ModeThemes {
    /** Theme mode and legacy palette arguments are ignored: Click stays light in every profile. */
    @Suppress("UNUSED_PARAMETER")
    fun forMode(
        mode: BrowserMode,
        dark: Boolean,
        lightVariant: LightThemeVariant = LightThemeVariant.CREAM
    ): ModeTheme = ModeTheme(
        mode = mode,
        dark = false,
        background = Color(0xFFFBF8F1),
        surface = Color(0xFFFFFEFB),
        surfaceVariant = Color(0xFFF2EBDD),
        primary = Color(0xFF66533F),
        secondary = Color(0xFFAA865C),
        onBackground = Color(0xFF302A22),
        onSurface = Color(0xFF393228),
        topBarBg = Color(0xFFFFFEFB),
        onTopBar = Color(0xFF514534),
        glow = Color(0xFFC7AE8A),
        modePillText = when (mode) {
            BrowserMode.SIMPLE -> "SIMPLE MODE"
            BrowserMode.DEVELOPER -> "DEVELOPER MODE"
            BrowserMode.HACK -> "POWER MODE • ACTIVE"
            BrowserMode.ADVANCED -> "ADVANCE MODE"
        }
    )
}
