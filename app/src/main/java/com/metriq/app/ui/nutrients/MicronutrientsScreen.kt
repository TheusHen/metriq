package com.metriq.app.ui.nutrients

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metriq.app.ui.common.SubScreen
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.theme.*
import com.metriq.core.NutrientDRI
import com.metriq.core.Sex
import java.util.Locale

private enum class MicroStatus(val label: String) {
    LOW("Under 50%"),
    BUILDING("50–80%"),
    ON_TRACK("On track"),
    HIGH("Over target"),
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

@Composable
fun MicronutrientsScreen(
    intake: Map<String, Double>,
    sex: Sex,
    age: Int,
    onBack: () -> Unit,
) {
    SubScreen("Nutrients", onBack) {
        Text(
            "Micronutrients",
            style = MaterialTheme.typography.h2,
            color = MetriqColors.ink,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Daily intake against reference values for your profile.",
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink2,
        )
        Spacer(Modifier.height(16.dp))

        Legend()
        Spacer(Modifier.height(16.dp))

        NutrientDRI.NUTRIENTS.forEach { def ->
            val target = NutrientDRI.value(def.key, sex, age)
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
                                "No data"
                            } else {
                                String.format(Locale.ENGLISH, "/ %.0f %s", target, def.unit)
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
                        "No data logged for this nutrient",
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
