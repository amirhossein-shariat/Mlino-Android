package app.malino.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
private fun PinDialog(title: String, askRepeat: Boolean, onDismiss: () -> Unit, onPin: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val err = when {
        a.isNotEmpty() && !Pin.isValid(a) -> "پین باید ${fa(Pin.MIN_LEN)} تا ${fa(Pin.MAX_LEN)} رقم باشد"
        askRepeat && b.isNotEmpty() && a != b -> "دو پین یکسان نیست"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(a, { a = digitsOnly(it).take(Pin.MAX_LEN) }, singleLine = true, label = { Text("پین") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (askRepeat) OutlinedTextField(b, { b = digitsOnly(it).take(Pin.MAX_LEN) }, singleLine = true, label = { Text("تکرار پین") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (err != null) Text(err, color = extra.expense, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = Pin.isValid(a) && (!askRepeat || a == b), onClick = { onPin(a) }) { Text("تأیید", fontWeight = FontWeight.Bold) } },
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
        msg?.let { m -> item { Text(m, Modifier.fillMaxWidth().padding(14.dp), color = if (staged || m.contains("ذخیره شد")) extra.income else extra.warn, fontWeight = FontWeight.Medium) } }

        item { SectionTitle("پشتیبان‌گیری") }
        item {
            Surface2 {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("همه‌ی کارت‌ها، تراکنش‌ها، بودجه، اقساط و دارایی‌ها در یک فایل zip ذخیره می‌شود. آن را جایی امن (مثلاً فضای ابری یا کامپیوتر) نگه دارید؛ با پاک شدن برنامه، نسخه‌های داخل برنامه هم از بین می‌روند.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button({ saveZip.launch("malino-backup-${d.y}${"%02d".format(d.m)}${"%02d".format(d.d)}.zip") }, colors = ButtonDefaults.buttonColors(containerColor = extra.accent, contentColor = extra.accentInk)) { Text("ذخیره‌ی پشتیبان", fontWeight = FontWeight.Bold) }
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
                    Button({ saveXlsx.launch("malino-transactions-${d.y}${"%02d".format(d.m)}${"%02d".format(d.d)}.xlsx") }) { Text("ساخت فایل اکسل") }
                    Text("دو برگه: تراکنش‌ها (با تاریخ شمسی، کارت، دسته، مانده) و خلاصه‌ی دسته‌ها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    confirmRestore?.let { go ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null }, title = { Text("بازیابی پشتیبان؟") },
            text = { Text("اطلاعات فعلی برنامه بعد از اعمال بازیابی با اطلاعات فایل جایگزین می‌شود. قبلش یک پشتیبان از وضعیت فعلی بگیرید.") },
            confirmButton = { TextButton({ confirmRestore = null; go() }) { Text("بازیابی", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ confirmRestore = null }) { Text("انصراف") } },
        )
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
    return "پشتیبان ${Jalali.format(j)}"
}
