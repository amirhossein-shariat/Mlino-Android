package app.malino.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Marks a composable as a stop of the intro tour. */
fun Modifier.tourTarget(targets: MutableMap<String, LayoutCoordinates>, key: String): Modifier =
    onGloballyPositioned { targets[key] = it }

enum class TourTab { Home, Inbox }

data class TourStep(val key: String, val tab: TourTab, val title: String, val body: String)

val TOUR_STEPS = listOf(
    TourStep("hero", TourTab.Home, "موجودی کل شما", "مجموع موجودی همه‌ی کارت‌ها اینجاست. با آیکن چشم می‌توانید مبلغ را پنهان کنید. نمودار و گزارش ماهانه در تب «گزارش» است."),
    TourStep("cards", TourTab.Home, "کارت‌های شما", "هر کارت یا حساب یک کارت دارد. روی آن بزنید تا خلاصه، هزینه‌ها و واریزی‌هایش را ببینید. با کشیدن، بین کارت‌ها جابه‌جا شوید."),
    TourStep("add", TourTab.Home, "ثبت دستی", "هر تراکنشی که پیامکش نمی‌آید (مثلاً پول نقد) را با همین دکمه‌ی + ثبت کنید؛ با کیبورد عددی و دسته‌های آماده."),
    TourStep("inbox", TourTab.Inbox, "صندوق ورودی", "پیامک‌ها و اعلان‌های بانکی خودکار اینجا می‌آیند. دسته را انتخاب و تأیید کنید، یا کارت را بکشید. اگر مانده‌ی بانک با برنامه نخواند، اختلاف هم همین‌جا تعیین تکلیف می‌شود."),
    TourStep("gear", TourTab.Home, "تنظیمات", "اینجا پین و اثر انگشت، پشتیبان‌گیری، وارد کردن صورت‌حساب، «یاد بده»، حالت روشن/تیره و ریست را پیدا می‌کنید. این آموزش را هم از همین‌جا دوباره ببینید."),
)

/** Dimmed screen with a moving spotlight, a pulsing ring and a short explanation for each stop. */
@Composable
fun TourOverlay(step: Int, targets: Map<String, LayoutCoordinates>, onNext: () -> Unit, onSkip: () -> Unit) {
    val st = TOUR_STEPS[step]
    val ex = extra
    val density = LocalDensity.current
    // positions are re-read for a while: tabs and list items are still animating into place when a step starts
    val rect by produceState<Rect?>(null, step) {
        value = null
        delay(380)
        while (true) { value = targets[st.key]?.takeIf { it.isAttached }?.boundsInRoot(); delay(100) }
    }
    val pad = with(density) { 8.dp.toPx() }
    val r = rect
    val l by animateFloatAsState(r?.left ?: 0f, spring(0.8f, Spring.StiffnessLow), label = "l")
    val t by animateFloatAsState(r?.top ?: 0f, spring(0.8f, Spring.StiffnessLow), label = "t")
    val rr by animateFloatAsState(r?.right ?: 0f, spring(0.8f, Spring.StiffnessLow), label = "r")
    val b by animateFloatAsState(r?.bottom ?: 0f, spring(0.8f, Spring.StiffnessLow), label = "b")
    val show by animateFloatAsState(if (r != null) 1f else 0f, tween(260), label = "show")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")

    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        val screenH = with(density) { maxHeight.toPx() }
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = .78f))
            if (show > 0.01f) {
                val tl = Offset(l - pad, t - pad); val sz = Size(rr - l + 2 * pad, b - t + 2 * pad)
                val cr = CornerRadius(26.dp.toPx())
                drawRoundRect(Color.Black, tl, sz, cr, blendMode = BlendMode.Clear, alpha = show)
                val grow = 3.dp.toPx() * pulse
                drawRoundRect(ex.accent.copy(alpha = (.95f - .45f * pulse) * show), Offset(tl.x - grow, tl.y - grow), Size(sz.width + 2 * grow, sz.height + 2 * grow),
                    CornerRadius(26.dp.toPx() + grow), style = Stroke(2.5.dp.toPx()))
            }
        }
        // tooltip above the target when the target is in the lower half of the screen, otherwise below it
        val below = r == null || (t + b) / 2f < screenH / 2f
        val gap = with(density) { 22.dp }
        val edge = if (r == null) 0.dp else with(density) { if (below) (b + pad).toDp() + gap else (screenH - t + pad).toDp() + gap }
        Box(
            Modifier.align(if (r == null) Alignment.Center else if (below) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(top = if (r != null && below) edge else 0.dp, bottom = if (r != null && !below) edge else 0.dp).padding(horizontal = 18.dp),
        ) {
            androidx.compose.animation.AnimatedContent(step, transitionSpec = { androidx.compose.animation.fadeIn(tween(260, 120)) togetherWith androidx.compose.animation.fadeOut(tween(120)) }, label = "tip") { i ->
                val s = TOUR_STEPS[i]
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("آموزش ${fa(i + 1)} از ${fa(TOUR_STEPS.size)}", color = ex.accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TOUR_STEPS.indices.forEach { d ->
                            val w by animateDpAsState(if (d == i) 22.dp else 7.dp, spring(0.7f), label = "dot")
                            Box(Modifier.height(7.dp).width(w).clip(CircleShape).background(if (d == i) ex.accent else MaterialTheme.colorScheme.outline))
                        }
                    }
                    Text(s.title, style = MaterialTheme.typography.titleLarge)
                    Text(s.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onNext, colors = ButtonDefaults.buttonColors(containerColor = ex.accent, contentColor = ex.accentInk)) {
                            Text(if (i == TOUR_STEPS.lastIndex) "شروع کار" else "بعدی", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.weight(1f))
                        if (i < TOUR_STEPS.lastIndex) TextButton(onSkip) { Text("رد کردن", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}
