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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.GoalProjection
import com.example.ai.WhatIfScenario
import com.example.ai.WhatIfSimulatorEngine
import com.example.data.model.GoalEntity
import com.example.ui.components.Formatters
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.SkyTertiary
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.MainViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun GoalsScreen(
    viewModel: MainViewModel,
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    val goals by viewModel.allGoals.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val recurring by viewModel.allRecurring.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var isAddingGoal by remember { mutableStateOf(false) }
    var goalToAddSavingsTo by remember { mutableStateOf<GoalEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("goals_screen")
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("goals_tab_row")
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("My Goals", fontWeight = FontWeight.SemiBold) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Savings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("tab_my_goals")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("What-If Planner", fontWeight = FontWeight.SemiBold) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("tab_what_if_planner")
            )
        }

        if (selectedTab == 0) {
            GoalsListTab(
                goals = goals,
                onAddGoal = { isAddingGoal = true },
                onAddSavings = { goalToAddSavingsTo = it },
                onDelete = { viewModel.deleteGoal(it) },
                onOpenSimulator = { selectedTab = 1 }
            )
        } else {
            WhatIfSimulatorTab(
                profile = profile,
                transactions = transactions,
                goals = goals,
                recurring = recurring
            )
        }
    }

    // Add Goal Dialog
    if (isAddingGoal) {
        var title by remember { mutableStateOf("") }
        var targetStr by remember { mutableStateOf("") }
        var savedStr by remember { mutableStateOf("0") }
        var targetDate by remember { mutableStateOf("Dec 2026") }
        val selectedIcon by remember { mutableStateOf("Laptop") }

        AlertDialog(
            onDismissRequest = { isAddingGoal = false },
            title = { Text("Create Savings Goal") },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Goal Title (e.g. New Laptop, Goa Trip)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("goal_title_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetStr,
                        onValueChange = { targetStr = it },
                        label = { Text("Target Amount (₹)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("goal_target_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = savedStr,
                        onValueChange = { savedStr = it },
                        label = { Text("Initial Saved Amount (₹)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetDate,
                        onValueChange = { targetDate = it },
                        label = { Text("Target Date (e.g. Nov 2026)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = targetStr.toDoubleOrNull() ?: 10000.0
                        val saved = savedStr.toDoubleOrNull() ?: 0.0
                        if (title.isNotBlank()) {
                            viewModel.addGoal(title, target, saved, targetDate, selectedIcon)
                            isAddingGoal = false
                        }
                    },
                    modifier = Modifier.testTag("save_goal_btn")
                ) {
                    Text("Save Goal")
                }
            },
            dismissButton = {
                TextButton(onClick = { isAddingGoal = false }) { Text("Cancel") }
            }
        )
    }

    // Add Savings to Goal Dialog
    if (goalToAddSavingsTo != null) {
        var addAmtStr by remember { mutableStateOf("500") }
        AlertDialog(
            onDismissRequest = { goalToAddSavingsTo = null },
            title = { Text("Add Savings to ${goalToAddSavingsTo!!.title}") },
            text = {
                Column {
                    Text("Deposit surplus money into this goal:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addAmtStr,
                        onValueChange = { addAmtStr = it },
                        label = { Text("Amount to Add (₹)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_savings_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val add = addAmtStr.toDoubleOrNull() ?: 500.0
                        viewModel.updateGoalSavings(goalToAddSavingsTo!!, add)
                        goalToAddSavingsTo = null
                    },
                    modifier = Modifier.testTag("confirm_add_savings_btn")
                ) {
                    Text("Deposit")
                }
            },
            dismissButton = {
                TextButton(onClick = { goalToAddSavingsTo = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun GoalsListTab(
    goals: List<GoalEntity>,
    onAddGoal: () -> Unit,
    onAddSavings: (GoalEntity) -> Unit,
    onDelete: (GoalEntity) -> Unit,
    onOpenSimulator: () -> Unit
) {
    val totalGoalTarget = goals.sumOf { it.targetAmount }
    val totalGoalSaved = goals.sumOf { it.savedAmount }
    val totalMonthlyRequired = goals.sumOf { it.requiredMonthlyContribution() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)
    ) {
        // Goals Header Overview
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("goals_summary_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = WarningOrange.copy(alpha = 0.12f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Total Savings Goals",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${Formatters.formatRupee(totalGoalSaved)} of ${Formatters.formatRupee(totalGoalTarget)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val overallProgress = if (totalGoalTarget > 0) (totalGoalSaved / totalGoalTarget).toFloat().coerceIn(0f, 1f) else 0f
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { overallProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = WarningOrange,
                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Needed to hit dates: ${Formatters.formatRupee(totalMonthlyRequired)}/mo",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedButton(
                            onClick = onOpenSimulator,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("open_what_if_from_goals_btn")
                        ) {
                            Icon(Icons.Default.AutoGraph, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("What-If", fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Section header and Add Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Goals (${goals.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onAddGoal,
                    modifier = Modifier.testTag("add_goal_btn")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Goal")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (goals.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.Savings, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No financial goals set yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Save up for a new laptop, emergency cushion, or semester trip!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(goals, key = { it.id }) { goal ->
                GoalItemCard(
                    goal = goal,
                    onAddSavings = { onAddSavings(goal) },
                    onDelete = { onDelete(goal) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun WhatIfSimulatorTab(
    profile: com.example.data.model.UserProfileEntity?,
    transactions: List<com.example.data.model.TransactionEntity>,
    goals: List<GoalEntity>,
    recurring: List<com.example.data.model.RecurringExpenseEntity>
) {
    var diningPct by remember { mutableFloatStateOf(-30f) }
    var discretionaryPct by remember { mutableFloatStateOf(0f) }
    var rentDelta by remember { mutableFloatStateOf(0f) }
    var incomeDelta by remember { mutableFloatStateOf(0f) }
    var cancelUnusedSubs by remember { mutableStateOf(false) }
    var scenarioLabel by remember { mutableStateOf("Cut dining out 30%") }

    val unusedSubCount = remember(recurring) { recurring.count { it.status == "UNUSED" } }
    val unusedSubTotal = remember(recurring) { recurring.filter { it.status == "UNUSED" }.sumOf { it.amount } }

    val scenario = WhatIfScenario(
        label = scenarioLabel,
        diningChangePercent = diningPct.roundToInt(),
        discretionaryChangePercent = discretionaryPct.roundToInt(),
        rentDeltaMonthly = rentDelta.toDouble(),
        incomeDeltaMonthly = incomeDelta.toDouble(),
        cancelUnusedSubscriptions = cancelUnusedSubs
    )

    val result = remember(scenario, profile, transactions, goals, recurring) {
        WhatIfSimulatorEngine.simulate(
            scenario = scenario,
            profile = profile,
            transactions = transactions,
            goals = goals,
            recurring = recurring
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("what_if_simulator_view"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Preset Chips
        item {
            Column {
                Text(
                    text = "Quick Scenarios",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = diningPct.roundToInt() == -30 && rentDelta == 0f && !cancelUnusedSubs,
                            onClick = {
                                diningPct = -30f
                                discretionaryPct = 0f
                                rentDelta = 0f
                                incomeDelta = 0f
                                cancelUnusedSubs = false
                                scenarioLabel = "Cut dining out 30%"
                            },
                            label = { Text("Cut dining 30%") },
                            modifier = Modifier.testTag("preset_cut_dining_30")
                        )
                    }
                    item {
                        FilterChip(
                            selected = rentDelta.roundToInt() == 2000 && diningPct == 0f,
                            onClick = {
                                diningPct = 0f
                                discretionaryPct = 0f
                                rentDelta = 2000f
                                incomeDelta = 0f
                                cancelUnusedSubs = false
                                scenarioLabel = "Rent goes up ₹2,000"
                            },
                            label = { Text("Rent +₹2,000") },
                            modifier = Modifier.testTag("preset_rent_up")
                        )
                    }
                    item {
                        FilterChip(
                            selected = cancelUnusedSubs && diningPct == 0f && rentDelta == 0f,
                            onClick = {
                                diningPct = 0f
                                discretionaryPct = 0f
                                rentDelta = 0f
                                incomeDelta = 0f
                                cancelUnusedSubs = true
                                scenarioLabel = "Cancel unused subs"
                            },
                            label = { Text("Cancel unused subs") },
                            modifier = Modifier.testTag("preset_cancel_subs")
                        )
                    }
                    item {
                        FilterChip(
                            selected = incomeDelta.roundToInt() == 3000 && diningPct == 0f,
                            onClick = {
                                diningPct = 0f
                                discretionaryPct = 0f
                                rentDelta = 0f
                                incomeDelta = 3000f
                                cancelUnusedSubs = false
                                scenarioLabel = "Side income +₹3,000"
                            },
                            label = { Text("Side income +₹3k") },
                            modifier = Modifier.testTag("preset_side_income")
                        )
                    }
                    item {
                        FilterChip(
                            selected = diningPct == 0f && discretionaryPct == 0f && rentDelta == 0f && incomeDelta == 0f && !cancelUnusedSubs,
                            onClick = {
                                diningPct = 0f
                                discretionaryPct = 0f
                                rentDelta = 0f
                                incomeDelta = 0f
                                cancelUnusedSubs = false
                                scenarioLabel = "Baseline (No Change)"
                            },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Reset") },
                            modifier = Modifier.testTag("preset_reset")
                        )
                    }
                }
            }
        }

        // Live Simulation Outcome Card
        item {
            val isPositive = result.monthlySurplusDelta >= 0
            val deltaColor = if (isPositive) IncomeGreen else ExpenseRed
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("what_if_result_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = deltaColor.copy(alpha = 0.10f)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Simulated Monthly Impact",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = deltaColor.copy(alpha = 0.18f)
                        ) {
                            val sign = if (result.monthlySurplusDelta >= 0) "+" else "-"
                            Text(
                                text = "$sign${Formatters.formatRupee(abs(result.monthlySurplusDelta))}/mo",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = deltaColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Baseline Surplus", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = Formatters.formatRupee(result.baselineMonthlySurplus),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Simulated Surplus", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = Formatters.formatRupee(result.simulatedMonthlySurplus),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (result.simulatedMonthlySurplus >= 0) EmeraldPrimary else ExpenseRed
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val annSign = if (result.annualImpact >= 0) "+" else "-"
                        Text(
                            text = "12-Month Impact: $annSign${Formatters.formatRupee(abs(result.annualImpact))}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = deltaColor
                        )
                        val efText = when (val simM = result.simulatedEmergencyMonthsToTarget) {
                            0 -> "EF Funded ✓"
                            null -> "EF Stalled"
                            else -> "EF in ${simM}m"
                        }
                        Text(
                            text = efText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = SkyTertiary
                        )
                    }
                }
            }
        }

        // Interactive Controls Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Adjust Variables",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Dining out % slider
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Dining / Food Spend", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        val dInt = diningPct.roundToInt()
                        Text(
                            text = "${if (dInt > 0) "+" else ""}$dInt% (${Formatters.formatRupee(result.simulatedDiningMonthly)}/mo)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (dInt <= 0) IncomeGreen else ExpenseRed
                        )
                    }
                    Slider(
                        value = diningPct,
                        onValueChange = {
                            diningPct = (it / 5f).roundToInt() * 5f
                            scenarioLabel = "Custom Scenario"
                        },
                        valueRange = -60f..40f,
                        modifier = Modifier.testTag("slider_dining_pct")
                    )

                    // Discretionary % slider
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Shopping & Entertainment", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        val discInt = discretionaryPct.roundToInt()
                        Text(
                            text = "${if (discInt > 0) "+" else ""}$discInt% (${Formatters.formatRupee(result.simulatedDiscretionaryMonthly)}/mo)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (discInt <= 0) IncomeGreen else ExpenseRed
                        )
                    }
                    Slider(
                        value = discretionaryPct,
                        onValueChange = {
                            discretionaryPct = (it / 5f).roundToInt() * 5f
                            scenarioLabel = "Custom Scenario"
                        },
                        valueRange = -60f..40f,
                        modifier = Modifier.testTag("slider_discretionary_pct")
                    )

                    // Rent / Fixed Costs delta slider
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Rent / Fixed Costs Change", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        val rInt = rentDelta.roundToInt()
                        Text(
                            text = "${if (rInt >= 0) "+" else "-"}₹${abs(rInt)}/mo",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (rInt <= 0) IncomeGreen else ExpenseRed
                        )
                    }
                    Slider(
                        value = rentDelta,
                        onValueChange = {
                            rentDelta = (it / 500f).roundToInt() * 500f
                            scenarioLabel = "Custom Scenario"
                        },
                        valueRange = -3000f..5000f,
                        modifier = Modifier.testTag("slider_rent_delta")
                    )

                    // Income delta slider
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Monthly Income / Stipend Change", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        val iInt = incomeDelta.roundToInt()
                        Text(
                            text = "${if (iInt >= 0) "+" else "-"}₹${abs(iInt)}/mo",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (iInt >= 0) IncomeGreen else ExpenseRed
                        )
                    }
                    Slider(
                        value = incomeDelta,
                        onValueChange = {
                            incomeDelta = (it / 500f).roundToInt() * 500f
                            scenarioLabel = "Custom Scenario"
                        },
                        valueRange = -5000f..10000f,
                        modifier = Modifier.testTag("slider_income_delta")
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cancel Unused Subscriptions",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (unusedSubCount > 0) "Frees ${Formatters.formatRupee(unusedSubTotal)}/mo from $unusedSubCount idle plan(s)"
                                else "No unused subscriptions currently flagged",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = cancelUnusedSubs,
                            onCheckedChange = {
                                cancelUnusedSubs = it
                                scenarioLabel = "Custom Scenario"
                            },
                            modifier = Modifier.testTag("switch_cancel_unused_subs")
                        )
                    }
                }
            }
        }

        // Per-Goal ETA Projections
        if (result.goalProjections.isNotEmpty()) {
            item {
                Text(
                    text = "Goal Timeline Impact",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(result.goalProjections, key = { it.goal.id }) { proj ->
                GoalProjectionCard(projection = proj)
            }
        }
    }
}

@Composable
private fun GoalProjectionCard(projection: GoalProjection) {
    val baseTxt = when (val m = projection.baselineMonthsToComplete) {
        0 -> "Done"
        null -> "Stalled"
        else -> "${m}m"
    }
    val simTxt = when (val m = projection.simulatedMonthsToComplete) {
        0 -> "Done"
        null -> "Stalled"
        else -> "${m}m"
    }
    val shiftColor = when {
        projection.monthsSaved > 0 -> IncomeGreen
        projection.monthsSaved < 0 -> ExpenseRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shiftBadge = when {
        projection.monthsSaved > 0 -> "${projection.monthsSaved}m faster"
        projection.monthsSaved < 0 -> "${abs(projection.monthsSaved)}m slower"
        projection.simulatedMonthsToComplete == null -> "Underfunded"
        else -> "No shift"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("goal_projection_${projection.goal.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = projection.goal.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Target: ${projection.goal.targetDate.ifBlank { "Flexible" }} • Remaining: ${Formatters.formatRupee(projection.goal.remainingAmount)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ETA: $baseTxt → $simTxt  (Alloc: ${Formatters.formatRupee(projection.simulatedAllocatedMonthly)}/mo)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = shiftColor.copy(alpha = 0.14f)
            ) {
                Text(
                    text = shiftBadge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = shiftColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun GoalItemCard(
    goal: GoalEntity,
    onAddSavings: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = if (goal.targetAmount > 0) (goal.savedAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
    val percent = (progress * 100).toInt()
    val monthsLeft = goal.monthsUntilTarget()
    val requiredMonthly = goal.requiredMonthlyContribution()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("goal_item_${goal.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(WarningOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when {
                            goal.title.contains("laptop", true) -> Icons.Default.Laptop
                            goal.title.contains("trip", true) || goal.title.contains("goa", true) -> Icons.Default.Flight
                            goal.title.contains("emergency", true) -> Icons.Default.Shield
                            else -> Icons.Default.Savings
                        }
                        Icon(imageVector = icon, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = goal.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${Formatters.formatRupee(goal.savedAmount)} of ${Formatters.formatRupee(goal.targetAmount)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldLight.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldLight,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (percent >= 100) EmeraldLight else WarningOrange,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Target: ${goal.targetDate.ifBlank { "Flexible" }} (${monthsLeft}m left)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (goal.remainingAmount > 0) {
                        Text(
                            text = "Need ${Formatters.formatRupee(requiredMonthly)}/mo to hit target",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = SkyTertiary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onAddSavings,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("add_savings_btn_${goal.id}")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Savings", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
