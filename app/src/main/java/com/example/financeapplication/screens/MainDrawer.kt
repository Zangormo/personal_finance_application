package com.example.financeapplication.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import com.example.financeapplication.ui.components.AppCard
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.financeapplication.datastores.Note
import com.example.financeapplication.datastores.NotesDataStore
import com.example.financeapplication.ui.components.EmptyState
import com.example.financeapplication.ui.components.FieldShape
import com.example.financeapplication.ui.components.ScreenHeader
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

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
            drawerState.close()
            onOpenSettings()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Text(
                    "Menu",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 16.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Home") },
                    icon = { Icon(Icons.Default.Home, null) },
                    selected = true,
                    onClick = { scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Spacer(Modifier.height(4.dp))
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    icon = { Icon(Icons.Default.Settings, null) },
                    selected = false,
                    onClick = ::onSettingsTap,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        HomeScreen(
            onNavigate = onNavigate,
            onOpenMenu = { scope.launch { drawerState.open() } }
        )
    }

    secretNotes?.let { list ->
        AlertDialog(
            onDismissRequest = { secretNotes = null },
            icon = { Icon(Icons.Default.Lock, null) },
            title = { Text("Secret notes") },
            text = {
                if (list.isEmpty()) {
                    Text("There are no secret notes.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(list, key = { it.id }) { note ->
                            Text(
                                note.title.ifBlank { "Untitled" },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(FieldShape)
                                    .clickable {
                                        secretNotes = null
                                        scope.launch { drawerState.close() }
                                        onOpenNote(note.id)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { secretNotes = null }) { Text("Close") } }
        )
    }
}

private const val DEVELOPERS_PAGE = "https://zangormo.github.io/"

@Composable
fun SettingsScreen(onBackPress: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings", onBack = onBackPress)
        AppCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DEVELOPERS_PAGE)))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Developers page", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "zangormo.github.io",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
