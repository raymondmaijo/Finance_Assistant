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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.InvestmentEntity
import com.example.ui.components.Formatters
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.PurpleInvestment
import com.example.ui.theme.SkyTertiary
import com.example.ui.viewmodel.MainViewModel

val INVESTMENT_TYPES = listOf(
    "Mutual Fund / SIP",
    "Stocks",
    "Fixed Deposit",
    "Gold",
    "Other"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val investments by viewModel.allInvestments.collectAsStateWithLifecycle()
    var isAddingInvestment by remember { mutableStateOf(false) }

    val totalInvested = investments.sumOf { it.investedAmount }
    val totalCurrentVal = investments.sumOf { it.currentValue }
    val totalMonthlySip = investments.sumOf { it.monthlySipAmount }
    val totalProfitLoss = totalCurrentVal - totalInvested
    val returnsPercent = if (totalInvested > 0) ((totalProfitLoss / totalInvested) * 100).toInt() else 0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("investments_screen"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)
    ) {
        // Portfolio Summary Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("investment_portfolio_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = PurpleInvestment.copy(alpha = 0.12f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Total Investment Portfolio",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Formatters.formatRupee(totalCurrentVal),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Invested Capital", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Formatters.formatRupee(totalInvested), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Monthly SIP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Formatters.formatRupee(totalMonthlySip), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Overall Gains", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${if (totalProfitLoss >= 0) "+" else ""}${Formatters.formatRupee(totalProfitLoss)} ($returnsPercent%)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (totalProfitLoss >= 0) IncomeGreen else ExpenseRed
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Header and Add Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tracked Assets (${investments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = { isAddingInvestment = true },
                    modifier = Modifier.testTag("add_investment_btn")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Asset")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Asset list
        if (investments.isEmpty()) {
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
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = PurpleInvestment,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No investments recorded yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Track your Groww/Zerodha index funds, college Fixed Deposits, or recurring SIPs here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(investments, key = { it.id }) { item ->
                InvestmentCardItem(
                    investment = item,
                    onDelete = { viewModel.deleteInvestment(item) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // Compliance Disclaimer & Compound Interest Note
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = SkyTertiary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Informational & Tracking Tracker Only", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Finance Coach does not execute buy/sell orders or provide licensed financial advisory. Investing ₹500/month in index funds during your 4 college years builds life-changing compounding habits.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Add Investment Dialog
    if (isAddingInvestment) {
        var name by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("Mutual Fund / SIP") }
        var typeExpanded by remember { mutableStateOf(false) }
        var investedStr by remember { mutableStateOf("") }
        var currentStr by remember { mutableStateOf("") }
        var sipStr by remember { mutableStateOf("500") }

        AlertDialog(
            onDismissRequest = { isAddingInvestment = false },
            title = { Text("Track Investment") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Asset Name (e.g. Nifty 50 Index, SBI FD)") },
                        modifier = Modifier.fillMaxWidth().testTag("inv_name_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = !typeExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = type,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false }
                        ) {
                            INVESTMENT_TYPES.forEach { t ->
                                DropdownMenuItem(text = { Text(t) }, onClick = { type = t; typeExpanded = false })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = investedStr,
                        onValueChange = { investedStr = it },
                        label = { Text("Invested Capital (₹)") },
                        modifier = Modifier.fillMaxWidth().testTag("inv_invested_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = currentStr,
                        onValueChange = { currentStr = it },
                        label = { Text("Current Value (₹)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sipStr,
                        onValueChange = { sipStr = it },
                        label = { Text("Monthly SIP amount (if recurring)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val inv = investedStr.toDoubleOrNull() ?: 1000.0
                        val cur = currentStr.toDoubleOrNull() ?: inv
                        val sip = sipStr.toDoubleOrNull() ?: 0.0
                        if (name.isNotBlank()) {
                            viewModel.addInvestment(name, type, inv, cur, sip)
                            isAddingInvestment = false
                        }
                    },
                    modifier = Modifier.testTag("save_inv_btn")
                ) {
                    Text("Save Asset")
                }
            },
            dismissButton = {
                TextButton(onClick = { isAddingInvestment = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun InvestmentCardItem(
    investment: InvestmentEntity,
    onDelete: () -> Unit
) {
    val gain = investment.currentValue - investment.investedAmount
    val gainPercent = if (investment.investedAmount > 0) ((gain / investment.investedAmount) * 100).toInt() else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("investment_item_${investment.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(PurpleInvestment.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = PurpleInvestment,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = investment.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${investment.type}${if (investment.monthlySipAmount > 0) " • SIP ${Formatters.formatRupee(investment.monthlySipAmount)}/mo" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Formatters.formatRupee(investment.currentValue),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${if (gain >= 0) "+" else ""}${Formatters.formatRupee(gain)} ($gainPercent%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (gain >= 0) IncomeGreen else ExpenseRed,
                    fontWeight = FontWeight.SemiBold
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}
