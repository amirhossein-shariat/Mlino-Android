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
import androidx.compose.material3.*
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

private val KINDS = listOf("برداشت", "واریز", "کارمزد", "تعدیل +", "تعدیل −")

private fun digits(s: String) = Jalali.toEn(s).filter { it.isDigit() }

/** Add / edit a transaction. [tx] == null means a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxSheet(s: UiState, tx: Tx?, newId: () -> String, onSave: (Tx) -> Unit, onDelete: (String) -> Unit, onDismiss: () -> Unit) {
    val editable = tx == null || tx.type != TxType.TRANSFER
    var kind by remember {
        mutableIntStateOf(when (tx?.type) { TxType.IN -> 1; TxType.FEE -> 2; TxType.ADJUST -> if ((tx.amount) >= 0) 3 else 4; else -> 0 })
    }
    var amount by remember { mutableStateOf(tx?.let { kotlin.math.abs(it.amount).toString() }.orEmpty()) }
    var cardId by remember { mutableStateOf(tx?.cardId ?: s.cards.firstOrNull()?.card?.id) }
    var cat by remember { mutableStateOf(tx?.categoryId) }
    var note by remember { mutableStateOf(tx?.note.orEmpty()) }
    var at by remember { mutableLongStateOf(tx?.at ?: System.currentTimeMillis()) }
    var pickDate by remember { mutableStateOf(false) }
    val dt = Jalali.fromMillis(at)
    var hh by remember { mutableStateOf(two(dt.h)) }
    var mm by remember { mutableStateOf(two(dt.mi)) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (tx == null) "تراکنش جدید" else "ویرایش تراکنش", style = MaterialTheme.typography.titleLarge)
            if (!editable) {
                Text("این تراکنش بخشی از یک انتقال بین کارت‌هاست و از صندوق ورودی قابل جدا شدن است.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton({ onDelete(tx!!.id); onDismiss() }) { Text("حذف") }
                return@Column
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                KINDS.forEachIndexed { i, k ->
                    SegmentedButton(selected = kind == i, onClick = { kind = i; cat = null }, shape = SegmentedButtonDefaults.itemShape(i, KINDS.size),
                        label = { Text(k, maxLines = 1, style = MaterialTheme.typography.labelSmall) })
                }
            }
            OutlinedTextField(amount, { amount = digits(it) }, label = { Text("مبلغ (ریال)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { if (amount.isNotEmpty()) Text(money(amount.toLongOrNull() ?: 0) + " ریال") })
            if (s.cards.isEmpty()) Text("اول یک کارت اضافه کنید.", color = extra.warn)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(s.cards, key = { it.card.id }) { c -> FilterChip(cardId == c.card.id, { cardId = c.card.id }, { Text(c.card.name) }) }
            }
            if (kind <= 1) {
                val cats = s.categories.filter { it.kind == if (kind == 1) "income" else "expense" }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(cats, key = { it.id }) { c -> FilterChip(cat == c.id, { cat = if (cat == c.id) null else c.id }, { Text(c.name) }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton({ pickDate = true }) { Text(fullDate(at)) }
                OutlinedTextField(hh, { hh = digits(it).take(2) }, Modifier.width(72.dp), singleLine = true, label = { Text("ساعت") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(mm, { mm = digits(it).take(2) }, Modifier.width(72.dp), singleLine = true, label = { Text("دقیقه") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            OutlinedTextField(note, { note = it }, label = { Text("یادداشت") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    enabled = (amount.toLongOrNull() ?: 0) > 0 && cardId != null,
                    onClick = {
                        val d = Jalali.fromMillis(at).date
                        val ms = Jalali.toMillis(d.y, d.m, d.d, (hh.toIntOrNull() ?: 0).coerceIn(0, 23), (mm.toIntOrNull() ?: 0).coerceIn(0, 59))
                        val a = amount.toLong()
                        val type = when (kind) { 0 -> TxType.OUT; 1 -> TxType.IN; 2 -> TxType.FEE; else -> TxType.ADJUST }
                        val base = tx ?: Tx(newId(), cardId!!, ms, type, a, source = "manual")
                        onSave(base.copy(cardId = cardId!!, at = ms, type = type, amount = if (kind == 4) -a else a, categoryId = cat, note = note))
                        onDismiss()
                    },
                ) { Text("ذخیره") }
                if (tx != null) OutlinedButton({ onDelete(tx.id); onDismiss() }) { Text("حذف") }
            }
        }
    }
    if (pickDate) JalaliPickerDialog(dt.date, { pickDate = false }) { d ->
        at = Jalali.toMillis(d.y, d.m, d.d, dt.h, dt.mi); pickDate = false
    }
}

private val SWATCHES = listOf(0xFF14304F, 0xFF1F5C7A, 0xFF12805C, 0xFFC98B1E, 0xFFC93B3B, 0xFF7A4FB5)

/** Add / edit a card. */
@Composable
fun CardDialog(card: Card?, newId: () -> String, onSave: (Card) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(card?.name.orEmpty()) }
    var bank by remember { mutableStateOf(card?.bank.orEmpty()) }
    var last4 by remember { mutableStateOf(card?.last4.orEmpty()) }
    var key by remember { mutableStateOf(card?.smsKey.orEmpty()) }
    var purpose by remember { mutableStateOf(card?.purpose.orEmpty()) }
    var opening by remember { mutableStateOf(card?.openingBalance?.toString().orEmpty()) }
    var openingAt by remember { mutableLongStateOf(card?.openingAt ?: System.currentTimeMillis()) }
    var color by remember { mutableLongStateOf(card?.color ?: SWATCHES[0]) }
    var pick by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (card == null) "کارت جدید" else "ویرایش کارت") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("نام (مثلاً روزمره)") }, singleLine = true)
                OutlinedTextField(bank, { bank = it }, label = { Text("بانک") }, singleLine = true)
                OutlinedTextField(purpose, { purpose = it }, label = { Text("کاربرد (مثلاً پس‌انداز)") }, singleLine = true)
                OutlinedTextField(last4, { last4 = digits(it).take(4) }, label = { Text("چهار رقم آخر کارت") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(key, { key = Jalali.toEn(it).trim() }, label = { Text("شناسه‌ی پیام (شماره حساب داخل پیامک، یا blu)") }, supportingText = {
                    Text("پیامک پارسیان: شماره‌ی بالای پیام. اعلان بلو: blu")
                })
                OutlinedTextField(opening, { opening = digits(it) }, label = { Text("موجودی اولیه (ریال)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedButton({ pick = true }) { Text("از تاریخ " + fullDate(openingAt)) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SWATCHES.forEach { c ->
                        val sc by animateFloatAsState(if (color == c) 1.2f else 1f, spring(dampingRatio = 0.5f), label = "sw")
                        Box(Modifier.size(30.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(CircleShape).background(Color(c)).pressScale { color = c })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val d = Jalali.fromMillis(openingAt).date
                onSave(Card(card?.id ?: newId(), name.trim(), bank.trim(), last4, key, purpose.trim(), color, opening.toLongOrNull() ?: 0,
                    Jalali.toMillis(d.y, d.m, d.d)))
                onDismiss()
            }) { Text("ذخیره", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row { if (onDelete != null) TextButton(onDelete) { Text("حذف") }; TextButton(onDismiss) { Text("انصراف") } }
        },
    )
    if (pick) JalaliPickerDialog(Jalali.fromMillis(openingAt).date, { pick = false }) { d ->
        openingAt = Jalali.toMillis(d.y, d.m, d.d); pick = false
    }
}
