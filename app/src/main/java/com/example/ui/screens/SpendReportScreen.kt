package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TransactionEntity
import com.example.ui.components.CategoryIcons
import com.example.ui.components.Formatters
import com.example.ui.components.charts.PaymentModeBarChart
import com.example.ui.components.charts.SpendBarChart
import com.example.ui.components.charts.SpendPieChart
import com.example.ui.components.charts.SpendTrendLineChart
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.SkyTertiary
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.MainViewModel
import com.example.util.ReportExporter
import com.example.util.ReportPeriod

@Composable
fun SpendReportScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val budgets by viewModel.allBudgets.collectAsStateWithLifecycle()

    var selectedPeriod by remember { mutableStateOf(ReportPeriod.WEEKLY) }
    var weekOffset by remember { mutableIntStateOf(0) }
    var monthOffset by remember { mutableIntStateOf(0) }
    var showDownloadMenu by remember { mutableStateOf(false) }

    val monthlyAllowance = profile?.monthlyAllowance ?: 10000.0

    // Compute active report reactively based on period and offset
    val activeReport = remember(selectedPeriod, weekOffset, monthOffset, transactions, budgets, monthlyAllowance) {
        if (selectedPeriod == ReportPeriod.WEEKLY) {
            ReportExporter.buildWeeklyReport(
                offsetWeeks = weekOffset,
                allTransactions = transactions,
                monthlyAllowance = monthlyAllowance,
                budgets = budgets
            )
        } else {
            ReportExporter.buildMonthlyReport(
                offsetMonths = monthOffset,
                allTransactions = transactions,
                monthlyAllowance = monthlyAllowance,
                budgets = budgets
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("spend_report_screen"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)
    ) {
        // Screen Header & Download Action
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Spend Reports & Analytics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Weekly & Monthly outflow breakdown with visual graphs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    Button(
                        onClick = { showDownloadMenu = true },
                        modifier = Modifier.testTag("download_spend_report_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download Report",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download", fontWeight = FontWeight.Bold)
                    }

                    DropdownMenu(
                        expanded = showDownloadMenu,
                        onDismissRequest = { showDownloadMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Download CSV (Spreadsheet)", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Saved to Downloads/FinanceCoach",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.TableChart,
                                    contentDescription = null,
                                    tint = EmeraldLight
                                )
                            },
                            onClick = {
                                showDownloadMenu = false
                                val result = ReportExporter.exportCsv(context, activeReport)
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.testTag("download_csv_menu_item")
                        )

                        HorizontalDivider()

                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Download Text Summary (.txt)", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Saved to Downloads/FinanceCoach",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    tint = SkyTertiary
                                )
                            },
                            onClick = {
                                showDownloadMenu = false
                                val result = ReportExporter.exportTextSummary(context, activeReport)
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.testTag("download_txt_menu_item")
                        )

                        HorizontalDivider()

                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Share Spend Report", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Share via WhatsApp, Email, Drive",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = null,
                                    tint = WarningOrange
                                )
                            },
                            onClick = {
                                showDownloadMenu = false
                                ReportExporter.shareReport(context, activeReport, asCsv = true)
                            },
                            modifier = Modifier.testTag("share_report_menu_item")
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Period Toggle: Weekly vs Monthly
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedPeriod == ReportPeriod.WEEKLY,
                        onClick = { selectedPeriod = ReportPeriod.WEEKLY },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Weekly Report", fontWeight = FontWeight.SemiBold)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("period_weekly_tab")
                    )

                    FilterChip(
                        selected = selectedPeriod == ReportPeriod.MONTHLY,
                        onClick = { selectedPeriod = ReportPeriod.MONTHLY },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Monthly Report", fontWeight = FontWeight.SemiBold)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("period_monthly_tab")
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Date Range Navigator Bar
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("report_navigator_bar"),
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
                        onClick = {
                            if (selectedPeriod == ReportPeriod.WEEKLY) weekOffset-- else monthOffset--
                        },
                        modifier = Modifier.testTag("report_prev_period_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Period")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = activeReport.periodLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = activeReport.dateRangeStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val canGoForward = if (selectedPeriod == ReportPeriod.WEEKLY) weekOffset < 0 else monthOffset < 0
                    IconButton(
                        onClick = {
                            if (selectedPeriod == ReportPeriod.WEEKLY) {
                                if (weekOffset < 0) weekOffset++
                            } else {
                                if (monthOffset < 0) monthOffset++
                            }
                        },
                        enabled = canGoForward,
                        modifier = Modifier.testTag("report_next_period_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Period",
                            tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Executive Financial Overview Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ReportMetricCard(
                    title = "Total Spent",
                    value = Formatters.formatRupee(activeReport.totalSpent),
                    subText = "${activeReport.transactions.count { it.type.equals("DEBIT", true) }} Outflows",
                    color = ExpenseRed,
                    modifier = Modifier.weight(1f),
                    tag = "report_total_spent"
                )
                ReportMetricCard(
                    title = "Total Received",
                    value = Formatters.formatRupee(activeReport.totalIncome),
                    subText = "${activeReport.transactions.count { it.type.equals("CREDIT", true) }} Inflows",
                    color = IncomeGreen,
                    modifier = Modifier.weight(1f),
                    tag = "report_total_income"
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ReportMetricCard(
                    title = "Daily Average",
                    value = Formatters.formatRupee(activeReport.dailyAverage),
                    subText = "Per day spend",
                    color = SkyTertiary,
                    modifier = Modifier.weight(1f),
                    tag = "report_daily_avg"
                )
                ReportMetricCard(
                    title = "Top Category",
                    value = activeReport.topCategory,
                    subText = "Highest spend",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    tag = "report_top_category"
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // GRAPH 1: Pie Chart / Donut Chart (Category Breakdown)
        item {
            SpendPieChart(
                categories = activeReport.categories,
                totalSpent = activeReport.totalSpent
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // GRAPH 2: Bar Chart (Daily / Weekly intervals & Top Category spend)
        item {
            SpendBarChart(
                timeItems = activeReport.timeBreakdown,
                categoryItems = activeReport.categories,
                isWeekly = selectedPeriod == ReportPeriod.WEEKLY
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // GRAPH 3: Line / Trend Graph (Cumulative Spending Velocity vs Budget Pace)
        item {
            SpendTrendLineChart(
                trendPoints = activeReport.cumulativeTrend
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // GRAPH 4: Payment Mode Distribution Bar Chart
        item {
            PaymentModeBarChart(
                paymentModes = activeReport.paymentModes
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Key Student Highlights
        if (activeReport.studentHighlights.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_highlights_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Period Insights & Audit Notes",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        activeReport.studentHighlights.forEach { hl ->
                            Text(
                                text = "• $hl",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Itemized Transactions Header & Count
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Itemized Transactions (${activeReport.transactions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${selectedPeriod.title} ledger",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (activeReport.transactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No transactions found in this ${selectedPeriod.title.lowercase()} period.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(activeReport.transactions, key = { it.id }) { tx ->
                ReportTransactionRow(tx = tx)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Bottom Download CTA banner
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bottom_download_cta_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Export Full ${activeReport.period.title} Spend Statement",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Download as CSV spreadsheet or formatted summary text to your device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val result = ReportExporter.exportCsv(context, activeReport)
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.testTag("bottom_cta_download_csv_btn")
                        ) {
                            Text("Download CSV")
                        }
                        OutlinedButton(
                            onClick = {
                                ReportExporter.shareReport(context, activeReport, asCsv = true)
                            },
                            modifier = Modifier.testTag("bottom_cta_share_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportMetricCard(
    title: String,
    value: String,
    subText: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    tag: String
) {
    Card(
        modifier = modifier.testTag(tag),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReportTransactionRow(tx: TransactionEntity) {
    val isDebit = tx.type.equals("DEBIT", ignoreCase = true)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("report_tx_${tx.id}"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(CategoryIcons.getColor(tx.category).copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CategoryIcons.getIcon(tx.category),
                    contentDescription = tx.category,
                    tint = CategoryIcons.getColor(tx.category),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.merchant.ifBlank { "Transaction" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${tx.category} • ${Formatters.formatDateShort(tx.date)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (tx.paymentMethod.isNotBlank()) {
                        Text(
                            text = " • ${tx.paymentMethod}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Text(
                text = (if (isDebit) "- " else "+ ") + Formatters.formatRupee(tx.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDebit) ExpenseRed else IncomeGreen
            )
        }
    }
}
