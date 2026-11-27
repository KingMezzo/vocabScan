package de.vocabscan.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** A lightweight entrance that keeps the previous screen out of composition. */
@Composable
internal fun ScreenEnter(content: @Composable () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val opacity = animateFloatAsState(
        if (entered) 1f else 0.9f, tween(190, easing = FastOutSlowInEasing), label = "Ansicht einblenden")
    val offset = animateFloatAsState(
        if (entered) 0f else 12f, tween(190, easing = FastOutSlowInEasing), label = "Ansicht einschieben")
    Box(Modifier.fillMaxSize().graphicsLayer {
        alpha = opacity.value
        translationY = offset.value.dp.toPx()
    }) { content() }
}
