package com.aoscoremonitor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aoscoremonitor.ui.theme.Spacing

/**
 * The card every reading is presented on.
 *
 * Screens each declared their own `Card` with a hand-picked container color and elevation, which
 * is why the same kind of reading sat on `surfaceVariant` in one place and the default surface in
 * another. This takes its colors from [status] and its inset from [Spacing], so a card's
 * appearance follows from what it says.
 *
 * @param spacing between the rows. The default suits a card whose lines belong together — a title
 *   over its detail. A card that is a list of [LabeledValue]s wants [Spacing.Small], which callers
 *   were reaching by hanging a top padding on every row but the first.
 */
@Composable
fun MonitorCard(
    modifier: Modifier = Modifier,
    status: ReadingStatus = ReadingStatus.Neutral,
    spacing: Dp = Spacing.ExtraSmall,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = status.containerColor,
            contentColor = status.onContainerColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content
        )
    }
}

/**
 * A single named reading: an icon, what was measured, and the measurement.
 *
 * @param icon describes the subject, not the state, so it is decorative and stays out of the
 *   accessibility tree — [status] is what gets announced, via [StatusRow] or the card's color.
 */
@Composable
fun InfoCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    status: ReadingStatus = ReadingStatus.Neutral,
    valueStyle: TextStyle = MaterialTheme.typography.bodyLarge
) {
    MonitorCard(modifier = modifier, status = status) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (status == ReadingStatus.Neutral) {
                    MaterialTheme.colorScheme.primary
                } else {
                    status.color
                },
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.padding(start = Spacing.Medium)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = value,
                    style = valueStyle,
                    color = LocalContentColor.current.copy(alpha = 0.85f)
                )
            }
        }
    }
}
