package com.metriq.app.ui.settings

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.metriq.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metriq.app.AppSettings
import com.metriq.app.util.HevyTimeFormatter
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.ScreenHeader
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.components.Segmented
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.app.ui.theme.Spacing
import com.metriq.app.ui.theme.bodyStrong
import com.metriq.app.ui.theme.h2
import com.metriq.app.ui.theme.caption
import com.metriq.app.ui.theme.label
import kotlinx.coroutines.delay

/**
 * Settings for units, local data, health integrations and analytics defaults.
 * Every option writes straight through [onSettingChange]; values live on-device only.
 */
@Composable
fun DataSettingsScreen(
    settings: Map<String, String>,
    onSettingChange: (String, String) -> Unit,
    onExport: () -> Unit,
    onSyncHealth: () -> Unit,
    onConnectHealth: () -> Unit,
    healthStatus: String,
    onBack: () -> Unit,
    onOpenAiModels: () -> Unit = {},
    onOpenIntegrations: () -> Unit = {},
    hevyConnected: Boolean = false,
    hevyAutoSync: Boolean = false,
    hevyLastSync: String? = null,
    hevyLastError: String? = null,
    hevyWorkoutCount: Int = 0,
    hevyApiKey: String = "",
    onHevyApiKeyChange: (String) -> Unit = {},
    onHevyConnect: () -> Unit = {},
    onHevySync: () -> Unit = {},
    onHevyDisconnect: () -> Unit = {},
    onHevyAutoSyncChange: (Boolean) -> Unit = {},
) {
    // Snapshot-backed reads: recomposition re-evaluates these when settings change.
    fun value(key: String): String = settings[key] ?: AppSettings.defaults[key].orEmpty()
    fun boolValue(key: String): Boolean = value(key).toBooleanStrictOrNull()
        ?: AppSettings.defaults[key]?.toBooleanStrictOrNull() ?: false

    // A settings-only write produces a DashboardState that is .equals() to the previous
    // one, so StateFlow skips emission and the parent may never recompose. Keep an
    // optimistic local copy of every switch and update it BEFORE forwarding the write.
    val switchKeys = listOf(
        AppSettings.REDUCED_MOTION,
        AppSettings.ADVANCED_METRICS,
        AppSettings.HEALTH_AUTO_SYNC,
        AppSettings.HEALTH_IMPORT_STEPS,
        AppSettings.HEALTH_IMPORT_WEIGHT,
        AppSettings.ADAPTIVE_MACROS,
    )
    val switchState = remember {
        mutableStateMapOf<String, Boolean>().apply {
            switchKeys.forEach { put(it, boolValue(it)) }
        }
    }
    var localHevyAutoSync by remember { mutableStateOf(hevyAutoSync) }

    // Re-seed only when an incoming value genuinely changes. This list is compared by
    // value, so an unrelated parent recomposition with the same settings cannot undo a
    // local toggle, while a real external change is still reflected.
    val incomingSwitches = switchKeys.map { value(it) }
    LaunchedEffect(incomingSwitches) {
        incomingSwitches.forEachIndexed { index, raw ->
            switchState[switchKeys[index]] = raw.toBooleanStrictOrNull() ?: false
        }
    }
    LaunchedEffect(hevyAutoSync) { localHevyAutoSync = hevyAutoSync }

    val reducedMotion = switchState[AppSettings.REDUCED_MOTION] ?: boolValue(AppSettings.REDUCED_MOTION)

    // Brief "Saved locally" confirmation after any change; no long animations.
    var savedRevision by remember { mutableStateOf(0) }
    var showSaved by remember { mutableStateOf(false) }
    LaunchedEffect(savedRevision) {
        if (savedRevision == 0) return@LaunchedEffect
        showSaved = true
        delay(1500)
        showSaved = false
    }
    val savedAlpha by animateFloatAsState(
        targetValue = if (showSaved) 1f else 0f,
        animationSpec = animationSpec(reducedMotion),
        label = "savedFeedback",
    )

    val change: (String, String) -> Unit = { key, newValue ->
        savedRevision++
        onSettingChange(key, newValue)
    }
    fun setSwitch(key: String, enabled: Boolean) {
        switchState[key] = enabled
        change(key, enabled.toString())
    }
    fun setHevyAutoSync(enabled: Boolean) {
        localHevyAutoSync = enabled
        onHevyAutoSyncChange(enabled)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MetriqColors.bg),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            ScreenHeader(
                title = stringResource(R.string.settings_title),
                subtitle = "Guardado neste dispositivo",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            ) {
                // Fixed-height slot keeps the caption from shifting layout when it appears.
                Box(Modifier.fillMaxWidth().height(18.dp)) {
                    Text(
                        "Guardado localmente",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.accent,
                        maxLines = 1,
                        modifier = Modifier.alpha(savedAlpha),
                    )
                }

                // ---------------------------------------------------------------- Units & display
                SectionHeader(title = stringResource(R.string.settings_units))
                Spacer(Modifier.height(Spacing.sm))
                Panel(Modifier.fillMaxWidth()) {
                    SettingChoice(
                        label = "Unidade de peso",
                        options = listOf("kg", "lb"),
                        values = listOf("kg", "lb"),
                        selected = value(AppSettings.UNIT_SYSTEM),
                        onSelect = { change(AppSettings.UNIT_SYSTEM, it) },
                    )
                    Spacer(Modifier.height(Spacing.md))
                    SettingChoice(
                        label = "Unidade de energia",
                        options = listOf("kcal", "kJ"),
                        values = listOf("kcal", "kj"),
                        selected = value(AppSettings.ENERGY_UNIT),
                        onSelect = { change(AppSettings.ENERGY_UNIT, it) },
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Hairline()
                    Spacer(Modifier.height(Spacing.sm))
                    SettingSwitch(
                        label = "Movimento reduzido",
                        subtitle = stringResource(R.string.settings_min_anim),
                        checked = switchState[AppSettings.REDUCED_MOTION] ?: boolValue(AppSettings.REDUCED_MOTION),
                        reducedMotion = reducedMotion,
                        onChange = { setSwitch(AppSettings.REDUCED_MOTION, it) },
                    )
                    SettingSwitch(
                        label = stringResource(R.string.settings_advanced),
                        subtitle = stringResource(R.string.settings_advanced_info),
                        checked = switchState[AppSettings.ADVANCED_METRICS] ?: boolValue(AppSettings.ADVANCED_METRICS),
                        reducedMotion = reducedMotion,
                        onChange = { setSwitch(AppSettings.ADVANCED_METRICS, it) },
                    )
                }

                Spacer(Modifier.height(Spacing.xl))

                // ---------------------------------------------------------------- Data & privacy
                SectionHeader(title = "Dados e privacidade")
                Spacer(Modifier.height(Spacing.sm))
                Panel(Modifier.fillMaxWidth()) {
                    Text(
                        "Tudo fica neste dispositivo — sem conta, sem servidor, sem telemetria.",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink2,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    MetriqButton(
                        text = "Exportar CSV",
                        onClick = onExport,
                        modifier = Modifier.fillMaxWidth(),
                        primary = false,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Hairline()
                    Spacer(Modifier.height(Spacing.md))
                    SettingChoice(
                        label = stringResource(R.string.settings_widget_update),
                        options = listOf("15 min", "30 min", "60 min"),
                        values = listOf("15", "30", "60"),
                        selected = value(AppSettings.WIDGET_REFRESH_MINUTES),
                        onSelect = { change(AppSettings.WIDGET_REFRESH_MINUTES, it) },
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "Minutos entre atualizações do widget — pode ser necessário reiniciar a app.",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink3,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Hairline()
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        "Metriq 0.1.0",
                        style = MaterialTheme.typography.label,
                        color = MetriqColors.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "AGPL-3.0 · implementação independente, sem código copiado de outras apps.",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink3,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(Spacing.xl))

                // ---------------------------------------------------------------- Integrations
                SectionHeader(title = stringResource(R.string.settings_integrations))
                Spacer(Modifier.height(Spacing.sm))
                Panel(Modifier.fillMaxWidth()) {
                    Text(
                        "HEALTH CONNECT",
                        style = MaterialTheme.typography.label,
                        color = MetriqColors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        healthStatus,
                        style = MaterialTheme.typography.bodyStrong,
                        color = MetriqColors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetriqButton(
                            text = "Ligar",
                            onClick = onConnectHealth,
                            modifier = Modifier.weight(1f),
                            primary = false,
                        )
                        MetriqButton(
                            text = "Sincronizar agora",
                            onClick = onSyncHealth,
                            modifier = Modifier.weight(1f),
                            primary = false,
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Hairline()
                    Spacer(Modifier.height(Spacing.sm))
                    SettingSwitch(
                        label = stringResource(R.string.settings_hevy_auto),
                        subtitle = "Sincronizar com o Health Connect em segundo plano",
                        checked = switchState[AppSettings.HEALTH_AUTO_SYNC] ?: boolValue(AppSettings.HEALTH_AUTO_SYNC),
                        reducedMotion = reducedMotion,
                        onChange = { setSwitch(AppSettings.HEALTH_AUTO_SYNC, it) },
                    )
                    SettingSwitch(
                        label = "Importar passos",
                        subtitle = "Importar passos do Health Connect",
                        checked = switchState[AppSettings.HEALTH_IMPORT_STEPS] ?: boolValue(AppSettings.HEALTH_IMPORT_STEPS),
                        reducedMotion = reducedMotion,
                        onChange = { setSwitch(AppSettings.HEALTH_IMPORT_STEPS, it) },
                    )
                    SettingSwitch(
                        label = "Importar peso",
                        subtitle = "Importar pesagens do Health Connect",
                        checked = switchState[AppSettings.HEALTH_IMPORT_WEIGHT]
                            ?: boolValue(AppSettings.HEALTH_IMPORT_WEIGHT),
                        reducedMotion = reducedMotion,
                        onChange = { setSwitch(AppSettings.HEALTH_IMPORT_WEIGHT, it) },
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "Os registos manuais têm sempre prioridade sobre os dados importados.",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink3,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(Spacing.xl))

                // ---------------------------------------------------------------- Hevy
                SectionHeader(title = stringResource(R.string.settings_hevy))
                Spacer(Modifier.height(Spacing.sm))
                Panel(Modifier.fillMaxWidth()) {
                    Text(
                        "CHAVE DE API",
                        style = MaterialTheme.typography.label,
                        color = MetriqColors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    // A chave permanece oculta e nunca é escrita em logs ou outro Text.
                    OutlinedTextField(
                        value = hevyApiKey,
                        onValueChange = onHevyApiKeyChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Chave da API Hevy") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                        ),
                        shape = RoundedCornerShape(10.dp),
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
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        when {
                            !hevyConnected && hevyApiKey.isBlank() ->
                                "Nenhuma chave guardada. Adiciona a tua chave para ligar."
                            !hevyConnected -> "Não ligado. Liga a conta para ler os teus treinos."
                            else -> "Conectado"
                        },
                        style = MaterialTheme.typography.bodyStrong,
                        color = if (hevyConnected) MetriqColors.ink else MetriqColors.ink2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        if (hevyWorkoutCount > 0) "$hevyWorkoutCount treinos importados"
                        else "Nenhum treino importado ainda",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val lastSyncText = when {
                        hevyLastSync.isNullOrBlank() -> "Nunca sincronizado"
                        else -> HevyTimeFormatter.formatLastSync(hevyLastSync)
                            ?.let { "Última sincronização: $it" }
                            ?: "Data de sincronização indisponível"
                    }
                    Text(
                        lastSyncText,
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!hevyLastError.isNullOrBlank()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            "Último erro: $hevyLastError",
                            style = MaterialTheme.typography.caption,
                            color = MetriqColors.ink2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(Spacing.md))
                    Hairline()
                    Spacer(Modifier.height(Spacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetriqButton(
                            text = "Conectar",
                            onClick = onHevyConnect,
                            modifier = Modifier.weight(1f),
                            primary = !hevyConnected,
                        )
                        MetriqButton(
                            text = "Sincronizar",
                            onClick = onHevySync,
                            modifier = Modifier.weight(1f),
                            primary = hevyConnected,
                        )
                        MetriqButton(
                            text = "Desconectar",
                            onClick = onHevyDisconnect,
                            modifier = Modifier.weight(1f),
                            primary = false,
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Hairline()
                    Spacer(Modifier.height(Spacing.sm))
                    SettingSwitch(
                        label = stringResource(R.string.settings_hevy_auto),
                        subtitle = "Ler novos treinos do Hevy automaticamente",
                        checked = localHevyAutoSync,
                        reducedMotion = reducedMotion,
                        onChange = { setHevyAutoSync(it) },
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "A chave fica criptografada no aparelho. Os treinos são apenas lidos do Hevy; nada é enviado de volta.",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink3,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(Spacing.xl))

                // ---------------------------------------------------------------- Analytics defaults
                SectionHeader(title = stringResource(R.string.settings_analytics))
                Spacer(Modifier.height(Spacing.sm))
                Panel(Modifier.fillMaxWidth()) {
                    SettingChoice(
                        label = stringResource(R.string.settings_default_period),
                        options = listOf("7D", "30D", "90D", "1A"),
                        values = listOf("7", "30", "90", "365"),
                        selected = value(AppSettings.ANALYTICS_RANGE),
                        onSelect = { change(AppSettings.ANALYTICS_RANGE, it) },
                    )
                    Spacer(Modifier.height(Spacing.md))
                    SettingChoice(
                        label = stringResource(R.string.settings_default_metric),
                        options = listOf("Ingestão", "Gasto", "Peso", "Passos"),
                        values = listOf("intake", "expenditure", "weight", "steps"),
                        selected = value(AppSettings.ANALYTICS_METRIC),
                        onSelect = { change(AppSettings.ANALYTICS_METRIC, it) },
                    )
                }

                Spacer(Modifier.height(Spacing.xl))
                SectionHeader(title = stringResource(R.string.settings_ai))
                Spacer(Modifier.height(Spacing.sm))
                Panel(Modifier.fillMaxWidth()) {
                    SettingSwitch(
                        label = "Macros adaptativos",
                        subtitle = "Reajustar hidratos e gordura com o sinal de treino de hoje do Health Connect",
                        checked = switchState[AppSettings.ADAPTIVE_MACROS] ?: boolValue(AppSettings.ADAPTIVE_MACROS),
                        reducedMotion = reducedMotion,
                        onChange = { setSwitch(AppSettings.ADAPTIVE_MACROS, it) },
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onOpenIntegrations() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_integrations),
                                style = MaterialTheme.typography.bodyStrong,
                                color = MetriqColors.ink,
                                maxLines = 1,
                            )
                            Text(
                                "Health Connect, Hevy, importação de histórico e mais",
                                style = MaterialTheme.typography.caption,
                                color = MetriqColors.ink2,
                                maxLines = 2,
                            )
                        }
                        Text("›", style = MaterialTheme.typography.h2, color = MetriqColors.accent)
                    }
                    Spacer(Modifier.height(Spacing.md))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onOpenAiModels() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "IA e modelos locais",
                                style = MaterialTheme.typography.bodyStrong,
                                color = MetriqColors.ink,
                                maxLines = 1,
                            )
                            Text(
                                "Orçamento de armazenamento, testes e modelos opcionais no dispositivo",
                                style = MaterialTheme.typography.caption,
                                color = MetriqColors.ink2,
                                maxLines = 2,
                            )
                        }
                        Text("›", style = MaterialTheme.typography.h2, color = MetriqColors.accent)
                    }
                }
            }
        }
    }
}

/** Label plus a segmented control; maps the chosen index back to a stored string value. */
@Composable
private fun SettingChoice(
    label: String,
    options: List<String>,
    values: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Text(
        label,
        style = MaterialTheme.typography.label,
        color = MetriqColors.ink3,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(Spacing.sm))
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    Segmented(
        options = options,
        selectedIndex = selectedIndex,
        onSelect = { onSelect(values[it]) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Real Material3 switch for accessibility and a 48.dp touch target. */
@Composable
@Suppress("UNUSED_PARAMETER")
private fun SettingSwitch(
    label: String,
    subtitle: String,
    checked: Boolean,
    reducedMotion: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onChange(!checked) }
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyStrong,
                color = MetriqColors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                subtitle,
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = switchColors(),
        )
    }
}

/** Metriq dark palette expressed through Material3 switch tokens. */
@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = MetriqColors.accentInk,
    checkedTrackColor = MetriqColors.accent,
    checkedBorderColor = MetriqColors.accent,
    uncheckedThumbColor = MetriqColors.ink3,
    uncheckedTrackColor = MetriqColors.surface2,
    uncheckedBorderColor = MetriqColors.border,
    disabledCheckedThumbColor = MetriqColors.accentInk.copy(alpha = 0.5f),
    disabledCheckedTrackColor = MetriqColors.accent.copy(alpha = 0.5f),
    disabledCheckedBorderColor = MetriqColors.accent.copy(alpha = 0.5f),
    disabledUncheckedThumbColor = MetriqColors.ink3.copy(alpha = 0.5f),
    disabledUncheckedTrackColor = MetriqColors.surface2.copy(alpha = 0.5f),
    disabledUncheckedBorderColor = MetriqColors.border,
)

@Composable
private fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MetriqColors.border),
    )
}

/** 200ms FastOutSlowIn for state changes, or snap when the user opted out of motion. */
private fun <T> animationSpec(reducedMotion: Boolean): AnimationSpec<T> =
    if (reducedMotion) {
        snap()
    } else {
        tween(durationMillis = 200, easing = FastOutSlowInEasing)
    }
