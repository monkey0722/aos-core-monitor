package com.aoscoremonitor.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks that a reading's state reaches a screen reader.
 *
 * The components this replaced signalled state with color alone — a green tick and a red cross
 * whose `contentDescription` said the same thing either way — so a TalkBack user heard
 * "Hardware-backed Keystore" without ever hearing whether it was supported.
 */
@RunWith(AndroidJUnit4::class)
class StatusPresentationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun statusRowAnnouncesItsState() {
        composeRule.setContent {
            AOSCoreMonitorTheme(dynamicColor = false) {
                StatusRow(
                    label = "StrongBox Keystore",
                    status = ReadingStatus.Problem,
                    statusDescription = "Not supported"
                )
            }
        }

        composeRule.onNodeWithText("StrongBox Keystore").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Not supported").assertIsDisplayed()
    }

    @Test
    fun infoCardIconIsDecorativeAndSilent() {
        composeRule.setContent {
            AOSCoreMonitorTheme(dynamicColor = false) {
                InfoCard(title = "Battery", value = "100%", icon = Icons.Default.Battery6Bar)
            }
        }

        composeRule.onNodeWithText("Battery").assertIsDisplayed()
        composeRule.onNodeWithText("100%").assertIsDisplayed()

        // The icon restates the title, so describing it would make TalkBack say "Battery" twice.
        val describedAsTitle = composeRule.onAllNodesWithContentDescription("Battery").fetchSemanticsNodes()
        assertTrue("The subject icon should not be described", describedAsTitle.isEmpty())
    }
}
