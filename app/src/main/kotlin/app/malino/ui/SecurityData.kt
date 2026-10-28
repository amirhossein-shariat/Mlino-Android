package app.malino.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.malino.core.Jalali
import app.malino.core.Pin
import app.malino.data.Backup
import app.malino.security.Biometric
import java.io.File

// ================= Security =================

@Composable
fun PinDialog(title: String, askRepeat: Boolean, onDismiss: () -> Unit, exactLen: Int? = null, onPin: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val err = when {
        exactLen != null && a.isNotEmpty() && a.length != exactLen -> "پین باید ${fa(exactLen)} رقم باشد"
        exactLen == null && a.isNotEmpty() && !Pin.isValid(a) -> "پین باید ${fa(Pin.MIN_LEN)} تا ${fa(Pin.MAX_LEN)} رقم باشد"
        askRepeat && b.isNotEmpty() && a != b -> "دو پین یکسان نیست"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(a, { a = digitsOnly(it).take(exactLen ?: Pin.MAX_LEN) }, singleLine = true, label = { Text("پین") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (askRepeat) OutlinedTextField(b, { b = digitsOnly(it).take(exactLen ?: Pin.MAX_LEN) }, singleLine = true, label = { Text("تکرار پین") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (err != null) Text(err, color = extra.expense, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = Pin.isValid(a) && (exactLen == null || a.length == exactLen) && (!askRepeat || a == b), onClick = { onPin(a) }) { Text("تأیید", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } },
    )
}

private enum class PinStep { None, SetNew, VerifyForChange, VerifyForDisable }

@Composable
fun SecurityPage(vm: MainVm, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val store = vm.security
    var tick by remember { mutableIntStateOf(0) }
    var step by remember { mutableStateOf(PinStep.None) }
    var wrongOld by remember { mutableStateOf(false) }
    @Suppress("UNUSED_EXPRESSION") tick
    val enabled = store.enabled

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("امنیت و قفل", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); TextButton(onClose) { Text("بستن") } } }
        item {
            Surface2 {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("قفل برنامه با پین", fontWeight = FontWeight.Medium)
                        Text("هر بار که برنامه باز شود پین می‌خواهد", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(enabled, { on -> wrongOld = false; step = if (on) PinStep.SetNew else PinStep.VerifyForDisable })
                }
                if (enabled) {
                    TextButton({ wrongOld = false; step = PinStep.VerifyForChange }, Modifier.padding(horizontal = 6.dp)) { Text("تغییر پین") }
                }
            }
        }
        if (enabled) {
            item {
                Surface2 {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("قفل خودکار", fontWeight = FontWeight.Medium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf(0 to "فوری", 60 to "۱ دقیقه", 300 to "۵ دقیقه", 900 to "۱۵ دقیقه")) { (sec, label) ->
                                FilterChip(store.autoLockSec == sec, { store.autoLockSec = sec; tick++ }, { Text(label) }, shape = RoundedCornerShape(99.dp))
                            }
                        }
                        Text("بعد از این مدت که برنامه در پس‌زمینه بماند، دوباره قفل می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (Biometric.available(ctx)) item {
                Surface2 {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("اثر انگشت یا چهره", fontWeight = FontWeight.Medium)
                            Text("به‌جای وارد کردن پین", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(store.biometric, { store.biometric = it; tick++ })
                    }
                }
            }
        }
        item {
            Text("این قفل فقط صفحه‌ی برنامه را می‌بندد و فایل‌های داخل گوشی را رمزگذاری نمی‌کند. وقتی قفل روشن است، محتوای برنامه در لیست برنامه‌های اخیر دیده نمی‌شود و نمی‌شود از آن عکس صفحه گرفت.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }

    when (step) {
        PinStep.None -> {}
        PinStep.SetNew -> PinDialog("پین جدید", true, { step = PinStep.None }) { pin -> store.setPin(pin); vm.refreshLock(); tick++; step = PinStep.None }
        PinStep.VerifyForChange -> PinDialog(if (wrongOld) "پین فعلی اشتباه بود؛ دوباره" else "پین فعلی", false, { step = PinStep.None }) { pin ->
            if (store.check(pin)) { wrongOld = false; step = PinStep.SetNew } else wrongOld = true
        }
        PinStep.VerifyForDisable -> PinDialog(if (wrongOld) "پین اشتباه بود؛ دوباره" else "برای خاموش کردن، پین را وارد کنید", false, { step = PinStep.None }) { pin ->
            if (store.check(pin)) { store.disable(); vm.refreshLock(); tick++; step = PinStep.None } else wrongOld = true
        }
    }
}

// ================= Backup & export =================

private fun rangeFor(kind: Int): Pair<Long, Long> {
    val t = Jalali.today()
    return when (kind) {
        1 -> Jalali.monthRange(t.y, t.m).let { it.first to it.last + 1 }
        2 -> { val i = t.y * 12 + t.m - 2; Jalali.monthRange(i / 12, i % 12 + 1).let { it.first to it.last + 1 } }
        3 -> Jalali.toMillis(t.y, 1, 1) to Jalali.toMillis(t.y + 1, 1, 1)
        else -> 0L to Long.MAX_VALUE
    }
}

@Composable
fun DataPage(s: UiState, vm: MainVm, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val msg by vm.dataMsg.collectAsState()
    val staged by vm.restoreStaged.collectAsState()
    var range by remember { mutableIntStateOf(0) }
    var confirmRestore by remember { mutableStateOf<(() -> Unit)?>(null) }
    var resetAsk by remember { mutableStateOf<ResetAsk?>(null) }
    val auto = remember(msg) { Backup.listAuto(ctx) }
    val d = Jalali.today()

    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) vm.backupTo(ctx, uri) }
    val saveXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null) { val (a, b) = rangeFor(range); vm.exportXlsx(ctx, uri, a, b) }
    }
    val openZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = { vm.restoreFrom(ctx) { ctx.contentResolver.openInputStream(uri) } }
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("پشتیبان و خروجی", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); TextButton(onClose) { Text("بستن") } } }

        item { SectionTitle("پشتیبان‌گیری") }
        item {
            Surface2 {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("همه‌ی کارت‌ها، تراکنش‌ها، بودجه، اقساط و دارایی‌ها در یک فایل zip ذخیره می‌شود. آن را جایی امن (مثلاً فضای ابری یا کامپیوتر) نگه دارید؛ با پاک شدن برنامه، نسخه‌های داخل برنامه هم از بین می‌روند.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button({ saveZip.launch("malino-backup-${d.y}${"%02d".format(d.m)}${"%02d".format(d.d)}.zip") }, colors = ButtonDefaults.buttonColors(containerColor = extra.accent, contentColor = extra.accentInk)) { Text("ذخیره‌ی پشتیبان در فایل‌ها", fontWeight = FontWeight.Bold) }
                    OutlinedButton({ vm.shareBackup(ctx) }) { Text("ارسال پشتیبان (تلگرام، ایمیل و …)") }
                    OutlinedButton({ openZip.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) { Text("بازیابی از فایل پشتیبان") }
                }
            }
        }
        if (auto.isNotEmpty()) {
            item { SectionTitle("پشتیبان‌های خودکار روزانه (۷ تای آخر)") }
            item {
                Surface2 {
                    auto.forEach { f ->
                        Row(Modifier.fillMaxWidth().padding(14.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(autoLabel(f), fontWeight = FontWeight.Medium)
                                Text("${fa((f.length() / 1024).toInt())} کیلوبایت", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton({ confirmRestore = { vm.restoreFrom(ctx) { f.inputStream() } } }) { Text("بازیابی") }
                        }
                    }
                }
            }
        }

        item { SectionTitle("خروجی اکسل") }
        item {
            Surface2 {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("همه", "این ماه", "ماه قبل", "امسال")) { label ->
                            val i = listOf("همه", "این ماه", "ماه قبل", "امسال").indexOf(label)
                            FilterChip(range == i, { range = i }, { Text(label) }, shape = RoundedCornerShape(99.dp))
                        }
                    }
                    Button({ saveXlsx.launch("malino-transactions-${d.y}${"%02d".format(d.m)}${"%02d".format(d.d)}.xlsx") }) { Text("ذخیره‌ی فایل اکسل در فایل‌ها") }
                    OutlinedButton({ val (a, b) = rangeFor(range); vm.shareXlsx(ctx, a, b) }) { Text("ارسال فایل اکسل") }
                    Text("دو برگه: تراکنش‌ها (با تاریخ شمسی، کارت، دسته، مانده) و خلاصه‌ی دسته‌ها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { SectionTitle("پاک‌سازی و ریست") }
        item {
            Surface2 {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("قبل از هر پاک‌سازی، برنامه خودش یک نسخه‌ی ایمنی می‌گیرد؛ اگر پشیمان شدید از «پشتیبان‌های خودکار» بازیابی کنید.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton({ resetAsk = ResetAsk.Range }, Modifier.fillMaxWidth()) { Text("حذف تراکنش‌های یک بازه‌ی زمانی") }
                    OutlinedButton({ resetAsk = ResetAsk.AllTx }, Modifier.fillMaxWidth()) { Text("حذف همه‌ی تراکنش‌ها (کارت‌ها می‌مانند)") }
                    OutlinedButton({ resetAsk = ResetAsk.Learned }, Modifier.fillMaxWidth()) { Text("فراموش کردن چیزهایی که برنامه یاد گرفته") }
                    OutlinedButton({ resetAsk = ResetAsk.Log }, Modifier.fillMaxWidth()) { Text("پاک کردن گزارش پیام‌های خام ثبت خودکار") }
                    OutlinedButton({ resetAsk = ResetAsk.Plan }, Modifier.fillMaxWidth()) { Text("حذف بودجه، اقساط، اهداف و دارایی‌ها") }
                    Button({ resetAsk = ResetAsk.Everything }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = extra.expense, contentColor = androidx.compose.ui.graphics.Color.White)) { Text("ریست کامل برنامه", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    resetAsk?.let { ask -> ResetDialog(ask, s, onDismiss = { resetAsk = null }) { kind, from, to -> resetAsk = null; vm.reset(ctx, kind, from, to) } }
    confirmRestore?.let { go ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null }, title = { Text("بازیابی پشتیبان؟") },
            text = { Text("اطلاعات فعلی برنامه بعد از اعمال بازیابی با اطلاعات فایل جایگزین می‌شود. قبلش یک پشتیبان از وضعیت فعلی بگیرید.") },
            confirmButton = { TextButton({ confirmRestore = null; go() }) { Text("بازیابی", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ confirmRestore = null }) { Text("انصراف") } },
        )
    }
    if (!staged) msg?.let { m ->
        AlertDialog(onDismissRequest = { vm.dataMsg.value = null }, text = { Text(m) }, confirmButton = { TextButton({ vm.dataMsg.value = null }) { Text("باشه", fontWeight = FontWeight.Bold) } })
    }
    if (staged) {
        AlertDialog(
            onDismissRequest = {}, title = { Text("آماده‌ی اعمال") },
            text = { Text("برای اعمال بازیابی، برنامه بسته می‌شود. بعد از آن دوباره بازش کنید.") },
            confirmButton = { TextButton({ android.os.Process.killProcess(android.os.Process.myPid()) }) { Text("بستن برنامه", fontWeight = FontWeight.Bold) } },
        )
    }
}

private fun autoLabel(f: File): String {
    val m = Regex("(\\d{4})(\\d{2})(\\d{2})").find(f.name)?.destructured ?: return f.name
    val (y, mo, da) = m
    val j = Jalali.toJalali(y.toInt(), mo.toInt(), da.toInt())
    return if (f.name.contains("safety")) "نسخه‌ی ایمنی قبل از پاک‌سازی ${Jalali.format(j)}" else "پشتیبان ${Jalali.format(j)}"
}

private enum class ResetAsk(val title: String, val body: String, val kind: app.malino.data.Repo.Reset) {
    Range("حذف تراکنش‌های یک بازه", "همه‌ی تراکنش‌های بین دو تاریخ حذف می‌شوند. کارت‌ها و تنظیمات می‌مانند و مانده‌ی کارت‌ها دوباره حساب می‌شود.", app.malino.data.Repo.Reset.TxRange),
    AllTx("حذف همه‌ی تراکنش‌ها", "همه‌ی تراکنش‌ها و پیام‌های ثبت‌شده حذف می‌شوند. کارت‌ها، بودجه و تنظیمات می‌مانند و مانده‌ی هر کارت به موجودی اولیه برمی‌گردد.", app.malino.data.Repo.Reset.AllTx),
    Learned("فراموش کردن آموخته‌ها", "دسته‌هایی که برنامه برای مبلغ‌های تکراری پیشنهاد می‌داد، قالب‌هایی که با «یاد بده» ساخته‌اید و کلمه‌های نادیده‌گرفته‌شده به حالت اول برمی‌گردند. تراکنش‌ها دست نمی‌خورند.", app.malino.data.Repo.Reset.Learned),
    Log("پاک کردن گزارش پیام‌ها", "گزارش پیام‌های خام و پیام‌های شناخته‌نشده‌ی صندوق ورودی پاک می‌شود. تراکنش‌های ثبت‌شده دست نمی‌خورند.", app.malino.data.Repo.Reset.Log),
    Plan("حذف برنامه‌ریزی", "بودجه‌ها، اقساط و بدهی‌ها، اهداف، دارایی‌ها و قیمت‌ها حذف می‌شوند. تراکنش‌ها و کارت‌ها می‌مانند.", app.malino.data.Repo.Reset.Plan),
    Everything("ریست کامل برنامه", "کارت‌ها، تراکنش‌ها، بودجه، اقساط، دارایی‌ها و هر چیزی که برنامه یاد گرفته حذف می‌شود و برنامه مثل روز اول می‌شود. فقط پین و قفل برنامه می‌ماند.", app.malino.data.Repo.Reset.Everything),
}

@Composable
private fun ResetDialog(ask: ResetAsk, s: UiState, onDismiss: () -> Unit, onGo: (app.malino.data.Repo.Reset, Long, Long) -> Unit) {
    val today = remember { Jalali.today() }
    var from by remember { mutableStateOf(Jalali.addDays(today, -30)) }
    var to by remember { mutableStateOf(today) }
    var pick by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    val hard = ask == ResetAsk.Everything
    var understood by remember { mutableStateOf(false) }
    val fromMs = Jalali.toMillis(from.y, from.m, from.d); val toMs = Jalali.toMillis(to.y, to.m, to.d) + 86_400_000L
    val affected = when (ask) {
        ResetAsk.Range -> "${fa(s.coreTxs.count { it.at in fromMs until toMs })} تراکنش حذف می‌شود."
        ResetAsk.AllTx -> "${fa(s.coreTxs.size)} تراکنش حذف می‌شود."
        ResetAsk.Everything -> "${fa(s.cards.size)} کارت و ${fa(s.coreTxs.size)} تراکنش حذف می‌شود."
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(ask.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("⚠️ این کار برگشت‌ناپذیر است (جز با نسخه‌ی ایمنی خودکار که قبلش گرفته می‌شود).", color = extra.expense, fontWeight = FontWeight.Medium)
                Text(ask.body)
                affected?.let { Text(it, fontWeight = FontWeight.Bold) }
                if (ask == ResetAsk.Range) {
                    OutlinedButton({ pick = 1 }) { Text("از " + Jalali.format(from)) }
                    OutlinedButton({ pick = 2 }) { Text("تا " + Jalali.format(to) + " (همان روز هم حذف می‌شود)") }
                }
                Row(Modifier.fillMaxWidth().clickable { understood = !understood }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(understood, { understood = it })
                    Text("متوجه‌ام و تأیید می‌کنم", style = MaterialTheme.typography.bodyMedium)
                }
                if (hard) OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text("برای تأیید بنویسید: ریست") })
            }
        },
        confirmButton = {
            TextButton(
                { onGo(ask.kind, fromMs, toMs) },
                enabled = understood && (!hard || typed.trim() == "ریست") && (ask != ResetAsk.Range || fromMs <= Jalali.toMillis(to.y, to.m, to.d)),
            ) { Text("حذف", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } },
    )
    if (pick == 1) JalaliPickerDialog(from, { pick = 0 }) { from = it; pick = 0 }
    if (pick == 2) JalaliPickerDialog(to, { pick = 0 }) { to = it; pick = 0 }
}
