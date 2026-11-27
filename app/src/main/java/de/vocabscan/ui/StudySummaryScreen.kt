package de.vocabscan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.vocabscan.learn.ReviewRating
import de.vocabscan.learn.StudySummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StudySummaryScreen(summary: StudySummary, onContinue: () -> Unit) {
    val flashcardsOnly = summary.attempts.isNotEmpty() && summary.attempts.all { it.card.isFlashcard }
    val ratings = if (flashcardsOnly) listOf(ReviewRating.AGAIN, ReviewRating.GOOD) else ReviewRating.entries
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(if (summary.completed == summary.planned) "Runde geschafft!" else "Runde beendet",
            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${summary.completed} / ${summary.planned}", style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold)
                Text("Lernrichtungen abgeschlossen")
                val seconds = summary.durationMillis / 1000
                Text("Dauer der Runde: ${seconds / 60} Min. ${seconds % 60} Sek.")
            }
        }
        Text("Beim ersten Versuch", style = MaterialTheme.typography.titleLarge)
        ratings.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { rating ->
                    Column(Modifier.weight(1f)) {
                        Column(Modifier.padding(18.dp)) {
                            Text(summary.firstAttempts.count { it.rating == rating }.toString(),
                                style = MaterialTheme.typography.headlineMedium)
                            Text(if (flashcardsOnly) { if (rating == ReviewRating.AGAIN) "Nicht gewusst" else "Gewusst" } else rating.label)
                        }
                    }
                }
            }
        }
        Text("${summary.attempts.size} Bewertungen · ${summary.repeats} zusätzliche Versuche",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        summary.nextDueAt?.let { due ->
            val next = if (due <= System.currentTimeMillis()) "Jetzt" else
                DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm", Locale.GERMAN)
                    .format(Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()))
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nächste Wiederholung dieser Karten", style = MaterialTheme.typography.titleMedium)
                    Text(next)
                }
            }
        }
        if (summary.completed < summary.planned) Text(
            "${summary.planned - summary.completed} Lernrichtungen bleiben offen. Deine Bewertungen sind gespeichert.")
        val continuePress = rememberPressFeedback()
        Button(onClick = onContinue, interactionSource = continuePress.source,
            modifier = Modifier.fillMaxWidth().then(continuePress.modifier)) { Text("Weiter") }
    }
}
