package com.example.ui.components.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.Formatters
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.SkyTertiary
import com.example.ui.theme.WarningOrange
import com.example.util.CumulativeTrendPoint

@Composable
fun SpendTrendLineChart(
    trendPoints: List<CumulativeTrendPoint>,
    modifier: Modifier = Modifier
) {
    if (trendPoints.isEmpty()) return

    var selectedIndex by remember { mutableIntStateOf(-1) }
    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800),
        label = "line_anim"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("spend_trend_line_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Spending Velocity & Cumulative Line Graph",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Actual spend trajectory (Solid) vs Planned target pace (Dashed)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tooltip or legend
            val selectedPoint = if (selectedIndex in trendPoints.indices) trendPoints[selectedIndex] else null
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedPoint != null) {
                        Text(
                            text = "${selectedPoint.dayLabel}: Spent ${Formatters.formatRupee(selectedPoint.actualSpent)} (Target: ${Formatters.formatRupee(selectedPoint.targetPace)})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { selectedIndex = -1 }
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(SkyTertiary, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Actual Spend", style = MaterialTheme.typography.labelSmall)

                            Spacer(modifier = Modifier.width(12.dp))
                            Box(modifier = Modifier.size(8.dp).background(WarningOrange, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ideal Pace", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas Line & Gradient Area
            val maxVal = maxOf(
                trendPoints.maxOfOrNull { it.actualSpent } ?: 1.0,
                trendPoints.maxOfOrNull { it.targetPace } ?: 1.0,
                500.0
            )

            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            val strokeColor = SkyTertiary
            val targetLineColor = WarningOrange.copy(alpha = 0.8f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .testTag("trend_line_canvas_box")
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .pointerInput(trendPoints) {
                            detectTapGestures { offset ->
                                val total = trendPoints.size
                                if (total <= 1) return@detectTapGestures
                                val bottomMargin = 24.dp.toPx()
                                val chartWidth = size.width
                                val stepX = chartWidth / (total - 1)

                                val nearestIdx = Math.round(offset.x / stepX).toInt().coerceIn(0, total - 1)
                                selectedIndex = if (selectedIndex == nearestIdx) -1 else nearestIdx
                            }
                        }
                ) {
                    val bottomMargin = 20.dp.toPx()
                    val chartHeight = size.height - bottomMargin
                    val chartWidth = size.width
                    val total = trendPoints.size
                    if (total <= 1) return@Canvas

                    val stepX = chartWidth / (total - 1)

                    // Draw Horizontal Guidelines
                    val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    for (i in 1..2) {
                        val y = chartHeight * (1f - (i / 2f))
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(chartWidth, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashedEffect
                        )
                    }

                    // Target pace dashed line
                    val targetPath = Path()
                    trendPoints.forEachIndexed { i, pt ->
                        val x = i * stepX
                        val y = (chartHeight - ((pt.targetPace / maxVal) * chartHeight * animationProgress)).toFloat().coerceIn(0f, chartHeight)
                        if (i == 0) targetPath.moveTo(x, y) else targetPath.lineTo(x, y)
                    }
                    drawPath(
                        path = targetPath,
                        color = targetLineColor,
                        style = Stroke(width = 2.dp.toPx(), pathEffect = dashedEffect, cap = StrokeCap.Round)
                    )

                    // Actual Spend line & gradient fill
                    val fillPath = Path()
                    val strokePath = Path()

                    trendPoints.forEachIndexed { i, pt ->
                        val x = i * stepX
                        val y = (chartHeight - ((pt.actualSpent / maxVal) * chartHeight * animationProgress)).toFloat().coerceIn(0f, chartHeight)
                        if (i == 0) {
                            fillPath.moveTo(x, chartHeight)
                            fillPath.lineTo(x, y)
                            strokePath.moveTo(x, y)
                        } else {
                            fillPath.lineTo(x, y)
                            strokePath.lineTo(x, y)
                        }
                    }
                    fillPath.lineTo(chartWidth, chartHeight)
                    fillPath.close()

                    // Draw Area Gradient
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(strokeColor.copy(alpha = 0.35f), Color.Transparent),
                            startY = 0f,
                            endY = chartHeight
                        )
                    )

                    // Draw Stroke Line
                    drawPath(
                        path = strokePath,
                        color = strokeColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw Nodes
                    trendPoints.forEachIndexed { i, pt ->
                        val x = i * stepX
                        val y = (chartHeight - ((pt.actualSpent / maxVal) * chartHeight * animationProgress)).toFloat().coerceIn(0f, chartHeight)
                        val isSelected = selectedIndex == i

                        drawCircle(
                            color = if (isSelected) EmeraldLight else strokeColor,
                            radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx(),
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = if (isSelected) 3.dp.toPx() else 2.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
            }

            // Labels under trend graph
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                trendPoints.forEachIndexed { idx, pt ->
                    Text(
                        text = pt.dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selectedIndex == idx) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedIndex == idx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
