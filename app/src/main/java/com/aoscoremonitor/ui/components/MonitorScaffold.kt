package com.aoscoremonitor.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
        content = { innerPadding ->
            // A ceiling on how wide a line of a reading gets. Turned sideways, or on a tablet, a
            // card ran the full width of the display and a mount's options or a log line became a
            // single line of text a foot long, which is a measure nobody reads comfortably. Centred
            // rather than left-aligned so the empty space is split evenly.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
                    content(innerPadding)
                }
            }
        }
    )
}

/**
 * The widest a column of readings gets.
 *
 * Wide enough that a phone in portrait is unaffected — every phone this app runs on is narrower
 * than this — and narrow enough that a landscape display does not stretch one card across it.
 */
private val ContentMaxWidth = 640.dp
