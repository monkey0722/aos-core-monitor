package com.aoscoremonitor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("HAL Interfaces", "HW Services", "VNDK Info")

    val halAnalyzer = remember {
        HalInterfaceAnalyzer(context) { data ->
            halData = data
        }
    }

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
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = { Text(title) },
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index }
                    )
                }
            }

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
        item {
            HalInfoHeader(
                title = "Hardware Abstraction Layer Interfaces",
                subtitle = "HALs provide standardized interfaces to hardware components"
            )
        }

        if (halInterfaces.isEmpty()) {
            item {
                Text(
                    text = "No HAL interface data available. Some information may require elevated permissions.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(halInterfaces) { halInterface ->
                HalInterfaceCard(halInterface = halInterface)
            }
        }
    }
}

@Composable
fun HalInterfaceCard(halInterface: HalInterfaceAnalyzer.HalInterface) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = halInterface.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
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

            Text(
                text = "Implementation: ${halInterface.implementation}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = "Status: ${halInterface.status}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (halInterface.status == "Running") {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
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
        item {
            HalInfoHeader(
                title = "Hardware Services",
                subtitle = "System services that provide hardware functionality"
            )
        }

        if (hwservices.isEmpty()) {
            item {
                Text(
                    text = "No hardware service data available.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
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
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = service.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Server: ${service.server}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                text = "Clients:",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

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
        item {
            HalInfoHeader(
                title = "VNDK Information",
                subtitle = "Vendor Native Development Kit libraries"
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
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

            Text(
                text = "VNDK Libraries:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
        }

        if (vndkInfo.libraries.isEmpty()) {
            item {
                Text(
                    text = "No VNDK library information available.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(vndkInfo.libraries) { library ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
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

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
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
                        text = "The VNDK helps ensure forward compatibility between Android OS updates" +
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
fun HalInfoHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp)
        )

        Divider(modifier = Modifier.padding(top = 8.dp))
    }
}
