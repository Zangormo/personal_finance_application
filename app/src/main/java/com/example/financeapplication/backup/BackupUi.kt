package com.example.financeapplication.backup

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.financeapplication.R
import com.example.financeapplication.ui.components.AppCard
import com.example.financeapplication.utils.Dates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Settings card: export all data to a file / share it, or import a backup. */
@Composable
fun BackupCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            exporting = true
            try {
                AppBackup.exportTo(context, uri)
                Toast.makeText(context, context.getString(R.string.backup_saved), Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.backup_export_failed), Toast.LENGTH_LONG).show()
            } finally {
                exporting = false
            }
        }
    }

    fun share() = scope.launch {
        exporting = true
        try {
            val uri = AppBackup.exportForSharing(context)
            val send = Intent(Intent.ACTION_SEND)
                .setType("application/octet-stream")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, context.getString(R.string.backup_share)))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.backup_export_failed), Toast.LENGTH_LONG).show()
        } finally {
            exporting = false
        }
    }

    val startImport = rememberBackupImport(onImported = {
        Toast.makeText(context, context.getString(R.string.backup_imported), Toast.LENGTH_SHORT).show()
    })

    AppCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SyncAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.backup_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                FilledTonalButton(
                    onClick = { exportMenu = true },
                    enabled = !exporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (exporting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Upload, null, Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.backup_export))
                }
                DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.backup_save_to_file)) },
                        leadingIcon = { Icon(Icons.Default.Save, null) },
                        onClick = { exportMenu = false; saveLauncher.launch(AppBackup.suggestedFileName()) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.backup_share)) },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = { exportMenu = false; share() }
                    )
                }
            }
            FilledTonalButton(onClick = startImport, enabled = !exporting, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.backup_import))
            }
        }
    }
}

/**
 * Picking a backup file, showing what it contains, asking for confirmation and importing it.
 * Returns the function that starts the flow; the dialogs are shown by this composable.
 */
@Composable
fun rememberBackupImport(onImported: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf<PreparedImport?>(null) }
    var error by remember { mutableStateOf<BackupException.Reason?>(null) }
    var failed by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            reading = true
            try {
                prepared = AppBackup.prepare(context, uri)
            } catch (e: BackupException) {
                error = e.reason
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = true
            } finally {
                reading = false
            }
        }
    }

    if (reading || applying) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(if (applying) R.string.backup_importing else R.string.backup_reading))
                }
            }
        )
    }

    prepared?.takeIf { !applying }?.let { p ->
        val s = p.summary
        val lines = listOfNotNull(
            stringResource(R.string.backup_count_spendings, s.spendings).takeIf { s.spendings > 0 },
            stringResource(R.string.backup_count_incomes, s.incomes).takeIf { s.incomes > 0 },
            stringResource(R.string.backup_count_notes, s.notes).takeIf { s.notes > 0 },
            stringResource(R.string.backup_count_tasks, s.tasks, s.taskCategories).takeIf { s.tasks > 0 || s.taskCategories > 0 },
            stringResource(R.string.backup_count_debts, s.debts).takeIf { s.debts > 0 },
            stringResource(R.string.backup_count_subscriptions, s.subscriptions).takeIf { s.subscriptions > 0 },
            stringResource(R.string.backup_count_planned, s.plannedItems).takeIf { s.plannedItems > 0 },
            stringResource(R.string.backup_count_files, s.files).takeIf { s.files > 0 },
        )
        AlertDialog(
            onDismissRequest = { AppBackup.discard(context, p); prepared = null },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.backup_from, Dates.dateTime(s.createdAt), s.appVersion.ifBlank { "?" }),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (lines.isNotEmpty()) Text(lines.joinToString("\n"))
                    Text(stringResource(R.string.backup_confirm_text), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    applying = true
                    scope.launch {
                        try {
                            AppBackup.apply(context, p)
                            onImported()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            failed = true
                        } finally {
                            applying = false
                            prepared = null
                        }
                    }
                }) { Text(stringResource(R.string.backup_replace), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { AppBackup.discard(context, p); prepared = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (error != null || failed) {
        AlertDialog(
            onDismissRequest = { error = null; failed = false },
            title = { Text(stringResource(R.string.backup_error_title)) },
            text = {
                Text(
                    stringResource(
                        when (error) {
                            BackupException.Reason.NOT_A_BACKUP -> R.string.backup_error_not_backup
                            BackupException.Reason.NEWER_VERSION -> R.string.backup_error_newer
                            BackupException.Reason.CORRUPTED -> R.string.backup_error_corrupted
                            BackupException.Reason.UNSAFE_PATH -> R.string.backup_error_unsafe
                            null -> R.string.backup_error_generic
                        }
                    )
                )
            },
            confirmButton = { TextButton(onClick = { error = null; failed = false }) { Text(stringResource(R.string.close)) } }
        )
    }

    // Backup files have their own extension, so their type is unknown to Android: allow any file
    return { picker.launch(arrayOf("*/*")) }
}
