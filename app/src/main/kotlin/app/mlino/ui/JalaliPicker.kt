package app.mlino.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mlino.core.Jalali

/** Jalali calendar dialog with month paging, today/yesterday shortcuts and animated selection. */
@Composable
fun JalaliPickerDialog(initial: Jalali.Date, onDismiss: () -> Unit, onPick: (Jalali.Date) -> Unit) {
    var ym by remember { mutableStateOf(initial.y to initial.m) }
    var sel by remember { mutableStateOf(initial) }
    val today = remember { Jalali.today() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton({ onPick(sel) }) { Text("تأیید", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } },
        title = null,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ ym = if (ym.second == 12) ym.first + 1 to 1 else ym.first to ym.second + 1 }) { Icon(Icons.Rounded.ChevronLeft, "ماه بعد") }
                    Text(Jalali.MONTHS[ym.second - 1] + " " + Jalali.toFa(ym.first), Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium)
                    IconButton({ ym = if (ym.second == 1) ym.first - 1 to 12 else ym.first to ym.second - 1 }) { Icon(Icons.Rounded.ChevronRight, "ماه قبل") }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach {
                        Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                AnimatedContent(ym, transitionSpec = {
                    val fwd = targetState.first * 12 + targetState.second > initialState.first * 12 + initialState.second
                    (fadeIn(tween(200)) + slideInHorizontally(tween(260)) { if (fwd) -it / 4 else it / 4 }) togetherWith
                        (fadeOut(tween(120)) + slideOutHorizontally(tween(200)) { if (fwd) it / 4 else -it / 4 })
                }, label = "month") { (y, m) ->
                    val lead = Jalali.weekday(y, m, 1)
                    val len = Jalali.monthLength(y, m)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (row in 0 until 6) {
                            Row(Modifier.fillMaxWidth()) {
                                for (col in 0 until 7) {
                                    val day = row * 7 + col - lead + 1
                                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                        if (day in 1..len) DayCell(day, selected = sel == Jalali.Date(y, m, day), isToday = today == Jalali.Date(y, m, day)) {
                                            sel = Jalali.Date(y, m, day)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip({ sel = today; ym = today.y to today.m }, { Text("امروز") })
                    AssistChip({ sel = Jalali.addDays(today, -1); ym = sel.y to sel.m }, { Text("دیروز") })
                    AssistChip({ sel = Jalali.addDays(today, -7); ym = sel.y to sel.m }, { Text("هفته‌ی قبل") })
                }
            }
        },
    )
}

@Composable
private fun DayCell(day: Int, selected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, tween(180), label = "day")
    val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, tween(180), label = "dayfg")
    Box(
        Modifier.fillMaxSize(0.9f).clip(CircleShape).background(bg)
            .then(if (isToday && !selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
            .pressScale(onClick),
        contentAlignment = Alignment.Center,
    ) { Text(Jalali.toFa(day), color = fg, style = MaterialTheme.typography.bodyMedium) }
}
