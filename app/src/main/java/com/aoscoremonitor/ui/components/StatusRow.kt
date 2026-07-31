package com.aoscoremonitor.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aoscoremonitor.ui.theme.Spacing

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
