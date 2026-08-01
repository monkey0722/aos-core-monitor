package com.aoscoremonitor.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aoscoremonitor.R
import com.aoscoremonitor.diagnostics.CutoutInsets
import com.aoscoremonitor.diagnostics.DisplayConnection
import com.aoscoremonitor.diagnostics.DisplayInfo
import com.aoscoremonitor.diagnostics.DisplayMode
import com.aoscoremonitor.diagnostics.DisplayProduct
import com.aoscoremonitor.diagnostics.DisplayState
import com.aoscoremonitor.diagnostics.HdrType
import com.aoscoremonitor.ui.components.FullScreenMessage
import com.aoscoremonitor.ui.components.LabeledValue
import com.aoscoremonitor.ui.components.MonitorCard
import com.aoscoremonitor.ui.components.MonitorScaffold
import com.aoscoremonitor.ui.components.MonitorTag
import com.aoscoremonitor.ui.components.SectionHeader
import com.aoscoremonitor.ui.theme.AOSCoreMonitorTheme
import com.aoscoremonitor.ui.theme.Spacing
import com.aoscoremonitor.ui.viewmodel.DisplayUiState
import com.aoscoremonitor.ui.viewmodel.DisplayViewModel
import com.aoscoremonitor.ui.viewmodel.monitorViewModel

@Composable
fun DisplayScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DisplayViewModel = monitorViewModel { DisplayViewModel(it) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisplayContent(uiState = uiState, onNavigateBack = onNavigateBack, modifier = modifier)
}

@Composable
private fun DisplayContent(uiState: DisplayUiState, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    MonitorScaffold(
        title = stringResource(R.string.display_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) { innerPadding ->
        val display = uiState.display
        if (display == null) {
            FullScreenMessage(
                message = stringResource(if (uiState.hasLoaded) R.string.display_unavailable else R.string.display_loading),
                icon = Icons.Default.Monitor,
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
            item(key = "panel-header") {
                SectionHeader(
                    title = stringResource(R.string.display_panel_section),
                    subtitle = stringResource(R.string.display_panel_subtitle),
                    icon = Icons.Default.Monitor
                )
            }
            item(key = "panel") { PanelCard(display) }

            item(key = "geometry-header") {
                SectionHeader(
                    title = stringResource(R.string.display_geometry_section),
                    subtitle = stringResource(R.string.display_geometry_subtitle),
                    icon = Icons.Default.AspectRatio
                )
            }
            item(key = "geometry") { GeometryCard(display) }

            item(key = "modes-header") {
                SectionHeader(
                    title = stringResource(R.string.display_modes_section),
                    subtitle = stringResource(R.string.display_modes_subtitle),
                    icon = Icons.Default.Refresh
                )
            }
            items(display.modes, key = { mode -> mode.id }) { mode ->
                ModeCard(mode = mode, isCurrent = mode.id == display.currentMode?.id)
            }

            item(key = "capabilities-header") {
                SectionHeader(
                    title = stringResource(R.string.display_capabilities_section),
                    subtitle = stringResource(R.string.display_capabilities_subtitle),
                    icon = Icons.Default.Palette
                )
            }
            item(key = "capabilities") { CapabilitiesCard(display) }
        }
    }
}

@Composable
private fun PanelCard(display: DisplayInfo, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        LabeledValue(label = stringResource(R.string.display_name), value = display.name)
        LabeledValue(
            label = stringResource(R.string.display_state),
            value = stringResource(stateLabel(display.state)),
            modifier = Modifier.padding(top = Spacing.Small)
        )
        LabeledValue(
            label = stringResource(R.string.display_rotation),
            value = stringResource(R.string.display_degrees, display.rotationDegrees),
            modifier = Modifier.padding(top = Spacing.Small)
        )
        LabeledValue(
            label = stringResource(R.string.display_id),
            value = display.displayId.toString(),
            modifier = Modifier.padding(top = Spacing.Small)
        )
        ProductRows(display.product)
    }
}

/**
 * What the panel says about itself, when it says anything.
 *
 * A screen built into a phone has no EDID for the platform to parse, so this is empty on most
 * devices and says why rather than showing four blank rows.
 */
@Composable
private fun ProductRows(product: DisplayProduct) {
    if (!product.hasIdentity) {
        Text(
            text = stringResource(R.string.display_product_absent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.Small)
        )
        return
    }

    product.manufacturerPnpId?.let { manufacturer ->
        LabeledValue(
            label = stringResource(R.string.display_product_manufacturer),
            value = manufacturer,
            modifier = Modifier.padding(top = Spacing.Small)
        )
    }
    product.productId?.let { id ->
        LabeledValue(
            label = stringResource(R.string.display_product_id),
            value = id,
            modifier = Modifier.padding(top = Spacing.Small)
        )
    }
    product.manufactureYear?.let { year ->
        LabeledValue(
            label = stringResource(R.string.display_product_year),
            value = year.toString(),
            modifier = Modifier.padding(top = Spacing.Small)
        )
    }
    LabeledValue(
        label = stringResource(R.string.display_product_connection),
        value = stringResource(connectionLabel(product.connection)),
        modifier = Modifier.padding(top = Spacing.Small)
    )
}

@Composable
private fun GeometryCard(display: DisplayInfo, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        display.currentMode?.let { mode ->
            LabeledValue(
                label = stringResource(R.string.display_resolution),
                value = stringResource(R.string.display_pixels, mode.widthPixels, mode.heightPixels)
            )
        }
        LabeledValue(
            label = stringResource(R.string.display_density),
            value = stringResource(R.string.display_density_value, display.densityDpi, display.density),
            modifier = Modifier.padding(top = Spacing.Small)
        )
        // The dpi the panel reports, as against the bucket Android rounded it into above: the two
        // differ on most devices, and only the first is a physical measurement.
        LabeledValue(
            label = stringResource(R.string.display_physical_density),
            value = stringResource(R.string.display_physical_density_value, display.xDpi, display.yDpi),
            modifier = Modifier.padding(top = Spacing.Small)
        )
        LabeledValue(
            label = stringResource(R.string.display_font_scale),
            value = stringResource(R.string.display_font_scale_value, display.fontScale),
            modifier = Modifier.padding(top = Spacing.Small)
        )
    }
}

@Composable
private fun ModeCard(mode: DisplayMode, isCurrent: Boolean, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.display_pixels, mode.widthPixels, mode.heightPixels),
                style = MaterialTheme.typography.titleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                if (isCurrent) MonitorTag(stringResource(R.string.display_mode_current))
                MonitorTag(stringResource(R.string.display_mode_hz, mode.refreshRateHz))
            }
        }
        Text(
            text = stringResource(R.string.display_mode_id, mode.id),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CapabilitiesCard(display: DisplayInfo, modifier: Modifier = Modifier) {
    MonitorCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.display_hdr),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (display.hdrTypes.isEmpty()) {
            Text(text = stringResource(R.string.display_hdr_none), style = MaterialTheme.typography.bodyMedium)
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                display.hdrTypes.forEach { type -> MonitorTag(stringResource(hdrLabel(type))) }
            }
        }

        SupportRow(R.string.display_wide_gamut, display.isWideColorGamut)
        SupportRow(R.string.display_minimal_post_processing, display.supportsMinimalPostProcessing)
        SupportRow(R.string.display_secure, display.isSecure)
        SupportRow(R.string.display_round, display.isRound)

        LabeledValue(
            label = stringResource(R.string.display_cutout),
            value = cutoutDescription(display.cutout),
            modifier = Modifier.padding(top = Spacing.Small)
        )
        display.cutout?.let { cutout ->
            Text(
                text = pluralStringResource(R.plurals.display_cutout_rects, cutout.boundingRects, cutout.boundingRects),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SupportRow(@StringRes label: Int, supported: Boolean) {
    LabeledValue(
        label = stringResource(label),
        value = stringResource(if (supported) R.string.display_supported else R.string.display_not_supported),
        modifier = Modifier.padding(top = Spacing.Small)
    )
}

@Composable
private fun cutoutDescription(cutout: CutoutInsets?): String = if (cutout == null) {
    stringResource(R.string.display_cutout_none)
} else {
    stringResource(R.string.display_cutout_insets, cutout.top, cutout.bottom, cutout.left, cutout.right)
}

@StringRes
private fun stateLabel(state: DisplayState): Int = when (state) {
    DisplayState.On -> R.string.display_state_on
    DisplayState.Off -> R.string.display_state_off
    DisplayState.Doze -> R.string.display_state_doze
    DisplayState.DozeSuspended -> R.string.display_state_doze_suspended
    DisplayState.OnSuspended -> R.string.display_state_on_suspended
    DisplayState.Unknown -> R.string.display_state_unknown
}

@StringRes
private fun connectionLabel(connection: DisplayConnection): Int = when (connection) {
    DisplayConnection.BuiltIn -> R.string.display_connection_built_in
    DisplayConnection.Direct -> R.string.display_connection_direct
    DisplayConnection.Transitive -> R.string.display_connection_transitive
    DisplayConnection.Unknown -> R.string.display_connection_unknown
}

@StringRes
private fun hdrLabel(type: HdrType): Int = when (type) {
    HdrType.DolbyVision -> R.string.display_hdr_dolby_vision
    HdrType.Hdr10 -> R.string.display_hdr_hdr10
    HdrType.Hlg -> R.string.display_hdr_hlg
    HdrType.Hdr10Plus -> R.string.display_hdr_hdr10_plus
    HdrType.Unknown -> R.string.display_hdr_unknown
}

@Preview(name = "Display", showBackground = true)
@Preview(name = "Display (dark)", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DisplayPreview() {
    AOSCoreMonitorTheme(dynamicColor = false) {
        DisplayContent(
            uiState = DisplayUiState(
                display = DisplayInfo(
                    name = "Built-in Screen",
                    displayId = 0,
                    state = DisplayState.On,
                    rotationDegrees = 0,
                    currentMode = DisplayMode(1, 1080, 2400, 120f),
                    modes = listOf(
                        DisplayMode(1, 1080, 2400, 120f),
                        DisplayMode(2, 1080, 2400, 60f)
                    ),
                    densityDpi = 420,
                    density = 2.625f,
                    xDpi = 425.6f,
                    yDpi = 424.1f,
                    fontScale = 1f,
                    hdrTypes = listOf(HdrType.Hdr10, HdrType.Hlg),
                    isWideColorGamut = true,
                    cutout = CutoutInsets(left = 0, top = 118, right = 0, bottom = 0, boundingRects = 1),
                    supportsMinimalPostProcessing = true,
                    appVsyncOffsetNanos = 1_000_000,
                    presentationDeadlineNanos = 12_000_000
                ),
                hasLoaded = true
            ),
            onNavigateBack = {}
        )
    }
}
