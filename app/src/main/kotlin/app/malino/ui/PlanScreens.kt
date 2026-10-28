package app.malino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.malino.core.*

enum class Page { Budgets, Debts, Assets }

@Composable
private fun PageHeader(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClose) { Text("بستن") }
    }
}

@Composable
private fun AmountField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value, { onChange(digitsOnly(it)) }, modifier.fillMaxWidth(), singleLine = true, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = { if (value.isNotEmpty()) Text(money(value.toLongOrNull() ?: 0) + " ریال") },
    )
}

@Composable
private fun levelColor(l: Insights.Level) = when (l) { Insights.Level.OK -> extra.income; Insights.Level.WARN -> extra.warn; Insights.Level.OVER -> extra.expense }

private fun dateOf(ms: Long) = Jalali.format(Jalali.fromMillis(ms).date)

// ================= Budgets, goals, recurring =================

@Composable
fun BudgetsPage(s: UiState, vm: MainVm, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val catName = remember(s.categories) { s.categories.associate { it.id to it.name } }
    var editBudget by remember { mutableStateOf<Pair<Budget?, Boolean>?>(null) }
    var editGoal by remember { mutableStateOf<Pair<Goal?, Boolean>?>(null) }
    val today = remember { Jalali.today() }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("بودجه و هدف", onClose) }
        item { SectionTitle("بودجه‌ی ${Jalali.MONTHS[today.m - 1]} ${Jalali.toFa(today.y)}") }
        if (s.budgetStatus.isEmpty()) item { Surface2 { EmptyState("بودجه‌ای تعریف نشده", "برای هر دسته‌ی هزینه یک سقف ماهانه بگذارید؛ در ۸۰٪ و ۱۰۰٪ هشدار می‌گیرید.") } }
        items(s.budgetStatus, key = { "b" + it.categoryId }) { b ->
            val c = levelColor(b.level)
            Surface2(Modifier.animateItem().clickable { editBudget = s.budgets.firstOrNull { it.categoryId == b.categoryId } to false }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(catName[b.categoryId] ?: b.categoryId, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        StatusPill(fa((b.pct * 100).toInt()) + "٪", c.copy(alpha = .14f), c)
                    }
                    ShareBar(b.pct.toFloat(), c)
                    Text("${money(b.spent)} از ${money(b.limit)} ریال", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        (s.monthSpend[""] ?: 0L).takeIf { it > 0 }?.let { u ->
            item { Text("بدون دسته در این ماه: ${money(u)} ریال", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)) }
        }
        item { OutlinedButton({ editBudget = null to true }, shape = RoundedCornerShape(16.dp)) { Text("＋ بودجه‌ی جدید") } }

        item { SectionTitle("هدف‌های پس‌انداز") }
        if (s.goals.isEmpty()) item { Surface2 { EmptyState("هدفی تعریف نشده", "مثلاً «سفر» با مبلغ هدف؛ از موجودی کارت یا دارایی‌ها پیشرفت حساب می‌شود.") } }
        items(s.goals, key = { "g" + it.id }) { g ->
            val p = s.goalProgress[g.id]
            Surface2(Modifier.animateItem().clickable { editGoal = g to false }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        if (p != null) StatusPill(if (p.done) "رسیدید" else fa((p.pct * 100).toInt()) + "٪", extra.incomeBg, extra.income)
                    }
                    ShareBar((p?.pct ?: 0.0).toFloat(), extra.accent)
                    Text("${money(p?.current ?: 0)} از ${money(g.target)} ریال", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (p != null && !p.done) Text(
                        goalHint(p), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { OutlinedButton({ editGoal = null to true }, shape = RoundedCornerShape(16.dp)) { Text("＋ هدف جدید") } }

        item { SectionTitle("هزینه‌های تکراری") }
        if (s.recurring.isEmpty()) item { Surface2 { EmptyState("هنوز الگویی پیدا نشد", "هزینه‌ای که حداقل ۳ ماه با مبلغ و روز مشابه تکرار شود اینجا می‌آید.") } }
        else item {
            Surface2 {
                s.recurring.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.categoryId?.let { catName[it] } ?: "بدون دسته", fontWeight = FontWeight.Medium)
                            Text("روز ${fa(r.day)} هر ماه · بعدی ${Jalali.format(r.next)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(money(r.amount), fontWeight = FontWeight.Bold)
                    }
                }
                s.forecast?.let { f -> Text("پیش‌بینی ماه بعد: ${money(f.total)} ریال", Modifier.padding(14.dp, 4.dp, 14.dp, 14.dp), style = MaterialTheme.typography.bodySmall, color = extra.warn) }
            }
        }
    }

    editBudget?.let { (b, isNew) ->
        val used = s.budgets.map { it.categoryId }.toSet()
        var cat by remember { mutableStateOf(b?.categoryId) }
        var limit by remember { mutableStateOf(b?.monthlyLimit?.toString().orEmpty()) }
        AlertDialog(
            onDismissRequest = { editBudget = null }, title = { Text(if (isNew) "بودجه‌ی جدید" else "ویرایش بودجه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isNew) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(s.categories.filter { it.kind == "expense" && it.id !in used }, key = { it.id }) { c -> FilterChip(cat == c.id, { cat = c.id }, { Text(c.name) }) }
                    } else Text(catName[b?.categoryId] ?: "", fontWeight = FontWeight.Medium)
                    AmountField(limit, { limit = it }, "سقف ماهانه (ریال)")
                }
            },
            confirmButton = {
                TextButton(enabled = cat != null && (limit.toLongOrNull() ?: 0) > 0, onClick = {
                    vm.saveBudgets(s.budgets.filter { it.categoryId != cat } + Budget(cat!!, limit.toLong())); editBudget = null
                }) { Text("ذخیره", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    if (!isNew) TextButton({ vm.saveBudgets(s.budgets.filter { it.categoryId != b?.categoryId }); editBudget = null }) { Text("حذف") }
                    TextButton({ editBudget = null }) { Text("انصراف") }
                }
            },
        )
    }

    editGoal?.let { (g, isNew) ->
        var name by remember { mutableStateOf(g?.name.orEmpty()) }
        var target by remember { mutableStateOf(g?.target?.toString().orEmpty()) }
        var cardIds by remember { mutableStateOf(g?.cardIds.orEmpty().toSet()) }
        var assetIds by remember { mutableStateOf(g?.assetIds.orEmpty().toSet()) }
        AlertDialog(
            onDismissRequest = { editGoal = null }, title = { Text(if (isNew) "هدف جدید" else "ویرایش هدف") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("نام هدف (مثلاً سفر)") }, singleLine = true)
                    AmountField(target, { target = it }, "مبلغ هدف (ریال)")
                    Text("از کدام کارت‌ها حساب شود؟", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(s.cards, key = { it.card.id }) { c -> FilterChip(c.card.id in cardIds, { cardIds = if (c.card.id in cardIds) cardIds - c.card.id else cardIds + c.card.id }, { Text(c.card.name) }) }
                    }
                    if (s.assets.isNotEmpty()) {
                        Text("و کدام دارایی‌ها؟", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(s.assets, key = { it.id }) { a -> FilterChip(a.id in assetIds, { assetIds = if (a.id in assetIds) assetIds - a.id else assetIds + a.id }, { Text(a.name.ifBlank { assetLabel(a.type) }) }) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && (target.toLongOrNull() ?: 0) > 0, onClick = {
                    val ng = Goal(g?.id ?: newId(), name.trim(), target.toLong(), cardIds.toList(), assetIds.toList())
                    vm.saveGoals(s.goals.filter { it.id != ng.id } + ng); editGoal = null
                }) { Text("ذخیره", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    if (!isNew) TextButton({ vm.saveGoals(s.goals.filter { it.id != g?.id }); editGoal = null }) { Text("حذف") }
                    TextButton({ editGoal = null }) { Text("انصراف") }
                }
            },
        )
    }
}

private fun goalHint(p: Insights.GoalProgress): String {
    val rate = p.monthlyRate
    val at = p.doneAt
    return when {
        at != null && rate != null -> "با روند فعلی (${money(rate)} در ماه) حدود ${Jalali.format(at)} می‌رسید"
        rate == null -> "برای تخمین زمان، حداقل ۱۴ روز داده لازم است"
        rate < 0 -> "با روند فعلی موجودی کم می‌شود (ماهانه ${money(-rate)} ریال)"
        else -> "با روند فعلی پس‌انداز نمی‌شود"
    }
}

fun assetLabel(t: AssetType) = when (t) { AssetType.GOLD -> "طلا"; AssetType.USD -> "دلار"; AssetType.COIN -> "سکه"; AssetType.OTHER -> "سایر" }

// ================= Debts & installments =================

@Composable
fun DebtsPage(s: UiState, vm: MainVm, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var edit by remember { mutableStateOf<Pair<Debt?, Boolean>?>(null) }
    val owedToMe = s.debts.filter { it.owedToMe }.sumOf { d -> d.installments.filter { !it.paid }.sumOf { it.amount } }
    val iOwe = s.debts.filter { !it.owedToMe }.sumOf { d -> d.installments.filter { !it.paid }.sumOf { it.amount } }

    fun setPaid(d: Debt, idx: Int, paid: Boolean) = vm.saveDebts(s.debts.map { x ->
        if (x.id != d.id) x else x.copy(installments = x.installments.mapIndexed { i, inst ->
            if (i != idx) inst else inst.copy(paidAt = if (paid) System.currentTimeMillis() else null, paidTxId = null)
        })
    })

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("طلب و بدهی", onClose) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SumTile("طلب من", owedToMe, extra.income, Modifier.weight(1f))
                SumTile("بدهی من", iOwe, extra.expense, Modifier.weight(1f))
            }
        }
        if (s.dueSoon.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(extra.warnBg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("سررسید نزدیک یا عقب‌افتاده", color = extra.warn, fontWeight = FontWeight.Bold)
                s.dueSoon.forEach { d ->
                    Text("${d.person}: ${money(d.amount)} ریال، " + (if (d.daysLeft < 0) "${fa(-d.daysLeft)} روز دیر" else if (d.daysLeft == 0) "امروز" else "${fa(d.daysLeft)} روز دیگر") + if (d.owedToMe) " (طلب)" else " (بدهی)",
                        color = extra.warn, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (s.debts.isEmpty()) item { Surface2 { EmptyState("چیزی ثبت نشده", "طلب‌ها، بدهی‌ها و اقساط را اینجا بنویسید تا سررسیدها یادآوری شود.") } }
        items(s.debts, key = { it.id }) { d ->
            val left = d.installments.filter { !it.paid }.sumOf { it.amount }
            Surface2(Modifier.animateItem()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.person, fontWeight = FontWeight.Bold)
                            Text(if (d.owedToMe) "طلب من" else "بدهی من", style = MaterialTheme.typography.labelSmall, color = if (d.owedToMe) extra.income else extra.expense)
                        }
                        Text(if (left == 0L) "تسویه شد" else "مانده ${money(left)}", style = MaterialTheme.typography.bodySmall, color = if (left == 0L) extra.income else MaterialTheme.colorScheme.onSurface)
                        TextButton({ edit = d to false }) { Text("ویرایش") }
                    }
                    d.installments.forEachIndexed { i, inst ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { setPaid(d, i, !inst.paid) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(if (inst.paid) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, if (inst.paid) "پرداخت شده" else "پرداخت نشده", tint = if (inst.paid) extra.income else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dateOf(inst.due), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(money(inst.amount), fontWeight = FontWeight.Medium, color = if (inst.paid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        item { OutlinedButton({ edit = null to true }, shape = RoundedCornerShape(16.dp)) { Text("＋ طلب یا بدهی جدید") } }
    }

    edit?.let { (d, isNew) ->
        var person by remember { mutableStateOf(d?.person.orEmpty()) }
        var owed by remember { mutableStateOf(d?.owedToMe ?: true) }
        var total by remember { mutableStateOf(d?.total?.toString().orEmpty()) }
        var count by remember { mutableStateOf("1") }
        var every by remember { mutableStateOf("1") }
        var first by remember { mutableStateOf(Jalali.addDays(Jalali.today(), 30)) }
        var pick by remember { mutableStateOf(false) }
        var note by remember { mutableStateOf(d?.note.orEmpty()) }
        AlertDialog(
            onDismissRequest = { edit = null }, title = { Text(if (isNew) "طلب یا بدهی جدید" else "ویرایش") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(owed, { owed = true }, { Text("طلب من") }); FilterChip(!owed, { owed = false }, { Text("بدهی من") })
                    }
                    OutlinedTextField(person, { person = it }, label = { Text("نام شخص") }, singleLine = true)
                    if (isNew) {
                        AmountField(total, { total = it }, "مبلغ کل (ریال)")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(count, { count = digitsOnly(it).take(3) }, Modifier.weight(1f), singleLine = true, label = { Text("تعداد قسط") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(every, { every = digitsOnly(it).take(2) }, Modifier.weight(1f), singleLine = true, label = { Text("فاصله (ماه)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        OutlinedButton({ pick = true }) { Text("اولین سررسید: " + Jalali.format(first)) }
                    }
                    OutlinedTextField(note, { note = it }, label = { Text("یادداشت") })
                }
            },
            confirmButton = {
                TextButton(enabled = person.isNotBlank() && (!isNew || (total.toLongOrNull() ?: 0) > 0), onClick = {
                    if (isNew) {
                        val n = (count.toIntOrNull() ?: 1).coerceIn(1, 120)
                        val nd = Debt(newId(), person.trim(), owed, total.toLong(), Insights.splitInstallments(total.toLong(), n, first, (every.toIntOrNull() ?: 1).coerceAtLeast(1)), note)
                        vm.saveDebts(s.debts + nd)
                    } else vm.saveDebts(s.debts.map { if (it.id == d!!.id) it.copy(person = person.trim(), owedToMe = owed, note = note) else it })
                    edit = null
                }) { Text("ذخیره", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    if (!isNew) TextButton({ vm.saveDebts(s.debts.filter { it.id != d!!.id }); edit = null }) { Text("حذف") }
                    TextButton({ edit = null }) { Text("انصراف") }
                }
            },
        )
        if (pick) JalaliPickerDialog(first, { pick = false }) { first = it; pick = false }
    }
}

@Composable
private fun SumTile(label: String, value: Long, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedMoney(value, MaterialTheme.typography.titleMedium, color)
    }
}

// ================= Assets & prices =================

@Composable
fun AssetsPage(s: UiState, vm: MainVm, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var editAsset by remember { mutableStateOf<Pair<Asset?, Boolean>?>(null) }
    var editPrices by remember { mutableStateOf(false) }
    val cardsTotal = s.cards.sumOf { it.balance }
    val staleDays = if (s.prices.updatedAt == 0L) null else ((System.currentTimeMillis() - s.prices.updatedAt) / 86_400_000L).toInt()

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("دارایی‌ها", onClose) }
        item {
            Surface2 {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ثروت کل", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedMoney(cardsTotal + s.assetsValue, MaterialTheme.typography.headlineSmall)
                    Text("کارت‌ها ${money(cardsTotal)} + دارایی‌ها ${money(s.assetsValue)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Surface2(Modifier.clickable { editPrices = true }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("قیمت‌ها (دستی)", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("ویرایش", color = extra.accent, fontWeight = FontWeight.Medium)
                    }
                    Text("طلای ۱۸ عیار هر گرم: " + if (s.prices.gold18 > 0) money(s.prices.gold18) else "وارد نشده", style = MaterialTheme.typography.bodySmall)
                    Text("هر دلار: " + if (s.prices.usd > 0) money(s.prices.usd) else "وارد نشده", style = MaterialTheme.typography.bodySmall)
                    Text("هر سکه: " + if (s.prices.coin > 0) money(s.prices.coin) else "وارد نشده", style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (staleDays == null) "قیمتی ثبت نشده؛ بدون قیمت، ارزش طلا و دلار و سکه محاسبه نمی‌شود."
                        else if (staleDays >= 3) "آخرین به‌روزرسانی ${fa(staleDays)} روز پیش؛ قیمت‌ها قدیمی است." else "به‌روزرسانی: ${dayLabel(s.prices.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall, color = if (staleDays == null || staleDays >= 3) extra.warn else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (s.assets.isEmpty()) item { Surface2 { EmptyState("دارایی ثبت نشده", "طلا، دلار، سکه یا هر دارایی دیگر را اضافه کنید.") } }
        items(s.assets, key = { it.id }) { a ->
            val v = Insights.assetValue(a, s.prices)
            Surface2(Modifier.animateItem().clickable { editAsset = a to false }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name.ifBlank { assetLabel(a.type) }, fontWeight = FontWeight.Medium)
                        Text(
                            when (a.type) {
                                AssetType.GOLD -> "${Jalali.toFa(a.qty.toString().removeSuffix(".0"))} گرم، عیار ${fa(a.karat)}"
                                AssetType.USD -> "${Jalali.toFa(a.qty.toString().removeSuffix(".0"))} دلار"
                                AssetType.COIN -> "${Jalali.toFa(a.qty.toString().removeSuffix(".0"))} سکه"
                                AssetType.OTHER -> "ارزش دستی"
                            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(if (v == null) "قیمت وارد نشده" else money(v), fontWeight = FontWeight.Bold, color = if (v == null) extra.warn else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        item { OutlinedButton({ editAsset = null to true }, shape = RoundedCornerShape(16.dp)) { Text("＋ دارایی جدید") } }
    }

    if (editPrices) {
        var g by remember { mutableStateOf(s.prices.gold18.takeIf { it > 0 }?.toString().orEmpty()) }
        var u by remember { mutableStateOf(s.prices.usd.takeIf { it > 0 }?.toString().orEmpty()) }
        var c by remember { mutableStateOf(s.prices.coin.takeIf { it > 0 }?.toString().orEmpty()) }
        AlertDialog(
            onDismissRequest = { editPrices = false }, title = { Text("قیمت‌ها (ریال)") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AmountField(g, { g = it }, "هر گرم طلای ۱۸ عیار")
                    AmountField(u, { u = it }, "هر دلار")
                    AmountField(c, { c = it }, "هر سکه")
                }
            },
            confirmButton = {
                TextButton({ vm.savePrices(Prices(g.toLongOrNull() ?: 0, u.toLongOrNull() ?: 0, c.toLongOrNull() ?: 0, System.currentTimeMillis())); editPrices = false }) { Text("ذخیره", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton({ editPrices = false }) { Text("انصراف") } },
        )
    }

    editAsset?.let { (a, isNew) ->
        var type by remember { mutableStateOf(a?.type ?: AssetType.GOLD) }
        var name by remember { mutableStateOf(a?.name.orEmpty()) }
        var qty by remember { mutableStateOf(a?.qty?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty()) }
        var karat by remember { mutableStateOf((a?.karat ?: 18).toString()) }
        var value by remember { mutableStateOf(a?.value?.takeIf { it > 0 }?.toString().orEmpty()) }
        AlertDialog(
            onDismissRequest = { editAsset = null }, title = { Text(if (isNew) "دارایی جدید" else "ویرایش دارایی") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AssetType.entries.toList()) { t -> FilterChip(type == t, { type = t }, { Text(assetLabel(t)) }) }
                    }
                    OutlinedTextField(name, { name = it }, label = { Text("نام (اختیاری)") }, singleLine = true)
                    if (type == AssetType.OTHER) AmountField(value, { value = it }, "ارزش (ریال)")
                    else {
                        OutlinedTextField(qty, { qty = Jalali.toEn(it).filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text(when (type) { AssetType.GOLD -> "مقدار (گرم)"; AssetType.USD -> "مقدار (دلار)"; else -> "تعداد سکه" }) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        if (type == AssetType.GOLD) OutlinedTextField(karat, { karat = digitsOnly(it).take(2) }, label = { Text("عیار") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = if (type == AssetType.OTHER) (value.toLongOrNull() ?: 0) > 0 else (qty.toDoubleOrNull() ?: 0.0) > 0, onClick = {
                    val na = Asset(a?.id ?: newId(), type, name.trim(), qty.toDoubleOrNull() ?: 0.0, (karat.toIntOrNull() ?: 18).coerceIn(1, 24), value.toLongOrNull() ?: 0)
                    vm.saveAssets(s.assets.filter { it.id != na.id } + na); editAsset = null
                }) { Text("ذخیره", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    if (!isNew) TextButton({ vm.saveAssets(s.assets.filter { it.id != a!!.id }); editAsset = null }) { Text("حذف") }
                    TextButton({ editAsset = null }) { Text("انصراف") }
                }
            },
        )
    }
}
