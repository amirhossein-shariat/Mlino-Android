package app.malino.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.malino.core.TxType

@Composable
fun InboxScreen(s: UiState, vm: MainVm, modifier: Modifier = Modifier) {
    val picks = remember { mutableStateMapOf<String, String?>() }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("صندوق ورودی", style = MaterialTheme.typography.titleLarge, modifier = Modifier.enter(0)) }

        items(s.gaps, key = { "gap" + it.gap.txId }) { g ->
            val ex = extra
            Column(Modifier.animateItem().fillMaxWidth().clip(MaterialTheme.shapes.medium).background(ex.warnBg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تراکنش جاافتاده در ${g.cardName}", color = ex.warn, fontWeight = FontWeight.Bold)
                Text("مانده‌ی بانک ${money(kotlin.math.abs(g.gap.diff))} ریال ${if (g.gap.diff < 0) "کمتر" else "بیشتر"} از حساب برنامه است. معمولاً یک کارمزد یا پیامک گم‌شده است.",
                    color = ex.warn, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (g.gap.diff < 0) FilledTonalButton({ vm.resolveGap(g.gap, true) }) { Text("ثبت به‌عنوان کارمزد") }
                    OutlinedButton({ vm.resolveGap(g.gap, false) }) { Text("ثبت به‌عنوان تعدیل") }
                }
            }
        }

        items(s.attention, key = { "raw" + it.id }) { r ->
            Column(Modifier.animateItem().fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (r.state == "unmapped") "کارتی برای این پیام پیدا نشد" else "قالب این پیام شناخته نشد",
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(r.body, maxLines = 4, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r.state == "unmapped") FilledTonalButton({ vm.reprocess() }) { Text("پردازش دوباره") }
                    TextButton({ vm.dismissRaw(r.id) }) { Text("نادیده بگیر") }
                }
            }
        }

        if (s.inbox.isEmpty() && s.gaps.isEmpty() && s.attention.isEmpty()) item {
            Surface2(Modifier.enter(1)) { EmptyState("همه‌چیز مرتب است", "تراکنش‌های تازه برای تأیید اینجا می‌آیند.") }
        }

        items(s.inbox, key = { it.tx.id }) { u ->
            val t = u.tx
            val cats = remember(s.categories, t.type) {
                val kind = if (t.type == TxType.IN) "income" else "expense"
                s.categories.filter { it.kind == kind }
            }
            val sel = if (picks.containsKey(t.id)) picks[t.id] else t.categoryId
            Surface2(Modifier.animateItem()) {
                TxRow(u)
                if (t.type == TxType.TRANSFER) {
                    Row(Modifier.padding(14.dp, 0.dp, 14.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ vm.confirm(t.id, null) }) { Text("تأیید") }
                        OutlinedButton({ vm.unlink(t.id) }) { Text("انتقال نیست، جدا کن") }
                    }
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(cats, key = { it.id }) { c ->
                            FilterChip(selected = sel == c.id, onClick = { picks[t.id] = if (sel == c.id) null else c.id }, label = { Text(c.name) })
                        }
                    }
                    Row(Modifier.padding(14.dp, 8.dp, 14.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ vm.confirm(t.id, sel) }) { Text("تأیید") }
                    }
                }
            }
        }

        if (s.inbox.size > 1) item {
            AnimatedVisibility(true) { FilledTonalButton({ vm.confirmAll(picks) }, Modifier.fillMaxWidth()) { Text("تأیید همه (${fa(s.inbox.size)})") } }
        }
    }
}
