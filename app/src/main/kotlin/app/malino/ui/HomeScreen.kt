package app.malino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(s: UiState, needsSetup: Boolean, goInbox: () -> Unit, goMore: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(Modifier.enter(0)) {
                Text("خوش آمدید", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("مالینو", style = MaterialTheme.typography.titleLarge)
            }
        }
        item { Hero(s, Modifier.enter(1)) }
        if (needsSetup) item {
            AlertCard("برای ثبت خودکار تراکنش‌ها، دسترسی پیامک و اعلان را فعال کنید", "فعال‌سازی", goMore, Modifier.enter(2))
        }
        if (s.gaps.isNotEmpty()) item {
            AlertCard("${fa(s.gaps.size)} تراکنش جاافتاده پیدا شد", "بررسی", goInbox, Modifier.enter(2))
        }
        item {
            if (s.cards.isEmpty()) Surface2(Modifier.enter(2)) { EmptyState("هنوز کارتی ندارید", "از بخش «بیشتر» اولین کارت را اضافه کنید.") }
            else LazyRow(Modifier.enter(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(s.cards, key = { it.card.id }) { c ->
                    Column(Modifier.width(158.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(c.card.color)))
                            Text(c.card.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        Text(c.card.purpose.ifBlank { c.card.bank }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        AnimatedMoney(c.balance, MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        item { SectionTitle("آخرین تراکنش‌ها", Modifier.enter(3)) }
        if (s.txs.isEmpty()) item { Surface2(Modifier.enter(3)) { EmptyState("تراکنشی ثبت نشده", "پیامک بانک که بیاید خودکار اینجا می‌نشیند.") } }
        else item {
            Surface2(Modifier.enter(3)) { s.txs.take(6).forEach { TxRow(it) } }
        }
    }
}

@Composable
private fun Hero(s: UiState, modifier: Modifier) {
    val ex = extra
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(ex.heroA, ex.heroB))).padding(22.dp),
    ) {
        Text("مجموع موجودی کارت‌ها", color = ex.heroFg.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AnimatedMoney(s.total, MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold), ex.heroFg)
            Text("ریال", color = ex.heroFg.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroPill("درآمد این ماه", s.monthIn, Modifier.weight(1f))
            HeroPill("هزینه‌ی این ماه", s.monthOut, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroPill(label: String, value: Long, modifier: Modifier) {
    Column(modifier.clip(MaterialTheme.shapes.small).background(Color.White.copy(alpha = .14f)).padding(12.dp, 8.dp)) {
        Text(label, color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelSmall)
        AnimatedMoney(value, MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), Color.White)
    }
}

@Composable
fun AlertCard(text: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ex = extra
    Row(modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(ex.warnBg).padding(14.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.WarningAmber, null, tint = ex.warn)
        Text(text, Modifier.weight(1f), color = ex.warn, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick, colors = ButtonDefaults.textButtonColors(contentColor = ex.warn)) { Text(action, fontWeight = FontWeight.Bold) }
    }
}
