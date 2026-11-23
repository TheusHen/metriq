package com.metriq.app.ui.aimodels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metriq.app.AppSettings
import com.metriq.app.ai.DeviceProfiler
import com.metriq.app.ai.ModelCatalog
import com.metriq.app.ai.ModelManager
import com.metriq.app.ai.ModelSpec
import com.metriq.app.ui.common.SubScreen
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.components.Segmented
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.app.ui.theme.body
import com.metriq.app.ui.theme.bodyStrong
import com.metriq.app.ui.theme.caption
import com.metriq.app.ui.theme.label
import com.metriq.app.ui.theme.number24
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class Profile(val ramMb: Int, val freeMb: Int)

/**
 * Model storage manager. This screen stores optional, user-supplied files; it does not run
 * them. Photo recognition in Metriq uses on-device ML Kit Image Labeling. Download, budget
 * and cancel behaviour is unchanged — only the (previously misleading) inference,
 * benchmark and accuracy framing has been removed.
 */
@Composable
fun AiModelsScreen(
    settings: Map<String, String>,
    onSettingChange: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { ModelManager(context) }

    fun value(key: String): String = settings[key] ?: AppSettings.defaults[key].orEmpty()

    val budgetRaw = value(AppSettings.AI_STORAGE_BUDGET_MB)
    val budgetMb = budgetRaw.toIntOrNull() ?: 0

    var installed by remember { mutableStateOf<List<String>>(emptyList()) }
    var profile by remember { mutableStateOf<Profile?>(null) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var urlText by remember { mutableStateOf(value(AppSettings.AI_MODEL_URL)) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    fun refreshInstalled() {
        installed = manager.installed()
    }

    LaunchedEffect(Unit) {
        refreshInstalled()
        profile = Profile(
            ramMb = DeviceProfiler.ramMb(context),
            freeMb = DeviceProfiler.freeMb(context),
        )
    }

    fun startDownload(id: String) {
        downloadingId = id
        urlText = value(AppSettings.AI_MODEL_URL)
        progress = null
        notice = null
    }

    fun commitDownload(id: String) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            try {
                progress = 0f
                val outcome = manager.download(id, urlText, budgetMb) { progress = it }
                outcome.fold(
                    onSuccess = {
                        refreshInstalled()
                        notice = "'$id' guardado neste dispositivo. O ficheiro não é executado."
                        downloadingId = null
                        progress = null
                    },
                    onFailure = {
                        notice = it.message ?: "Falha no download."
                        progress = null
                    },
                )
            } finally {
                downloadJob = null
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        downloadingId = null
        progress = null
    }

    fun removeModel(id: String) {
        manager.remove(id)
        refreshInstalled()
        notice = "'$id' removido."
    }

    SubScreen(title = "AI models", onBack = onBack) {
        Text(
            text = "Model storage manager · everything stays on this device",
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (notice != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = notice.orEmpty(),
                style = MaterialTheme.typography.body,
                color = MetriqColors.accent,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // ------------------------------------------------------------------ Honest status
        Spacer(Modifier.height(16.dp))
        Panel {
            Text("WHAT RUNS", style = MaterialTheme.typography.label, color = MetriqColors.ink3)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Photo recognition runs on-device with ML Kit Image Labeling. " +
                    "Metriq does not include a model inference runtime.",
                style = MaterialTheme.typography.body,
                color = MetriqColors.ink2,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Downloaded files are only stored here. They are not loaded or executed, " +
                    "and no speed or accuracy is claimed for them.",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
            )
        }

        // ------------------------------------------------------------------ Storage budget
        Spacer(Modifier.height(20.dp))
        SectionHeader(title = "Orçamento de armazenamento")
        Spacer(Modifier.height(8.dp))
        Panel {
            val labels = listOf("Off", "250", "500", "1,000+")
            val values = listOf("0", "250", "500", "1000")
            val selectedIndex = values.indexOf(budgetRaw).coerceAtLeast(0)
            Segmented(
                options = labels,
                selectedIndex = selectedIndex,
                onSelect = { onSettingChange(AppSettings.AI_STORAGE_BUDGET_MB, values[it]) },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (budgetMb == 0) {
                    "Off — no model files are downloaded."
                } else {
                    "Até $budgetMb MB reservados para ficheiros que descarregues. Nada é executado."
                },
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // ------------------------------------------------------------------ Device facts
        Spacer(Modifier.height(18.dp))
        SectionHeader(title = "Device storage")
        Spacer(Modifier.height(8.dp))
        Panel {
            Text("MEASURED SIGNALS", style = MaterialTheme.typography.label, color = MetriqColors.ink3)
            Spacer(Modifier.height(10.dp))
            MetricRow("Memory class", profile?.ramMb?.let { "$it MB" } ?: "—")
            Spacer(Modifier.height(8.dp))
            MetricRow("Free storage", profile?.freeMb?.let { "$it MB" } ?: "—")
        }

        // ------------------------------------------------------------------ Catalog
        Spacer(Modifier.height(18.dp))
        SectionHeader(title = "Reference tiers (files only)")
        Spacer(Modifier.height(8.dp))
        for (spec in ModelCatalog.all) {
            CatalogItem(
                spec = spec,
                installed = spec.id in installed,
                downloading = downloadingId == spec.id,
                urlText = urlText,
                progress = progress,
                onUrlChange = { text ->
                    urlText = text
                    onSettingChange(AppSettings.AI_MODEL_URL, text)
                },
                onInstall = { startDownload(spec.id) },
                onDownload = { commitDownload(spec.id) },
                onCancel = { cancelDownload() },
                onRemove = { removeModel(spec.id) },
            )
            Spacer(Modifier.height(12.dp))
        }

        // ------------------------------------------------------------------ Privacy note
        Spacer(Modifier.height(18.dp))
        Panel {
            Text("PRIVACIDADE", style = MaterialTheme.typography.label, color = MetriqColors.ink3)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Os ficheiros são downloads opcionais que indicas tu — nenhum binário vem " +
                    "com o Metriq e nenhum URL é incluído. Tudo fica no dispositivo; nada é " +
                    "enviado. Guardar um ficheiro não o torna ativo: o Metriq não o executa e " +
                    "não reivindica velocidade ou precisão.",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink2,
            )
        }
    }
}

@Composable
private fun MetricRow(label: String, number: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = number,
            style = MaterialTheme.typography.number24,
            color = MetriqColors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CatalogItem(
    spec: ModelSpec,
    installed: Boolean,
    downloading: Boolean,
    urlText: String,
    progress: Float?,
    onUrlChange: (String) -> Unit,
    onInstall: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    Panel {
        Text(
            text = spec.name,
            style = MaterialTheme.typography.bodyStrong,
            color = MetriqColors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(MetriqColors.surface2)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = spec.kind,
                    style = MaterialTheme.typography.label,
                    color = MetriqColors.trend,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${spec.sizeMb} MB",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink2,
                maxLines = 1,
            )
            if (installed) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Stored (not executed)",
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink3,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = spec.description,
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink3,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))

        if (installed) {
            MetriqButton(
                text = "Remove",
                onClick = onRemove,
                modifier = Modifier.fillMaxWidth(),
                primary = false,
            )
        } else if (!downloading) {
            MetriqButton(
                text = "Store file",
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth(),
                primary = true,
            )
        }

        if (downloading) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = urlText,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model file URL (https://…)") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MetriqColors.ink,
                    unfocusedTextColor = MetriqColors.ink,
                    cursorColor = MetriqColors.accent,
                    focusedBorderColor = MetriqColors.accent,
                    unfocusedBorderColor = MetriqColors.border,
                    focusedLabelColor = MetriqColors.ink2,
                    unfocusedLabelColor = MetriqColors.ink3,
                    focusedContainerColor = MetriqColors.surface2,
                    unfocusedContainerColor = MetriqColors.surface2,
                ),
            )
            Spacer(Modifier.height(10.dp))
            val fraction = (progress ?: 0f).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MetriqColors.surface2),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MetriqColors.accent),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                MetriqButton(
                    text = "Download",
                    onClick = onDownload,
                    modifier = Modifier.weight(1f),
                    primary = true,
                )
                MetriqButton(
                    text = "Cancelar",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    primary = false,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Descarrega o ficheiro que indicas para o armazenamento privado da app. " +
                    "Nenhum URL é incluído e o ficheiro não é executado.",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
