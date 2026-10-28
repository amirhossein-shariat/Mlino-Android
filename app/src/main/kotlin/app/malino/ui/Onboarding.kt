package app.malino.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** First launch: explain what is read, and ask for the permissions before anything else. */
@Composable
fun OnboardingScreen(vm: MainVm, onDone: () -> Unit, onCheck: () -> Unit) {
    val ctx = LocalContext.current
    var perms by remember { mutableStateOf(readPerms(ctx)) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).systemBarsPadding().padding(horizontal = 16.dp, vertical = 24.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.enter(0), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("به مالینو خوش آمدید", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("برای اینکه تراکنش‌ها خودکار ثبت شوند، مالینو باید پیامک و اعلان بانکی را ببیند.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface2(Modifier.enter(1)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("قول ما درباره‌ی حریم خصوصی", fontWeight = FontWeight.Bold)
                listOf(
                    "فقط پیامک بانک‌ها و اعلان برنامه‌های بانکی ذخیره می‌شود؛ پیامک شخصی هرگز.",
                    "رمز یکبار مصرف (OTP) هرگز ذخیره نمی‌شود.",
                    "همه‌چیز فقط روی گوشی شما می‌ماند؛ برنامه به اینترنت وصل نمی‌شود.",
                ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
        Column(Modifier.enter(2), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PermissionChecklist(perms, null, null) { perms = readPerms(ctx) }
            RestrictedHelp()
        }
        var pinSet by remember { mutableStateOf(vm.security.enabled) }
        var askPin by remember { mutableStateOf(false) }
        Surface2(Modifier.enter(3)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("قفل برنامه با پین ۴ رقمی (اختیاری)", fontWeight = FontWeight.Bold)
                Text("با پین، فقط خودتان وارد برنامه می‌شوید. هر وقت خواستید از «تنظیمات ← امنیت و قفل» پین را تغییر یا حذف کنید؛ برنامه بدون رمز هم کامل کار می‌کند.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (pinSet) Text("پین تنظیم شد ✓", color = extra.income, fontWeight = FontWeight.Medium)
                OutlinedButton({ askPin = true }) { Text(if (pinSet) "تغییر پین" else "تعیین پین ۴ رقمی") }
            }
        }
        if (askPin) PinDialog("پین ۴ رقمی جدید", true, { askPin = false }, exactLen = 4) { pin ->
            vm.security.setPin(pin); vm.refreshLock(); pinSet = true; askPin = false
        }
        Column(Modifier.enter(4), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ vm.onboarded = true; onDone() }, Modifier.fillMaxWidth()) { Text(if (perms.ok) "ورود به برنامه" else "بعداً، ورود به برنامه") }
            OutlinedButton({ vm.onboarded = true; onCheck() }, Modifier.fillMaxWidth()) { Text("بررسی ثبت خودکار") }
        }
    }
}
