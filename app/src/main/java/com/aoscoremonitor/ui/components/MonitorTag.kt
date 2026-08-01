package com.aoscoremonitor.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aoscoremonitor.ui.theme.Spacing

/**
 * A word about the thing next to it: a filesystem's type, a descriptor's kind.
 *
 * One classifying word, set apart from the reading it qualifies. It carries no state — use
 * [StatusRow] or a [MonitorCard] status for anything that says whether something is in good shape,
 * since a pill that changes colour says nothing to TalkBack.
 */
@Composable
fun MonitorTag(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.ExtraSmall)
        )
    }
}
