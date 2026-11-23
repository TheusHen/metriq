package com.metriq.app.ui.nutrients

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.metriq.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metriq.app.AppGraph
import com.metriq.app.analytics.MicroTargetCodec
import com.metriq.app.ui.common.SubScreen
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.theme.*
import com.metriq.core.NutrientDef
import com.metriq.core.NutrientDRI
import com.metriq.core.Sex
import java.util.Locale

private const val MICRO_TARGETS_KEY = "micro_targets_json"

private enum class MicroStatus(val label: String) {
    LOW("Under 50%"),
    BUILDING("50–80%"),
    ON_TRACK("On track"),
    HIGH("Acima da meta"),
}

private fun statusFor(ratio: Double): MicroStatus = when {
    ratio < 0.5 -> MicroStatus.LOW
    ratio < 0.8 -> MicroStatus.BUILDING
    ratio > 1.2 -> MicroStatus.HIGH
    else -> MicroStatus.ON_TRACK
}

@Composable
private fun statusColor(status: MicroStatus): Color = when (status) {
    MicroStatus.LOW -> MetriqColors.danger
    MicroStatus.BUILDING -> MetriqColors.carbs
    MicroStatus.ON_TRACK -> MetriqColors.accent
    MicroStatus.HIGH -> MetriqColors.energy
}

/** Keeps small targets (vitamins, caffeine) legible instead of rounding them to zero. */
private fun pretty(value: Double): String = when {
    value >= 100.0 -> String.format(Locale.ENGLISH, "%.0f", value)
    value >= 10.0 -> String.format(Locale.ENGLISH, "%.1f", value).trimEnd('0').trimEnd('.')
    else -> String.format(Locale.ENGLISH, "%.2f", value).trimEnd('0').trimEnd('.')
}

private fun loadTargets(): Map<String, Double> = runCatching {
    MicroTargetCodec.decode(AppGraph.store.settings.get(MICRO_TARGETS_KEY))
}.getOrElse { emptyMap() }

private fun saveTargets(targets: Map<String, Double>) {
    runCatching { AppGraph.store.settings.set(MICRO_TARGETS_KEY, MicroTargetCodec.encode(targets)) }
}

@Composable
fun MicronutrientsScreen(
    intake: Map<String, Double>,
    sex: Sex,
    age: Int,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var customTargets by remember { mutableStateOf(loadTargets()) }
    val drafts = remember { mutableStateMapOf<String, String>() }

    fun targetFor(def: NutrientDef): Double =
        customTargets[def.key]?.takeIf { it.isFinite() && it > 0.0 }
            ?: NutrientDRI.value(def.key, sex, age)

    fun toggleEdit() {
        if (editing) {
            editing = false
        } else {
            NutrientDRI.NUTRIENTS.forEach { def ->
                drafts[def.key] = pretty(targetFor(def))
            }
            editing = true
        }
    }

    fun updateTarget(key: String, raw: String) {
        drafts[key] = raw
        val value = raw.toDoubleOrNull()
        if (value != null && value.isFinite() && value > 0.0) {
            val next = customTargets + (key to value)
            customTargets = next
            saveTargets(next)
        }
    }

    SubScreen(stringResource(R.string.nutrients), onBack) {
        Text(
            stringResource(R.string.nutrients),
            style = MaterialTheme.typography.h2,
            color = MetriqColors.ink,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.micro_intro),
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink2,
        )
        Spacer(Modifier.height(16.dp))

        Panel(Modifier.fillMaxWidth()) {
            SectionHeader(
                title = stringResource(R.string.targets),
                action = if (editing) stringResource(R.string.micro_done) else stringResource(R.string.micro_edit),
                onAction = { toggleEdit() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (editing) {
                    stringResource(R.string.micro_custom_info)
                } else if (customTargets.isEmpty()) {
                    stringResource(R.string.micro_defaults)
                } else {
                    "${customTargets.size} meta personalizada${if (customTargets.size == 1) "" else "s"} set."
                },
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
            )
            if (editing) {
                Spacer(Modifier.height(12.dp))
                NutrientDRI.NUTRIENTS.forEach { def ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${def.label} (${def.unit})",
                            style = MaterialTheme.typography.label,
                            color = MetriqColors.ink2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = drafts[def.key] ?: pretty(targetFor(def)),
                            onValueChange = { updateTarget(def.key, it) },
                            modifier = Modifier.width(110.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = stringResource(R.string.micro_reset),
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink2,
                    modifier = Modifier.clickable {
                        customTargets = emptyMap()
                        saveTargets(emptyMap())
                        NutrientDRI.NUTRIENTS.forEach { def ->
                            drafts[def.key] = pretty(NutrientDRI.value(def.key, sex, age))
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Legend()
        Spacer(Modifier.height(16.dp))

        NutrientDRI.NUTRIENTS.forEach { def ->
            val target = targetFor(def)
            val amount = intake[def.key]
            val status = if (amount != null && target > 0) {
                statusFor((amount / target).coerceIn(0.0, 1.5))
            } else {
                null
            }

            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        def.label,
                        style = MaterialTheme.typography.bodyStrong,
                        color = MetriqColors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (amount == null) {
                                "—"
                            } else {
                                String.format(Locale.ENGLISH, "%.0f", amount)
                            },
                            style = MaterialTheme.typography.number24,
                            color = if (amount == null) MetriqColors.ink3 else MetriqColors.ink,
                        )
                        Text(
                            text = if (amount == null) {
                                "Sem dados"
                            } else {
                                "/ ${pretty(target)} ${def.unit}"
                            },
                            style = MaterialTheme.typography.caption,
                            color = MetriqColors.ink3,
                        )
                    }
                }

                if (amount != null && target > 0 && status != null) {
                    val ratio = (amount / target).coerceIn(0.0, 1.5)
                    val color = statusColor(status)
                    Spacer(Modifier.height(10.dp))
                    // Unknown is never asserted as measured: a bar is drawn only
                    // when a real amount exists.
                    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                        drawRoundRect(
                            color = MetriqColors.surface2,
                            cornerRadius = CornerRadius(size.height / 2),
                        )
                        drawRoundRect(
                            color = color,
                            size = Size(size.width * (ratio / 1.5).toFloat(), size.height),
                            cornerRadius = CornerRadius(size.height / 2),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        status.label,
                        style = MaterialTheme.typography.caption,
                        color = color,
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.micro_no_data),
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink3,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Legend() {
    Row(Modifier.fillMaxWidth()) {
        MicroStatus.entries.forEach { status ->
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor(status)),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    status.label,
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
