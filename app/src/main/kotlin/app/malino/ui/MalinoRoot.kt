package app.malino.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.malino.core.Card
import app.malino.core.Tx

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("خانه", Icons.Rounded.Home), Tx("تراکنش‌ها", Icons.Rounded.ReceiptLong), Inbox("صندوق", Icons.Rounded.Inbox),
    Reports("گزارش", Icons.Rounded.BarChart), More("تنظیمات", Icons.Rounded.Settings)
}

@Composable
fun MalinoRoot(vm: MainVm = viewModel()) {
    MalinoTheme {
        val ctx = LocalContext.current
        val s by vm.state.collectAsStateWithLifecycle()
        val hidden by vm.hideBalance.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableStateOf(Tab.Home) }
        var perms by remember { mutableStateOf(readPerms(ctx)) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { perms = readPerms(ctx) }
        var onboarded by remember { mutableStateOf(vm.onboarded) }
        var showCheck by rememberSaveable { mutableStateOf(false) }
        var page by rememberSaveable { mutableStateOf<Page?>(null) }
        var sheetTx by remember { mutableStateOf<Tx?>(null) }
        var sheetKind by remember { mutableIntStateOf(0) }
        var showSheet by remember { mutableStateOf(false) }
        var editCard by remember { mutableStateOf<Card?>(null) }
        var showCard by remember { mutableStateOf(false) }
        fun add(kind: Int) { sheetTx = null; sheetKind = kind; showSheet = true }

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AnimatedContent(
                targetState = tab, modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (fadeIn(tween(240, 60)) + slideInHorizontally(spring(0.85f, Spring.StiffnessMediumLow)) { -it / 10 * dir } + scaleIn(tween(240), 0.97f)) togetherWith
                        (fadeOut(tween(110)) + slideOutHorizontally(tween(200)) { it / 12 * dir })
                }, label = "tabs",
            ) { t ->
                when (t) {
                    Tab.Home -> HomeScreen(s, hidden, vm::toggleHide, !perms.ok, goInbox = { tab = Tab.Inbox }, goMore = { tab = Tab.More },
                        goTx = { tab = Tab.Tx }, goReports = { tab = Tab.Reports }, onAdd = ::add, onPage = { page = it })
                    Tab.Inbox -> InboxScreen(s, vm)
                    Tab.Tx -> TxScreen(s, onEdit = { sheetTx = it; showSheet = true })
                    Tab.Reports -> ReportsScreen(s, Modifier.statusBarsPadding())
                    Tab.More -> MoreScreen(s, vm, perms, onOpenCheck = { showCheck = true }, onPage = { page = it }, onEditCard = { editCard = it; showCard = true }, modifier = Modifier.statusBarsPadding())
                }
            }
            FloatingNav(tab, s.inbox.size + s.gaps.size, onTab = { tab = it }, onAdd = { add(0) }, modifier = Modifier.align(Alignment.BottomCenter))
        }

        AnimatedVisibility(page != null, enter = slideInVertically(spring(0.9f, Spring.StiffnessMediumLow)) { it / 3 } + fadeIn(), exit = slideOutVertically { it / 3 } + fadeOut()) {
            BackHandler { page = null }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
                when (page) {
                    Page.Budgets -> BudgetsPage(s, vm, onClose = { page = null })
                    Page.Debts -> DebtsPage(s, vm, onClose = { page = null })
                    Page.Assets -> AssetsPage(s, vm, onClose = { page = null })
                    null -> {}
                }
            }
        }
        AnimatedVisibility(showCheck, enter = slideInVertically(spring(0.9f, Spring.StiffnessMediumLow)) { it / 3 } + fadeIn(), exit = slideOutVertically { it / 3 } + fadeOut()) {
            BackHandler { showCheck = false }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
                CaptureCheckScreen(s, vm, onClose = { showCheck = false })
            }
        }
        AnimatedVisibility(!onboarded, exit = fadeOut() + slideOutVertically { -it / 6 }) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                OnboardingScreen(vm, onDone = { onboarded = true }, onCheck = { onboarded = true; showCheck = true })
            }
        }

        if (showSheet) TxSheet(s, sheetTx, sheetKind, vm::newId, vm::saveTx, vm::deleteTx) { showSheet = false }
        if (showCard) CardDialog(editCard, vm::newId, vm::saveCard,
            onDelete = editCard?.let { c -> { vm.deleteCard(c) { }; showCard = false } }) { showCard = false }
    }
}

/** Pill-shaped bar floating above the content: the selected tab widens to show its label; the add button is raised in the middle. */
@Composable
private fun FloatingNav(tab: Tab, badge: Int, onTab: (Tab) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(30.dp)
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp).padding(bottom = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 22.dp).height(68.dp)
                .shadow(14.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
                .background(MaterialTheme.colorScheme.surface, shape).border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f)) { NavItem(Tab.Home, tab == Tab.Home, 0) { onTab(Tab.Home) }; NavItem(Tab.Tx, tab == Tab.Tx, 0) { onTab(Tab.Tx) } }
            Spacer(Modifier.width(70.dp))
            Row(Modifier.weight(1f)) { NavItem(Tab.Inbox, tab == Tab.Inbox, badge) { onTab(Tab.Inbox) }; NavItem(Tab.Reports, tab == Tab.Reports, 0) { onTab(Tab.Reports) } }
        }
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 1.dp).size(58.dp)
                .shadow(12.dp, RoundedCornerShape(21.dp), ambientColor = extra.accent, spotColor = extra.accent)
                .background(extra.accent, RoundedCornerShape(21.dp)).pressScale(onAdd),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, "تراکنش جدید", tint = extra.accentInk, modifier = Modifier.size(28.dp)) }
    }
}

@Composable
private fun RowScope.NavItem(t: Tab, selected: Boolean, badge: Int, onClick: () -> Unit) {
    val w by animateFloatAsState(if (selected) 2.1f else 1f, spring(0.8f, Spring.StiffnessMediumLow), label = "w")
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, label = "bg")
    val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "fg")
    Box(Modifier.weight(w).height(52.dp).clip(RoundedCornerShape(22.dp)).background(bg).pressScale(onClick), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BadgedBox({ if (badge > 0) Badge { Text(fa(badge)) } }) { Icon(t.icon, t.label, tint = fg) }
            AnimatedVisibility(selected, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                Text(t.label, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}
