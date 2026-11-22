package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.ai.DebtPortfolioSummary
import com.example.ai.DebtProjectionEngine
import com.example.ai.StrategyOutcome
import com.example.data.model.DebtEntity
import com.example.ui.components.Formatters
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavySurfaceVariant
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.SkyTertiary
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.MainViewModel
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DebtTrackerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val debts by viewModel.allDebts.collectAsStateWithLifecycle()
    val summary = remember(debts) { DebtProjectionEngine.summarizePortfolio(debts) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Accounts, 1: Schedule, 2: Rate Impact
    var typeFilter by remember { mutableStateOf("ALL") } // "ALL", "STUDENT_LOAN", "CREDIT_CARD"
    var selectedDebtId by remember { mutableStateOf<Long?>(null) }

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingDebt by remember { mutableStateOf<DebtEntity?>(null) }
    var payingDebt by remember { mutableStateOf<DebtEntity?>(null) }
    var strategyExtraBudget by remember { mutableFloatStateOf(2000f) }

    val filteredDebts = remember(debts, typeFilter) {
        when (typeFilter) {
            "STUDENT_LOAN" -> debts.filter { it.isStudentLoan }
            "CREDIT_CARD" -> debts.filter { it.isCreditCard }
            else -> debts
        }
    }

    val effectiveSelectedId = selectedDebtId?.takeIf { id -> debts.any { it.id == id } }
        ?: debts.firstOrNull()?.id

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("debt_tracker_screen"),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Portfolio Hero Summary Card
        item {
            DebtPortfolioHeroCard(
                summary = summary,
                onAddDebtClick = {
                    editingDebt = null
                    showAddEditDialog = true
                }
            )
        }

        // 3-Tab Switcher
        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .testTag("debt_tab_row")
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Accounts (${debts.size})", fontWeight = FontWeight.Bold, fontSize = 12.5.sp) },
                    modifier = Modifier.testTag("debt_tab_accounts")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Schedule", fontWeight = FontWeight.Bold, fontSize = 12.5.sp) },
                    modifier = Modifier.testTag("debt_tab_schedule")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Rate Impact", fontWeight = FontWeight.Bold, fontSize = 12.5.sp) },
                    modifier = Modifier.testTag("debt_tab_projections")
                )
            }
        }

        if (debts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Student Loans or Credit Cards Tracked",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Add your education loans or credit cards to unlock amortization schedules, APR impact simulations, and payoff strategies.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                editingDebt = null
                                showAddEditDialog = true
                            },
                            modifier = Modifier.testTag("debt_empty_add_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Student Loan or Card")
                        }
                    }
                }
            }
        } else {
            when (selectedTab) {
                0 -> {
                    // Type filter pills
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = typeFilter == "ALL",
                                onClick = { typeFilter = "ALL" },
                                label = { Text("All (${debts.size})") },
                                modifier = Modifier.testTag("debt_filter_all")
                            )
                            FilterChip(
                                selected = typeFilter == "STUDENT_LOAN",
                                onClick = { typeFilter = "STUDENT_LOAN" },
                                label = { Text("Student Loans") },
                                modifier = Modifier.testTag("debt_filter_loans")
                            )
                            FilterChip(
                                selected = typeFilter == "CREDIT_CARD",
                                onClick = { typeFilter = "CREDIT_CARD" },
                                label = { Text("Credit Cards") },
                                modifier = Modifier.testTag("debt_filter_cards")
                            )
                        }
                    }

                    // Individual Debt Cards
                    items(filteredDebts, key = { it.id }) { debt ->
                        DebtAccountCard(
                            debt = debt,
                            onLogPayment = { payingDebt = debt },
                            onOpenSchedule = {
                                selectedDebtId = debt.id
                                selectedTab = 1
                            },
                            onOpenRateImpact = {
                                selectedDebtId = debt.id
                                selectedTab = 2
                            },
                            onEdit = {
                                editingDebt = debt
                                showAddEditDialog = true
                            },
                            onDelete = { viewModel.deleteDebt(debt) }
                        )
                    }

                    // Avalanche vs Snowball Multi-Debt Strategy Card
                    if (debts.count { it.currentBalance > 0.01 } >= 2) {
                        item {
                            DebtStrategyComparisonCard(
                                debts = debts,
                                extraBudget = strategyExtraBudget,
                                onExtraBudgetChange = { strategyExtraBudget = it }
                            )
                        }
                    }
                }

                1 -> {
                    item {
                        DebtPaymentScheduleTab(
                            debts = debts,
                            selectedDebtId = effectiveSelectedId,
                            onSelectDebt = { selectedDebtId = it }
                        )
                    }
                }

                2 -> {
                    item {
                        DebtInterestProjectionsTab(
                            debts = debts,
                            selectedDebtId = effectiveSelectedId,
                            onSelectDebt = { selectedDebtId = it }
                        )
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditDebtDialog(
            existingDebt = editingDebt,
            onDismiss = { showAddEditDialog = false },
            onSave = { saved ->
                if (editingDebt == null) {
                    viewModel.addDebt(saved)
                } else {
                    viewModel.updateDebt(saved)
                }
                showAddEditDialog = false
            }
        )
    }

    if (payingDebt != null) {
        LogDebtPaymentDialog(
            debt = payingDebt!!,
            onDismiss = { payingDebt = null },
            onConfirmPayment = { amt, recordTxn ->
                viewModel.recordDebtPayment(payingDebt!!, amt, recordTxn)
                payingDebt = null
            }
        )
    }
}

@Composable
private fun DebtPortfolioHeroCard(
    summary: DebtPortfolioSummary,
    onAddDebtClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(DarkNavyBackground, DarkNavySurfaceVariant, EmeraldDark)
                )
            )
            .padding(18.dp)
            .testTag("debt_portfolio_hero")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TOTAL DEBT BALANCE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.75f),
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = Formatters.formatRupee(summary.totalBalance),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
                Button(
                    onClick = onAddDebtClick,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("debt_add_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Loan or Card", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Debt", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeroStatPill(
                    label = "Min Monthly",
                    value = Formatters.formatRupee(summary.totalMinimumMonthly),
                    modifier = Modifier.weight(1f)
                )
                HeroStatPill(
                    label = "Weighted APR",
                    value = "${String.format(Locale.ENGLISH, "%.1f", summary.weightedAverageApr)}%",
                    modifier = Modifier.weight(1f)
                )
                HeroStatPill(
                    label = "Interest/Mo",
                    value = Formatters.formatRupee(summary.monthlyInterestBurn),
                    highlightColor = WarningOrange,
                    modifier = Modifier.weight(1f)
                )
            }

            if (summary.studentLoanBalance > 0 || summary.creditCardBalance > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Student Loans: ${Formatters.formatRupee(summary.studentLoanBalance)}",
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Credit Cards: ${Formatters.formatRupee(summary.creditCardBalance)}",
                        fontSize = 11.5.sp,
                        color = if (summary.creditCardBalance > 0) Color(0xFFFCA5A5) else Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStatPill(
    label: String,
    value: String,
    highlightColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.11f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                fontSize = 10.5.sp,
                color = Color.White.copy(alpha = 0.72f)
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = highlightColor
            )
        }
    }
}

@Composable
private fun DebtAccountCard(
    debt: DebtEntity,
    onLogPayment: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenRateImpact: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val projection = remember(debt) { DebtProjectionEngine.buildPayoffProjection(debt) }
    val typeAccent = if (debt.isStudentLoan) SkyTertiary else ExpenseRed
    val aprHigh = debt.annualInterestRate >= 18.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("debt_card_${debt.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, typeAccent.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Icon, Title, APR Badge, Edit/Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(typeAccent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (debt.isStudentLoan) Icons.Default.School else Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = typeAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = debt.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = buildString {
                            append(if (debt.isStudentLoan) "Student Loan" else "Credit Card")
                            if (debt.lender.isNotBlank()) append(" • ${debt.lender}")
                            append(" • Due ${debt.dueDayOfMonth}th")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = (if (aprHigh) ExpenseRed else EmeraldPrimary).copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "${debt.annualInterestRate}% APR",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (aprHigh) ExpenseRed else EmeraldPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Balance & Progress / Credit Utilization
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Remaining Balance",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = Formatters.formatRupee(debt.currentBalance),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (debt.isStudentLoan) "EMI / Month" else "Min Payment",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${Formatters.formatRupee(debt.minimumMonthlyPayment)}/mo",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = typeAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (debt.isStudentLoan) {
                val paidPct = (debt.payoffProgressFraction * 100f).roundToInt()
                LinearProgressIndicator(
                    progress = { debt.payoffProgressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = EmeraldPrimary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("$paidPct% paid off (${Formatters.formatRupee(debt.paidOffAmount)})", fontSize = 11.5.sp, color = EmeraldPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Orig: ${Formatters.formatRupee(debt.originalPrincipal)}", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val utilPct = debt.creditUtilizationPercent
                val utilColor = when {
                    utilPct >= 50 -> ExpenseRed
                    utilPct >= 30 -> WarningOrange
                    else -> EmeraldPrimary
                }
                LinearProgressIndicator(
                    progress = { (utilPct / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = utilColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Credit Utilization: $utilPct% (keep <30%)", fontSize = 11.5.sp, color = utilColor, fontWeight = FontWeight.SemiBold)
                    Text("Limit: ${Formatters.formatRupee(debt.originalPrincipal)}", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Payoff Summary Strip
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Interest/Mo", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Formatters.formatRupee(debt.currentMonthlyInterest), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = ExpenseRed)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Est. Payoff", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(projection.payoffDateLabel, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Total Interest", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Formatters.formatRupee(projection.totalInterestPaid), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = WarningOrange)
                    }
                }
            }

            if (!projection.isAmortizing) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Minimum payment doesn't cover monthly interest!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onLogPayment,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("debt_pay_btn_${debt.id}")
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pay", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onOpenSchedule,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1.1f)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Schedule", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onOpenRateImpact,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1.1f)
                ) {
                    Icon(Icons.Default.Percent, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("APR Sim", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Debt", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Debt", tint = ExpenseRed.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun DebtStrategyComparisonCard(
    debts: List<DebtEntity>,
    extraBudget: Float,
    onExtraBudgetChange: (Float) -> Unit
) {
    val comparison = remember(debts, extraBudget) {
        DebtProjectionEngine.comparePayoffStrategies(debts, extraBudget.toDouble())
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("debt_strategy_comparison_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TrendingDown, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Avalanche vs. Snowball Payoff Planner",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "See how adding extra monthly cash eliminates your student loans & credit cards faster.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Extra Monthly Debt Budget", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "+${Formatters.formatRupee(extraBudget.toDouble())}/mo",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = EmeraldPrimary
                )
            }
            Slider(
                value = extraBudget,
                onValueChange = { onExtraBudgetChange(((it / 500f).roundToInt() * 500f)) },
                valueRange = 0f..10000f,
                colors = SliderDefaults.colors(thumbColor = EmeraldPrimary, activeTrackColor = EmeraldPrimary),
                modifier = Modifier.testTag("debt_strategy_extra_slider")
            )

            StrategyRow(outcome = comparison.minimumOnly, accent = InfoBlue, isRecommended = false)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            StrategyRow(outcome = comparison.avalanche, accent = EmeraldPrimary, isRecommended = true)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            StrategyRow(outcome = comparison.snowball, accent = SkyTertiary, isRecommended = false)
        }
    }
}

@Composable
private fun StrategyRow(
    outcome: StrategyOutcome,
    accent: Color,
    isRecommended: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1.3f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = outcome.strategyName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isRecommended) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = EmeraldPrimary.copy(alpha = 0.14f)) {
                        Text(
                            text = "Lowest Cost",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = "Debt-free: ${outcome.payoffDateLabel} (${outcome.monthsToDebtFree} mo)",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(0.9f)) {
            Text(
                text = Formatters.formatRupee(outcome.totalInterestPaid),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accent
            )
            Text(
                text = if (outcome.interestSavedVsMinimum > 1.0) {
                    "Save ${Formatters.formatRupee(outcome.interestSavedVsMinimum)}"
                } else "Total interest",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (outcome.interestSavedVsMinimum > 1.0) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
