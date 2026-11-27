package de.vocabscan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.vocabscan.data.*

@Composable
fun LibraryScreen(
    decks: List<DeckWithVocabulary>, folders: List<Folder>, folderId: String?, archived: Boolean, busy: Boolean,
    onFolder: (String) -> Unit, onSelect: (String) -> Unit, onManage: (Deck) -> Unit,
    onRenameFolder: (Folder) -> Unit, onDeleteFolder: (Folder) -> Unit, onShowActive: () -> Unit,
    onOpenArchive: () -> Unit = {}, loading: Boolean = false, failed: Boolean = false, onRetry: () -> Unit = {},
) {
    val scopedFolders = folders.filter { it.isArchived == archived }
    val currentFolder = scopedFolders.firstOrNull { it.id == folderId }
    val visibleDecks = decks.filter { it.deck.isArchived == archived && it.deck.folderId == folderId }
        .sortedBy { it.deck.name.lowercase(java.util.Locale.ROOT) }
    val archiveDecks = decks.filter { it.deck.isArchived }
    val visibleFolders = scopedFolders.filter { it.parentId == folderId }
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(currentFolder?.name ?: if (archived) "Archiv" else "Bibliothek", style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 8.dp).semantics { heading() })
            if (currentFolder?.parentId != null) Text(folderPath(scopedFolders, currentFolder.id).joinToString(" / ") { it.name },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (archived) {
                Text(if (folderId == null) "Deine pausierten Listen. Karten und Lernstände bleiben erhalten."
                    else "Archiv · ${currentFolder?.name.orEmpty()}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (loading) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(if (archived) "Archiv wird geladen …" else "Bibliothek wird geladen …", style = MaterialTheme.typography.bodyMedium)
                }
            }
            return@LazyColumn
        }
        if (failed) {
            item { EmptyStatePanel("Listen konnten nicht geladen werden", "Deine gespeicherten Listen bleiben erhalten.", "Erneut versuchen", onRetry) }
            return@LazyColumn
        }
        if (archived && archiveDecks.isEmpty()) {
            item { EmptyStatePanel("Noch keine archivierten Listen",
                "Wenn du eine Liste pausierst, findest du sie hier. Du kannst sie jederzeit wieder aktivieren.", "Zur Bibliothek", onShowActive) }
            return@LazyColumn
        }
        if (visibleFolders.isNotEmpty() || folderId == null && !archived) item { LibrarySection(if (folderId == null) "Ordner" else "Unterordner") }
        if (!archived && folderId == null && scopedFolders.isEmpty()) item {
            Text("Noch keine Ordner. Über + kannst du deine Listen ordnen.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp))
        }
        items(visibleFolders, key = { "folder-${it.id}" }) { folder ->
            // Counts describe the visible archive scope, matching the folder's contents.
            val contents = decks.filter { data -> data.deck.isArchived == archived &&
                folderPath(scopedFolders, data.deck.folderId).any { it.id == folder.id } }
            FolderRow(folder, contents.size, contents.sumOf { it.words.size }, busy,
                onOpen = { onFolder(folder.id) }, onRename = { onRenameFolder(folder) }, onDelete = { onDeleteFolder(folder) },
                showManagement = !archived)
        }
        if (folderId == null && (!archived || visibleDecks.isNotEmpty())) item { LibrarySection("Ohne Ordner") }
        if (visibleDecks.isEmpty() && visibleFolders.isEmpty() && (!archived || folderId != null)) item {
            val title = when {
                archived -> "Keine archivierten Listen in diesem Ordner"
                folderId != null -> "Dieser Ordner ist noch leer"
                decks.isEmpty() -> "Deine erste Liste beginnt hier"
                else -> "Keine losen Listen"
            }
            val message = when {
                archived -> "Die Listen dieses Ordners sind bereits aktiv. Kehre zur Archivübersicht zurück, um weitere Listen zu öffnen."
                folderId != null -> "Lege über + eine Liste in diesem Ordner an. Bestehende Listen kannst du über ihr Menü hierher verschieben."
                decks.isEmpty() -> "Tippe auf +, um eine neue Liste anzulegen oder über Importieren eine Liste zu übernehmen."
                else -> "Öffne einen Ordner oder lege über + eine weitere Liste an."
            }
            // The + FAB remains the only primary action, including in empty states.
            EmptyStatePanel(title, message)
        }
        items(visibleDecks, key = { "deck-${it.deck.id}" }) { data ->
            Surface(onClick = { onSelect(data.deck.id) }, enabled = !busy,
                color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().animateItem().semantics {
                    onClick(label = "Liste ${data.deck.name} öffnen", action = null)
                }) {
                Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(if (data.deck.isFlashcards) Icons.Outlined.Style else Icons.Outlined.Translate, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(data.deck.name, style = MaterialTheme.typography.titleMedium)
                        Text("${data.deck.subjectLabel} · ${data.deck.countLabel(data.words.size)}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onManage(data.deck) }, enabled = !busy) {
                        Icon(Icons.Outlined.MoreVert, "Liste ${data.deck.name} verwalten")
                    }
                }
            }
        }
        if (!archived && folderId == null) item(key = "archive-entry") {
            Surface(onClick = onOpenArchive, enabled = !busy, shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                ListItem(
                    headlineContent = { Text("Archiv", style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text("${archiveDecks.size} ${if (archiveDecks.size == 1) "Liste" else "Listen"} pausiert",
                        style = MaterialTheme.typography.bodyMedium) },
                    leadingContent = { Icon(Icons.Outlined.Archive, null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.heightIn(min = 64.dp),
                )
            }
        }
    }
}

@Composable
private fun LibrarySection(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).semantics { heading() })
}

@Composable
private fun FolderRow(folder: Folder, listCount: Int, wordCount: Int, busy: Boolean,
    onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, showManagement: Boolean = true) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(onClick = onOpen, enabled = !busy, color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().semantics {
            onClick(label = "Ordner ${folder.name} öffnen", action = null)
        }) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(folder.name, style = MaterialTheme.typography.titleMedium)
                Text("$listCount ${if (listCount == 1) "Liste" else "Listen"} · $wordCount ${if (wordCount == 1) "Karte" else "Karten"}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showManagement) Box {
                IconButton(onClick = { menuOpen = true }, enabled = !busy) {
                    Icon(Icons.Outlined.MoreVert, "Ordner ${folder.name} verwalten")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Umbenennen") }, onClick = { menuOpen = false; onRename() })
                    DropdownMenuItem(text = { Text("Ordner löschen") }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun LibraryPreview() = VocabTheme(ThemeMode.LIGHT) {
    val folder = Folder("school", "Schule")
    LibraryScreen(listOf(DeckWithVocabulary(Deck("d", "Für die Reise", "fr"), emptyList())),
        listOf(folder, Folder("work", "Beruf")), null, false, false, {}, {}, {}, {}, {}, {})
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun FolderDarkPreview() = VocabTheme(ThemeMode.DARK) {
    LibraryScreen(emptyList(), listOf(Folder("school", "Schule")), "school", false, false, {}, {}, {}, {}, {}, {})
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun LibraryEmptyPreview() = VocabTheme(ThemeMode.LIGHT) {
    LibraryScreen(emptyList(), emptyList(), null, false, false, {}, {}, {}, {}, {}, {})
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun LibraryLargeFontPreview() = VocabTheme(ThemeMode.DARK) {
    LibraryScreen(emptyList(), listOf(Folder("school", "Französisch für die Schule")), null, false, false, {}, {}, {}, {}, {}, {})
}
