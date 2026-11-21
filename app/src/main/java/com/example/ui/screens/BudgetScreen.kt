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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BudgetEntity
import com.example.data.model.RecurringExpenseEntity
import com.example.ui.components.CategoryBadge
import com.example.ui.components.CategoryIcons
import com.example.ui.components.Formatters
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.MainViewModel

@Composable
fun BudgetScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val budgets by viewModel.allBudgets.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val recurringList by viewModel.allRecurring.collectAsStateWithLifecycle()

    var editingBudget by remember { mutableStateOf<BudgetEntity?>(null) }
    var isAddingNewBudget by remember { mutableStateOf(false) }
    var isAddingRecurring by remember { mutableStateOf(false) }

    val allowance = profile?.monthlyAllowance ?: 10000.0
    val totalBudgetLimits = budgets.sumOf { it.monthlyLimit }
    val totalSpent = metrics.totalSpentThisMonth

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("budget_screen"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)
    ) {
        // Overall Monthly Allowance vs Spent Header Card
        item {
            OverallBudgetOverviewCard(
                allowance = allowance,
                totalSpent = totalSpent,
                remaining = (allowance - totalSpent).coerceAtLeast(0.0)
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Category Budgets Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Category Budgets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = { viewModel.autoFillBudgetsFromAvailableData() },
                        modifier = Modifier.testTag("auto_fill_category_budgets_btn"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto-Fill", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(
                        onClick = { isAddingNewBudget = true },
                        modifier = Modifier.testTag("add_category_budget_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Smart Auto-Fill Banner Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auto_fill_smart_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Auto-Fill",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Fill Category Budgets",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Calculates smart limits based on your actual spending data (Food, People, Shopping, etc.)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.autoFillBudgetsFromAvailableData() },
                        modifier = Modifier.testTag("autofill_budgets_now_btn"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Auto-Fill", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Category Budget Cards
        if (budgets.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No custom budgets set yet.")
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.autoFillBudgetsFromAvailableData() },
                                modifier = Modifier.testTag("empty_state_autofill_btn")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Auto-Fill from Available Data")
                            }
                            OutlinedButton(onClick = { isAddingNewBudget = true }) {
                                Text("Add Manually")
                            }
                        }
                    }
                }
            }
        } else {
            items(budgets, key = { it.categoryName }) { b ->
                val spent = metrics.categorySpending[b.categoryName] ?: 0.0
                CategoryBudgetCard(
                    budget = b,
                    spent = spent,
                    onEdit = { editingBudget = b }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // Recurring Expenses Section
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Loop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recurring & Subscriptions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(
                    onClick = { isAddingRecurring = true },
                    modifier = Modifier.testTag("add_recurring_btn")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (recurringList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "No recurring subscriptions tracked yet. Add your Netflix, Spotify, Gym, or Mobile bills here!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(recurringList, key = { it.id }) { rec ->
                RecurringExpenseItem(
                    recurring = rec,
                    onDelete = { viewModel.deleteRecurring(rec) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Edit Budget Dialog
    if (editingBudget != null) {
        var limitStr by remember { mutableStateOf(editingBudget!!.monthlyLimit.toLong().toString()) }
        AlertDialog(
            onDismissRequest = { editingBudget = null },
            title = { Text("Edit ${editingBudget!!.categoryName} Budget") },
            text = {
                Column {
                    Text("Set monthly spending limit for ${editingBudget!!.categoryName}:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = limitStr,
                        onValueChange = { limitStr = it },
                        label = { Text("Monthly Limit (₹)") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_budget_limit_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val limit = limitStr.toDoubleOrNull() ?: editingBudget!!.monthlyLimit
                        viewModel.setBudget(editingBudget!!.categoryName, limit)
                        editingBudget = null
                    },
                    modifier = Modifier.testTag("confirm_save_budget_btn")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBudget = null }) { Text("Cancel") }
            }
        )
    }

    // Add New Budget Dialog
    if (isAddingNewBudget) {
        var catName by remember { mutableStateOf("Food") }
        var limitStr by remember { mutableStateOf("3000") }
        AlertDialog(
            onDismissRequest = { isAddingNewBudget = false },
            title = { Text("Set Category Budget") },
            text = {
                Column {
                    OutlinedTextField(
                        value = catName,
                        onValueChange = { catName = it },
                        label = { Text("Category Name") },
                        modifier = Modifier.fillMaxWidth().testTag("new_budget_category_input")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("People", "Food", "Shopping", "Transport").forEach { c ->
                            FilterChip(
                                selected = catName.equals(c, ignoreCase = true),
                                onClick = { catName = c },
                                label = { Text(c, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = limitStr,
                        onValueChange = { limitStr = it },
                        label = { Text("Monthly Limit (₹)") },
                        modifier = Modifier.fillMaxWidth().testTag("new_budget_limit_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val limit = limitStr.toDoubleOrNull() ?: 3000.0
                        viewModel.setBudget(catName.trim(), limit)
                        isAddingNewBudget = false
                    },
                    modifier = Modifier.testTag("save_new_budget_btn")
                ) {
                    Text("Set Budget")
                }
            },
            dismissButton = {
                TextButton(onClick = { isAddingNewBudget = false }) { Text("Cancel") }
            }
        )
    }

    // Add Recurring Expense Dialog
    if (isAddingRecurring) {
        var title by remember { mutableStateOf("") }
        var amountStr by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Entertainment") }
        var frequency by remember { mutableStateOf("Monthly") }
        var dueDate by remember { mutableStateOf("1st of every month") }

        AlertDialog(
            onDismissRequest = { isAddingRecurring = false },
            title = { Text("Add Recurring Payment") },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Service Name (e.g. Netflix, Spotify, Jio)") },
                        modifier = Modifier.fillMaxWidth().testTag("recurring_title_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Amount (₹)") },
                        modifier = Modifier.fillMaxWidth().testTag("recurring_amount_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("Next Due Date") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountStr.toDoubleOrNull() ?: 199.0
                        if (title.isNotBlank()) {
                            viewModel.addRecurring(title, amt, category, frequency, dueDate)
                            isAddingRecurring = false
                        }
                    },
                    modifier = Modifier.testTag("save_recurring_btn")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { isAddingRecurring = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun OverallBudgetOverviewCard(
    allowance: Double,
    totalSpent: Double,
    remaining: Double
) {
    val progress = if (allowance > 0) (totalSpent / allowance).toFloat().coerceIn(0f, 1f) else 0f
    val percent = (progress * 100).toInt()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("overall_budget_card"),
        shape = RoundedCornerShape(20.dp),
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
                Text(
                    text = "Total Monthly Allowance",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = Formatters.formatRupee(allowance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = when {
                    progress >= 0.9f -> ExpenseRed
                    progress >= 0.75f -> WarningOrange
                    else -> EmeraldLight
                },
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Spent So Far", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${Formatters.formatRupee(totalSpent)} ($percent%)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Remaining to Spend", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        Formatters.formatRupee(remaining),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (remaining > 1000) IncomeGreen else WarningOrange
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryBudgetCard(
    budget: BudgetEntity,
    spent: Double,
    onEdit: () -> Unit
) {
    val limit = budget.monthlyLimit
    val fraction = if (limit > 0) (spent / limit).toFloat().coerceIn(0f, 1f) else 0f
    val percent = if (limit > 0) ((spent / limit) * 100).toInt() else 0

    val statusColor = when {
        percent >= 100 -> ExpenseRed
        percent >= 75 -> WarningOrange
        else -> IncomeGreen
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .testTag("budget_card_${budget.categoryName}"),
        shape = RoundedCornerShape(16.dp),
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CategoryIcons.getColor(budget.categoryName).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CategoryIcons.getIcon(budget.categoryName),
                            contentDescription = budget.categoryName,
                            tint = CategoryIcons.getColor(budget.categoryName),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = budget.categoryName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${Formatters.formatRupee(spent)} of ${Formatters.formatRupee(limit)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "$percent% used",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )

            if (percent >= 90) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = statusColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (percent >= 100) "Budget limit exceeded for ${budget.categoryName}!" else "Approaching budget limit!",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun RecurringExpenseItem(
    recurring: RecurringExpenseEntity,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recurring_item_${recurring.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recurring.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${recurring.frequency} • Next: ${recurring.nextDueDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = Formatters.formatRupee(recurring.amount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = ExpenseRed
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}
