package com.example.ui.screens

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
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.DebtProjectionEngine
import com.example.data.model.TransactionEntity
import com.example.ui.components.CategoryBadge
import com.example.ui.components.CategoryIcons
import com.example.ui.components.Formatters
import com.example.ui.components.PrivacyNoticeBanner
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.MintAccent
import com.example.ui.theme.PurpleInvestment
import com.example.ui.theme.SkyTertiary
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.CategoryBreakdown
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.PeriodSummary

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToTransactions: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToInvestments: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSpendReports: () -> Unit = {},
    onNavigateToStatementImport: () -> Unit = {},
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToFinancialProfile: () -> Unit = {},
    onNavigateToInsights: () -> Unit = {},
    onNavigateToDebtTracker: () -> Unit = {},
    onOpenSmsSimulator: () -> Unit,
    onOpenAddTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val maskAccounts = profile?.maskAccountNumbers ?: true

    val dailySummary by viewModel.dailySummary.collectAsStateWithLifecycle()
    val weeklySummary by viewModel.weeklySummary.collectAsStateWithLifecycle()
    val monthlySummary by viewModel.monthlySummary.collectAsStateWithLifecycle()
    val aiInsights by viewModel.aiInsightsList.collectAsStateWithLifecycle()
    val latestDetectedUpiTxn by viewModel.latestDetectedUpiTxn.collectAsStateWithLifecycle()
    val driftNudges by viewModel.driftNudges.collectAsStateWithLifecycle()
    val allRecurring by viewModel.allRecurring.collectAsStateWithLifecycle()
    val allDebts by viewModel.allDebts.collectAsStateWithLifecycle()
    val debtSummary = remember(allDebts) { DebtProjectionEngine.summarizePortfolio(allDebts) }
    val subAlertCount = allRecurring.count { it.hasAlert }

    val context = LocalContext.current
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.scanInboxForUpi(context)
        }
    }

    var selectedSummaryPeriod by remember { mutableStateOf(0) } // 0: Daily, 1: Weekly, 2: Monthly

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // Prominent banner when a new UPI transaction was just parsed/found
        if (latestDetectedUpiTxn != null) {
            item {
                NewUpiDetectedBanner(
                    transaction = latestDetectedUpiTxn!!,
                    onDismiss = { viewModel.dismissDetectedUpiTxn() },
                    onViewTransactions = onNavigateToTransactions
                )
            }
        }

        // Hero Balance Card
        item {
            HeroBalanceSection(
                name = profile?.name ?: "Student",
                balance = metrics.currentBalance,
                remainingBudget = metrics.remainingBudget,
                totalSpent = metrics.totalSpentThisMonth,
                totalReceived = metrics.totalIncomeThisMonth,
                onAddTransaction = onOpenAddTransaction,
                onOpenSimulator = onOpenSmsSimulator
            )
        }

        // Plan Drift Nudges Card (top 2 active nudges)
        if (driftNudges.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = WarningOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Plan Drift Nudges",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "All (${driftNudges.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onNavigateToInsights() }
                        )
                    }

                    driftNudges.take(2).forEach { nudge ->
                        DriftNudgeCard(
                            nudge = nudge,
                            onAction = {
                                when (nudge.actionTarget) {
                                    "SIMULATOR" -> onNavigateToGoals()
                                    "SUBSCRIPTIONS" -> onNavigateToSubscriptions()
                                    "BUDGET" -> onNavigateToBudget()
                                    "PROFILE" -> onNavigateToFinancialProfile()
                                    "REVIEW" -> onNavigateToInsights()
                                    else -> onNavigateToAssistant()
                                }
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // Quick Actions Grid (2 compact rows covering core capabilities)
        item {
            QuickActionsRow(
                subAlertCount = subAlertCount,
                onNavigateToStatementImport = onNavigateToStatementImport,
                onNavigateToSubscriptions = onNavigateToSubscriptions,
                onNavigateToGoals = onNavigateToGoals,
                onNavigateToFinancialProfile = onNavigateToFinancialProfile,
                onNavigateToRules = onNavigateToRules,
                onNavigateToDebtTracker = onNavigateToDebtTracker,
                onNavigateToInvestments = onNavigateToInvestments,
                onNavigateToInsights = onNavigateToInsights
            )
        }

        // Financial Profile & Emergency Runway Summary Card
        if (profile != null) {
            item {
                val p = profile!!
                val efProgress = if (p.emergencyFundTargetAmount > 0) {
                    (p.emergencyFundCurrent / p.emergencyFundTargetAmount).toFloat().coerceIn(0f, 1f)
                } else 0f
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onNavigateToFinancialProfile() }
                        .testTag("dashboard_profile_runway_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Financial Profile & Emergency Fund",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Edit",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Income: ${Formatters.formatRupee(p.totalMonthlyIncome)}/mo",
                                style = MaterialTheme.typography.labelSmall,
                                color = IncomeGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Fixed: ${Formatters.formatRupee(p.totalFixedCosts)}/mo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "EF: ${String.format("%.1f", p.emergencyFundMonthsCovered)}/${p.emergencyFundTargetMonths}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = SkyTertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { efProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = EmeraldLight,
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // Student Loans & Credit Card Debt Summary Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { onNavigateToDebtTracker() }
                    .testTag("dashboard_debt_tracker_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CreditCard,
                                contentDescription = null,
                                tint = ExpenseRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Student Loans & Credit Card Debt",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Schedules →",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Balance: ${Formatters.formatRupee(debtSummary.totalBalance)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Avg APR: ${String.format("%.1f", debtSummary.weightedAverageApr)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningOrange,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Interest: ${Formatters.formatRupee(debtSummary.monthlyInterestBurn)}/mo",
                            style = MaterialTheme.typography.labelSmall,
                            color = ExpenseRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Quick UPI SMS Detection Section
        item {
            QuickUpiSmsScannerSection(
                onScanSms = { smsPermissionLauncher.launch(Manifest.permission.READ_SMS) },
                onQuickTest = onOpenSmsSimulator
            )
        }

        // Structured Periodic Dashboard Summary (Daily, Weekly, Monthly)
        item {
            val currentSummary = when (selectedSummaryPeriod) {
                0 -> dailySummary
                1 -> weeklySummary
                else -> monthlySummary
            }
            PeriodicDashboardSummaryCard(
                selectedPeriod = selectedSummaryPeriod,
                onPeriodSelected = { selectedSummaryPeriod = it },
                summary = currentSummary
            )
        }

        // Automated AI Insights Card
        item {
            AiInsightsSectionCard(
                insights = aiInsights,
                onAskAssistant = onNavigateToAssistant
            )
        }

        // Key Monthly Metrics Grid
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "This Month at a Glance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricMiniCard(
                        title = "Money Received",
                        amount = Formatters.formatRupee(metrics.totalIncomeThisMonth),
                        icon = Icons.Default.ArrowDownward,
                        iconColor = IncomeGreen,
                        modifier = Modifier.weight(1f),
                        testTag = "metric_received"
                    )
                    MetricMiniCard(
                        title = "Money Spent",
                        amount = Formatters.formatRupee(metrics.totalSpentThisMonth),
                        icon = Icons.Default.ArrowUpward,
                        iconColor = ExpenseRed,
                        modifier = Modifier.weight(1f),
                        testTag = "metric_spent"
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricMiniCard(
                        title = "Money Saved",
                        amount = Formatters.formatRupee(metrics.totalSavedThisMonth),
                        icon = Icons.Default.Savings,
                        iconColor = EmeraldLight,
                        modifier = Modifier.weight(1f),
                        testTag = "metric_saved"
                    )
                    MetricMiniCard(
                        title = "Investments",
                        amount = Formatters.formatRupee(metrics.totalInvested),
                        icon = Icons.Default.TrendingUp,
                        iconColor = PurpleInvestment,
                        modifier = Modifier.weight(1f),
                        testTag = "metric_investments"
                    )
                }
            }
        }

        // Visual Expense Breakdown by Category
        item {
            CategoryBreakdownCard(
                categorySpending = metrics.categorySpending,
                totalSpent = metrics.totalSpentThisMonth,
                onViewBudgets = onNavigateToBudget
            )
        }

        // Spend Reports & Analytics Banner Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { onNavigateToSpendReports() }
                    .testTag("dashboard_spend_report_banner"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Weekly & Monthly Reports",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = EmeraldLight.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "Download",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldLight,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Download statements & view interactive pie, bar, and velocity graphs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Reports",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Privacy Banner
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                PrivacyNoticeBanner(onOpenPrivacySettings = onNavigateToSettings)
            }
        }

        // Recent Transactions Header & List
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "View All",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onNavigateToTransactions() }
                        .testTag("view_all_transactions_btn")
                )
            }
        }

        val recentList = transactions.take(5)
        if (recentList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sms,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No transactions tracked yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'Simulate Bank SMS' to test automatic expense detection or 'Add' to enter manually.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onOpenSmsSimulator,
                                modifier = Modifier.testTag("empty_state_simulate_btn")
                            ) {
                                Text("Simulate Bank SMS")
                            }
                            OutlinedButton(onClick = { viewModel.loadSampleStudentData() }) {
                                Text("Load Samples")
                            }
                        }
                    }
                }
            }
        } else {
            items(recentList, key = { it.id }) { txn ->
                RecentTransactionRow(
                    transaction = txn,
                    maskAccounts = maskAccounts,
                    onClick = onNavigateToTransactions
                )
            }
        }
    }
}

@Composable
fun HeroBalanceSection(
    name: String,
    balance: Double,
    remainingBudget: Double,
    totalSpent: Double,
    totalReceived: Double,
    onAddTransaction: () -> Unit,
    onOpenSimulator: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("hero_balance_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF065F46),
                            Color(0xFF0F172A)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Hello, $name 👋",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFD1FAE5)
                        )
                        Text(
                            text = "Finance Coach Smart Wallet",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA7F3D0)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color(0x2210B981)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldLight)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Auto SMS AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Estimated Current Balance",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Formatters.formatRupee(balance),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x25000000)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Remaining Monthly Budget",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFCBD5E1)
                            )
                            Text(
                                text = Formatters.formatRupee(remainingBudget),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (remainingBudget > 1000) Color(0xFF34D399) else WarningOrange
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = onOpenSimulator,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x3310B981))
                                    .testTag("hero_simulate_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sms,
                                    contentDescription = "Test SMS",
                                    tint = EmeraldLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onAddTransaction,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldLight)
                                    .testTag("hero_add_tx_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Expense",
                                    tint = Color(0xFF022C22),
                                    modifier = Modifier.size(20.dp)
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
fun QuickActionsRow(
    subAlertCount: Int,
    onNavigateToStatementImport: () -> Unit,
    onNavigateToSubscriptions: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToFinancialProfile: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToDebtTracker: () -> Unit,
    onNavigateToInvestments: () -> Unit,
    onNavigateToInsights: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            QuickActionButton(
                label = "Import PDF/CSV",
                icon = Icons.Default.FileOpen,
                color = EmeraldPrimary,
                onClick = onNavigateToStatementImport,
                testTag = "quick_action_import"
            )
            QuickActionButton(
                label = if (subAlertCount > 0) "Subs ($subAlertCount!)" else "Subscriptions",
                icon = Icons.Default.Autorenew,
                color = if (subAlertCount > 0) ExpenseRed else WarningOrange,
                onClick = onNavigateToSubscriptions,
                testTag = "quick_action_subscriptions"
            )
            QuickActionButton(
                label = "Debt & Loans",
                icon = Icons.Default.CreditCard,
                color = ExpenseRed,
                onClick = onNavigateToDebtTracker,
                testTag = "quick_action_debt"
            )
            QuickActionButton(
                label = "What-If Goals",
                icon = Icons.Default.Savings,
                color = WarningOrange,
                onClick = onNavigateToGoals,
                testTag = "quick_action_goals"
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            QuickActionButton(
                label = "Monthly Review",
                icon = Icons.Default.BarChart,
                color = EmeraldLight,
                onClick = onNavigateToInsights,
                testTag = "quick_action_review"
            )
            QuickActionButton(
                label = "Learned Rules",
                icon = Icons.Default.Rule,
                color = SkyTertiary,
                onClick = onNavigateToRules,
                testTag = "quick_action_rules"
            )
            QuickActionButton(
                label = "Profile",
                icon = Icons.Default.AccountCircle,
                color = SkyTertiary,
                onClick = onNavigateToFinancialProfile,
                testTag = "quick_action_profile"
            )
            QuickActionButton(
                label = "Investments",
                icon = Icons.Default.TrendingUp,
                color = PurpleInvestment,
                onClick = onNavigateToInvestments,
                testTag = "quick_action_invest"
            )
        }
    }
}

@Composable
fun QuickActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun MetricMiniCard(
    title: String,
    amount: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Card(
        modifier = modifier.testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun CategoryBreakdownCard(
    categorySpending: Map<String, Double>,
    totalSpent: Double,
    onViewBudgets: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("category_breakdown_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PieChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Expense Breakdown",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Budgets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onViewBudgets() }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (categorySpending.isEmpty() || totalSpent <= 0) {
                Text(
                    text = "No expenses recorded this month yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val sortedCategories = categorySpending.toList().sortedByDescending { it.second }

                sortedCategories.take(5).forEach { (category, amount) ->
                    val fraction = (amount / totalSpent).toFloat().coerceIn(0f, 1f)
                    val percent = (fraction * 100).toInt()
                    val catColor = CategoryIcons.getColor(category)

                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(catColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "${Formatters.formatRupee(amount)} ($percent%)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = catColor,
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecentTransactionRow(
    transaction: TransactionEntity,
    maskAccounts: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() }
            .testTag("recent_txn_item_${transaction.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(CategoryIcons.getColor(transaction.category).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CategoryIcons.getIcon(transaction.category),
                    contentDescription = transaction.category,
                    tint = CategoryIcons.getColor(transaction.category),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.merchant,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryBadge(category = transaction.category)
                    Text(
                        text = Formatters.formatDate(transaction.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val isDebit = transaction.isDebit
                Text(
                    text = "${if (isDebit) "- " else "+ "}${Formatters.formatRupee(transaction.amount)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isDebit) ExpenseRed else IncomeGreen
                )
                if (transaction.categorizedByRule) {
                    Text(
                        text = "Account Rule",
                        style = MaterialTheme.typography.labelSmall,
                        color = SkyTertiary,
                        fontWeight = FontWeight.Medium
                    )
                } else if (transaction.isAiCategorized) {
                    Text(
                        text = "AI categorized",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldLight,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun PeriodicDashboardSummaryCard(
    selectedPeriod: Int,
    onPeriodSelected: (Int) -> Unit,
    summary: PeriodSummary
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("periodic_summary_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Spending Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = selectedPeriod == 0,
                        onClick = { onPeriodSelected(0) },
                        label = { Text("Daily", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("summary_tab_daily")
                    )
                    FilterChip(
                        selected = selectedPeriod == 1,
                        onClick = { onPeriodSelected(1) },
                        label = { Text("Weekly", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("summary_tab_weekly")
                    )
                    FilterChip(
                        selected = selectedPeriod == 2,
                        onClick = { onPeriodSelected(2) },
                        label = { Text("Monthly", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("summary_tab_monthly")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = summary.periodTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ExpenseRed.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "Total Spent: ${Formatters.formatRupee(summary.totalSpent)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseRed,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (summary.breakdowns.isEmpty()) {
                        Text(
                            text = "No spending recorded for this period yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        summary.breakdowns.forEach { item ->
                            val catColor = CategoryIcons.getColor(item.category)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(catColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.category,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = Formatters.formatRupee(item.amount),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
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
fun AiInsightsSectionCard(
    insights: List<String>,
    onAskAssistant: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("ai_insights_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = EmeraldPrimary.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Financial Insights",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = EmeraldPrimary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Auto Generated",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                insights.forEach { insight ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "• ",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary
                        )
                        Text(
                            text = insight,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onAskAssistant,
                    modifier = Modifier.testTag("ask_assistant_from_insights_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chat with Assistant", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun NewUpiDetectedBanner(
    transaction: TransactionEntity,
    onDismiss: () -> Unit,
    onViewTransactions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("new_upi_detected_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = EmeraldPrimary.copy(alpha = 0.12f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, EmeraldLight)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(EmeraldLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "New UPI",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NEW UPI TRANSACTION DETECTED",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldLight
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("dismiss_upi_banner_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.merchant,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${transaction.dateStr.ifBlank { "Just now" }} ${transaction.timeStr} • via ${transaction.paymentMethod} (${transaction.accountRef})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = Formatters.formatRupee(transaction.amount),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = ExpenseRed
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryBadge(category = transaction.category)
                FilledTonalButton(
                    onClick = onViewTransactions,
                    modifier = Modifier.testTag("view_upi_txn_button"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("View in Transactions", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun QuickUpiSmsScannerSection(
    onScanSms: () -> Unit,
    onQuickTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("quick_upi_scanner_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(EmeraldPrimary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Quick UPI",
                        tint = EmeraldLight,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quick UPI SMS Detection",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Instantly detect & categorize UPI spending (Canteen, Swiggy, Mess) from SMS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onScanSms,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_scan_sms_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sms,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan SMS", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onQuickTest,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_test_upi_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test UPI SMS", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
