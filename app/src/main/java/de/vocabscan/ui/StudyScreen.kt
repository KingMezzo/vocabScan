package de.vocabscan.ui

import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material3.*
import androidx.compose.ui.tooling.preview.Preview
import de.vocabscan.data.DeckType
import de.vocabscan.data.Vocabulary
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import de.vocabscan.learn.ReviewRating
import de.vocabscan.learn.StudyCard
import de.vocabscan.learn.StudyDirection
import de.vocabscan.learn.AnswerChecker
import de.vocabscan.learn.AnswerKind
import de.vocabscan.data.StudyLanguage
import java.util.Locale

private data class CardFrame(val card: StudyCard, val attempt: Int)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudyScreen(
    session: StudySession,
    busy: Boolean,
    onGrade: (ReviewRating) -> Unit,
    onSaveAlternative: (String) -> Unit,
    onEdit: () -> Unit,
) {
    val card = session.queue.firstOrNull()
    if (card == null) {
        EmptyStatePanel("Keine Karten in dieser Runde", "Wähle in der Bibliothek eine Liste zum Lernen aus.")
        return
    }
    val cardsMode = card.isFlashcard || session.mode == StudyMode.CARDS
    val word = card.word
    val language = card.language
    val direction = card.direction
    val foreignPrompt = direction == StudyDirection.FOREIGN_TO_GERMAN
    val prompt = if (foreignPrompt) word.term else word.translation
    val solution = if (foreignPrompt) word.translation else word.term
    val extraSolutions = if (foreignPrompt) word.translationAlternatives else word.termAlternatives
    val variants = remember(solution, extraSolutions) { AnswerChecker.alternatives(solution, extraSolutions).drop(1) }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val haptics = LocalHapticFeedback.current
    val fontScale = LocalDensity.current.fontScale
    var readyToSpeak by remember { mutableStateOf(false) }
    var speechMessage by remember(card.key) { mutableStateOf<String?>(null) }
    val speaker = remember(context, card.isFlashcard) {
        if (card.isFlashcard) null else TextToSpeech(context.applicationContext) { status -> readyToSpeak = status == TextToSpeech.SUCCESS }
    }
    DisposableEffect(speaker) { onDispose { speaker?.stop(); speaker?.shutdown() } }
    var answer by rememberSaveable(word.id, direction, session.attempt) { mutableStateOf("") }
    var revealed by rememberSaveable(word.id, direction, session.attempt) { mutableStateOf(false) }
    var manuallyAccepted by rememberSaveable(word.id, direction, session.attempt) { mutableStateOf(false) }
    var askToSaveVariant by rememberSaveable(word.id, direction, session.attempt) { mutableStateOf(false) }
    var variantSaved by rememberSaveable(word.id, direction, session.attempt) { mutableStateOf(false) }
    val check = remember(answer, solution, extraSolutions, direction) {
        AnswerChecker.check(answer, solution, extraSolutions, card.answerLanguage)
    }
    val scrollState = rememberScrollState()
    val progressValue by animateFloatAsState(
        targetValue = session.completed.toFloat() / session.total.coerceAtLeast(1),
        animationSpec = tween(320), label = "Lernfortschritt")
    LaunchedEffect(card.key, session.attempt) { scrollState.scrollTo(0) }
    LaunchedEffect(revealed, session.attempt) {
        if (revealed && !cardsMode) {
            delay(240)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    fun speak(text: String, locale: Locale) {
        val voice = speaker ?: return
        if (voice.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) {
            speechMessage = "Für ${StudyLanguage.fromCode(locale.language).label} ist keine Android-Stimme installiert."
            return
        }
        speechMessage = null
        voice.speak(text, TextToSpeech.QUEUE_FLUSH, null, "study-${word.id}")
    }
    val promptLocale = Locale.forLanguageTag(card.promptLanguage)
    val solutionLocale = Locale.forLanguageTag(card.answerLanguage)

    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(card.deckName + if (card.isFlashcard) " · Lernkarten" else " · ${StudyLanguage.fromCode(language).label} → ${StudyLanguage.fromCode(card.targetLanguage).label}", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${session.completed + 1} von ${session.total} · ${card.directionLabel}",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        )
        LinearProgressIndicator(
            progress = { progressValue },
            modifier = Modifier.fillMaxWidth(),
        )
        val editPress = rememberPressFeedback()
        OutlinedButton(onClick = onEdit, enabled = !busy, interactionSource = editPress.source,
            modifier = Modifier.fillMaxWidth().then(editPress.modifier)) {
            Icon(Icons.Outlined.Edit, null)
            Spacer(Modifier.width(8.dp))
            Text(if (card.isFlashcard) "Karte korrigieren" else "Vokabel korrigieren")
        }
        speechMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (cardsMode) {
            AnimatedContent(
                targetState = CardFrame(card, session.attempt),
                contentKey = { it.card.key to it.attempt },
                transitionSpec = {
                    (fadeIn(tween(210)) + slideInHorizontally(tween(210)) { it / 8 }) togetherWith
                        (fadeOut(tween(150)) + slideOutHorizontally(tween(210)) { -it / 8 })
                },
                label = "Nächste Karte",
            ) { frame ->
                val current = frame.card.key == card.key && frame.attempt == session.attempt
                key(frame.card.key, frame.attempt) {
                    StudyFlipCard(frame.card, if (current) revealed else true, current && !busy) {
                        revealed = !revealed
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (card.isFlashcard) { if (revealed == foreignPrompt) "Rückseite" else "Vorderseite" }
                    else if (revealed) "Rückseite" else "Vorderseite", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium)
                if (!card.isFlashcard) IconButton(enabled = readyToSpeak, onClick = {
                    if (revealed) speak(solution, solutionLocale) else speak(prompt, promptLocale)
                }) { Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Karte anhören") }
            }
        } else {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (card.language == card.targetLanguage) { if (foreignPrompt) "ERKLÄRE" else "NENNE DEN BEGRIFF" } else "ÜBERSETZE",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(prompt, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                    if (!card.isFlashcard) IconButton(enabled = readyToSpeak, onClick = { speak(prompt, promptLocale) }) {
                        Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Wort anhören")
                    }
                }
            }
            OutlinedTextField(
                value = answer, onValueChange = { answer = it },
                enabled = !revealed && !busy,
                label = { Text(if (card.language == card.targetLanguage) { if (foreignPrompt) "Deine Erklärung / dein Synonym" else "Dein Begriff" } else "Deine Übersetzung") },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
            if (!revealed) {
                val checkPress = rememberPressFeedback()
                Button(onClick = { keyboard?.hide(); revealed = true }, enabled = !busy && answer.isNotBlank(),
                    interactionSource = checkPress.source,
                    modifier = Modifier.fillMaxWidth().then(checkPress.modifier)) { Text("Antwort prüfen") }
                TextButton(onClick = { keyboard?.hide(); revealed = true }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) { Text("Lösung ohne Eingabe zeigen") }
            }
            AnimatedVisibility(visible = revealed, enter = fadeIn(tween(220)) + expandVertically(tween(220))) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Lösung", style = MaterialTheme.typography.labelLarge)
                        Text(solution, style = MaterialTheme.typography.headlineSmall)
                        if (variants.isNotEmpty()) {
                            Text("Weitere richtige Antworten", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                variants.forEach { variant -> Surface(shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(variant, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium)
                                } }
                            }
                        }
                        if (word.note.isNotBlank()) Text(word.note)
                        Text(if (manuallyAccepted) "Für diese Antwort als richtig markiert." else when (check.kind) {
                            AnswerKind.MATCH -> "Deine Eingabe stimmt mit der Lösung überein."
                            AnswerKind.ARTICLE -> "Richtig – der Artikel ist optional."
                            AnswerKind.ACCENT -> if (card.answerLanguage == "la")
                                "Fast richtig: Achte auf die Längenzeichen. Richtig ist „${check.closest}“."
                                else "Fast richtig: Achte auf den Akzent. Richtig ist „${check.closest}“."
                            AnswerKind.TYPO -> "Fast richtig: Prüfe die Schreibweise von „${check.closest}“."
                            AnswerKind.DIFFERENT -> "Deine Antwort weicht von den hinterlegten Lösungen ab."
                            AnswerKind.EMPTY -> "Vergleiche die Lösung und schätze dich selbst ein."
                        }, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        if (!check.accepted && answer.isNotBlank()) {
                            Text(highlightDifference(check.closest ?: solution, answer, expectedLine = false),
                                style = MaterialTheme.typography.bodyMedium)
                            Text(highlightDifference(check.closest ?: solution, answer, expectedLine = true),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        if (!check.accepted && !manuallyAccepted && answer.isNotBlank()) {
                            TextButton(onClick = { manuallyAccepted = true; askToSaveVariant = true }) { Text("Trotzdem richtig") }
                        }
                        if (variantSaved) Text("Diese Antwort ist künftig zusätzlich erlaubt.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        if (!card.isFlashcard) IconButton(enabled = readyToSpeak, onClick = { speak(solution, solutionLocale) }) {
                            Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Lösung anhören")
                        }
                    }
                }
            }
        }
        if (card.isFlashcard) {
            if (!revealed) Button(onClick = { revealed = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Aufdecken")
            } else {
                Button(onClick = { onGrade(ReviewRating.GOOD) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Gewusst")
                }
                TextButton(onClick = { onGrade(ReviewRating.AGAIN) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Nicht gewusst")
                }
            }
        }
        if (revealed && !card.isFlashcard) {
            Text("Wie lief's?", style = MaterialTheme.typography.titleMedium)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
            val maxColumns = if (fontScale >= 1.3f || maxWidth < 340.dp) 2 else 4
            val columns = ((maxWidth + 8.dp) / (80.dp * fontScale + 8.dp)).toInt().coerceIn(1, maxColumns)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = columns, modifier = Modifier.fillMaxWidth()) {
                ReviewRating.entries.forEach { rating ->
                    val ratingPress = rememberPressFeedback()
                    val colors = MaterialTheme.colorScheme
                    val (background, foreground) = when (rating) {
                        ReviewRating.AGAIN -> colors.errorContainer to colors.onErrorContainer
                        ReviewRating.HARD -> colors.tertiaryContainer to colors.onTertiaryContainer
                        ReviewRating.GOOD -> colors.primaryContainer to colors.onPrimaryContainer
                        ReviewRating.EASY -> colors.secondaryContainer to colors.onSecondaryContainer
                    }
                    ElevatedCard(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onGrade(rating)
                        }, enabled = !busy,
                        interactionSource = ratingPress.source,
                        modifier = Modifier.weight(1f).then(ratingPress.modifier).semantics {
                            onClick(label = "Mit ${rating.label} bewerten", action = null)
                        },
                        colors = CardDefaults.elevatedCardColors(containerColor = background),
                    ) {
                        Column(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(horizontal = 4.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Icon(when (rating) {
                                ReviewRating.AGAIN -> Icons.Outlined.Replay
                                ReviewRating.HARD -> Icons.Outlined.SentimentDissatisfied
                                ReviewRating.GOOD -> Icons.Outlined.CheckCircleOutline
                                ReviewRating.EASY -> Icons.Outlined.AutoAwesome
                            }, null, tint = foreground, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.height(5.dp))
                            Text(rating.label, style = MaterialTheme.typography.labelMedium,
                                color = foreground, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            }
        }
    }
    if (askToSaveVariant) AlertDialog(
        onDismissRequest = { askToSaveVariant = false },
        title = { Text("Antwort dauerhaft übernehmen?") },
        text = { Text("„$answer“ wird dann bei dieser Vokabel als weitere richtige Antwort gespeichert.") },
        confirmButton = { TextButton(onClick = {
            onSaveAlternative(answer); variantSaved = true; askToSaveVariant = false
        }) { Text("Antwort speichern") } },
        dismissButton = { TextButton(onClick = { askToSaveVariant = false }) { Text("Nur dieses Mal") } },
    )
}

@Composable
private fun StudyFlipCard(card: StudyCard, revealed: Boolean, enabled: Boolean, onFlip: () -> Unit) {
    val word = card.word
    val foreignPrompt = card.direction == StudyDirection.FOREIGN_TO_GERMAN
    val prompt = if (foreignPrompt) word.term else word.translation
    val solution = if (foreignPrompt) word.translation else word.term
    val extraSolutions = if (foreignPrompt) word.translationAlternatives else word.termAlternatives
    val variants = remember(solution, extraSolutions) { AnswerChecker.alternatives(solution, extraSolutions).drop(1) }
    val rotation = animateFloatAsState(
        targetValue = if (revealed) 180f else 0f,
        animationSpec = tween(380, easing = FastOutSlowInEasing), label = "Karte umdrehen",
    )
    // Only the midpoint changes the text. Reading the angle in graphicsLayer avoids
    // recomposing and measuring the card on every animation frame.
    val showingBack by remember { derivedStateOf { rotation.value > 90f } }
    val flipPress = rememberPressFeedback()
    ElevatedCard(
        onClick = onFlip,
        enabled = enabled,
        interactionSource = flipPress.source,
        modifier = Modifier.fillMaxWidth().heightIn(min = 288.dp).then(flipPress.modifier).semantics {
            stateDescription = if (revealed) "Rückseite, Lösung sichtbar" else "Vorderseite, Lösung verdeckt"
            onClick(label = "Karte umdrehen", action = null)
        },
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (showingBack) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 288.dp).graphicsLayer {
            rotationY = rotation.value
            cameraDistance = 12f * density
        }.padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.graphicsLayer { rotationY = if (showingBack) 180f else 0f },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(if (card.isFlashcard || card.language == card.targetLanguage) {
                    if (showingBack == foreignPrompt) { if (card.isFlashcard) "Erklärung" else "Erklärung / Synonym" } else "Begriff"
                } else if (showingBack) "Lösung" else "Übersetze",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(if (showingBack) solution else prompt,
                    style = if (card.isFlashcard && showingBack == foreignPrompt) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineMedium,
                    fontWeight = if (card.isFlashcard && showingBack == foreignPrompt) FontWeight.Normal else FontWeight.Bold,
                    textAlign = TextAlign.Center)
                if (showingBack && word.note.isNotBlank()) Text(word.note, textAlign = TextAlign.Center)
                if (showingBack && !card.isFlashcard && variants.isNotEmpty()) Text("Weitere richtige Antworten: ${variants.joinToString(" · ")}",
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                Text("Zum Umdrehen tippen", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun highlightDifference(expected: String, actual: String, expectedLine: Boolean): androidx.compose.ui.text.AnnotatedString {
    val prefix = expected.zip(actual).takeWhile { it.first.equals(it.second, ignoreCase = true) }.size
    val suffix = expected.drop(prefix).reversed().zip(actual.drop(prefix).reversed())
        .takeWhile { it.first.equals(it.second, ignoreCase = true) }.size
    val value = if (expectedLine) expected else actual
    val end = (value.length - suffix).coerceAtLeast(prefix)
    val mark = MaterialTheme.colorScheme.error
    return buildAnnotatedString {
        append(if (expectedLine) "Richtig: " else "Deine Antwort: ")
        append(value.take(prefix))
        withStyle(SpanStyle(color = mark, background = MaterialTheme.colorScheme.errorContainer)) {
            append(value.substring(prefix, end))
        }
        append(value.drop(end))
    }
}



@Composable
private fun FlashcardStudyPreview(theme: ThemeMode, empty: Boolean = false) = VocabTheme(theme) {
    val card = StudyCard(Vocabulary("w", "d", "Vertrag", "Zwei übereinstimmende Willenserklärungen."),
        "", "Fachbegriffe Recht", StudyDirection.FOREIGN_TO_GERMAN, DeckType.LERNKARTEN)
    Surface { StudyScreen(StudySession(StudyMode.CARDS, if (empty) emptyList() else listOf(card), if (empty) 0 else 1),
        false, {}, {}, {}) }
}
@Preview(name = "Lernkarte · Hell", showBackground = true)
@Composable private fun FlashcardStudyLightPreview() = FlashcardStudyPreview(ThemeMode.LIGHT)
@Preview(name = "Lernkarte · Dunkel", showBackground = true)
@Composable private fun FlashcardStudyDarkPreview() = FlashcardStudyPreview(ThemeMode.DARK)
@Preview(name = "Lernkarte · Leer", showBackground = true)
@Composable private fun FlashcardStudyEmptyPreview() = FlashcardStudyPreview(ThemeMode.LIGHT, true)
