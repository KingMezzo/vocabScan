package de.vocabscan.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun AdaptiveAppBody(
    padding: PaddingValues, showRail: Boolean, maxWidth: Dp,
    navigation: @Composable () -> Unit, content: @Composable BoxScope.() -> Unit,
) {
    Row(Modifier.fillMaxSize().padding(padding)) {
        if (showRail) navigation()
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = maxWidth).fillMaxSize(), content = content)
        }
    }
}

@Composable
internal fun NavigationLabel(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium.copy(
        hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph, localeList = LocaleList("de")))
}

@Composable
internal fun FavoriteButton(favorite: Boolean, word: String, enabled: Boolean, expanded: Boolean = false,
    onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(favorite) }
    LaunchedEffect(favorite) {
        if (previous != favorite) {
            scale.snapTo(0.78f)
            scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 450f))
        }
        previous = favorite
    }
    val label = if (favorite) "Favorit entfernen" else "Als Favorit merken"
    val semantics = Modifier.semantics {
        stateDescription = "$word, ${if (favorite) "als Favorit gespeichert" else "kein Favorit"}"
    }
    val action = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
    if (expanded) {
        TextButton(onClick = action, enabled = enabled, modifier = semantics) {
            Icon(if (favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline, null,
                Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value })
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    } else {
        IconToggleButton(checked = favorite, onCheckedChange = { action() }, enabled = enabled, modifier = semantics) {
            Icon(if (favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline, label,
                Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun WordPairText(term: String, translation: String) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (largeText || maxWidth < 240.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(term, style = MaterialTheme.typography.titleMedium)
                Text(translation, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(term, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(translation, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun EmptyStatePanel(title: String, description: String, actionLabel: String? = null,
    onAction: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.SearchOff, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
