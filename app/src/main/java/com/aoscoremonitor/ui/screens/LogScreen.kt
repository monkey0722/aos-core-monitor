package com.aoscoremonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aoscoremonitor.R
import com.aoscoremonitor.ui.components.FullScreenMessage
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.MonitorTypography
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.theme.statusColors
import com.aoscoremonitor.ui.viewmodel.LogLevel
import com.aoscoremonitor.ui.viewmodel.LogLine
import com.aoscoremonitor.ui.viewmodel.LogViewModel
import kotlinx.coroutines.launch

@Composable
fun LogScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier, viewModel: LogViewModel = viewModel()) {
    // Collection follows the screen rather than the activity, so leaving the app stops the
    // logcat pipe. It used to run from the activity's onResume for the app's whole lifetime.
    LifecycleStartEffect(viewModel) {
        viewModel.startCollecting()
        onStopOrDispose { viewModel.stopCollecting() }
    }

    LogScreenContent(
        lines = viewModel.lines,
        droppedCount = viewModel.droppedCount,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
private fun LogScreenContent(lines: List<LogLine>, droppedCount: Int, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val canScrollDown by remember { derivedStateOf { listState.canScrollForward } }

    // Follow the tail only while the user is already at it, so scrolling back to read something
    // is not undone by the next line arriving.
    LaunchedEffect(lines.size) {
        if (!canScrollDown && lines.isNotEmpty()) {
            listState.scrollToItem(lines.lastIndex)
        }
    }

    MonitorScaffold(
        title = stringResource(R.string.logs_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        floatingActionButton = {
            if (canScrollDown) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch { listState.animateScrollToItem(lines.lastIndex) }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = stringResource(R.string.action_scroll_to_bottom)
                    )
                }
            }
        }
    ) { innerPadding ->
        if (lines.isEmpty()) {
            FullScreenMessage(
                message = stringResource(R.string.logs_empty),
                supportingText = stringResource(R.string.logs_empty_hint),
                icon = Icons.AutoMirrored.Filled.List,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = Spacing.Small, vertical = Spacing.Small)
            ) {
                if (droppedCount > 0) {
                    item(key = "dropped") {
                        Text(
                            text = pluralStringResource(
                                R.plurals.logs_dropped_notice,
                                droppedCount,
                                lines.size,
                                droppedCount
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.Small)
                        )
                    }
                }
                // Keyed on the line's id: the same message repeats constantly in logcat, so
                // keying on its text would collide and make Compose reuse the wrong row.
                items(lines, key = { it.id }) { line ->
                    LogRow(line)
                }
            }
        }
    }
}

@Composable
private fun LogRow(line: LogLine, modifier: Modifier = Modifier) {
    val accent = line.level.accentColor
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(line.level.backgroundColor)
    ) {
        // A rule in the level's color, instead of tinting the whole row's background. Five
        // different tinted backgrounds made a screen of logs hard to read; this keeps the text on
        // one surface and still marks severity at a glance.
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent)
        )
        Text(
            text = line.text,
            style = MonitorTypography.machineText,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall)
        )
    }
}

private val LogLevel.accentColor: Color
    @Composable
    get() = when (this) {
        LogLevel.Error -> MaterialTheme.colorScheme.error
        LogLevel.Warn -> MaterialTheme.statusColors.warning
        LogLevel.Info -> MaterialTheme.colorScheme.primary
        LogLevel.Debug -> MaterialTheme.colorScheme.secondary
        LogLevel.Verbose, LogLevel.Unknown -> MaterialTheme.colorScheme.outlineVariant
    }

/** Only the two levels worth interrupting a scan of the log get a tinted row. */
private val LogLevel.backgroundColor: Color
    @Composable
    get() = when (this) {
        LogLevel.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        LogLevel.Warn -> MaterialTheme.statusColors.warningContainer.copy(alpha = 0.4f)
        else -> Color.Transparent
    }

@Preview(name = "Logs", showBackground = true)
@Preview(name = "Logs (dark)", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LogScreenPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        LogScreenContent(
            lines = listOf(
                LogLine(0, "07-31 11:11:07.104  1731  1731 I ActivityManager: Start proc 4821", LogLevel.Info),
                LogLine(1, "07-31 11:11:07.201  1731  1802 W BroadcastQueue: Background execution not allowed", LogLevel.Warn),
                LogLine(2, "07-31 11:11:07.310  4821  4821 E AndroidRuntime: FATAL EXCEPTION: main", LogLevel.Error),
                LogLine(3, "07-31 11:11:07.402  1731  1731 D SurfaceFlinger: duplicate frame", LogLevel.Debug)
            ),
            droppedCount = 0,
            onNavigateBack = {}
        )
    }
}
