package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.DebtProjectionEngine
import com.example.data.model.DebtEntity
import com.example.ui.components.Formatters
import kotlin.math.roundToInt

@Composable
fun AddEditDebtDialog(
    existingDebt: DebtEntity? = null,
    onDismiss: () -> Unit,
    onSave: (DebtEntity) -> Unit
) {
    var debtType by remember { mutableStateOf(existingDebt?.debtType ?: "STUDENT_LOAN") }
    var name by remember { mutableStateOf(existingDebt?.name ?: "") }
    var lender by remember { mutableStateOf(existingDebt?.lender ?: "") }
    var originalPrincipal by remember {
        mutableStateOf(existingDebt?.originalPrincipal?.toLong()?.toString() ?: "")
    }
    var currentBalance by remember {
        mutableStateOf(existingDebt?.currentBalance?.toLong()?.toString() ?: "")
    }
    var aprText by remember {
        mutableStateOf(existingDebt?.annualInterestRate?.toString() ?: if (debtType == "STUDENT_LOAN") "8.5" else "36.0")
    }
    var minPaymentText by remember {
        mutableStateOf(existingDebt?.minimumMonthlyPayment?.toLong()?.toString() ?: "")
    }
    var dueDayText by remember {
        mutableStateOf((existingDebt?.dueDayOfMonth ?: 5).toString())
    }
    var graceMonthsText by remember {
        mutableStateOf((existingDebt?.gracePeriodMonths ?: 0).toString())
    }
    var tenureYearsText by remember { mutableStateOf("5") }
    var notes by remember { mutableStateOf(existingDebt?.notes ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val isStudentLoan = debtType == "STUDENT_LOAN"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingDebt == null) "Add Debt Account" else "Edit Debt Account",
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isStudentLoan,
                        onClick = {
                            debtType = "STUDENT_LOAN"
                            if (existingDebt == null && aprText == "36.0") aprText = "8.5"
                        },
                        label = { Text("Student Loan") },
                        leadingIcon = { Icon(Icons.Default.School, contentDescription = null) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("debt_type_student_loan")
                    )
                    FilterChip(
                        selected = !isStudentLoan,
                        onClick = {
                            debtType = "CREDIT_CARD"
                            if (existingDebt == null && aprText == "8.5") aprText = "36.0"
                        },
                        label = { Text("Credit Card") },
                        leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("debt_type_credit_card")
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMsg = null },
                    label = { Text(if (isStudentLoan) "Loan Name (e.g., SBI Education Loan)" else "Card Name (e.g., HDFC Credit Card)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("debt_name_input")
                )

                OutlinedTextField(
                    value = lender,
                    onValueChange = { lender = it },
                    label = { Text("Bank / Lender") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = currentBalance,
                        onValueChange = { currentBalance = it.filter { ch -> ch.isDigit() || ch == '.' }; errorMsg = null },
                        label = { Text("Current Balance (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("debt_balance_input")
                    )
                    OutlinedTextField(
                        value = originalPrincipal,
                        onValueChange = { originalPrincipal = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text(if (isStudentLoan) "Original Loan (₹)" else "Credit Limit (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = aprText,
                        onValueChange = { aprText = it.filter { ch -> ch.isDigit() || ch == '.' }; errorMsg = null },
                        label = { Text("Interest APR (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("debt_apr_input")
                    )
                    OutlinedTextField(
                        value = minPaymentText,
                        onValueChange = { minPaymentText = it.filter { ch -> ch.isDigit() || ch == '.' }; errorMsg = null },
                        label = { Text(if (isStudentLoan) "Monthly EMI (₹)" else "Min Payment (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("debt_payment_input")
                    )
                }

                // Helpful EMI Auto-Calculator for Student Loans
                if (isStudentLoan) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = tenureYearsText,
                            onValueChange = { tenureYearsText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Tenure (Yrs)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.8f)
                        )
                        OutlinedButton(
                            onClick = {
                                val bal = currentBalance.toDoubleOrNull() ?: originalPrincipal.toDoubleOrNull() ?: 0.0
                                val rate = aprText.toDoubleOrNull() ?: 8.5
                                val yrs = (tenureYearsText.toIntOrNull() ?: 5).coerceIn(1, 25)
                                if (bal > 0) {
                                    val emi = DebtProjectionEngine.calculateMonthlyEmi(bal, rate, yrs * 12)
                                    minPaymentText = emi.roundToInt().toString()
                                }
                            },
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("debt_calc_emi_btn")
                        ) {
                            Icon(Icons.Default.Calculate, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auto-Calc EMI", fontSize = 12.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = dueDayText,
                        onValueChange = { dueDayText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Due Day (1–28)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (isStudentLoan) {
                        OutlinedTextField(
                            value = graceMonthsText,
                            onValueChange = { graceMonthsText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Grace Period (Mo)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    val bal = currentBalance.toDoubleOrNull()
                    val apr = aprText.toDoubleOrNull()
                    val pay = minPaymentText.toDoubleOrNull()

                    when {
                        trimmedName.isBlank() -> errorMsg = "Please enter an account name."
                        bal == null || bal <= 0.0 -> errorMsg = "Enter a valid balance greater than ₹0."
                        apr == null || apr < 0.0 || apr > 65.0 -> errorMsg = "Enter a realistic APR between 0% and 65%."
                        pay == null || pay <= 0.0 -> errorMsg = "Enter a monthly EMI or minimum payment."
                        else -> {
                            val orig = (originalPrincipal.toDoubleOrNull() ?: bal).coerceAtLeast(bal)
                            val dueDay = (dueDayText.toIntOrNull() ?: 5).coerceIn(1, 28)
                            val grace = if (isStudentLoan) (graceMonthsText.toIntOrNull() ?: 0).coerceIn(0, 48) else 0
                            onSave(
                                DebtEntity(
                                    id = existingDebt?.id ?: 0,
                                    name = trimmedName,
                                    debtType = debtType,
                                    lender = lender.trim(),
                                    originalPrincipal = orig,
                                    currentBalance = bal,
                                    annualInterestRate = apr,
                                    minimumMonthlyPayment = pay,
                                    dueDayOfMonth = dueDay,
                                    gracePeriodMonths = grace,
                                    notes = notes.trim(),
                                    createdAt = existingDebt?.createdAt ?: System.currentTimeMillis()
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.testTag("debt_save_btn")
            ) {
                Text(if (existingDebt == null) "Save Account" else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun LogDebtPaymentDialog(
    debt: DebtEntity,
    onDismiss: () -> Unit,
    onConfirmPayment: (amount: Double, recordInTransactions: Boolean) -> Unit
) {
    var paymentText by remember {
        mutableStateOf(
            minOf(debt.minimumMonthlyPayment, debt.currentBalance).roundToInt().toString()
        )
    }
    var logTransaction by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Log Payment — ${debt.name}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Current Balance: ${Formatters.formatRupee(debt.currentBalance)} • Est. Monthly Interest: ${Formatters.formatRupee(debt.currentMonthlyInterest)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = paymentText,
                    onValueChange = {
                        paymentText = it.filter { ch -> ch.isDigit() || ch == '.' }
                        errorMsg = null
                    },
                    label = { Text("Payment Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("debt_log_payment_input")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = logTransaction,
                        onCheckedChange = { logTransaction = it }
                    )
                    Text(
                        text = "Also record in Transactions ledger",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = paymentText.toDoubleOrNull()
                    if (amt == null || amt <= 0.0) {
                        errorMsg = "Enter a valid payment amount."
                    } else {
                        onConfirmPayment(amt, logTransaction)
                    }
                },
                modifier = Modifier.testTag("debt_confirm_payment_btn")
            ) {
                Text("Apply Payment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
