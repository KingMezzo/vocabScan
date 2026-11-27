package de.vocabscan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.vocabscan.data.Deck
import de.vocabscan.data.Folder
import de.vocabscan.ui.components.TransferOption
import de.vocabscan.ui.components.TransferOptionsSheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeckManagementSheet(deck: Deck, decks: List<Deck>, folders: List<Folder>, busy: Boolean, onDismiss: () -> Unit,
    onFolder: (String?) -> Unit, onArchive: (Boolean) -> Unit, onRename: () -> Unit, onDelete: () -> Unit,
    onDuplicate: (String, Boolean) -> Unit, onMerge: (String) -> Unit) {
    var page by rememberSaveable(deck.id) { mutableStateOf("organize") }
    var folderId by rememberSaveable(deck.id) { mutableStateOf(deck.folderId) }
    var name by rememberSaveable(deck.id) { mutableStateOf("${deck.name} (Kopie)") }
    var includeLearning by rememberSaveable { mutableStateOf(false) }
    var targetId by rememberSaveable(deck.id) { mutableStateOf<String?>(null) }
    val targets = decks.filter { it.id != deck.id && it.compatibleWith(deck) && !it.isArchived }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page != "organize") TextButton(onClick = { page = "organize" }, enabled = !busy) { Text("Zur Übersicht") }
            Text(when (page) { "move" -> "Liste verschieben"; "duplicate" -> "Liste duplizieren"; "merge" -> "Listen zusammenführen"; else -> "Liste verwalten" },
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(deck.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (page) {
                "move" -> {
                    Text("Wähle den Zielordner. Karten und Lernstände bleiben erhalten.",
                        style = MaterialTheme.typography.bodyMedium)
                    (listOf<Pair<String?, String>>(null to "Ohne Ordner") + folders.map { it.id to it.name }).forEach { (id, label) ->
                        Surface(onClick = { folderId = id }, enabled = !busy, shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.semantics { role = Role.RadioButton; selected = folderId == id },
                            color = if (folderId == id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = folderId == id, onClick = null)
                                Text(label, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    if (folders.isEmpty()) Text("Weitere Ordner legst du in der Bibliothek über + an.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { onFolder(folderId) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Hierher verschieben") }
                }
                "duplicate" -> {
                    OutlinedTextField(name, { name = it }, label = { Text("Name der Kopie") }, singleLine = true,
                        enabled = !busy, modifier = Modifier.fillMaxWidth())
                    Text("Die Kopie erhält eigene Karten und Originalbilder. Favoriten, Notizen, Kapitel, Tags und der Ordner werden übernommen.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Lernstand mitkopieren", Modifier.weight(1f))
                        Switch(includeLearning, { includeLearning = it }, enabled = !busy,
                            modifier = Modifier.semantics { contentDescription = "Lernstand mitkopieren" })
                    }
                    Text(if (includeLearning) "Bewertungen und nächste Termine werden in die Kopie übernommen."
                        else "Die Kopie beginnt mit einem neuen Lernstand.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onDuplicate(name, includeLearning) }, enabled = name.isNotBlank() && !busy,
                        modifier = Modifier.fillMaxWidth()) { Text("Kopie erstellen") }
                }
                "merge" -> {
                    if (targets.isEmpty()) Text("Du brauchst eine weitere aktive Liste mit gleichem Typ und gleichem Sprachpaar als Ziel.")
                    else ChoiceDropdown("Zielliste", targets.firstOrNull { it.id == targetId }?.name ?: "Zielliste wählen",
                        targets.map { it.name }) { targetId = targets[it].id }
                    Text("Alle Karten, Lernstände, Bewertungen und Originalbilder werden in die Zielliste verschoben. Doppelte Wortpaare bleiben erhalten.")
                    Text("„${deck.name}“ wird anschließend als eigene Liste entfernt. Ordner und Name der Zielliste bleiben bestehen.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { targetId?.let(onMerge) }, enabled = !busy && targets.any { it.id == targetId },
                        modifier = Modifier.fillMaxWidth()) { Text("Jetzt zusammenführen") }
                }
                else -> {
                    TextButton(onClick = { page = "move" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null); Spacer(Modifier.width(8.dp)); Text("Verschieben")
                    }
                    TextButton(onClick = onRename, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Liste umbenennen") }
                    HorizontalDivider()
                    OutlinedButton(onClick = { onArchive(!deck.isArchived) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (deck.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, null)
                        Spacer(Modifier.width(8.dp)); Text(if (deck.isArchived) "Wieder aktivieren" else "Liste archivieren")
                    }
                    Text("Archivierte Listen bleiben vollständig erhalten und erscheinen nicht in automatischen Lernrunden oder Erinnerungen.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { page = "duplicate" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Liste duplizieren")
                    }
                    TextButton(onClick = { page = "merge" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Merge, null); Spacer(Modifier.width(8.dp)); Text("Mit anderer Liste zusammenführen")
                    }
                    TextButton(onClick = onDelete, enabled = !busy,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()) { Text("Liste löschen") }
                }
            }
        }
    }
}

@Composable
fun FolderNameDialog(initial: Folder?, folders: List<Folder>, busy: Boolean, onDismiss: () -> Unit,
    onSave: (String) -> Unit) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    val duplicate = folders.any { it.id != initial?.id && it.name.equals(name.trim(), ignoreCase = true) }
    val invalid = name.trim().length > 80 || duplicate
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (initial == null) "Neuer Ordner" else "Ordner umbenennen") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Ordnername") }, singleLine = true,
                enabled = !busy, isError = invalid, supportingText = {
                    if (duplicate) Text("Dieser Ordner existiert bereits.")
                    else if (invalid) Text("Höchstens 80 Zeichen.")
                }, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = !busy && name.isNotBlank() && !invalid) {
        Text(if (initial == null) "Erstellen" else "Umbenennen")
    } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Abbrechen") } })
}

@Composable
fun LibraryAddSheet(onDismiss: () -> Unit, onCreate: () -> Unit, onFolder: () -> Unit,
    onQr: () -> Unit, onCsv: () -> Unit, onRestore: () -> Unit, busy: Boolean = false,
    initiallyImporting: Boolean = false) {
    var importing by rememberSaveable { mutableStateOf(initiallyImporting) }
    TransferOptionsSheet(
        title = if (importing) "Importieren" else "Zur Bibliothek hinzufügen",
        onDismiss = onDismiss,
        onBack = if (importing) ({ importing = false }) else null,
        options = if (importing) listOf(
            TransferOption("QR-Code", Icons.Outlined.QrCodeScanner,
                description = "Eine geteilte Liste scannen", enabled = !busy, onClick = onQr),
            TransferOption("CSV-Datei", Icons.Outlined.Description,
                description = "Listen aus einer CSV-Datei importieren", enabled = !busy, onClick = onCsv),
            TransferOption("Sicherung wiederherstellen", Icons.Outlined.Restore,
                description = "ZIP-Sicherung · ersetzt die aktuellen App-Daten", enabled = !busy, onClick = onRestore),
        ) else listOf(
            TransferOption("Neue Liste", Icons.Outlined.Add, enabled = !busy, onClick = onCreate),
            TransferOption("Neuer Ordner", Icons.Outlined.CreateNewFolder, enabled = !busy, onClick = onFolder),
            TransferOption("Importieren", Icons.Outlined.FileDownload, enabled = !busy, onClick = { importing = true }),
        ),
    )
}

@Preview(name = "Bibliothek hinzufügen · Hell", showBackground = true)
@Composable private fun LibraryAddLightPreview() = VocabTheme(ThemeMode.LIGHT) {
    LibraryAddSheet({}, {}, {}, {}, {}, {})
}
@Preview(name = "Bibliothek hinzufügen · Dunkel", showBackground = true)
@Composable private fun LibraryAddDarkPreview() = VocabTheme(ThemeMode.DARK) {
    LibraryAddSheet({}, {}, {}, {}, {}, {})
}
@Preview(name = "Bibliothek importieren · Hell", showBackground = true)
@Composable private fun LibraryImportLightPreview() = VocabTheme(ThemeMode.LIGHT) {
    LibraryAddSheet({}, {}, {}, {}, {}, {}, initiallyImporting = true)
}
@Preview(name = "Bibliothek importieren · Dunkel", showBackground = true)
@Composable private fun LibraryImportDarkPreview() = VocabTheme(ThemeMode.DARK) {
    LibraryAddSheet({}, {}, {}, {}, {}, {}, initiallyImporting = true)
}
