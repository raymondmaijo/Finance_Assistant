package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.DriftNudge
import com.example.ai.DriftSeverity
import com.example.ui.components.Formatters
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.SkyTertiary
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.MainViewModel

data class InsightCardItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val tag: String
)

@Composable
fun InsightsScreen(
    viewModel: MainViewModel,
    onNavigateToTarget: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val recurringList by viewModel.allRecurring.collectAsStateWithLifecycle()
    val driftNudges by viewModel.driftNudges.collectAsStateWithLifecycle()

    val allowance = profile?.totalMonthlyIncome ?: 15000.0
    val totalSpent = metrics.totalSpentThisMonth
    val totalSaved = metrics.totalSavedThisMonth

    val insights = rememberDerivedInsights(
        transactions = transactions,
        metrics = metrics,
        allowance = allowance,
        recurringCount = recurringList.size,
        recurringTotal = recurringList.sumOf { it.amount }
    )

    val budgetScore = rememberFinancialScore(allowance, totalSpent, profile?.monthlySavingsGoal ?: 2500.0)

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("insights_tab_row")
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Monthly Review", fontWeight = FontWeight.SemiBold, maxLines = 1) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("tab_monthly_review")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Spend Graphs", fontWeight = FontWeight.SemiBold, maxLines = 1) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("tab_spend_reports")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Drift & Nudges", fontWeight = FontWeight.SemiBold, maxLines = 1) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("tab_smart_insights")
            )
        }

        when (selectedTab) {
            0 -> MonthlyReviewSection(viewModel = viewModel)
            1 -> SpendReportScreen(viewModel = viewModel)
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("insights_screen"),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)
                ) {
                    // Live Plan Drift Nudges
                    if (driftNudges.isNotEmpty()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = WarningOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Live Plan Drift Nudges (${driftNudges.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        items(driftNudges, key = { it.id }) { nudge ->
                            DriftNudgeCard(
                                nudge = nudge,
                                onAction = {
                                    if (nudge.actionTarget == "REVIEW") {
                                        selectedTab = 0
                                    } else {
                                        onNavigateToTarget(nudge.actionTarget)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        item { Spacer(modifier = Modifier.height(10.dp)) }
                    }

                    // Financial Health & Discipline Score
                    item {
                        FinancialHealthScoreCard(score = budgetScore, allowance = allowance, saved = totalSaved)
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Section Title
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Smart Spending Insights",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Insight Cards
                    items(insights) { item ->
                        InsightRowCard(item = item)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Actionable Habit Tips
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("habit_tips_card"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Financial Coaching Tips",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "• Cutting delivery orders 2x/week typically frees ₹1,000–₹1,400/month toward your emergency fund.\n" +
                                        "• Audit recurring plans every 30 days and cancel unused subscriptions right from the Subscriptions tab.\n" +
                                        "• Correcting any mis-categorized merchant teaches Finance Coach to auto-tag future PDF, CSV, and SMS imports.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DriftNudgeCard(
    nudge: DriftNudge,
    onAction: () -> Unit
) {
    val accentColor = when (nudge.severity) {
        DriftSeverity.CRITICAL -> ExpenseRed
        DriftSeverity.WARNING -> WarningOrange
        DriftSeverity.INFO -> SkyTertiary
        DriftSeverity.POSITIVE -> IncomeGreen
    }
    val icon = when (nudge.severity) {
        DriftSeverity.CRITICAL -> Icons.Default.Warning
        DriftSeverity.WARNING -> Icons.Default.NotificationsActive
        DriftSeverity.INFO -> Icons.Default.Info
        DriftSeverity.POSITIVE -> Icons.Default.CheckCircle
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("drift_nudge_${nudge.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.10f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nudge.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = nudge.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
            if (nudge.actionLabel.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onAction,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(nudge.actionLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun FinancialHealthScoreCard(
    score: Int,
    allowance: Double,
    saved: Double
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("financial_health_score_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Financial Discipline Score",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Calculated from monthly adherence & budget limits",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = when {
                        score >= 80 -> EmeraldLight.copy(alpha = 0.18f)
                        score >= 60 -> WarningOrange.copy(alpha = 0.18f)
                        else -> ExpenseRed.copy(alpha = 0.18f)
                    }
                ) {
                    Text(
                        text = "$score / 100",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            score >= 80 -> EmeraldLight
                            score >= 60 -> WarningOrange
                            else -> ExpenseRed
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { score / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (score >= 75) EmeraldLight else WarningOrange,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when {
                    score >= 80 -> "Outstanding financial control! You're saving consistently against your plan."
                    score >= 60 -> "Balanced pace. Keep an eye on dining and impulsive UPI expenses."
                    else -> "Spending velocity is high. Try slowing down weekend takeout or simulating cuts."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun InsightRowCard(item: InsightCardItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insight_item_${item.tag}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(item.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = item.color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

private fun rememberFinancialScore(allowance: Double, spent: Double, savingsGoal: Double): Int {
    if (allowance <= 0) return 75
    val spendRatio = spent / allowance
    val base = (1.0 - spendRatio) * 70
    val savingsBonus = if (allowance - spent >= savingsGoal) 30.0 else ((allowance - spent) / savingsGoal * 30.0).coerceAtLeast(0.0)
    return (base + savingsBonus).toInt().coerceIn(10, 100)
}

private fun rememberDerivedInsights(
    transactions: List<com.example.data.model.TransactionEntity>,
    metrics: com.example.ui.viewmodel.DashboardMetrics,
    allowance: Double,
    recurringCount: Int,
    recurringTotal: Double
): List<InsightCardItem> {
    val items = mutableListOf<InsightCardItem>()

    val topCat = metrics.categorySpending.maxByOrNull { it.value }
    if (topCat != null && topCat.value > 0) {
        items.add(
            InsightCardItem(
                title = "Largest Expense: ${topCat.key}",
                description = "You've spent ${Formatters.formatRupee(topCat.value)} on ${topCat.key} this month, making up ${((topCat.value / metrics.totalSpentThisMonth.coerceAtLeast(1.0)) * 100).toInt()}% of your total expenses.",
                icon = Icons.Default.TrendingUp,
                color = WarningOrange,
                tag = "top_cat"
            )
        )
    }

    val saved = metrics.totalSavedThisMonth
    val percentSaved = if (allowance > 0) ((saved / allowance) * 100).toInt() else 0
    items.add(
        InsightCardItem(
            title = "Monthly Surplus: ${Formatters.formatRupee(saved)}",
            description = "You've conserved $percentSaved% of your monthly income so far. Redirecting surplus into your emergency fund or goals accelerates target dates.",
            icon = Icons.Default.Savings,
            color = IncomeGreen,
            tag = "savings"
        )
    )

    val foodTxnCount = transactions.count { it.isDebit && it.category.equals("Food", ignoreCase = true) }
    if (foodTxnCount > 0) {
        items.add(
            InsightCardItem(
                title = "Food & Dining Activity",
                description = "You made $foodTxnCount food-related payments recently. Average spend per meal/order is ~${Formatters.formatRupee((metrics.categorySpending["Food"] ?: 0.0) / foodTxnCount.coerceAtLeast(1))}.",
                icon = Icons.Default.Fastfood,
                color = Color(0xFFF97316),
                tag = "food_freq"
            )
        )
    }

    if (recurringCount > 0) {
        items.add(
            InsightCardItem(
                title = "$recurringCount Recurring Payments Tracked",
                description = "You have $recurringCount recurring subscriptions scheduled totaling ${Formatters.formatRupee(recurringTotal)}/month.",
                icon = Icons.Default.CalendarToday,
                color = SkyTertiary,
                tag = "recurring"
            )
        )
    }

    return items
}
