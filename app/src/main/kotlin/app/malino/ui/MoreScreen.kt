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
import app.malino.core.Jalali

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreScreen(s: UiState, vm: MainVm, perms: Perms, onOpenCheck: () -> Unit, onPage: (Page) -> Unit, onOpenCard: (Card) -> Unit, onEditCard: (Card?) -> Unit, onTour: () -> Unit = {}, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val importing by vm.importing.collectAsState()
    val imported by vm.importedCount.collectAsState()
    var askRange by remember { mutableStateOf(false) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("تنظیمات", style = MaterialTheme.typography.titleLarge, modifier = Modifier.enter(0)) }

        item { SectionTitle("ظاهر برنامه", Modifier.enter(1)) }
        item {
            val mode by vm.themeMode.collectAsState()
            Surface2(Modifier.enter(1)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("حالت نمایش", fontWeight = FontWeight.Medium)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "هماهنگ با دستگاه", 1 to "روشن", 2 to "تیره").forEach { (m, label) ->
                            FilterChip(mode == m, { vm.setTheme(m) }, { Text(label) }, shape = androidx.compose.foundation.shape.RoundedCornerShape(99.dp))
                        }
                    }
                    TextButton({ onTour() }) { Text("نمایش دوباره‌ی آموزش برنامه") }
                }
            }
        }

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

        item { SectionTitle("امنیت و داده", Modifier.enter(2)) }
        item {
            Surface2(Modifier.enter(2)) {
                CheckRow("امنیت و قفل", "پین، اثر انگشت، قفل خودکار", null) { onPage(Page.Security) }
                CheckRow("پشتیبان و خروجی", "پشتیبان‌گیری، بازیابی، خروجی اکسل، ریست", null) { onPage(Page.Data) }
                CheckRow("وارد کردن صورت‌حساب بانک", "پرینت، عکس یا اکسل بانک با کمک هوش مصنوعی", null) { onPage(Page.Statement) }
            }
        }

        item { SectionTitle("کارت‌ها", Modifier.enter(2)) }
        item {
            Surface2(Modifier.enter(2)) {
                if (s.cards.isEmpty()) EmptyState("کارتی اضافه نشده", "هر کارت یا حساب یک کاربرد دارد؛ مثلاً روزمره یا پس‌انداز.")
                s.cards.forEach { c ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenCard(c.card) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(bankTitle(c.card), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(cardSub(c.card),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("پیامک‌های بانکی گوشی در بازه‌ای که انتخاب می‌کنید خوانده و به صندوق ورودی اضافه می‌شود. اول کارت‌ها را بسازید.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedVisibility(importing >= 0) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton({ askRange = true }, enabled = perms.sms && importing < 0) {
                            Text(if (importing >= 0) "در حال خواندن… ${fa(importing)}" else "شروع")
                        }
                        if (importing < 0 && imported > 0) Text("${fa(imported)} تراکنش اضافه شد", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton({ vm.reprocess() }) { Text("پردازش دوباره‌ی پیام‌های ناشناخته") }
                }
            }
        }
    }

    if (askRange) ImportRangeDialog(onDismiss = { askRange = false }) { from -> askRange = false; vm.importSms(ctx, from) }
}

private val RANGES = listOf("۱ ماه اخیر" to 30, "۳ ماه اخیر" to 90, "۶ ماه اخیر" to 180, "۱ سال اخیر" to 365, "همه" to 0)

/** Asks how far back to read bank SMS before the import starts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportRangeDialog(onDismiss: () -> Unit, onStart: (Long) -> Unit) {
    var sel by remember { mutableIntStateOf(1) }       // index in RANGES, or -1 = custom date
    var custom by remember { mutableStateOf(Jalali.addDays(Jalali.today(), -30)) }
    var pick by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("پیامک‌ها از چه زمانی خوانده شود؟") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RANGES.forEachIndexed { i, (label, _) -> FilterChip(sel == i, { sel = i }, { Text(label) }) }
                    FilterChip(sel == -1, { sel = -1; pick = true }, { Text(if (sel == -1) "از " + Jalali.format(custom) else "تاریخ دلخواه") })
                }
                Text("پیامک‌های قبل از «تاریخ شروع» هر کارت در موجودی آن حساب نمی‌شوند. اگر موجودی اولیه را برای امروز گذاشته‌اید، بازه‌ی کوتاه کافی است.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton({
                val days = if (sel >= 0) RANGES[sel].second else -1
                onStart(when { sel == -1 -> Jalali.toMillis(custom.y, custom.m, custom.d); days == 0 -> 0L; else -> System.currentTimeMillis() - days * 86_400_000L })
            }) { Text("شروع", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } },
    )
    if (pick) JalaliPickerDialog(custom, { pick = false }) { custom = it; pick = false }
}
