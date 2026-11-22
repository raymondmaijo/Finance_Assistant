package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.SkyTertiary
import kotlin.math.abs
import kotlin.math.max

/**
 * Single parameter in an AI Coach visual bar comparison chart.
 */
data class ComparisonMetricBar(
    val label: String,
    val shortLabel: String,
    val currentValue: Double,
    val projectedValue: Double,
    val currentFormatted: String,
    val projectedFormatted: String,
    val deltaLabel: String,
    val isLowerBetter: Boolean = false
)

/**
 * Structured visual bar-chart analysis payload rendered inside AI Coach chat bubbles.
 */
data class ChatVisualAnalysis(
    val title: String,
    val subtitle: String,
    val currentLegend: String = "Current",
    val projectedLegend: String = "After Change",
    val metrics: List<ComparisonMetricBar>
)

/**
 * Converts Markdown-flavored AI replies (**bold**, *italic*, ### headers, - bullets)
 * into clean, high-visibility Compose AnnotatedString with ZERO literal ** or * artifacts.
 */
fun parseRichChatText(
    rawText: String,
    baseColor: Color,
    boldColor: Color,
    headingColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        val lines = rawText.replace("\r\n", "\n").split("\n")
        lines.forEachIndexed { lineIndex, originalLine ->
            var line = originalLine.trimEnd()

            // Detect markdown headings (#, ##, ###)
            val isMarkdownHeading = line.trimStart().startsWith("#")
            if (isMarkdownHeading) {
                line = line.trimStart().trimStart('#').trimStart()
            }

            // Convert markdown bullets ("- " or "* ") into crisp typographic bullets
            val trimmedStart = line.trimStart()
            if (trimmedStart.startsWith("- ") || trimmedStart.startsWith("* ")) {
                line = "• " + trimmedStart.drop(2)
            }

            // First non-empty title line with emoji or heading gets elevated heading treatment
            val isHeadingLine = isMarkdownHeading ||
                (lineIndex == 0 && (line.contains("**") || line.length <= 72))

            if (isHeadingLine) {
                val cleanedHeading = line
                    .replace("**", "")
                    .replace("__", "")
                    .replace("*", "")
                pushStyle(
                    SpanStyle(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = headingColor,
                        letterSpacing = 0.1.sp
                    )
                )
                append(cleanedHeading)
                pop()
            } else {
                appendInlineMarkdown(
                    line = line,
                    baseColor = baseColor,
                    boldColor = boldColor
                )
            }

            if (lineIndex < lines.lastIndex) {
                append("\n")
            }
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    line: String,
    baseColor: Color,
    boldColor: Color
) {
    // Match **bold**, __bold__, or *italic* segments in order
    val tokenRegex = Regex("""(\*\*[^*]+\*\*|__[^_]+__|\*[^*\n]+\*)""")
    var cursor = 0

    for (match in tokenRegex.findAll(line)) {
        if (match.range.first > cursor) {
            val plain = line.substring(cursor, match.range.first)
                .replace("**", "")
                .replace("__", "")
                .replace("*", "")
            pushStyle(SpanStyle(color = baseColor, fontSize = 16.sp))
            append(plain)
            pop()
        }

        val token = match.value
        when {
            token.startsWith("**") && token.endsWith("**") -> {
                val content = token.removeSurrounding("**")
                pushStyle(
                    SpanStyle(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = boldColor,
                        letterSpacing = 0.15.sp
                    )
                )
                append(content)
                pop()
            }
            token.startsWith("__") && token.endsWith("__") -> {
                val content = token.removeSurrounding("__")
                pushStyle(
                    SpanStyle(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = boldColor
                    )
                )
                append(content)
                pop()
            }
            token.startsWith("*") && token.endsWith("*") -> {
                val content = token.removeSurrounding("*")
                pushStyle(
                    SpanStyle(
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.5.sp,
                        color = baseColor.copy(alpha = 0.85f)
                    )
                )
                append(content)
                pop()
            }
        }
        cursor = match.range.last + 1
    }

    if (cursor < line.length) {
        val trailing = line.substring(cursor)
            .replace("**", "")
            .replace("__", "")
            .replace("*", "")
        pushStyle(SpanStyle(color = baseColor, fontSize = 16.sp))
        append(trailing)
        pop()
    }
}

/**
 * Interactive Grouped Comparison Bar Chart + Parameter Breakdown Card
 * embedded directly inside Finance Coach AI replies.
 */
@Composable
fun ChatVisualAnalysisCard(
    analysis: ChatVisualAnalysis,
    modifier: Modifier = Modifier
) {
    if (analysis.metrics.isEmpty()) return

    var animateBars by remember(analysis) { mutableStateOf(false) }
    var selectedIndex by remember(analysis) { mutableIntStateOf(0) }

    LaunchedEffect(analysis) {
        animateBars = true
    }

    val animProgress by animateFloatAsState(
        targetValue = if (animateBars) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 260f),
        label = "chat_bar_chart_progress"
    )

    val safeSelectedIdx = selectedIndex.coerceIn(0, analysis.metrics.lastIndex)
    val highlighted = analysis.metrics[safeSelectedIdx]
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val subtleBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("chat_visual_analysis_card"),
        shape = RoundedCornerShape(16.dp),
        color = surfaceColor,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(EmeraldPrimary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = "Visual comparison chart",
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = analysis.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = analysis.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Legend Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendChip(color = InfoBlue, label = analysis.currentLegend)
                LegendChip(color = EmeraldPrimary, label = analysis.projectedLegend)
                LegendChip(color = ExpenseRed, label = "Higher Cost / Dip")
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Highlighted Metric Callout Pill (tapping any bar updates this)
            val isHighlightedBetter = isMetricImproved(highlighted)
            val isHighlightedWorse = isMetricWorsened(highlighted)
            val calloutAccent = when {
                isHighlightedBetter -> EmeraldPrimary
                isHighlightedWorse -> ExpenseRed
                else -> SkyTertiary
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = calloutAccent.copy(alpha = 0.11f),
                border = BorderStroke(1.dp, calloutAccent.copy(alpha = 0.30f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chat_visual_selected_callout")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = highlighted.label,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${highlighted.currentFormatted}  →  ${highlighted.projectedFormatted}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = calloutAccent
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = calloutAccent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isHighlightedWorse) Icons.Default.TrendingDown else Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = highlighted.deltaLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Grouped Vertical Bar Chart (Canvas) across all 6+ parameters
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(subtleBg)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(152.dp)
                            .testTag("chat_grouped_bar_chart_canvas")
                    ) {
                        val chartWidth = size.width
                        val chartHeight = size.height
                        val count = analysis.metrics.size
                        val slotWidth = chartWidth / count
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

                        // Horizontal guide gridlines (25%, 50%, 75%, 100%)
                        for (step in 1..4) {
                            val y = chartHeight - (chartHeight * step / 4f)
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(chartWidth, y),
                                strokeWidth = 1.5f,
                                pathEffect = dashEffect
                            )
                        }

                        // Baseline axis
                        drawLine(
                            color = gridColor.copy(alpha = 0.9f),
                            start = Offset(0f, chartHeight),
                            end = Offset(chartWidth, chartHeight),
                            strokeWidth = 2f
                        )

                        analysis.metrics.forEachIndexed { idx, metric ->
                            val groupStart = idx * slotWidth
                            val isSelected = idx == safeSelectedIdx

                            // Subtle highlight column behind selected parameter
                            if (isSelected) {
                                drawRoundRect(
                                    color = EmeraldPrimary.copy(alpha = 0.10f),
                                    topLeft = Offset(groupStart + 2f, 2f),
                                    size = Size(slotWidth - 4f, chartHeight - 2f),
                                    cornerRadius = CornerRadius(10f, 10f)
                                )
                            }

                            // Per-parameter normalization so every parameter pair (₹, %, months) is crystal clear
                            val absCurrent = abs(metric.currentValue)
                            val absProjected = abs(metric.projectedValue)
                            val pairMax = max(max(absCurrent, absProjected), 1.0)

                            val currentRatio = (absCurrent / pairMax).toFloat().coerceIn(0.06f, 1f)
                            val projectedRatio = (absProjected / pairMax).toFloat().coerceIn(0.06f, 1f)

                            val maxBarHeight = chartHeight * 0.88f
                            val curBarH = maxBarHeight * currentRatio * animProgress
                            val projBarH = maxBarHeight * projectedRatio * animProgress

                            val barWidth = (slotWidth * 0.30f).coerceIn(10f, 28f)
                            val barGap = (slotWidth * 0.08f).coerceIn(3f, 8f)
                            val pairTotalW = barWidth * 2 + barGap
                            val pairLeft = groupStart + (slotWidth - pairTotalW) / 2f

                            val projectedColor = when {
                                isMetricWorsened(metric) -> ExpenseRed
                                isMetricImproved(metric) -> EmeraldPrimary
                                else -> EmeraldLight
                            }

                            // Current Bar (Blue)
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(InfoBlue, SkyTertiary)
                                ),
                                topLeft = Offset(pairLeft, chartHeight - curBarH),
                                size = Size(barWidth, curBarH),
                                cornerRadius = CornerRadius(6f, 6f)
                            )

                            // Projected / Consequent Bar (Emerald or Red)
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(projectedColor, projectedColor.copy(alpha = 0.8f))
                                ),
                                topLeft = Offset(pairLeft + barWidth + barGap, chartHeight - projBarH),
                                size = Size(barWidth, projBarH),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Interactive X-Axis Labels (tap any parameter to inspect)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        analysis.metrics.forEachIndexed { idx, metric ->
                            val isSelected = idx == safeSelectedIdx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) EmeraldPrimary.copy(alpha = 0.16f)
                                        else Color.Transparent
                                    )
                                    .clickable { selectedIndex = idx }
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = metric.shortLabel,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isSelected) EmeraldDark else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    lineHeight = 12.sp,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Tap any parameter below or above to highlight (${analysis.metrics.size} parameters)",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Detailed Current vs Consequent Value Table with Dual Progress Bars
            analysis.metrics.forEachIndexed { idx, metric ->
                val isSelected = idx == safeSelectedIdx
                val improved = isMetricImproved(metric)
                val worsened = isMetricWorsened(metric)
                val projectedColor = when {
                    worsened -> ExpenseRed
                    improved -> EmeraldPrimary
                    else -> SkyTertiary
                }
                val pairMax = max(max(abs(metric.currentValue), abs(metric.projectedValue)), 1.0)
                val curFrac = (abs(metric.currentValue) / pairMax).toFloat().coerceIn(0.05f, 1f) * animProgress
                val projFrac = (abs(metric.projectedValue) / pairMax).toFloat().coerceIn(0.05f, 1f) * animProgress

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) EmeraldPrimary.copy(alpha = 0.08f) else Color.Transparent
                        )
                        .clickable { selectedIndex = idx }
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                        .testTag("chat_visual_metric_$idx")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = metric.label,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = projectedColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = metric.deltaLabel,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = projectedColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // Current Bar Row
                    ComparisonMiniBarRow(
                        tag = analysis.currentLegend,
                        valueText = metric.currentFormatted,
                        fraction = curFrac,
                        barColor = InfoBlue
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    // Projected Bar Row
                    ComparisonMiniBarRow(
                        tag = analysis.projectedLegend,
                        valueText = metric.projectedFormatted,
                        fraction = projFrac,
                        barColor = projectedColor
                    )
                }

                if (idx < analysis.metrics.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonMiniBarRow(
    tag: String,
    valueText: String,
    fraction: Float,
    barColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tag,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(66.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = valueText,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.width(74.dp)
        )
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun isMetricImproved(metric: ComparisonMetricBar): Boolean {
    val diff = metric.projectedValue - metric.currentValue
    if (abs(diff) < 0.01) return false
    return if (metric.isLowerBetter) diff < 0 else diff > 0
}

private fun isMetricWorsened(metric: ComparisonMetricBar): Boolean {
    val diff = metric.projectedValue - metric.currentValue
    if (abs(diff) < 0.01) return false
    return if (metric.isLowerBetter) diff > 0 else diff < 0
}
