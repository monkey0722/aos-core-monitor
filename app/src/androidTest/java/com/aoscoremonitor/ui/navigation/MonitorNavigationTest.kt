package com.aoscoremonitor.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso
import androidx.test.espresso.NoActivityResumedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aoscoremonitor.MainActivity
import com.aoscoremonitor.R
import org.junit.Assert.assertThrows
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

    /**
     * Every destination has a tile, including the ones past the bottom of the screen.
     *
     * Scrolled to rather than asserted on where it stands: the grid outgrew the screen, and a tile
     * below the fold is not composed at all, so the assertion would fail on a phone while passing
     * on a tall emulator. Scrolling to each in turn also pins that the grid can reach all of them.
     */
    @Test
    fun homeListsEveryDestination() {
        awaitText(string(R.string.destination_system_info))

        HomeDestinations.forEach { destination ->
            val label = string(destination.shortTitleRes)
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(label))
            composeRule.onNodeWithText(label).assertIsDisplayed()
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

    /**
     * Backing out past home leaves the app, rather than doing nothing.
     *
     * `MonitorNavHost` refuses to pop the last entry, because an empty back stack is what
     * `NavDisplay` rejects with an IllegalArgumentException. The risk in that guard is the
     * opposite failure — a root screen that swallows back and traps the user — so this pins that
     * the press still finishes the activity. Espresso reports exactly that as
     * [NoActivityResumedException].
     *
     * It is not a regression test for the crash itself: it passes with the guard removed, because
     * NavDisplay stops routing back here once Home is the only entry.
     */
    @Test
    fun backingOutPastHomeLeavesTheApp() {
        openSystemInfo()

        Espresso.pressBack()
        awaitText(string(R.string.home_subtitle))

        assertThrows(NoActivityResumedException::class.java) { Espresso.pressBack() }
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
