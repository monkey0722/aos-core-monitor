package com.aoscoremonitor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

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
