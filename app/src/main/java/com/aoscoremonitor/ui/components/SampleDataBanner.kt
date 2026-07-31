package com.aoscoremonitor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aoscoremonitor.R
import com.aoscoremonitor.ui.theme.Spacing

/**
 * Warns that the section below shows built-in sample data rather than values read from the device.
 *
 * Every screen that can fall back to samples uses this, so the distinction always looks the same.
 *
 * Carries the warning color rather than the error color it used to: samples are a limitation of
 * the device, not a fault. Painting them in `errorContainer` put them at the same visual weight as
 * a real problem, and on the network and TCP screens that meant most of the screen was red.
 */
@Composable
fun SampleDataBanner(message: String, modifier: Modifier = Modifier) {
    val status = ReadingStatus.Warning
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = status.containerColor,
            contentColor = status.onContainerColor
        )
    ) {
        Row(modifier = Modifier.padding(Spacing.Large)) {
            Icon(
                imageVector = status.icon,
                contentDescription = null,
                tint = status.onContainerColor,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.padding(start = Spacing.Medium)) {
                Text(
                    text = stringResource(R.string.sample_data_label),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
