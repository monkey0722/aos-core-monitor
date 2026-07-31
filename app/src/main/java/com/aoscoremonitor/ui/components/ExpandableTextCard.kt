package com.aoscoremonitor.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aoscoremonitor.R
import com.aoscoremonitor.ui.theme.MonitorTypography
import com.aoscoremonitor.ui.theme.Spacing

/**
 * A card holding tool output too long to show in full, with a control to show the rest.
 *
 * The diagnostics screen previously gave these a fixed height and its own `verticalScroll`, while
 * the screen around them scrolled vertically too. Nesting two scrollables on the same axis leaves
 * the user guessing which one a drag will move, and pinned the content to a height that suited
 * neither short nor long output. Expanding in place removes the inner scroller entirely.
 */
@Composable
fun ExpandableTextCard(
    title: String,
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    collapsedLines: Int = 8
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val canExpand = text.lineSequence().take(collapsedLines + 1).count() > collapsedLines

    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )

    // A short reading gets a plain card rather than a clickable one with `enabled = false`.
    // Disabling a card dims its content, so output that simply fitted looked unavailable.
    val cardContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Large)
                .animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(start = Spacing.Medium)
                        .weight(1f)
                )
                if (canExpand) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.action_collapse else R.string.action_expand
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Prose about the reading, in the body font rather than the machine one below: what
            // limits a reading is not part of it. The diagnostics screen uses this to say that the
            // platform will only ever report one process here.
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.Small)
                )
            }
            Spacer(modifier = Modifier.padding(top = Spacing.Small))
            Text(
                text = text,
                style = MonitorTypography.machineText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else collapsedLines
            )
        }
    }

    if (canExpand) {
        Card(
            onClick = { expanded = !expanded },
            modifier = modifier.fillMaxWidth(),
            colors = colors
        ) { cardContent() }
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors) { cardContent() }
    }
}
