package de.vocabscan.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.vocabscan.data.Deck
import de.vocabscan.data.DeckWithVocabulary
import de.vocabscan.data.Folder
import de.vocabscan.data.Vocabulary

/** The archive has its own destination; rows and folder navigation match the library. */
@Composable
internal fun ArchiveScreen(
    contents: LibraryContents, folderId: String?, busy: Boolean,
    onFolder: (String) -> Unit, onSelect: (String) -> Unit, onManage: (Deck) -> Unit,
    onLibrary: () -> Unit, onRetry: () -> Unit,
) {
    LibraryScreen(contents.decks, contents.folders, folderId, archived = true, busy = busy,
        onFolder = onFolder, onSelect = onSelect, onManage = onManage,
        onRenameFolder = {}, onDeleteFolder = {}, onShowActive = onLibrary,
        loading = contents.loading, failed = contents.failed, onRetry = onRetry)
}

@Composable
internal fun ArchiveRestoreBar(busy: Boolean, onRestore: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Button(onClick = onRestore, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Unarchive, null)
                Spacer(Modifier.width(8.dp))
                Text("Wieder aktivieren")
            }
        }
    }
}

private fun archivePreviewContents(): LibraryContents {
    val folder = Folder("school", "Schule")
    return LibraryContents(
        decks = listOf(
            DeckWithVocabulary(Deck("school-list", "Unit 1", "en", folderId = folder.id, isArchived = true), emptyList()),
            DeckWithVocabulary(Deck("travel", "Für die Reise", "fr", isArchived = true),
                listOf(Vocabulary("word", "travel", "bonjour", "Guten Tag"))),
        ), folders = listOf(folder, Folder("empty", "Leerer Ordner")), loading = false,
    )
}

@Preview(name = "Archiv · Hell", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ArchiveLightPreview() = VocabTheme(ThemeMode.LIGHT) {
    ArchiveScreen(archivePreviewContents(), null, false, {}, {}, {}, {}, {})
}

@Preview(name = "Archiv · Dunkel", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ArchiveDarkPreview() = VocabTheme(ThemeMode.DARK) {
    ArchiveScreen(archivePreviewContents(), null, false, {}, {}, {}, {}, {})
}

@Preview(name = "Archiv · Leer", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ArchiveEmptyPreview() = VocabTheme(ThemeMode.LIGHT) {
    ArchiveScreen(LibraryContents(loading = false), null, false, {}, {}, {}, {}, {})
}

@Preview(name = "Archiv · Laden", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ArchiveLoadingPreview() = VocabTheme(ThemeMode.DARK) {
    ArchiveScreen(LibraryContents(), null, false, {}, {}, {}, {}, {})
}

@Preview(name = "Archiv · Fehler", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ArchiveErrorPreview() = VocabTheme(ThemeMode.LIGHT) {
    ArchiveScreen(LibraryContents(loading = false, failed = true), null, false, {}, {}, {}, {}, {})
}

@Preview(name = "Archiv · Große Schrift", showBackground = true, widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun ArchiveLargeFontPreview() = VocabTheme(ThemeMode.DARK) {
    ArchiveScreen(archivePreviewContents(), null, false, {}, {}, {}, {}, {})
}

@Composable
private fun ArchivedListPreview(theme: ThemeMode, empty: Boolean = false) = VocabTheme(theme) {
    val data = archivePreviewContents().decks.last().let { if (empty) it.copy(words = emptyList()) else it }
    Scaffold(bottomBar = { ArchiveRestoreBar(false) {} }) { padding ->
        Box(Modifier.padding(padding)) {
            DeckScreen(data, listOf(data.deck), emptyList(), busy = false, scanPages = emptyList(),
                scanFile = { android.net.Uri.EMPTY }, onEdit = {}, onFavorite = {}, onShareQr = {}, onShareCsv = {},
                onScan = {}, onPhoto = {}, onStudy = {}, onPractice = {}, onPracticeAll = {}, onStudyScan = {},
                onTransfer = { _, _, _ -> })
        }
    }
}

@Preview(name = "Archivliste · Hell", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ArchivedListLightPreview() = ArchivedListPreview(ThemeMode.LIGHT)

@Preview(name = "Archivliste · Dunkel", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ArchivedListDarkPreview() = ArchivedListPreview(ThemeMode.DARK)

@Preview(name = "Archivliste · Leer", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ArchivedListEmptyPreview() = ArchivedListPreview(ThemeMode.LIGHT, empty = true)
