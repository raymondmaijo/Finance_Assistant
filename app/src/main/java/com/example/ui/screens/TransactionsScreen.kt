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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.data.model.TransactionEntity
import com.example.ui.components.CategoryBadge
import com.example.ui.components.CategoryIcons
import com.example.ui.components.Formatters
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.SkyTertiary
import com.example.ui.viewmodel.MainViewModel
import java.util.Calendar

val ALL_CATEGORIES = listOf(
    "Food", "People", "Transportation", "Entertainment", "Shopping", "Education",
    "Bills", "Mobile/Internet", "Subscriptions", "Investments", "Healthcare",
    "Miscellaneous", "Income", "Other"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: MainViewModel,
    onAddTransactionClick: () -> Unit,
    onOpenStatementImport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val latestAddedTxnId by viewModel.latestAddedTxnId.collectAsStateWithLifecycle()
    val maskAccounts = profile?.maskAccountNumbers ?: true

    val context = LocalContext.current
    val listState = rememberLazyListState()
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.scanInboxForUpi(context)
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("ALL") } // ALL, DEBIT, CREDIT
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var selectedDateFilter by remember { mutableStateOf("ALL") } // ALL, THIS_MONTH, THIS_WEEK

    var activeDetailTxn by remember { mutableStateOf<TransactionEntity?>(null) }
    var txnToSplit by remember { mutableStateOf<TransactionEntity?>(null) }
    var txnToDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var lastSeenAddedId by remember { mutableStateOf<Long?>(null) }

    // Filter transactions
    val filteredList = remember(
        allTransactions,
        searchQuery,
        selectedTypeFilter,
        selectedCategoryFilter,
        selectedDateFilter
    ) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)

        allTransactions.filter { txn ->
            // Search query
            val matchesQuery = searchQuery.isBlank() ||
                    txn.merchant.contains(searchQuery, ignoreCase = true) ||
                    txn.notes.contains(searchQuery, ignoreCase = true) ||
                    txn.category.contains(searchQuery, ignoreCase = true) ||
                    txn.accountRef.contains(searchQuery, ignoreCase = true)

            // Type (case-insensitive)
            val matchesType = when (selectedTypeFilter) {
                "DEBIT" -> txn.isDebit
                "CREDIT" -> txn.isCredit
                else -> true
            }

            // Category
            val matchesCat = selectedCategoryFilter == "ALL" || txn.category.equals(selectedCategoryFilter, ignoreCase = true)

            // Date
            val matchesDate = when (selectedDateFilter) {
                "THIS_MONTH" -> {
                    val tCal = Calendar.getInstance().apply { timeInMillis = txn.date }
                    tCal.get(Calendar.MONTH) == currentMonth && tCal.get(Calendar.YEAR) == currentYear
                }
                "THIS_WEEK" -> {
                    txn.date >= (now - (7L * 24 * 60 * 60 * 1000))
                }
                else -> true
            }

            matchesQuery && matchesType && matchesCat && matchesDate
        }.sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.id })
    }

    // When a brand-new transaction is added, ensure filters don't hide it and scroll straight to top
    LaunchedEffect(latestAddedTxnId) {
        if (latestAddedTxnId != null && latestAddedTxnId != lastSeenAddedId) {
            lastSeenAddedId = latestAddedTxnId
            searchQuery = ""
            selectedTypeFilter = "ALL"
            selectedCategoryFilter = "ALL"
            selectedDateFilter = "ALL"
        }
    }

    // Prevent Compose LazyColumn key-anchoring from pushing newly prepended items above the viewport
    val topTxnId = filteredList.firstOrNull()?.id
    LaunchedEffect(topTxnId) {
        if (topTxnId != null) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("transactions_screen"),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransactionClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(bottom = 72.dp)
                    .testTag("add_transaction_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Transaction")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_transactions_input"),
                placeholder = { Text("Search by merchant, notes, account...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            // Type & Date Filters
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = false,
                        onClick = onOpenStatementImport,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = "Import PDF or CSV",
                                tint = SkyTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Import PDF / CSV", color = SkyTertiary, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("chip_import_statement")
                    )
                }
                item {
                    FilterChip(
                        selected = false,
                        onClick = { smsPermissionLauncher.launch(Manifest.permission.READ_SMS) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Scan UPI SMS",
                                tint = EmeraldLight,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Scan UPI SMS", color = EmeraldLight, fontWeight = FontWeight.Bold) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTypeFilter == "ALL",
                        onClick = { selectedTypeFilter = "ALL" },
                        label = { Text("All Types") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTypeFilter == "DEBIT",
                        onClick = { selectedTypeFilter = "DEBIT" },
                        label = { Text("Expenses") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTypeFilter == "CREDIT",
                        onClick = { selectedTypeFilter = "CREDIT" },
                        label = { Text("Income") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedDateFilter == "THIS_MONTH",
                        onClick = {
                            selectedDateFilter = if (selectedDateFilter == "THIS_MONTH") "ALL" else "THIS_MONTH"
                        },
                        label = { Text("This Month") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedDateFilter == "THIS_WEEK",
                        onClick = {
                            selectedDateFilter = if (selectedDateFilter == "THIS_WEEK") "ALL" else "THIS_WEEK"
                        },
                        label = { Text("This Week") }
                    )
                }
            }

            // Category Chips Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategoryFilter == "ALL",
                        onClick = { selectedCategoryFilter = "ALL" },
                        label = { Text("All Categories") }
                    )
                }
                items(ALL_CATEGORIES) { cat ->
                    FilterChip(
                        selected = selectedCategoryFilter.equals(cat, ignoreCase = true),
                        onClick = {
                            selectedCategoryFilter = if (selectedCategoryFilter.equals(cat, ignoreCase = true)) "ALL" else cat
                        },
                        label = { Text(cat) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Total count and sum header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredList.size} Transactions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val sumDebits = filteredList.filter { it.isDebit }.sumOf { it.amount }
                if (sumDebits > 0) {
                    Text(
                        text = "Total Spent: ${Formatters.formatRupee(sumDebits)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseRed
                    )
                }
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No matching transactions found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Try adjusting your filters or search query.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredList, key = { it.id }) { txn ->
                        TransactionCardItem(
                            transaction = txn,
                            maskAccounts = maskAccounts,
                            onClick = { activeDetailTxn = txn },
                            onSplit = { txnToSplit = txn },
                            onDelete = { txnToDelete = txn }
                        )
                    }
                }
            }
        }
    }

    // Transaction Details & Edit Bottom Sheet
    if (activeDetailTxn != null) {
        TransactionEditBottomSheet(
            transaction = activeDetailTxn!!,
            onDismiss = { activeDetailTxn = null },
            onSave = { updatedMerchant, updatedCategory, updatedAccount, updatedNotes, isRecurring, learnAI, applyToPast ->
                val targetTxn = activeDetailTxn!!
                viewModel.updateTransactionDetails(
                    transaction = targetTxn,
                    newMerchant = updatedMerchant,
                    newCategory = updatedCategory,
                    newAccount = updatedAccount,
                    newNotes = updatedNotes,
                    isRecurring = isRecurring
                )
                if (updatedCategory != targetTxn.category) {
                    viewModel.fixTransactionCategoryWithLearning(
                        transaction = targetTxn.copy(merchant = updatedMerchant),
                        newCategory = updatedCategory,
                        learnRule = learnAI,
                        applyToSimilarPast = applyToPast
                    )
                }
                activeDetailTxn = null
            },
            onDelete = {
                viewModel.deleteTransaction(activeDetailTxn!!.id)
                activeDetailTxn = null
            },
            onSplit = {
                val target = activeDetailTxn
                activeDetailTxn = null
                txnToSplit = target
            }
        )
    }

    // Split Transaction Dialog
    if (txnToSplit != null) {
        SplitTransactionDialog(
            transaction = txnToSplit!!,
            onDismiss = { txnToSplit = null },
            onConfirmSplit = { cat1, amt1, cat2, amt2 ->
                viewModel.splitTransaction(txnToSplit!!, cat1, amt1, cat2, amt2)
                txnToSplit = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (txnToDelete != null) {
        AlertDialog(
            onDismissRequest = { txnToDelete = null },
            title = { Text("Delete Transaction") },
            text = { Text("Are you sure you want to delete this ₹${txnToDelete!!.amount.toLong()} transaction to ${txnToDelete!!.merchant}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransaction(txnToDelete!!.id)
                        txnToDelete = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { txnToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun TransactionCardItem(
    transaction: TransactionEntity,
    maskAccounts: Boolean,
    onClick: () -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable { onClick() }
            .testTag("txn_card_${transaction.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CategoryIcons.getColor(transaction.category).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CategoryIcons.getIcon(transaction.category),
                        contentDescription = transaction.category,
                        tint = CategoryIcons.getColor(transaction.category),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.merchant,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${Formatters.formatDate(transaction.date)} • ${transaction.paymentMethod}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    val isDebit = transaction.isDebit
                    Text(
                        text = "${if (isDebit) "- " else "+ "}${Formatters.formatRupee(transaction.amount)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDebit) ExpenseRed else IncomeGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryBadge(category = transaction.category)
                    if (transaction.accountRef.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = Formatters.maskAccount(transaction.accountRef, maskAccounts),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (transaction.categorizedByRule) {
                        Text(
                            text = "• Rule Applied",
                            style = MaterialTheme.typography.labelSmall,
                            color = SkyTertiary,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (transaction.isAiCategorized) {
                        Text(
                            text = "• AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldLight,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = onSplit,
                        modifier = Modifier.size(28.dp).testTag("btn_split_${transaction.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallSplit,
                            contentDescription = "Split transaction",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp).testTag("btn_delete_${transaction.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete transaction",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (transaction.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Note: ${transaction.notes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditBottomSheet(
    transaction: TransactionEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Boolean, Boolean, Boolean) -> Unit,
    onDelete: () -> Unit,
    onSplit: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var merchant by remember { mutableStateOf(transaction.merchant) }
    var category by remember { mutableStateOf(transaction.category) }
    var accountRef by remember { mutableStateOf(transaction.accountRef) }
    var notes by remember { mutableStateOf(transaction.notes) }
    var isRecurring by remember { mutableStateOf(transaction.isRecurring) }
    var learnAI by remember { mutableStateOf(true) }
    var applyToSimilarPast by remember { mutableStateOf(true) }

    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("edit_transaction_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Transaction Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${Formatters.formatRupee(transaction.amount)} • ${transaction.type}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (transaction.isDebit) ExpenseRed else IncomeGreen,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Merchant field
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant / Payee") },
                modifier = Modifier.fillMaxWidth().testTag("edit_merchant_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category Dropdown
            ExposedDropdownMenuBox(
                expanded = categoryDropdownExpanded,
                onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .testTag("edit_category_selector")
                )
                ExposedDropdownMenu(
                    expanded = categoryDropdownExpanded,
                    onDismissRequest = { categoryDropdownExpanded = false }
                ) {
                    ALL_CATEGORIES.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                category = cat
                                categoryDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Account Ref
            OutlinedTextField(
                value = accountRef,
                onValueChange = { accountRef = it },
                label = { Text("Account Identifier (e.g. XX1234)") },
                modifier = Modifier.fillMaxWidth().testTag("edit_account_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes / Split details") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Checkbox: Teach AI this category correction
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { learnAI = !learnAI }
            ) {
                Checkbox(checked = learnAI, onCheckedChange = { learnAI = it })
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Learn rule for future imports",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Auto-tag future PDF, CSV & SMS payments to '$merchant' as '$category'",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Checkbox: Also update similar past transactions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { applyToSimilarPast = !applyToSimilarPast }
            ) {
                Checkbox(checked = applyToSimilarPast, onCheckedChange = { applyToSimilarPast = it })
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Also update similar past transactions",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Checkbox: Recurring
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { isRecurring = !isRecurring }
            ) {
                Checkbox(checked = isRecurring, onCheckedChange = { isRecurring = it })
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Mark as Recurring Expense",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onSave(merchant, category, accountRef, notes, isRecurring, learnAI, applyToSimilarPast)
                    },
                    modifier = Modifier.weight(1f).testTag("save_transaction_details_btn")
                ) {
                    Text("Save Changes")
                }

                TextButton(
                    onClick = onSplit,
                    modifier = Modifier.testTag("split_transaction_btn")
                ) {
                    Text("Split")
                }

                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = ExpenseRed)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SplitTransactionDialog(
    transaction: TransactionEntity,
    onDismiss: () -> Unit,
    onConfirmSplit: (String, Double, String, Double) -> Unit
) {
    val total = transaction.amount
    val half = total / 2.0

    var cat1 by remember { mutableStateOf(transaction.category) }
    var amt1 by remember { mutableStateOf(half.toString()) }
    var cat2 by remember { mutableStateOf("Entertainment") }
    var amt2 by remember { mutableStateOf(half.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split Transaction (${Formatters.formatRupee(total)})") },
        text = {
            Column {
                Text(
                    text = "Allocate this transaction across two different categories.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Split 1 Category", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = cat1,
                    onValueChange = { cat1 = it },
                    label = { Text("Category 1") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amt1,
                    onValueChange = {
                        amt1 = it
                        val v = it.toDoubleOrNull()
                        if (v != null && v <= total) {
                            amt2 = (total - v).coerceAtLeast(0.0).toString()
                        }
                    },
                    label = { Text("Amount 1 (₹)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Split 2 Category", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = cat2,
                    onValueChange = { cat2 = it },
                    label = { Text("Category 2") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amt2,
                    onValueChange = { amt2 = it },
                    label = { Text("Amount 2 (₹)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val a1 = amt1.toDoubleOrNull() ?: half
                    val a2 = amt2.toDoubleOrNull() ?: half
                    onConfirmSplit(cat1, a1, cat2, a2)
                },
                modifier = Modifier.testTag("confirm_split_btn")
            ) {
                Text("Confirm Split")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
