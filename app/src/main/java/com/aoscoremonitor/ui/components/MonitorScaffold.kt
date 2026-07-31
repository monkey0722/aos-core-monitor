package com.aoscoremonitor.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.aoscoremonitor.R

/**
 * The frame every detail screen sits in: app bar, back affordance, and content insets.
 *
 * Each screen used to build this itself, which meant eight copies of the same `TopAppBar` — all
 * of them repainting the bar in `primaryContainer`, none of them reacting to scroll. Sharing the
 * frame also keeps the app to a single [Scaffold] per screen; the host previously wrapped these
 * in a second one, so window insets were applied twice and left a dead band under the app bar.
 *
 * The scroll behavior is created here rather than taken as a parameter: its type is still an
 * experimental Material 3 API, and exposing it in the signature would force every caller to opt
 * in for something none of them needs to configure.
 *
 * @param title shown in the app bar, truncated rather than wrapped.
 * @param onNavigateBack invoked by the back button. The system back gesture is handled by the
 *   navigation host, so screens do not wire it up here.
 * @param content receives the insets to apply — the same contract as [Scaffold].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = floatingActionButton,
        content = content
    )
}
