package app.malino.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.malino.core.Card

@Composable
fun MoreScreen(s: UiState, vm: MainVm, perms: Perms, onOpenCheck: () -> Unit, onPage: (Page) -> Unit, onEditCard: (Card?) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val importing by vm.importing.collectAsState()
    val imported by vm.importedCount.collectAsState()

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("تنظیمات", style = MaterialTheme.typography.titleLarge, modifier = Modifier.enter(0)) }

        item { SectionTitle("ثبت خودکار", Modifier.enter(1)) }
        item {
            Surface2(Modifier.enter(1)) {
                CheckRow(
                    "بررسی ثبت خودکار پیامک و اعلان",
                    if (perms.ok) "دسترسی‌ها فعال است. برای دیدن شمارنده‌ها و تست پیام وارد شوید." else "دسترسی‌ها کامل نیست. اینجا فعالشان کنید.",
                    ok = if (perms.ok) true else false, action = "تنظیم", onClick = onOpenCheck,
                )
            }
        }

        item { SectionTitle("برنامه‌ریزی", Modifier.enter(2)) }
        item {
            Surface2(Modifier.enter(2)) {
                CheckRow("بودجه و هدف", "سقف ماهانه‌ی دسته‌ها، هدف پس‌انداز، هزینه‌های تکراری", null) { onPage(Page.Budgets) }
                CheckRow("طلب و بدهی", "اقساط و سررسیدها با یادآوری", null) { onPage(Page.Debts) }
                CheckRow("دارایی‌ها", "طلا، دلار، سکه و ثروت کل", null) { onPage(Page.Assets) }
            }
        }

        item { SectionTitle("کارت‌ها", Modifier.enter(2)) }
        item {
            Surface2(Modifier.enter(2)) {
                if (s.cards.isEmpty()) EmptyState("کارتی اضافه نشده", "هر کارت یا حساب یک کاربرد دارد؛ مثلاً روزمره یا پس‌انداز.")
                s.cards.forEach { c ->
                    Row(Modifier.fillMaxWidth().clickable { onEditCard(c.card) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.card.name, fontWeight = FontWeight.Medium)
                            Text(listOf(c.card.bank, c.card.purpose).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton({ onEditCard(null) }, Modifier.padding(horizontal = 6.dp)) { Text("＋ کارت جدید") }
            }
        }

        item { SectionTitle("ابزارها", Modifier.enter(3)) }
        item {
            Surface2(Modifier.enter(3)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("وارد کردن پیامک‌های قبلی بانک", fontWeight = FontWeight.Medium)
                    Text("همه‌ی پیامک‌های بانکی گوشی خوانده و به صندوق ورودی اضافه می‌شود. اول کارت‌ها را بسازید.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedVisibility(importing >= 0) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton({ vm.importSms(ctx) }, enabled = perms.sms && importing < 0) {
                            Text(if (importing >= 0) "در حال خواندن… ${fa(importing)}" else "شروع")
                        }
                        if (importing < 0 && imported > 0) Text("${fa(imported)} تراکنش اضافه شد", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton({ vm.reprocess() }) { Text("پردازش دوباره‌ی پیام‌های ناشناخته") }
                }
            }
        }
    }
}
