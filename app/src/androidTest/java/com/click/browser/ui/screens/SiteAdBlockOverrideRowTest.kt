package com.click.browser.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.BrowserTabEngine
import com.click.browser.engine.FeatureId
import com.click.browser.engine.ModeCapabilityRegistry
import com.click.browser.engine.ModeThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SiteAdBlockOverrideRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun geckoShowsUnavailableReasonWithoutEditableSiteOverride() {
        val capability = ModeCapabilityRegistry.availability(
            feature = FeatureId.ADBLOCK,
            mode = BrowserMode.SIMPLE,
            engine = BrowserTabEngine.GECKOVIEW
        )
        assertTrue(capability.visible)
        assertFalse(capability.enabled)
        val explanation = requireNotNull(capability.explanation)
        var changed = false

        composeRule.setContent {
            MaterialTheme {
                SiteAdBlockOverrideRow(
                    theme = ModeThemes.forMode(BrowserMode.SIMPLE, dark = false),
                    value = false,
                    globalAdBlock = true,
                    enabled = capability.enabled,
                    unavailableExplanation = explanation,
                    onChange = { changed = true }
                )
            }
        }

        composeRule.onNodeWithText("Intrusive ads").assertIsDisplayed()
        composeRule.onNodeWithText("Unavailable").assertIsDisplayed()
        composeRule.onNodeWithText(explanation).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Default").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("On").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Off").fetchSemanticsNodes().isEmpty())
        assertTrue(
            composeRule.onAllNodesWithText("Using global default: Blocked")
                .fetchSemanticsNodes()
                .isEmpty()
        )
        composeRule.runOnIdle { assertFalse(changed) }
    }

    @Test
    fun webViewKeepsEditableSiteOverride() {
        val capability = ModeCapabilityRegistry.availability(
            feature = FeatureId.ADBLOCK,
            mode = BrowserMode.SIMPLE,
            engine = BrowserTabEngine.WEBVIEW
        )
        assertTrue(capability.visible)
        assertTrue(capability.enabled)
        var selected: Boolean? = null

        composeRule.setContent {
            MaterialTheme {
                SiteAdBlockOverrideRow(
                    theme = ModeThemes.forMode(BrowserMode.SIMPLE, dark = false),
                    value = null,
                    globalAdBlock = true,
                    enabled = capability.enabled,
                    unavailableExplanation = capability.explanation,
                    onChange = { selected = it }
                )
            }
        }

        composeRule.onNodeWithText("Default").assertIsDisplayed()
        composeRule.onNodeWithText("On").performClick()
        composeRule.runOnIdle { assertEquals(true, selected) }
    }
}
