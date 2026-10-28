package app.malino.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Springy press feedback: shrinks slightly while held, bounces back. */
fun Modifier.pressScale(onClick: () -> Unit): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press",
    )
    graphicsLayer { scaleX = s; scaleY = s }.clickable(interactionSource = src, indication = null) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
}

/** Staggered entrance: each item fades and rises into place, a little after the previous one. */
@Composable
fun Modifier.enter(index: Int): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(12)) * 45L)
        a.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    }
    return graphicsLayer { alpha = a.value.coerceIn(0f, 1f); translationY = (1f - a.value) * 28.dp.toPx() }
}

/** Counts up to [value] (and between values on change) instead of jumping. */
@Composable
fun AnimatedMoney(value: Long, style: TextStyle, color: Color = Color.Unspecified, prefix: String = "") {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value) { anim.animateTo(value.toFloat(), tween(850, easing = FastOutSlowInEasing)) }
    val shown = if (anim.isRunning) anim.value.toLong() else value
    Text(money(shown) + (if (shown < 0) "\u200F−" else if (prefix.isNotEmpty()) "\u200F" + prefix else ""), style = style, color = color)
}
