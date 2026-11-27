package de.vocabscan.ui

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.vocabscan.data.DeckType
import de.vocabscan.data.Deck
import de.vocabscan.data.ReviewLog
import de.vocabscan.data.StudyLanguage
import de.vocabscan.data.StudyProgress
import de.vocabscan.data.Vocabulary
import de.vocabscan.learn.ReviewRating
import de.vocabscan.learn.StudyDirection
import de.vocabscan.learn.WordReviewSummaryCalculator
import de.vocabscan.learn.wordTags
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordDetailsScreen(
    word: Vocabulary, language: String, progress: List<StudyProgress>, logs: List<ReviewLog>,
    type: DeckType = DeckType.SPRACHE,
    targetLanguage: String = if (type.isFlashcards) "" else "de",
    busy: Boolean, onEdit: () -> Unit, onReset: () -> Unit, onDelete: () -> Unit, onFavorite: () -> Unit,
) {
    val summary = remember(word.id, progress, logs) { WordReviewSummaryCalculator.calculate(word.id, progress, logs) }
    val context = LocalContext.current
    var readyToSpeak by remember { mutableStateOf(false) }
    var speechMessage by remember { mutableStateOf<String?>(null) }
    val speaker = remember(context, type) {
        if (type.isFlashcards) null else TextToSpeech(context.applicationContext) { status -> readyToSpeak = status == TextToSpeech.SUCCESS }
    }
    DisposableEffect(speaker) { onDispose { speaker?.stop(); speaker?.shutdown() } }
    fun speak(text: String, locale: Locale) {
        val voice = speaker ?: return
        val availability = voice.isLanguageAvailable(locale)
        if (availability < TextToSpeech.LANG_AVAILABLE) {
            speechMessage = "Für diese Sprache ist keine Android-Stimme installiert."
        } else {
            speechMessage = null
            voice.language = locale
            voice.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word-${word.id}")
        }
    }
    val foreignName = StudyLanguage.fromCode(language).label
    val targetName = StudyLanguage.fromCode(targetLanguage).label
    val deck = Deck(word.deckId, "", language, type = type, targetLanguage = targetLanguage)
    val wordLogs = logs.filter { it.wordId == word.id }.sortedWith(compareByDescending<ReviewLog> { it.reviewedAt }.thenByDescending { it.id })

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(deck.termLabel, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(word.term, style = MaterialTheme.typography.headlineLarge)
                    Text(deck.translationLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(word.translation, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FavoriteButton(word.isFavorite, word.term, !busy, expanded = true, onClick = onFavorite)
                    if (!type.isFlashcards) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { speak(word.term, Locale.forLanguageTag(language)) }, enabled = readyToSpeak) {
                            Icon(Icons.AutoMirrored.Outlined.VolumeUp, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (language == targetLanguage) "Begriff vorlesen" else foreignName)
                        }
                        OutlinedButton(onClick = { speak(word.translation, Locale.forLanguageTag(targetLanguage)) }, enabled = readyToSpeak) {
                            Icon(Icons.AutoMirrored.Outlined.VolumeUp, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (language == targetLanguage) "Erklärung vorlesen" else targetName)
                        }
                    }
                    if (!type.isFlashcards && !readyToSpeak) Text("Aussprache benötigt eine installierte Android-Stimme.",
                        style = MaterialTheme.typography.bodySmall)
                    speechMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item {
            Button(onClick = onEdit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Edit, null)
                Spacer(Modifier.width(8.dp))
                Text(if (type.isFlashcards) "Karte bearbeiten" else "Vokabel bearbeiten")
            }
        }
        item {
            Text("Dein Lernstand", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailMetric("${summary.reviews}", "Wiederholungen", Modifier.weight(1f))
                DetailMetric(summary.errorPercent?.let { "$it %" } ?: "–", "Fehlerquote", Modifier.weight(1f))
            }
            Text(if (type.isFlashcards) "Fehlerquote = Anteil der Bewertungen „Nicht gewusst“."
                else "Fehlerquote = Anteil der Bewertungen „Nochmal“. Getippte Fehler werden nicht einzeln gespeichert.",
                modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Nächster Termin", style = MaterialTheme.typography.titleMedium)
                    Text(dueLabel(summary.nextDueAt), style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text("Jede Lernrichtung hat ihren eigenen Termin.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("Nach Richtung", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            summary.directions.forEach { direction ->
                val label = deck.directionLabel(direction.direction == StudyDirection.GERMAN_TO_FOREIGN)
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        Text("${direction.reviews} Wiederholungen · ${direction.errorPercent?.let { "$it % " + if (type.isFlashcards) "Nicht gewusst" else "Nochmal" } ?: "noch keine Bewertungen"}")
                        Text("Nächster Termin: ${dueLabel(direction.nextDueAt)}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Persönliche Notiz", style = MaterialTheme.typography.titleMedium)
                    Text(word.note.ifBlank { "Noch keine Notiz. Über „Bearbeiten“ kannst du eine ergänzen." },
                        color = if (word.note.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    if (word.chapter.isNotBlank()) Text("Kapitel: ${word.chapter}", style = MaterialTheme.typography.bodySmall)
                    val tags = wordTags(word.tags)
                    if (tags.isNotEmpty()) Text(tags.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                    val alternatives = listOf(word.termAlternatives, word.translationAlternatives).filter { it.isNotBlank() }
                    if (alternatives.isNotEmpty()) Text("Weitere Antworten: ${alternatives.joinToString(" · ")}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (wordLogs.isNotEmpty()) item {
            Text("Letzte Bewertungen", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            wordLogs.take(5).forEach { log ->
                val rating = if (type.isFlashcards) { if (log.rating == 1) "Nicht gewusst" else "Gewusst" }
                    else ReviewRating.entries.firstOrNull { it.grade == log.rating }?.label ?: "Bewertet"
                val direction = deck.directionLabel(log.direction == 1)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(rating, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text("$direction · ${formatDate(log.reviewedAt)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
        item {
            HorizontalDivider()
            TextButton(onClick = onReset, enabled = !busy && (summary.reviews > 0 || progress.any { it.wordId == word.id }),
                modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.RestartAlt, null)
                Spacer(Modifier.width(8.dp))
                Text("Lernstand zurücksetzen")
            }
            TextButton(onClick = onDelete, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Outlined.DeleteOutline, null)
                Spacer(Modifier.width(8.dp))
                Text(if (type.isFlashcards) "Karte löschen" else "Vokabel löschen")
            }
        }
    }
}

@Composable
private fun DetailMetric(value: String, label: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val detailDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm", Locale.GERMAN)

private fun formatDate(millis: Long): String = detailDateFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun dueLabel(millis: Long?): String = when {
    millis == null -> "Noch nicht geplant"
    millis <= System.currentTimeMillis() -> "Jetzt fällig"
    else -> formatDate(millis)
}
