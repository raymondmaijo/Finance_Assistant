package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTransactionDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("DEBIT") }
    var category by remember { mutableStateOf("Food") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var paymentMethod by remember { mutableStateOf("UPI") }
    var accountRef by remember { mutableStateOf("XX1234") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == "DEBIT") "Add Expense" else "Add Income") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = type == "DEBIT",
                        onClick = { type = "DEBIT" },
                        label = { Text("Expense (Debit)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = type == "CREDIT",
                        onClick = { type = "CREDIT" },
                        label = { Text("Income (Credit)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount (₹)") },
                    modifier = Modifier.fillMaxWidth().testTag("manual_amount_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text(if (type == "DEBIT") "Merchant / Person (e.g. Canteen, Zomato)" else "Received From (e.g. Papa, Stipend)") },
                    modifier = Modifier.fillMaxWidth().testTag("manual_merchant_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        ALL_CATEGORIES.forEach { cat ->
                            DropdownMenuItem(text = { Text(cat) }, onClick = { category = cat; categoryExpanded = false })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = accountRef,
                    onValueChange = { accountRef = it },
                    label = { Text("Account (e.g. XX1234, Cash)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    if (amt != null && amt > 0 && merchant.isNotBlank()) {
                        viewModel.addManualTransaction(amt, type, merchant, category, paymentMethod, accountRef, notes)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("confirm_manual_tx_btn")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
