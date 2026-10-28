package app.malino.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.malino.core.Jalali

private fun greeting(h: Int) = when { h < 5 -> "شب بخیر"; h < 12 -> "صبح بخیر"; h < 17 -> "ظهر بخیر"; h < 20 -> "عصر بخیر"; else -> "شب بخیر" }

private fun todayText(): String {
    val d = Jalali.today()
    return Jalali.WEEKDAYS[Jalali.weekday(d.y, d.m, d.d)] + " " + Jalali.toFa(d.d) + " " + Jalali.MONTHS[d.m - 1] + " " + Jalali.toFa(d.y)
}

@Composable
fun HomeScreen(
    s: UiState, hidden: Boolean, onToggleHide: () -> Unit, needsSetup: Boolean,
    goInbox: () -> Unit, goMore: () -> Unit, goTx: () -> Unit, goReports: () -> Unit, onAdd: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hour = remember { Jalali.fromMillis(System.currentTimeMillis()).h }
    val pending = s.inbox.size + s.gaps.size
    LazyColumn(modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 130.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.enter(0), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(todayText(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(greeting(hour), style = MaterialTheme.typography.titleLarge)
                }
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(15.dp)).pressScale(goMore),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Settings, "تنظیمات", Modifier.size(21.dp)) }
            }
        }
        item { Hero(s, hidden, onToggleHide, Modifier.enter(1)) }
        if (needsSetup) item { AlertCard("برای ثبت خودکار تراکنش‌ها، دسترسی پیامک و اعلان را فعال کنید", "فعال‌سازی", goMore, Modifier.enter(2)) }
        if (pending > 0) item { PendingBanner(pending, goInbox, Modifier.enter(2)) }
        item {
            Row(Modifier.enter(3), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Quick("هزینه", Icons.Rounded.ArrowUpward, extra.expenseBg, extra.expense, Modifier.weight(1f)) { onAdd(0) }
                Quick("درآمد", Icons.Rounded.ArrowDownward, extra.incomeBg, extra.income, Modifier.weight(1f)) { onAdd(1) }
                Quick("گزارش", Icons.Rounded.BarChart, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.primary, Modifier.weight(1f), goReports)
            }
        }
        item { SectionTitle("کارت‌ها", Modifier.enter(4)) }
        item {
            if (s.cards.isEmpty()) Surface2(Modifier.enter(4)) { EmptyState("هنوز کارتی ندارید", "از «تنظیمات» اولین کارت را اضافه کنید.") }
            else {
                val ls = rememberLazyListState()
                LazyRow(Modifier.enter(4), state = ls, flingBehavior = rememberSnapFlingBehavior(ls), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.cards, key = { it.card.id }) { c -> BankCard(c, hidden) }
                }
            }
        }
        item {
            Row(Modifier.enter(5), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("آخرین تراکنش‌ها", Modifier.weight(1f))
                TextButton(goTx) { Text("همه", color = extra.accent, fontWeight = FontWeight.Medium) }
            }
        }
        if (s.txs.isEmpty()) item { Surface2(Modifier.enter(5)) { EmptyState("تراکنشی ثبت نشده", "پیامک بانک که بیاید خودکار اینجا می‌نشیند.") } }
        else item { Surface2(Modifier.enter(5)) { s.txs.take(6).forEach { TxRow(it) } } }
    }
}

@Composable
private fun Hero(s: UiState, hidden: Boolean, onToggleHide: () -> Unit, modifier: Modifier) {
    val ex = extra
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(ex.heroA, ex.heroB)))) {
        Canvas(Modifier.matchParentSize()) {
            val line = Stroke(1.5.dp.toPx())
            drawCircle(Color.White.copy(alpha = .12f), 115.dp.toPx(), Offset(size.width - 45.dp.toPx(), 25.dp.toPx()), style = line)
            drawCircle(Color.White.copy(alpha = .10f), 80.dp.toPx(), Offset(size.width - 20.dp.toPx(), 20.dp.toPx()), style = line)
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("مجموع موجودی کارت‌ها", Modifier.weight(1f), color = ex.heroFg.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .14f)).pressScale(onToggleHide),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (hidden) "نمایش موجودی" else "پنهان کردن موجودی", tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
            AnimatedContent(hidden, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "hide") { h ->
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (h) Text("••••••••", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold), color = ex.heroFg)
                    else AnimatedMoney(s.total, MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold), ex.heroFg)
                    Text("ریال", color = ex.heroFg.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 7.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroPill("درآمد این ماه", s.monthIn, Color(0xFF7BE0B1), hidden, Modifier.weight(1f))
                HeroPill("هزینه‌ی این ماه", s.monthOut, Color(0xFFFF9C97), hidden, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroPill(label: String, value: Long, dot: Color, hidden: Boolean, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .14f)).padding(12.dp, 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Text(label, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelSmall)
        }
        if (hidden) Text("••••••", color = Color.White, fontWeight = FontWeight.Bold)
        else AnimatedMoney(value, MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), Color.White)
    }
}

@Composable
private fun PendingBanner(count: Int, onClick: () -> Unit, modifier: Modifier) {
    val ex = extra
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(ex.warnBg).pressScale(onClick).padding(14.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = .55f)), contentAlignment = Alignment.Center) {
            Text(fa(count), color = ex.warn, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text("تراکنش منتظر تأیید", color = ex.warn, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text("کارت را بکشید یا دسته را انتخاب کنید", color = ex.warn, style = MaterialTheme.typography.labelSmall)
        }
        Icon(Icons.Rounded.ChevronLeft, null, tint = ex.warn)
    }
}

@Composable
private fun Quick(label: String, icon: ImageVector, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)).pressScale(onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconBadge(icon, bg, fg, 38)
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}

/** A bank-card tile tinted with the card's own colour. */
@Composable
private fun BankCard(c: CardUi, hidden: Boolean) {
    val base = Color(c.card.color)
    val chip = Brush.linearGradient(listOf(Color(0xFFF6DD9A), Color(0xFFC9A24B)))
    Box(
        Modifier.width(232.dp).height(140.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(lerp(base, Color.White, .12f), lerp(base, Color.Black, .42f)))),
    ) {
        Canvas(Modifier.matchParentSize()) { drawCircle(Color.White.copy(alpha = .10f), 75.dp.toPx(), Offset(20.dp.toPx(), size.height - 5.dp.toPx())) }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.card.name, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                Box(Modifier.size(30.dp, 22.dp).clip(RoundedCornerShape(6.dp)).background(chip))
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (hidden) Text("••••••", color = Color.White, style = MaterialTheme.typography.titleLarge)
                else AnimatedMoney(c.balance, MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp), Color.White)
                Text("ریال", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
            }
            Row {
                Text(c.card.bank.ifBlank { c.card.purpose }, Modifier.weight(1f), color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelSmall)
                if (c.card.last4.isNotBlank()) Text("⁦•••• ${Jalali.toFa(c.card.last4)}⁩", color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun AlertCard(text: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ex = extra
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(ex.warnBg).padding(14.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, Modifier.weight(1f), color = ex.warn, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick, colors = ButtonDefaults.textButtonColors(contentColor = ex.warn)) { Text(action, fontWeight = FontWeight.Bold) }
    }
}
