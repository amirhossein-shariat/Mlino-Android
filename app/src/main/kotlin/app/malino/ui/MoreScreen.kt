package app.malino.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.malino.core.Card

data class Perms(val sms: Boolean, val notifAccess: Boolean, val postNotif: Boolean) {
    val ok get() = sms && notifAccess
}

fun readPerms(ctx: Context): Perms = Perms(
    sms = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED,
    notifAccess = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName),
    postNotif = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
)

@Composable
fun MoreScreen(s: UiState, vm: MainVm, perms: Perms, onPermsChanged: () -> Unit, onEditCard: (Card?) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val importing by vm.importing.collectAsState()
    val imported by vm.importedCount.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onPermsChanged() }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onPermsChanged() }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("بیشتر", style = MaterialTheme.typography.titleLarge, modifier = Modifier.enter(0)) }

        item { SectionTitle("ثبت خودکار", Modifier.enter(1)) }
        item {
            Surface2(Modifier.enter(1)) {
                PermRow("خواندن پیامک بانک", "پیامک‌های بانک‌ها خودکار ثبت می‌شود؛ پیامک شخصی هرگز ذخیره نمی‌شود.", perms.sms) {
                    ask.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
                }
                PermRow("خواندن اعلان برنامه‌های بانکی", "برای بانک‌هایی که فقط اعلان می‌فرستند (مثل بلو).", perms.notifAccess) {
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                PermRow("اعلان تراکنش جدید", "وقتی تراکنشی ثبت شد خبر بدهد.", perms.postNotif) {
                    if (Build.VERSION.SDK_INT >= 33) ask.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
                PermRow("بدون محدودیت باتری", "تا سیستم برنامه را در پس‌زمینه نبندد.", null) {
                    ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        }

        item { SectionTitle("کارت‌ها", Modifier.enter(2)) }
        item {
            Surface2(Modifier.enter(2)) {
                if (s.cards.isEmpty()) EmptyState("کارتی اضافه نشده", "هر کارت یا حساب یک کاربرد دارد؛ مثلاً روزمره یا پس‌انداز.")
                s.cards.forEach { c ->
                    Row(Modifier.fillMaxWidth().clickable { onEditCard(c.card) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.card.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
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
                    Text("وارد کردن پیامک‌های قبلی بانک", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
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

@Composable
private fun PermRow(title: String, hint: String, granted: Boolean?, onClick: () -> Unit) {
    val ex = extra
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when (granted) {
            true -> Icon(Icons.Rounded.CheckCircle, null, tint = ex.income)
            false -> Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = ex.warn)
            null -> Icon(Icons.Rounded.ChevronLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
