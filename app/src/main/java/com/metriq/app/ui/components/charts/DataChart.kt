package com.metriq.app.ui.components.charts

import android.graphics.Paint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metriq.app.ui.analytics.AnalyticsFormat
import com.metriq.app.ui.analytics.DataPoint
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.app.ui.theme.caption
import com.metriq.app.ui.theme.number24
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

private data class Geom(val left: Dp, val top: Dp, val width: Dp, val height: Dp, val totalWidth: Dp)

private const val CHART_HEIGHT_DP = 240

/**
 * Interactive analytics chart. The primary series owns the x axis and its own y scale;
 * the optional compare series is drawn against its own scale so different units overlay
 * honestly. Tapping or horizontal-drag scrubbing reveals a crosshair and an instant tooltip.
 */
@Composable
fun DataChart(
    points: List<DataPoint>,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    comparePoints: List<DataPoint> = emptyList(),
    compareColor: Color = Color.Unspecified,
    chartType: String = "line",
    selectedIndex: Int? = null,
    onSelect: (Int?) -> Unit = {},
    metricId: String = "intake",
    compareMetricId: String? = null,
    xLabel: (LocalDate) -> String = { AnalyticsFormat.date(it) },
    reducedMotion: Boolean = false,
) {
    // A fresh keyed child replays exactly one 260 ms reveal whenever the data changes.
    key(points to comparePoints) {
        ChartSurface(
            points = points,
            primaryColor = primaryColor,
            modifier = modifier,
            comparePoints = comparePoints,
            compareColor = compareColor,
            chartType = chartType,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            metricId = metricId,
            compareMetricId = compareMetricId,
            xLabel = xLabel,
            reducedMotion = reducedMotion,
        )
    }
}

@Composable
private fun ChartSurface(
    points: List<DataPoint>,
    primaryColor: Color,
    modifier: Modifier,
    comparePoints: List<DataPoint>,
    compareColor: Color,
    chartType: String,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    metricId: String,
    compareMetricId: String?,
    xLabel: (LocalDate) -> String,
    reducedMotion: Boolean,
) {
    var target by remember { mutableFloatStateOf(if (reducedMotion) 1f else 0f) }
    val reveal by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "analyticsChartReveal",
    )
    LaunchedEffect(Unit) { target = 1f }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT_DP.dp)
            .graphicsLayer(
                alpha = reveal,
                translationY = (1f - reveal) * 8f,
            ),
    ) {
        val density = LocalDensity.current
        val geom = Geom(
            left = 46.dp,
            top = 14.dp,
            width = (maxWidth - 46.dp - 14.dp).coerceAtLeast(1.dp),
            height = (CHART_HEIGHT_DP.dp - 14.dp - 30.dp).coerceAtLeast(1.dp),
            totalWidth = maxWidth,
        )
        val isBars = chartType == "bar"
        val scale = scaleFor(points.map { it.value }, isBars)
        val compareScale = scaleFor(comparePoints.map { it.value }, isBars)
        val compareInk = if (compareColor == Color.Unspecified) MetriqColors.ink2 else compareColor

        fun xAt(index: Int, count: Int): Dp = if (count <= 1) {
            geom.left + geom.width / 2f
        } else {
            geom.left + geom.width * (index.toFloat() / (count - 1).toFloat())
        }

        fun yAt(value: Double, range: Pair<Double, Double>): Dp =
            geom.top + geom.height * (1f - ((value - range.first) / (range.second - range.first))
                .toFloat().coerceIn(0f, 1f))

        fun nearestIndex(xPx: Float): Int {
            if (points.isEmpty()) return 0
            val xDp = with(density) { xPx.toDp() }
            val fraction = ((xDp - geom.left) / geom.width).coerceIn(0f, 1f)
            return (fraction * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)
        }

        Canvas(Modifier.fillMaxSize()) {
            val l = geom.left.toPx()
            val t = geom.top.toPx()
            val w = geom.width.toPx()
            val h = geom.height.toPx()
            val inkPaint = Paint().apply {
                isAntiAlias = true
                color = MetriqColors.ink3.toAndroidArgb()
                textSize = 11.dp.toPx()
                textAlign = Paint.Align.LEFT
            }

            // Four faint gridlines with compact y ticks.
            for (i in 0..3) {
                val y = t + h * i / 3f
                drawLine(
                    color = MetriqColors.border.copy(alpha = 0.55f),
                    start = Offset(l, y),
                    end = Offset(l + w, y),
                    strokeWidth = 1.dp.toPx(),
                )
                val tick = scale.second - (scale.second - scale.first) * i / 3.0
                drawContext.canvas.nativeCanvas.drawText(
                    AnalyticsFormat.axis(tick),
                    l - 6.dp.toPx(),
                    y + 3.5.dp.toPx(),
                    inkPaint.apply { textAlign = Paint.Align.RIGHT },
                )
            }

            if (points.isEmpty()) {
                inkPaint.textAlign = Paint.Align.CENTER
                drawContext.canvas.nativeCanvas.drawText(
                    "No data in this range",
                    l + w / 2f,
                    t + h / 2f,
                    inkPaint,
                )
            } else {
                // Four to six x labels, never rotated, centred on their tick.
                val indices = xLabelIndices(points.size, geom.totalWidth)
                inkPaint.textAlign = Paint.Align.CENTER
                indices.forEach { index ->
                    drawContext.canvas.nativeCanvas.drawText(
                        xLabel(points[index].date),
                        xAt(index, points.size).toPx(),
                        size.height - 9.dp.toPx(),
                        inkPaint,
                    )
                }

                if (isBars) {
                    val slot = w / points.size
                    val barW = minOf(slot * 0.62f, 22.dp.toPx())
                    if (comparePoints.isNotEmpty()) {
                        val compareSlot = w / comparePoints.size
                        val compareW = minOf(compareSlot * 0.62f, 22.dp.toPx())
                        comparePoints.forEachIndexed { index, point ->
                            val x = xAt(index, comparePoints.size).toPx()
                            val y = yAt(point.value, compareScale).toPx()
                            drawRoundRect(
                                color = compareInk.copy(alpha = 0.45f),
                                topLeft = Offset(x - compareW / 2f, y),
                                size = Size(compareW, (t + h - y).coerceAtLeast(0f)),
                                cornerRadius = CornerRadius(3.dp.toPx()),
                            )
                        }
                    }
                    points.forEachIndexed { index, point ->
                        val x = xAt(index, points.size).toPx()
                        val y = yAt(point.value, scale).toPx()
                        drawRoundRect(
                            color = primaryColor.copy(
                                alpha = if (selectedIndex != null && selectedIndex != index) 0.55f else 1f,
                            ),
                            topLeft = Offset(x - barW / 2f, y),
                            size = Size(barW, (t + h - y).coerceAtLeast(0f)),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                    }
                } else {
                    fun drawSeries(series: List<DataPoint>, range: Pair<Double, Double>, color: Color, dashed: Boolean) {
                        if (series.isEmpty()) return
                        val stroke = Stroke(
                            width = if (dashed) 1.8.dp.toPx() else 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(9f, 7f), 0f) else null,
                        )
                        if (series.size == 1) {
                            drawCircle(
                                color = color,
                                radius = 4.dp.toPx(),
                                center = Offset(xAt(0, 1).toPx(), yAt(series[0].value, range).toPx()),
                            )
                            return
                        }
                        val path = Path()
                        series.forEachIndexed { index, point ->
                            val x = xAt(index, series.size).toPx()
                            val y = yAt(point.value, range).toPx()
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color = color, style = stroke)
                    }
                    drawSeries(comparePoints, compareScale, compareInk.copy(alpha = 0.9f), dashed = true)
                    drawSeries(points, scale, primaryColor, dashed = false)
                }

                val index = selectedIndex?.takeIf { it in points.indices }
                if (index != null) {
                    val x = xAt(index, points.size).toPx()
                    val y = yAt(points[index].value, scale).toPx()
                    drawLine(
                        color = MetriqColors.ink2.copy(alpha = 0.7f),
                        start = Offset(x, t),
                        end = Offset(x, t + h),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawCircle(color = MetriqColors.bg, radius = 6.dp.toPx(), center = Offset(x, y))
                    drawCircle(color = primaryColor, radius = 4.dp.toPx(), center = Offset(x, y))
                    val compareIndex = comparePoints.indexOfFirst { it.date == points[index].date }
                    if (compareIndex >= 0) {
                        drawCircle(
                            color = compareInk,
                            radius = 3.5.dp.toPx(),
                            center = Offset(
                                xAt(compareIndex, comparePoints.size).toPx(),
                                yAt(comparePoints[compareIndex].value, compareScale).toPx(),
                            ),
                        )
                    }
                }
            }
        }

        val selected = selectedIndex?.takeIf { it in points.indices }?.let { points[it] }
        if (selected != null) {
            val compareValue = comparePoints.firstOrNull { it.date == selected.date }
            val estimatedWidth = 160.dp
            val left = (xAt(selectedIndex!!, points.size) - estimatedWidth / 2f)
                .coerceIn(4.dp, (geom.totalWidth - estimatedWidth - 4.dp).coerceAtLeast(4.dp))
            Box(
                Modifier
                    .offset(x = left, y = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MetriqColors.surface2)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Column {
                    Text(
                        text = xLabel(selected.date),
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink2,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(primaryColor))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${AnalyticsFormat.value(metricId, selected.value)} ${AnalyticsFormat.unit(metricId)}",
                            style = MaterialTheme.typography.number24,
                            color = MetriqColors.ink,
                            maxLines = 1,
                        )
                    }
                    if (compareValue != null && compareMetricId != null) {
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(compareInk))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${AnalyticsFormat.label(compareMetricId)} " +
                                    AnalyticsFormat.value(compareMetricId, compareValue.value) +
                                    " ${AnalyticsFormat.unit(compareMetricId)}",
                                style = MaterialTheme.typography.caption,
                                color = MetriqColors.ink2,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(points.size) {
                    detectTapGestures { offset ->
                        if (points.isEmpty()) return@detectTapGestures
                        val index = nearestIndex(offset.x)
                        onSelect(if (index == selectedIndex) null else index)
                    }
                }
                .pointerInput(points.size) {
                    detectHorizontalDragGestures { change, _ ->
                        if (points.isEmpty()) return@detectHorizontalDragGestures
                        onSelect(nearestIndex(change.position.x))
                    }
                },
        )
    }
}

private fun scaleFor(values: List<Double>, bars: Boolean): Pair<Double, Double> {
    val finite = values.filter { it.isFinite() }
    if (finite.isEmpty()) return 0.0 to 1.0
    val low = finite.minOrNull()!!
    val high = finite.maxOrNull()!!
    if (bars) return 0.0 to (if (high > 0) high * 1.15 else 1.0)
    if (low == high) return (low - 1.0) to (high + 1.0)
    val pad = (high - low) * 0.12
    return (low - pad) to (high + pad)
}

private fun xLabelIndices(size: Int, width: Dp): List<Int> {
    if (size <= 0) return emptyList()
    if (size == 1) return listOf(0)
    val slots = if (width < 360.dp) 4 else 6
    val count = minOf(slots, size)
    return (0 until count).map { it * (size - 1) / (count - 1).coerceAtLeast(1) }.distinct()
}

private fun Color.toAndroidArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt().coerceIn(0, 255),
    (red * 255).toInt().coerceIn(0, 255),
    (green * 255).toInt().coerceIn(0, 255),
    (blue * 255).toInt().coerceIn(0, 255),
)
