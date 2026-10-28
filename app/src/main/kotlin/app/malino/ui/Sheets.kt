package app.malino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.border
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.malino.core.*

private val KINDS = listOf("هزینه", "درآمد", "کارمزد")

private fun digits(s: String) = digitsOnly(s)

/** Add / edit a transaction: big amount, numeric keypad, icon categories. [tx] == null means a new one; [kind0] 0=expense 1=income 2=fee. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxSheet(s: UiState, tx: Tx?, kind0: Int, newId: () -> String, onSave: (Tx) -> Unit, onDelete: (String) -> Unit, onUnlink: (String) -> Unit, onMoveStart: (String) -> Unit, onDismiss: () -> Unit) {
    val readOnly = tx != null && (tx.type == TxType.TRANSFER || tx.type == TxType.ADJUST)
    var kind by remember { mutableIntStateOf(when (tx?.type) { TxType.IN -> 1; TxType.FEE -> 2; null -> kind0; else -> 0 }) }
    var amount by remember { mutableStateOf(tx?.let { kotlin.math.abs(it.amount).toString() }.orEmpty()) }
    var cardId by remember { mutableStateOf(tx?.cardId ?: s.cards.firstOrNull()?.card?.id) }
    var cat by remember { mutableStateOf(tx?.categoryId) }
    var note by remember { mutableStateOf(tx?.note.orEmpty()) }
    var at by remember { mutableLongStateOf(tx?.at ?: System.currentTimeMillis()) }
    var pickDate by remember { mutableStateOf(false) }
    val dt = Jalali.fromMillis(at)
    var hh by remember { mutableStateOf(two(dt.h)) }
    var mm by remember { mutableStateOf(two(dt.mi)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val dimU = tx?.let { t -> s.txs.firstOrNull { it.tx.id == t.id } }?.takeIf { it.dimmed }
            if (dimU != null) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(extra.warnBg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("چرا کم‌رنگ است؟", color = extra.warn, fontWeight = FontWeight.Bold)
                    Text("تاریخ شروع کارت «${dimU.card?.name.orEmpty()}» (${fullDate(dimU.card?.openingAt ?: 0)}) بعد از این تراکنش است؛ برنامه فقط تراکنش‌های بعد از شروع را در مانده و گزارش حساب می‌کند.",
                        color = extra.warn, style = MaterialTheme.typography.bodySmall)
                    if (dimU.tx.balanceAfter != null) {
                        Text("اگر می‌خواهید حساب شود، شروع کارت را به قبل از این تراکنش می‌بریم و موجودی اولیه از مانده‌ی بانک همین پیام حساب می‌شود.", color = extra.warn, style = MaterialTheme.typography.bodySmall)
                        Button({ onMoveStart(dimU.tx.id); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = extra.warn, contentColor = extra.warnBg)) { Text("شروع کارت را به قبل از این ببر") }
                    } else Text("مانده‌ی بانک برای این تراکنش ثبت نشده؛ از «ویرایش کارت» تاریخ شروع را تغییر دهید.", color = extra.warn, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (readOnly) {
                Text(if (tx!!.type == TxType.TRANSFER) "انتقال بین کارت‌ها" else "تعدیل مانده", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (tx.type == TxType.TRANSFER) "پول از یک کارت شما به کارت دیگرتان رفته است. این مورد جزو هزینه یا درآمد حساب نمی‌شود و فقط مانده‌ی دو کارت را جابه‌جا می‌کند. اگر انتقال نبوده، با دکمه‌ی زیر به برداشت/واریز عادی تبدیلش کنید." else "برنامه این تعدیل را خودش ساخته تا مانده با مانده‌ی بانک یکی شود. جزو هزینه یا درآمد نیست.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tx.type == TxType.TRANSFER) {
                    Button({ onUnlink(tx.id); onDismiss() }, Modifier.fillMaxWidth()) { Text("انتقال نبوده؛ تبدیل به برداشت/واریز عادی") }
                }
                OutlinedButton({ onDelete(tx.id); onDismiss() }, Modifier.fillMaxWidth()) { Text("حذف") }
                return@Column
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                KINDS.forEachIndexed { i, k ->
                    SegmentedButton(selected = kind == i, onClick = { kind = i; cat = null }, shape = SegmentedButtonDefaults.itemShape(i, KINDS.size), label = { Text(k) })
                }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (amount.isEmpty()) "۰" else money(amount.toLongOrNull() ?: 0),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold),
                    color = if (kind == 1) extra.income else if (amount.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Text("ریال", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (s.cards.isEmpty()) Text("اول یک کارت اضافه کنید.", color = extra.warn)
            else if (s.cards.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.cards, key = { it.card.id }) { c -> FilterChip(cardId == c.card.id, { cardId = c.card.id }, { Text(c.card.name) }, shape = RoundedCornerShape(99.dp)) }
            }
            if (kind <= 1) {
                val cats = s.categories.filter { it.kind == if (kind == 1) "income" else "expense" }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cats, key = { it.id }) { c ->
                        val on = cat == c.id
                        Column(
                            Modifier.width(72.dp).clip(RoundedCornerShape(18.dp)).pressScale { cat = if (on) null else c.id }.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            IconBadge(catIcon(c.id) ?: Icons.Rounded.MoreHoriz, if (on) extra.accent else MaterialTheme.colorScheme.surfaceVariant,
                                if (on) extra.accentInk else MaterialTheme.colorScheme.primary, 42)
                            Text(c.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton({ pickDate = true }, shape = RoundedCornerShape(16.dp)) { Text(fullDate(at)) }
                OutlinedTextField(hh, { hh = digits(it).take(2) }, Modifier.width(76.dp), singleLine = true, label = { Text("ساعت") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(mm, { mm = digits(it).take(2) }, Modifier.width(76.dp), singleLine = true, label = { Text("دقیقه") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            OutlinedTextField(note, { note = it }, label = { Text("یادداشت (اختیاری)") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            Keypad(onKey = { k ->
                amount = when (k) {
                    "⌫" -> amount.dropLast(1)
                    "۰۰۰" -> if (amount.isNotEmpty() && amount.length <= 9) amount + "000" else amount
                    else -> if (amount.length < 12 && !(amount.isEmpty() && k == "۰")) amount + Jalali.toEn(k) else amount
                }
            })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    enabled = (amount.toLongOrNull() ?: 0) > 0 && cardId != null, modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = extra.accent, contentColor = extra.accentInk),
                    onClick = {
                        val d = Jalali.fromMillis(at).date
                        val ms = Jalali.toMillis(d.y, d.m, d.d, (hh.toIntOrNull() ?: 0).coerceIn(0, 23), (mm.toIntOrNull() ?: 0).coerceIn(0, 59))
                        val a = amount.toLong()
                        val type = when (kind) { 0 -> TxType.OUT; 1 -> TxType.IN; else -> TxType.FEE }
                        val base = tx ?: Tx(newId(), cardId!!, ms, type, a, source = "manual")
                        onSave(base.copy(cardId = cardId!!, at = ms, type = type, amount = a, categoryId = cat, note = note))
                        onDismiss()
                    },
                ) { Text("ذخیره", fontWeight = FontWeight.Bold) }
                if (tx != null) OutlinedButton({ onDelete(tx.id); onDismiss() }, Modifier.height(52.dp)) { Text("حذف") }
            }
        }
    }
    if (pickDate) JalaliPickerDialog(dt.date, { pickDate = false }) { d ->
        at = Jalali.toMillis(d.y, d.m, d.d, dt.h, dt.mi); pickDate = false
    }
}

/** Phone-dialer style keypad: 1-2-3 run left to right even in an RTL layout. */
@Composable
private fun Keypad(onKey: (String) -> Unit) {
    val keys = listOf("۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹", "۰۰۰", "۰", "⌫")
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).pressScale { onKey(k) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (k == "⌫") Icon(Icons.AutoMirrored.Rounded.Backspace, "حذف", Modifier.size(22.dp))
                            else Text(k, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium, fontSize = 20.sp))
                        }
                    }
                }
            }
        }
    }
}

private val SWATCHES = listOf(
    0xFF14304F, 0xFF1F5C7A, 0xFF12805C, 0xFFC98B1E, 0xFFC93B3B, 0xFF7A4FB5,
    0xFF1B1B1F, 0xFFF4F5F7, 0xFF4B5563, 0xFFB8BEC8,   // black, white, dark grey, light grey
    0xFFE0A800, 0xFFF3DC7A, 0xFFE8731A,               // strong yellow, light yellow, orange
    0xFF6D3FB0, 0xFFB9A2E0,                           // strong purple, light purple
)

/** Add / edit a card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardDialog(card: Card?, newId: () -> String, onSave: (Card) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit, isNew: Boolean = card == null) {
    var name by remember { mutableStateOf(card?.name.orEmpty()) }
    var bank by remember { mutableStateOf(card?.bank.orEmpty()) }
    var last4 by remember { mutableStateOf(card?.last4.orEmpty()) }
    var key by remember { mutableStateOf(card?.smsKey.orEmpty()) }
    var purpose by remember { mutableStateOf(card?.purpose.orEmpty()) }
    var opening by remember { mutableStateOf(card?.openingBalance?.toString().orEmpty()) }
    var openingAt by remember { mutableLongStateOf(card?.openingAt ?: System.currentTimeMillis()) }
    var color by remember { mutableLongStateOf(card?.color ?: SWATCHES[0]) }
    var pick by remember { mutableStateOf(false) }
    var dateChanged by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "کارت جدید" else "ویرایش کارت") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("نام (مثلاً روزمره)") }, singleLine = true)
                OutlinedTextField(bank, { bank = it }, label = { Text("بانک") }, singleLine = true)
                OutlinedTextField(purpose, { purpose = it }, label = { Text("کاربرد (مثلاً پس‌انداز)") }, singleLine = true)
                OutlinedTextField(last4, { last4 = digits(it).take(4) }, label = { Text("چهار رقم آخر کارت") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(key, { key = Jalali.toEn(it).trim() }, label = { Text("شناسه‌ی پیام") }, supportingText = {
                    Text("پیامک پارسیان: شماره‌ی حساب بالای پیامک. اعلان بلو: لازم نیست؛ اگر بانک یا نام کارت «بلو» باشد خودکار وصل می‌شود. چند شناسه را با ویرگول جدا کنید.")
                })
                OutlinedTextField(opening, { opening = digits(it).take(14) }, label = { Text("موجودی اولیه (ریال)") }, singleLine = true,
                    visualTransformation = ThousandsTransformation, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { if (opening.isNotEmpty()) Text(money(opening.toLongOrNull() ?: 0) + " ریال") })
                OutlinedButton({ pick = true }) { Text("از تاریخ " + fullDate(openingAt)) }
                // the picked colour gets a thick ring and a check mark so it is obvious which one is selected
                FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SWATCHES.forEach { c ->
                        val on = color == c
                        val sc by animateFloatAsState(if (on) 1.15f else 1f, spring(dampingRatio = 0.5f), label = "sw")
                        Box(
                            Modifier.size(34.dp).graphicsLayer { scaleX = sc; scaleY = sc }
                                .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, CircleShape)
                                .padding(if (on) 4.dp else 0.dp).clip(CircleShape).background(Color(c)).pressScale { color = c },
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(Icons.Rounded.Check, null, tint = cardInk(Color(c)), modifier = Modifier.size(16.dp)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val d = Jalali.fromMillis(openingAt).date
                onSave(Card(card?.id ?: newId(), name.trim(), bank.trim(), last4, key, purpose.trim(), color, opening.toLongOrNull() ?: 0,
                    if (dateChanged || (isNew && card == null)) startOf(d) else openingAt))
                onDismiss()
            }) { Text("ذخیره", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row { if (onDelete != null) TextButton(onDelete) { Text("حذف") }; TextButton(onDismiss) { Text("انصراف") } }
        },
    )
    if (pick) JalaliPickerDialog(Jalali.fromMillis(openingAt).date, { pick = false }) { d ->
        openingAt = startOf(d); dateChanged = true; pick = false
    }
}

/** Opening balance "as of" a day: today means right now (so today's messages are not counted twice); other days start at midnight. */
private fun startOf(d: Jalali.Date): Long = if (d == Jalali.today()) System.currentTimeMillis() else Jalali.toMillis(d.y, d.m, d.d)
