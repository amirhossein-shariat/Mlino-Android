package app.malino.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.malino.core.Jalali

/** Categorical colours that stay distinguishable in both themes. The last one is for «other». */
val ChartPalette = listOf(
    Color(0xFF2E86C1), Color(0xFFE67E22), Color(0xFF27AE60), Color(0xFF8E44AD), Color(0xFFC0392B), Color(0xFF16A085), Color(0xFFD4AC0D), Color(0xFF7F8C8D),
)

/** Grouped income/expense columns, one group per month. The selected month is highlighted; tapping a group selects it. Bars grow with a spring. */
@Composable
fun MonthBars(months: List<Pair<String, Pair<Long, Long>>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val ex = extra
    val max = (months.maxOfOrNull { maxOf(it.second.first, it.second.second) } ?: 0L).coerceAtLeast(1L)
    Row(modifier.fillMaxWidth().height(190.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        months.forEachIndexed { i, (label, v) ->
            val on = i == selected
            val grow = remember { Animatable(0f) }
            LaunchedEffect(v) { grow.snapTo(0f); grow.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow)) }
            val dim by animateFloatAsState(if (on) 1f else 0.55f, tween(250), label = "dim")
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(MaterialTheme.shapes.small).pressScale { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally)) {
                    Bar(v.first.toFloat() / max, grow.value, ex.income.copy(alpha = dim))
                    Bar(v.second.toFloat() / max, grow.value, ex.expense.copy(alpha = dim))
                }
                Spacer(Modifier.height(6.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RowScope.Bar(fraction: Float, grow: Float, color: Color) {
    Box(
        Modifier.weight(1f).fillMaxHeight((fraction * grow).coerceIn(0.012f, 1f)).widthIn(max = 22.dp)
            .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 2.dp, bottomEnd = 2.dp)).background(color),
    )
}

/** Ring chart; slices sweep in clockwise. */
@Composable
fun Donut(slices: List<Pair<Color, Long>>, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) { progress.snapTo(0f); progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(176.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val total = slices.sumOf { it.second }.toFloat()
            val stroke = 26.dp.toPx()
            val tl = Offset(stroke / 2, stroke / 2)
            val sz = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(stroke))
            if (total <= 0f) return@Canvas
            var start = -90f
            slices.forEach { (c, v) ->
                val sweep = v / total * 360f * progress.value
                val gap = if (sweep > 4f) 2f else 0f
                drawArc(c, start + gap / 2, sweep - gap, false, tl, sz, style = Stroke(stroke))
                start += sweep
            }
        }
        content()
    }
}

/** Month-end total as a line that draws itself, with a soft area fill. Oldest value is on the right (RTL). */
@Composable
fun TrendLine(values: List<Long>, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(values) { progress.snapTo(0f); progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val line = MaterialTheme.colorScheme.secondary
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        if (values.size < 2) return@Canvas
        val lo = values.min(); val hi = values.max()
        val span = (hi - lo).coerceAtLeast(1L).toFloat()
        val padX = 10.dp.toPx(); val padY = 14.dp.toPx()
        val n = values.size
        fun pt(i: Int): Offset {
            val x = size.width - padX - (i.toFloat() / (n - 1)) * (size.width - 2 * padX)
            val y = size.height - padY - ((values[i] - lo) / span) * (size.height - 2 * padY)
            return Offset(x, y)
        }
        val path = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until n) {
                val a = pt(i - 1); val b = pt(i); val mx = (a.x + b.x) / 2
                cubicTo(mx, a.y, mx, b.y, b.x, b.y)
            }
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val shown = Path()
        measure.getSegment(0f, measure.length * progress.value, shown, true)
        val fill = Path().apply {
            addPath(shown)
            val end = measure.getPosition(measure.length * progress.value)
            lineTo(end.x, size.height); lineTo(pt(0).x, size.height); close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = .28f), line.copy(alpha = 0f))))
        drawPath(shown, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        val end = measure.getPosition(measure.length * progress.value)
        drawCircle(line, 5.dp.toPx(), end)
        drawCircle(Color.White.copy(alpha = .9f), 2.2.dp.toPx(), end)
    }
}

/** Horizontal share bar that fills with a spring. */
@Composable
fun ShareBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow), label = "share")
    Box(modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceAtLeast(0.01f)).clip(RoundedCornerShape(99.dp)).background(color))
    }
}

fun shortMonth(m: Int) = Jalali.MONTHS[m - 1]

/** A checkmark that draws itself; shown when the inbox is empty. */
@Composable
fun AnimatedCheck(modifier: Modifier = Modifier) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(700, 150, FastOutSlowInEasing)) }
    val bg = extra.incomeBg
    val fg = extra.income
    Canvas(modifier.size(84.dp)) {
        drawCircle(bg)
        val path = Path().apply {
            moveTo(size.width * .31f, size.height * .51f); lineTo(size.width * .44f, size.height * .64f); lineTo(size.width * .69f, size.height * .37f)
        }
        val m = PathMeasure().apply { setPath(path, false) }
        val seg = Path()
        m.getSegment(0f, m.length * p.value, seg, true)
        drawPath(seg, fg, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
