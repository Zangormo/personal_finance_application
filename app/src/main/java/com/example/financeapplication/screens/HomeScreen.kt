package com.example.financeapplication.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemInfo
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.financeapplication.R
import com.example.financeapplication.datastores.*
import com.example.financeapplication.ui.components.AppCard
import com.example.financeapplication.ui.components.CardShape
import com.example.financeapplication.ui.components.HOME_TILE_HEIGHT
import com.example.financeapplication.ui.theme.*
import com.example.financeapplication.utils.Dates
import com.example.financeapplication.utils.Money
import kotlinx.coroutines.launch

enum class HomeDestination { ADD_SPENDING, ADD_INCOME, HISTORY, PLANNED, SAVINGS, OVERVIEW, SUBSCRIPTIONS, DEBTS, TODO, NOTES }

private const val TILE_KEY = "t:"

private fun LazyStaggeredGridItemInfo.contains(p: Offset): Boolean =
    p.x >= offset.x && p.x <= offset.x + size.width && p.y >= offset.y && p.y <= offset.y + size.height

@Composable
fun HomeScreen(
    onNavigate: (HomeDestination) -> Unit,
    onOpenMenu: () -> Unit,
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        // Charge subscriptions whose payment date has come
        SubscriptionsDataStore.processDue(context)
    }
    BackHandler(enabled = editMode) { onEditModeChange(false) }

    val layout by HomeLayoutStore.get(context).collectAsState(initial = null)
    val balance by UserPreferencesDataStore.getOverallBalance(context).collectAsState(initial = 0f)
    val savings by UserPreferencesDataStore.getSavingsBalance(context).collectAsState(initial = 0f)
    val debts by DebtsDataStore.getDebts(context).collectAsState(initial = emptyList())
    val subs by SubscriptionsDataStore.getSubscriptions(context).collectAsState(initial = emptyList())
    val todos by TodoDataStore.getItems(context).collectAsState(initial = emptyList())
    val spendings by SpendingDataStore.getSpendings(context).collectAsState(initial = emptyList())
    val notes by NotesDataStore.getNotes(context).collectAsState(initial = emptyList())

    val iOwe = debts.filter { it.direction == DebtDirection.I_OWE }.sumOf { it.remaining.toDouble() }.toFloat()
    val owedToMe = debts.filter { it.direction == DebtDirection.OWED_TO_ME }.sumOf { it.remaining.toDouble() }.toFloat()
    val nextSub = subs.filter { it.active }.minByOrNull { it.nextChargeAt }
    val openTodos = todos.count { !it.isDoneNow() }

    val monthStart = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1); set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val spentThisMonth = spendings.filter { it.timestamp >= monthStart }.sumOf { it.amount.toDouble() }.toFloat()

    // ---- Reordering -------------------------------------------------------------------------
    val stored = layout?.order.orEmpty()
    val balanceSize = layout?.balanceSize ?: BalanceSize.LARGE
    // While dragging (and until the new order is saved) the grid uses a local order
    var localOrder by remember { mutableStateOf<List<HomeTileId>?>(null) }
    var dragging by remember { mutableStateOf<HomeTileId?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(stored, dragging) {
        if (dragging == null && localOrder == stored) localOrder = null
    }
    val order = localOrder ?: stored
    val currentOrder by rememberUpdatedState(order)
    val currentEditMode by rememberUpdatedState(editMode)
    val gridState = rememberLazyStaggeredGridState()
    // Tiles have different sizes, so right after a swap the finger can still be over the tile it
    // was swapped with; that tile is ignored until the finger leaves it.
    var lastSwappedKey by remember { mutableStateOf<Any?>(null) }

    fun onDragMove() {
        val id = dragging ?: return
        val items = gridState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == TILE_KEY + id.name } ?: return
        val center = Offset(dragPos.x + current.size.width / 2f, dragPos.y + current.size.height / 2f)
        lastSwappedKey?.let { k -> if (items.none { it.key == k && it.contains(center) }) lastSwappedKey = null }
        val target = items.firstOrNull {
            it.key != current.key && it.key != lastSwappedKey &&
                (it.key as? String)?.startsWith(TILE_KEY) == true && it.contains(center)
        } ?: return
        lastSwappedKey = target.key
        val ids = localOrder ?: currentOrder
        val from = ids.indexOf(id)
        val to = ids.indexOfFirst { TILE_KEY + it.name == target.key }
        if (from == -1 || to == -1) return
        localOrder = ids.toMutableList().apply { add(to, removeAt(from)) }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun finishDrag() {
        val ids = localOrder
        if (dragging != null && ids != null) scope.launch { HomeLayoutStore.setOrder(context, ids) }
        dragging = null
        lastSwappedKey = null
    }

    // Scroll while a tile is held near the top or bottom edge
    LaunchedEffect(dragging) {
        if (dragging == null) return@LaunchedEffect
        val edge = with(density) { 72.dp.toPx() }
        while (true) {
            val info = gridState.layoutInfo
            val current = info.visibleItemsInfo.firstOrNull { it.key == TILE_KEY + dragging?.name }
            if (current != null) {
                val top = dragPos.y - info.viewportStartOffset
                val bottom = top + current.size.height
                val height = info.viewportSize.height
                val speed = when {
                    top < edge -> -(edge - top) / edge * 24f
                    bottom > height - edge -> (bottom - (height - edge)) / edge * 24f
                    else -> 0f
                }
                if (speed != 0f && gridState.scrollBy(speed.coerceIn(-24f, 24f)) != 0f) onDragMove()
            }
            withFrameNanos { }
        }
    }

    // Wiggle of the tiles while editing
    val wiggle = rememberInfiniteTransition(label = "wiggle")
    val angle by wiggle.animateFloat(
        initialValue = -0.6f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(140, easing = LinearEasing), RepeatMode.Reverse),
        label = "angle"
    )

    Column(Modifier.fillMaxSize()) {
        // ---- Header -----------------------------------------------------------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp)
        ) {
            IconButton(onClick = onOpenMenu, enabled = !editMode) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu))
            }
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Crossfade(targetState = editMode, label = "homeEdit") { editing ->
                if (editing) {
                    TextButton(onClick = { onEditModeChange(false) }) {
                        Text(stringResource(R.string.done), fontWeight = FontWeight.Bold)
                    }
                } else {
                    IconButton(onClick = { onEditModeChange(true) }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.home_edit))
                    }
                }
            }
        }
        AnimatedVisibility(visible = editMode) {
            Text(
                stringResource(R.string.home_edit_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
            )
        }

        // Built only once the layout is loaded, so tiles do not animate into place on every open
        if (layout == null) return@Column

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            state = gridState,
            userScrollEnabled = dragging == null,
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val startItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.contains(down.position) }
                            ?: return@awaitEachGesture
                        val key = startItem.key as? String ?: return@awaitEachGesture
                        val id = HomeTileId.entries.firstOrNull { TILE_KEY + it.name == key } ?: return@awaitEachGesture
                        val wasEditMode = currentEditMode
                        var started = false
                        fun lift() {
                            started = true
                            localOrder = null
                            dragging = id
                            dragPos = Offset(startItem.offset.x.toFloat(), startItem.offset.y.toFloat())
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        if (!wasEditMode) {
                            // Long press: start editing and keep holding the tile to move it right away
                            val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                var moved = Offset.Zero
                                var cancelled = false
                                while (!cancelled) {
                                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed) {
                                        cancelled = true
                                    } else {
                                        moved += change.positionChange()
                                        if (moved.getDistance() > viewConfiguration.touchSlop) cancelled = true
                                    }
                                }
                                false
                            } ?: true
                            if (!longPressed) return@awaitEachGesture
                            onEditModeChange(true)
                            lift()
                        }
                        var slop = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                // the long press must not also open the tile
                                if (started) change.consume()
                                break
                            }
                            val delta = change.positionChange()
                            if (!started) {
                                slop += delta
                                if (slop.getDistance() > viewConfiguration.touchSlop) {
                                    lift()
                                    dragPos += slop
                                }
                            } else {
                                dragPos += delta
                                onDragMove()
                            }
                            if (started) change.consume()
                        }
                        if (started) finishDrag()
                    }
                }
        ) {
            order.forEachIndexed { index, tile ->
                val fullLine = tile == HomeTileId.BALANCE && balanceSize != BalanceSize.SMALL
                item(
                    key = TILE_KEY + tile.name,
                    span = if (fullLine) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane
                ) {
                    val isDragging = dragging == tile
                    val tileModifier = if (isDragging) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer {
                                val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == TILE_KEY + tile.name }
                                if (info != null) {
                                    translationX = dragPos.x - info.offset.x
                                    translationY = dragPos.y - info.offset.y
                                }
                                scaleX = 1.05f
                                scaleY = 1.05f
                                shadowElevation = 24f
                                shape = CardShape
                            }
                    } else {
                        Modifier
                            .animateItem()
                            .graphicsLayer {
                                // full-width tiles turn less, otherwise their corners swing a lot
                                val a = if (fullLine) angle / 3f else angle
                                rotationZ = if (editMode) a * (if (index % 2 == 0) 1 else -1) else 0f
                            }
                    }
                    val open: (HomeDestination) -> Unit = { if (!editMode) onNavigate(it) }
                    when (tile) {
                        HomeTileId.BALANCE -> BalanceHero(
                            balance = balance,
                            savings = savings,
                            spentThisMonth = spentThisMonth,
                            size = balanceSize,
                            editMode = editMode,
                            modifier = tileModifier,
                            onResize = {
                                val next = BalanceSize.entries[(balanceSize.ordinal + 1) % BalanceSize.entries.size]
                                scope.launch { HomeLayoutStore.setBalanceSize(context, next) }
                            },
                            onClick = { open(HomeDestination.OVERVIEW) }
                        )
                        HomeTileId.SPENDING -> QuickAction(
                            stringResource(R.string.spending), Icons.Filled.Remove, Negative, tileModifier
                        ) { open(HomeDestination.ADD_SPENDING) }
                        HomeTileId.INCOME -> QuickAction(
                            stringResource(R.string.income), Icons.Filled.Add, Positive, tileModifier
                        ) { open(HomeDestination.ADD_INCOME) }
                        HomeTileId.HISTORY -> HomeTile(
                            stringResource(R.string.history), stringResource(R.string.history_subtitle),
                            Icons.AutoMirrored.Filled.List, tileModifier
                        ) { open(HomeDestination.HISTORY) }
                        HomeTileId.PLANNED -> HomeTile(
                            stringResource(R.string.planned), stringResource(R.string.planned_subtitle),
                            Icons.Filled.ShoppingCart, tileModifier
                        ) { open(HomeDestination.PLANNED) }
                        HomeTileId.SAVINGS -> HomeTile(
                            stringResource(R.string.savings), Money.format(savings), Icons.Filled.Savings, tileModifier
                        ) { open(HomeDestination.SAVINGS) }
                        HomeTileId.OVERVIEW -> HomeTile(
                            stringResource(R.string.overview), stringResource(R.string.overview_subtitle),
                            Icons.Filled.PieChart, tileModifier
                        ) { open(HomeDestination.OVERVIEW) }
                        HomeTileId.SUBSCRIPTIONS -> HomeTile(
                            stringResource(R.string.subscriptions),
                            if (nextSub != null) stringResource(R.string.subscriptions_next, nextSub.name, Dates.date(nextSub.nextChargeAt))
                            else stringResource(R.string.none_yet),
                            Icons.Filled.Autorenew, tileModifier
                        ) { open(HomeDestination.SUBSCRIPTIONS) }
                        HomeTileId.DEBTS -> HomeTile(
                            stringResource(R.string.debts),
                            when {
                                iOwe > 0 && owedToMe > 0 -> stringResource(R.string.debts_both, Money.format(iOwe), Money.format(owedToMe))
                                iOwe > 0 -> stringResource(R.string.debts_i_owe_amount, Money.format(iOwe))
                                owedToMe > 0 -> stringResource(R.string.debts_owed_amount, Money.format(owedToMe))
                                else -> stringResource(R.string.no_debts)
                            },
                            Icons.Filled.Handshake, tileModifier
                        ) { open(HomeDestination.DEBTS) }
                        HomeTileId.TODO -> HomeTile(
                            stringResource(R.string.todo),
                            if (openTodos > 0) pluralStringResource(R.plurals.open_tasks, openTodos, openTodos)
                            else stringResource(R.string.all_done),
                            Icons.Filled.Checklist, tileModifier
                        ) { open(HomeDestination.TODO) }
                        HomeTileId.NOTES -> HomeTile(
                            stringResource(R.string.notes),
                            if (notes.isEmpty()) stringResource(R.string.no_notes_yet)
                            else pluralStringResource(R.plurals.notes_count, notes.size, notes.size),
                            Icons.Filled.Description, tileModifier
                        ) { open(HomeDestination.NOTES) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceHero(
    balance: Float,
    savings: Float,
    spentThisMonth: Float,
    size: BalanceSize,
    editMode: Boolean,
    modifier: Modifier,
    onResize: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = CardShape,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF172554), Color(0xFF0F172A))))
                .animateContentSize()
        ) {
            val small = size == BalanceSize.SMALL
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (small) Modifier.height(HOME_TILE_HEIGHT) else Modifier)
                    .padding(if (small) 16.dp else 20.dp),
                verticalArrangement = if (small) Arrangement.Center else Arrangement.Top
            ) {
                Text(
                    stringResource(R.string.balance),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.75f)
                )
                // Balance counts up/down smoothly when it changes
                val animatedBalance by animateFloatAsState(
                    targetValue = balance,
                    animationSpec = tween(700, easing = FastOutSlowInEasing),
                    label = "balance"
                )
                Text(
                    Money.format(animatedBalance),
                    fontSize = when (size) {
                        BalanceSize.LARGE -> 38.sp
                        BalanceSize.MEDIUM -> 30.sp
                        BalanceSize.SMALL -> 22.sp
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (balance < 0) Negative else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                when (size) {
                    BalanceSize.LARGE -> {
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth()) {
                            HeroStat(stringResource(R.string.savings), Money.format(savings), Modifier.weight(1f))
                            HeroStat(stringResource(R.string.total), Money.format(balance + savings), Modifier.weight(1f))
                            HeroStat(stringResource(R.string.spent_this_month), Money.format(spentThisMonth), Modifier.weight(1.3f))
                        }
                    }
                    BalanceSize.MEDIUM -> {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.total) + " " + Money.format(balance + savings),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                    BalanceSize.SMALL -> {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.total) + " " + Money.format(balance + savings),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            // Size switch, only while editing
            androidx.compose.animation.AnimatedVisibility(
                visible = editMode,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                IconButton(onClick = onResize) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (size == BalanceSize.SMALL) Icons.Filled.OpenInFull else Icons.Filled.CloseFullscreen,
                            contentDescription = stringResource(R.string.balance_resize),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f), maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun QuickAction(text: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.16f), contentColor = color)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun HomeTile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    AppCard(modifier = modifier.fillMaxWidth().height(HOME_TILE_HEIGHT), onClick = onClick) {
        Box(
            Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
