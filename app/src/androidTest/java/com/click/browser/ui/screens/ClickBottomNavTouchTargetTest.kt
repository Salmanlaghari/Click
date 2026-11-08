package com.click.browser.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
class ClickBottomNavTouchTargetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun navigationTargetsStayAccessibleAsLabelsScaleTo200Percent() {
        val fontScale = renderBottomNav()

        assertNavigationTargetsMeetMinimumSize()
        val defaultLabelHeight = composeRule
            .onNodeWithText("Home", useUnmergedTree = true)
            .fetchSemanticsNode().size.height

        composeRule.runOnIdle { fontScale.value = 2f }
        assertNavigationTargetsMeetMinimumSize()
        val enlargedLabelHeight = composeRule
            .onNodeWithText("Home", useUnmergedTree = true)
            .fetchSemanticsNode().size.height

        assertTrue(
            "Home label should grow at 200% font scale (default=$defaultLabelHeight, enlarged=$enlargedLabelHeight)",
            enlargedLabelHeight > defaultLabelHeight
        )
    }

    private fun renderBottomNav(): MutableState<Float> {
        val fontScale = mutableStateOf(1f)
        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(deviceDensity.density, fontScale.value)
            ) {
                ClickBottomNav(
                    theme = ModeThemes.forMode(BrowserMode.SIMPLE, dark = false),
                    tabCount = 3,
                    onHome = {},
                    onTabs = {},
                    onAi = {},
                    onBookmarks = {},
                    onMenu = {}
                )
            }
        }
        return fontScale
    }

    private fun assertNavigationTargetsMeetMinimumSize() {
        listOf("Home", "Tabs", "Bookmarks", "Menu").forEach { label ->
            val target = composeRule.onNodeWithContentDescription(label)
            target.assertWidthIsAtLeast(48.dp)
            target.assertHeightIsAtLeast(48.dp)
        }
    }
}
