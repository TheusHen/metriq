package com.metriq.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.metriq.app.ui.common.FoodPackUrls
import com.metriq.app.ui.common.SubScreen
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.app.ui.theme.body
import com.metriq.app.ui.theme.caption
import com.metriq.data.FoodPackEntry
import com.metriq.data.FoodPackManager
import com.metriq.data.InstalledFoodPack
import com.metriq.data.PackInstallResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Optional food packs. The base pack bundled with the app still works offline; packs
 * are downloaded from the release tag for the current month unless overridden.
 */
@Composable
fun FoodPackScreen(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var baseUrlText by remember { mutableStateOf(baseUrl) }
    var installed by remember { mutableStateOf<InstalledFoodPack?>(null) }
    var packs by remember { mutableStateOf<List<FoodPackEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val manager = remember(baseUrlText) {
        FoodPackManager(
            AppGraph.store.food,
            AppGraph.store.settings,
            FoodPackUrls.resolveBaseUrl(baseUrlText),
        )
    }

    LaunchedEffect(Unit) {
        installed = withContext(Dispatchers.IO) { manager.installedPack() }
    }
    // Persist after typing pauses, like the Hevy key on Settings.
    LaunchedEffect(baseUrlText) {
        if (baseUrlText == baseUrl) return@LaunchedEffect
        delay(500)
        onBaseUrlChange(baseUrlText)
    }

    fun checkForPacks() {
        loading = true
        message = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { manager.fetchManifest().packs }
            }
            outcome.onSuccess { packs = it }
                .onFailure { message = context.getString(R.string.packs_error, it.message.orEmpty()) }
            loading = false
        }
    }

    fun installPack(packId: String) {
        loading = true
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { manager.install(packId) }
            when (result) {
                is PackInstallResult.Success -> {
                    message = context.getString(R.string.packs_install_ok, result.inserted, result.total)
                    installed = withContext(Dispatchers.IO) { manager.installedPack() }
                }
                is PackInstallResult.Failure -> message = context.getString(R.string.packs_error, result.reason)
            }
            loading = false
        }
    }

    SubScreen(title = stringResource(R.string.packs_title), onBack = onBack) {
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.packs_intro),
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink2,
        )
        Spacer(Modifier.height(18.dp))

        SectionHeader(title = stringResource(R.string.packs_installed))
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth()) {
            val current = installed
            if (current == null) {
                Text(
                    stringResource(R.string.packs_none),
                    style = MaterialTheme.typography.body,
                    color = MetriqColors.ink2,
                )
            } else {
                Text(
                    stringResource(
                        R.string.packs_installed_detail,
                        current.id,
                        current.version,
                        current.file.orEmpty(),
                    ),
                    style = MaterialTheme.typography.body,
                    color = MetriqColors.ink,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionHeader(title = stringResource(R.string.packs_base_url))
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = baseUrlText,
                onValueChange = { baseUrlText = it },
                label = { Text(stringResource(R.string.packs_base_url)) },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MetriqColors.ink,
                    unfocusedTextColor = MetriqColors.ink,
                    cursorColor = MetriqColors.accent,
                    focusedBorderColor = MetriqColors.accent,
                    unfocusedBorderColor = MetriqColors.border,
                    focusedLabelColor = MetriqColors.ink2,
                    unfocusedLabelColor = MetriqColors.ink3,
                    focusedContainerColor = MetriqColors.surface2,
                    unfocusedContainerColor = MetriqColors.surface1,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.packs_base_url_info),
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
            )
        }

        Spacer(Modifier.height(14.dp))
        MetriqButton(
            text = stringResource(R.string.packs_check),
            onClick = ::checkForPacks,
            modifier = Modifier.fillMaxWidth(),
            primary = true,
        )
        if (loading) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MetriqColors.accent,
                trackColor = MetriqColors.surface2,
            )
        }

        if (packs.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SectionHeader(title = stringResource(R.string.packs_available))
            Spacer(Modifier.height(8.dp))
            Panel(Modifier.fillMaxWidth()) {
                packs.forEach { entry ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(
                                R.string.packs_entry,
                                entry.id,
                                entry.count?.toString() ?: "—",
                                entry.size?.let {
                                    String.format(Locale.getDefault(), "%,d", it) + " B"
                                } ?: "—",
                            ),
                            style = MaterialTheme.typography.body,
                            color = MetriqColors.ink,
                        )
                        val isInstalled = installed?.id == entry.id
                        MetriqButton(
                            text = if (isInstalled) {
                                stringResource(R.string.packs_installed_badge)
                            } else {
                                stringResource(R.string.packs_install)
                            },
                            onClick = { installPack(entry.id) },
                            modifier = Modifier.fillMaxWidth(),
                            primary = !isInstalled,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        } else if (!loading && message == null) {
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.packs_empty),
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
            )
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
}
