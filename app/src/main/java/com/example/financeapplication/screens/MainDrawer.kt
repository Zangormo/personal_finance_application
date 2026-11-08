package com.example.financeapplication.screens

import android.app.LocaleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.LocaleList
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.financeapplication.utils.appVersionName
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.financeapplication.R
import com.example.financeapplication.datastores.Note
import com.example.financeapplication.datastores.NotesDataStore
import com.example.financeapplication.ui.components.AppCard
import com.example.financeapplication.ui.components.FieldShape
import com.example.financeapplication.ui.components.ScreenHeader
import com.example.financeapplication.ui.components.SegmentedSelector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Taps on "Settings" closer together than this count as one series (3 taps show the secret notes). */
private const val MULTI_TAP_WINDOW_MS = 350L

/** The home screen with the side menu (Home, Settings). */
@Composable
fun HomeWithDrawer(
    onNavigate: (HomeDestination) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNote: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var homeEditing by rememberSaveable { mutableStateOf(false) }

    // Own side menu instead of ModalNavigationDrawer: that one is drawn open for a moment when the
    // screen first appears. This one is not drawn at all while closed, so nothing can flash.
    val density = LocalDensity.current
    val drawerWidth = 300.dp
    val widthPx = with(density) { drawerWidth.toPx() }
    /** 0 = closed, 1 = fully open. */
    var progress by remember { mutableFloatStateOf(0f) }
    var animJob by remember { mutableStateOf<Job?>(null) }
    fun animateDrawer(target: Float) {
        animJob?.cancel()
        animJob = scope.launch {
            animate(progress, target, animationSpec = tween(240, easing = FastOutSlowInEasing)) { v, _ -> progress = v }
        }
    }
    fun closeInstantly() {
        animJob?.cancel()
        progress = 0f
    }
    val dragState = rememberDraggableState { dx ->
        animJob?.cancel()
        progress = (progress + dx / widthPx).coerceIn(0f, 1f)
    }
    fun settle(velocity: Float) = animateDrawer(
        when {
            velocity > 800f -> 1f
            velocity < -800f -> 0f
            progress > 0.5f -> 1f
            else -> 0f
        }
    )
    val drawerVisible = progress > 0f
    BackHandler(enabled = drawerVisible) { animateDrawer(0f) }

    var secretNotes by remember { mutableStateOf<List<Note>?>(null) }

    // A single tap opens Settings; it waits a moment to see whether more taps follow
    var taps by remember { mutableIntStateOf(0) }
    var tapJob by remember { mutableStateOf<Job?>(null) }
    fun onSettingsTap() {
        taps++
        tapJob?.cancel()
        if (taps >= 3) {
            taps = 0
            scope.launch {
                secretNotes = NotesDataStore.getNotes(context).first()
                    .filter { it.secret == true }
                    .sortedBy { it.title.lowercase() }
            }
            return
        }
        tapJob = scope.launch {
            delay(MULTI_TAP_WINDOW_MS)
            taps = 0
            closeInstantly()
            onOpenSettings()
        }
    }

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            onNavigate = onNavigate,
            onOpenMenu = { animateDrawer(1f) },
            editMode = homeEditing,
            onEditModeChange = { homeEditing = it }
        )

        // Swipe from the left edge opens the menu (below the header, so the menu button stays tappable;
        // off while tiles are being moved)
        if (!drawerVisible && !homeEditing) {
            Box(
                Modifier
                    .padding(top = 64.dp)
                    .fillMaxHeight()
                    .width(16.dp)
                    .draggable(dragState, Orientation.Horizontal, onDragStopped = { v -> settle(v) })
            )
        }

        if (drawerVisible) {
            // Scrim: tap or swipe left to close
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress }
                    .background(DrawerDefaults.scrimColor)
                    .pointerInput(Unit) { detectTapGestures { animateDrawer(0f) } }
                    .draggable(dragState, Orientation.Horizontal, onDragStopped = { v -> settle(v) })
            )
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(drawerWidth)
                    .graphicsLayer { translationX = (progress - 1f) * widthPx }
                    .draggable(dragState, Orientation.Horizontal, onDragStopped = { v -> settle(v) })
            ) {
                Column {
                    Text(
                        stringResource(R.string.menu),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 12.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.home)) },
                        icon = { Icon(Icons.Default.Home, null) },
                        selected = true,
                        onClick = { animateDrawer(0f) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.settings)) },
                        icon = { Icon(Icons.Default.Settings, null) },
                        selected = false,
                        onClick = ::onSettingsTap,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }

    secretNotes?.let { list ->
        AlertDialog(
            onDismissRequest = { secretNotes = null },
            icon = { Icon(Icons.Default.Lock, null) },
            title = { Text(stringResource(R.string.secret_notes)) },
            text = {
                if (list.isEmpty()) {
                    Text(stringResource(R.string.no_secret_notes))
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(list, key = { it.id }) { note ->
                            Text(
                                note.title.ifBlank { stringResource(R.string.untitled) },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(FieldShape)
                                    .clickable {
                                        secretNotes = null
                                        closeInstantly()
                                        onOpenNote(note.id)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { secretNotes = null }) { Text(stringResource(R.string.close)) } }
        )
    }
}

private const val DEVELOPERS_PAGE = "https://zangormo.github.io/"

/** Language tags offered in the settings; "" follows the phone's language. */
private val LANGUAGES = listOf("", "en", "ru")

private fun currentLanguage(context: Context): String {
    val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
    return if (locales.isEmpty) "" else locales[0].language
}

/** Per-app language (Android 13+). The activity is recreated with the new language by the system. */
private fun setLanguage(context: Context, tag: String) {
    context.getSystemService(LocaleManager::class.java).applicationLocales =
        if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
}

@Composable
fun SettingsScreen(onBackPress: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.settings), onBack = onBackPress)
        // Scrollable, with the version label at the bottom of the screen (or below the cards when
        // they do not fit): the content is at least as high as the screen, so the spacer can push it down
        BoxWithConstraints(Modifier.weight(1f)) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                com.example.financeapplication.backup.BackupCard()
                AppCard(Modifier.fillMaxWidth()) {
                    SettingsRowHeader(Icons.Default.Language, stringResource(R.string.language), null)
                    Spacer(Modifier.height(12.dp))
                    val selected = LANGUAGES.indexOf(currentLanguage(context)).coerceAtLeast(0)
                    SegmentedSelector(
                        // language names are always written in their own language
                        options = listOf(stringResource(R.string.language_system), "English", "Русский"),
                        selectedIndex = selected,
                        onSelect = { i -> if (i != selected) setLanguage(context, LANGUAGES[i]) }
                    )
                }
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DEVELOPERS_PAGE)))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, context.getString(R.string.no_browser), Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            SettingsRowHeader(Icons.Default.Code, stringResource(R.string.developers_page), "zangormo.github.io")
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                val version = remember { context.appVersionName() }
                Text(
                    stringResource(R.string.app_name) + if (version.isNotEmpty()) " · " + stringResource(R.string.version_label, version) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsRowHeader(icon: ImageVector, title: String, subtitle: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
