package app.malino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.malino.core.Jalali
import app.malino.core.Parsers
import app.malino.core.Role
import app.malino.core.Templates
import app.malino.data.RawE

private fun roleLabel(r: Role) = when (r) { Role.AMOUNT -> "مبلغ"; Role.BALANCE -> "مانده"; Role.KEY -> "شماره حساب/کارت"; Role.DATE -> "تاریخ"; Role.TIME -> "ساعت" }

@Composable
private fun roleColor(r: Role): Color = when (r) {
    Role.AMOUNT -> extra.accent; Role.BALANCE -> MaterialTheme.colorScheme.secondary; Role.KEY -> extra.warn
    Role.DATE -> MaterialTheme.colorScheme.onSurfaceVariant; Role.TIME -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** "Teach Malino": pick a role, tap the number with that role in the real message; the app builds a template and reads such messages from now on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeachPage(raw: RawE, s: UiState, vm: MainVm, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val text = remember(raw.id) { Parsers.normalize(if (raw.title.isBlank()) raw.body else raw.title + "\n" + raw.body) }
    val toks = remember(text) { Templates.tokens(text) }
    val guess = remember(text) { Templates.guessDirection(text) }
    var role by remember { mutableStateOf(Role.AMOUNT) }
    val picks = remember { mutableStateMapOf<Role, Int>() }
    var out by remember { mutableStateOf(guess?.first ?: true) }
    var cardId by remember { mutableStateOf(s.cards.singleOrNull()?.card?.id) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun tap(ord: Int) {
        val tok = toks[ord].text
        val ok = when (role) { Role.AMOUNT, Role.BALANCE -> Templates.isMoney(tok); Role.TIME -> Templates.isTime(tok); Role.DATE -> Templates.isDate(tok); Role.KEY -> tok.all { it.isDigit() } }
        if (!ok) { msg = "این عدد برای «${roleLabel(role)}» مناسب نیست."; return }
        msg = null
        if (picks[role] == ord) picks.remove(role)
        else { picks.entries.removeAll { it.value == ord }; picks[role] = ord }
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("یاد بده", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); TextButton(onClose) { Text("بستن") } } }
        item {
            Text("۱) نقش را انتخاب کنید. ۲) روی عدد همان نقش در پیام بزنید. فقط مبلغ و مانده الزامی است.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Role.entries.forEach { r ->
                    FilterChip(role == r, { role = r }, { Text(roleLabel(r) + if (picks.containsKey(r)) " ✓" else "") }, shape = RoundedCornerShape(99.dp))
                }
            }
        }
        item {
            Surface2 {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var lineStart = 0
                    text.split('\n').forEach { line ->
                        val lineEnd = lineStart + line.length
                        val inLine = toks.filter { it.start >= lineStart && it.end <= lineEnd }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            var cursor = lineStart
                            fun words(seg: String) = seg.split(' ').filter { it.isNotBlank() }.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            inLine.forEach { tk ->
                                words(text.substring(cursor, tk.start))
                                val picked = picks.entries.firstOrNull { it.value == tk.ordinal }?.key
                                val c = picked?.let { roleColor(it) }
                                Box(
                                    Modifier.clip(RoundedCornerShape(10.dp)).background(c?.copy(alpha = .22f) ?: Color.Transparent)
                                        .border(1.2.dp, c ?: MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)).pressScale { tap(tk.ordinal) }.padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(Jalali.toFa(tk.text) + (picked?.let { " · " + roleLabel(it) } ?: ""), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                cursor = tk.end
                            }
                            words(text.substring(cursor, lineEnd))
                        }
                        lineStart = lineEnd + 1
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("نوع پیام:", style = MaterialTheme.typography.bodyMedium)
                FilterChip(out, { out = true }, { Text("برداشت") }, shape = RoundedCornerShape(99.dp))
                FilterChip(!out, { out = false }, { Text("واریز") }, shape = RoundedCornerShape(99.dp))
            }
        }
        item {
            if (s.cards.isEmpty()) Text("اول یک کارت بسازید.", color = extra.warn)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("کارت:", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.bodyMedium)
                s.cards.forEach { c -> FilterChip(cardId == c.card.id, { cardId = c.card.id }, { Text(c.card.name) }, shape = RoundedCornerShape(99.dp)) }
            }
        }
        msg?.let { m -> item { Text(m, color = if (m.startsWith("یاد گرفتم")) extra.income else extra.warn, fontWeight = FontWeight.Medium) } }
        item {
            Button(
                enabled = picks.containsKey(Role.AMOUNT) && picks.containsKey(Role.BALANCE) && cardId != null && !busy,
                onClick = {
                    busy = true
                    val word = if (guess != null && guess.first == out) guess.second else ""
                    vm.teach(raw, text, picks.toMap(), out, word, cardId!!) { ok, m -> busy = false; msg = m; if (ok) onClose() }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = extra.accent, contentColor = extra.accentInk),
            ) { Text("یاد بگیر", fontWeight = FontWeight.Bold) }
        }
    }
}
