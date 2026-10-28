@file:OptIn(ExperimentalMaterial3Api::class)

package app.malino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.malino.core.Jalali
import app.malino.core.TxType

/** Swipe either way to confirm; the green strip behind the card shows what will happen. */
@Composable
private fun SwipeConfirm(onConfirm: () -> Unit, content: @Composable () -> Unit) {
    val latest by rememberUpdatedState(onConfirm)
    val haptic = LocalHapticFeedback.current
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        if (v == SwipeToDismissBoxValue.Settled) false else { haptic.performHapticFeedback(HapticFeedbackType.LongPress); latest(); true }
    })
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)).background(extra.incomeBg).padding(horizontal = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Arrangement.Start else Arrangement.End,
            ) {
                Icon(Icons.Rounded.Check, null, tint = extra.income)
                Spacer(Modifier.width(8.dp))
                Text("تأیید", color = extra.income, fontWeight = FontWeight.Bold)
            }
        },
        content = { content() },
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun InboxScreen(s: UiState, vm: MainVm, onTeach: (app.malino.data.RawE) -> Unit, onCardFrom: (app.malino.data.RawE) -> Unit, modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val picks = remember { mutableStateMapOf<String, String?>() }
    var showSnoozed by remember { mutableStateOf(false) }
    val empty = s.inbox.isEmpty() && s.gaps.isEmpty() && s.attention.isEmpty()
    LazyColumn(modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 130.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("صندوق ورودی", style = MaterialTheme.typography.titleLarge, modifier = Modifier.enter(0)) }

        items(s.gaps, key = { "gap" + it.gap.txId }) { g -> GapCard(g, s, vm, snoozed = false, Modifier.animateItem()) }

        if (s.snoozedGaps.isNotEmpty()) {
            item(key = "snoozed-h") {
                Row(Modifier.animateItem().fillMaxWidth().clickable { showSnoozed = !showSnoozed }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("اختلاف‌های موکول‌شده (${fa(s.snoozedGaps.size)})", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text(if (showSnoozed) "پنهان" else "نمایش", color = extra.accent)
                }
            }
            if (showSnoozed) items(s.snoozedGaps, key = { "sgap" + it.gap.txId }) { g -> GapCard(g, s, vm, snoozed = true, Modifier.animateItem()) }
        }

        items(s.attention, key = { "raw" + it.id }) { r ->
            Surface2(Modifier.animateItem()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (r.state == "unmapped") "کارتی برای این پیام پیدا نشد" else "قالب این پیام شناخته نشد", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(r.body, maxLines = 4, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (r.state == "unmapped") {
                            Button({ onCardFrom(r) }, colors = ButtonDefaults.buttonColors(containerColor = extra.accent, contentColor = extra.accentInk)) { Text("ساخت کارت از این پیام") }
                            FilledTonalButton({ vm.reprocess() }) { Text("پردازش دوباره") }
                        } else {
                            Button({ onTeach(r) }, colors = ButtonDefaults.buttonColors(containerColor = extra.accent, contentColor = extra.accentInk)) { Text("یاد بده") }
                            OutlinedButton({ vm.shareSample(ctx, r) }) { Text("ارسال نمونه") }
                        }
                        TextButton({ vm.dismissRaw(r.id) }) { Text("نادیده بگیر") }
                    }
                }
            }
        }

        if (empty) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 36.dp).enter(1), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AnimatedCheck()
                Text("همه‌چیز مرتب است", style = MaterialTheme.typography.titleMedium)
                Text("تراکنش‌های تازه برای تأیید اینجا می‌آیند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        items(s.inbox, key = { it.tx.id }) { u ->
            val t = u.tx
            val cats = remember(s.categories, t.type) {
                val kind = if (t.type == TxType.IN) "income" else "expense"
                s.categories.filter { it.kind == kind }
            }
            val sel = if (picks.containsKey(t.id)) picks[t.id] else t.categoryId
            val cur by rememberUpdatedState(sel)
            Box(Modifier.animateItem()) {
                SwipeConfirm(onConfirm = { vm.confirm(t.id, if (t.type == TxType.TRANSFER) null else cur) }) {
                    Surface2 {
                        TxRow(u)
                        if (t.type == TxType.TRANSFER) {
                            Row(Modifier.padding(14.dp, 0.dp, 14.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button({ vm.confirm(t.id, null) }) { Text("تأیید") }
                                OutlinedButton({ vm.unlink(t.id) }) { Text("انتقال نیست، جدا کن") }
                            }
                        } else {
                            LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(cats, key = { it.id }) { c ->
                                    val ic = catIcon(c.id)
                                    FilterChip(
                                        selected = sel == c.id, onClick = { picks[t.id] = if (sel == c.id) null else c.id }, label = { Text(c.name) },
                                        leadingIcon = if (ic != null) { { Icon(ic, null, Modifier.size(16.dp)) } } else null,
                                        shape = RoundedCornerShape(99.dp),
                                    )
                                }
                            }
                            Row(Modifier.padding(14.dp, 8.dp, 14.dp, 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button({ vm.confirm(t.id, sel) }, colors = ButtonDefaults.buttonColors(containerColor = extra.accent, contentColor = extra.accentInk)) { Text("تأیید", fontWeight = FontWeight.Bold) }
                                Text("یا کارت را بکشید", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        if (s.inbox.size > 1) item {
            FilledTonalButton({ vm.confirmAll(picks) }, Modifier.fillMaxWidth().animateItem()) { Text("تأیید همه (${fa(s.inbox.size)})") }
        }
    }
}


/** A balance mismatch explained in plain words, with the period it happened in and ways to settle it right here (or later). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GapCard(g: GapUi, s: UiState, vm: MainVm, snoozed: Boolean, modifier: Modifier) {
    val ex = extra
    val less = g.gap.diff < 0
    val types = if (less) listOf(TxType.OUT to "هزینه", TxType.FEE to "کارمزد", TxType.ADJUST to "تعدیل") else listOf(TxType.IN to "درآمد", TxType.ADJUST to "تعدیل")
    var open by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(types.first().first) }
    var cat by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    // suggested date: the day of the message that revealed the gap, but any day inside the period is fine
    var at by remember { mutableLongStateOf((g.gap.at - 1000).coerceAtLeast(g.gap.from + 1)) }
    val dt = Jalali.fromMillis(at)
    var hh by remember { mutableStateOf(two(dt.h)) }
    var mm by remember { mutableStateOf(two(dt.mi)) }
    var pickDate by remember { mutableStateOf(false) }
    val cats = remember(s.categories, type) { s.categories.filter { it.kind == if (type == TxType.IN) "income" else "expense" } }
    val amount = money(kotlin.math.abs(g.gap.diff))
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(ex.warnBg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("اختلاف مانده در ${cardLabel(g.card)}", color = ex.warn, fontWeight = FontWeight.Bold)
        Text(
            "مانده‌ی بانک در آخرین پیامک (${dayLabel(g.gap.at)} ${clock(g.gap.at)}) $amount ریال ${if (less) "کمتر" else "بیشتر"} از حساب برنامه است. " +
                "این اختلاف بین ${dayLabel(g.gap.from)} ${clock(g.gap.from)} و ${dayLabel(g.gap.at)} ${clock(g.gap.at)} پیش آمده؛ یعنی در این بازه " +
                (if (less) "یک خروج پول ثبت نشده (پیامکش نرسیده یا کارمزد بوده)." else "یک ورودی پول ثبت نشده (پیامکش نرسیده)."),
            color = ex.warn, style = MaterialTheme.typography.bodySmall,
        )
        if (!open) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ open = true }, colors = ButtonDefaults.buttonColors(containerColor = ex.warn, contentColor = ex.warnBg)) { Text("تعیین تکلیف") }
                if (!snoozed) OutlinedButton({ vm.snoozeGap(g.gap.txId, true) }) { Text("بعداً") }
                else OutlinedButton({ vm.snoozeGap(g.gap.txId, false) }) { Text("برگردان به صندوق") }
            }
        } else {
            Text("این مبلغ چه بوده؟", color = ex.warn, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                types.forEach { (t, label) -> FilterChip(type == t, { type = t; cat = null }, { Text(label) }, shape = RoundedCornerShape(99.dp)) }
            }
            if (type == TxType.OUT || type == TxType.IN) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(cats, key = { it.id }) { c -> FilterChip(cat == c.id, { cat = if (cat == c.id) null else c.id }, { Text(c.name) }, shape = RoundedCornerShape(99.dp)) }
            }
            Text(
                when (type) {
                    TxType.OUT -> "هزینه: خرجی که پیامکش نرسیده. در گزارش هزینه‌ها و دسته‌بندی حساب می‌شود."
                    TxType.FEE -> "کارمزد: کارمزد بانکی (مثلاً انتقال یا کارت). جزو هزینه‌ی «کارمزد بانکی» حساب می‌شود."
                    TxType.IN -> "درآمد: واریزی که پیامکش نرسیده. در گزارش درآمد حساب می‌شود."
                    else -> "تعدیل: فقط مانده را با بانک یکی می‌کند و جزو هزینه یا درآمد نیست. وقتی علت را نمی‌دانید این را بزنید."
                },
                color = ex.warn, style = MaterialTheme.typography.labelSmall,
            )
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("یادداشت (اختیاری)") }, shape = RoundedCornerShape(14.dp))
            Text("تاریخ ثبت (پیشنهاد: روز آخرین پیامک؛ می‌توانید هر روز داخل بازه‌ی اختلاف را بگذارید):", color = ex.warn, style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton({ pickDate = true }, shape = RoundedCornerShape(14.dp)) { Text(fullDate(at)) }
                OutlinedTextField(hh, { hh = digitsOnly(it).take(2) }, Modifier.width(72.dp), singleLine = true, label = { Text("ساعت") }, shape = RoundedCornerShape(14.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                OutlinedTextField(mm, { mm = digitsOnly(it).take(2) }, Modifier.width(72.dp), singleLine = true, label = { Text("دقیقه" ) }, shape = RoundedCornerShape(14.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            }
            Text("بازه‌ی مجاز: ${dayLabel(g.gap.from)} ${clock(g.gap.from)} تا ${dayLabel(g.gap.at)} ${clock(g.gap.at)}", color = ex.warn, style = MaterialTheme.typography.labelSmall)
            if (pickDate) JalaliPickerDialog(dt.date, { pickDate = false }) { d -> at = Jalali.toMillis(d.y, d.m, d.d, dt.h, dt.mi); pickDate = false }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({
                    val d = Jalali.fromMillis(at).date
                    vm.resolveGap(g.gap, type, cat, note.trim(), Jalali.toMillis(d.y, d.m, d.d, (hh.toIntOrNull() ?: 0).coerceIn(0, 23), (mm.toIntOrNull() ?: 0).coerceIn(0, 59)))
                }, colors = ButtonDefaults.buttonColors(containerColor = ex.warn, contentColor = ex.warnBg)) { Text("ثبت") }
                TextButton({ open = false }) { Text("انصراف") }
                if (!snoozed) TextButton({ vm.snoozeGap(g.gap.txId, true) }) { Text("بعداً") }
            }
        }
    }
}
