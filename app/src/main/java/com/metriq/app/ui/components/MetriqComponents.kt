package com.metriq.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/** Standard panel with accent-free surface separation and generous inner padding. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MetriqColors.surface1)
            .padding(16.dp),
        content = content,
    )
}

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
                style = MaterialTheme.typography.label,
                color = MetriqColors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onAction() },
            )
        }
    }
}

/** The signature energy-budget panel: total, progress and three macro rails. */
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
            .clip(RoundedCornerShape(20.dp))
            .background(MetriqColors.surface1)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "ENERGY BUDGET",
                style = MaterialTheme.typography.label,
                color = MetriqColors.ink2,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (target > 0 && consumed <= target) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(MetriqColors.surface2)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "HOLDING",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.accent,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = consumed.toString(),
                style = MaterialTheme.typography.number40,
                color = MetriqColors.ink,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "/ $target kcal",
                style = MaterialTheme.typography.body,
                color = MetriqColors.ink2,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 5.dp),
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
        Spacer(Modifier.height(16.dp))
        MacroRow("PROTEIN", protein.first, protein.second, MetriqColors.protein)
        Spacer(Modifier.height(12.dp))
        MacroRow("CARBS", carbs.first, carbs.second, MetriqColors.carbs)
        Spacer(Modifier.height(12.dp))
        MacroRow("FAT", fat.first, fat.second, MetriqColors.fat)
    }
}

@Composable
private fun MacroRow(label: String, consumed: Int, target: Int, color: Color) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.label,
                color = MetriqColors.ink2,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$consumed / $target g",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink2,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        val fraction = if (target <= 0) 0f else (consumed.toFloat() / target.toFloat()).coerceIn(0f, 1f)
        Canvas(Modifier.widthIn(max = 150.dp).fillMaxWidth().height(5.dp)) {
            drawRoundRect(
                color = MetriqColors.surface2,
                cornerRadius = CornerRadius(size.height / 2),
            )
            if (fraction > 0f) {
                drawRoundRect(
                    color = color,
                    size = Size(size.width * fraction, size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            }
        }
    }
}

/** Seven-day rail; today is the filled accent circle, past logged days carry a dot. */
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
                        .clip(CircleShape)
                        .background(if (day.isToday) MetriqColors.accent else MetriqColors.surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodyStrong,
                        color = if (day.isToday) MetriqColors.accentInk else MetriqColors.ink2,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                day.isToday -> MetriqColors.accent
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
 * Flat food-category icon: a tinted Material icon in a subtle 40.dp slot.
 * [category] is a keyword string (e.g. "fruit", "grain", "sushi"); unknown values
 * fall back to a neutral restaurant glyph.
 */
@Composable
fun FoodIcon(category: String?, modifier: Modifier = Modifier) {
    val key = category?.lowercase(Locale.ROOT).orEmpty()
    val glyph: ImageVector
    val tint: Color
    when {
        key.contains("fruit") || key.contains("berry") || key.contains("berries") ||
            key.contains("apple") || key.contains("banana") || key.contains("grape") ||
            key.contains("melon") -> {
            glyph = Icons.Outlined.Spa
            tint = MetriqColors.carbs
        }
        key.contains("vegetable") || key.contains("greens") || key.contains("salad") ||
            key.contains("leaf") || key.contains("broccoli") || key.contains("carrot") -> {
            glyph = Icons.Outlined.Eco
            tint = MetriqColors.carbs
        }
        key.contains("meat") || key.contains("chicken") || key.contains("beef") ||
            key.contains("steak") || key.contains("fish") || key.contains("sushi") ||
            key.contains("seafood") -> {
            glyph = Icons.Outlined.SetMeal
            tint = MetriqColors.protein
        }
        key.contains("dairy") || key.contains("milk") || key.contains("cheese") ||
            key.contains("yogurt") || key.contains("egg") -> {
            glyph = Icons.Outlined.BreakfastDining
            tint = MetriqColors.fat
        }
        key.contains("grain") || key.contains("bread") || key.contains("rice") ||
            key.contains("pasta") || key.contains("oat") || key.contains("cereal") -> {
            glyph = Icons.Outlined.LunchDining
            tint = MetriqColors.carbs
        }
        key.contains("pizza") -> {
            glyph = Icons.Outlined.LocalPizza
            tint = MetriqColors.energy
        }
        key.contains("snack") || key.contains("burger") || key.contains("fried") -> {
            glyph = Icons.Outlined.Fastfood
            tint = MetriqColors.energy
        }
        key.contains("sweet") || key.contains("cake") || key.contains("honey") ||
            key.contains("chocolate") || key.contains("dessert") -> {
            glyph = Icons.Outlined.Cake
            tint = MetriqColors.carbs
        }
        key.contains("drink") || key.contains("water") || key.contains("juice") ||
            key.contains("coffee") -> {
            glyph = Icons.Outlined.LocalDrink
            tint = MetriqColors.trend
        }
        else -> {
            glyph = Icons.Outlined.Restaurant
            tint = MetriqColors.accent
        }
    }
    Box(
        Modifier
            .size(40.dp)
            .then(modifier)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = glyph,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Compact food/log row with an icon slot and a circular quick-add slot.
 * [icon] is interpreted as a food category key and rendered by [FoodIcon].
 */
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
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FoodIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyStrong,
                color = MetriqColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MetriqColors.surface2)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.bodyStrong,
                color = MetriqColors.accent,
                maxLines = 1,
            )
        }
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
                    label = "Today",
                    icon = Icons.Outlined.SpaceDashboard,
                    active = current == Screen.DASHBOARD,
                    onClick = { onNavigate(Screen.DASHBOARD) },
                )
                NavTab(
                    label = "Log",
                    icon = Icons.Outlined.Restaurant,
                    active = current == Screen.SEARCH,
                    onClick = onOpenLog,
                )
                NavTab(
                    label = "Weight",
                    icon = Icons.Outlined.MonitorWeight,
                    active = current == Screen.WEIGHT,
                    onClick = { onNavigate(Screen.WEIGHT) },
                )
                NavTab(
                    label = "Data",
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                    active = current == Screen.ANALYTICS,
                    onClick = { onNavigate(Screen.ANALYTICS) },
                )
                NavTab(
                    label = "More",
                    icon = Icons.Outlined.MoreHoriz,
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
    val tint = if (active) MetriqColors.accent else MetriqColors.ink3
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.caption, color = tint, maxLines = 1)
    }
}

@Composable
fun MetriqButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
        label = "buttonPress",
    )
    Button(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        },
        interactionSource = interactionSource,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) MetriqColors.accent else MetriqColors.surface2,
            contentColor = if (primary) MetriqColors.accentInk else MetriqColors.ink,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyStrong, maxLines = 1)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Outlined.ChevronLeft,
                    contentDescription = "Back",
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
            .clip(CircleShape)
            .background(MetriqColors.surface2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val pillColor by animateColorAsState(
                targetValue = if (selected) MetriqColors.accent else Color.Transparent,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                label = "segmentBackground",
            )
            val labelColor by animateColorAsState(
                targetValue = if (selected) MetriqColors.accentInk else MetriqColors.ink2,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                label = "segmentLabel",
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(pillColor)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.label,
                    color = labelColor,
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
                    "No data yet",
                    padLeft + w / 2f - gridPaint.measureText("No data yet") / 2f,
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
