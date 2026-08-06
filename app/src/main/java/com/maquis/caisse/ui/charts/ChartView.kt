package com.maquis.caisse.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.ChartPoint
import com.maquis.caisse.domain.model.ChartSlice
import com.maquis.caisse.domain.model.ChartType
import com.maquis.caisse.domain.model.StatsPeriod
import com.maquis.caisse.ui.common.GlassCard
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import com.maquis.caisse.ui.theme.GestionBlue
import com.maquis.caisse.ui.theme.GestionCyan
import com.maquis.caisse.ui.theme.GestionSuccess
import com.maquis.caisse.ui.theme.GestionWarning
import kotlin.math.min

val ChartPalette = listOf(
    GestionBlue,
    GestionCyan,
    GestionSuccess,
    GestionWarning,
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
    Color(0xFF14B8A6),
    Color(0xFFF97316),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeriodSelector(
    selected: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatsPeriod.entries.forEach { period ->
            TextPill(
                text = period.label,
                tone = if (period == selected) PillTone.INFO else PillTone.NEUTRAL,
                modifier = Modifier
                    .padding(0.dp)
                    .then(
                        Modifier.clickableNoIndication { onSelect(period) },
                    ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartTypeSelector(
    selected: ChartType,
    onSelect: (ChartType) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ChartType.entries.forEach { type ->
            TextPill(
                text = type.label,
                tone = if (type == selected) PillTone.CYAN else PillTone.NEUTRAL,
                modifier = Modifier.clickableNoIndication { onSelect(type) },
            )
        }
    }
}

private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

/**
 * Carte graphe complète : titre, période, type, rendu.
 */
@Composable
fun ChartCard(
    title: String,
    subtitle: String? = null,
    points: List<ChartPoint>,
    chartType: ChartType,
    onChartTypeChange: (ChartType) -> Unit,
    period: StatsPeriod? = null,
    onPeriodChange: ((StatsPeriod) -> Unit)? = null,
    periodExtra: (@Composable () -> Unit)? = null,
    valueAsMoney: Boolean = true,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GestionBlue)
        if (subtitle != null) {
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        if (period != null && onPeriodChange != null) {
            Spacer(Modifier.height(6.dp))
            Text("Période", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PeriodSelector(selected = period, onSelect = onPeriodChange)
            periodExtra?.invoke()
        }
        Spacer(Modifier.height(6.dp))
        Text("Type de graphe", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChartTypeSelector(selected = chartType, onSelect = onChartTypeChange)
        Spacer(Modifier.height(10.dp))
        if (points.isEmpty() || points.all { it.value <= 0f }) {
            Text("Aucune donnée pour cette période", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlexibleChart(
                points = points,
                type = chartType,
                valueAsMoney = valueAsMoney,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (chartType == ChartType.BAR_HORIZONTAL) (points.size * 28).coerceIn(140, 320).dp else 200.dp),
            )
            if (chartType != ChartType.PIE) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    points.takeLast(12).forEach { p ->
                        Text(
                            "${p.label}: ${if (valueAsMoney) MoneyFormat.format(p.value.toLong()) else p.value.toInt()}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 10.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FlexibleChart(
    points: List<ChartPoint>,
    type: ChartType,
    valueAsMoney: Boolean = true,
    modifier: Modifier = Modifier,
) {
    when (type) {
        ChartType.CURVE -> CurveChart(points, modifier)
        ChartType.BAR_VERTICAL, ChartType.HISTOGRAM -> VerticalBarChart(points, modifier, histogram = type == ChartType.HISTOGRAM)
        ChartType.BAR_HORIZONTAL -> HorizontalBarsChart(points, modifier, valueAsMoney)
        ChartType.PIE -> PieChart(points.map { ChartSlice(it.label, it.value) }, modifier)
    }
}

@Composable
private fun CurveChart(points: List<ChartPoint>, modifier: Modifier) {
    val color = GestionBlue
    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas
        val maxV = points.maxOf { it.value }.coerceAtLeast(1f)
        val padL = 8f
        val padR = 8f
        val padT = 12f
        val padB = 28f
        val w = size.width - padL - padR
        val h = size.height - padT - padB
        val stepX = if (points.size == 1) 0f else w / (points.size - 1)

        // grille
        for (i in 0..3) {
            val y = padT + h * i / 3f
            drawLine(Color(0xFFD7E4F7), Offset(padL, y), Offset(padL + w, y), strokeWidth = 1f)
        }

        val path = Path()
        val fill = Path()
        points.forEachIndexed { i, p ->
            val x = padL + stepX * i
            val y = padT + h * (1f - p.value / maxV)
            if (i == 0) {
                path.moveTo(x, y)
                fill.moveTo(x, padT + h)
                fill.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(padL + stepX * (points.size - 1), padT + h)
        fill.close()
        drawPath(fill, color.copy(alpha = 0.12f))
        drawPath(path, color, style = Stroke(width = 4f, cap = StrokeCap.Round))
        points.forEachIndexed { i, p ->
            val x = padL + stepX * i
            val y = padT + h * (1f - p.value / maxV)
            drawCircle(color, radius = 5f, center = Offset(x, y))
        }
    }
}

@Composable
private fun VerticalBarChart(points: List<ChartPoint>, modifier: Modifier, histogram: Boolean) {
    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas
        val maxV = points.maxOf { it.value }.coerceAtLeast(1f)
        val padT = 12f
        val padB = 8f
        val gap = if (histogram) 2f else 8f
        val slot = size.width / points.size
        val barW = (slot - gap).coerceAtLeast(4f)
        points.forEachIndexed { i, p ->
            val h = (p.value / maxV) * (size.height - padT - padB)
            val left = slot * i + (slot - barW) / 2f
            val top = size.height - padB - h
            val color = ChartPalette[i % ChartPalette.size]
            if (histogram) {
                drawRect(color.copy(alpha = 0.85f), Offset(left, top), Size(barW, h.coerceAtLeast(2f)))
            } else {
                drawRoundRect(
                    color,
                    Offset(left, top),
                    Size(barW, h.coerceAtLeast(4f)),
                    CornerRadius(8f, 8f),
                )
            }
        }
    }
}

@Composable
private fun HorizontalBarsChart(points: List<ChartPoint>, modifier: Modifier, valueAsMoney: Boolean) {
    val maxV = points.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        points.forEachIndexed { i, p ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(p.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(
                        if (valueAsMoney) MoneyFormat.format(p.value.toLong()) else p.value.toInt().toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ChartPalette[i % ChartPalette.size],
                    )
                }
                Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                    val color = ChartPalette[i % ChartPalette.size]
                    drawRoundRect(color.copy(alpha = 0.15f), cornerRadius = CornerRadius(6f, 6f), size = size)
                    drawRoundRect(
                        color,
                        cornerRadius = CornerRadius(6f, 6f),
                        size = Size(size.width * (p.value / maxV), size.height),
                    )
                }
            }
        }
    }
}

@Composable
private fun PieChart(slices: List<ChartSlice>, modifier: Modifier) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)
    Row(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(180.dp),
        ) {
            var start = -90f
            val diameter = min(size.width, size.height) * 0.9f
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            slices.forEachIndexed { i, s ->
                val sweep = 360f * (s.value / total)
                drawArc(
                    color = ChartPalette[i % ChartPalette.size],
                    startAngle = start,
                    sweepAngle = sweep.coerceAtLeast(0.5f),
                    useCenter = true,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                )
                start += sweep
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            slices.take(8).forEachIndexed { i, s ->
                val pct = (100f * s.value / total).toInt()
                Text(
                    "${s.label} · $pct%",
                    style = MaterialTheme.typography.labelLarge,
                    color = ChartPalette[i % ChartPalette.size],
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

fun List<ChartSlice>.asPoints(): List<ChartPoint> =
    map { ChartPoint(label = it.label, value = it.value) }

fun categoricalPoints(labels: List<String>, values: List<Long>): List<ChartPoint> =
    labels.zip(values).map { (l, v) -> ChartPoint(label = l, value = v.toFloat()) }
