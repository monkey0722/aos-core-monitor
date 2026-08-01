package com.aoscoremonitor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aoscoremonitor.R
import com.aoscoremonitor.ui.navigation.Destination
import com.aoscoremonitor.ui.navigation.HomeGroups
import com.aoscoremonitor.ui.navigation.shortTitleRes
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.MonitorPreviews
import com.aoscoremonitor.ui.theme.Spacing

/**
 * The entry point: a grid of everything the app can inspect.
 *
 * @param onNavigate receives the chosen [Destination]. The screen used to take nine separate
 *   callbacks, one per tile, which had to be declared, threaded through the activity and kept in
 *   sync with the grid by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigate: (Destination) -> Unit, modifier: Modifier = Modifier) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // Replaces the full-width primaryContainer card that used to sit above the grid
            // holding nothing but the app name. The bar says the same thing where users look for
            // it, and collapses out of the way once they scroll.
            //
            // Medium rather than large: the grid fits on one screen, so a large bar would never
            // get the scroll that collapses it and would just hold 40dp of empty space open.
            MediumTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        // Adaptive rather than a fixed three columns: on a tablet or an unfolded device, three
        // columns stretched each tile to several hundred dp with an icon marooned in the middle.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = TileMinSize),
            contentPadding = PaddingValues(Spacing.Large),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item(key = "subtitle", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.Small)
                )
            }
            // A heading over each run. The order already meant something — the framework first,
            // then what this process is made of, then what it can reach — and this is where that
            // shows. The index carries on across the groups so the entrance still staggers down
            // the screen rather than restarting at every heading.
            HomeGroups.forEach { group ->
                item(key = "group-${group.titleRes}", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(group.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.Medium, bottom = Spacing.Small)
                            .semantics(mergeDescendants = true) { heading() }
                    )
                }
                items(group.destinations, key = { it.toString() }) { destination ->
                    DestinationTile(destination = destination, onClick = { onNavigate(destination) })
                }
            }
        }
    }
}

/**
 * One tile.
 *
 * No entrance animation. Each tile used to fade and slide in on a stagger, which meant an
 * [AnimatedVisibility] per tile — and an invisible AnimatedVisibility measures zero height, so a
 * tile composed as it scrolled into view left a hole in the grid until its delay elapsed and then
 * dropped into place. That read as the grid redrawing itself under the user, and it got worse once
 * the grid grew headings and had more to scroll. A flourish worth one second of the first launch is
 * not worth that on every scroll.
 */
@Composable
private fun DestinationTile(destination: Destination, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.Medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // One treatment for every tile. The icons used to be tinted from a rotation of
            // primary / secondary / tertiary / error / inversePrimary, which read as though
            // Security and TCP were in a failure state and left Framework's inversePrimary
            // barely visible against the card.
            // primaryContainer rather than secondaryContainer: the theme leaves the surface
            // containers to Material, which derives them from the same near-white surface the
            // secondary container sits beside, so the well was the same lightness as the card
            // and read as nothing. The primary one carries enough blue to be seen.
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(modifier = Modifier.size(IconWellSize), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize)
                    )
                }
            }
            // Two lines of room whether the label needs them or not. The column centres what it
            // holds, so a label that wrapped made the content taller and pushed the icon up —
            // "Kernel Counters" and "Native Libraries" sat a line above the tiles beside them.
            //
            // The label is centred in that room rather than laid at the top of it, which is
            // what `minLines = 2` would do: that fixed the icons but left a line-high hole
            // under every short label. Room measured from the style's line height, so it grows
            // with the user's font scale instead of being pinned to a dp constant.
            val labelStyle = MaterialTheme.typography.titleSmall
            val labelHeight = with(LocalDensity.current) { labelStyle.lineHeight.toDp() * 2 }
            Box(
                modifier = Modifier
                    .padding(top = Spacing.Small)
                    .height(labelHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(destination.shortTitleRes),
                    style = labelStyle,
                    textAlign = TextAlign.Center,
                    // Shrink to fit so long single words like "Diagnostics" are not broken
                    // mid-word by the tile's width.
                    maxLines = 2,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = labelStyle.fontSize)
                )
            }
        }
    }
}

/** Wide enough for a two-word label at the smallest step, narrow enough for three phone columns. */
private val TileMinSize = 108.dp
private val IconWellSize = 48.dp
private val IconSize = 24.dp

@MonitorPreviews
@Composable
private fun HomeScreenPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        HomeScreen(onNavigate = {})
    }
}
