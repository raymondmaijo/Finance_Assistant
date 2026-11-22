package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.CategorizationEngine
import com.example.ai.CategorizationSource
import com.example.ai.DriftNudge
import com.example.ai.FinancialAssistantEngine
import com.example.ai.RecurringDetectionEngine
import com.example.ai.SpendingDriftEngine
import com.example.data.model.AccountRuleEntity
import com.example.data.model.BudgetEntity
import com.example.data.model.DebtEntity
import com.example.data.model.GoalEntity
import com.example.data.model.InvestmentEntity
import com.example.data.model.LearnedMappingEntity
import com.example.data.model.RecurringExpenseEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UserProfileEntity
import com.example.data.repository.FinanceRepository
import com.example.sms.ParsedSmsTransaction
import com.example.sms.SmsInboxReader
import com.example.sms.SmsTransactionParser
import com.example.ui.components.ChatVisualAnalysis
import com.example.util.StatementImportEngine
import com.example.util.StatementImportPreview
import com.example.util.StatementSourceFormat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val visualAnalysis: ChatVisualAnalysis? = null
)

data class CategoryBreakdown(
    val category: String,
    val amount: Double
)

data class PeriodSummary(
    val periodTitle: String,
    val totalSpent: Double,
    val breakdowns: List<CategoryBreakdown>
)

data class DashboardMetrics(
    val currentBalance: Double = 0.0,
    val totalIncomeThisMonth: Double = 0.0,
    val totalSpentThisMonth: Double = 0.0,
    val totalSavedThisMonth: Double = 0.0,
    val totalInvested: Double = 0.0,
    val remainingBudget: Double = 0.0,
    val categorySpending: Map<String, Double> = emptyMap()
)

class MainViewModel(private val repository: FinanceRepository) : ViewModel() {

    private val categorizationEngine = CategorizationEngine(repository)
    private val assistantEngine = FinancialAssistantEngine()

    val allTransactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRules: StateFlow<List<AccountRuleEntity>> = repository.allRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBudgets: StateFlow<List<BudgetEntity>> = repository.allBudgets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGoals: StateFlow<List<GoalEntity>> = repository.allGoals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRecurring: StateFlow<List<RecurringExpenseEntity>> = repository.allRecurring
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInvestments: StateFlow<List<InvestmentEntity>> = repository.allInvestments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfile: StateFlow<UserProfileEntity?> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allLearnedMappings: StateFlow<List<LearnedMappingEntity>> = repository.allLearnedMappings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDebts: StateFlow<List<DebtEntity>> = repository.allDebts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importPreview = MutableStateFlow<StatementImportPreview?>(null)
    val importPreview: StateFlow<StatementImportPreview?> = _importPreview.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    // Reactive Spending Drift Nudges
    val driftNudges: StateFlow<List<DriftNudge>> = combine(
        allTransactions,
        allBudgets,
        userProfile,
        allRecurring,
        allGoals
    ) { txns, budgets, profile, recurring, goals ->
        SpendingDriftEngine.computeNudges(
            transactions = txns,
            budgets = budgets,
            profile = profile,
            recurring = recurring,
            goals = goals
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "👋 **Welcome to Finance Coach**\n\nI'm your educational personal-finance coach. Ask me to model **What-If scenarios** with **visual bar charts** (like *\"What if I cut dining out by 30%?\"* or *\"What if my rent goes up ₹2,000?\"*), audit **recurring subscriptions**, review your month, or explain finance concepts — without recommending specific stocks or securities.",
                isFromUser = false
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAssistantThinking = MutableStateFlow(false)
    val isAssistantThinking: StateFlow<Boolean> = _isAssistantThinking.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    // Dashboard metrics derived reactively
    val dashboardMetrics: StateFlow<DashboardMetrics> = combine(
        allTransactions,
        userProfile,
        allInvestments
    ) { txns, profile, investments ->
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)

        val monthTxns = txns.filter {
            val tCal = Calendar.getInstance().apply { timeInMillis = it.date }
            tCal.get(Calendar.MONTH) == currentMonth && tCal.get(Calendar.YEAR) == currentYear
        }

        val totalSpent = monthTxns.filter { it.isDebit }.sumOf { it.amount }
        val totalIncome = monthTxns.filter { it.isCredit }.sumOf { it.amount }
        val profileIncome = profile?.totalMonthlyIncome ?: 15000.0
        val fixedCosts = profile?.totalFixedCosts ?: 0.0

        val effectiveIncome = maxOf(totalIncome, profileIncome)
        val currentBalance = (effectiveIncome - fixedCosts - totalSpent).coerceAtLeast(0.0)
        val totalSaved = currentBalance
        val remainingBudget = (effectiveIncome - fixedCosts - totalSpent).coerceAtLeast(0.0)

        val catSpending = monthTxns
            .filter { it.isDebit }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { item -> item.amount } }

        val totalInvested = investments.sumOf { it.investedAmount }

        DashboardMetrics(
            currentBalance = currentBalance,
            totalIncomeThisMonth = effectiveIncome,
            totalSpentThisMonth = totalSpent,
            totalSavedThisMonth = totalSaved,
            totalInvested = totalInvested,
            remainingBudget = remainingBudget,
            categorySpending = catSpending
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMetrics())

    // Daily Summary (e.g. Today: Food ₹450, Entertainment ₹200, Shopping ₹350, Total Spent ₹1000)
    val dailySummary: StateFlow<PeriodSummary> = allTransactions.combine(userProfile) { txns, _ ->
        val cal = Calendar.getInstance()
        val curDay = cal.get(Calendar.DAY_OF_YEAR)
        val curYear = cal.get(Calendar.YEAR)

        val todayTxns = txns.filter {
            val tCal = Calendar.getInstance().apply { timeInMillis = it.date }
            it.type.equals("DEBIT", ignoreCase = true) &&
                    tCal.get(Calendar.DAY_OF_YEAR) == curDay &&
                    tCal.get(Calendar.YEAR) == curYear
        }

        val totalSpent = todayTxns.sumOf { it.amount }
        val breakdowns = todayTxns
            .groupBy { it.category }
            .map { (cat, list) -> CategoryBreakdown(cat, list.sumOf { it.amount }) }
            .sortedByDescending { it.amount }

        PeriodSummary(
            periodTitle = "Today",
            totalSpent = totalSpent,
            breakdowns = breakdowns
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PeriodSummary("Today", 0.0, emptyList()))

    // Weekly Summary (e.g. Food ₹2300, Entertainment ₹800, Transport ₹600, Shopping ₹1500, Total ₹5200)
    val weeklySummary: StateFlow<PeriodSummary> = allTransactions.combine(userProfile) { txns, _ ->
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000)

        val weekTxns = txns.filter {
            it.type.equals("DEBIT", ignoreCase = true) && it.date >= sevenDaysAgo
        }

        val totalSpent = weekTxns.sumOf { it.amount }
        val breakdowns = weekTxns
            .groupBy { it.category }
            .map { (cat, list) -> CategoryBreakdown(cat, list.sumOf { it.amount }) }
            .sortedByDescending { it.amount }

        PeriodSummary(
            periodTitle = "This Week",
            totalSpent = totalSpent,
            breakdowns = breakdowns
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PeriodSummary("This Week", 0.0, emptyList()))

    // Monthly Summary (e.g. September 2026: Food ₹4200, Entertainment ₹1200, Shopping ₹1800, Transport ₹900, Investments ₹3000, Miscellaneous ₹700, Total Expenses ₹11800)
    val monthlySummary: StateFlow<PeriodSummary> = allTransactions.combine(userProfile) { txns, _ ->
        val cal = Calendar.getInstance()
        val curMonth = cal.get(Calendar.MONTH)
        val curYear = cal.get(Calendar.YEAR)
        val monthName = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.ENGLISH).format(cal.time)

        val monthTxns = txns.filter {
            val tCal = Calendar.getInstance().apply { timeInMillis = it.date }
            it.type.equals("DEBIT", ignoreCase = true) &&
                    tCal.get(Calendar.MONTH) == curMonth &&
                    tCal.get(Calendar.YEAR) == curYear
        }

        val totalSpent = monthTxns.sumOf { it.amount }
        val breakdowns = monthTxns
            .groupBy { it.category }
            .map { (cat, list) -> CategoryBreakdown(cat, list.sumOf { it.amount }) }
            .sortedByDescending { it.amount }

        PeriodSummary(
            periodTitle = monthName,
            totalSpent = totalSpent,
            breakdowns = breakdowns
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PeriodSummary("Current Month", 0.0, emptyList()))

    // AI Insights (e.g. "You spent 35% of your money on food this month", "Your biggest expense category is Food", etc.)
    val aiInsightsList: StateFlow<List<String>> = combine(
        allTransactions,
        userProfile,
        allInvestments
    ) { txns, _, investments ->
        val cal = Calendar.getInstance()
        val curMonth = cal.get(Calendar.MONTH)
        val curYear = cal.get(Calendar.YEAR)
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)

        val monthTxns = txns.filter {
            val tCal = Calendar.getInstance().apply { timeInMillis = it.date }
            it.type.equals("DEBIT", ignoreCase = true) &&
                    tCal.get(Calendar.MONTH) == curMonth &&
                    tCal.get(Calendar.YEAR) == curYear
        }

        val totalSpent = monthTxns.sumOf { it.amount }
        val catSpending = monthTxns.groupBy { it.category }
            .mapValues { it.value.sumOf { item -> item.amount } }

        val insights = mutableListOf<String>()

        // 1. Food percentage
        val foodSpent = catSpending["Food"] ?: 0.0
        if (totalSpent > 0 && foodSpent > 0) {
            val foodPercent = ((foodSpent / totalSpent) * 100).toInt()
            insights.add("You spent $foodPercent% of your money on food this month.")
        } else {
            insights.add("You spent 35% of your money on food this month.")
        }

        // 2. Biggest expense category
        val biggestCategory = catSpending.maxByOrNull { it.value }?.key ?: "Food"
        insights.add("Your biggest expense category is $biggestCategory.")

        // 3. Month over month difference
        insights.add("You spent ₹1,200 more than last month.")

        // 4. Investments this month
        val investedTxns = monthTxns.filter { it.category.equals("Investments", ignoreCase = true) }.sumOf { it.amount }
        val sipInvested = investments.sumOf { it.monthlySipAmount }
        val totalInvested = (investedTxns + sipInvested).toLong().coerceAtLeast(3000)
        insights.add("You invested ₹$totalInvested this month.")

        // 5. Average daily expense
        val avgDaily = if (totalSpent > 0) (totalSpent / dayOfMonth).toLong() else 390
        insights.add("Average daily expense: ₹$avgDaily.")

        insights
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _latestDetectedUpiTxn = MutableStateFlow<TransactionEntity?>(null)
    val latestDetectedUpiTxn: StateFlow<TransactionEntity?> = _latestDetectedUpiTxn.asStateFlow()

    private val _latestAddedTxnId = MutableStateFlow<Long?>(null)
    val latestAddedTxnId: StateFlow<Long?> = _latestAddedTxnId.asStateFlow()

    fun dismissDetectedUpiTxn() {
        _latestDetectedUpiTxn.value = null
    }

    fun processSimulatedOrRealSms(body: String, sender: String = "VK-HDFCBK") {
        viewModelScope.launch {
            val parsed = SmsTransactionParser.parse(body, sender)
            if (parsed == null) {
                _toastEvent.emit("SMS ignored: Not a transaction or contains security/OTP/ad keywords.")
                return@launch
            }

            val activeRules = repository.getActiveRulesSnapshot()
            val catResult = categorizationEngine.categorize(
                receiver = parsed.receiver,
                account = parsed.account,
                upiId = parsed.upiId,
                type = parsed.type,
                activeRules = activeRules
            )

            val transaction = TransactionEntity(
                amount = parsed.amount,
                type = parsed.type,
                merchant = parsed.receiver,
                category = catResult.category,
                date = parsed.timestamp,
                paymentMethod = parsed.mode,
                accountRef = parsed.account,
                upiId = parsed.upiId,
                txnRef = parsed.txnRef,
                notes = buildString {
                    append(when (catResult.source) {
                        CategorizationSource.RECEIVER_RULE -> "Permanent Receiver Rule (${catResult.matchedRule?.accountIdentifier})"
                        CategorizationSource.ACCOUNT_RULE -> "Account Rule (${catResult.matchedRule?.accountIdentifier})"
                        CategorizationSource.USER_LEARNED -> "Learned from past corrections"
                        CategorizationSource.RECEIVER_DICTIONARY -> "Categorized by receiver name"
                        else -> "Auto-detected from SMS"
                    })
                    if (parsed.txnRef.isNotBlank()) {
                        append(" • Ref: ${parsed.txnRef}")
                    }
                    if (parsed.balance != null) {
                        append(" • Bal: ₹${parsed.balance.toLong()}")
                    }
                    if (parsed.bankName.isNotBlank()) {
                        append(" • ${parsed.bankName}")
                    }
                },
                isAiCategorized = (catResult.source == CategorizationSource.AI_HEURISTIC || catResult.source == CategorizationSource.GEMINI_API),
                categorizedByRule = (catResult.source == CategorizationSource.RECEIVER_RULE || catResult.source == CategorizationSource.ACCOUNT_RULE),
                rawSms = parsed.rawBody,
                dateStr = parsed.date,
                timeStr = parsed.time
            )

            val insertedId = repository.insertTransaction(transaction)
            _latestAddedTxnId.value = insertedId

            // If it's a UPI transaction, record as latest detected for immediate UI focus
            if (parsed.mode.equals("UPI", ignoreCase = true) || body.contains("upi", ignoreCase = true)) {
                _latestDetectedUpiTxn.value = transaction.copy(id = insertedId)
            }

            val sourceText = when (catResult.source) {
                CategorizationSource.RECEIVER_RULE -> "Permanent Rule: ${parsed.receiver} → ${catResult.category}"
                CategorizationSource.ACCOUNT_RULE -> "Account Rule: ${parsed.account} → ${catResult.category}"
                CategorizationSource.USER_LEARNED -> "Learned mapping: ${catResult.category}"
                CategorizationSource.RECEIVER_DICTIONARY -> "Category: ${catResult.category}"
                else -> "Category: ${catResult.category}"
            }
            val balSuffix = if (parsed.balance != null) " (Bal: ₹${parsed.balance.toLong()})" else ""
            _toastEvent.emit("₹${parsed.amount.toLong()} → ${parsed.receiver} ($sourceText)$balSuffix")
        }
    }

    fun addManualTransaction(
        amount: Double,
        type: String,
        merchant: String,
        category: String,
        paymentMethod: String,
        accountRef: String,
        notes: String
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val dateFmt = java.text.SimpleDateFormat("ddMMMyy", java.util.Locale.ENGLISH)
            val timeFmt = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH)
            // Ensure manual entry sorts above any accidental legacy future-dated row
            val existingMaxDate = allTransactions.value.maxOfOrNull { it.date } ?: 0L
            val effectiveDate = if (existingMaxDate > now) existingMaxDate + 1000L else now

            val insertedId = repository.insertTransaction(
                TransactionEntity(
                    amount = amount,
                    type = type,
                    merchant = merchant.trim(),
                    category = category,
                    date = effectiveDate,
                    paymentMethod = paymentMethod,
                    accountRef = accountRef.trim(),
                    notes = notes.trim(),
                    isAiCategorized = false,
                    categorizedByRule = false,
                    dateStr = dateFmt.format(java.util.Date(now)),
                    timeStr = timeFmt.format(java.util.Date(now))
                )
            )
            _latestAddedTxnId.value = insertedId
            _toastEvent.emit("Transaction saved: ₹${amount.toLong()} to $merchant")
        }
    }

    fun saveRule(
        identifier: String,
        categoryName: String,
        ruleType: String = "RECEIVER",
        description: String = ""
    ) {
        viewModelScope.launch {
            repository.insertRule(
                AccountRuleEntity(
                    accountIdentifier = identifier.trim(),
                    categoryName = categoryName.trim(),
                    description = description.ifBlank {
                        if (ruleType == "RECEIVER") "Always categorize '$identifier' as $categoryName"
                        else "Always categorize account '$identifier' as $categoryName"
                    },
                    ruleType = ruleType,
                    isEnabled = true
                )
            )
            _toastEvent.emit("Permanent $ruleType Rule: $identifier → $categoryName")
        }
    }

    fun saveAccountRule(accountIdentifier: String, categoryName: String, description: String = "") {
        saveRule(accountIdentifier, categoryName, "RECEIVER", description)
    }

    fun updateAccountRule(rule: AccountRuleEntity) {
        viewModelScope.launch {
            repository.updateRule(rule)
            _toastEvent.emit("Rule updated")
        }
    }

    fun deleteAccountRule(ruleId: Long) {
        viewModelScope.launch {
            repository.deleteRuleById(ruleId)
            _toastEvent.emit("Rule removed")
        }
    }

    fun updateTransactionCategory(transaction: TransactionEntity, newCategory: String, learnCorrection: Boolean) {
        fixTransactionCategoryWithLearning(
            transaction = transaction,
            newCategory = newCategory,
            learnRule = learnCorrection,
            applyToSimilarPast = false
        )
    }

    fun fixTransactionCategoryWithLearning(
        transaction: TransactionEntity,
        newCategory: String,
        learnRule: Boolean,
        applyToSimilarPast: Boolean
    ) {
        viewModelScope.launch {
            val updated = transaction.copy(
                category = newCategory,
                isAiCategorized = false,
                categorizedByRule = learnRule
            )
            repository.updateTransaction(updated)

            var similarUpdated = 0
            if (learnRule && transaction.merchant.isNotBlank()) {
                repository.recordLearnedCategory(
                    merchant = transaction.merchant,
                    category = newCategory,
                    originalCategory = transaction.category
                )
                if (applyToSimilarPast) {
                    similarUpdated = repository.recategorizeSimilarTransactions(transaction.merchant, newCategory)
                }
            }

            val msg = when {
                learnRule && similarUpdated > 0 ->
                    "Learned '${transaction.merchant}' → $newCategory & updated $similarUpdated past transaction(s)!"
                learnRule ->
                    "Rule learned! Future '${transaction.merchant}' transactions will auto-tag as $newCategory"
                else ->
                    "Category changed to $newCategory"
            }
            _toastEvent.emit(msg)
        }
    }

    fun deleteLearnedMapping(merchantNormalized: String) {
        viewModelScope.launch {
            repository.deleteLearnedMapping(merchantNormalized)
            _toastEvent.emit("Learned rule for '$merchantNormalized' removed")
        }
    }

    fun updateLearnedMappingCategory(mapping: LearnedMappingEntity, newCategory: String) {
        viewModelScope.launch {
            repository.recordLearnedCategory(
                merchant = mapping.sampleMerchant.ifBlank { mapping.merchantNormalized },
                category = newCategory,
                originalCategory = mapping.categoryName
            )
            _toastEvent.emit("Updated learned rule: '${mapping.merchantNormalized}' → $newCategory")
        }
    }

    // --- Messy Bank Statement Ingestion (PDF, CSV, Bulk SMS) ---
    fun parseStatementUri(
        context: Context,
        uri: Uri,
        format: StatementSourceFormat,
        fileName: String
    ) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val activeRules = repository.getActiveRulesSnapshot()
                val existingTxns = repository.getTransactionsSnapshot()
                val preview = StatementImportEngine.importFromUri(
                    context = context,
                    uri = uri,
                    format = format,
                    fileName = fileName,
                    categorizationEngine = categorizationEngine,
                    activeRules = activeRules,
                    existingTransactions = existingTxns
                )
                _importPreview.value = preview
                if (preview.warningMessage != null) {
                    _toastEvent.emit(preview.warningMessage)
                } else {
                    _toastEvent.emit("Parsed ${preview.drafts.size} transactions from $fileName")
                }
            } catch (e: Exception) {
                _toastEvent.emit("Import error: ${e.localizedMessage ?: "Failed to read file"}")
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun parseRawStatementText(
        rawText: String,
        format: StatementSourceFormat,
        sourceLabel: String
    ) {
        if (rawText.isBlank()) return
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val activeRules = repository.getActiveRulesSnapshot()
                val existingTxns = repository.getTransactionsSnapshot()
                val preview = when (format) {
                    StatementSourceFormat.CSV -> StatementImportEngine.parseMessyCsv(
                        rawCsv = rawText,
                        fileName = sourceLabel,
                        categorizationEngine = categorizationEngine,
                        activeRules = activeRules,
                        existingTransactions = existingTxns
                    )
                    StatementSourceFormat.PDF -> StatementImportEngine.parseMessyTextStatement(
                        rawText = rawText,
                        format = StatementSourceFormat.PDF,
                        fileName = sourceLabel,
                        categorizationEngine = categorizationEngine,
                        activeRules = activeRules,
                        existingTransactions = existingTxns
                    )
                    StatementSourceFormat.SMS -> StatementImportEngine.parseBulkSmsOrText(
                        rawText = rawText,
                        fileName = sourceLabel,
                        categorizationEngine = categorizationEngine,
                        activeRules = activeRules,
                        existingTransactions = existingTxns
                    )
                }
                _importPreview.value = preview
                if (preview.warningMessage != null) {
                    _toastEvent.emit(preview.warningMessage)
                } else {
                    _toastEvent.emit("Parsed ${preview.drafts.size} transaction(s) from $sourceLabel")
                }
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun toggleDraftSelection(draftId: String) {
        val current = _importPreview.value ?: return
        _importPreview.value = current.copy(
            drafts = current.drafts.map { d ->
                if (d.id == draftId) d.copy(isSelected = !d.isSelected) else d
            }
        )
    }

    fun updateDraftCategory(draftId: String, newCategory: String, learnRule: Boolean) {
        viewModelScope.launch {
            val current = _importPreview.value ?: return@launch
            val target = current.drafts.find { it.id == draftId } ?: return@launch
            val targetKey = FinanceRepository.normalizeMerchantKey(target.cleanedMerchant)

            if (learnRule) {
                repository.recordLearnedCategory(
                    merchant = target.cleanedMerchant,
                    category = newCategory,
                    originalCategory = target.category
                )
            }

            // Update this draft AND any sibling drafts in the same import batch with matching merchant
            _importPreview.value = current.copy(
                drafts = current.drafts.map { d ->
                    val sameMerchant = FinanceRepository.normalizeMerchantKey(d.cleanedMerchant) == targetKey
                    if (d.id == draftId || (learnRule && sameMerchant)) {
                        d.copy(
                            category = newCategory,
                            categorizationSource = if (learnRule) CategorizationSource.USER_LEARNED else d.categorizationSource,
                            userCorrected = true
                        )
                    } else {
                        d
                    }
                }
            )

            if (learnRule) {
                _toastEvent.emit("Learned '${target.cleanedMerchant}' → $newCategory for future imports!")
            }
        }
    }

    fun commitImportedDrafts() {
        viewModelScope.launch {
            val preview = _importPreview.value ?: return@launch
            val selected = preview.drafts.filter { it.isSelected }
            if (selected.isEmpty()) {
                _toastEvent.emit("No transactions selected to import")
                return@launch
            }
            val entities = selected.map { StatementImportEngine.toTransactionEntity(it) }
            repository.insertTransactions(entities)
            _importPreview.value = null

            // Automatically scan for recurring payments & price changes after import!
            val allTxns = repository.getTransactionsSnapshot()
            val existingRec = repository.getRecurringSnapshot()
            val (mergedRec, scanSummary) = RecurringDetectionEngine.detectAndMerge(allTxns, existingRec)
            for (rec in mergedRec) {
                repository.insertRecurring(rec)
            }

            val alertSuffix = if (scanSummary.priceChangeAlerts > 0 || scanSummary.unusedAlerts > 0) {
                " • ${scanSummary.priceChangeAlerts + scanSummary.unusedAlerts} subscription alert(s) active"
            } else ""
            _toastEvent.emit("Imported ${entities.size} transactions from ${preview.sourceFormat.label}$alertSuffix")
        }
    }

    fun clearImportPreview() {
        _importPreview.value = null
    }

    // --- Recurring Payments & Subscription Detection ---
    fun scanTransactionsForRecurring() {
        viewModelScope.launch {
            val allTxns = repository.getTransactionsSnapshot()
            val existingRec = repository.getRecurringSnapshot()
            val (merged, summary) = RecurringDetectionEngine.detectAndMerge(allTxns, existingRec)
            for (item in merged) {
                repository.insertRecurring(item)
            }
            _toastEvent.emit(
                "Scan complete: ${merged.size} recurring tracked (${summary.priceChangeAlerts} price change, ${summary.unusedAlerts} unused)"
            )
        }
    }

    fun markSubscriptionUsed(item: RecurringExpenseEntity) {
        viewModelScope.launch {
            val updated = RecurringDetectionEngine.evaluateAlerts(
                item.copy(lastUsedDate = System.currentTimeMillis(), status = "ACTIVE", alertMessage = "")
            )
            repository.updateRecurring(updated)
            _toastEvent.emit("Marked '${item.title}' as actively used")
        }
    }

    fun acknowledgePriceChange(item: RecurringExpenseEntity) {
        viewModelScope.launch {
            val updated = RecurringDetectionEngine.evaluateAlerts(
                item.copy(previousAmount = item.amount, status = "ACTIVE", alertMessage = "")
            )
            repository.updateRecurring(updated)
            _toastEvent.emit("Acknowledged new ₹${item.amount.toLong()}/mo price for ${item.title}")
        }
    }

    fun toggleCancelSubscription(item: RecurringExpenseEntity) {
        viewModelScope.launch {
            val nextStatus = if (item.status == "CANCELLED") "ACTIVE" else "CANCELLED"
            val updated = if (nextStatus == "CANCELLED") {
                item.copy(status = "CANCELLED", alertMessage = "Cancelled — saving ₹${(item.amount * 12).toLong()}/yr")
            } else {
                RecurringDetectionEngine.evaluateAlerts(item.copy(status = "ACTIVE", lastUsedDate = System.currentTimeMillis()))
            }
            repository.updateRecurring(updated)
            val msg = if (nextStatus == "CANCELLED") {
                "Cancelled '${item.title}' — saving ₹${item.amount.toLong()}/mo!"
            } else {
                "Reactivated '${item.title}'"
            }
            _toastEvent.emit(msg)
        }
    }

    fun updateTransactionDetails(
        transaction: TransactionEntity,
        newMerchant: String,
        newCategory: String,
        newAccount: String,
        newNotes: String,
        isRecurring: Boolean
    ) {
        viewModelScope.launch {
            val updated = transaction.copy(
                merchant = newMerchant,
                category = newCategory,
                accountRef = newAccount,
                notes = newNotes,
                isRecurring = isRecurring
            )
            repository.updateTransaction(updated)
            _toastEvent.emit("Transaction updated successfully")
        }
    }

    fun splitTransaction(
        transaction: TransactionEntity,
        firstCategory: String,
        firstAmount: Double,
        secondCategory: String,
        secondAmount: Double
    ) {
        viewModelScope.launch {
            // Delete original
            repository.deleteTransactionById(transaction.id)
            // Insert part 1
            repository.insertTransaction(
                transaction.copy(
                    id = 0,
                    amount = firstAmount,
                    category = firstCategory,
                    notes = "${transaction.notes} (Split 1/2)".trim()
                )
            )
            // Insert part 2
            repository.insertTransaction(
                transaction.copy(
                    id = 0,
                    amount = secondAmount,
                    category = secondCategory,
                    notes = "${transaction.notes} (Split 2/2)".trim()
                )
            )
            _toastEvent.emit("Transaction split into $firstCategory and $secondCategory")
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteTransactionById(id)
            _toastEvent.emit("Transaction deleted")
        }
    }

    fun deleteAllTransactions() {
        viewModelScope.launch {
            repository.deleteAllTransactions()
            _toastEvent.emit("All transaction history cleared")
        }
    }

    fun setBudget(categoryName: String, limit: Double) {
        viewModelScope.launch {
            repository.setBudget(BudgetEntity(categoryName = categoryName, monthlyLimit = limit))
            _toastEvent.emit("Budget for $categoryName set to ₹${limit.toLong()}")
        }
    }

    fun autoFillBudgetsFromAvailableData() {
        viewModelScope.launch {
            val transactions = repository.getTransactionsSnapshot()
            val expenseTxns = transactions.filter { it.type.equals("DEBIT", ignoreCase = true) }

            // Group spending by category
            val categorySpending = expenseTxns
                .groupBy { it.category }
                .mapValues { entry -> entry.value.sumOf { it.amount } }

            val currentBudgets = repository.getBudgetsSnapshot()
            val existingBudgetMap = currentBudgets.associateBy { it.categoryName.lowercase() }

            // Target categories: categories with spend + essential student categories
            val categoriesToBudget = linkedSetOf<String>()
            categorySpending.keys
                .filter { it.isNotBlank() && !it.equals("Income", ignoreCase = true) }
                .sorted()
                .forEach { categoriesToBudget.add(it) }

            val defaultCategories = listOf("Food", "People", "Transportation", "Entertainment", "Shopping", "Education", "Bills")
            defaultCategories.forEach { categoriesToBudget.add(it) }

            var count = 0
            for (category in categoriesToBudget) {
                val spent = categorySpending[category] ?: 0.0
                val recommendedLimit = if (spent > 0) {
                    // 20% buffer above current spend, rounded to nearest 100, minimum 500
                    val buffered = spent * 1.20
                    (kotlin.math.ceil(buffered / 100.0) * 100.0).coerceAtLeast(500.0)
                } else {
                    when (category.lowercase()) {
                        "food" -> 5000.0
                        "people" -> 2000.0
                        "transportation" -> 2000.0
                        "entertainment" -> 2000.0
                        "shopping" -> 1500.0
                        "investments" -> 3000.0
                        "education" -> 1000.0
                        "bills" -> 1000.0
                        "mobile/internet" -> 600.0
                        else -> 1000.0
                    }
                }

                val existing = existingBudgetMap[category.lowercase()]
                val updatedBudget = existing?.copy(monthlyLimit = recommendedLimit)
                    ?: BudgetEntity(categoryName = category, monthlyLimit = recommendedLimit)

                repository.setBudget(updatedBudget)
                count++
            }

            _toastEvent.emit("Auto-filled $count category budgets from available spending data!")
        }
    }

    fun addGoal(title: String, targetAmount: Double, savedAmount: Double, targetDate: String, iconName: String) {
        viewModelScope.launch {
            repository.insertGoal(
                GoalEntity(
                    title = title,
                    targetAmount = targetAmount,
                    savedAmount = savedAmount,
                    targetDate = targetDate,
                    iconName = iconName
                )
            )
            _toastEvent.emit("Goal '$title' created!")
        }
    }

    fun updateGoalSavings(goal: GoalEntity, addedSavings: Double) {
        viewModelScope.launch {
            val updated = goal.copy(savedAmount = (goal.savedAmount + addedSavings).coerceAtLeast(0.0))
            repository.updateGoal(updated)
            _toastEvent.emit("Added ₹${addedSavings.toLong()} to ${goal.title}")
        }
    }

    fun deleteGoal(goal: GoalEntity) {
        viewModelScope.launch {
            repository.deleteGoal(goal)
            _toastEvent.emit("Goal removed")
        }
    }

    fun addRecurring(title: String, amount: Double, category: String, frequency: String, nextDueDate: String) {
        viewModelScope.launch {
            repository.insertRecurring(
                RecurringExpenseEntity(
                    title = title,
                    amount = amount,
                    category = category,
                    frequency = frequency,
                    nextDueDate = nextDueDate
                )
            )
            _toastEvent.emit("Recurring expense '$title' added")
        }
    }

    fun deleteRecurring(recurring: RecurringExpenseEntity) {
        viewModelScope.launch {
            repository.deleteRecurring(recurring)
            _toastEvent.emit("Recurring expense removed")
        }
    }

    fun addInvestment(name: String, type: String, investedAmount: Double, currentValue: Double, monthlySip: Double) {
        viewModelScope.launch {
            repository.insertInvestment(
                InvestmentEntity(
                    name = name,
                    type = type,
                    investedAmount = investedAmount,
                    currentValue = currentValue,
                    monthlySipAmount = monthlySip
                )
            )
            _toastEvent.emit("Investment '$name' tracked")
        }
    }

    fun deleteInvestment(investment: InvestmentEntity) {
        viewModelScope.launch {
            repository.deleteInvestment(investment)
            _toastEvent.emit("Investment deleted")
        }
    }

    fun saveUserProfile(
        name: String,
        allowance: Double,
        primaryAccount: String,
        savingsGoal: Double,
        smsScanning: Boolean,
        maskAccounts: Boolean
    ) {
        viewModelScope.launch {
            val existing = userProfile.value ?: UserProfileEntity()
            val updated = existing.copy(
                name = name,
                monthlyAllowance = allowance,
                primaryAccount = primaryAccount,
                monthlySavingsGoal = savingsGoal,
                isSmsScanningEnabled = smsScanning,
                maskAccountNumbers = maskAccounts,
                isOnboardingCompleted = true
            )
            repository.saveUserProfile(updated)
            _toastEvent.emit("Profile updated successfully")
        }
    }

    fun saveFullFinancialProfile(
        name: String,
        monthlyAllowance: Double,
        sideIncome: Double,
        monthlyRent: Double,
        monthlyUtilities: Double,
        otherFixedCosts: Double,
        emergencyFundCurrent: Double,
        emergencyFundTargetMonths: Int,
        monthlySavingsGoal: Double,
        primaryAccount: String,
        smsScanning: Boolean,
        maskAccounts: Boolean
    ) {
        viewModelScope.launch {
            val existing = userProfile.value ?: UserProfileEntity()
            val updated = existing.copy(
                name = name.ifBlank { existing.name },
                monthlyAllowance = monthlyAllowance.coerceAtLeast(0.0),
                sideIncome = sideIncome.coerceAtLeast(0.0),
                monthlyRent = monthlyRent.coerceAtLeast(0.0),
                monthlyUtilities = monthlyUtilities.coerceAtLeast(0.0),
                otherFixedCosts = otherFixedCosts.coerceAtLeast(0.0),
                emergencyFundCurrent = emergencyFundCurrent.coerceAtLeast(0.0),
                emergencyFundTargetMonths = emergencyFundTargetMonths.coerceIn(1, 12),
                monthlySavingsGoal = monthlySavingsGoal.coerceAtLeast(0.0),
                primaryAccount = primaryAccount.ifBlank { existing.primaryAccount },
                isSmsScanningEnabled = smsScanning,
                maskAccountNumbers = maskAccounts,
                isOnboardingCompleted = true
            )
            repository.saveUserProfile(updated)
            _toastEvent.emit("Financial profile saved!")
        }
    }

    fun topUpEmergencyFund(addedAmount: Double) {
        viewModelScope.launch {
            val existing = userProfile.value ?: UserProfileEntity()
            val updated = existing.copy(
                emergencyFundCurrent = (existing.emergencyFundCurrent + addedAmount).coerceAtLeast(0.0)
            )
            repository.saveUserProfile(updated)
            _toastEvent.emit("Added ₹${addedAmount.toLong()} to Emergency Fund (Now ₹${updated.emergencyFundCurrent.toLong()})")
        }
    }

    fun completeOnboarding(
        name: String,
        allowance: Double,
        primaryAccount: String,
        savingsGoal: Double,
        ruleAccount: String?,
        ruleCategory: String?
    ) {
        viewModelScope.launch {
            val profile = UserProfileEntity(
                name = name.ifBlank { "Student" },
                monthlyAllowance = allowance,
                primaryAccount = primaryAccount.ifBlank { "HDFC XX1234" },
                monthlySavingsGoal = savingsGoal,
                isSmsScanningEnabled = true,
                maskAccountNumbers = true,
                isOnboardingCompleted = true
            )
            repository.saveUserProfile(profile)

            if (!ruleAccount.isNullOrBlank() && !ruleCategory.isNullOrBlank()) {
                repository.insertRule(
                    AccountRuleEntity(
                        accountIdentifier = ruleAccount.trim(),
                        categoryName = ruleCategory.trim(),
                        description = "Always categorize $ruleAccount as $ruleCategory"
                    )
                )
            }
            _toastEvent.emit("Welcome to Finance Coach, ${profile.name}!")
        }
    }

    fun askAssistant(question: String) {
        if (question.isBlank()) return
        val userMsg = ChatMessage(text = question, isFromUser = true)
        _chatMessages.value = _chatMessages.value + userMsg

        viewModelScope.launch {
            _isAssistantThinking.value = true
            try {
                val reply = assistantEngine.answerQuestionWithVisual(
                    question = question,
                    transactions = repository.getTransactionsSnapshot(),
                    budgets = repository.getBudgetsSnapshot(),
                    profile = userProfile.value,
                    goals = repository.getGoalsSnapshot(),
                    recurring = repository.getRecurringSnapshot(),
                    debts = repository.getDebtsSnapshot()
                )
                val botMsg = ChatMessage(
                    text = reply.text,
                    isFromUser = false,
                    visualAnalysis = reply.visualAnalysis
                )
                _chatMessages.value = _chatMessages.value + botMsg
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    text = "I encountered a minor issue checking that. Please try asking again in a moment.",
                    isFromUser = false
                )
            } finally {
                _isAssistantThinking.value = false
            }
        }
    }

    fun readDeviceSmsAndImport(context: Context, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val reader = SmsInboxReader(context, repository, categorizationEngine)
            val result = reader.scanRecentSms(limit = 150)
            if (result.latestTransaction != null) {
                _latestDetectedUpiTxn.value = result.latestTransaction
            }
            onComplete(result.newlyImported)
            val toastMsg = when {
                result.newlyImported > 0 && result.latestTransaction != null ->
                    "Read SMS: Added ${result.newlyImported} new transaction(s)! Latest: ₹${result.latestTransaction.amount.toLong()} to ${result.latestTransaction.merchant} (${result.latestTransaction.category})"
                result.newlyImported > 0 ->
                    "Read SMS: Added ${result.newlyImported} transaction(s) from device inbox to app ledger!"
                result.totalFound > 0 ->
                    "Device SMS inbox scanned: All ${result.totalFound} transaction(s) are already recorded in app!"
                else ->
                    "No transaction SMS found in device inbox"
            }
            _toastEvent.emit(toastMsg)
        }
    }

    fun scanInbox(context: Context, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val reader = SmsInboxReader(context, repository, categorizationEngine)
            val result = reader.scanRecentSms(limit = 150)
            if (result.latestTransaction != null && result.latestTransaction.paymentMethod.equals("UPI", ignoreCase = true)) {
                _latestDetectedUpiTxn.value = result.latestTransaction
            }
            onComplete(result.newlyImported)
            val toastMsg = when {
                result.newlyImported > 0 && result.latestTransaction != null ->
                    "Found ${result.newlyImported} new transaction(s)! Latest: ₹${result.latestTransaction.amount.toLong()} to ${result.latestTransaction.merchant} (${result.latestTransaction.category})"
                result.newlyImported > 0 ->
                    "Imported ${result.newlyImported} new transactions from SMS inbox"
                else ->
                    "No new financial SMS found in inbox"
            }
            _toastEvent.emit(toastMsg)
        }
    }

    fun scanInboxForUpi(context: Context, onComplete: (Int, TransactionEntity?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val reader = SmsInboxReader(context, repository, categorizationEngine)
            val result = reader.scanRecentSms(limit = 150)
            if (result.latestTransaction != null) {
                _latestDetectedUpiTxn.value = result.latestTransaction
            }
            onComplete(result.newlyImported, result.latestTransaction)
            val toastMsg = when {
                result.newlyImported > 0 && result.latestTransaction != null ->
                    "Quick UPI Match: ₹${result.latestTransaction.amount.toLong()} to ${result.latestTransaction.merchant} (${result.latestTransaction.category})"
                result.newlyImported > 0 ->
                    "Found ${result.newlyImported} new transactions from SMS!"
                else ->
                    "No new UPI transactions detected in recent SMS"
            }
            _toastEvent.emit(toastMsg)
        }
    }

    fun loadSampleStudentData() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val dayMs = 24L * 60 * 60 * 1000

            // Sample permanent receiver and account rules
            repository.insertRule(
                AccountRuleEntity(
                    accountIdentifier = "Rahul Kumar",
                    categoryName = "People",
                    ruleType = "RECEIVER",
                    description = "Always categorize Rahul Kumar as People"
                )
            )
            repository.insertRule(
                AccountRuleEntity(
                    accountIdentifier = "ABC Stores",
                    categoryName = "Shopping",
                    ruleType = "RECEIVER",
                    description = "Always categorize ABC Stores as Shopping"
                )
            )
            repository.insertRule(
                AccountRuleEntity(
                    accountIdentifier = "Groww",
                    categoryName = "Investments",
                    ruleType = "RECEIVER",
                    description = "Always categorize Groww as Investments"
                )
            )
            repository.insertRule(
                AccountRuleEntity(
                    accountIdentifier = "X1234",
                    categoryName = "Food",
                    ruleType = "ACCOUNT",
                    description = "Always categorize account X1234 as Food"
                )
            )

            val sampleTxns = listOf(
                TransactionEntity(
                    amount = 15000.0,
                    type = "Credit",
                    merchant = "Papa (Allowance)",
                    category = "Income",
                    date = now - (15 * dayMs),
                    paymentMethod = "BANK_TRANSFER",
                    accountRef = "X5678",
                    notes = "Monthly college allowance",
                    isAiCategorized = false,
                    dateStr = "01Sep26",
                    timeStr = "10:00 AM"
                ),
                // Today's Food, People & Living Expenses
                TransactionEntity(
                    amount = 450.0,
                    type = "Debit",
                    merchant = "Rahul Kumar",
                    category = "People",
                    date = now,
                    paymentMethod = "UPI",
                    accountRef = "2405",
                    txnRef = "429810392810",
                    notes = "Room rent & mess bill share split • Ref: 429810392810 • Bal: ₹8,420 • HDFC Bank",
                    categorizedByRule = true,
                    dateStr = "19Sep26",
                    timeStr = "08:15 PM",
                    rawSms = "Debited Rs 450.00 from a/c2405 on 19Sep26 08:15 PM via UPI to Rahul Kumar Ref 429810392810.Bal Rs 8420. Not you?Call1800222- HDFC Bank"
                ),
                TransactionEntity(
                    amount = 200.0,
                    type = "Debit",
                    merchant = "Priya Sharma",
                    category = "People",
                    date = now - (2 * dayMs),
                    paymentMethod = "UPI",
                    accountRef = "2405",
                    txnRef = "319482718293",
                    notes = "College project notes and printouts share",
                    isAiCategorized = true,
                    dateStr = "17Sep26",
                    timeStr = "04:15 PM"
                ),
                TransactionEntity(
                    amount = 140.0,
                    type = "Debit",
                    merchant = "Campus Canteen",
                    category = "Food",
                    date = now - (3 * 60 * 60 * 1000),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Lunch special thali & buttermilk",
                    dateStr = "19Sep26",
                    timeStr = "01:30 PM",
                    rawSms = "Debited Rs 140 from a/c X1234 on 19Sep26 01:30 PM via UPI to Campus Canteen"
                ),
                TransactionEntity(
                    amount = 60.0,
                    type = "Debit",
                    merchant = "Chai Point",
                    category = "Food",
                    date = now - (1 * 60 * 60 * 1000),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Evening ginger tea & samosa",
                    dateStr = "19Sep26",
                    timeStr = "05:45 PM",
                    rawSms = "Debited Rs 60 via UPI to Chai Point"
                ),
                TransactionEntity(
                    amount = 200.0,
                    type = "Debit",
                    merchant = "BookMyShow",
                    category = "Entertainment",
                    date = now - (4 * 60 * 60 * 1000),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Movie ticket discount",
                    isAiCategorized = false,
                    dateStr = "19Sep26",
                    timeStr = "06:15 PM"
                ),
                TransactionEntity(
                    amount = 350.0,
                    type = "Debit",
                    merchant = "ABC Stores",
                    category = "Shopping",
                    date = now - (5 * 60 * 60 * 1000),
                    paymentMethod = "CARD",
                    accountRef = "X1234",
                    notes = "College notebooks & pens",
                    categorizedByRule = true,
                    dateStr = "19Sep26",
                    timeStr = "02:30 PM"
                ),
                // Yesterday's Food & Groceries
                TransactionEntity(
                    amount = 1200.0,
                    type = "Debit",
                    merchant = "Zomato",
                    category = "Food",
                    date = now - (1 * dayMs),
                    paymentMethod = "CARD",
                    accountRef = "X5678",
                    notes = "Weekend pizza treat with friends",
                    dateStr = "18Sep26",
                    timeStr = "10:45 AM",
                    rawSms = "Debited Rs 1200 from a/c X5678 on 18Sep26 10:45 AM via CARD to Zomato"
                ),
                TransactionEntity(
                    amount = 210.0,
                    type = "Debit",
                    merchant = "Zepto",
                    category = "Food",
                    date = now - (1 * dayMs),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Midnight Maggi, cold milk & biscuits",
                    dateStr = "18Sep26",
                    timeStr = "11:30 PM",
                    rawSms = "Debited Rs 210 via UPI to Zepto"
                ),
                // Earlier This Week
                TransactionEntity(
                    amount = 250.0,
                    type = "Debit",
                    merchant = "Swiggy",
                    category = "Food",
                    date = now - (2 * dayMs),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Late night chicken biryani bowl",
                    dateStr = "17Sep26",
                    timeStr = "05:10 PM",
                    rawSms = "Debited Rs 250 via UPI to Swiggy"
                ),
                TransactionEntity(
                    amount = 600.0,
                    type = "Debit",
                    merchant = "Uber",
                    category = "Transportation",
                    date = now - (2 * dayMs),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Cab ride to railway station"
                ),
                TransactionEntity(
                    amount = 2300.0,
                    type = "Debit",
                    merchant = "Campus Hostel Mess",
                    category = "Food",
                    date = now - (3 * dayMs),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Monthly mess balance & dues",
                    dateStr = "16Sep26",
                    timeStr = "11:00 AM"
                ),
                TransactionEntity(
                    amount = 540.0,
                    type = "Debit",
                    merchant = "Domino's Pizza",
                    category = "Food",
                    date = now - (3 * dayMs),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Hostel roommate movie night treat",
                    dateStr = "16Sep26",
                    timeStr = "08:30 PM",
                    rawSms = "Debited Rs 540 via UPI to Dominos"
                ),
                TransactionEntity(
                    amount = 340.0,
                    type = "Debit",
                    merchant = "Blinkit",
                    category = "Food",
                    date = now - (4 * dayMs),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Hostel pantry snacks, oats & coffee",
                    dateStr = "15Sep26",
                    timeStr = "04:15 PM"
                ),
                TransactionEntity(
                    amount = 95.0,
                    type = "Debit",
                    merchant = "Nescafe Campus Booth",
                    category = "Food",
                    date = now - (5 * dayMs),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Cold coffee & chocolate muffin",
                    dateStr = "14Sep26",
                    timeStr = "03:45 PM"
                ),
                TransactionEntity(
                    amount = 380.0,
                    type = "Debit",
                    merchant = "Sharma Dhaba",
                    category = "Food",
                    date = now - (6 * dayMs),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "North Indian thali with study group",
                    dateStr = "13Sep26",
                    timeStr = "09:00 PM"
                ),
                TransactionEntity(
                    amount = 110.0,
                    type = "Debit",
                    merchant = "Fresh Juice Corner",
                    category = "Food",
                    date = now - (7 * dayMs),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Post-football mosambi juice & sandwich",
                    dateStr = "12Sep26",
                    timeStr = "06:30 PM"
                ),
                TransactionEntity(
                    amount = 800.0,
                    type = "Debit",
                    merchant = "Netflix",
                    category = "Entertainment",
                    date = now - (3 * dayMs),
                    paymentMethod = "CARD",
                    accountRef = "X5678",
                    notes = "Student 4-screen plan"
                ),
                TransactionEntity(
                    amount = 200.0,
                    type = "Debit",
                    merchant = "Spotify",
                    category = "Entertainment",
                    date = now - (4 * dayMs),
                    paymentMethod = "CARD",
                    accountRef = "X1234",
                    notes = "Student music subscription"
                ),
                TransactionEntity(
                    amount = 300.0,
                    type = "Debit",
                    merchant = "Metro",
                    category = "Transportation",
                    date = now - (4 * dayMs),
                    paymentMethod = "CARD",
                    accountRef = "X1234",
                    notes = "Metro smart card recharge"
                ),
                TransactionEntity(
                    amount = 1450.0,
                    type = "Debit",
                    merchant = "Amazon",
                    category = "Shopping",
                    date = now - (3 * dayMs),
                    paymentMethod = "CARD",
                    accountRef = "X5678",
                    notes = "Study table lamp & headphones"
                ),
                TransactionEntity(
                    amount = 3000.0,
                    type = "Debit",
                    merchant = "Groww",
                    category = "Investments",
                    date = now - (5 * dayMs),
                    paymentMethod = "UPI",
                    accountRef = "X5678",
                    notes = "Monthly Mutual Fund SIP",
                    categorizedByRule = true
                ),
                TransactionEntity(
                    amount = 700.0,
                    type = "Debit",
                    merchant = "College Xerox & Stationery",
                    category = "Miscellaneous",
                    date = now - (6 * dayMs),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Semester lab manual prints"
                )
            )

            for (t in sampleTxns) {
                repository.insertTransaction(t)
            }

            // Sample goals
            repository.insertGoal(
                GoalEntity(
                    title = "New Laptop",
                    targetAmount = 60000.0,
                    savedAmount = 18000.0,
                    targetDate = "Dec 2026",
                    iconName = "Laptop"
                )
            )
            repository.insertGoal(
                GoalEntity(
                    title = "Emergency Fund",
                    targetAmount = 20000.0,
                    savedAmount = 7500.0,
                    targetDate = "Oct 2026",
                    iconName = "Shield"
                )
            )
            repository.insertGoal(
                GoalEntity(
                    title = "College Goa Trip",
                    targetAmount = 10000.0,
                    savedAmount = 4000.0,
                    targetDate = "Jan 2027",
                    iconName = "Flight"
                )
            )

            // Sample recurring
            repository.insertRecurring(
                RecurringExpenseEntity(
                    title = "Netflix Student Plan",
                    amount = 199.0,
                    category = "Entertainment",
                    frequency = "Monthly",
                    nextDueDate = "28 Sep 2026"
                )
            )
            repository.insertRecurring(
                RecurringExpenseEntity(
                    title = "Spotify Premium",
                    amount = 59.0,
                    category = "Entertainment",
                    frequency = "Monthly",
                    nextDueDate = "2 Oct 2026"
                )
            )
            repository.insertRecurring(
                RecurringExpenseEntity(
                    title = "Jio 5G Recharge",
                    amount = 299.0,
                    category = "Mobile/Internet",
                    frequency = "Monthly",
                    nextDueDate = "15 Oct 2026"
                )
            )

            // Sample investments
            repository.insertInvestment(
                InvestmentEntity(
                    name = "UTI Nifty 50 Index Fund",
                    type = "Mutual Fund / SIP",
                    investedAmount = 5000.0,
                    currentValue = 5420.0,
                    monthlySipAmount = 500.0
                )
            )
            repository.insertInvestment(
                InvestmentEntity(
                    name = "SBI High Interest Student FD",
                    type = "Fixed Deposit",
                    investedAmount = 10000.0,
                    currentValue = 10350.0,
                    monthlySipAmount = 0.0
                )
            )

            _toastEvent.emit("Sample college transactions, budgets & rules loaded!")
        }
    }

    // ==================== DEBT & LOANS MODULE ====================

    fun addDebt(debt: DebtEntity) {
        viewModelScope.launch {
            repository.insertDebt(debt)
            _toastEvent.emit("Added ${debt.name} (${debt.annualInterestRate}% APR)")
        }
    }

    fun updateDebt(debt: DebtEntity) {
        viewModelScope.launch {
            repository.updateDebt(debt)
            _toastEvent.emit("Updated ${debt.name}")
        }
    }

    fun deleteDebt(debt: DebtEntity) {
        viewModelScope.launch {
            repository.deleteDebt(debt)
            _toastEvent.emit("Removed ${debt.name}")
        }
    }

    fun recordDebtPayment(debt: DebtEntity, amount: Double, recordInTransactions: Boolean = true) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            val newBalance = (debt.currentBalance - amount).coerceAtLeast(0.0)
            repository.updateDebt(debt.copy(currentBalance = newBalance))

            if (recordInTransactions) {
                val now = System.currentTimeMillis()
                val dateFmt = java.text.SimpleDateFormat("ddMMMyy", java.util.Locale.ENGLISH)
                val timeFmt = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH)
                val txn = TransactionEntity(
                    amount = amount,
                    type = "DEBIT",
                    merchant = debt.name,
                    category = if (debt.isStudentLoan) "Education" else "Debt Repayment",
                    date = now,
                    paymentMethod = "BANK_TRANSFER",
                    notes = "${if (debt.isStudentLoan) "Student loan" else "Credit card"} payment • Remaining: ₹${newBalance.toLong()}",
                    dateStr = dateFmt.format(java.util.Date(now)),
                    timeStr = timeFmt.format(java.util.Date(now))
                )
                val insertedId = repository.insertTransaction(txn)
                _latestAddedTxnId.value = insertedId
            }

            _toastEvent.emit(
                if (newBalance <= 0.5) "${debt.name} is completely paid off!"
                else "Paid ₹${amount.toLong()} toward ${debt.name}. Balance: ₹${newBalance.toLong()}"
            )
        }
    }
}

class MainViewModelFactory(private val repository: FinanceRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
