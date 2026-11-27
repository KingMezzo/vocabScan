package de.vocabscan.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.vocabscan.R
import de.vocabscan.data.StudyLanguage
import de.vocabscan.ui.ThemeMode
import de.vocabscan.ui.VocabTheme

private val StudyLanguage.illustration: Int
    get() = when (this) {
        StudyLanguage.ENGLISH -> R.drawable.flag_uk
        StudyLanguage.FRENCH -> R.drawable.flag_france
        StudyLanguage.GERMAN -> R.drawable.flag_germany
        StudyLanguage.LATIN -> R.drawable.language_latin
    }

@Composable
internal fun StudyLanguageSelection(
    selected: StudyLanguage,
    onSelect: (StudyLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StudyLanguage.entries.forEach { language ->
            val isSelected = selected == language
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(language) })
                        .heightIn(min = 64.dp)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(language.illustration),
                        contentDescription = null,
                        modifier = Modifier.size(width = 48.dp, height = 32.dp).clip(MaterialTheme.shapes.extraSmall),
                    )
                    Text(language.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    RadioButton(selected = isSelected, onClick = null, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudyLanguageField(label: String, selected: StudyLanguage, enabled: Boolean,
    onSelect: (StudyLanguage) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 64.dp).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(selected.illustration), contentDescription = null, modifier = Modifier.size(32.dp, 24.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selected.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    if (expanded) ModalBottomSheet(onDismissRequest = { expanded = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Sprache wählen · $label", style = MaterialTheme.typography.titleLarge)
            StudyLanguageSelection(selected, onSelect = { onSelect(it); expanded = false })
        }
    }
}

@Preview(name = "Sprachauswahl · Französisch", showBackground = true, widthDp = 280)
@Composable
private fun FrenchSelectionPreview() {
    VocabTheme(ThemeMode.LIGHT) {
        StudyLanguageSelection(StudyLanguage.FRENCH, onSelect = {}, modifier = Modifier.padding(8.dp))
    }
}

@Preview(name = "Sprachauswahl · Latein · Hell", showBackground = true, widthDp = 320)
@Preview(name = "Sprachauswahl · Latein · Große Schrift", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun LatinSelectionLightPreview() {
    VocabTheme(ThemeMode.LIGHT) {
        StudyLanguageSelection(StudyLanguage.LATIN, onSelect = {}, modifier = Modifier.padding(8.dp))
    }
}

@Preview(name = "Sprachauswahl · Latein · Dunkel", showBackground = true, widthDp = 320)
@Composable
private fun LatinSelectionDarkPreview() {
    VocabTheme(ThemeMode.DARK) {
        StudyLanguageSelection(StudyLanguage.LATIN, onSelect = {}, modifier = Modifier.padding(8.dp))
    }
}
