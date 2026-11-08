package com.click.browser.ui.screens

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.ModeThemes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LongPressMenuSheetAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lowerLinkAndPlaylistActionsRemainReachableAtLargeFontScale() {
        val linkUrl = "https://media.example.test/download.mp4"
        val imageUrl = "https://media.example.test/cover.png"
        var playlistUrl: String? = null
        var sharedLinkUrl: String? = null

        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            val compactConfiguration = Configuration(LocalConfiguration.current).apply {
                screenHeightDp = 320
            }

            CompositionLocalProvider(
                LocalConfiguration provides compactConfiguration,
                LocalDensity provides Density(deviceDensity.density, fontScale = 2f)
            ) {
                LongPressMenuSheet(
                    theme = ModeThemes.forMode(BrowserMode.SIMPLE, dark = false),
                    linkUrl = linkUrl,
                    imageUrl = imageUrl,
                    onOpenInNewTab = {},
                    onCopyLink = {},
                    onShareLink = { sharedLinkUrl = it },
                    onSaveImage = {},
                    onCopyImageUrl = {},
                    onShareImage = {},
                    onAddToPlaylist = { playlistUrl = it },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("Add to Playlist")
            .assertIsNotDisplayed()
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithText("Share link")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(linkUrl, playlistUrl)
            assertEquals(linkUrl, sharedLinkUrl)
        }
    }
}
