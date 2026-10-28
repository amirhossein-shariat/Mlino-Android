package app.malino.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.malino.core.Jalali
import app.malino.core.Tx
import app.malino.core.TxType

@Composable
fun TxScreen(s: UiState, onEdit: (Tx) -> Unit, modifier: Modifier = Modifier) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var q by rememberSaveable { mutableStateOf("") }
    val list = remember(s.txs, filter, q) {
        val digits = Jalali.toEn(q).filter { it.isDigit() }
        s.txs.filter { u ->
            (filter == null || u.tx.cardId == filter) && (q.isBlank() ||
                (u.categoryName?.contains(q.trim()) == true) || u.tx.note.contains(q.trim()) || (u.card?.name?.contains(q.trim()) == true) ||
                (digits.isNotEmpty() && u.tx.amount.toString().contains(digits)))
        }
    }
    val groups = remember(list) { list.groupBy { dayLabel(it.tx.at) } }
    LazyColumn(modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 130.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("تراکنش‌ها", style = MaterialTheme.typography.titleLarge, modifier = Modifier.enter(0)) }
        item {
            OutlinedTextField(
                q, { q = it }, Modifier.fillMaxWidth().enter(1), singleLine = true, shape = RoundedCornerShape(18.dp),
                placeholder = { Text("جست‌وجو در یادداشت، دسته یا مبلغ") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface),
            )
        }
        if (s.cards.size > 1) item {
            LazyRow(Modifier.enter(2), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(filter == null, { filter = null }, { Text("همه") }, shape = RoundedCornerShape(99.dp)) }
                items(s.cards, key = { it.card.id }) { c -> FilterChip(filter == c.card.id, { filter = c.card.id }, { Text(c.card.name) }, shape = RoundedCornerShape(99.dp)) }
            }
        }
        if (list.isEmpty()) item { Surface2(Modifier.enter(3)) { EmptyState(if (q.isBlank()) "تراکنشی نیست" else "چیزی پیدا نشد", if (q.isBlank()) "با دکمه‌ی + یک تراکنش دستی اضافه کنید." else "عبارت دیگری را امتحان کنید.") } }
        groups.forEach { (day, rows) ->
            item(key = "h$day") {
                val sum = rows.sumOf { u -> when (u.tx.type) { TxType.IN -> u.tx.amount; TxType.OUT, TxType.FEE -> -u.tx.amount; else -> 0L } }
                Row(Modifier.animateItem().fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp)) {
                    Text(day, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(signedMoney(kotlin.math.abs(sum), sum >= 0), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "d$day") {
                Surface2(Modifier.animateItem()) {
                    rows.forEachIndexed { i, u -> if (i > 0) TxDivider(); Box(Modifier.clickable { onEdit(u.tx) }) { TxRow(u) } }
                }
            }
        }
    }
}
