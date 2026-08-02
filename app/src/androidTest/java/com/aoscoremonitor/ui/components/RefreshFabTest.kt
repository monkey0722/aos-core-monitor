package com.aoscoremonitor.ui.components

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aoscoremonitor.R
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks that a reading in flight reaches a screen reader, not only the eye.
 *
 * The screens using this button do not poll, so a tap is the only way to a new reading — and the
 * readings take about a second, during which the view model drops further taps. Swapping the icon
 * for a spinner says so to a sighted user and to nobody else: `FloatingActionButton` has no enabled
 * parameter, so without the `disabled` semantics TalkBack and Switch Access go on offering a button
 * that silently does nothing.
 */
@RunWith(AndroidJUnit4::class)
class RefreshFabTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aRefreshInFlightIsOfferedAsDisabledAndTakesNoTaps() {
        var taps = 0
        composeRule.setContent {
            AOSCoreMonitorTheme(dynamicColor = false) {
                RefreshFab(onRefresh = { taps++ }, isRefreshing = true)
            }
        }

        val button = composeRule.onNodeWithContentDescription(string(R.string.action_refreshing))
        button.assertIsNotEnabled()
        button.performClick()
        assertEquals("A refresh already in flight must not start another", 0, taps)
    }

    @Test
    fun anIdleButtonIsOfferedAndAsksForAReading() {
        var taps = 0
        composeRule.setContent {
            AOSCoreMonitorTheme(dynamicColor = false) {
                RefreshFab(onRefresh = { taps++ }, isRefreshing = false)
            }
        }

        val button = composeRule.onNodeWithContentDescription(string(R.string.action_refresh))
        button.assertIsEnabled()
        button.performClick()
        assertEquals(1, taps)
    }

    private fun string(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
