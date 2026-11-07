package com.example.financeapplication.screens

import androidx.compose.ui.res.stringResource
import com.example.financeapplication.R
import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.financeapplication.datastores.NoteSpan
import com.example.financeapplication.datastores.NoteStyle

internal const val BULLET = "• "

/* ------------------------------------------------------------------------------------------ */
/* Span bookkeeping                                                                           */
/* ------------------------------------------------------------------------------------------ */

/** Merges overlapping / touching spans of the same style and drops empty or out-of-range ones. */
internal fun normalizeSpans(spans: List<NoteSpan>, length: Int): List<NoteSpan> {
    val out = mutableListOf<NoteSpan>()
    spans.groupBy { it.style }.forEach { (style, list) ->
        var curStart = -1
        var curEnd = -1
        list.map { NoteSpan(style, it.start.coerceIn(0, length), it.end.coerceIn(0, length)) }
            .filter { it.end > it.start }
            .sortedBy { it.start }
            .forEach { s ->
                if (curStart == -1) {
                    curStart = s.start; curEnd = s.end
                } else if (s.start <= curEnd) {
                    curEnd = maxOf(curEnd, s.end)
                } else {
                    out += NoteSpan(style, curStart, curEnd)
                    curStart = s.start; curEnd = s.end
                }
            }
        if (curStart != -1) out += NoteSpan(style, curStart, curEnd)
    }
    return out.sortedWith(compareBy({ it.start }, { it.style.ordinal }))
}

private fun removeRange(spans: List<NoteSpan>, style: NoteStyle?, start: Int, end: Int): List<NoteSpan> =
    spans.flatMap { s ->
        if ((style != null && s.style != style) || s.end <= start || s.start >= end) listOf(s)
        else listOfNotNull(
            NoteSpan(s.style, s.start, start).takeIf { it.end > it.start },
            NoteSpan(s.style, end, s.end).takeIf { it.end > it.start },
        )
    }

/** True when every character in [start, end) has [style]. */
internal fun isCovered(spans: List<NoteSpan>, style: NoteStyle, start: Int, end: Int): Boolean =
    end > start && spans.any { it.style == style && it.start <= start && it.end >= end }

/** Styles that text typed at [pos] picks up: the ones of the character before it. */
internal fun stylesAt(spans: List<NoteSpan>, pos: Int): Set<NoteStyle> =
    spans.filter { if (pos == 0) it.start == 0 && it.end > 0 else it.start < pos && it.end >= pos }
        .map { it.style }.toSet()

/** Turns [style] on for [start, end), or off when the whole range already has it. */
internal fun toggleStyle(spans: List<NoteSpan>, style: NoteStyle, start: Int, end: Int, length: Int): List<NoteSpan> =
    if (isCovered(spans, style, start, end)) normalizeSpans(removeRange(spans, style, start, end), length)
    else normalizeSpans(spans + NoteSpan(style, start, end), length)

internal fun clearStyles(spans: List<NoteSpan>, start: Int, end: Int, length: Int): List<NoteSpan> =
    normalizeSpans(removeRange(spans, null, start, end), length)

/**
 * Moves spans for an edit that replaced [removed] characters at [at] with [inserted] new ones.
 * The inserted text gets exactly the [typing] styles.
 */
internal fun applyEdit(
    spans: List<NoteSpan>, at: Int, removed: Int, inserted: Int, typing: Set<NoteStyle>, newLength: Int
): List<NoteSpan> {
    val delEnd = at + removed
    val delta = inserted - removed
    fun mapStart(x: Int) = when {
        x < at -> x
        x >= delEnd -> x + delta
        else -> at + inserted
    }
    fun mapEnd(y: Int) = when {
        y <= at -> y
        y >= delEnd -> y + delta
        else -> at
    }
    var result = spans.map { NoteSpan(it.style, mapStart(it.start), mapEnd(it.end)) }.filter { it.end > it.start }
    if (inserted > 0) {
        result = removeRange(result, null, at, at + inserted) + typing.map { NoteSpan(it, at, at + inserted) }
    }
    return normalizeSpans(result, newLength)
}

/** Finds what changed between [old] and [new] text and moves the spans accordingly. */
internal fun adjustSpans(
    spans: List<NoteSpan>, old: String, new: String, newSelection: TextRange, typing: Set<NoteStyle>
): List<NoteSpan> {
    if (old == new) return spans
    // Plain typing / pasting: the inserted text ends at the cursor (avoids guessing for "aa" -> "aaa")
    val k = new.length - old.length
    val c = newSelection.end
    if (k > 0 && newSelection.collapsed && c - k >= 0 && c <= new.length &&
        new.regionMatches(0, old, 0, c - k) && new.regionMatches(c, old, c - k, old.length - (c - k))
    ) {
        return applyEdit(spans, c - k, 0, k, typing, new.length)
    }
    val min = minOf(old.length, new.length)
    var p = 0
    while (p < min && old[p] == new[p]) p++
    var s = 0
    while (s < min - p && old[old.length - 1 - s] == new[new.length - 1 - s]) s++
    return applyEdit(spans, p, old.length - s - p, new.length - s - p, typing, new.length)
}

/* ------------------------------------------------------------------------------------------ */
/* Lists                                                                                      */
/* ------------------------------------------------------------------------------------------ */

data class RichValue(val value: TextFieldValue, val spans: List<NoteSpan>)

private fun lineStartOf(text: String, pos: Int): Int = text.lastIndexOf('\n', (pos - 1).coerceAtLeast(-1)).let { it + 1 }

/** Adds a bullet to every selected line, or removes them when all of the lines already have one. */
internal fun toggleBullets(v: RichValue): RichValue {
    var text = v.value.text
    var spans = v.spans
    var selStart = v.value.selection.min
    var selEnd = v.value.selection.max
    val starts = mutableListOf<Int>()
    var ls = lineStartOf(text, selStart)
    while (true) {
        starts += ls
        val next = text.indexOf('\n', ls)
        if (next == -1 || next >= selEnd) break
        ls = next + 1
    }
    val remove = starts.all { text.startsWith(BULLET, it) }
    for (st in starts.asReversed()) {
        if (remove) {
            text = text.removeRange(st, st + BULLET.length)
            spans = applyEdit(spans, st, BULLET.length, 0, emptySet(), text.length)
            if (selStart > st) selStart = maxOf(st, selStart - BULLET.length)
            if (selEnd > st) selEnd = maxOf(st, selEnd - BULLET.length)
        } else if (!text.startsWith(BULLET, st)) {
            text = text.substring(0, st) + BULLET + text.substring(st)
            spans = applyEdit(spans, st, 0, BULLET.length, emptySet(), text.length)
            if (selStart >= st) selStart += BULLET.length
            if (selEnd >= st) selEnd += BULLET.length
        }
    }
    return RichValue(v.value.copy(text = text, selection = TextRange(selStart, selEnd), composition = null), spans)
}

/**
 * After Enter on a bulleted line the new line gets a bullet too; Enter on an empty bulleted line
 * ends the list instead. Returns null when nothing has to change.
 */
internal fun continueBullets(old: String, v: RichValue): RichValue? {
    val text = v.value.text
    val c = v.value.selection.end
    if (!v.value.selection.collapsed || text.length != old.length + 1 || c < 1 || text[c - 1] != '\n') return null
    val prevStart = lineStartOf(text, c - 1)
    if (!text.startsWith(BULLET, prevStart)) return null
    return if (c - 1 - prevStart == BULLET.length) {
        // empty bullet: remove it together with the new line break
        val newText = text.removeRange(prevStart, c)
        RichValue(
            TextFieldValue(newText, TextRange(prevStart)),
            applyEdit(v.spans, prevStart, c - prevStart, 0, emptySet(), newText.length)
        )
    } else {
        val newText = text.substring(0, c) + BULLET + text.substring(c)
        RichValue(
            TextFieldValue(newText, TextRange(c + BULLET.length)),
            applyEdit(v.spans, c, 0, BULLET.length, emptySet(), newText.length)
        )
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Rendering                                                                                  */
/* ------------------------------------------------------------------------------------------ */

internal val HighlightColor = Color(0xFFFFC94D).copy(alpha = 0.45f)

/** The note text with its formatting applied (and clickable links when [linkColor] is set). */
internal fun richText(text: String, spans: List<NoteSpan>, linkColor: Color? = null): AnnotatedString =
    buildAnnotatedString {
        append(text)
        val valid = normalizeSpans(spans, text.length)
        // Split into segments so overlapping underline + strike-through combine correctly
        val bounds = (valid.flatMap { listOf(it.start, it.end) }).toSortedSet().toList()
        for (i in 0 until bounds.size - 1) {
            val a = bounds[i]
            val b = bounds[i + 1]
            val styles = valid.filter { it.start <= a && it.end >= b }.map { it.style }.toSet()
            if (styles.isEmpty()) continue
            val decorations = listOfNotNull(
                TextDecoration.Underline.takeIf { NoteStyle.UNDERLINE in styles },
                TextDecoration.LineThrough.takeIf { NoteStyle.STRIKE in styles },
            )
            addStyle(
                SpanStyle(
                    fontWeight = if (NoteStyle.BOLD in styles) FontWeight.Bold else null,
                    fontStyle = if (NoteStyle.ITALIC in styles) FontStyle.Italic else null,
                    textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
                    background = if (NoteStyle.HIGHLIGHT in styles) HighlightColor else Color.Unspecified,
                ),
                a, b
            )
        }
        if (linkColor != null) {
            val matcher = Patterns.WEB_URL.matcher(text)
            val style = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
            while (matcher.find()) {
                val raw = matcher.group() ?: continue
                addLink(LinkAnnotation.Url(normalizeUrl(raw), style), matcher.start(), matcher.end())
            }
        }
    }

/** Shows the formatting inside the text field; characters are not changed, so offsets map 1:1. */
internal class RichTransformation(private val spans: List<NoteSpan>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(richText(text.text, spans), OffsetMapping.Identity)
}

/* ------------------------------------------------------------------------------------------ */
/* Toolbar                                                                                    */
/* ------------------------------------------------------------------------------------------ */

@Composable
internal fun FormatToolbar(
    isActive: (NoteStyle) -> Boolean,
    bulletsActive: Boolean,
    onToggle: (NoteStyle) -> Unit,
    onBullets: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            FormatButton(Icons.Default.FormatBold, stringResource(R.string.format_bold), isActive(NoteStyle.BOLD)) { onToggle(NoteStyle.BOLD) }
            FormatButton(Icons.Default.FormatItalic, stringResource(R.string.format_italic), isActive(NoteStyle.ITALIC)) { onToggle(NoteStyle.ITALIC) }
            FormatButton(Icons.Default.FormatUnderlined, stringResource(R.string.format_underline), isActive(NoteStyle.UNDERLINE)) { onToggle(NoteStyle.UNDERLINE) }
            FormatButton(Icons.Default.FormatStrikethrough, stringResource(R.string.format_strike), isActive(NoteStyle.STRIKE)) { onToggle(NoteStyle.STRIKE) }
            FormatButton(Icons.Default.BorderColor, stringResource(R.string.format_highlight), isActive(NoteStyle.HIGHLIGHT)) { onToggle(NoteStyle.HIGHLIGHT) }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
            FormatButton(Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(R.string.format_bullets), bulletsActive, onBullets)
            FormatButton(Icons.Default.FormatClear, stringResource(R.string.format_clear), false, onClear)
        }
    }
}

@Composable
private fun FormatButton(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) primary.copy(alpha = 0.16f) else Color.Transparent)
    ) {
        Icon(icon, description, tint = if (active) primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
