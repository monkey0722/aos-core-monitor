package com.aoscoremonitor.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            // SELinux Status Section
            item {
                SecuritySectionTitle(title = "SELinux Status", Icons.Filled.Security)
                SELinuxStatusCard(
                    status = securityInfo.selinuxStatus,
                    mode = securityInfo.selinuxMode
                )
            }

            // Hardware Security Module Section
            item {
                SecuritySectionTitle(title = "Hardware Security", Icons.Filled.Security)
                HardwareSecurityCard(hardwareInfo = securityInfo.hardwareSecurityInfo)
            }

            // App Permissions Section
            item {
                SecuritySectionTitle(title = "App Permissions (Non-System Apps)", Icons.Filled.Info)
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
fun SecuritySectionTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SELinuxStatusCard(status: String, mode: String) {
    val isEnforcing = mode.contains("Enforcing", ignoreCase = true)
    val isPermissive = mode.contains("Permissive", ignoreCase = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isEnforcing -> MaterialTheme.colorScheme.primaryContainer
                isPermissive -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Status: $status",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Mode: $mode",
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    isEnforcing -> MaterialTheme.colorScheme.primary
                    isPermissive -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                },
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun HardwareSecurityCard(hardwareInfo: SecurityInfoCollector.HardwareSecurityInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Keystore Implementation: ${hardwareInfo.keystoreVersion}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SecurityFeatureItem(title: String, isSupported: Boolean) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSupported) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = if (isSupported) "Supported" else "Not Supported",
            tint = if (isSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun AppPermissionCard(
    packageName: String,
    permissions: List<SecurityInfoCollector.AppPermissionInfo>
) {
    val hasDangerousPermissions = permissions.any { it.isProtectionDangerous && it.isGranted }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (hasDangerousPermissions) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
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
                color = if (hasDangerousPermissions) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Show the first 3 permissions only to avoid cluttering the UI
            permissions.take(3).forEach { permission ->
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            permission.isGranted && permission.isProtectionDangerous -> Icons.Filled.Error
                            permission.isGranted -> Icons.Filled.CheckCircle
                            else -> Icons.Filled.Info
                        },
                        contentDescription = "Permission Status",
                        tint = when {
                            permission.isGranted && permission.isProtectionDangerous -> MaterialTheme.colorScheme.error
                            permission.isGranted -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
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
