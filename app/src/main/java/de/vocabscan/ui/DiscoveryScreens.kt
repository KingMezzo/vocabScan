package de.vocabscan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import de.vocabscan.data.DeckWithVocabulary
import de.vocabscan.data.Deck
import de.vocabscan.data.Folder
import de.vocabscan.data.ReviewLog
import de.vocabscan.data.Vocabulary
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun GlobalSearchScreen(decks: List<DeckWithVocabulary>, busy: Boolean,
    onOpen: (String, Vocabulary) -> Unit, onFavorite: (Vocabulary) -> Unit,
    folders: List<Folder> = emptyList(), onOpenFolder: (String) -> Unit = {}, onOpenDeck: (Deck) -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    val folderMatches = if (query.isBlank() || favoritesOnly) emptyList() else folders.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val deckMatches = if (query.isBlank() || favoritesOnly) emptyList() else decks.filter { it.deck.name.contains(query.trim(), ignoreCase = true) }
    val matches = decks.flatMap { deck -> deck.words.map { deck to it } }.filter { (_, word) ->
        (!favoritesOnly || word.isFavorite) &&
            (query.isBlank() || listOf(word.term, word.translation, word.note, word.chapter, word.tags,
                word.termAlternatives, word.translationAlternatives).any { it.contains(query.trim(), ignoreCase = true) })
    }.sortedWith(compareByDescending<Pair<DeckWithVocabulary, Vocabulary>> { it.second.isFavorite }
        .thenBy { it.second.term.lowercase(Locale.ROOT) })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text("Bibliothek durchsuchen", style = MaterialTheme.typography.headlineLarge)
            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("In allen Listen suchen") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp), placeholder = { Text("Vorderseite, Rückseite, Notiz …") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly },
                    label = { Text("Nur Favoriten") }, leadingIcon = { Icon(Icons.Outlined.Star, null) })
                Text("${matches.size + folderMatches.size + deckMatches.size} Treffer", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(folderMatches, key = { "folder-${it.id}" }) { folder ->
            ListItem(headlineContent = { Text(folder.name) }, supportingContent = { Text((if (folder.isArchived) "Archiv · " else "Bibliothek · ") +
                de.vocabscan.data.folderPath(folders, folder.id).joinToString(" / ") { it.name }) },
                modifier = Modifier.clickable(enabled = !busy) { onOpenFolder(folder.id) })
        }
        items(deckMatches, key = { "deck-${it.deck.id}" }) { data ->
            ListItem(headlineContent = { Text(data.deck.name) },
                supportingContent = { Text(if (data.deck.isArchived) "Liste im Archiv" else data.deck.subjectLabel) },
                modifier = Modifier.clickable(enabled = !busy) { onOpenDeck(data.deck) })
        }
        if (matches.isEmpty() && folderMatches.isEmpty() && deckMatches.isEmpty()) item {
            EmptyStatePanel(if (favoritesOnly && query.isBlank()) "Noch keine Favoriten" else "Keine Treffer",
                if (favoritesOnly && query.isBlank()) "Tippe bei einer Karte auf den Stern. Deine gemerkten Karten findest du dann hier."
                else "Versuche einen kürzeren Suchbegriff oder suche auch außerhalb deiner Favoriten.",
                "Alle Karten anzeigen", onAction = { query = ""; favoritesOnly = false })
        }
        items(matches.take(300), key = { it.second.id }) { (deck, word) ->
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable(enabled = !busy, onClickLabel = "Kartendetails öffnen") { onOpen(deck.deck.id, word) }.padding(12.dp)) {
                        WordPairText(word.term, word.translation)
                        Text((if (deck.deck.isArchived) "Archiv · " else "") + deck.deck.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                    FavoriteButton(word.isFavorite, word.term, !busy, onClick = { onFavorite(word) })
                }
            }
        }
        if (matches.size > 300) item { Text("Zeige die ersten 300 Treffer. Verfeinere deine Suche.") }
    }
}

@Composable
fun LearningCalendar(logs: List<ReviewLog>, dailyGoal: Int) {
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    LaunchedEffect(month) { selected = if (month == YearMonth.from(today)) today else month.atDay(1) }
    val counts = remember(logs) { logs.groupingBy {
        Instant.ofEpochMilli(it.reviewedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }.eachCount() }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Lernkalender", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Vorheriger Monat") }
                Text("${month.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)} ${month.year}",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.from(today)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Nächster Monat")
                }
            }
            Row(Modifier.fillMaxWidth()) { listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach {
                Text(it, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            } }
            val offset = month.atDay(1).dayOfWeek.value - 1
            repeat((offset + month.lengthOfMonth() + 6) / 7) { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(7) { weekday ->
                        val number = week * 7 + weekday - offset + 1
                        val day = if (number in 1..month.lengthOfMonth()) month.atDay(number) else null
                        val count = day?.let { counts[it] ?: 0 } ?: 0
                        Surface(onClick = { if (day != null) selected = day }, enabled = day != null && day <= today,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                                if (day != null) contentDescription = "$day, $count Wiederholungen, " +
                                    if (count >= dailyGoal) "Tagesziel erreicht" else "Tagesziel $dailyGoal"
                                this.selected = day != null && day == selected
                            }, shape = RoundedCornerShape(10.dp),
                            color = when {
                                day == null -> MaterialTheme.colorScheme.surface
                                count >= dailyGoal -> MaterialTheme.colorScheme.primary
                                count > 0 -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }, contentColor = if (count >= dailyGoal) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface) {
                            Box(contentAlignment = Alignment.Center) {
                                if (day != null) Text(number.toString(), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            Text("${selected.dayOfMonth}. ${selected.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)}: ${counts[selected] ?: 0} von $dailyGoal Wiederholungen",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
