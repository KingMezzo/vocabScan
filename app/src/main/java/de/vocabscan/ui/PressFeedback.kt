package de.vocabscan.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

internal data class PressFeedback(val source: MutableInteractionSource, val modifier: Modifier)

@Composable
internal fun rememberPressFeedback(): PressFeedback {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = if (pressed) tween(90, easing = FastOutSlowInEasing)
            else spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
        label = "Tastendruck",
    )
    return PressFeedback(source, Modifier.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    })
}
