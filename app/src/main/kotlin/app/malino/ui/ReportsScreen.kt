package app.malino.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.malino.core.Card
import app.malino.core.CardReport
import app.malino.core.Jalali
import app.malino.core.Report
import app.malino.core.Reports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReportsScreen(s: UiState, onCard: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    val today = remember { Jalali.today() }
    var y by rememberSaveable { mutableIntStateOf(today.y) }
    var m by rememberSaveable { mutableIntStateOf(today.m) }
    val series by produceState<List<Report>>(emptyList(), s.coreTxs, s.coreCards, y, m) {
        value = withContext(Dispatchers.Default) { Reports.series(y, m, 6, s.coreCards, s.coreTxs) }
    }
    val cur = series.lastOrNull()
    val prev = series.getOrNull(series.size - 2)
    val catName = remember(s.categories) { s.categories.associate { it.id to it.name } + ("" to "بدون دسته") }
    val isNow = y == today.y && m == today.m

    fun step(d: Int) { val i = y * 12 + m - 1 + d; y = i / 12; m = i % 12 + 1 }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.enter(0), verticalAlignment = Alignment.CenterVertically) {
                Text("گزارش ماهانه", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton({ step(-1) }) { Icon(Icons.Rounded.ChevronRight, "ماه قبل") }
                AnimatedContent(y * 12 + m, transitionSpec = {
                    val up = targetState > initialState
                    (fadeIn(tween(200)) + slideInVertically(tween(260)) { if (up) -it else it }) togetherWith (fadeOut(tween(120)) + slideOutVertically(tween(200)) { if (up) it else -it })
                }, label = "month") { _ ->
                    Text(Jalali.MONTHS[m - 1] + " " + Jalali.toFa(y), style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 110.dp), textAlign = TextAlign.Center)
                }
                IconButton({ step(1) }, enabled = !isNow) { Icon(Icons.Rounded.ChevronLeft, "ماه بعد") }
            }
        }
        if (cur == null) { item { Surface2 { EmptyState("در حال محاسبه…", "") } }; return@LazyColumn }
        if (cur.txCount == 0) item { Surface2(Modifier.enter(1)) { EmptyState("این ماه تراکنشی نیست", "تراکنش‌های قبل از «تاریخ شروع» کارت در گزارش نمی‌آیند.") } }

        item { Kpis(cur, prev, Modifier.enter(1)) }

        item {
            Surface2(Modifier.enter(2)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("درآمد و هزینه، ۶ ماه اخیر", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Legend(extra.income, "درآمد"); Spacer(Modifier.width(10.dp)); Legend(extra.expense, "هزینه")
                    }
                    MonthBars(series.map { shortMonth(it.m) to (it.income to it.expense) }, series.lastIndex, onSelect = { i ->
                        val r = series[i]; y = r.y; m = r.m
                    })
                }
            }
        }

        item {
            Surface2(Modifier.enter(3)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("هزینه بر اساس دسته", fontWeight = FontWeight.Bold)
                    val parts = cur.expenseByCat.entries.sortedByDescending { it.value }
                    if (parts.isEmpty()) Text("هزینه‌ای ثبت نشده.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else {
                        val top = parts.take(6)
                        val rest = parts.drop(6).sumOf { it.value }
                        val slices = top.mapIndexed { i, e -> ChartPalette[i] to e.value } + if (rest > 0) listOf(ChartPalette.last() to rest) else emptyList()
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Donut(slices) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("جمع هزینه", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    AnimatedMoney(cur.expense, MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        slices.forEachIndexed { i, (c, v) ->
                            val name = if (i < top.size) catName[top[i].key] ?: top[i].key.ifEmpty { "بدون دسته" } else "سایر دسته‌ها"
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                                    Spacer(Modifier.width(8.dp))
                                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(fa((v * 100 / cur.expense.coerceAtLeast(1)).toInt()) + "٪  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(money(v), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                                ShareBar(v.toFloat() / cur.expense.coerceAtLeast(1), c)
                            }
                        }
                    }
                }
            }
        }

        if (cur.incomeByCat.isNotEmpty()) item {
            Surface2(Modifier.enter(3)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("درآمد بر اساس دسته", fontWeight = FontWeight.Bold)
                    cur.incomeByCat.entries.sortedByDescending { it.value }.forEach { e ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            Row { Text(catName[e.key] ?: e.key.ifEmpty { "بدون دسته" }, Modifier.weight(1f)); Text(money(e.value), fontWeight = FontWeight.Medium) }
                            ShareBar(e.value.toFloat() / cur.income.coerceAtLeast(1), extra.income)
                        }
                    }
                }
            }
        }

        item {
            Surface2(Modifier.enter(4)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مجموع موجودی کارت‌ها در پایان ماه", fontWeight = FontWeight.Bold)
                    TrendLine(series.map { it.wealth })
                    Row {
                        Text(shortMonth(series.first().m), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(shortMonth(cur.m), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedMoney(cur.wealth, MaterialTheme.typography.titleMedium)
                        Text(" ریال", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        cur.wealthChange?.let { Delta(it, true) }
                    }
                }
            }
        }

        if (cur.businessIncome + cur.businessExpense > 0) item {
            Surface2(Modifier.enter(4)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("شخصی و کسب‌وکار", fontWeight = FontWeight.Bold)
                    SplitRow("هزینه", cur.personalExpense, cur.businessExpense)
                    SplitRow("درآمد", cur.personalIncome, cur.businessIncome)
                }
            }
        }

        item { SectionTitle("کارت‌ها در این ماه", Modifier.enter(5)) }
        itemsIndexed(cur.cards, key = { _, c -> "cm" + c.cardId }) { i, cr ->
            CardMonthTile(cr, s.cards.firstOrNull { it.card.id == cr.cardId }?.card, { onCard(cr.cardId) }, Modifier.enter(5 + i))
        }

        if (cur.top.isNotEmpty()) item {
            Surface2(Modifier.enter(5)) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text("بزرگ‌ترین برداشت‌ها", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp))
                    cur.top.forEach { t ->
                        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.categoryId?.let { catName[it] } ?: t.note.ifBlank { "برداشت" }, fontWeight = FontWeight.Medium)
                                Text("${dayLabel(t.at)} ${clock(t.at)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("−" + money(t.amount), color = extra.expense, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(c: Color, text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(9.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(4.dp))
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SplitRow(label: String, personal: Long, business: Long) {
    val total = (personal + business).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row { Text(label, Modifier.weight(1f)); Text("شخصی ${money(personal)} · کسب‌وکار ${money(business)}", style = MaterialTheme.typography.bodySmall) }
        ShareBar(business.toFloat() / total, ChartPalette[1])
    }
}

@Composable
private fun Kpis(cur: Report, prev: Report?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kpi("درآمد", cur.income, prev?.income, extra.income, true, Modifier.weight(1f))
            Kpi("هزینه", cur.expense, prev?.expense, extra.expense, false, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kpi("خالص (درآمد − هزینه)", cur.net, prev?.net, if (cur.net >= 0) extra.income else extra.expense, true, Modifier.weight(1f))
            if (cur.savings != null) Kpi("پس‌انداز این ماه", cur.savings!!, prev?.savings, MaterialTheme.colorScheme.secondary, true, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Kpi(label: String, value: Long, prev: Long?, color: Color, upIsGood: Boolean, modifier: Modifier) {
    Column(modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedMoney(value, MaterialTheme.typography.titleMedium, color)
        val d = prev?.let { Reports.delta(value, it) }
        if (d != null) Delta(d.toLong(), upIsGood, percent = true)
    }
}

@Composable
private fun Delta(v: Long, upIsGood: Boolean, percent: Boolean = false) {
    val good = (v >= 0) == upIsGood
    val c = if (v == 0L) MaterialTheme.colorScheme.onSurfaceVariant else if (good) extra.income else extra.expense
    Text((if (v >= 0) "▲ " else "▼ ") + (if (percent) fa(kotlin.math.abs(v).toInt()) + "٪ نسبت به ماه قبل" else money(v) + " نسبت به ماه قبل"),
        style = MaterialTheme.typography.labelSmall, color = c)
}


/** One card's month as its own tile: colour badge, bank and balance, an in/out split bar, and three numbers. Tap opens the card. */
@Composable
private fun CardMonthTile(r: CardReport, card: Card?, onClick: () -> Unit, modifier: Modifier) {
    val ex = extra
    val base = Color(card?.color ?: 0xFF14304F)
    val shape = RoundedCornerShape(26.dp)
    val change = r.closing - r.opening
    Column(
        modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .pressScale(onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(lerp(base, Color.White, .15f), lerp(base, Color.Black, .40f)))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.CreditCard, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
            Column(Modifier.weight(1f)) {
                Text(card?.bank?.ifBlank { null } ?: r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    listOf(r.name.takeIf { card?.bank?.isNotBlank() == true }, card?.purpose?.takeIf { it.isNotBlank() && it != r.name }).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("مانده‌ی پایان ماه", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedMoney(r.closing, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
        if (r.inn + r.out > 0) {
            Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (r.inn > 0) Box(Modifier.weight(r.inn.toFloat()).fillMaxHeight().background(ex.income))
                if (r.out > 0) Box(Modifier.weight(r.out.toFloat()).fillMaxHeight().background(ex.expense))
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            MiniStat("شروع ماه", money(r.opening), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
            MiniStat("ورودی", "+" + money(r.inn), ex.income, Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
            MiniStat("خروجی", "−" + money(r.out), ex.expense, Modifier.weight(1f))
        }
        if (r.inn + r.out > 0) {
            val up = change >= 0
            StatusPill((if (up) "▲ " else "▼ ") + money(kotlin.math.abs(change)) + " تغییر در این ماه", if (up) ex.incomeBg else ex.expenseBg, if (up) ex.income else ex.expense)
        } else Text("در این ماه حرکتی نداشته", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}
