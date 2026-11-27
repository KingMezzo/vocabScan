package de.vocabscan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun LearnScreen(
    roundSize: Int, hasWords: Boolean, busy: Boolean, todayReviews: Int, dailyGoal: Int,
    onLearn: () -> Unit, onCustomize: () -> Unit, onLibrary: () -> Unit,
    onCreate: () -> Unit = onLibrary, loading: Boolean = false, failed: Boolean = false,
    onRetry: () -> Unit = {}, nextReviewAt: Long? = null, now: Long = System.currentTimeMillis(),
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Heute lernen", style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() })
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text("Deine Lernrunde wird vorbereitet …")
            }
            return@Column
        }
        if (failed) {
            EmptyStatePanel("Lernrunde konnte nicht geladen werden", "Versuche es noch einmal.", "Erneut versuchen", onRetry)
            return@Column
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when {
                    roundSize > 0 -> "Deine nächste Runde"
                    hasWords -> "Für jetzt ist alles erledigt"
                    else -> "Deine erste Lernrunde beginnt hier"
                }, style = MaterialTheme.typography.headlineSmall)
                Text(when {
                    roundSize > 0 -> "$roundSize ${if (roundSize == 1) "Karte" else "Karten"} · aus deinen aktiven Listen"
                    hasWords -> nextReviewAt?.let { nextReviewLabel(it, now) }
                        ?: "Aktuell sind keine Wiederholungen fällig. Du kannst trotzdem weiter üben."
                    else -> "Lege eine Liste an und ergänze eigene Karten oder scanne dein Lernmaterial."
                }, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = if (roundSize > 0) onLearn else if (hasWords) onCustomize else onCreate,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (roundSize > 0) "Jetzt lernen" else if (hasWords) "Übungsrunde zusammenstellen" else "Deine erste Liste anlegen",
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
                if (roundSize > 0) TextButton(onClick = onCustomize, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Runde anpassen") }
                if (!hasWords) TextButton(onClick = onLibrary, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Zur Bibliothek") }
            }
        }
        if (hasWords) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (todayReviews >= dailyGoal) Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Text(if (todayReviews >= dailyGoal) "Tagesziel erreicht" else "Dein Tagesziel",
                    style = MaterialTheme.typography.titleMedium)
            }
            Text("Heute $todayReviews von $dailyGoal Wiederholungen", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { (todayReviews.toFloat() / dailyGoal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().semantics {
                    stateDescription = "$todayReviews von $dailyGoal Wiederholungen" +
                        if (todayReviews >= dailyGoal) ", Tagesziel erreicht" else ""
                })
        }
    }
}

internal fun nextReviewLabel(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val next = Instant.ofEpochMilli(at).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val time = next.format(DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN))
    return when (next.toLocalDate()) {
        today -> "Nächste Wiederholung heute um $time Uhr."
        today.plusDays(1) -> "Nächste Wiederholung morgen um $time Uhr."
        else -> "Nächste Wiederholung am ${next.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN))} um $time Uhr."
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun LearnPreview() = VocabTheme(ThemeMode.LIGHT) {
    LearnScreen(10, true, false, 3, 10, {}, {}, {})
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun LearnEmptyPreview() = VocabTheme(ThemeMode.LIGHT) {
    LearnScreen(0, false, false, 0, 10, {}, {}, {})
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun LearnDoneDarkPreview() = VocabTheme(ThemeMode.DARK) {
    LearnScreen(0, true, false, 10, 10, {}, {}, {})
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun LearnLargeFontPreview() = VocabTheme(ThemeMode.LIGHT) {
    LearnScreen(10, true, false, 3, 10, {}, {}, {})
}

@Preview(name = "Lernen · Laden", showBackground = true)
@Composable private fun LearnLoadingPreview() = VocabTheme(ThemeMode.LIGHT) {
    LearnScreen(0, false, false, 0, 10, {}, {}, {}, loading = true)
}
@Preview(name = "Lernen · Fehler", showBackground = true)
@Composable private fun LearnErrorPreview() = VocabTheme(ThemeMode.DARK) {
    LearnScreen(0, false, false, 0, 10, {}, {}, {}, failed = true)
}
