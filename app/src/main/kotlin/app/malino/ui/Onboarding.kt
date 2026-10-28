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
        Column(Modifier.enter(3), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ vm.onboarded = true; onDone() }, Modifier.fillMaxWidth()) { Text(if (perms.ok) "ورود به برنامه" else "بعداً، ورود به برنامه") }
            OutlinedButton({ vm.onboarded = true; onCheck() }, Modifier.fillMaxWidth()) { Text("بررسی ثبت خودکار") }
        }
    }
}
