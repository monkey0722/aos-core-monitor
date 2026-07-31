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
import androidx.compose.ui.unit.dp
import com.aoscoremonitor.ui.theme.Spacing

/**
 * The card every reading is presented on.
 *
 * Screens each declared their own `Card` with a hand-picked container color and elevation, which
 * is why the same kind of reading sat on `surfaceVariant` in one place and the default surface in
 * another. This takes its colors from [status] and its inset from [Spacing], so a card's
 * appearance follows from what it says.
 */
@Composable
fun MonitorCard(modifier: Modifier = Modifier, status: ReadingStatus = ReadingStatus.Neutral, content: @Composable ColumnScope.() -> Unit) {
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
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
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

/**
 * A label and its value stacked as one unit.
 *
 * Screens were writing `"Version: ${x}"` into a single [Text], which glues the label to the value
 * for a screen reader and leaves no way to style them apart. Keeping them separate fixes both.
 */
@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, valueStyle: TextStyle = MaterialTheme.typography.bodyMedium) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = valueStyle)
    }
}

/**
 * A row stating one thing and whether it is in good shape.
 *
 * The status icon carries a spoken description because the color on its own conveys nothing to
 * TalkBack or to a colorblind user.
 */
@Composable
fun StatusRow(label: String, status: ReadingStatus, statusDescription: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = status.icon,
            contentDescription = statusDescription,
            tint = status.color,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = Spacing.Small)
        )
    }
}
