package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.DebtProjectionEngine
import com.example.data.model.DebtEntity
import com.example.ui.components.Formatters
import com.example.ui.components.InterestRateImpactBarChart
import com.example.ui.components.PayoffBalanceCurveChart
import com.example.ui.components.PrincipalVsInterestBar
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.SkyTertiary
import com.example.ui.theme.WarningOrange
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tab 2: Payment Schedule (Month-by-month amortization schedule with extra-payment boost chips).
 */
@Composable
fun DebtPaymentScheduleTab(
    debts: List<DebtEntity>,
    selectedDebtId: Long?,
    onSelectDebt: (Long) -> Unit
) {
    val activeDebt = debts.firstOrNull { it.id == selectedDebtId } ?: debts.firstOrNull() ?: return
    var extraPayment by remember(activeDebt.id) { mutableStateOf(0.0) }
    var showFullSchedule by remember(activeDebt.id) { mutableStateOf(false) }

    val baseProj = remember(activeDebt) {
        DebtProjectionEngine.buildPayoffProjection(activeDebt, extraMonthlyPayment = 0.0)
    }
    val proj = remember(activeDebt, extraPayment) {
        DebtProjectionEngine.buildPayoffProjection(activeDebt, extraMonthlyPayment = extraPayment)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("debt_schedule_tab"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DebtSelectorStrip(
            debts = debts,
            selectedId = activeDebt.id,
            onSelect = onSelectDebt
        )

        // Extra payment quick-boost chips
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Test Extra Monthly Payment",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.0, 500.0, 1500.0, 3000.0).forEach { boost ->
                        FilterChip(
                            selected = abs(extraPayment - boost) < 1.0,
                            onClick = { extraPayment = boost },
                            label = {
                                Text(
                                    text = if (boost == 0.0) "Min" else "+₹${boost.toInt()}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            modifier = Modifier.testTag("schedule_boost_${boost.toInt()}")
                        )
                    }
                }

                if (!proj.isAmortizing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ExpenseRed.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Payment (${Formatters.formatRupee(proj.monthlyPaymentUsed)}/mo) is below monthly interest (${Formatters.formatRupee(activeDebt.currentMonthlyInterest)}/mo). Increase payment to pay off this balance!",
                                style = MaterialTheme.typography.bodySmall,
                                color = ExpenseRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4 KPI chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScheduleKpiTile(
                        label = "Payoff Date",
                        value = proj.payoffDateLabel,
                        accent = if (proj.isAmortizing) EmeraldPrimary else ExpenseRed,
                        modifier = Modifier.weight(1f)
                    )
                    ScheduleKpiTile(
                        label = "Monthly Pay",
                        value = Formatters.formatRupee(proj.monthlyPaymentUsed),
                        accent = InfoBlue,
                        modifier = Modifier.weight(1f)
                    )
                    ScheduleKpiTile(
                        label = "Total Interest",
                        value = Formatters.formatRupee(proj.totalInterestPaid),
                        accent = ExpenseRed,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (extraPayment > 0 && baseProj.isAmortizing && proj.isAmortizing) {
                    val savedInterest = (baseProj.totalInterestPaid - proj.totalInterestPaid).coerceAtLeast(0.0)
                    val savedMonths = (baseProj.monthsToPayoff - proj.monthsToPayoff).coerceAtLeast(0)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EmeraldPrimary.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Adding +${Formatters.formatRupee(extraPayment)}/mo saves ${Formatters.formatRupee(savedInterest)} in interest and finishes $savedMonths months earlier!",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                PrincipalVsInterestBar(
                    principal = activeDebt.currentBalance,
                    totalInterest = proj.totalInterestPaid
                )
            }
        }

        // Amortization Table Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Amortization Schedule (Due ${activeDebt.dueDayOfMonth}th)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${proj.schedule.size} mo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                ) {
                    Text("Date", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1.1f))
                    Text("Principal", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text("Interest", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End, modifier = Modifier.weight(0.95f))
                    Text("Balance", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End, modifier = Modifier.weight(1.15f))
                }

                Spacer(modifier = Modifier.height(4.dp))

                val displayedRows = if (showFullSchedule) proj.schedule else proj.schedule.take(12)
                displayedRows.forEachIndexed { idx, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1.1f)) {
                            Text(
                                text = row.dueDateLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (row.isGracePeriod) {
                                Text("Grace", fontSize = 9.5.sp, color = WarningOrange, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = Formatters.formatRupee(row.principalPaid),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldPrimary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = Formatters.formatRupee(row.interestPaid),
                            fontSize = 12.sp,
                            color = ExpenseRed,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(0.95f)
                        )
                        Text(
                            text = Formatters.formatRupee(row.remainingBalance),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.15f)
                        )
                    }
                    if (idx < displayedRows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    }
                }

                if (proj.schedule.size > 12) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = { showFullSchedule = !showFullSchedule },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .testTag("toggle_full_schedule_btn")
                    ) {
                        Text(
                            text = if (showFullSchedule) "Show First 12 Months" else "Show All ${proj.schedule.size} Months",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tab 3: Interest Rate Impact Projections (Live APR delta slider + Extra Payment slider
 * + Dual Payoff Balance Curve + 5-Tier APR Sensitivity Bar Chart).
 */
@Composable
fun DebtInterestProjectionsTab(
    debts: List<DebtEntity>,
    selectedDebtId: Long?,
    onSelectDebt: (Long) -> Unit
) {
    val activeDebt = debts.firstOrNull { it.id == selectedDebtId } ?: debts.firstOrNull() ?: return

    val minSliderApr = (activeDebt.annualInterestRate - if (activeDebt.isCreditCard) 18.0 else 4.0).coerceAtLeast(0.0).toFloat()
    val maxSliderApr = (activeDebt.annualInterestRate + if (activeDebt.isCreditCard) 12.0 else 6.0).coerceAtMost(60.0).toFloat()

    var simulatedApr by remember(activeDebt.id) { mutableFloatStateOf(activeDebt.annualInterestRate.toFloat()) }
    var extraMonthly by remember(activeDebt.id) { mutableFloatStateOf(0f) }

    val baseline = remember(activeDebt) {
        DebtProjectionEngine.buildPayoffProjection(activeDebt)
    }
    val projected = remember(activeDebt, simulatedApr, extraMonthly) {
        DebtProjectionEngine.buildPayoffProjection(
            debt = activeDebt,
            aprOverride = simulatedApr.toDouble(),
            extraMonthlyPayment = extraMonthly.toDouble()
        )
    }
    val sensitivityPoints = remember(activeDebt, extraMonthly) {
        DebtProjectionEngine.buildInterestRateSensitivity(
            debt = activeDebt,
            extraMonthlyPayment = extraMonthly.toDouble()
        )
    }

    val interestDiff = projected.totalInterestPaid - baseline.totalInterestPaid
    val monthsDiff = projected.monthsToPayoff - baseline.monthsToPayoff

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("debt_projections_tab"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DebtSelectorStrip(
            debts = debts,
            selectedId = activeDebt.id,
            onSelect = onSelectDebt
        )

        // Interactive Rate & Extra Payment Simulator Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Percent, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Interest Rate Impact Simulator",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    TextButton(
                        onClick = {
                            simulatedApr = activeDebt.annualInterestRate.toFloat()
                            extraMonthly = 0f
                        }
                    ) {
                        Text("Reset", fontSize = 12.sp)
                    }
                }

                // APR Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Simulated APR",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${String.format(Locale.ENGLISH, "%.2f", simulatedApr)}% (Current: ${activeDebt.annualInterestRate}%)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (simulatedApr <= activeDebt.annualInterestRate) EmeraldPrimary else ExpenseRed
                    )
                }
                Slider(
                    value = simulatedApr,
                    onValueChange = { simulatedApr = (it * 4f).roundToInt() / 4f },
                    valueRange = minSliderApr..maxSliderApr,
                    colors = SliderDefaults.colors(
                        thumbColor = EmeraldPrimary,
                        activeTrackColor = EmeraldPrimary
                    ),
                    modifier = Modifier.testTag("debt_apr_slider")
                )

                // Extra Payment Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Extra Monthly Payment",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "+${Formatters.formatRupee(extraMonthly.toDouble())}/mo",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = InfoBlue
                    )
                }
                Slider(
                    value = extraMonthly,
                    onValueChange = { extraMonthly = ((it / 250f).roundToInt() * 250f) },
                    valueRange = 0f..10000f,
                    colors = SliderDefaults.colors(
                        thumbColor = InfoBlue,
                        activeTrackColor = InfoBlue
                    ),
                    modifier = Modifier.testTag("debt_extra_payment_slider")
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Live Impact KPI Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val interestLabel = when {
                        abs(interestDiff) < 1.0 -> "No change"
                        interestDiff < 0 -> "Save ${Formatters.formatRupee(abs(interestDiff))}"
                        else -> "+${Formatters.formatRupee(interestDiff)} cost"
                    }
                    ScheduleKpiTile(
                        label = "Interest Impact",
                        value = interestLabel,
                        accent = if (interestDiff <= 0.5) EmeraldPrimary else ExpenseRed,
                        modifier = Modifier.weight(1f)
                    )
                    val timeLabel = when {
                        !projected.isAmortizing -> "Never"
                        monthsDiff == 0 -> "${projected.monthsToPayoff} mo"
                        monthsDiff < 0 -> "${abs(monthsDiff)} mo faster"
                        else -> "+$monthsDiff mo longer"
                    }
                    ScheduleKpiTile(
                        label = "Timeline Shift",
                        value = timeLabel,
                        accent = if (monthsDiff <= 0 && projected.isAmortizing) EmeraldPrimary else ExpenseRed,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Payoff Balance Trajectory (Current vs Simulated)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                PayoffBalanceCurveChart(baseline = baseline, projected = projected)
            }
        }

        // 5-Point APR Sensitivity Bar Chart & Breakdown Table
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Interest Rate Sensitivity Analysis",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Total interest paid if your APR drops (refinance/EMI conversion) or rises (floating rate/penalty APR)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                InterestRateImpactBarChart(points = sensitivityPoints)

                Spacer(modifier = Modifier.height(12.dp))
                sensitivityPoints.forEachIndexed { idx, pt ->
                    val isBase = abs(pt.deltaApr) < 0.05
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isBase) InfoBlue.copy(alpha = 0.09f) else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1.1f)) {
                            Text(
                                text = "${String.format(Locale.ENGLISH, "%.2f", pt.aprPercent)}% APR",
                                fontSize = 13.sp,
                                fontWeight = if (isBase) FontWeight.ExtraBold else FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isBase) "Current Rate" else "${pt.label} change",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (pt.isAmortizing) "${pt.monthsToPayoff} mo" else "Trap",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (pt.isAmortizing) MaterialTheme.colorScheme.onSurface else ExpenseRed,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(0.75f)
                        )
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.weight(1.25f)
                        ) {
                            Text(
                                text = Formatters.formatRupee(pt.totalInterest),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val deltaStr = when {
                                isBase -> "Baseline"
                                pt.interestDeltaVsBase < 0 -> "-${Formatters.formatRupee(abs(pt.interestDeltaVsBase))} saved"
                                else -> "+${Formatters.formatRupee(pt.interestDeltaVsBase)} extra"
                            }
                            Text(
                                text = deltaStr,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isBase -> SkyTertiary
                                    pt.interestDeltaVsBase < 0 -> EmeraldPrimary
                                    else -> ExpenseRed
                                }
                            )
                        }
                    }
                    if (idx < sensitivityPoints.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DebtSelectorStrip(
    debts: List<DebtEntity>,
    selectedId: Long,
    onSelect: (Long) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(debts, key = { it.id }) { debt ->
            FilterChip(
                selected = debt.id == selectedId,
                onClick = { onSelect(debt.id) },
                label = {
                    Text(
                        text = "${debt.name} (${debt.annualInterestRate}%)",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (debt.id == selectedId) FontWeight.Bold else FontWeight.Medium
                    )
                },
                modifier = Modifier.testTag("select_debt_chip_${debt.id}")
            )
        }
    }
}

@Composable
private fun ScheduleKpiTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.10f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
