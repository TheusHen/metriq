package com.metriq.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BreakfastDining
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LocalPizza
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SetMeal
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metriq.app.AppSettings
import com.metriq.app.Screen
import com.metriq.app.ui.home.WeekDay
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.app.ui.theme.body
import com.metriq.app.ui.theme.bodyStrong
import com.metriq.app.ui.theme.caption
import com.metriq.app.ui.theme.h1
import com.metriq.app.ui.theme.h2
import com.metriq.app.ui.theme.label
import com.metriq.app.ui.theme.number40
import java.util.Locale

/** One coherent block: a flat surface step, one small radius, no accent, no shadow. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MetriqColors.surface1)
            .padding(16.dp),
        content = content,
    )
}

/** Titles sit outside panels; the action is a quiet text action, never an accent link. */
@Composable
fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.h2,
            color = MetriqColors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (action != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = action,
                style = MaterialTheme.typography.body,
                color = MetriqColors.ink2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable { onAction() },
            )
        }
    }
}

/** Tabular figures for every value that lines up in a row or updates in place. */
@Composable
internal fun tabularNumbers() = MaterialTheme.typography.bodyStrong.copy(
    fontFeatureSettings = "tnum",
)

@Composable
internal fun tabularCaption() = MaterialTheme.typography.caption.copy(
    fontFeatureSettings = "tnum",
)

internal fun formatCount(value: Int): String = String.format(Locale.ENGLISH, "%,d", value)

/** The one block that carries the screen's answer: intake, budget fill and macro rails. */
@Composable
fun EnergyBudget(
    consumed: Int,
    target: Int,
    protein: Pair<Int, Int>,
    carbs: Pair<Int, Int>,
    fat: Pair<Int, Int>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MetriqColors.surface1)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Meta diária",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            val over = target > 0 && consumed > target
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MetriqColors.surface2)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(
                    text = if (over) "Acima da meta" else "Estável",
                    style = MaterialTheme.typography.label,
                    color = if (over) MetriqColors.danger else MetriqColors.ink2,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatCount(consumed),
                style = MaterialTheme.typography.number40.copy(fontFeatureSettings = "tnum"),
                color = MetriqColors.ink,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "/ ${formatCount(target)}",
                style = MaterialTheme.typography.body.copy(fontFeatureSettings = "tnum"),
                color = MetriqColors.ink2,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 5.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "kcal",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        val fraction = if (target <= 0) 0f else (consumed.toFloat() / target.toFloat()).coerceIn(0f, 1f)
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            drawRoundRect(
                color = MetriqColors.surface2,
                cornerRadius = CornerRadius(size.height / 2),
            )
            if (fraction > 0f) {
                drawRoundRect(
                    color = MetriqColors.accent,
                    size = Size(size.width * fraction, size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        MacroRow("Proteína", protein.first, protein.second)
        HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
        MacroRow("Hidratos", carbs.first, carbs.second)
        HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
        MacroRow("Gordura", fat.first, fat.second)
    }
}

@Composable
private fun MacroRow(label: String, consumed: Int, target: Int) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.body,
                color = MetriqColors.ink2,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier.widthIn(max = 112.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(text = formatCount(consumed), style = tabularNumbers(), color = MetriqColors.ink)
                Text(
                    text = " / ${formatCount(target)}",
                    style = tabularCaption(),
                    color = MetriqColors.ink3,
                    maxLines = 1,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "g",
                    style = tabularCaption(),
                    color = MetriqColors.ink3,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val fraction = if (target <= 0) 0f else (consumed.toFloat() / target.toFloat()).coerceIn(0f, 1f)
        Canvas(Modifier.fillMaxWidth().height(3.dp)) {
            drawRoundRect(
                color = MetriqColors.surface2,
                cornerRadius = CornerRadius(size.height / 2),
            )
            if (fraction > 0f) {
                drawRoundRect(
                    color = MetriqColors.ink2,
                    size = Size(size.width * fraction, size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            }
        }
    }
}

/** Seven-day rail: a neutral raised slot marks today, a small dot marks days with data. */
@Composable
fun WeekStrip(
    week: List<WeekDay>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        week.forEach { day ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = day.letter,
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink3,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (day.isToday) MetriqColors.surface2 else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.dayOfMonth.toString(),
                        style = tabularNumbers(),
                        color = if (day.isToday) MetriqColors.ink else MetriqColors.ink2,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                day.logged -> MetriqColors.ink3
                                else -> Color.Transparent
                            },
                        ),
                )
            }
        }
    }
}

/**
 * One monochrome glyph, no tinted slot. [category] is a keyword string such as
 * "fruit" or "grain"; unknown values fall back to a neutral restaurant glyph.
 */
@Composable
fun FoodIcon(category: String?, modifier: Modifier = Modifier) {
    val key = category?.lowercase(Locale.ROOT).orEmpty()
    val glyph: ImageVector = when {
        key.contains("fruit") || key.contains("berry") || key.contains("berries") ||
            key.contains("apple") || key.contains("banana") || key.contains("grape") ||
            key.contains("melon") -> Icons.Outlined.Spa
        key.contains("vegetable") || key.contains("greens") || key.contains("salad") ||
            key.contains("leaf") || key.contains("broccoli") || key.contains("carrot") -> Icons.Outlined.Eco
        key.contains("meat") || key.contains("chicken") || key.contains("beef") ||
            key.contains("steak") || key.contains("fish") || key.contains("sushi") ||
            key.contains("seafood") -> Icons.Outlined.SetMeal
        key.contains("dairy") || key.contains("milk") || key.contains("cheese") ||
            key.contains("yogurt") || key.contains("egg") -> Icons.Outlined.BreakfastDining
        key.contains("grain") || key.contains("bread") || key.contains("rice") ||
            key.contains("pasta") || key.contains("oat") || key.contains("cereal") -> Icons.Outlined.LunchDining
        key.contains("pizza") -> Icons.Outlined.LocalPizza
        key.contains("snack") || key.contains("burger") || key.contains("fried") -> Icons.Outlined.Fastfood
        key.contains("sweet") || key.contains("cake") || key.contains("honey") ||
            key.contains("chocolate") || key.contains("dessert") -> Icons.Outlined.Cake
        key.contains("drink") || key.contains("water") || key.contains("juice") ||
            key.contains("coffee") -> Icons.Outlined.LocalDrink
        else -> Icons.Outlined.Restaurant
    }
    Icon(
        imageVector = glyph,
        contentDescription = null,
        tint = MetriqColors.ink2,
        modifier = Modifier.size(18.dp).then(modifier),
    )
}

/** Two-line food row with a plain text add action, right-aligned tabular meta and hairline-ready padding. */
@Composable
fun FoodRow(
    name: String,
    meta: String,
    icon: String? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FoodIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.body,
                color = MetriqColors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                style = tabularCaption(),
                color = MetriqColors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = "+",
            style = MaterialTheme.typography.bodyStrong,
            color = MetriqColors.ink2,
            maxLines = 1,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable { onClick() }
                .padding(horizontal = 12.dp),
        )
    }
}

@Composable
fun MetriqNavBar(
    current: Screen,
    onNavigate: (Screen) -> Unit,
    onOpenLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MetriqColors.surface1) {
        Column(modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MetriqColors.border),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                NavTab(
                    label = "Hoje",
                    icon = Icons.Outlined.SpaceDashboard,
                    active = current == Screen.DASHBOARD,
                    onClick = { onNavigate(Screen.DASHBOARD) },
                )
                NavTab(
                    label = "Registar",
                    icon = Icons.Outlined.Restaurant,
                    active = current == Screen.SEARCH,
                    onClick = onOpenLog,
                )
                NavTab(
                    label = "Peso",
                    icon = Icons.Outlined.MonitorWeight,
                    active = current == Screen.WEIGHT,
                    onClick = { onNavigate(Screen.WEIGHT) },
                )
                NavTab(
                    label = "Dados",
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                    active = current == Screen.ANALYTICS,
                    onClick = { onNavigate(Screen.ANALYTICS) },
                )
                NavTab(
                    label = "Mais",
                    icon = Icons.Outlined.GridView,
                    active = current == Screen.PROFILE,
                    onClick = { onNavigate(Screen.PROFILE) },
                )
            }
        }
    }
}

@Composable
private fun NavTab(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (active) MetriqColors.ink else MetriqColors.ink3
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(
            Modifier
                .size(width = 20.dp, height = 2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (active) MetriqColors.accent else Color.Transparent),
        )
        Spacer(Modifier.height(5.dp))
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(text = label, style = MaterialTheme.typography.caption, color = tint, maxLines = 1)
    }
}

@Composable
fun MetriqButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) MetriqColors.accent else MetriqColors.surface2,
            contentColor = if (primary) MetriqColors.accentInk else MetriqColors.ink,
            disabledContainerColor = MetriqColors.surface1,
            disabledContentColor = MetriqColors.ink4,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyStrong,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Outlined.ChevronLeft,
                    contentDescription = "Voltar",
                    tint = MetriqColors.ink2,
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.h1,
                color = MetriqColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.label,
                    color = MetriqColors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun Segmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MetriqColors.bg)
            .border(1.dp, MetriqColors.border, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) MetriqColors.surface2 else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.label,
                    color = if (selected) MetriqColors.ink else MetriqColors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Minimal line chart: faint grid, one straight-segment polyline, optional points and axis
 * labels. No gradients and no interactive scrub layer.
 */
@Composable
fun TrendChart(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    showAxis: Boolean = false,
    showPoints: Boolean = false,
) {
    val reducedMotion = AppSettings.bool(AppSettings.REDUCED_MOTION)
    val reveal = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(values, reducedMotion) {
        if (reducedMotion) {
            reveal.snapTo(1f)
        } else {
            reveal.snapTo(0f)
            reveal.animateTo(1f, animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing))
        }
    }
    val slidePx = with(LocalDensity.current) { 8.dp.toPx() }
    val progress by reveal.asState()
    Box(
        modifier
            .fillMaxWidth()
            .height(140.dp)
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * slidePx
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val padTop = 8.dp.toPx()
            val padBottom = if (showAxis) 22.dp.toPx() else 8.dp.toPx()
            val padLeft = if (showAxis) 30.dp.toPx() else 0f
            val padRight = if (showAxis) 8.dp.toPx() else 0f
            val w = (size.width - padLeft - padRight).coerceAtLeast(1f)
            val h = (size.height - padTop - padBottom).coerceAtLeast(1f)

            val rawMin = values.minOrNull()
            val rawMax = values.maxOrNull()
            val minValue: Double
            val maxValue: Double
            if (rawMin == null || rawMax == null) {
                minValue = 0.0
                maxValue = 1.0
            } else if (rawMin == rawMax) {
                minValue = rawMin - 1.0
                maxValue = rawMax + 1.0
            } else {
                val span = rawMax - rawMin
                minValue = rawMin - span * 0.12
                maxValue = rawMax + span * 0.12
            }
            val span = maxValue - minValue

            fun yOf(value: Double): Float =
                padTop + h - (((value - minValue) / span).toFloat().coerceIn(0f, 1f) * h)

            fun xOf(index: Int): Float = if (values.size <= 1) {
                padLeft + w / 2f
            } else {
                padLeft + index.toFloat() / (values.size - 1).toFloat() * w
            }

            val gridPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 10.dp.toPx()
                setColor(MetriqColors.ink3.toArgbCompat())
            }

            // Four faint horizontal gridlines.
            for (i in 0..3) {
                val y = padTop + h * (i / 3f)
                drawLine(
                    color = MetriqColors.border.copy(alpha = 0.55f),
                    start = Offset(padLeft, y),
                    end = Offset(padLeft + w, y),
                    strokeWidth = 1.dp.toPx(),
                )
                if (showAxis) {
                    val tick = maxValue - (maxValue - minValue) * (i / 3.0)
                    drawContext.canvas.nativeCanvas.drawText(
                        String.format(Locale.ENGLISH, "%.1f", tick),
                        0f,
                        y + 3.dp.toPx(),
                        gridPaint,
                    )
                }
            }

            if (values.isEmpty()) {
                drawContext.canvas.nativeCanvas.drawText(
                    "Ainda sem dados",
                    padLeft + w / 2f - gridPaint.measureText("Ainda sem dados") / 2f,
                    padTop + h / 2f,
                    gridPaint,
                )
            } else {
                if (values.size == 1) {
                    drawCircle(
                        color = color,
                        radius = 4.dp.toPx(),
                        center = Offset(padLeft + w / 2f, yOf(values[0])),
                    )
                } else {
                    val line = Path()
                    values.forEachIndexed { index, value ->
                        val x = xOf(index)
                        val y = yOf(value)
                        if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
                    }
                    drawPath(
                        line,
                        color = color,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
                if (showPoints) {
                    values.forEachIndexed { index, value ->
                        drawCircle(
                            color = color,
                            radius = 3.dp.toPx(),
                            center = Offset(xOf(index), yOf(value)),
                        )
                    }
                }
            }

            if (showAxis && values.isNotEmpty()) {
                drawContext.canvas.nativeCanvas.drawText("START", padLeft, size.height - 4.dp.toPx(), gridPaint)
                val now = "NOW"
                drawContext.canvas.nativeCanvas.drawText(
                    now,
                    padLeft + w - gridPaint.measureText(now),
                    size.height - 4.dp.toPx(),
                    gridPaint,
                )
            }
        }
    }
}

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
