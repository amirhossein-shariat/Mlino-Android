package app.malino.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.malino.security.Biometric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** Full-screen PIN pad. Wrong PINs shake the dots; 5 wrong tries block input for 30 s. */
@Composable
fun LockScreen(vm: MainVm, onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    val store = vm.security
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var blockedUntil by remember { mutableLongStateOf(store.blockedUntil) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val shake = remember { Animatable(0f) }
    val len = store.pinLen
    val bio = store.biometric && Biometric.available(ctx)

    LaunchedEffect(blockedUntil) {
        while (blockedUntil > System.currentTimeMillis()) { now = System.currentTimeMillis(); delay(400) }
        now = System.currentTimeMillis()
    }
    fun bioPrompt() { ctx.findActivity()?.let { Biometric.prompt(it, onUnlocked) } }
    LaunchedEffect(Unit) { if (bio) bioPrompt() }
    val blocked = blockedUntil > now

    fun submit(p: String) {
        scope.launch {
            val ok = withContext(Dispatchers.Default) { store.check(p) }
            if (ok) { store.resetFails(); onUnlocked() }
            else {
                blockedUntil = store.wrong(); wrong = true; pin = ""
                shake.snapTo(0f)
                shake.animateTo(1f, keyframes { durationMillis = 380; -1f at 60; 1f at 130; -0.7f at 200; 0.5f at 260; 0f at 380 })
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("مالینو", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (blocked) "چند بار اشتباه زدید؛ ${fa(((blockedUntil - now) / 1000 + 1).toInt())} ثانیه صبر کنید" else if (wrong) "پین اشتباه است" else "پین را وارد کنید",
            color = if (blocked || wrong) extra.expense else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(26.dp))
        Row(Modifier.offset { IntOffset((shake.value * 14.dp.toPx()).toInt(), 0) }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(len) { i ->
                val on = i < pin.length
                val sc by animateFloatAsState(if (on) 1.2f else 1f, spring(0.5f), label = "dot")
                Box(Modifier.size(14.dp).scale(sc).clip(CircleShape).background(if (on) extra.accent else MaterialTheme.colorScheme.surfaceVariant))
            }
        }
        Spacer(Modifier.height(36.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "del")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { k ->
                            Box(
                                Modifier.size(76.dp, 62.dp).clip(RoundedCornerShape(20.dp))
                                    .then(if (k == "bio" && !bio) Modifier else Modifier.background(MaterialTheme.colorScheme.surface))
                                    .then(if (k == "bio" && !bio) Modifier else Modifier.pressScale {
                                        if (blocked) return@pressScale
                                        when (k) {
                                            "bio" -> bioPrompt()
                                            "del" -> { pin = pin.dropLast(1); wrong = false }
                                            else -> if (pin.length < len) { wrong = false; pin += k; if (pin.length == len) submit(pin) }
                                        }
                                    }),
                                contentAlignment = Alignment.Center,
                            ) {
                                when (k) {
                                    "bio" -> if (bio) Icon(Icons.Rounded.Fingerprint, "اثر انگشت", Modifier.size(28.dp), tint = extra.accent)
                                    "del" -> Icon(Icons.AutoMirrored.Rounded.Backspace, "حذف", Modifier.size(22.dp))
                                    else -> Text(Jalali_toFa(k), fontSize = 24.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Jalali_toFa(s: String) = app.malino.core.Jalali.toFa(s)
