package com.metriq.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metriq.app.ui.theme.LocalMacroColors

data class TargetSpec(
    val label: String,
    val consumed: Int,
    val target: Int,
    val color: Color,
)

@Composable
fun TargetBar(
    consumed: Int,
    target: Int,
    color: Color,
    modifier: Modifier = Modifier,
    height: Int = 60,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = consumed.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "/ $target",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        val fraction = if (target <= 0) 0f else (consumed.toFloat() / target).coerceIn(0f, 1f)
        val barColor = if (target > 0 && consumed > target) com.metriq.app.ui.theme.Accent else color
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(6.dp),
        ) {
            drawRoundRect(
                color = barColor.copy(alpha = 0.25f),
                cornerRadius = CornerRadius(size.height / 2),
            )
            if (fraction > 0f) {
                drawRoundRect(
                    color = barColor,
                    size = Size(size.width * fraction, size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            }
        }
    }
}

/** The four-rail header: calories, protein, fat, carbs. */
@Composable
fun TargetRails(
    kcal: Int,
    kcalTarget: Int,
    protein: Int,
    proteinTarget: Int,
    fat: Int,
    fatTarget: Int,
    carb: Int,
    carbTarget: Int,
    modifier: Modifier = Modifier,
) {
    val macro = LocalMacroColors.current
    val ink = MaterialTheme.colorScheme.onBackground
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            TargetBar(kcal, kcalTarget, ink, Modifier.weight(1f))
            TargetBar(protein, proteinTarget, macro.protein, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            TargetBar(fat, fatTarget, macro.fat, Modifier.weight(1f))
            TargetBar(carb, carbTarget, macro.carb, Modifier.weight(1f))
        }
    }
}

@Composable
fun RailLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Small dot used in legends and list rows. */
@Composable
fun NutrientDot(color: Color, size: Int = 8) {
    Canvas(Modifier.width(size.dp).height(size.dp)) {
        drawCircle(color, radius = this.size.minDimension / 2f, center = Offset(center.x, center.y))
    }
}
