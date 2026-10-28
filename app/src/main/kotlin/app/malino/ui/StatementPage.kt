package app.malino.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.malino.core.Statement

/** Imports a bank statement that an AI assistant converted to the fixed Malino CSV format. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatementPage(s: UiState, vm: MainVm, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val msg by vm.statementMsg.collectAsState()
    var text by remember { mutableStateOf("") }
    var cardId by remember { mutableStateOf(s.cards.singleOrNull()?.card?.id) }
    var copied by remember { mutableStateOf(false) }
    val parsed = remember(text) { if (text.isBlank()) null else Statement.parse(text) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) text = runCatching {
            ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().take(3_000_000).toByteArray().toString(Charsets.UTF_8) }.removePrefix("﻿")
        }.getOrDefault("")
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("وارد کردن صورت‌حساب", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); TextButton(onClose) { Text("بستن") } } }

        item { SectionTitle("چطور؟ (۴ قدم)") }
        item {
            Surface2 {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("۱) «کپی دستور» را بزنید.", fontWeight = FontWeight.Medium)
                    Text("۲) در ChatGPT یا هر هوش مصنوعی دیگر، دستور را بچسبانید و همراهش عکس، PDF یا فایل اکسل صورت‌حساب بانک را بفرستید.")
                    Text("۳) جواب آن (متن CSV) را کپی کنید و پایین بچسبانید؛ یا آن را فایل کنید و «انتخاب فایل» را بزنید.")
                    Text("۴) پیش‌نمایش را ببینید و «وارد کن» را بزنید.")
                    Text("نکته: برنامه‌ی مالینو اینترنت ندارد و چیزی نمی‌فرستد. فقط شما خودتان صورت‌حساب را به هوش مصنوعی می‌دهید؛ اگر نمی‌خواهید، شماره‌ی حساب و نام را قبلش از عکس یا فایل پاک کنید.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button({ clip.setText(AnnotatedString(Statement.PROMPT)); copied = true }, colors = ButtonDefaults.buttonColors(containerColor = extra.accent, contentColor = extra.accentInk)) {
                        Text(if (copied) "کپی شد ✓" else "کپی دستور برای هوش مصنوعی", fontWeight = FontWeight.Bold)
                    }
                    Text("قالب: ${Statement.HEADER}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { SectionTitle("مال کدام کارت است؟") }
        item {
            if (s.cards.isEmpty()) Text("اول یک کارت بسازید.", color = extra.warn)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.cards.forEach { c -> FilterChip(cardId == c.card.id, { cardId = c.card.id }, { Text(cardLabel(c.card)) }, shape = RoundedCornerShape(99.dp)) }
            }
        }

        item { SectionTitle("جواب هوش مصنوعی") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 140.dp), placeholder = { Text("متن CSV را اینجا بچسبانید") }, shape = RoundedCornerShape(16.dp), maxLines = 12)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ clip.getText()?.text?.let { text = it } }) { Text("چسباندن") }
                    OutlinedButton({ pickFile.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) }) { Text("انتخاب فایل") }
                    if (text.isNotEmpty()) TextButton({ text = "" }) { Text("پاک کن") }
                }
            }
        }

        parsed?.let { p ->
            item { SectionTitle("پیش‌نمایش") }
            item {
                Surface2 {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (p.rows.isEmpty()) Text("هیچ ردیف معتبری پیدا نشد. دستور را دوباره به هوش مصنوعی بدهید و فقط CSV بخواهید.", color = extra.warn)
                        else {
                            val a = p.rows.minOf { it.at }; val b = p.rows.maxOf { it.at }
                            Text("${fa(p.rows.size)} ردیف معتبر · از ${fullDate(a)} تا ${fullDate(b)}", fontWeight = FontWeight.Medium)
                            Text("خروجی: ${money(p.rows.filter { it.out }.sumOf { it.amount })} ریال · ورودی: ${money(p.rows.filter { !it.out }.sumOf { it.amount })} ریال",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            p.rows.take(5).forEach { r ->
                                Text("${fullDate(r.at)} ${clock(r.at)} · ${if (r.out) "−" else "+"}${money(r.amount)} · ${r.note.take(30)}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (p.rows.size > 5) Text("…", style = MaterialTheme.typography.bodySmall)
                        }
                        if (p.errors.isNotEmpty()) {
                            Text("${fa(p.errors.size)} سطر خوانده نشد:", color = extra.warn, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            p.errors.take(4).forEach { Text(it, color = extra.warn, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
            item {
                Button(
                    { vm.importStatement(cardId!!, p.rows) }, enabled = p.rows.isNotEmpty() && cardId != null, modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = extra.accent, contentColor = extra.accentInk),
                ) { Text("وارد کن", fontWeight = FontWeight.Bold) }
            }
            item {
                Text("ردیف‌های تکراری (که قبلاً از پیامک یا اعلان ثبت شده‌اند) خودکار رد می‌شوند. اگر مانده در ردیف‌ها باشد، مانده‌ی بانک مرجع است و اختلاف‌ها در صندوق ورودی می‌آید.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    msg?.let { m ->
        AlertDialog(onDismissRequest = { vm.statementMsg.value = null }, text = { Text(m) },
            confirmButton = { TextButton({ vm.statementMsg.value = null; text = "" }) { Text("باشه", fontWeight = FontWeight.Bold) } })
    }
}
