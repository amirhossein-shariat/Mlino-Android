package app.mlino.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.mlino.core.Tx

@Composable
fun TxScreen(s: UiState, onEdit: (Tx) -> Unit, modifier: Modifier = Modifier) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val list = remember(s.txs, filter) { s.txs.filter { filter == null || it.tx.cardId == filter } }
    val groups = remember(list) { list.groupBy { dayLabel(it.tx.at) } }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("تراکنش‌ها", style = MaterialTheme.typography.titleLarge, modifier = Modifier.enter(0)) }
        if (s.cards.size > 1) item {
            LazyRow(Modifier.enter(1), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(filter == null, { filter = null }, { Text("همه") }) }
                items(s.cards, key = { it.card.id }) { c -> FilterChip(filter == c.card.id, { filter = c.card.id }, { Text(c.card.name) }) }
            }
        }
        if (list.isEmpty()) item { Surface2(Modifier.enter(2)) { EmptyState("تراکنشی نیست", "با دکمه‌ی + یک تراکنش دستی اضافه کنید.") } }
        groups.forEach { (day, rows) ->
            item(key = "h$day") {
                Text(day, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.animateItem().padding(start = 6.dp, top = 6.dp))
            }
            items(rows, key = { it.tx.id }) { u ->
                Surface2(Modifier.animateItem().clickable { onEdit(u.tx) }) { TxRow(u) }
            }
        }
    }
}
