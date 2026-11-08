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
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.ModeThemes
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextScaleSheetAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resetAndDoneRemainReachableAtLargeFontScaleOnCompactScreen() {
        var resetInvoked = false
        var dismissInvoked = false

        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            val compactConfiguration = Configuration(LocalConfiguration.current).apply {
                screenHeightDp = 320
            }

            CompositionLocalProvider(
                LocalConfiguration provides compactConfiguration,
                LocalDensity provides Density(deviceDensity.density, fontScale = 2f)
            ) {
                TextScaleSheet(
                    theme = ModeThemes.forMode(BrowserMode.SIMPLE, dark = false),
                    host = "a-very-long-subdomain-for-accessibility.example.co.uk",
                    currentScale = 125,
                    globalScale = 100,
                    hostHasOverride = false,
                    onScaleChange = {},
                    onSaveForSite = {},
                    onResetGlobal = { resetInvoked = true },
                    onDismiss = { dismissInvoked = true }
                )
            }
        }

        composeRule.onNodeWithText("Done").assertIsNotDisplayed()
        composeRule.onNodeWithText("Reset (100%)")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertTrue("Reset action should remain usable", resetInvoked) }

        composeRule.onNodeWithText("Done")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertTrue("Done action should remain usable", dismissInvoked) }
    }
}
