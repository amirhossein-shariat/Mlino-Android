package app.malino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.CarRepair
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Replay
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
    "rent" -> Icons.Rounded.Home
    "telecom" -> Icons.Rounded.Smartphone
    "edu" -> Icons.Rounded.School
    "fun" -> Icons.Rounded.Movie
    "gift", "gift_in" -> Icons.Rounded.CardGiftcard
    "invest" -> Icons.Rounded.TrendingUp
    "interest" -> Icons.Rounded.Percent
    "refund" -> Icons.Rounded.Replay
    "grocery" -> Icons.Rounded.ShoppingCart
    "installment" -> Icons.Rounded.EventRepeat
    "insurance" -> Icons.Rounded.VerifiedUser
    "car" -> Icons.Rounded.CarRepair
    "clothes" -> Icons.Rounded.Checkroom
    "beauty" -> Icons.Rounded.Spa
    "subscription" -> Icons.Rounded.Subscriptions
    "travel" -> Icons.Rounded.Flight
    "tax" -> Icons.Rounded.RequestQuote
    "loan_out" -> Icons.Rounded.Payments
    "freelance" -> Icons.Rounded.Work
    "sales" -> Icons.Rounded.Storefront
    "rent_in" -> Icons.Rounded.HomeWork
    "bonus" -> Icons.Rounded.EmojiEvents
    "profit" -> Icons.Rounded.ShowChart
    "loan_in", "debt_back" -> Icons.Rounded.Paid
    "subsidy" -> Icons.Rounded.VolunteerActivism
    "sell_asset" -> Icons.Rounded.Sell
    "cashback" -> Icons.Rounded.Percent
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
            if (t.categoryId == "roundup") extra.accent.copy(alpha = .20f) else MaterialTheme.colorScheme.surfaceVariant,
            if (t.categoryId == "roundup") extra.accent else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val plus = when (t.type) { TxType.IN -> true; TxType.TRANSFER -> t.dirIn; TxType.ADJUST -> t.amount >= 0; else -> false }
    val amountColor = when (t.type) {
        TxType.IN -> ex.income; TxType.OUT, TxType.FEE -> ex.expense; TxType.ADJUST -> if (t.categoryId == "roundup") ex.accent else if (plus) ex.income else ex.expense
        TxType.TRANSFER -> MaterialTheme.colorScheme.onSurface
    }
    // faded = before the card start (not counted) or inside a period whose bank balance has not been settled yet
    Row(modifier.fillMaxWidth().alpha(if (u.dimmed || u.unsettled) .5f else 1f).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(icon, bg, fg)
        Column(Modifier.weight(1f)) {
            Text(u.categoryName ?: t.note.ifBlank { typeLabel(t.type) }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${cardLabel(u.card)} · ${dayLabel(t.at)} ${clock(t.at)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (u.unsettled && !u.dimmed) Text("مانده‌ی بانک در این بازه هماهنگ نیست؛ در صندوق ورودی تعیین تکلیف کنید", style = MaterialTheme.typography.labelSmall,
                color = extra.warn, maxLines = 2)
            if (u.dimmed) Text("قبل از تاریخ شروع کارت؛ در مانده و گزارش حساب نمی‌شود", style = MaterialTheme.typography.labelSmall,
                color = extra.warn, maxLines = 2)
            // user note under the line when a category already took the title
            if (u.categoryName != null && t.type != TxType.ADJUST && t.categoryId != "roundup" && t.note.isNotBlank() && t.note !in AUTO_NOTES) Text("📝 " + t.note, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .75f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            signedMoney(kotlin.math.abs(t.amount), plus),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
            color = amountColor,
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

/** «Bank (name)» as entered when the card was added. */
fun cardLabel(c: app.malino.core.Card?): String = when {
    c == null -> ""
    c.bank.isBlank() -> c.name
    else -> (if (c.bank.trim().startsWith("بانک")) c.bank.trim() else "بانک ${c.bank.trim()}") + " (${c.name})"
}

/** Light card colours get a light gradient and dark text; the rest keep white text. */
fun cardInk(base: Color): Color = if (base.luminance() > .5f) Color(0xFF14202B) else Color.White

fun cardBrush(base: Color): Brush =
    if (base.luminance() > .5f) Brush.linearGradient(listOf(Color.White, lerp(base, Color.Black, .14f)))
    else Brush.linearGradient(listOf(lerp(base, Color.White, .12f), lerp(base, Color.Black, .42f)))

/** Card face title: «بانک X»; the card's own name goes in parentheses below it. */
fun bankTitle(c: app.malino.core.Card): String =
    if (c.bank.isBlank()) c.name else (if (c.bank.trim().startsWith("بانک")) c.bank.trim() else "بانک ${c.bank.trim()}")

fun cardSub(c: app.malino.core.Card): String =
    listOfNotNull(if (c.bank.isNotBlank()) "(${c.name})" else null, c.purpose.takeIf { it.isNotBlank() && it != c.name }).joinToString(" · ")

/** Notes the app writes itself; they are not repeated under the row. */
private val AUTO_NOTES = setOf("کارمزد", "کارمزد انتقال", "تعدیل مانده", "رُند (پس‌انداز)")

@Composable
fun TxDivider() = HorizontalDivider(Modifier.padding(horizontal = 14.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .16f))
