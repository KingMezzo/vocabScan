package de.vocabscan.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class TransferOption(
    val label: String,
    val icon: ImageVector,
    val description: String? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferOptionsSheet(
    title: String,
    options: List<TransferOption>,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        BackHandler(enabled = onBack != null) { onBack?.invoke() }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onBack != null) TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                Spacer(Modifier.width(8.dp))
                Text("Zurück")
            }
            Text(title, style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
            options.forEach { option ->
                val contentColor = if (option.enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                ListItem(
                    headlineContent = { Text(option.label) },
                    supportingContent = option.description?.let { description -> { Text(description) } },
                    leadingContent = { Icon(option.icon, null) },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        headlineColor = contentColor,
                        leadingIconColor = if (option.enabled) MaterialTheme.colorScheme.primary else contentColor,
                        supportingColor = if (option.enabled) MaterialTheme.colorScheme.onSurfaceVariant else contentColor,
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable(enabled = option.enabled, role = Role.Button, onClick = option.onClick),
                )
            }
        }
    }
}
