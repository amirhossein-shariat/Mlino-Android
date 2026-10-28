@file:OptIn(ExperimentalMaterial3Api::class)

package app.malino.ui

import androidx.compose.foundation.background
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

@Composable
fun InboxScreen(s: UiState, vm: MainVm, modifier: Modifier = Modifier) {
    val picks = remember { mutableStateMapOf<String, String?>() }
    val empty = s.inbox.isEmpty() && s.gaps.isEmpty() && s.attention.isEmpty()
    LazyColumn(modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 130.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("صندوق ورودی", style = MaterialTheme.typography.titleLarge, modifier = Modifier.enter(0)) }

        items(s.gaps, key = { "gap" + it.gap.txId }) { g ->
            val ex = extra
            Column(
                Modifier.animateItem().fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(ex.warnBg).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("تراکنش جاافتاده در ${g.cardName}", color = ex.warn, fontWeight = FontWeight.Bold)
                Text(
                    "مانده‌ی بانک ${money(kotlin.math.abs(g.gap.diff))} ریال ${if (g.gap.diff < 0) "کمتر" else "بیشتر"} از حساب برنامه است. معمولاً یک کارمزد یا پیامک گم‌شده است.",
                    color = ex.warn, style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (g.gap.diff < 0) Button({ vm.resolveGap(g.gap, true) }, colors = ButtonDefaults.buttonColors(containerColor = ex.warn, contentColor = ex.warnBg)) { Text("ثبت به‌عنوان کارمزد") }
                    FilledTonalButton({ vm.resolveGap(g.gap, false) }) { Text("تعدیل") }
                }
            }
        }

        items(s.attention, key = { "raw" + it.id }) { r ->
            Surface2(Modifier.animateItem()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (r.state == "unmapped") "کارتی برای این پیام پیدا نشد" else "قالب این پیام شناخته نشد", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(r.body, maxLines = 4, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (r.state == "unmapped") FilledTonalButton({ vm.reprocess() }) { Text("پردازش دوباره") }
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
                                    FilterChip(
                                        selected = sel == c.id, onClick = { picks[t.id] = if (sel == c.id) null else c.id }, label = { Text(c.name) },
                                        leadingIcon = catIcon(c.id)?.let { ic -> { Icon(ic, null, Modifier.size(16.dp)) } },
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
