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
import com.click.browser.engine.MenuCustomization
import com.click.browser.engine.ModeThemes
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MenuCustomizeSheetAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lastItemAndDoneRemainReachableAtLargeFontScaleOnCompactScreen() {
        var savedOrder: List<MenuCustomization.MenuItemId>? = null
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
                MenuCustomizeSheet(
                    theme = ModeThemes.forMode(BrowserMode.SIMPLE, dark = false),
                    initialOrder = emptyList(),
                    initialHidden = emptySet(),
                    onSave = { order, _ -> savedOrder = order },
                    onDismiss = { dismissInvoked = true }
                )
            }
        }

        val lastMenuItem = MenuCustomization.DEFAULT_ORDER.last().title
        composeRule.onNodeWithText(lastMenuItem)
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithText("Done")
            .assertIsNotDisplayed()
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue("Done should save the menu order", !savedOrder.isNullOrEmpty())
            assertTrue("Done should dismiss the sheet", dismissInvoked)
        }
    }
}
