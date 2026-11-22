package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.DebtPayoffProjection
import com.example.ai.InterestRateImpactPoint
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.SkyTertiary
import com.example.ui.theme.WarningOrange
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Dual-line Canvas chart showing remaining debt balance over time:
 * Baseline (Current APR & Min Payment) vs Projected (Modified APR & Extra Payment).
 */
@Composable
fun PayoffBalanceCurveChart(
    baseline: DebtPayoffProjection,
    projected: DebtPayoffProjection,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val startBalance = baseline.debt.currentBalance.coerceAtLeast(1.0)
    val maxBalance = max(
        startBalance,
        max(
            baseline.schedule.maxOfOrNull { it.remainingBalance } ?: startBalance,
            projected.schedule.maxOfOrNull { it.remainingBalance } ?: startBalance
        )
    )
    val maxMonths = max(
        max(baseline.schedule.size, projected.schedule.size),
        1
    )

    val isProjectedBetter = projected.totalInterestPaid <= baseline.totalInterestPaid + 1.0 &&
        projected.monthsToPayoff <= baseline.monthsToPayoff
    val projectedColor = if (isProjectedBetter) EmeraldPrimary else ExpenseRed

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DebtChartLegendDot(color = InfoBlue, label = "Current Plan (${baseline.effectiveApr}%)")
            DebtChartLegendDot(
                color = projectedColor,
                label = "Projected (${String.format(Locale.ENGLISH, "%.2f", projected.effectiveApr)}%)"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .testTag("debt_payoff_curve_canvas")
            ) {
                val w = size.width
                val h = size.height
                val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

                // Horizontal gridlines
                for (i in 0..4) {
                    val y = h * (i / 4f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.4f,
                        pathEffect = if (i < 4) dash else null
                    )
                }

                fun buildBalancePoints(proj: DebtPayoffProjection): List<Offset> {
                    val pts = mutableListOf<Offset>()
                    pts.add(Offset(0f, h - ((startBalance / maxBalance).toFloat() * h * 0.92f)))
                    proj.schedule.forEach { row ->
                        val x = ((row.monthIndex.toFloat() / maxMonths.toFloat()) * w).coerceIn(0f, w)
                        val y = h - ((row.remainingBalance / maxBalance).toFloat().coerceIn(0f, 1f) * h * 0.92f)
                        pts.add(Offset(x, y))
                    }
                    return pts
                }

                val basePts = buildBalancePoints(baseline)
                val projPts = buildBalancePoints(projected)

                // Projected area fill
                if (projPts.size >= 2) {
                    val fillPath = Path().apply {
                        moveTo(projPts.first().x, h)
                        projPts.forEach { lineTo(it.x, it.y) }
                        lineTo(projPts.last().x, h)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                projectedColor.copy(alpha = 0.25f),
                                projectedColor.copy(alpha = 0.02f)
                            )
                        )
                    )
                }

                // Baseline curve (Blue dashed)
                if (basePts.size >= 2) {
                    val basePath = Path().apply {
                        moveTo(basePts.first().x, basePts.first().y)
                        basePts.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = basePath,
                        color = InfoBlue,
                        style = Stroke(
                            width = 5f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
                        )
                    )
                }

                // Projected curve (Solid Emerald or Red)
                if (projPts.size >= 2) {
                    val projPath = Path().apply {
                        moveTo(projPts.first().x, projPts.first().y)
                        projPts.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = projPath,
                        color = projectedColor,
                        style = Stroke(
                            width = 6.5f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    // Endpoint highlight dot
                    drawCircle(
                        color = projectedColor,
                        radius = 7f,
                        center = projPts.last()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Today (${Formatters.formatRupee(startBalance)})",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Month ${maxMonths / 2}",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Month $maxMonths",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * 5-bar sensitivity chart showing Total Interest Cost across Interest Rate (APR) tiers.
 */
@Composable
fun InterestRateImpactBarChart(
    points: List<InterestRateImpactPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    var playAnim by remember(points) { mutableStateOf(false) }
    LaunchedEffect(points) { playAnim = true }
    val progress by animateFloatAsState(
        targetValue = if (playAnim) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 240f),
        label = "apr_impact_bars"
    )

    val maxInterest = points.maxOfOrNull { it.totalInterest }?.coerceAtLeast(1.0) ?: 1.0
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                .padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top value tags
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    points.forEach { pt ->
                        Text(
                            text = if (pt.isAmortizing) formatCompactRupee(pt.totalInterest) else "Trap!",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                !pt.isAmortizing || pt.deltaApr > 0.05 -> ExpenseRed
                                abs(pt.deltaApr) < 0.05 -> SkyTertiary
                                else -> EmeraldPrimary
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .testTag("debt_apr_bar_chart_canvas")
                ) {
                    val w = size.width
                    val h = size.height
                    val slotW = w / points.size
                    val dash = PathEffect.dashPathEffect(floatArrayOf(7f, 7f), 0f)

                    for (step in 1..3) {
                        val y = h - (h * step / 3f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.2f,
                            pathEffect = dash
                        )
                    }

                    points.forEachIndexed { idx, pt ->
                        val ratio = if (pt.isAmortizing) {
                            (pt.totalInterest / maxInterest).toFloat().coerceIn(0.08f, 1f)
                        } else 1f
                        val barH = h * 0.92f * ratio * progress
                        val barW = (slotW * 0.48f).coerceIn(18f, 44f)
                        val left = idx * slotW + (slotW - barW) / 2f

                        val barColor = when {
                            !pt.isAmortizing -> ExpenseRed
                            pt.deltaApr < -0.05 -> EmeraldPrimary
                            abs(pt.deltaApr) <= 0.05 -> InfoBlue
                            pt.deltaApr <= 1.5 -> WarningOrange
                            else -> ExpenseRed
                        }

                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(barColor, barColor.copy(alpha = 0.72f))
                            ),
                            topLeft = Offset(left, h - barH),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom APR labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    points.forEach { pt ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${String.format(Locale.ENGLISH, "%.1f", pt.aprPercent)}%",
                                fontSize = 11.5.sp,
                                fontWeight = if (abs(pt.deltaApr) < 0.05) FontWeight.ExtraBold else FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = pt.label,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Stacked horizontal bar illustrating Principal vs Interest composition of total payoff.
 */
@Composable
fun PrincipalVsInterestBar(
    principal: Double,
    totalInterest: Double,
    modifier: Modifier = Modifier
) {
    val total = (principal + totalInterest).coerceAtLeast(1.0)
    val principalRatio = (principal / total).toFloat().coerceIn(0.05f, 1f)
    val principalPct = (principalRatio * 100f).roundToInt()
    val interestPct = (100 - principalPct).coerceAtLeast(0)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Principal ($principalPct%): ${Formatters.formatRupee(principal)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary
            )
            Text(
                text = "Interest ($interestPct%): ${Formatters.formatRupee(totalInterest)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = ExpenseRed
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ExpenseRed.copy(alpha = 0.25f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(principalRatio)
                    .height(12.dp)
                    .background(EmeraldPrimary)
            )
            if (principalRatio < 0.99f) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .background(ExpenseRed)
                )
            }
        }
    }
}

@Composable
private fun DebtChartLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatCompactRupee(amount: Double): String {
    return when {
        amount >= 100000.0 -> "₹${String.format(Locale.ENGLISH, "%.1f", amount / 100000.0)}L"
        amount >= 1000.0 -> "₹${String.format(Locale.ENGLISH, "%.1f", amount / 1000.0)}k"
        else -> "₹${amount.roundToInt()}"
    }
}
