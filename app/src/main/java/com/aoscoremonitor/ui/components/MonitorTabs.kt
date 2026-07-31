package com.aoscoremonitor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aoscoremonitor.ui.theme.Spacing
import kotlinx.coroutines.launch

/** One tab: what it is called, what it looks like, and how many things are behind it. */
data class MonitorTab(val title: String, val icon: ImageVector? = null, val count: Int? = null)

/**
 * A tab row wired to a pager.
 *
 * Driving a [PagerState] rather than a plain selected index gives swiping between tabs, which
 * the two tabbed screens did not support, and gives each tab its own scroll position instead of
 * resetting to the top on every switch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorTabs(tabs: List<MonitorTab>, pagerState: PagerState, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()

    PrimaryTabRow(selectedTabIndex = pagerState.currentPage, modifier = modifier) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = pagerState.currentPage == index,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                text = { Text(tab.title) },
                icon = tab.icon?.let { icon ->
                    {
                        // The count belongs on the tab, not in it: a badge says how much is
                        // behind the tab without competing with its label for width.
                        if (tab.count != null && tab.count > 0) {
                            // Material's default badge is the error color, which framed "294
                            // services" as something wrong. A count is just a count.
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ) { Text(tab.count.toString()) }
                                }
                            ) {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(TabIconSize))
                            }
                        } else {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(TabIconSize))
                        }
                    }
                }
            )
        }
    }
}

/** The list layout each tab's page uses, so gutters and card spacing match across the two tabbed screens. */
@Composable
fun TabContentList(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        content = content
    )
}

private val TabIconSize = 24.dp
