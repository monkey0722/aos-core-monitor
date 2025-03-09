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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(
    onNavigateToLogs: () -> Unit,
    onNavigateToSystemInfo: () -> Unit,
    onNavigateToSystemDiagnostics: () -> Unit,
    onNavigateToSecurityInfo: () -> Unit,
    onNavigateToFrameworkAnalysis: () -> Unit,
    onNavigateToHalInfo: () -> Unit,
    onNavigateToNativeSystemMonitor: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically(
                initialOffsetY = { -50 },
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AOS Core Monitor",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        val menuItems = listOf(
            MenuItem(
                title = "System Info",
                icon = Icons.Default.Computer,
                color = MaterialTheme.colorScheme.primary,
                onClick = onNavigateToSystemInfo
            ),
            MenuItem(
                title = "System Logs",
                icon = Icons.Default.List,
                color = MaterialTheme.colorScheme.secondary,
                onClick = onNavigateToLogs
            ),
            MenuItem(
                title = "Diagnostics",
                icon = Icons.Default.BarChart,
                color = MaterialTheme.colorScheme.tertiary,
                onClick = onNavigateToSystemDiagnostics
            ),
            MenuItem(
                title = "Security",
                icon = Icons.Default.Security,
                color = MaterialTheme.colorScheme.error,
                onClick = onNavigateToSecurityInfo
            ),
            MenuItem(
                title = "Framework",
                icon = Icons.Default.Analytics,
                color = MaterialTheme.colorScheme.inversePrimary,
                onClick = onNavigateToFrameworkAnalysis
            ),
            MenuItem(
                title = "HAL Interface",
                icon = Icons.Default.Settings,
                color = MaterialTheme.colorScheme.secondary,
                onClick = onNavigateToHalInfo
            ),
            MenuItem(
                title = "Native Monitor",
                icon = Icons.Default.Memory,
                color = MaterialTheme.colorScheme.tertiary,
                onClick = onNavigateToNativeSystemMonitor
            )
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(menuItems) { item ->
                GridMenuItem(
                    menuItem = item,
                    visible = visible
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GridMenuItem(
    menuItem: MenuItem,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    var itemVisible by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            delay(200)
            itemVisible = true
        }
    }

    AnimatedVisibility(
        visible = itemVisible,
        enter = fadeIn() + slideInVertically(
            initialOffsetY = { 100 },
            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
        )
    ) {
        ElevatedCard(
            onClick = menuItem.onClick,
            modifier = modifier.aspectRatio(1f),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = 6.dp
            ),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = menuItem.icon,
                        contentDescription = menuItem.title,
                        tint = menuItem.color,
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .fillMaxSize(0.4f)
                    )
                    Text(
                        text = menuItem.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private data class MenuItem(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)
