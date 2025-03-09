package com.aoscoremonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDisplay(
    logs: List<String>,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Show "Scroll to bottom" button only if there are enough logs at the bottom
    val showScrollToBottom by remember {
        derivedStateOf {
            lazyListState.canScrollForward || (
                logs.isNotEmpty() && !lazyListState.isScrolledToEnd() && lazyListState.firstVisibleItemIndex > 0
                )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Logs") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            if (showScrollToBottom) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            lazyListState.animateScrollToItem(logs.size - 1)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = "Scroll to bottom"
                    )
                }
            }
        }
    ) { innerPadding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = "No logs available yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 8.dp)
            ) {
                items(logs) { log ->
                    LogItem(log = log)
                }
            }
        }
    }
}

@Composable
fun LogItem(log: String) {
    val (logColor, logBackground) = getLogColors(log)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(MaterialTheme.shapes.small)
            .background(logBackground)
            .padding(8.dp)
    ) {
        Text(
            text = log,
            style = MaterialTheme.typography.bodyMedium,
            color = logColor,
            overflow = TextOverflow.Ellipsis,
            maxLines = 3
        )
    }
}

@Composable
private fun getLogColors(log: String): Pair<Color, Color> {
    return when {
        log.contains(" E ", ignoreCase = true) ||
            log.contains("error", ignoreCase = true) ||
            log.contains(
                "exception",
                ignoreCase = true
            ) -> {
            // Error logs
            MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        }
        log.contains(" W ", ignoreCase = true) || log.contains("warning", ignoreCase = true) -> {
            // Warning logs
            MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        }
        log.contains(" I ", ignoreCase = true) || log.contains("info", ignoreCase = true) -> {
            // Info logs
            MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        }
        log.contains(" D ", ignoreCase = true) || log.contains("debug", ignoreCase = true) -> {
            // Debug logs
            MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
        }
        else -> {
            // Other logs
            MaterialTheme.colorScheme.onSurface to MaterialTheme.colorScheme.surface
        }
    }
}

// Extension function to check if the list has been scrolled to the end
private fun androidx.compose.foundation.lazy.LazyListState.isScrolledToEnd(): Boolean {
    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return true
    return lastVisibleItem.index == layoutInfo.totalItemsCount - 1
}
