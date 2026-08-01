package com.aoscoremonitor.ui.screens.jni

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aoscoremonitor.R
import com.aoscoremonitor.diagnostics.jni.CapabilitySet
import com.aoscoremonitor.diagnostics.jni.CapabilitySetKeys
import com.aoscoremonitor.diagnostics.jni.CredentialIds
import com.aoscoremonitor.diagnostics.jni.ProcessCredentials
import com.aoscoremonitor.diagnostics.jni.SeccompMode
import com.aoscoremonitor.diagnostics.jni.Unavailable
import com.aoscoremonitor.diagnostics.jni.androidGroupName
import com.aoscoremonitor.ui.components.FullScreenMessage
import com.aoscoremonitor.ui.components.LabeledValue
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.MonitorTag
import com.aoscoremonitor.ui.components.ReadingStatus
import com.aoscoremonitor.ui.components.SectionHeader
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.MonitorTypography
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.ProcessCredentialsUiState
import com.aoscoremonitor.ui.viewmodel.ProcessCredentialsViewModel

@Composable
fun ProcessCredentialsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProcessCredentialsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ProcessCredentialsContent(uiState = uiState, onNavigateBack = onNavigateBack, modifier = modifier)
}

@Composable
private fun ProcessCredentialsContent(uiState: ProcessCredentialsUiState, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    MonitorScaffold(
        title = stringResource(R.string.credentials_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) { innerPadding ->
        val credentials = uiState.credentials
        if (credentials == null) {
            FullScreenMessage(
                message = stringResource(if (uiState.hasLoaded) R.string.credentials_unavailable else R.string.credentials_loading),
                icon = Icons.Default.VerifiedUser,
                modifier = Modifier.padding(innerPadding)
            )
            return@MonitorScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            item(key = "context-header") {
                SectionHeader(
                    title = stringResource(R.string.credentials_context_section),
                    subtitle = stringResource(R.string.credentials_context_subtitle),
                    icon = Icons.Default.Shield
                )
            }
            item(key = "context") { ContextCard(credentials) }

            item(key = "user-header") {
                SectionHeader(
                    title = stringResource(R.string.credentials_user_section),
                    subtitle = stringResource(R.string.credentials_user_subtitle),
                    icon = Icons.Default.Badge
                )
            }
            item(key = "user") { UserCard(credentials) }

            item(key = "groups-header") {
                SectionHeader(
                    title = stringResource(R.string.credentials_groups),
                    icon = Icons.Default.Group
                )
            }
            item(key = "groups") { GroupsCard(credentials) }

            item(key = "capabilities-header") {
                SectionHeader(
                    title = stringResource(R.string.credentials_capabilities_section),
                    subtitle = stringResource(R.string.credentials_capabilities_subtitle),
                    icon = Icons.Default.VerifiedUser
                )
            }
            CapabilitySetKeys.forEach { key ->
                val set = credentials.capabilities[key] ?: return@forEach
                item(key = "capability-$key") { CapabilityCard(key = key, set = set) }
            }

            item(key = "sandbox-header") {
                SectionHeader(
                    title = stringResource(R.string.credentials_sandbox_section),
                    subtitle = stringResource(R.string.credentials_sandbox_subtitle),
                    icon = Icons.Default.Lock
                )
            }
            item(key = "sandbox") { SandboxCard(credentials) }

            item(key = "identity-header") {
                SectionHeader(title = stringResource(R.string.credentials_identity_section))
            }
            item(key = "identity") { IdentityCard(credentials) }
        }
    }
}

/**
 * The SELinux domain, with the type pulled out of the middle of it.
 *
 * The type is where the whole meaning is — `untrusted_app` against `platform_app` is the difference
 * between two very different sets of rights — and it would otherwise be the third field of a string
 * that runs to a hundred characters of MLS categories.
 */
@Composable
private fun ContextCard(credentials: ProcessCredentials, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        val context = credentials.selinuxContext
        if (context == null) {
            Text(
                text = stringResource(contextReason(credentials.selinuxUnavailable)),
                style = MaterialTheme.typography.bodyMedium
            )
            return@MonitorCard
        }

        credentials.selinuxType?.let { type ->
            Text(text = type, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(R.string.credentials_context_type),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = context,
            style = MonitorTypography.machineText,
            modifier = Modifier.padding(top = Spacing.Small)
        )
    }
}

@Composable
private fun UserCard(credentials: ProcessCredentials, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        credentials.user?.let { user ->
            LabeledValue(label = stringResource(R.string.credentials_uid), value = describe(user))
            Text(
                text = if (credentials.isAppUid) {
                    stringResource(R.string.credentials_app_uid, credentials.androidUserId ?: 0, credentials.appId ?: 0)
                } else {
                    stringResource(R.string.credentials_platform_uid)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        credentials.group?.let { group ->
            LabeledValue(
                label = stringResource(R.string.credentials_gid),
                value = describe(group),
                modifier = Modifier.padding(top = Spacing.Small)
            )
        }
    }
}

/**
 * The supplementary groups, which is how Android grants an app a platform capability: an app with
 * INTERNET is in AID_INET, and one without it is not, with the kernel enforcing the difference at
 * the socket call rather than the framework enforcing it at an API.
 */
@Composable
private fun GroupsCard(credentials: ProcessCredentials, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        if (credentials.supplementaryGroups.isEmpty()) {
            Text(
                text = stringResource(
                    if (credentials.groupsUnavailable != null) {
                        R.string.credentials_groups_unavailable
                    } else {
                        R.string.credentials_groups_none
                    }
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            return@MonitorCard
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            credentials.supplementaryGroups.forEach { gid ->
                val name = androidGroupName(gid)
                MonitorTag(
                    if (name != null) stringResource(R.string.count_with_name, gid, name) else gid.toString()
                )
            }
        }
    }
}

/**
 * One capability set.
 *
 * The effective set holding anything is worth pointing at: an app process is forked from zygote
 * with every capability dropped, so a non-empty effective set means this is not running as an
 * ordinary app. The other four sets are reported without judgement — a full bounding set is the
 * normal state of affairs and says only that nothing has narrowed the ceiling.
 */
@Composable
private fun CapabilityCard(key: String, set: CapabilitySet, modifier: Modifier = Modifier) {
    val status = if (key == EFFECTIVE_SET && !set.isEmpty) ReadingStatus.Warning else ReadingStatus.Neutral

    MonitorCard(modifier = modifier, status = status) {
        Text(text = stringResource(capabilityLabel(key)), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(capabilityNote(key)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (set.isEmpty) {
            Text(
                text = stringResource(R.string.credentials_cap_none),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.ExtraSmall)
            )
        } else {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.ExtraSmall),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                // The names as the kernel spells them, so they match the CAP_ constants and the
                // audit lines someone reading this screen would be comparing against.
                set.names.forEach { name -> MonitorTag(name) }
            }
        }

        Text(
            text = stringResource(R.string.credentials_cap_mask, set.hex),
            style = MonitorTypography.machineText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.ExtraSmall)
        )
    }
}

@Composable
private fun SandboxCard(credentials: ProcessCredentials, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        LabeledValue(
            label = stringResource(R.string.credentials_seccomp),
            value = seccompDescription(credentials)
        )
        LabeledValue(
            label = stringResource(R.string.credentials_no_new_privs),
            value = stringResource(
                when (credentials.noNewPrivs) {
                    true -> R.string.credentials_no_new_privs_set
                    false -> R.string.credentials_no_new_privs_clear
                    null -> R.string.status_not_reported
                }
            ),
            modifier = Modifier.padding(top = Spacing.Small)
        )
        LabeledValue(
            label = stringResource(R.string.credentials_dumpable),
            value = stringResource(
                when (credentials.dumpable) {
                    true -> R.string.credentials_dumpable_on
                    false -> R.string.credentials_dumpable_off
                    null -> R.string.status_not_reported
                }
            ),
            modifier = Modifier.padding(top = Spacing.Small)
        )
        LabeledValue(
            label = stringResource(R.string.credentials_umask),
            value = credentials.umask ?: stringResource(R.string.status_not_reported),
            valueStyle = MonitorTypography.machineText,
            modifier = Modifier.padding(top = Spacing.Small)
        )
    }
}

@Composable
private fun IdentityCard(credentials: ProcessCredentials, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        LabeledValue(label = stringResource(R.string.credentials_pid), value = credentials.pid.toString())
        LabeledValue(
            label = stringResource(R.string.credentials_ppid),
            value = credentials.parentPid.toString(),
            modifier = Modifier.padding(top = Spacing.Small)
        )
        LabeledValue(
            label = stringResource(R.string.credentials_pgid),
            value = credentials.processGroup.toString(),
            modifier = Modifier.padding(top = Spacing.Small)
        )
        credentials.session?.let { session ->
            LabeledValue(
                label = stringResource(R.string.credentials_session),
                value = session.toString(),
                modifier = Modifier.padding(top = Spacing.Small)
            )
        }
    }
}

/** The three ids, collapsed to one number when they agree — which for an app they always do. */
@Composable
private fun describe(ids: CredentialIds): String = if (ids.allTheSame) {
    stringResource(R.string.credentials_ids_uniform, ids.effective)
} else {
    stringResource(R.string.credentials_ids, ids.real, ids.effective, ids.saved)
}

@Composable
private fun seccompDescription(credentials: ProcessCredentials): String {
    val filters = credentials.seccompFilters
    return when (credentials.seccomp) {
        SeccompMode.Disabled -> stringResource(R.string.credentials_seccomp_disabled)
        SeccompMode.Strict -> stringResource(R.string.credentials_seccomp_strict)
        SeccompMode.Filter -> if (filters != null && filters > 1) {
            stringResource(R.string.credentials_seccomp_filter_count, filters)
        } else {
            stringResource(R.string.credentials_seccomp_filter)
        }
        SeccompMode.Unknown -> stringResource(R.string.credentials_seccomp_unknown)
    }
}

@StringRes
private fun capabilityLabel(key: String): Int = when (key) {
    "effective" -> R.string.credentials_cap_effective
    "permitted" -> R.string.credentials_cap_permitted
    "inheritable" -> R.string.credentials_cap_inheritable
    "bounding" -> R.string.credentials_cap_bounding
    else -> R.string.credentials_cap_ambient
}

@StringRes
private fun capabilityNote(key: String): Int = when (key) {
    "effective" -> R.string.credentials_cap_effective_note
    "permitted" -> R.string.credentials_cap_permitted_note
    "inheritable" -> R.string.credentials_cap_inheritable_note
    "bounding" -> R.string.credentials_cap_bounding_note
    else -> R.string.credentials_cap_ambient_note
}

/** Why the context is missing, when the read said why. */
@StringRes
private fun contextReason(reason: Unavailable?): Int = when (reason) {
    Unavailable.Denied -> R.string.credentials_context_denied
    Unavailable.Absent -> R.string.credentials_context_absent
    else -> R.string.credentials_context_unavailable
}

private const val EFFECTIVE_SET = "effective"

@Preview(name = "Credentials", showBackground = true)
@Preview(name = "Credentials (dark)", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProcessCredentialsPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        ProcessCredentialsContent(
            uiState = ProcessCredentialsUiState(
                credentials = ProcessCredentials(
                    pid = 8_421,
                    parentPid = 731,
                    processGroup = 8_421,
                    session = 8_421,
                    user = CredentialIds(10_213, 10_213, 10_213),
                    group = CredentialIds(10_213, 10_213, 10_213),
                    supplementaryGroups = listOf(3_003, 9_997, 20_213, 50_213),
                    capabilities = mapOf(
                        "effective" to CapabilitySet("0000000000000000"),
                        "permitted" to CapabilitySet("0000000000000000"),
                        "inheritable" to CapabilitySet("0000000000000000"),
                        "bounding" to CapabilitySet(
                            "000001ffffffffff",
                            listOf("CAP_CHOWN", "CAP_DAC_OVERRIDE", "CAP_NET_RAW", "CAP_SYS_ADMIN")
                        ),
                        "ambient" to CapabilitySet("0000000000000000")
                    ),
                    noNewPrivs = false,
                    dumpable = true,
                    seccomp = SeccompMode.Filter,
                    seccompFilters = 1,
                    umask = "0077",
                    selinuxContext = "u:r:untrusted_app:s0:c213,c256,c512,c768"
                ),
                hasLoaded = true
            ),
            onNavigateBack = {}
        )
    }
}
