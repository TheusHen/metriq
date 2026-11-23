package com.metriq.app.ui.aimodels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.metriq.app.ai.BenchmarkResult
import com.metriq.app.ai.DeviceProfiler
import com.metriq.app.ai.ModelBenchmark
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
import java.util.Locale

private data class Profile(val ramMb: Int, val freeMb: Int, val score: String)

/**
 * Optional on-device models: storage budget, real device signals, reference-tier catalog,
 * user-supplied downloads and locally measured benchmarks. Nothing here claims a model is
 * running unless the user has both installed and explicitly selected one.
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
    val selectedId = value(AppSettings.AI_MODEL_ID)

    var installed by remember { mutableStateOf<List<String>>(emptyList()) }
    var profile by remember { mutableStateOf<Profile?>(null) }
    var results by remember { mutableStateOf<List<BenchmarkResult>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
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
            score = DeviceProfiler.score(context),
        )
        results = ModelBenchmark.run(context)
    }

    fun runBenchmark() {
        if (running) return
        running = true
        notice = null
        scope.launch {
            results = ModelBenchmark.run(context)
            running = false
        }
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
                        notice = "'$id' instalado. Não é o motor ativo até o selecionares."
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
        if (selectedId == id) onSettingChange(AppSettings.AI_MODEL_ID, "")
        refreshInstalled()
        notice = "Removed '$id'."
    }

    fun selectModel(id: String) {
        onSettingChange(AppSettings.AI_MODEL_ID, id)
        notice = "'$id' selecionado. Volta a executar o teste para atualizar o estado do motor ativo."
        scope.launch { results = ModelBenchmark.run(context) }
    }

    SubScreen(title = "AI models", onBack = onBack) {
        Text(
            text = "Optional models · everything stays on this device",
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
                    "Off — deterministic engine only, no model files."
                } else {
                    "Até $budgetMb MB reservados para ficheiros de modelos opcionais que descarregues."
                },
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // ------------------------------------------------------------------ Device profile
        Spacer(Modifier.height(18.dp))
        SectionHeader(title = "Perfil do dispositivo")
        Spacer(Modifier.height(8.dp))
        Panel {
            Text("MEASURED SIGNALS", style = MaterialTheme.typography.label, color = MetriqColors.ink3)
            Spacer(Modifier.height(10.dp))
            MetricRow("Memory class", profile?.ramMb?.let { "$it MB" } ?: "—")
            Spacer(Modifier.height(8.dp))
            MetricRow("Free storage", profile?.freeMb?.let { "$it MB" } ?: "—")
            Spacer(Modifier.height(8.dp))
            MetricRow("Capability score", profile?.score ?: "—")
        }

        // ------------------------------------------------------------------ Recommendation
        Spacer(Modifier.height(18.dp))
        SectionHeader(title = "Recommendation")
        Spacer(Modifier.height(8.dp))
        Panel {
            val availableForRec = if (budgetMb > 0) {
                minOf(budgetMb, profile?.freeMb ?: budgetMb)
            } else {
                0
            }
            val recommendation = profile?.let {
                ModelCatalog.recommend(availableForRec, it.ramMb, "TEXT")
            }
            when {
                budgetMb == 0 -> Text(
                    text = "Define acima um orçamento de armazenamento para ver que nível se adapta a este dispositivo.",
                    style = MaterialTheme.typography.body,
                    color = MetriqColors.ink2,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                recommendation == null -> Text(
                    text = "No reference tier fits this device's memory or free storage. " +
                        "O motor determinístico continua ativo.",
                    style = MaterialTheme.typography.body,
                    color = MetriqColors.ink2,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                else -> {
                    Text(
                        text = recommendation.name,
                        style = MaterialTheme.typography.bodyStrong,
                        color = MetriqColors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${recommendation.sizeMb} MB · ${recommendation.kind}",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = recommendation.description,
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink3,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // ------------------------------------------------------------------ Catalog
        Spacer(Modifier.height(18.dp))
        SectionHeader(title = "Model catalog")
        Spacer(Modifier.height(8.dp))
        for (spec in ModelCatalog.all) {
            CatalogItem(
                spec = spec,
                installed = spec.id in installed,
                selected = spec.id == selectedId && spec.id in installed,
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
                onSelect = { selectModel(spec.id) },
            )
            Spacer(Modifier.height(12.dp))
        }

        // ------------------------------------------------------------------ Benchmark
        Spacer(Modifier.height(10.dp))
        SectionHeader(
            title = "Benchmark",
            action = if (running) "Running…" else "Run benchmark",
            onAction = { runBenchmark() },
        )
        Spacer(Modifier.height(8.dp))
        Panel {
            if (results.isEmpty() && !running) {
                Text(
                    text = "No measurements yet.",
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink3,
                    maxLines = 1,
                )
            }
            results.forEachIndexed { index, result ->
                if (index > 0) {
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MetriqColors.border))
                    Spacer(Modifier.height(10.dp))
                }
                ResultRow(result)
            }
        }

        // ------------------------------------------------------------------ Privacy note
        Spacer(Modifier.height(18.dp))
        Panel {
            Text("PRIVACIDADE", style = MaterialTheme.typography.label, color = MetriqColors.ink3)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Os modelos são downloads opcionais que indicas tu — nenhum binário vem com " +
                    "o Metriq e nenhum URL é incluído. Tudo corre no dispositivo; nada é " +
                    "enviado. O motor determinístico está ativo por predefinição. Um modelo só " +
                    "se torna ativo depois de o instalares e selecionares, e o Metriq não " +
                    "afirma precisão ou velocidade que não consiga medir.",
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
    selected: Boolean,
    downloading: Boolean,
    urlText: String,
    progress: Float?,
    onUrlChange: (String) -> Unit,
    onInstall: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onSelect: () -> Unit,
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
                    text = if (selected) "Active" else "Installed",
                    style = MaterialTheme.typography.caption,
                    color = if (selected) MetriqColors.accent else MetriqColors.ink3,
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetriqButton(
                    text = "Remove",
                    onClick = onRemove,
                    modifier = Modifier.weight(1f),
                    primary = false,
                )
                if (!selected) {
                    MetriqButton(
                        text = "Use",
                        onClick = onSelect,
                        modifier = Modifier.weight(1f),
                        primary = true,
                    )
                }
            }
        } else if (!downloading) {
            MetriqButton(
                text = "Install",
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                text = "Descarrega o ficheiro que indicas para o armazenamento privado da app. Nenhum URL é incluído.",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ResultRow(result: BenchmarkResult) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                text = result.label,
                style = MaterialTheme.typography.bodyStrong,
                color = MetriqColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = result.note,
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val big: String? = when {
            result.label == "Perfil do dispositivo" -> result.unit
            result.unit.isBlank() -> null
            else -> formatScore(result.score)
        }
        if (big != null) {
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = big,
                    style = MaterialTheme.typography.number24,
                    color = MetriqColors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (result.label != "Perfil do dispositivo" && result.unit.isNotBlank()) {
                    Text(
                        text = result.unit,
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink2,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun formatScore(value: Double): String = when {
    value >= 100.0 -> String.format(Locale.US, "%.0f", value)
    value >= 10.0 -> String.format(Locale.US, "%.1f", value)
    else -> String.format(Locale.US, "%.2f", value)
}
