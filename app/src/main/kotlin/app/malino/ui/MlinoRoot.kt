package app.malino.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.malino.core.Card
import app.malino.core.Tx

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("خانه", Icons.Rounded.Home), Inbox("صندوق", Icons.Rounded.Inbox), Tx("تراکنش‌ها", Icons.Rounded.ReceiptLong),
    Reports("گزارش", Icons.Rounded.BarChart), More("بیشتر", Icons.Rounded.GridView)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MalinoRoot(vm: MainVm = viewModel()) {
    MalinoTheme {
        val ctx = LocalContext.current
        val s by vm.state.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableStateOf(Tab.Home) }
        var perms by remember { mutableStateOf(readPerms(ctx)) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { perms = readPerms(ctx) }
        var onboarded by remember { mutableStateOf(vm.onboarded) }
        var showCheck by rememberSaveable { mutableStateOf(false) }
        var sheetTx by remember { mutableStateOf<Tx?>(null) }
        var showSheet by remember { mutableStateOf(false) }
        var editCard by remember { mutableStateOf<Card?>(null) }
        var showCard by remember { mutableStateOf(false) }
        val snack = remember { SnackbarHostState() }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snack) },
            floatingActionButton = {
                AnimatedVisibility(tab == Tab.Home || tab == Tab.Tx, enter = scaleIn(spring(0.55f, Spring.StiffnessMedium)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                    FloatingActionButton({ sheetTx = null; showSheet = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Icon(Icons.Rounded.Add, "تراکنش جدید")
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t, onClick = { tab = t }, label = { Text(t.label) },
                            icon = {
                                BadgedBox({ if (t == Tab.Inbox && s.inbox.size + s.gaps.size > 0) Badge { Text(fa(s.inbox.size + s.gaps.size)) } }) { Icon(t.icon, t.label) }
                            },
                        )
                    }
                }
            },
        ) { pad ->
            AnimatedContent(
                targetState = tab, modifier = Modifier.padding(pad).fillMaxSize(),
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (fadeIn(tween(240, 60)) + slideInHorizontally(spring(0.85f, Spring.StiffnessMediumLow)) { -it / 10 * dir } + scaleIn(tween(240), 0.97f)) togetherWith
                        (fadeOut(tween(110)) + slideOutHorizontally(tween(200)) { it / 12 * dir })
                }, label = "tabs",
            ) { t ->
                when (t) {
                    Tab.Home -> HomeScreen(s, !perms.ok, goInbox = { tab = Tab.Inbox }, goMore = { tab = Tab.More })
                    Tab.Inbox -> InboxScreen(s, vm)
                    Tab.Tx -> TxScreen(s, onEdit = { sheetTx = it; showSheet = true })
                    Tab.Reports -> ReportsScreen(s)
                    Tab.More -> MoreScreen(s, vm, perms, onOpenCheck = { showCheck = true }, onEditCard = { editCard = it; showCard = true })
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

        if (showSheet) TxSheet(s, sheetTx, vm::newId, vm::saveTx, vm::deleteTx) { showSheet = false }
        if (showCard) CardDialog(editCard, vm::newId, vm::saveCard,
            onDelete = editCard?.let { c -> { vm.deleteCard(c) { }; showCard = false } }) { showCard = false }
    }
}
