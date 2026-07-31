package com.aoscoremonitor.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aoscoremonitor.MainActivity
import com.aoscoremonitor.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the navigation behaviour the hand-rolled `when (currentScreen)` did not have.
 *
 * [systemBackReturnsHome] is the regression test that matters: before Navigation 3 there was no
 * back handler at all, so backing out of a detail screen closed the app.
 */
@RunWith(AndroidJUnit4::class)
class MonitorNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeListsEveryDestination() {
        awaitText(string(R.string.destination_system_info))

        HomeDestinations.forEach { destination ->
            composeRule.onNodeWithText(string(destination.shortTitleRes)).assertIsDisplayed()
        }
    }

    @Test
    fun tappingATileOpensThatScreen() {
        openSystemInfo()

        composeRule.onNodeWithText(string(R.string.system_info_title)).assertIsDisplayed()
    }

    @Test
    fun systemBackReturnsHome() {
        openSystemInfo()

        Espresso.pressBack()

        awaitText(string(R.string.home_subtitle))
        composeRule.onNodeWithText(string(R.string.home_subtitle)).assertIsDisplayed()
    }

    private fun openSystemInfo() {
        val tile = string(R.string.destination_system_info)
        awaitText(tile)
        composeRule.onNodeWithText(tile).performClick()
        awaitText(string(R.string.system_info_title))
    }

    /** Waits for [text] to appear, which the home grid's staggered entrance makes necessary. */
    private fun awaitText(text: String) {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(resId: Int) = composeRule.activity.getString(resId)

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
