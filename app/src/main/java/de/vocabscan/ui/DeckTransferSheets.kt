package de.vocabscan.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import de.vocabscan.ui.components.TransferOption
import de.vocabscan.ui.components.TransferOptionsSheet

@Composable
fun DeckImportSheet(isFlashcards: Boolean, busy: Boolean, onDismiss: () -> Unit,
    onScan: () -> Unit, onPhoto: () -> Unit, onImportText: () -> Unit) {
    TransferOptionsSheet("Importieren", buildList {
        if (!isFlashcards) {
            add(TransferOption("Arbeitsblatt scannen", Icons.Outlined.DocumentScanner,
                enabled = !busy, onClick = onScan))
            add(TransferOption("Bilder importieren", Icons.Outlined.PhotoLibrary,
                enabled = !busy, onClick = onPhoto))
        }
        add(TransferOption("Text oder CSV importieren", Icons.Outlined.Description,
            enabled = !busy, onClick = onImportText))
    }, onDismiss)
}

@Composable
fun DeckExportSheet(hasWords: Boolean, busy: Boolean, onDismiss: () -> Unit,
    onShareQr: () -> Unit, onShareCsv: () -> Unit) {
    TransferOptionsSheet("Liste exportieren", listOf(
        TransferOption("QR-Code", Icons.Outlined.QrCode2,
            description = if (hasWords) "Als QR-Code anzeigen und teilen" else "Füge zuerst Karten hinzu.",
            enabled = !busy && hasWords, onClick = onShareQr),
        TransferOption("CSV", Icons.Outlined.IosShare, description = "Als CSV-Datei teilen",
            enabled = !busy, onClick = onShareCsv),
    ), onDismiss)
}

@Preview(name = "Importauswahl · Hell", showBackground = true)
@Composable private fun DeckImportLightPreview() = VocabTheme(ThemeMode.LIGHT) {
    DeckImportSheet(false, false, {}, {}, {}, {})
}
@Preview(name = "Importauswahl · Dunkel", showBackground = true)
@Composable private fun DeckImportDarkPreview() = VocabTheme(ThemeMode.DARK) {
    DeckImportSheet(false, false, {}, {}, {}, {})
}
@Preview(name = "Exportauswahl · Hell", showBackground = true)
@Composable private fun DeckExportLightPreview() = VocabTheme(ThemeMode.LIGHT) {
    DeckExportSheet(true, false, {}, {}, {})
}
@Preview(name = "Exportauswahl · Dunkel", showBackground = true)
@Composable private fun DeckExportDarkPreview() = VocabTheme(ThemeMode.DARK) {
    DeckExportSheet(true, false, {}, {}, {})
}
@Preview(name = "Exportauswahl · Leere Liste", showBackground = true)
@Composable private fun DeckExportEmptyPreview() = VocabTheme(ThemeMode.LIGHT) {
    DeckExportSheet(false, false, {}, {}, {})
}
