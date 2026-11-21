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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.RecurringExpenseEntity
import com.example.ui.components.CategoryBadge
import com.example.ui.components.Formatters
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.SkyTertiary
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SubscriptionsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val recurringList by viewModel.allRecurring.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    val activeItems = recurringList.filter { it.status != "CANCELLED" }
    val cancelledItems = recurringList.filter { it.status == "CANCELLED" }
    val priceAlerts = activeItems.filter { it.status == "PRICE_CHANGED" }
    val unusedAlerts = activeItems.filter { it.status == "UNUSED" }
    val totalMonthly = activeItems.sumOf { if (it.frequency.equals("Weekly", true)) it.amount * 4.0 else it.amount }
    val unusedAnnualSavings = unusedAlerts.sumOf { it.amount } * 12.0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("subscriptions_screen"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("subscriptions_summary_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Monthly Recurring & Subscriptions",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${Formatters.formatRupee(totalMonthly)}/mo",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (priceAlerts.isNotEmpty() || unusedAlerts.isNotEmpty()) {
                                WarningOrange.copy(alpha = 0.2f)
                            } else {
                                EmeraldLight.copy(alpha = 0.18f)
                            }
                        ) {
                            Text(
                                text = "${priceAlerts.size + unusedAlerts.size} Alert(s)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (priceAlerts.isNotEmpty() || unusedAlerts.isNotEmpty()) WarningOrange else EmeraldPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (unusedAnnualSavings > 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ExpenseRed.copy(alpha = 0.10f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Cancelling ${unusedAlerts.size} unused subscription(s) saves ${Formatters.formatRupee(unusedAnnualSavings)}/year.",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ExpenseRed
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.scanTransactionsForRecurring() },
                            modifier = Modifier.weight(1f).testTag("scan_recurring_btn")
                        ) {
                            Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auto-Detect")
                        }
                        OutlinedButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.weight(1f).testTag("add_subscription_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add New")
                        }
                    }
                }
            }
        }

        // Alerts Section (Price Changes & Unused)
        val alertItems = activeItems.filter { it.hasAlert }
        if (alertItems.isNotEmpty()) {
            item {
                Text(
                    text = "Action Needed (${alertItems.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(alertItems, key = { "alert_${it.id}" }) { sub ->
                SubscriptionCard(
                    item = sub,
                    onMarkUsed = { viewModel.markSubscriptionUsed(sub) },
                    onAckPrice = { viewModel.acknowledgePriceChange(sub) },
                    onToggleCancel = { viewModel.toggleCancelSubscription(sub) },
                    onDelete = { viewModel.deleteRecurring(sub) }
                )
            }
        }

        // Healthy Active Recurring
        val healthyItems = activeItems.filter { !it.hasAlert }
        if (healthyItems.isNotEmpty()) {
            item {
                Text(
                    text = "Active & On Track (${healthyItems.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(healthyItems, key = { "active_${it.id}" }) { sub ->
                SubscriptionCard(
                    item = sub,
                    onMarkUsed = { viewModel.markSubscriptionUsed(sub) },
                    onAckPrice = { viewModel.acknowledgePriceChange(sub) },
                    onToggleCancel = { viewModel.toggleCancelSubscription(sub) },
                    onDelete = { viewModel.deleteRecurring(sub) }
                )
            }
        }

        // Cancelled / Paused
        if (cancelledItems.isNotEmpty()) {
            item {
                Text(
                    text = "Cancelled / Paused (${cancelledItems.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(cancelledItems, key = { "cancelled_${it.id}" }) { sub ->
                SubscriptionCard(
                    item = sub,
                    onMarkUsed = { viewModel.markSubscriptionUsed(sub) },
                    onAckPrice = { viewModel.acknowledgePriceChange(sub) },
                    onToggleCancel = { viewModel.toggleCancelSubscription(sub) },
                    onDelete = { viewModel.deleteRecurring(sub) }
                )
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var amountStr by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Subscriptions") }
        var dueDate by remember { mutableStateOf("1st of Month") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Recurring Payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Service / Bill Name (e.g. YouTube Premium)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Monthly Amount (₹)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("Next Due Date") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank() && amt > 0) {
                        viewModel.addRecurring(title.trim(), amt, category.trim(), "Monthly", dueDate.trim())
                        showAddDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SubscriptionCard(
    item: RecurringExpenseEntity,
    onMarkUsed: () -> Unit,
    onAckPrice: () -> Unit,
    onToggleCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val badgeColor = when (item.status) {
        "PRICE_CHANGED" -> ExpenseRed
        "UNUSED" -> WarningOrange
        "CANCELLED" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> EmeraldLight
    }
    val statusLabel = when (item.status) {
        "PRICE_CHANGED" -> "Price Changed"
        "UNUSED" -> "Unused 30d+"
        "CANCELLED" -> "Cancelled"
        else -> "Active"
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("subscription_card_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(badgeColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (item.status) {
                                "PRICE_CHANGED" -> Icons.Default.TrendingUp
                                "UNUSED" -> Icons.Default.Warning
                                "CANCELLED" -> Icons.Default.PauseCircle
                                else -> Icons.Default.Autorenew
                            },
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CategoryBadge(category = item.category)
                            Text(
                                text = "${item.frequency} • Next: ${item.nextDueDate.ifBlank { "Upcoming" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatters.formatRupee(item.amount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (item.status == "CANCELLED") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (item.alertMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = badgeColor.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.alertMessage,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = badgeColor,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (item.status) {
                    "PRICE_CHANGED" -> {
                        FilledTonalButton(
                            onClick = onAckPrice,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).testTag("ack_price_btn_${item.id}")
                        ) {
                            Text("Acknowledge")
                        }
                        OutlinedButton(
                            onClick = onToggleCancel,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).testTag("cancel_sub_btn_${item.id}")
                        ) {
                            Text("Cancel Plan", color = ExpenseRed)
                        }
                    }
                    "UNUSED" -> {
                        FilledTonalButton(
                            onClick = onMarkUsed,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).testTag("mark_used_btn_${item.id}")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Still Using")
                        }
                        Button(
                            onClick = onToggleCancel,
                            colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).testTag("cancel_unused_btn_${item.id}")
                        ) {
                            Text("Cancel & Save")
                        }
                    }
                    "CANCELLED" -> {
                        OutlinedButton(
                            onClick = onToggleCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reactivate")
                        }
                    }
                    else -> {
                        OutlinedButton(
                            onClick = onToggleCancel,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Pause / Cancel")
                        }
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete recurring",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
