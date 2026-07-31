package com.aoscoremonitor.ui.components

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aoscoremonitor.ui.theme.Spacing

/**
 * Introduces a group of cards.
 *
 * Three screens each had their own version of this — one used `headlineMedium` with a divider,
 * one `titleLarge` with an icon, one a bare bold [Text] — so the same structural level looked
 * like three different things. This one marks itself as a heading so TalkBack users can jump
 * between sections, which none of the originals did.
 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Small)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = Spacing.Medium)
                    .size(24.dp)
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
