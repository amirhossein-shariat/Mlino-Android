package app.malino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.malino.core.TxType

fun typeLabel(t: TxType) = when (t) {
    TxType.OUT -> "برداشت"; TxType.IN -> "واریز"; TxType.FEE -> "کارمزد"; TxType.ADJUST -> "تعدیل"; TxType.TRANSFER -> "انتقال بین کارت‌ها"
}

fun catIcon(id: String?): ImageVector? = when (id) {
    "food" -> Icons.Rounded.Restaurant
    "transport" -> Icons.Rounded.DirectionsCar
    "shopping" -> Icons.Rounded.ShoppingBag
    "bills" -> Icons.Rounded.Receipt
    "health" -> Icons.Rounded.LocalHospital
    "family" -> Icons.Rounded.Favorite
    "biz_cost" -> Icons.Rounded.BusinessCenter
    "fee" -> Icons.Rounded.AccountBalance
    "invoice" -> Icons.Rounded.Description
    "salary" -> Icons.Rounded.Payments
    "roundup" -> Icons.Rounded.Savings
    "other_out", "other_in" -> Icons.Rounded.MoreHoriz
    else -> null
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(horizontal = 4.dp))

@Composable
fun Surface2(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(
        modifier.clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(26.dp)),
        content = content,
    )

@Composable
fun IconBadge(icon: ImageVector, bg: Color, fg: Color, size: Int = 42) =
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(15.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size((size * 0.5f).dp))
    }

@Composable
fun TxRow(u: TxUi, modifier: Modifier = Modifier) {
    val t = u.tx
    val ex = extra
    val (icon, bg, fg) = when (t.type) {
        TxType.IN -> Triple(catIcon(t.categoryId) ?: Icons.Rounded.ArrowDownward, ex.incomeBg, ex.income)
        TxType.OUT, TxType.FEE -> Triple(catIcon(t.categoryId) ?: Icons.Rounded.ArrowUpward, ex.expenseBg, ex.expense)
        TxType.TRANSFER -> Triple(Icons.Rounded.SwapHoriz, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.secondary)
        TxType.ADJUST -> Triple(catIcon(t.categoryId) ?: if (t.amount >= 0) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
            if (t.categoryId == "roundup") extra.incomeBg else MaterialTheme.colorScheme.surfaceVariant,
            if (t.categoryId == "roundup") extra.income else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val plus = when (t.type) { TxType.IN -> true; TxType.TRANSFER -> t.dirIn; TxType.ADJUST -> t.amount >= 0; else -> false }
    val amountColor = when (t.type) {
        TxType.IN -> ex.income; TxType.OUT, TxType.FEE -> ex.expense; TxType.ADJUST -> if (plus) ex.income else ex.expense
        TxType.TRANSFER -> MaterialTheme.colorScheme.onSurface
    }
    Row(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(icon, bg, fg)
        Column(Modifier.weight(1f)) {
            Text(u.categoryName ?: t.note.ifBlank { typeLabel(t.type) }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${u.card?.name.orEmpty()} · ${dayLabel(t.at)} ${clock(t.at)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (u.dimmed) "—" else if (t.type == TxType.TRANSFER && !t.dirIn) "−" + money(t.amount) else signedMoney(kotlin.math.abs(t.amount), plus),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = amountColor,
        )
    }
}

@Composable
fun StatusPill(text: String, bg: Color, fg: Color) =
    Text(text, color = fg, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 8.dp, vertical = 2.dp))

@Composable
fun EmptyState(title: String, hint: String, modifier: Modifier = Modifier) =
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
