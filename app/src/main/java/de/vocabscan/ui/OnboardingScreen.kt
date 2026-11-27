package de.vocabscan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    BackHandler {
        if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
        else onFinish()
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("VocabScan", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onFinish) { Text("Überspringen") }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) { page ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (LocalDensity.current.fontScale < 1.5f) OnboardingIllustration(page)
                    Text("0${page + 1} / 03", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Text(when (page) {
                        0 -> "Deine Wörter.\nDein nächster Schritt."
                        1 -> "Ein Scan.\nAlles an seinem Platz."
                        else -> "Kurz lernen.\nLange behalten."
                    }, style = MaterialTheme.typography.headlineLarge)
                    Text(when (page) {
                        0 -> "Unter Lernen startet deine nächste Runde. In der Bibliothek findest du deine Listen und Ordner. Über + legst du eine Sprachliste mit zwei frei wählbaren Sprachen an."
                        1 -> "Wähle die Tabelle im Bild aus und prüfe die erkannten Wortpaare. Du kannst Zeilen verbinden, trennen und Wörter verschieben."
                        else -> "Tippe deine Antwort oder drehe Lernkarten um. Deine Bewertung bestimmt, wann ein Wort wiederkommt. Listen und Lernstände bleiben auf deinem Gerät."
                    }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                repeat(3) { index -> Box(Modifier.padding(horizontal = 4.dp)
                    .size(width = if (index == pager.currentPage) 28.dp else 8.dp, height = 8.dp)
                    .background(if (index == pager.currentPage) MaterialTheme.colorScheme.primary else
                        MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))) }
            }
            Button(onClick = {
                if (pager.currentPage == 2) onFinish()
                else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                Text(if (pager.currentPage == 2) "Los geht’s" else "Weiter")
            }
            TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                enabled = pager.currentPage > 0, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) { Text("Zurück") }
        }
        }
    }
}

@Composable
private fun OnboardingIllustration(page: Int) {
    Box(Modifier.fillMaxWidth().heightIn(min = 196.dp).clearAndSetSemantics {}.background(Ink, RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
        Card(Modifier.padding(28.dp).fillMaxWidth().graphicsLayer { rotationZ = if (page == 1) -3f else 3f },
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEF6), contentColor = Ink)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(when (page) { 0 -> Icons.Outlined.Translate; 1 -> Icons.Outlined.CropFree; else -> Icons.Outlined.AutoAwesome },
                        null, Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(when (page) { 0 -> "Meine ersten Wörter"; 1 -> "Wortpaare prüfen"; else -> "Schon ein Wort mehr." },
                        fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider(color = Color(0xFFE4E3DB))
                Row(Modifier.fillMaxWidth()) { Text("la maison", Modifier.weight(1f)); Text("das Haus") }
                Row(Modifier.fillMaxWidth()) { Text("to discover", Modifier.weight(1f)); Text("entdecken") }
                if (page == 2) Text("✓  Gut gewusst", color = Color(0xFF3C6553), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
