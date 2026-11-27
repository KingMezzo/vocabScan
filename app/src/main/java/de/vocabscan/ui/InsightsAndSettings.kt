package de.vocabscan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.vocabscan.data.StudyLanguage
import de.vocabscan.data.Deck
import de.vocabscan.learn.LearningStats
import de.vocabscan.data.ReviewLog
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun InsightsScreen(stats: LearningStats, logs: List<ReviewLog>, dailyGoal: Int) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Dein Lernweg", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("So entwickelt sich dein Lernfortschritt.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Ink, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Heute wiederholt", style = MaterialTheme.typography.titleMedium)
                        Text("${stats.today} / $dailyGoal", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                        Text(if (stats.today >= dailyGoal) "Tagesziel erreicht" else "Wiederholungen bis zum Tagesziel",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(48.dp), tint = InkAccent)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InsightMetric("${stats.due}", "Fällig", "Jetzt lernbar", Modifier.weight(1f))
                InsightMetric("${stats.streak}", "Tage in Folge", "Bis gestern/heute", Modifier.weight(1f))
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("Wochenfortschritt", style = MaterialTheme.typography.titleLarge)
                    Text("Letzte 7 Tage", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${stats.week.count { it.count >= dailyGoal }} von 7 Tagen mit erreichtem Ziel",
                        color = MaterialTheme.colorScheme.primary)
                    val maxCount = stats.week.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        stats.week.forEach { day ->
                            Column(
                                Modifier.weight(1f).heightIn(min = 146.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                Text(day.count.toString(), style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(6.dp))
                                val fraction = (if (day.count == 0) 5f else 10f + 86f * day.count / maxCount) / 96f
                                val barScale = animateFloatAsState(fraction, animationSpec = tween(360), label = "Tagesbalken")
                                Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.BottomCenter) {
                                    Surface(
                                        Modifier.fillMaxWidth(0.65f).fillMaxHeight().graphicsLayer {
                                            transformOrigin = TransformOrigin(0.5f, 1f)
                                            scaleY = barScale.value
                                        },
                                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                                        color = if (day.count >= dailyGoal) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.secondaryContainer,
                                    ) {}
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        item { LearningCalendar(logs, dailyGoal) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InsightMetric("${stats.totalReviews}", "Bewertungen", "Insgesamt", Modifier.weight(1f))
                InsightMetric(
                    stats.goodOrEasyPercent?.let { "$it %" } ?: "–",
                    "Erfolgreich", "Letzte 30 Tage", Modifier.weight(1f),
                )
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Lernfortschritt", style = MaterialTheme.typography.titleLarge)
                    Text("${stats.studiedDirections} von ${stats.totalDirections} Lernrichtungen begonnen")
                    LinearProgressIndicator(
                        progress = { if (stats.totalDirections == 0) 0f else stats.studiedDirections.toFloat() / stats.totalDirections },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Jede Karte zählt in beiden Lernrichtungen.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Text("Deine Listen", style = MaterialTheme.typography.titleLarge) }
        if (stats.decks.isEmpty()) item {
            Text("Sobald du eine Liste anlegst, erscheint hier ihr Fortschritt.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(stats.decks.size) { index ->
            val deck = stats.decks[index]
            Column(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(deck.name + if (deck.archived) " · Archiv" else "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(if (deck.type.isFlashcards) "Lernkarten" else StudyLanguage.fromCode(deck.language).label, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    LinearProgressIndicator(
                        progress = { if (deck.total == 0) 0f else deck.studied.toFloat() / deck.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${deck.studied}/${deck.total} begonnen · ${deck.due} fällig",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        item {
            Text("Die Statistik umfasst aktive Listen. Im Lernkalender und Wochenfortschritt bleiben auch frühere Wiederholungen archivierter Listen erhalten.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InsightMetric(value: String, title: String, subtitle: String, modifier: Modifier) {
    Column(modifier) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    decks: List<Deck>,
    onOnboarding: () -> Unit,
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    remindersEnabled: Boolean,
    onRemindersChange: (Boolean) -> Unit,
    busy: Boolean,
    onExport: (Set<String>) -> Unit,
    onShareCsv: (Set<String>) -> Unit,
    onImport: () -> Unit,
    onFullExport: () -> Unit,
    onFullImport: () -> Unit,
    dailyGoal: Int,
    onDailyGoalChange: (Int) -> Unit,
    installedVersion: String = "",
    updatesEnabled: Boolean = true,
    checkingUpdates: Boolean = false,
    onCheckUpdates: () -> Unit = {},
    automaticUpdates: Boolean = true,
    onAutomaticUpdatesChange: (Boolean) -> Unit = {},
    updateCheckMessage: String? = null,
) {
    var showCsvSelection by remember { mutableStateOf(false) }
    var selectedCsvIds by remember { mutableStateOf(emptySet<String>()) }
    val availableIds = decks.map { it.id }.toSet()
    val chosenIds = selectedCsvIds.intersect(availableIds)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Deine App", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Passe das Lernen an und sichere deine Listen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Darstellung", style = MaterialTheme.typography.titleLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEach { option ->
                            FilterChip(
                                selected = mode == option,
                                onClick = { onModeChange(option) },
                                label = { Text(option.title) },
                            )
                        }
                    }
                    TextButton(onClick = onOnboarding) { Text("Einführung noch einmal ansehen") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tagesziel", style = MaterialTheme.typography.titleLarge)
                    Text("Wie viele Wiederholungen möchtest du pro Tag schaffen?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(5, 10, 20, 30).forEach { goal ->
                            FilterChip(selected = dailyGoal == goal, onClick = { onDailyGoalChange(goal) },
                                label = { Text("$goal") }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Lernerinnerung", style = MaterialTheme.typography.titleMedium)
                        Text("Etwa um 18 Uhr, wenn Karten fällig sind.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = remindersEnabled, enabled = !busy, onCheckedChange = onRemindersChange,
                        modifier = Modifier.semantics { contentDescription = "Lernerinnerung" })
                }
            }
        }
        item {
            Text("Daten & Sicherung", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            Text("Deine Listen bleiben auf deinem Gerät. Sichere sie regelmäßig.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                SettingsAction(Icons.Outlined.SaveAlt, "Vollständig sichern", "ZIP mit Karten, Lernständen und Bildern", !busy, onFullExport)
                HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                SettingsAction(Icons.Outlined.Restore, "Sicherung wiederherstellen", "Ersetzt die aktuellen App-Daten", !busy, onFullImport)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                SettingsAction(Icons.Outlined.IosShare, "CSV exportieren oder teilen", "Listen auswählen und als Datei speichern oder versenden", !busy && decks.isNotEmpty()) {
                    selectedCsvIds = availableIds
                    showCsvSelection = true
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                SettingsAction(Icons.Outlined.CloudDownload, "CSV importieren", "Karten aus einer Datei hinzufügen", !busy, onImport)
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("App-Version", style = MaterialTheme.typography.titleLarge)
                    Text("Installiert: $installedVersion", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!updatesEnabled) {
                        Text("Updateprüfung ist in der DEV-Version deaktiviert.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Automatisch nach Updates suchen", style = MaterialTheme.typography.bodyLarge)
                                Text("Beim App-Start, höchstens einmal täglich.", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = automaticUpdates, onCheckedChange = onAutomaticUpdatesChange,
                                modifier = Modifier.semantics { contentDescription = "Automatisch nach Updates suchen" })
                        }
                        TextButton(onClick = onCheckUpdates, enabled = !checkingUpdates && !busy,
                            modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (checkingUpdates) "Suche nach Updates …" else "Nach Updates suchen")
                        }
                        if (checkingUpdates) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (updateCheckMessage != null) Text(updateCheckMessage, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (showCsvSelection) AlertDialog(
        onDismissRequest = { showCsvSelection = false },
        title = { Text("Listen als CSV") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text("Wähle die Listen aus. Die CSV enthält Karten und Notizen, aber keine Lernstände oder Bilder.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().clickable {
                    selectedCsvIds = if (chosenIds.size == decks.size) emptySet() else availableIds
                }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = decks.isNotEmpty() && chosenIds.size == decks.size,
                        onCheckedChange = { selectedCsvIds = if (it) availableIds else emptySet() })
                    Text("Alle Listen (${decks.size})")
                }
                decks.forEach { deck ->
                    val checked = deck.id in chosenIds
                    Row(Modifier.fillMaxWidth().clickable {
                        selectedCsvIds = if (checked) chosenIds - deck.id else chosenIds + deck.id
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = {
                            selectedCsvIds = if (it) chosenIds + deck.id else chosenIds - deck.id
                        })
                        Column {
                            Text(deck.name)
                            Text(if (deck.type.isFlashcards) "Lernkarten" else StudyLanguage.fromCode(deck.language).label,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onExport(chosenIds); showCsvSelection = false }, enabled = chosenIds.isNotEmpty() && !busy) {
                    Text("Speichern")
                }
                Button(onClick = { onShareCsv(chosenIds); showCsvSelection = false }, enabled = chosenIds.isNotEmpty() && !busy) {
                    Text("Teilen")
                }
            }
        },
        dismissButton = { TextButton(onClick = { showCsvSelection = false }) { Text("Abbrechen") } },
    )
}

@Composable
private fun SettingsAction(icon: ImageVector, title: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
