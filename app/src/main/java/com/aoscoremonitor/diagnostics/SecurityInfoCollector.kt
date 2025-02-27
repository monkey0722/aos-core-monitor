package com.aoscoremonitor.diagnostics

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo as AndroidPermissionInfo
import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A class to collect security-related information from the Android system.
 * Includes SELinux status, app permissions, and hardware security module information.
 */
class SecurityInfoCollector(
    private val context: Context,
    private val onInfoUpdated: (SecurityInfo) -> Unit
) {
    /**
     * Data class representing the security information collected
     */
    data class SecurityInfo(
        val selinuxStatus: String,
        val selinuxMode: String,
        val permissionMap: Map<String, List<AppPermissionInfo>>,
        val hardwareSecurityInfo: HardwareSecurityInfo
    )

    /**
     * Data class for app permission details
     */
    data class AppPermissionInfo(
        val permissionName: String,
        val isGranted: Boolean,
        val isProtectionDangerous: Boolean
    )

    /**
     * Data class for hardware security information
     */
    data class HardwareSecurityInfo(
        val isHardwareBackedKeyStoreSupported: Boolean,
        val isStrongBoxBackedKeyStoreSupported: Boolean,
        val isFingerprintSupported: Boolean,
        val isBiometricSupported: Boolean,
        val isTeeSupported: Boolean,
        val keystoreVersion: String
    )

    // Dedicated CoroutineScope with SupervisorJob
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    /**
     * Starts collecting security information.
     */
    fun startCollecting() {
        if (job?.isActive == true) return

        job = scope.launch {
            while (isActive) {
                try {
                    val selinuxInfo = checkSELinuxStatus()
                    val permissionInfo = analyzeAppPermissions()
                    val hardwareSecurityInfo = checkHardwareSecurityStatus()

                    val securityInfo = SecurityInfo(
                        selinuxStatus = selinuxInfo.first,
                        selinuxMode = selinuxInfo.second,
                        permissionMap = permissionInfo,
                        hardwareSecurityInfo = hardwareSecurityInfo
                    )

                    withContext(Dispatchers.Main) {
                        onInfoUpdated(securityInfo)
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
                // Update every 5 seconds as security info changes less frequently
                delay(5000L)
            }
        }
    }

    /**
     * Stops collecting security information.
     */
    fun stopCollecting() {
        job?.cancel()
        job = null
    }

    /**
     * Checks the SELinux status using shell commands.
     * @return Pair of (status, mode) where status is "Enabled"/"Disabled" and mode is "Enforcing"/"Permissive"
     */
    private fun checkSELinuxStatus(): Pair<String, String> {
        var isEnabled = "Unknown"
        var mode = "Unknown"

        try {
            // Check if SELinux is enabled
            val processEnabled = Runtime.getRuntime().exec("getenforce")
            BufferedReader(InputStreamReader(processEnabled.inputStream)).use { reader ->
                val output = reader.readLine()
                isEnabled = if (output.isNullOrBlank()) "Unknown" else "Enabled"
                mode = output ?: "Unknown"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Pair(isEnabled, mode)
    }

    /**
     * Analyzes permissions for all installed applications.
     * @return Map of app package names to their permissions
     */
    private fun analyzeAppPermissions(): Map<String, List<AppPermissionInfo>> {
        val permissionMap = mutableMapOf<String, List<AppPermissionInfo>>()
        val packageManager = context.packageManager

        try {
            // Get all installed packages with requested permissions
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledPackages(
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            }

            for (packageInfo in packages) {
                // Skip system apps to focus on user-installed apps
                if ((packageInfo.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) != 0) continue

                val permissionInfoList = analyzePackagePermissions(packageInfo)
                if (permissionInfoList.isNotEmpty()) {
                    permissionMap[packageInfo.packageName] = permissionInfoList
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return permissionMap
    }

    /**
     * Analyzes permissions for a specific package.
     * @param packageInfo The PackageInfo object for the app
     * @return List of AppPermissionInfo objects
     */
    private fun analyzePackagePermissions(packageInfo: PackageInfo): List<AppPermissionInfo> {
        val permissionInfoList = mutableListOf<AppPermissionInfo>()
        val packageManager = context.packageManager
        val requestedPermissions = packageInfo.requestedPermissions ?: return emptyList()
        val requestedPermissionsFlags = packageInfo.requestedPermissionsFlags

        for (i in requestedPermissions.indices) {
            val permissionName = requestedPermissions[i]

            // Skip non-runtime permissions
            try {
                if (permissionName.startsWith("android.permission.")) {
                    val permInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getPermissionInfo(permissionName, 0)
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getPermissionInfo(permissionName, 0)
                    }

                    val isGranted = 
                        requestedPermissionsFlags?.get(i)?.and(PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    val isProtectionDangerous = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        (permInfo.protection and AndroidPermissionInfo.PROTECTION_DANGEROUS) == AndroidPermissionInfo.PROTECTION_DANGEROUS
                    } else {
                        @Suppress("DEPRECATION")
                        permInfo.protectionLevel == AndroidPermissionInfo.PROTECTION_DANGEROUS
                    }

                    permissionInfoList.add(
                        AppPermissionInfo(
                            permissionName = permissionName,
                            isGranted = isGranted,
                            isProtectionDangerous = isProtectionDangerous
                        )
                    )
                }
            } catch (e: Exception) {
                // Permission might not exist on this device
                continue
            }
        }

        return permissionInfoList
    }

    /**
     * Checks hardware security features.
     * @return HardwareSecurityInfo object
     */
    private fun checkHardwareSecurityStatus(): HardwareSecurityInfo {
        var isHardwareBackedKeyStoreSupported = false
        var isStrongBoxBackedKeyStoreSupported = false
        var isFingerprintSupported = false
        var isBiometricSupported = false
        var isTeeSupported = false
        var keystoreVersion = "Unknown"

        try {
            // Check for hardware-backed keystore
            val algorithm = "RSA"
            isHardwareBackedKeyStoreSupported = android.security.keystore.KeyProperties.isKeystoreBackedImplementation(
                algorithm
            )

            // Check for StrongBox support (Android 9+)
            isStrongBoxBackedKeyStoreSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            } else {
                false
            }

            // Check for fingerprint support
            isFingerprintSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)

            // Check for biometric support (Android 9+)
            isBiometricSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_BIOMETRIC)
            } else {
                isFingerprintSupported
            }

            // Check for TEE support (using fingerprint as proxy since it requires TEE)
            isTeeSupported = isFingerprintSupported

            // Get keystore version
            val keystoreProcess = Runtime.getRuntime().exec("getprop ro.hardware.keystore")
            BufferedReader(InputStreamReader(keystoreProcess.inputStream)).use { reader ->
                val version = reader.readLine()
                if (!version.isNullOrBlank()) {
                    keystoreVersion = version
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return HardwareSecurityInfo(
            isHardwareBackedKeyStoreSupported = isHardwareBackedKeyStoreSupported,
            isStrongBoxBackedKeyStoreSupported = isStrongBoxBackedKeyStoreSupported,
            isFingerprintSupported = isFingerprintSupported,
            isBiometricSupported = isBiometricSupported,
            isTeeSupported = isTeeSupported,
            keystoreVersion = keystoreVersion
        )
    }
}
