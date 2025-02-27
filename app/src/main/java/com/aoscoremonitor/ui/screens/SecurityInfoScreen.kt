package com.aoscoremonitor.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aoscoremonitor.diagnostics.SecurityInfoCollector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityInfoScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var securityInfo by remember {
        mutableStateOf(
            SecurityInfoCollector.SecurityInfo(
                selinuxStatus = "Unknown",
                selinuxMode = "Unknown",
                permissionMap = emptyMap(),
                hardwareSecurityInfo = SecurityInfoCollector.HardwareSecurityInfo(
                    isHardwareBackedKeyStoreSupported = false,
                    isStrongBoxBackedKeyStoreSupported = false,
                    isFingerprintSupported = false,
                    isBiometricSupported = false,
                    isTeeSupported = false,
                    keystoreVersion = "Unknown"
                )
            )
        )
    }

    val securityInfoCollector = remember {
        SecurityInfoCollector(context) { info ->
            securityInfo = info
        }
    }

    DisposableEffect(securityInfoCollector) {
        securityInfoCollector.startCollecting()
        onDispose {
            securityInfoCollector.stopCollecting()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Information") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // SELinux Status Section
            item {
                SecuritySectionTitle(title = "SELinux Status")
                SELinuxStatusCard(
                    status = securityInfo.selinuxStatus,
                    mode = securityInfo.selinuxMode
                )
                Divider(modifier = Modifier.padding(vertical = 16.dp))
            }

            // Hardware Security Module Section
            item {
                SecuritySectionTitle(title = "Hardware Security")
                HardwareSecurityCard(hardwareInfo = securityInfo.hardwareSecurityInfo)
                Divider(modifier = Modifier.padding(vertical = 16.dp))
            }

            // App Permissions Section
            item {
                SecuritySectionTitle(title = "App Permissions (Non-System Apps)")
            }

            items(securityInfo.permissionMap.entries.toList()) { (packageName, permissions) ->
                AppPermissionCard(
                    packageName = packageName,
                    permissions = permissions
                )
            }
        }
    }
}

@Composable
fun SecuritySectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun SELinuxStatusCard(status: String, mode: String) {
    Card(
        modifier = Modifier.padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                mode.contains("Enforcing", ignoreCase = true) -> Color(0xFFE8F5E9) // Light green
                mode.contains("Permissive", ignoreCase = true) -> Color(0xFFFFF8E1) // Light amber
                else -> Color(0xFFFFEBEE) // Light red
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Status: $status",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Mode: $mode",
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    mode.contains("Enforcing", ignoreCase = true) -> Color(0xFF388E3C) // Dark green
                    mode.contains("Permissive", ignoreCase = true) -> Color(0xFFFFA000) // Amber
                    else -> Color(0xFFD32F2F) // Red
                }
            )
        }
    }
}

@Composable
fun HardwareSecurityCard(hardwareInfo: SecurityInfoCollector.HardwareSecurityInfo) {
    Card(
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SecurityFeatureItem(
                title = "Hardware-backed Keystore",
                isSupported = hardwareInfo.isHardwareBackedKeyStoreSupported
            )
            SecurityFeatureItem(
                title = "StrongBox Keystore",
                isSupported = hardwareInfo.isStrongBoxBackedKeyStoreSupported
            )
            SecurityFeatureItem(
                title = "Fingerprint Hardware",
                isSupported = hardwareInfo.isFingerprintSupported
            )
            SecurityFeatureItem(
                title = "Biometric Authentication",
                isSupported = hardwareInfo.isBiometricSupported
            )
            SecurityFeatureItem(
                title = "Trusted Execution Environment (TEE)",
                isSupported = hardwareInfo.isTeeSupported
            )
            Text(
                text = "Keystore Implementation: ${hardwareInfo.keystoreVersion}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
fun SecurityFeatureItem(title: String, isSupported: Boolean) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSupported) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = if (isSupported) "Supported" else "Not Supported",
            tint = if (isSupported) Color(0xFF388E3C) else Color(0xFFD32F2F),
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun AppPermissionCard(
    packageName: String,
    permissions: List<SecurityInfoCollector.AppPermissionInfo>
) {
    Card(
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = packageName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Dangerous Permissions: ${permissions.count { it.isProtectionDangerous && it.isGranted }}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (permissions.any { it.isProtectionDangerous && it.isGranted }) {
                    Color(0xFFD32F2F) // Red for dangerous permissions
                } else {
                    Color(0xFF388E3C) // Green if no dangerous permissions
                },
                modifier = Modifier.padding(top = 4.dp)
            )

            // Show the first 3 permissions only to avoid cluttering the UI
            permissions.take(3).forEach { permission ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            permission.isGranted && permission.isProtectionDangerous -> Icons.Filled.Error
                            permission.isGranted -> Icons.Filled.CheckCircle
                            else -> Icons.Filled.Info
                        },
                        contentDescription = "Permission Status",
                        tint = when {
                            permission.isGranted && permission.isProtectionDangerous -> Color(0xFFD32F2F)
                            permission.isGranted -> Color(0xFF388E3C)
                            else -> Color(0xFF9E9E9E)
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    // Extract just the permission name without the android.permission prefix
                    val shortPermName = permission.permissionName.split(".").lastOrNull() ?: permission.permissionName
                    Text(
                        text = shortPermName,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Show count of remaining permissions
            if (permissions.size > 3) {
                Text(
                    text = "... and ${permissions.size - 3} more permissions",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
