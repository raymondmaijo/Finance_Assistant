package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.Formatters
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.SkyTertiary
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.MainViewModel
import com.example.util.MonthlyReviewEngine
import java.util.Locale
import kotlin.math.abs

@Composable
fun MonthlyReviewSection(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val budgets by viewModel.allBudgets.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val recurring by viewModel.allRecurring.collectAsStateWithLifecycle()
    val goals by viewModel.allGoals.collectAsStateWithLifecycle()

    var monthOffset by remember { mutableIntStateOf(0) }

    val review = remember(monthOffset, transactions, budgets, profile, recurring, goals) {
        MonthlyReviewEngine.buildMonthlyReview(
            monthOffset = monthOffset,
            transactions = transactions,
            budgets = budgets,
            profile = profile,
            recurring = recurring,
            goals = goals
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("monthly_review_section"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Month Navigator + Export
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { monthOffset-- },
                        modifier = Modifier.testTag("review_prev_month_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Monthly Review",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = review.monthLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    IconButton(
                        onClick = { if (monthOffset < 0) monthOffset++ },
                        enabled = monthOffset < 0,
                        modifier = Modifier.testTag("review_next_month_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                    }
                }
            }
        }

        // Grade & Scorecard Hero
        item {
            val gradeColor = when (review.grade) {
                "A" -> EmeraldLight
                "B" -> SkyTertiary
                "C" -> WarningOrange
                else -> ExpenseRed
            }
            Card(
                modifier = Modifier.fillMaxWidth().testTag("monthly_review_scorecard"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = gradeColor.copy(alpha = 0.12f)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Monthly Financial Health",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${review.score} / 100",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Savings Rate: ${review.savingsRatePercent}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (review.savingsRatePercent >= 15) EmeraldPrimary else WarningOrange,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(gradeColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = review.grade,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReviewKpiPill("Income", Formatters.formatRupee(review.totalIncome), IncomeGreen, Modifier.weight(1f))
                        ReviewKpiPill("Fixed", Formatters.formatRupee(review.fixedCosts), WarningOrange, Modifier.weight(1f))
                        ReviewKpiPill("Variable", Formatters.formatRupee(review.variableSpend), ExpenseRed, Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Net Savings: ${Formatters.formatRupee(review.netSavings)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (review.netSavings >= 0) EmeraldPrimary else ExpenseRed
                        )
                        if (review.previousMonthSpend > 0) {
                            val sign = if (review.monthOverMonthDelta >= 0) "+" else "-"
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (review.monthOverMonthDelta <= 0) IncomeGreen.copy(alpha = 0.16f) else ExpenseRed.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "MoM $sign${abs(review.monthOverMonthDeltaPercent)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (review.monthOverMonthDelta <= 0) IncomeGreen else ExpenseRed,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Budget Adherence Table
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Budget Adherence", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    review.budgetRows.forEach { row ->
                        val barColor = when {
                            row.percentUsed > 100 -> ExpenseRed
                            row.percentUsed >= 85 -> WarningOrange
                            else -> EmeraldLight
                        }
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(row.category, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${Formatters.formatRupee(row.actual)} / ${Formatters.formatRupee(row.budgeted)} (${row.percentUsed}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = barColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (row.percentUsed / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = barColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Recurring Audit & Emergency Fund Snapshot
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Subscriptions & Safety Cushion", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Active Recurring Total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${Formatters.formatRupee(review.activeSubscriptionsTotal)}/mo", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Price Hikes / Unused Alerts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${review.priceChangedSubs.size} hikes • ${review.unusedSubs.size} unused",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (review.priceChangedSubs.isNotEmpty() || review.unusedSubs.isNotEmpty()) WarningOrange else EmeraldPrimary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Emergency Fund Runway", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${Formatters.formatRupee(review.emergencyFundCurrent)} (${String.format(Locale.ENGLISH, "%.1f", review.emergencyFundMonthsCovered)} mo)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = SkyTertiary
                        )
                    }
                }
            }
        }

        // Wins, Watchouts & Next Month Focus
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("monthly_review_takeaways"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (review.wins.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Monthly Wins", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        review.wins.forEach { w ->
                            Text("• $w", style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                        }
                    }

                    if (review.watchouts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Watchouts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        review.watchouts.forEach { w ->
                            Text("• $w", style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Flag, contentDescription = null, tint = SkyTertiary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Next Month Focus", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    review.nextMonthFocus.forEach { f ->
                        Text("• $f", style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Educational budgeting review only — does not recommend specific securities.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Export Review CTA
        item {
            Button(
                onClick = {
                    val result = MonthlyReviewEngine.exportMonthlyReviewText(context, review)
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth().testTag("export_monthly_review_btn")
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Monthly Review Report (.txt)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ReviewKpiPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
