package com.aoscoremonitor.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aoscoremonitor.R

/**
 * Asks for a new reading, and shows that one is being taken.
 *
 * The screens that use this do not poll, so a tap is the only thing that produces a new reading —
 * and the readings behind it are slow enough to be waited on: four AAudio streams opened and closed,
 * or a Vulkan instance created and destroyed. Without the spinner the button answers a tap with
 * nothing at all for about a second, and since the view models drop taps that arrive while a read is
 * in flight, tapping again does not help either. The state is the one honest thing to show there.
 *
 * The label changes with it rather than only the icon: a spinner that replaces an icon is invisible
 * to TalkBack, which would go on offering "Refresh" for a button that has stopped accepting taps.
 */
@Composable
fun RefreshFab(onRefresh: () -> Unit, isRefreshing: Boolean, modifier: Modifier = Modifier) {
    val description = stringResource(if (isRefreshing) R.string.action_refreshing else R.string.action_refresh)
    FloatingActionButton(
        // FloatingActionButton has no enabled parameter, so being unavailable has to be said twice:
        // the tap is swallowed here rather than sent to the view model to be dropped there, and the
        // semantics carry `disabled` so that TalkBack and Switch Access stop offering a button that
        // would do nothing. Without the second half the spinner is invisible to them and the
        // control still reads as actionable.
        onClick = { if (!isRefreshing) onRefresh() },
        modifier = modifier.semantics {
            contentDescription = description
            if (isRefreshing) disabled()
        }
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                // The container's own content colour: the default is the theme's primary, which is
                // what the container is painted in.
                color = LocalContentColor.current,
                strokeWidth = 2.dp
            )
        } else {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
        }
    }
}
