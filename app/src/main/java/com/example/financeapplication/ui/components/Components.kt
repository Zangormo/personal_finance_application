package com.example.financeapplication.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.financeapplication.classes.NecessityLevel
import com.example.financeapplication.ui.theme.MediumColor
import com.example.financeapplication.ui.theme.NecessaryColor
import com.example.financeapplication.ui.theme.NotNeededColor
import com.example.financeapplication.utils.Money

val CardShape = RoundedCornerShape(16.dp)
val FieldShape = RoundedCornerShape(12.dp)

/** Height shared by all tiles (home screen sections, to-do categories). */
val HOME_TILE_HEIGHT = 120.dp

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        actions()
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = CardShape, colors = colors, border = border) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier = modifier, shape = CardShape, colors = colors, border = border) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun appTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
)

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        shape = FieldShape,
        modifier = modifier.fillMaxWidth(),
        colors = appTextFieldColors()
    )
}

/**
 * Amount input. Accepts both "," and "." as decimal separator (max 2 decimals).
 * Use [Money.parse] to read the value.
 */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
    placeholder: String? = "0.00",
) {
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(Money.filterInput(it, value)) },
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            prefix = { Text("$ ") },
            singleLine = true,
            isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth(),
            colors = appTextFieldColors()
        )
        if (isError && !errorText.isNullOrEmpty()) {
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = FieldShape,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = Color.White)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Segmented two/three-way switch. */
@Composable
fun SegmentedSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedColors: List<Color>? = null,
) {
    val gap = 4.dp
    val segmentHeight = 40.dp
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(FieldShape)
            // a step lighter than cards and dialogs, so the track is visible everywhere
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(4.dp)
    ) {
        val count = options.size.coerceAtLeast(1)
        val segmentWidth = (maxWidth - gap * (count - 1)) / count
        // The selection pill slides between segments instead of jumping
        val indicatorOffset by animateDpAsState(
            targetValue = (segmentWidth + gap) * selectedIndex,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
            label = "segmentOffset"
        )
        val indicatorColor by animateColorAsState(
            targetValue = selectedColors?.getOrNull(selectedIndex) ?: MaterialTheme.colorScheme.primary,
            animationSpec = tween(250),
            label = "segmentColor"
        )
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .height(segmentHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(indicatorColor)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "segmentText"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(segmentHeight)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

fun necessityColor(level: NecessityLevel?): Color = when (level) {
    NecessityLevel.MEDIUM -> MediumColor
    NecessityLevel.NOT_NEEDED -> NotNeededColor
    else -> NecessaryColor
}

fun necessityLabel(level: NecessityLevel?): String = when (level) {
    NecessityLevel.MEDIUM -> "Medium"
    NecessityLevel.NOT_NEEDED -> "Not needed"
    else -> "Necessary"
}

@Composable
fun NecessitySelector(value: NecessityLevel, onChange: (NecessityLevel) -> Unit, modifier: Modifier = Modifier) {
    val levels = NecessityLevel.entries
    SegmentedSelector(
        options = levels.map { necessityLabel(it) },
        selectedIndex = levels.indexOf(value),
        onSelect = { onChange(levels[it]) },
        selectedColors = levels.map { necessityColor(it).copy(alpha = 0.85f) },
        modifier = modifier
    )
}

@Composable
fun Dot(color: Color, size: Int = 10) {
    Box(
        Modifier
            .size(size.dp)
            .background(color, CircleShape)
    )
}

@Composable
fun Chip(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun SwitchRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FieldShape)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Simple confirm dialog. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
