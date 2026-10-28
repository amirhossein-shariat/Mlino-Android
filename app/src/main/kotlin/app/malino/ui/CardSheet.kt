package app.malino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.malino.core.Jalali
import app.malino.core.TxType

private enum class CardFilter(val label: String) { All("همه"), Out("هزینه‌ها"), In("واریزی‌ها"), Move("انتقال و تعدیل") }

/** Short, clean summary of one card: balance, this month's in and out, and its own list of transactions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardSheet(cu: CardUi, s: UiState, onEdit: () -> Unit, onDismiss: () -> Unit) {
    val c = cu.card
    val mine = remember(s.txs, c.id) { s.txs.filter { it.tx.cardId == c.id } }
    val month = remember { Jalali.today().let { Jalali.monthRange(it.y, it.m) } }
    var filter by remember { mutableStateOf(CardFilter.All) }
    val inMonth = mine.filter { !it.dimmed && it.tx.at in month }
    val monthIn = inMonth.filter { it.tx.type == TxType.IN }.sumOf { it.tx.amount }
    val monthOut = inMonth.filter { it.tx.type == TxType.OUT || it.tx.type == TxType.FEE }.sumOf { it.tx.amount }
    val shown = mine.filter {
        when (filter) {
            CardFilter.All -> true
            CardFilter.Out -> it.tx.type == TxType.OUT || it.tx.type == TxType.FEE
            CardFilter.In -> it.tx.type == TxType.IN
            CardFilter.Move -> it.tx.type == TxType.TRANSFER || it.tx.type == TxType.ADJUST
        }
    }
    val base = Color(c.color)

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), containerColor = MaterialTheme.colorScheme.background,
    ) {
        // fixed height: switching filters must not resize the sheet; only the list below scrolls
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(lerp(base, Color.White, .12f), lerp(base, Color.Black, .42f)))).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(c.bank.ifBlank { c.name }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text(listOf(c.name.takeIf { c.bank.isNotBlank() }, c.purpose.takeIf { it.isNotBlank() && it != c.name }).filterNotNull().joinToString(" · "),
                            color = Color.White.copy(alpha = .88f), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (c.last4.isNotBlank()) Text("⁦•••• ${Jalali.toFa(c.last4)}⁩", color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnimatedMoney(cu.balance, MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold), Color.White)
                    Text("ریال", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 5.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("درآمد این ماه", monthIn, extra.income, Modifier.weight(1f))
                Stat("هزینه‌ی این ماه", monthOut, extra.expense, Modifier.weight(1f))
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CardFilter.entries.toList()) { f ->
                    FilterChip(filter == f, { filter = f }, { Text(f.label) }, shape = RoundedCornerShape(99.dp))
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (shown.isEmpty()) Surface2 { EmptyState("چیزی نیست", "برای این بخش تراکنشی ثبت نشده.") }
            else Surface2 { shown.take(60).forEach { TxRow(it) } }
            if (shown.size > 60) Text("۶۰ مورد آخر نمایش داده شد؛ بقیه در «تراکنش‌ها».", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onEdit, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("ویرایش کارت") }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Long, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedMoney(value, MaterialTheme.typography.titleMedium, color)
    }
}
