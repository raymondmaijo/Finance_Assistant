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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.Formatters
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.WarningOrange
import com.example.util.CategorySpendItem
import com.example.util.TimeSpendItem

enum class BarChartMode {
    TIME_INTERVAL,      // Daily / Weekly breakdown
    CATEGORY_SPEND      // By category spend
}

@Composable
fun SpendBarChart(
    timeItems: List<TimeSpendItem>,
    categoryItems: List<CategorySpendItem>,
    isWeekly: Boolean,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(BarChartMode.TIME_INTERVAL) }
    var selectedIndex by remember { mutableIntStateOf(-1) }

    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 700),
        label = "bar_anim"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("spend_bar_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (mode == BarChartMode.TIME_INTERVAL) {
                            if (isWeekly) "Daily Spend (Bar Chart)" else "Weekly Spend (Bar Chart)"
                        } else {
                            "Top Category Spend (Bar Chart)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap any bar to inspect exact transaction total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = mode == BarChartMode.TIME_INTERVAL,
                        onClick = {
                            mode = BarChartMode.TIME_INTERVAL
                            selectedIndex = -1
                        },
                        label = { Text(if (isWeekly) "Days" else "Weeks", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = mode == BarChartMode.CATEGORY_SPEND,
                        onClick = {
                            mode = BarChartMode.CATEGORY_SPEND
                            selectedIndex = -1
                        },
                        label = { Text("Categories", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Highlight Tooltip if bar selected
            val itemsCount = if (mode == BarChartMode.TIME_INTERVAL) timeItems.size else categoryItems.take(6).size
            val selectedInfo = if (selectedIndex in 0 until itemsCount) {
                if (mode == BarChartMode.TIME_INTERVAL) {
                    val item = timeItems[selectedIndex]
                    "${item.label} (${item.subLabel}): ${Formatters.formatRupee(item.amount)}"
                } else {
                    val item = categoryItems.take(6)[selectedIndex]
                    "${item.category}: ${Formatters.formatRupee(item.amount)} (${item.percentage.toInt()}%)"
                }
            } else null

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedInfo ?: "Tip: Tap bars below to inspect breakdown",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (selectedInfo != null) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (selectedInfo != null) {
                        Text(
                            text = "Dismiss",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { selectedIndex = -1 }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Canvas Bar Chart
            val values = if (mode == BarChartMode.TIME_INTERVAL) {
                timeItems.map { it.amount }
            } else {
                categoryItems.take(6).map { it.amount }
            }
            val labels = if (mode == BarChartMode.TIME_INTERVAL) {
                timeItems.map { it.label }
            } else {
                categoryItems.take(6).map { it.category.take(4) }
            }

            val maxVal = (values.maxOrNull() ?: 1.0).coerceAtLeast(100.0)
            val primaryColor = MaterialTheme.colorScheme.primary
            val secondaryColor = MaterialTheme.colorScheme.secondary
            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            val textColor = MaterialTheme.colorScheme.onSurfaceVariant

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .testTag("bar_chart_canvas_box")
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .pointerInput(values, mode) {
                            detectTapGestures { offset ->
                                val totalBars = values.size
                                if (totalBars == 0) return@detectTapGestures
                                val bottomMargin = 30.dp.toPx()
                                val chartHeight = size.height - bottomMargin
                                val barWidth = (size.width / totalBars) * 0.55f
                                val spacing = size.width / totalBars

                                for (i in 0 until totalBars) {
                                    val barX = (i * spacing) + (spacing - barWidth) / 2f
                                    if (offset.x in barX..(barX + barWidth)) {
                                        selectedIndex = if (selectedIndex == i) -1 else i
                                        break
                                    }
                                }
                            }
                        }
                ) {
                    val bottomMargin = 28.dp.toPx()
                    val chartHeight = size.height - bottomMargin
                    val totalBars = values.size
                    if (totalBars == 0) return@Canvas

                    // Draw 3 horizontal dashed guidelines
                    val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    for (step in 1..3) {
                        val y = chartHeight * (1f - (step / 3f))
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashedEffect
                        )
                    }

                    // Baseline
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, chartHeight),
                        end = Offset(size.width, chartHeight),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    // Draw Bars
                    val spacing = size.width / totalBars
                    val barWidth = (spacing * 0.55f).coerceIn(12.dp.toPx(), 36.dp.toPx())

                    for (i in 0 until totalBars) {
                        val value = values[i]
                        val isSelected = selectedIndex == i
                        val isMax = value == values.maxOrNull() && value > 0
                        val barHeight = ((value / maxVal) * chartHeight * animationProgress).toFloat()
                            .coerceAtLeast(if (value > 0) 6.dp.toPx() else 2.dp.toPx())

                        val barX = (i * spacing) + (spacing - barWidth) / 2f
                        val barY = chartHeight - barHeight

                        val barColor = when {
                            isSelected -> ExpenseRed
                            isMax -> EmeraldLight
                            mode == BarChartMode.CATEGORY_SPEND -> categoryItems.getOrNull(i)?.color ?: primaryColor
                            else -> primaryColor.copy(alpha = 0.85f)
                        }

                        // Bar
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(barX, barY),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                    }
                }
            }

            // Labels under bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                labels.forEachIndexed { index, label ->
                    val isSelected = selectedIndex == index
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
