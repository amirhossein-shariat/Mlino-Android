package app.malino.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.malino.capture.CaptureSnapshot
import kotlinx.coroutines.delay

/** One permission / requirement line with a live check mark. [ok] == null renders a plain navigation row. */
@Composable
fun CheckRow(title: String, hint: String, ok: Boolean?, action: String? = null, onClick: () -> Unit = {}) {
    val ex = extra
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (ok) {
            true -> Icon(Icons.Rounded.CheckCircle, null, tint = ex.income)
            false -> Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = ex.warn)
            null -> Icon(Icons.Rounded.ChevronLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null && ok != true) FilledTonalButton(onClick, contentPadding = PaddingValues(horizontal = 14.dp)) { Text(action) }
    }
}

/** Requests / opens settings for every capture requirement. Shared by the onboarding and the check screen. */
@Composable
fun PermissionChecklist(perms: Perms, listenerAlive: Boolean?, hasKeyedCard: Boolean?, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onChanged() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onChanged() }
    Surface2 {
        CheckRow("خواندن پیامک بانک", "پیامک بانک‌ها خودکار ثبت می‌شود؛ پیامک شخصی هرگز ذخیره نمی‌شود.", perms.sms, "اجازه") {
            ask.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
        }
        CheckRow("خواندن اعلان برنامه‌های بانکی", "برای بانک‌هایی که فقط اعلان می‌فرستند (مثل بلو).", perms.notifAccess, "باز کردن") {
            openNotificationAccess(ctx)
        }
        if (listenerAlive != null) CheckRow("سرویس خواندن اعلان روشن است", "اگر خاموش بود، دسترسی اعلان را یک‌بار خاموش و روشن کنید.", listenerAlive && perms.notifAccess)
        CheckRow("اعلان تراکنش جدید", "وقتی تراکنشی ثبت شد خبرتان می‌کند.", perms.postNotif, "اجازه") {
            if (Build.VERSION.SDK_INT >= 33) ask.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
        CheckRow("بدون محدودیت باتری", "تا سیستم برنامه را در پس‌زمینه نبندد.", perms.battery, "تنظیم") { openBatterySettings(ctx) }
        if (hasKeyedCard != null) CheckRow("کارتی با شناسه‌ی پیام دارید", "بدون شناسه، پیام بانک به هیچ کارتی نمی‌رسد.", hasKeyedCard)
    }
}

/** Why the system blocks the permission dialogs of an APK installed outside a store, and how to unblock them. */
@Composable
fun RestrictedHelp() {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Surface2 {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("دکمه‌ی اجازه کار نمی‌کند یا خاکستری است؟", Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(if (open) "بستن" else "راهنما", color = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(14.dp, 0.dp, 14.dp, 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("اندروید ۱۳ به بالا، دسترسی پیامک و اعلان را برای برنامه‌ای که خارج از فروشگاه نصب شده قفل می‌کند. یک‌بار این‌ها را انجام دهید:",
                    style = MaterialTheme.typography.bodySmall)
                listOf("«اطلاعات برنامه» را باز کنید (دکمه‌ی پایین).", "بالا سمت راست یا چپ روی سه‌نقطه ⋮ بزنید.",
                    "«Allow restricted settings» (مجاز کردن تنظیمات محدود) را انتخاب کنید.", "برگردید و دوباره روی «اجازه» بزنید.")
                    .forEachIndexed { i, t -> Text("${fa(i + 1)}. $t", style = MaterialTheme.typography.bodySmall) }
                Text("اگر سه‌نقطه نبود، اول یک‌بار روی «اجازه» بزنید و بعد دوباره به اطلاعات برنامه بروید.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton({ openAppInfo(ctx) }) { Text("باز کردن اطلاعات برنامه") }
            }
        }
    }
}

private fun ago(ms: Long, now: Long): String {
    if (ms <= 0) return "هنوز نبوده"
    val s = (now - ms) / 1000
    return when {
        s < 60 -> "چند ثانیه پیش"
        s < 3600 -> "${fa((s / 60).toInt())} دقیقه پیش"
        s < 86400 -> "${fa((s / 3600).toInt())} ساعت پیش"
        else -> "${dayLabel(ms)} ${clock(ms)}"
    }
}

private fun stateLabel(s: String) = when (s) {
    "done" -> "ثبت شد"; "duplicate" -> "تکراری"; "unparsed" -> "قالب ناشناخته"; "unmapped" -> "کارت نامشخص"; "dismissed" -> "بسته شد"; else -> s
}

/** Full check screen: live status of everything auto-capture needs, counters, the last captured messages, and a dry-run parser. */
@Composable
fun CaptureCheckScreen(s: UiState, vm: MainVm, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var perms by remember { mutableStateOf(readPerms(ctx)) }
    var snap by remember { mutableStateOf<CaptureSnapshot>(vm.stats.snapshot()) }
    var sources by remember { mutableStateOf(vm.stats.sources()) }
    var bankApps by remember { mutableStateOf(vm.bankApps()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { snap = vm.stats.snapshot(); sources = vm.stats.sources(); now = System.currentTimeMillis(); delay(1500) } }
    val raw by vm.recentRaw.collectAsState()
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<MainVm.ParseCheck?>(null) }
    val ex = extra
    val hasKey = s.cards.any { it.card.smsKey.isNotBlank() }
    val allOk = perms.ok && hasKey && snap.listenerConnected

    val taught by vm.templates.collectAsState()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("بررسی ثبت خودکار", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClose) { Text("بستن") }
            }
        }
        item {
            val bg by animateColorAsState(if (allOk) ex.incomeBg else ex.warnBg, label = "hero")
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(bg).padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (allOk) "ثبت خودکار آماده است" else "هنوز چند مورد مانده", fontWeight = FontWeight.Bold, color = if (allOk) ex.income else ex.warn)
                Text(if (allOk) "حالا از یک کارت واقعی خرید کنید یا از ماشین‌حساب بانک پیامک تست بگیرید و پایین‌تر ببینید ثبت شد."
                else "موارد قرمز را فعال کنید. بعد از هر تغییر این صفحه خودکار به‌روز می‌شود.", style = MaterialTheme.typography.bodySmall,
                    color = if (allOk) ex.income else ex.warn)
            }
        }
        item { PermissionChecklist(perms, snap.listenerConnected, hasKey) { perms = readPerms(ctx) } }
        item { RestrictedHelp() }

        item { SectionTitle("شمارنده‌های زنده") }
        item {
            Surface2 {
                CheckRow("پیامک‌های دیده‌شده", "${fa(snap.smsSeen)} پیامک · آخرین: ${ago(snap.smsLast, now)}", null)
                CheckRow("اعلان‌های دیده‌شده", "${fa(snap.notifSeen)} اعلان · آخرین: ${ago(snap.notifLast, now)}", null)
                CheckRow("آخرین پیام بانکی شناخته‌شده", if (snap.bankLast > 0) "${ago(snap.bankLast, now)} (${if (snap.bankLastKind == "sms") "پیامک" else "اعلان"})" else "هنوز نبوده", null)
            }
        }
        item { Text("«شمارنده‌ی اعلان» با هر اعلان هر برنامه بالا می‌رود (فقط عدد شمرده می‌شود، متن ذخیره نمی‌شود). اگر بعد از اعلان بلو بالا نرفت، دسترسی اعلان فعال نیست.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)) }

        item { SectionTitle("برنامه‌های بانکی من") }
        item {
            Surface2 {
                Text("برنامه‌ی بانکتان را اینجا روشن کنید تا همه‌ی اعلان‌های آن (حتی با قالب ناشناخته) برای بررسی در صندوق بیاید. برنامه‌ها بعد از اولین اعلانشان در این فهرست پیدا می‌شوند.",
                    Modifier.padding(14.dp, 14.dp, 14.dp, 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val shown = sources.filter { it.pkg != ctx.packageName }.take(12)
                if (shown.isEmpty()) Text("هنوز اعلانی از برنامه‌ای دیده نشده؛ دسترسی اعلان باید روشن باشد.", Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
                shown.forEach { src ->
                    Row(Modifier.fillMaxWidth().padding(14.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(src.label, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text("${fa(src.count)} اعلان · ${src.pkg}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Switch(src.pkg in bankApps, { on -> vm.setBankApp(src.pkg, on); bankApps = vm.bankApps() })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        item { SectionTitle("تست یک پیام (ذخیره نمی‌شود)") }
        item {
            Surface2 {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(text, { text = it; result = null }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("متن پیامک یا اعلان بانک را بچسبانید") })
                    Button({ result = vm.checkText(text) }, enabled = text.isNotBlank()) { Text("بررسی") }
                    AnimatedVisibility(result != null) {
                        val r = result
                        if (r != null) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            when {
                                r.ignored != null -> Text(if (r.ignored == "otp") "رمز یکبار مصرف است؛ هرگز ذخیره نمی‌شود." else "پیام تبلیغاتی است و نادیده گرفته می‌شود.")
                                r.parsed == null -> Text("قالب شناخته نشد. این پیام در صندوق ورودی به‌صورت «قالب ناشناخته» می‌آید؛ متنش را برای پشتیبانی بفرستید.", color = ex.warn)
                                else -> {
                                    val p = r.parsed
                                    Text("بانک: ${p.bank}", fontWeight = FontWeight.Medium)
                                    Text((if (p.out) "برداشت " else "واریز ") + money(p.amount) + " ریال")
                                    Text("مانده: " + money(p.balance) + " ریال")
                                    Text("زمان: ${fullDate(p.at)} ${clock(p.at)}")
                                    Text("شناسه‌ی پیام: ${p.key}")
                                    if (r.cardName != null) Text("کارت: ${r.cardName} ✔", color = ex.income)
                                    else Text("هیچ کارتی با این شناسه نیست؛ در کارت‌ها، «شناسه‌ی پیام» را ${p.key} بگذارید.", color = ex.warn)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (taught.isNotEmpty()) {
            item { SectionTitle("قالب‌هایی که یاد داده‌اید") }
            item {
                Surface2 {
                    taught.forEach { t ->
                        Row(Modifier.fillMaxWidth().padding(14.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text((if (t.out) "برداشت" else "واریز") + " · " + t.sender.ifBlank { "هر فرستنده" }, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(s.cards.firstOrNull { it.card.id == t.cardId }?.card?.name ?: "کارت حذف شده", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton({ vm.deleteTemplate(t.id) }) { Text("حذف") }
                        }
                    }
                }
            }
        }

        item { SectionTitle("آخرین پیام‌های بانکی دریافت‌شده") }
        if (raw.isEmpty()) item { Surface2 { EmptyState("هنوز پیامی ثبت نشده", "بعد از اولین پیامک یا اعلان بانکی اینجا می‌آید.") } }
        items(raw, key = { it.id }) { r ->
            Surface2(Modifier.animateItem()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(if (r.kind == "sms") "پیامک" else "اعلان", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        StatusPill(stateLabel(r.state), if (r.state == "done") ex.incomeBg else ex.warnBg, if (r.state == "done") ex.income else ex.warn)
                        Spacer(Modifier.weight(1f))
                        Text("${dayLabel(r.receivedAt)} ${clock(r.receivedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("فرستنده: " + r.sender.ifBlank { "—" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(r.body, maxLines = 3, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({ vm.shareSample(ctx, r) }, contentPadding = PaddingValues(0.dp)) { Text("ارسال به‌عنوان نمونه (بدون شماره)") }
                }
            }
        }
    }
}
