package com.aoscoremonitor.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aoscoremonitor.diagnostics.FrameworkAnalyzer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameworkAnalysisScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var frameworkData by remember {
        mutableStateOf(
            FrameworkAnalyzer.FrameworkData(
                binderTransactions = emptyList(),
                apiCalls = emptyList(),
                serviceData = FrameworkAnalyzer.ServiceManagerData(
                    runningServices = emptyMap(),
                    serviceConnections = emptyList()
                )
            )
        )
    }

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Binder Transactions", "API Calls", "Services")

    val frameworkAnalyzer = remember {
        FrameworkAnalyzer(context) { data ->
            frameworkData = data
        }
    }

    DisposableEffect(frameworkAnalyzer) {
        frameworkAnalyzer.startAnalyzing()
        onDispose {
            frameworkAnalyzer.stopAnalyzing()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Framework Analysis") },
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
                0 -> BinderTransactionsTab(binderTransactions = frameworkData.binderTransactions)
                1 -> ApiCallsTab(apiCalls = frameworkData.apiCalls)
                2 -> ServicesTab(serviceData = frameworkData.serviceData)
            }
        }
    }
}

@Composable
fun BinderTransactionsTab(binderTransactions: List<FrameworkAnalyzer.BinderTransaction>) {
    val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (binderTransactions.isEmpty()) {
            item {
                Text(
                    text = "No Binder transactions detected." +
                        "The device may need root access or special permissions to access this data.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(binderTransactions) { transaction ->
                Card(
                    modifier = Modifier.padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Process: ${transaction.process} (PID: ${transaction.pid})",
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Transaction Code: 0x${transaction.transactionCode.toString(16)}")
                        Text(text = "Destination: ${transaction.destination}")
                        Text(text = "Data Size: ${transaction.dataSize} bytes")
                        Text(
                            text = "Time: ${dateFormat.format(Date(transaction.timestamp))}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ApiCallsTab(apiCalls: List<FrameworkAnalyzer.ApiCallInfo>) {
    val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (apiCalls.isEmpty()) {
            item {
                Text(
                    text = "No API calls detected. API tracing requires special instrumentation.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(apiCalls) { call ->
                Card(
                    modifier = Modifier.padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "API: ${call.apiName}",
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Caller: ${call.callerPackage}")
                        Text(text = "Duration: ${call.duration}ms")
                        Text(
                            text = "Time: ${dateFormat.format(Date(call.timestamp))}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ServicesTab(serviceData: FrameworkAnalyzer.ServiceManagerData) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "Running Services",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (serviceData.runningServices.isEmpty()) {
            item {
                Text(
                    text = "No service data available. Some information may require elevated permissions.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(serviceData.runningServices.entries.toList()) { (service, state) ->
                Card(
                    modifier = Modifier.padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = service,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "State: $state")
                    }
                }
            }
        }

        item {
            Divider(modifier = Modifier.padding(vertical = 16.dp))
            Text(
                text = "Service Connections",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (serviceData.serviceConnections.isEmpty()) {
            item {
                Text(
                    text = "No connection data available.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(serviceData.serviceConnections) { (client, service) ->
                Card(
                    modifier = Modifier.padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Client: $client",
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Connected to: $service")
                    }
                }
            }
        }
    }
}
