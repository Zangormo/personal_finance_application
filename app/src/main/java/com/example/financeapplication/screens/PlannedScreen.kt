package com.example.financeapplication.screens

import androidx.compose.ui.res.stringResource
import com.example.financeapplication.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.financeapplication.datastores.PlannedItem
import com.example.financeapplication.datastores.PlannedItemsDataStore
import com.example.financeapplication.datastores.PlannedSort
import com.example.financeapplication.datastores.PlannedType
import com.example.financeapplication.datastores.sortedFor
import com.example.financeapplication.ui.components.*
import com.example.financeapplication.utils.Money
import kotlinx.coroutines.launch

/** Merged Essentials + Wishlist screen. */
@Composable
fun PlannedScreen(onBackPress: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loadedItems by PlannedItemsDataStore.getItems(context).collectAsState(initial = null)
    val items = loadedItems.orEmpty()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val type = if (tab == 0) PlannedType.ESSENTIAL else PlannedType.WISH
    // each list remembers its own sort mode
    val essentialsSort by remember { PlannedItemsDataStore.getSort(context, PlannedType.ESSENTIAL) }.collectAsState(initial = null)
    val wishlistSort by remember { PlannedItemsDataStore.getSort(context, PlannedType.WISH) }.collectAsState(initial = null)
    val sort = if (type == PlannedType.ESSENTIAL) essentialsSort else wishlistSort
    var sortMenu by remember { mutableStateOf(false) }
    val visible = items.filter { it.type == type }.sortedFor(sort ?: PlannedSort.NAME_AZ)
    val total = visible.sumOf { it.price.toDouble() }.toFloat()

    var name by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var reusable by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlannedItem?>(null) }
    var deleting by remember { mutableStateOf<PlannedItem?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        ScreenHeader(stringResource(R.string.planned), onBack = onBackPress) {
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort)) }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    PlannedSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.label)) },
                            trailingIcon = {
                                if (option == (sort ?: PlannedSort.NAME_AZ)) {
                                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                sortMenu = false
                                scope.launch { PlannedItemsDataStore.setSort(context, type, option) }
                            }
                        )
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            SegmentedSelector(
                options = listOf(stringResource(R.string.essentials), stringResource(R.string.wishlist)),
                selectedIndex = tab,
                onSelect = { tab = it }
            )
            Spacer(Modifier.height(12.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = if (tab == 0) stringResource(R.string.essential_item) else stringResource(R.string.wish_item),
                        modifier = Modifier.weight(1.4f)
                    )
                    AmountField(
                        value = price,
                        onValueChange = { price = it },
                        label = stringResource(R.string.price),
                        placeholder = null,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        SwitchRow(
                            text = stringResource(R.string.reusable),
                            subtitle = stringResource(R.string.stays_after_purchase),
                            checked = reusable,
                            onCheckedChange = { reusable = it }
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                val item = PlannedItem(
                                    name = name.trim(),
                                    price = Money.parse(price) ?: 0f,
                                    type = type,
                                    reusable = reusable
                                )
                                scope.launch { PlannedItemsDataStore.add(context, item) }
                                name = ""; price = ""; reusable = false
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add)) }
                }
            }
        }

        // built once the items and the sort modes are loaded, so the list does not jump on open
        if (loadedItems == null || essentialsSort == null || wishlistSort == null) return@Column
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SectionTitle(
                    if (tab == 0) stringResource(R.string.essentials_count, visible.size) else stringResource(R.string.wishlist_count, visible.size),
                    trailing = if (total > 0) stringResource(R.string.total_amount, Money.format(total)) else null
                )
            }
            if (visible.isEmpty()) {
                item { EmptyState(if (tab == 0) stringResource(R.string.no_essentials) else stringResource(R.string.wishlist_empty)) }
            }
            items(visible, key = { it.id }) { item ->
                AppCard(Modifier.animateItem().fillMaxWidth(), onClick = { editing = item }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            if (item.reusable) {
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Autorenew, null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        stringResource(R.string.reusable),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        if (item.price > 0f) {
                            Text(Money.format(item.price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        IconButton(onClick = { deleting = item }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    editing?.let { item ->
        PlannedEditDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { updated ->
                scope.launch { PlannedItemsDataStore.update(context, updated) }
                editing = null
            }
        )
    }
    deleting?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.delete_item_title),
            text = stringResource(R.string.removed_from_list, item.name),
            onConfirm = {
                scope.launch { PlannedItemsDataStore.remove(context, item.id) }
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun PlannedEditDialog(item: PlannedItem, onDismiss: () -> Unit, onSave: (PlannedItem) -> Unit) {
    var name by remember { mutableStateOf(item.name) }
    var price by remember { mutableStateOf(Money.toInput(item.price)) }
    var reusable by remember { mutableStateOf(item.reusable) }
    var isWish by remember { mutableStateOf(item.type == PlannedType.WISH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_item)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.name))
                AmountField(value = price, onValueChange = { price = it }, label = stringResource(R.string.price_optional))
                SegmentedSelector(
                    options = listOf(stringResource(R.string.essential), stringResource(R.string.wish)),
                    selectedIndex = if (isWish) 1 else 0,
                    onSelect = { isWish = it == 1 }
                )
                SwitchRow(
                    text = stringResource(R.string.reusable),
                    subtitle = stringResource(R.string.reusable_subtitle),
                    checked = reusable,
                    onCheckedChange = { reusable = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        item.copy(
                            name = name.trim(),
                            price = Money.parse(price) ?: 0f,
                            reusable = reusable,
                            type = if (isWish) PlannedType.WISH else PlannedType.ESSENTIAL
                        )
                    )
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
