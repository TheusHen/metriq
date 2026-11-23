package com.metriq.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metriq.app.AppGraph
import com.metriq.app.R
import com.metriq.app.ui.common.SubScreen
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.app.ui.theme.body
import com.metriq.app.ui.theme.caption
import com.metriq.data.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-device JSON backup: export and restore stay off the main thread. */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var exported by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var restored by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var pendingJson by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val json = AppGraph.store.backup.exportJson()
                    val rowCounts = org.json.JSONObject(json).optJSONObject("rowCounts")
                    val counts = rowCounts?.keys()?.asSequence()
                        ?.map { it to rowCounts.optInt(it) }
                        ?.toList().orEmpty()
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Cannot open the selected file")
                    counts
                }
            }
            outcome.onSuccess { counts ->
                exported = counts.sortedBy { it.first }
                message = context.getString(
                    R.string.backup_export_ok,
                    counts.sumOf { it.second },
                    counts.size,
                )
            }.onFailure { error ->
                message = context.getString(R.string.backup_error, error.message.orEmpty())
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("Cannot read the selected file")
                }
            }
            outcome.onSuccess { pendingJson = it }
                .onFailure { message = context.getString(R.string.backup_error, it.message.orEmpty()) }
        }
    }

    SubScreen(title = stringResource(R.string.backup_title), onBack = onBack) {
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.backup_intro),
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink2,
        )
        Spacer(Modifier.height(18.dp))
        SectionHeader(title = stringResource(R.string.backup_export))
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth()) {
            MetriqButton(
                text = stringResource(R.string.backup_export),
                onClick = { exportLauncher.launch("metriq-backup.json") },
                modifier = Modifier.fillMaxWidth(),
                primary = true,
            )
            CountList(exported)
        }
        Spacer(Modifier.height(18.dp))
        SectionHeader(title = stringResource(R.string.backup_restore))
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth()) {
            MetriqButton(
                text = stringResource(R.string.backup_pick_json),
                onClick = { importLauncher.launch("application/json") },
                modifier = Modifier.fillMaxWidth(),
                primary = false,
            )
            CountList(restored)
        }
        if (message != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                message.orEmpty(),
                style = MaterialTheme.typography.body,
                color = MetriqColors.ink,
            )
        }
    }

    if (pendingJson != null) {
        AlertDialog(
            onDismissRequest = { pendingJson = null },
            title = { Text(stringResource(R.string.backup_restore_warn_title)) },
            text = { Text(stringResource(R.string.backup_restore_warn_body)) },
            confirmButton = {
                TextButton(onClick = {
                    val json = pendingJson.orEmpty()
                    pendingJson = null
                    scope.launch {
                        when (val result = withContext(Dispatchers.IO) {
                            AppGraph.store.backup.importJson(json)
                        }) {
                            is ImportResult.Success -> {
                                restored = result.rowCounts.toList().sortedBy { it.first }
                                message = context.getString(
                                    R.string.backup_restore_ok,
                                    result.rowCounts.values.sum(),
                                    result.rowCounts.size,
                                )
                            }
                            is ImportResult.Failure ->
                                message = context.getString(R.string.backup_error, result.reason)
                        }
                    }
                }) { Text(stringResource(R.string.backup_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingJson = null }) {
                    Text(stringResource(R.string.backup_cancel))
                }
            },
        )
    }
}

@Composable
private fun CountList(counts: List<Pair<String, Int>>) {
    counts.forEach { (table, rows) ->
        Spacer(Modifier.height(6.dp))
        Text(
            "$table: $rows",
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink2,
        )
    }
}
