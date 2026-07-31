package com.aoscoremonitor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aoscoremonitor.R
import com.aoscoremonitor.ui.navigation.Destination
import com.aoscoremonitor.ui.navigation.HomeDestinations
import com.aoscoremonitor.ui.navigation.menuTitleRes
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.Spacing
import kotlinx.coroutines.delay

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
            items(HomeDestinations, key = { it.toString() }) { destination ->
                DestinationTile(
                    destination = destination,
                    index = HomeDestinations.indexOf(destination),
                    onClick = { onNavigate(destination) }
                )
            }
        }
    }
}

@Composable
private fun DestinationTile(destination: Destination, index: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // The tiles were already animating in, but every one of them waited the same 200ms, so they
    // all arrived together and the stagger the code was reaching for never happened.
    var visible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(STAGGER_STEP_MS * index.coerceAtMost(MAX_STAGGER_STEPS))
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(
            initialOffsetY = { it / 4 },
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
        )
    ) {
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
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Box(modifier = Modifier.size(IconWellSize), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize)
                        )
                    }
                }
                Text(
                    text = stringResource(destination.menuTitleRes),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.Small),
                    // Shrink to fit so long single words like "Diagnostics" are not broken
                    // mid-word by the tile's width.
                    maxLines = 2,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 10.sp,
                        maxFontSize = MaterialTheme.typography.titleSmall.fontSize
                    )
                )
            }
        }
    }
}

/** Wide enough for a two-word label at the smallest step, narrow enough for three phone columns. */
private val TileMinSize = 108.dp
private val IconWellSize = 48.dp
private val IconSize = 24.dp
private const val STAGGER_STEP_MS = 35L
private const val MAX_STAGGER_STEPS = 8

@Preview(name = "Home", showBackground = true)
@Preview(name = "Home (dark)", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        HomeScreen(onNavigate = {})
    }
}
