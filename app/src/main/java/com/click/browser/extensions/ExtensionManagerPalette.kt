package com.click.browser.extensions

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.LightThemeVariant
import com.click.browser.engine.ModeThemes

/** Light-only palette for the separate GeckoView extension manager. Uses the
 *  ADVANCED profile's CREAM variant — the standard warm premium palette —
 *  so the manager looks the same regardless of per-mode identities. */
internal object ExtensionManagerPalette {
    private val cream = ModeThemes.forMode(
        mode = BrowserMode.ADVANCED,
        dark = false,
        lightVariant = LightThemeVariant.CREAM
    )

    val colorScheme = lightColorScheme(
        primary = cream.primary,
        onPrimary = cream.surface,
        primaryContainer = cream.surfaceVariant,
        onPrimaryContainer = cream.onBackground,
        inversePrimary = cream.glow,
        secondary = cream.secondary,
        onSecondary = Color(0xFF241D16),
        secondaryContainer = cream.surfaceVariant,
        onSecondaryContainer = cream.onBackground,
        tertiary = Color(0xFF476247),
        onTertiary = cream.surface,
        tertiaryContainer = Color(0xFFE3EBDD),
        onTertiaryContainer = Color(0xFF253128),
        background = cream.background,
        onBackground = cream.onBackground,
        surface = cream.surface,
        onSurface = cream.onSurface,
        surfaceVariant = cream.surfaceVariant,
        onSurfaceVariant = Color(0xFF6C6255),
        surfaceTint = cream.primary,
        surfaceBright = cream.surface,
        surfaceDim = Color(0xFFE8E1D6),
        surfaceContainerLowest = cream.surface,
        surfaceContainerLow = Color(0xFFFCFAF5),
        surfaceContainer = Color(0xFFF7F2E9),
        surfaceContainerHigh = cream.surfaceVariant,
        surfaceContainerHighest = Color(0xFFECE5DA),
        inverseSurface = cream.onSurface,
        inverseOnSurface = cream.background,
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFEFB),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        outline = Color(0xFF75695B),
        outlineVariant = Color(0xFFD4C8B8),
        scrim = Color(0x99000000)
    )
}
