package de.vocabscan.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.vocabscan.data.CsvCodec

@Composable
fun CardTextImportDialog(busy: Boolean, onDismiss: () -> Unit,
    onReadFile: (Uri, (String) -> Unit) -> Unit,
    onImport: (String, Char) -> Unit, previewText: String = "") {
    var text by rememberSaveable { mutableStateOf(previewText) }
    var separator by rememberSaveable { mutableIntStateOf(0) }
    val delimiters = listOf(';', ',', '\t', '|')
    val parsed = remember(text, separator) { runCatching { CsvCodec.decodeCards(text, delimiters[separator]) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onReadFile(uri) { text = it }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Karten importieren") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Eine Karte pro Zeile: Vorderseite;Rückseite. Optional folgen Notiz und Tags. Mehrzeilige Felder in Anführungszeichen setzen.",
                    style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { picker.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) },
                    enabled = !busy) { Text("CSV- oder Textdatei öffnen") }
                ChoiceDropdown("Trennzeichen", listOf("Semikolon ;", "Komma ,", "Tabulator", "Senkrechter Strich |")[separator],
                    listOf("Semikolon ;", "Komma ,", "Tabulator", "Senkrechter Strich |")) { separator = it }
                OutlinedTextField(text, { text = it }, label = { Text("Karten einfügen") },
                    placeholder = { Text("Vertrag;Zwei übereinstimmende Willenserklärungen") },
                    minLines = 5, maxLines = 10, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    isError = text.isNotBlank() && parsed.isFailure)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                else Text(if (text.isBlank()) "Noch keine Karten eingefügt."
                    else parsed.fold({ "${it.size} Karten bereit" }, { it.message ?: "Text prüfen" }),
                    color = if (text.isNotBlank() && parsed.isFailure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { onImport(text, delimiters[separator]) },
            enabled = !busy && parsed.isSuccess) { Text("Importieren") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Abbrechen") } })
}

@Preview(name = "Textimport · Hell", showBackground = true)
@Composable private fun TextImportLightPreview() = VocabTheme(ThemeMode.LIGHT) {
    CardTextImportDialog(false, {}, { _, _ -> }, { _, _ -> }, "Vertrag;Zwei übereinstimmende Willenserklärungen")
}
@Preview(name = "Textimport · Dunkel", showBackground = true)
@Composable private fun TextImportDarkPreview() = VocabTheme(ThemeMode.DARK) {
    CardTextImportDialog(false, {}, { _, _ -> }, { _, _ -> }, "Vertrag;Zwei übereinstimmende Willenserklärungen")
}
@Preview(name = "Textimport · Leer", showBackground = true)
@Composable private fun TextImportEmptyPreview() = VocabTheme(ThemeMode.LIGHT) {
    CardTextImportDialog(false, {}, { _, _ -> }, { _, _ -> })
}
