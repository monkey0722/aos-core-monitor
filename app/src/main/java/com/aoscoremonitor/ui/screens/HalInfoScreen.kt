package com.aoscoremonitor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aoscoremonitor.diagnostics.HalInterfaceAnalyzer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HalInfoScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var halData by remember {
        mutableStateOf(
            HalInterfaceAnalyzer.HalData(
                halInterfaces = emptyList(),
                hwservices = emptyList(),
                vndkInfo = HalInterfaceAnalyzer.VndkInfo(
                    version = "Unknown",
                    libraries = emptyList()
                )
            )
        )
    }

    // Tab selection state
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf(
        TabInfo("HAL Interfaces", Icons.Default.Hardware, halData.halInterfaces.size),
        TabInfo("HW Services", Icons.Default.Devices, halData.hwservices.size),
        TabInfo("VNDK Info", Icons.Default.Memory, halData.vndkInfo.libraries.size)
    )

    // Initialize the HAL analyzer
    val halAnalyzer = remember {
        HalInterfaceAnalyzer(context) { data ->
            halData = data
        }
    }

    // Start/stop analyzer based on component lifecycle
    DisposableEffect(halAnalyzer) {
        halAnalyzer.startAnalyzing()
        onDispose {
            halAnalyzer.stopAnalyzing()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HAL Interface Information") },
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
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Custom styled tab row
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, tabInfo ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(tabInfo.title) },
                        icon = {
                            if (tabInfo.count > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge { Text(text = tabInfo.count.toString()) }
                                    }
                                ) {
                                    Icon(
                                        imageVector = tabInfo.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = tabInfo.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    )
                }
            }

            // Content based on selected tab
            when (selectedTabIndex) {
                0 -> HalInterfacesTab(halInterfaces = halData.halInterfaces)
                1 -> HwServicesTab(hwservices = halData.hwservices)
                2 -> VndkInfoTab(vndkInfo = halData.vndkInfo)
            }
        }
    }
}

@Composable
fun HalInterfacesTab(halInterfaces: List<HalInterfaceAnalyzer.HalInterface>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header section
        item {
            HalInfoHeader(
                title = "Hardware Abstraction Layer Interfaces",
                subtitle = "HALs provide standardized interfaces to hardware components",
                icon = Icons.Default.Hardware
            )
        }

        // Empty state message
        if (halInterfaces.isEmpty()) {
            item {
                EmptyStateMessage(
                    message = "No HAL interface data available. Some information may require elevated permissions."
                )
            }
        } else {
            // Display each HAL interface as a card
            items(halInterfaces) { halInterface ->
                HalInterfaceCard(halInterface = halInterface)
            }
        }
    }
}

@Composable
fun HalInterfaceCard(halInterface: HalInterfaceAnalyzer.HalInterface) {
    // Determine if the HAL is running
    val isRunning = halInterface.status == "Running"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title row with status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Status icon
                Icon(
                    imageVector = if (isRunning) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = halInterface.status,
                    tint = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(20.dp)
                )

                // HAL name
                Text(
                    text = halInterface.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Version and type info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Version: ${halInterface.version}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Type: ${halInterface.type}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Implementation info
            Text(
                text = "Implementation: ${halInterface.implementation}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Status text
            Text(
                text = "Status: ${halInterface.status}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun HwServicesTab(hwservices: List<HalInterfaceAnalyzer.HwService>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header section
        item {
            HalInfoHeader(
                title = "Hardware Services",
                subtitle = "System services that provide hardware functionality",
                icon = Icons.Default.Devices
            )
        }

        // Empty state message
        if (hwservices.isEmpty()) {
            item {
                EmptyStateMessage(
                    message = "No hardware service data available."
                )
            }
        } else {
            // Display each hardware service as a card
            items(hwservices) { service ->
                HwServiceCard(service = service)
            }
        }
    }
}

@Composable
fun HwServiceCard(service: HalInterfaceAnalyzer.HwService) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Service name
            Text(
                text = service.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Server info
            Text(
                text = "Server: ${service.server}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Client list header
            Text(
                text = "Clients:",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            // List of clients
            service.clients.forEach { client ->
                Text(
                    text = "• $client",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun VndkInfoTab(vndkInfo: HalInterfaceAnalyzer.VndkInfo) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header section
        item {
            HalInfoHeader(
                title = "VNDK Information",
                subtitle = "Vendor Native Development Kit libraries",
                icon = Icons.Default.Memory
            )

            // VNDK version card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 3.dp
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "VNDK Version: ${vndkInfo.version}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Text(
                        text = "The VNDK (Vendor Native Development Kit) is " +
                            "a set of libraries that vendors can use to build their HALs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Libraries section title
            Text(
                text = "VNDK Libraries:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
        }

        // Empty state for libraries
        if (vndkInfo.libraries.isEmpty()) {
            item {
                EmptyStateMessage(
                    message = "No VNDK library information available."
                )
            }
        } else {
            // List of VNDK libraries
            items(vndkInfo.libraries) { library ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 1.dp
                    )
                ) {
                    Text(
                        text = library,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Information note card at bottom
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Information",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    Text(
                        text = "The VNDK helps ensure forward compatibility between Android OS updates " +
                            "and vendor implementations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun HalInfoHeader(title: String, subtitle: String, icon: ImageVector) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        // Title row with icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(28.dp)
            )

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Subtitle
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp, start = 36.dp)
        )

        // Divider
        Divider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun EmptyStateMessage(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Information",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp)
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// Data class to represent tab information
private data class TabInfo(
    val title: String,
    val icon: ImageVector,
    val count: Int
)
