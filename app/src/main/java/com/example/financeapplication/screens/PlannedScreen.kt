package com.example.financeapplication.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
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
import com.example.financeapplication.datastores.PlannedType
import com.example.financeapplication.ui.components.*
import com.example.financeapplication.utils.Money
import kotlinx.coroutines.launch

/** Merged Essentials + Wishlist screen. */
@Composable
fun PlannedScreen(onBackPress: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by PlannedItemsDataStore.getItems(context).collectAsState(initial = emptyList())

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val type = if (tab == 0) PlannedType.ESSENTIAL else PlannedType.WISH
    val visible = items.filter { it.type == type }.sortedBy { it.name.lowercase() }
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
        ScreenHeader("Planned", onBack = onBackPress)
        Column(Modifier.padding(horizontal = 16.dp)) {
            SegmentedSelector(
                options = listOf("Essentials", "Wishlist"),
                selectedIndex = tab,
                onSelect = { tab = it }
            )
            Spacer(Modifier.height(12.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = if (tab == 0) "Essential item" else "Wish item",
                        modifier = Modifier.weight(1.4f)
                    )
                    AmountField(
                        value = price,
                        onValueChange = { price = it },
                        label = "Price",
                        placeholder = null,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        SwitchRow(
                            text = "Reusable",
                            subtitle = "Stays after purchase",
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
                    ) { Icon(Icons.Default.Add, contentDescription = "Add") }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SectionTitle(
                    if (tab == 0) "Essentials (${visible.size})" else "Wishlist (${visible.size})",
                    trailing = if (total > 0) "Total ${Money.format(total)}" else null
                )
            }
            if (visible.isEmpty()) {
                item { EmptyState(if (tab == 0) "No essentials yet" else "Your wishlist is empty") }
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
                                        "Reusable",
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
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            title = "Delete item?",
            text = "\"${item.name}\" will be removed from the list.",
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
        title = { Text("Edit item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(value = name, onValueChange = { name = it }, label = "Name")
                AmountField(value = price, onValueChange = { price = it }, label = "Price (optional)")
                SegmentedSelector(
                    options = listOf("Essential", "Wish"),
                    selectedIndex = if (isWish) 1 else 0,
                    onSelect = { isWish = it == 1 }
                )
                SwitchRow(
                    text = "Reusable",
                    subtitle = "Don't remove it after it's used in a spending",
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
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
