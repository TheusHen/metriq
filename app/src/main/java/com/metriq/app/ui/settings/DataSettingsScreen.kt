package com.metriq.app.ui.settings

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metriq.app.AppSettings
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.ScreenHeader
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.components.Segmented
import com.metriq.app.ui.theme.MetriqColors
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
) {
    // Snapshot-backed reads: recomposition re-evaluates these when settings change.
    fun value(key: String): String = settings[key] ?: AppSettings.defaults[key].orEmpty()
    fun boolValue(key: String): Boolean = value(key).toBooleanStrictOrNull()
        ?: AppSettings.defaults[key]?.toBooleanStrictOrNull() ?: false

    val reducedMotion = boolValue(AppSettings.REDUCED_MOTION)

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
                title = "Settings",
                subtitle = "Stored on this device",
                onBack = onBack,
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
                        "Saved locally",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.accent,
                        maxLines = 1,
                        modifier = Modifier.alpha(savedAlpha),
                    )
                }

                // ---------------------------------------------------------------- Units & display
                SectionHeader(title = "Units & display")
                Spacer(Modifier.height(8.dp))
                Panel(Modifier.fillMaxWidth()) {
                    SettingChoice(
                        label = "Weight unit",
                        options = listOf("kg", "lb"),
                        values = listOf("kg", "lb"),
                        selected = value(AppSettings.UNIT_SYSTEM),
                        onSelect = { change(AppSettings.UNIT_SYSTEM, it) },
                    )
                    Spacer(Modifier.height(14.dp))
                    SettingChoice(
                        label = "Energy unit",
                        options = listOf("kcal", "kJ"),
                        values = listOf("kcal", "kj"),
                        selected = value(AppSettings.ENERGY_UNIT),
                        onSelect = { change(AppSettings.ENERGY_UNIT, it) },
                    )
                    Spacer(Modifier.height(6.dp))
                    Hairline()
                    Spacer(Modifier.height(6.dp))
                    SettingSwitch(
                        label = "Reduced motion",
                        subtitle = "Minimize interface animation",
                        checked = boolValue(AppSettings.REDUCED_MOTION),
                        reducedMotion = reducedMotion,
                        onChange = { change(AppSettings.REDUCED_MOTION, it.toString()) },
                    )
                    SettingSwitch(
                        label = "Advanced metrics",
                        subtitle = "Show nerdy metrics in Analytics",
                        checked = boolValue(AppSettings.ADVANCED_METRICS),
                        reducedMotion = reducedMotion,
                        onChange = { change(AppSettings.ADVANCED_METRICS, it.toString()) },
                    )
                }

                Spacer(Modifier.height(18.dp))

                // ---------------------------------------------------------------- Data & privacy
                SectionHeader(title = "Data & privacy")
                Spacer(Modifier.height(8.dp))
                Panel(Modifier.fillMaxWidth()) {
                    Text(
                        "Everything stays on this device — no account, no backend, no telemetry.",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink2,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    MetriqButton(
                        text = "Export CSV",
                        onClick = onExport,
                        modifier = Modifier.fillMaxWidth(),
                        primary = false,
                    )
                    Spacer(Modifier.height(14.dp))
                    Hairline()
                    Spacer(Modifier.height(14.dp))
                    SettingChoice(
                        label = "Widget refresh",
                        options = listOf("15 min", "30 min", "60 min"),
                        values = listOf("15", "30", "60"),
                        selected = value(AppSettings.WIDGET_REFRESH_MINUTES),
                        onSelect = { change(AppSettings.WIDGET_REFRESH_MINUTES, it) },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Minutes between widget updates — an app restart may be needed.",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink3,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(14.dp))
                    Hairline()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Metriq 0.1.0",
                        style = MaterialTheme.typography.label,
                        color = MetriqColors.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "AGPL-3.0 · clean-room implementation, no code copied from other apps.",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink3,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(18.dp))

                // ---------------------------------------------------------------- Integrations
                SectionHeader(title = "Integrations")
                Spacer(Modifier.height(8.dp))
                Panel(Modifier.fillMaxWidth()) {
                    Text(
                        "HEALTH CONNECT",
                        style = MaterialTheme.typography.label,
                        color = MetriqColors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        healthStatus,
                        style = MaterialTheme.typography.bodyStrong,
                        color = MetriqColors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetriqButton(
                            text = "Connect",
                            onClick = onConnectHealth,
                            modifier = Modifier.weight(1f),
                            primary = false,
                        )
                        MetriqButton(
                            text = "Sync now",
                            onClick = onSyncHealth,
                            modifier = Modifier.weight(1f),
                            primary = false,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Hairline()
                    Spacer(Modifier.height(6.dp))
                    SettingSwitch(
                        label = "Auto-sync",
                        subtitle = "Sync with Health Connect in the background",
                        checked = boolValue(AppSettings.HEALTH_AUTO_SYNC),
                        reducedMotion = reducedMotion,
                        onChange = { change(AppSettings.HEALTH_AUTO_SYNC, it.toString()) },
                    )
                    SettingSwitch(
                        label = "Import steps",
                        subtitle = "Pull step counts from Health Connect",
                        checked = boolValue(AppSettings.HEALTH_IMPORT_STEPS),
                        reducedMotion = reducedMotion,
                        onChange = { change(AppSettings.HEALTH_IMPORT_STEPS, it.toString()) },
                    )
                    SettingSwitch(
                        label = "Import weight",
                        subtitle = "Pull weight samples from Health Connect",
                        checked = boolValue(AppSettings.HEALTH_IMPORT_WEIGHT),
                        reducedMotion = reducedMotion,
                        onChange = { change(AppSettings.HEALTH_IMPORT_WEIGHT, it.toString()) },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Manual entries always take priority over imported data.",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink3,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(18.dp))

                // ---------------------------------------------------------------- Analytics defaults
                SectionHeader(title = "Analytics defaults")
                Spacer(Modifier.height(8.dp))
                Panel(Modifier.fillMaxWidth()) {
                    SettingChoice(
                        label = "Default range",
                        options = listOf("7D", "30D", "90D", "1Y"),
                        values = listOf("7", "30", "90", "365"),
                        selected = value(AppSettings.ANALYTICS_RANGE),
                        onSelect = { change(AppSettings.ANALYTICS_RANGE, it) },
                    )
                    Spacer(Modifier.height(14.dp))
                    SettingChoice(
                        label = "Default metric",
                        options = listOf("Intake", "Burn", "Weight", "Steps"),
                        values = listOf("intake", "expenditure", "weight", "steps"),
                        selected = value(AppSettings.ANALYTICS_METRIC),
                        onSelect = { change(AppSettings.ANALYTICS_METRIC, it) },
                    )
                }

                Spacer(Modifier.height(18.dp))
                SectionHeader(title = "Intelligence")
                Spacer(Modifier.height(8.dp))
                Panel(Modifier.fillMaxWidth()) {
                    SettingSwitch(
                        label = "Adaptive macros",
                        subtitle = "Rebalance carbs and fat using today's Health Connect training signal",
                        checked = boolValue(AppSettings.ADAPTIVE_MACROS),
                        reducedMotion = reducedMotion,
                        onChange = { change(AppSettings.ADAPTIVE_MACROS, it.toString()) },
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAiModels() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "AI & local models",
                                style = MaterialTheme.typography.bodyStrong,
                                color = MetriqColors.ink,
                                maxLines = 1,
                            )
                            Text(
                                "Storage budget, benchmarks and optional on-device models",
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
    Spacer(Modifier.height(8.dp))
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    Segmented(
        options = options,
        selectedIndex = selectedIndex,
        onSelect = { onSelect(values[it]) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Color/opacity-only toggle — the thumb snaps, only colors animate, capped at 200ms. */
@Composable
private fun SettingSwitch(
    label: String,
    subtitle: String,
    checked: Boolean,
    reducedMotion: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val track by animateColorAsState(
        targetValue = if (checked) MetriqColors.accent else MetriqColors.surface2,
        animationSpec = animationSpec(reducedMotion),
        label = "switchTrack",
    )
    val thumb by animateColorAsState(
        targetValue = if (checked) MetriqColors.accentInk else MetriqColors.ink3,
        animationSpec = animationSpec(reducedMotion),
        label = "switchThumb",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyStrong,
                color = MetriqColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .width(48.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(track)
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(thumb),
            )
        }
    }
}

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
