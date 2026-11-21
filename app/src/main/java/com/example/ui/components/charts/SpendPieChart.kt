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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CategoryIcons
import com.example.ui.components.Formatters
import com.example.util.CategorySpendItem
import kotlin.math.atan2

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpendPieChart(
    categories: List<CategorySpendItem>,
    totalSpent: Double,
    modifier: Modifier = Modifier
) {
    if (categories.isEmpty() || totalSpent <= 0.0) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag("pie_chart_empty"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No debit transactions recorded for this period to display category pie chart.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    var selectedCategory by remember { mutableStateOf<CategorySpendItem?>(null) }
    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800),
        label = "pie_anim"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("spend_pie_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Category Breakdown (Pie Chart)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap a slice or tag to inspect individual spending",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selectedCategory != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.clickable { selectedCategory = null }
                    ) {
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Donut Chart with center callout
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .testTag("donut_canvas_box"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .size(200.dp)
                        .pointerInput(categories) {
                            detectTapGestures { offset ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val dx = offset.x - center.x
                                val dy = offset.y - center.y
                                val distance = Math.hypot(dx.toDouble(), dy.toDouble())
                                val innerRadius = (size.width / 2f) * 0.55f
                                val outerRadius = (size.width / 2f)

                                if (distance in innerRadius..outerRadius) {
                                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                    if (angle < 0) angle += 360f
                                    // Offset by -90 deg since start angle is at top (-90)
                                    val normalizedAngle = (angle + 90f) % 360f

                                    var currentAngle = 0f
                                    for (item in categories) {
                                        val sweep = (item.percentage / 100f) * 360f
                                        if (normalizedAngle in currentAngle..(currentAngle + sweep)) {
                                            selectedCategory = if (selectedCategory == item) null else item
                                            break
                                        }
                                        currentAngle += sweep
                                    }
                                } else if (distance < innerRadius) {
                                    selectedCategory = null
                                }
                            }
                        }
                ) {
                    val strokeWidth = 36.dp.toPx()
                    val selectedStrokeWidth = 44.dp.toPx()
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                    var startAngle = -90f
                    for (item in categories) {
                        val isSelected = selectedCategory == item
                        val sweepAngle = ((item.percentage / 100f) * 360f) * animationProgress
                        val currentStroke = if (isSelected) selectedStrokeWidth else strokeWidth
                        val sliceColor = if (selectedCategory == null || isSelected) item.color else item.color.copy(alpha = 0.35f)

                        drawArc(
                            color = sliceColor,
                            startAngle = startAngle,
                            sweepAngle = (sweepAngle - 2f).coerceAtLeast(0.5f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = currentStroke, cap = StrokeCap.Round)
                        )
                        startAngle += sweepAngle
                    }
                }

                // Center Information Bubble
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (selectedCategory != null) {
                        Text(
                            text = selectedCategory!!.category,
                            style = MaterialTheme.typography.labelMedium,
                            color = selectedCategory!!.color,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = Formatters.formatRupee(selectedCategory!!.amount),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${String.format("%.1f", selectedCategory!!.percentage)}% • ${selectedCategory!!.count} txns",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Total Spent",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = Formatters.formatRupee(totalSpent),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${categories.size} Categories",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // FlowRow of Category Badges & Legend
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) cat.color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, cat.color) else null,
                        modifier = Modifier
                            .clickable { selectedCategory = if (isSelected) null else cat }
                            .testTag("pie_legend_${cat.category}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(cat.color, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = CategoryIcons.getIcon(cat.category),
                                contentDescription = null,
                                tint = cat.color,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = cat.category,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${Formatters.formatRupee(cat.amount)} (${cat.percentage.toInt()}%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
