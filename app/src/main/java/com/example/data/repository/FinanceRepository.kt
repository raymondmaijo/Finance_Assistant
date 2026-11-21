package com.example.data.repository

import com.example.data.db.AppDatabase
import com.example.data.model.AccountRuleEntity
import com.example.data.model.BudgetEntity
import com.example.data.model.GoalEntity
import com.example.data.model.InvestmentEntity
import com.example.data.model.LearnedMappingEntity
import com.example.data.model.RecurringExpenseEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class FinanceRepository(private val database: AppDatabase) {
    private val transactionDao = database.transactionDao()
    private val accountRuleDao = database.accountRuleDao()
    private val budgetDao = database.budgetDao()
    private val goalDao = database.goalDao()
    private val recurringExpenseDao = database.recurringExpenseDao()
    private val investmentDao = database.investmentDao()
    private val userProfileDao = database.userProfileDao()
    private val learnedMappingDao = database.learnedMappingDao()

    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allRules: Flow<List<AccountRuleEntity>> = accountRuleDao.getAllRules()
    val allBudgets: Flow<List<BudgetEntity>> = budgetDao.getAllBudgets()
    val allGoals: Flow<List<GoalEntity>> = goalDao.getAllGoals()
    val allRecurring: Flow<List<RecurringExpenseEntity>> = recurringExpenseDao.getAllRecurring()
    val allInvestments: Flow<List<InvestmentEntity>> = investmentDao.getAllInvestments()
    val userProfile: Flow<UserProfileEntity?> = userProfileDao.getUserProfile()
    val allLearnedMappings: Flow<List<LearnedMappingEntity>> = learnedMappingDao.observeAllLearnedMappings()

    suspend fun getTransactionsSnapshot(): List<TransactionEntity> =
        transactionDao.getAllTransactionsSnapshot()

    suspend fun getActiveRulesSnapshot(): List<AccountRuleEntity> =
        accountRuleDao.getActiveRulesSnapshot()

    suspend fun getBudgetsSnapshot(): List<BudgetEntity> =
        budgetDao.getAllBudgetsSnapshot()

    suspend fun getGoalsSnapshot(): List<GoalEntity> =
        goalDao.getAllGoalsSnapshot()

    suspend fun getRecurringSnapshot(): List<RecurringExpenseEntity> =
        recurringExpenseDao.getAllRecurringSnapshot()

    suspend fun getUserProfileSnapshot(): UserProfileEntity? =
        userProfileDao.getUserProfileSnapshot()

    suspend fun getAllLearnedMappingsSnapshot(): List<LearnedMappingEntity> =
        learnedMappingDao.getAllLearnedMappings()

    suspend fun insertTransaction(transaction: TransactionEntity): Long =
        transactionDao.insertTransaction(transaction)

    suspend fun insertTransactions(transactions: List<TransactionEntity>) =
        transactionDao.insertAll(transactions)

    suspend fun updateTransaction(transaction: TransactionEntity) =
        transactionDao.updateTransaction(transaction)

    suspend fun deleteTransactionById(id: Long) =
        transactionDao.deleteById(id)

    suspend fun deleteAllTransactions() =
        transactionDao.deleteAll()

    suspend fun insertRule(rule: AccountRuleEntity): Long =
        accountRuleDao.insertRule(rule)

    suspend fun updateRule(rule: AccountRuleEntity) =
        accountRuleDao.updateRule(rule)

    suspend fun deleteRule(rule: AccountRuleEntity) =
        accountRuleDao.deleteRule(rule)

    suspend fun deleteRuleById(id: Long) =
        accountRuleDao.deleteById(id)

    suspend fun setBudget(budget: BudgetEntity) =
        budgetDao.insertOrUpdateBudget(budget)

    suspend fun insertGoal(goal: GoalEntity): Long =
        goalDao.insertGoal(goal)

    suspend fun updateGoal(goal: GoalEntity) =
        goalDao.updateGoal(goal)

    suspend fun deleteGoal(goal: GoalEntity) =
        goalDao.deleteGoal(goal)

    suspend fun insertRecurring(recurring: RecurringExpenseEntity): Long =
        recurringExpenseDao.insertRecurring(recurring)

    suspend fun updateRecurring(recurring: RecurringExpenseEntity) =
        recurringExpenseDao.updateRecurring(recurring)

    suspend fun deleteRecurring(recurring: RecurringExpenseEntity) =
        recurringExpenseDao.deleteRecurring(recurring)

    suspend fun insertInvestment(investment: InvestmentEntity): Long =
        investmentDao.insertInvestment(investment)

    suspend fun deleteInvestment(investment: InvestmentEntity) =
        investmentDao.deleteInvestment(investment)

    suspend fun saveUserProfile(profile: UserProfileEntity) =
        userProfileDao.saveUserProfile(profile)

    suspend fun recordLearnedCategory(
        merchant: String,
        category: String,
        originalCategory: String = ""
    ) {
        val normalized = normalizeMerchantKey(merchant)
        if (normalized.isBlank()) return
        val existing = learnedMappingDao.getMappingForMerchant(normalized)
        if (existing != null) {
            learnedMappingDao.insertMapping(
                existing.copy(
                    categoryName = category,
                    correctionCount = existing.correctionCount + 1,
                    lastUpdated = System.currentTimeMillis(),
                    originalCategory = originalCategory.ifBlank { existing.originalCategory },
                    sampleMerchant = merchant.trim().ifBlank { existing.sampleMerchant }
                )
            )
        } else {
            learnedMappingDao.insertMapping(
                LearnedMappingEntity(
                    merchantNormalized = normalized,
                    categoryName = category,
                    correctionCount = 1,
                    lastUpdated = System.currentTimeMillis(),
                    originalCategory = originalCategory,
                    sampleMerchant = merchant.trim()
                )
            )
        }
    }

    suspend fun deleteLearnedMapping(merchantNormalized: String) {
        learnedMappingDao.deleteByMerchant(merchantNormalized)
    }

    suspend fun getLearnedCategory(merchant: String): String? {
        val normalized = normalizeMerchantKey(merchant)
        if (normalized.isBlank()) return null
        // 1. Exact normalized match
        learnedMappingDao.getMappingForMerchant(normalized)?.let { return it.categoryName }
        // 2. Fuzzy / substring match against all learned patterns (for messy bank narrations)
        val allMappings = learnedMappingDao.getAllLearnedMappings()
        for (mapping in allMappings) {
            val pattern = mapping.merchantNormalized
            if (pattern.length >= 3 && (normalized.contains(pattern) || pattern.contains(normalized))) {
                return mapping.categoryName
            }
        }
        return null
    }

    suspend fun recategorizeSimilarTransactions(merchant: String, newCategory: String): Int {
        val normalizedTarget = normalizeMerchantKey(merchant)
        if (normalizedTarget.isBlank()) return 0
        val allTxns = transactionDao.getAllTransactionsSnapshot()
        var updatedCount = 0
        for (txn in allTxns) {
            val normTxn = normalizeMerchantKey(txn.merchant)
            val matches = normTxn == normalizedTarget ||
                (normalizedTarget.length >= 4 && (normTxn.contains(normalizedTarget) || normalizedTarget.contains(normTxn)))
            if (matches && !txn.category.equals(newCategory, ignoreCase = true)) {
                transactionDao.updateTransaction(
                    txn.copy(
                        category = newCategory,
                        categorizedByRule = true,
                        isAiCategorized = false
                    )
                )
                updatedCount++
            }
        }
        return updatedCount
    }

    suspend fun initializeDefaultDataIfEmpty() {
        val profile = userProfileDao.getUserProfileSnapshot()
        if (profile == null) {
            userProfileDao.saveUserProfile(
                UserProfileEntity(
                    id = 1,
                    name = "Aarav",
                    monthlyAllowance = 15000.0,
                    sideIncome = 3500.0,
                    monthlyRent = 4500.0,
                    monthlyUtilities = 1200.0,
                    otherFixedCosts = 800.0,
                    emergencyFundCurrent = 14500.0,
                    emergencyFundTargetMonths = 3,
                    primaryAccount = "HDFC XX1234",
                    monthlySavingsGoal = 3500.0,
                    isSmsScanningEnabled = true,
                    maskAccountNumbers = true,
                    isOnboardingCompleted = false
                )
            )
        }

        val budgets = budgetDao.getAllBudgetsSnapshot()
        if (budgets.isEmpty()) {
            budgetDao.insertAll(
                listOf(
                    BudgetEntity(categoryName = "Food", monthlyLimit = 4500.0),
                    BudgetEntity(categoryName = "People", monthlyLimit = 2000.0),
                    BudgetEntity(categoryName = "Transportation", monthlyLimit = 1800.0),
                    BudgetEntity(categoryName = "Entertainment", monthlyLimit = 1500.0),
                    BudgetEntity(categoryName = "Shopping", monthlyLimit = 1500.0),
                    BudgetEntity(categoryName = "Subscriptions", monthlyLimit = 1200.0),
                    BudgetEntity(categoryName = "Education", monthlyLimit = 1200.0)
                )
            )
        }

        val rules = accountRuleDao.getActiveRulesSnapshot()
        if (rules.isEmpty()) {
            accountRuleDao.insertRule(
                AccountRuleEntity(
                    accountIdentifier = "ABC Stores",
                    categoryName = "Shopping",
                    ruleType = "RECEIVER",
                    description = "Always categorize ABC Stores as Shopping"
                )
            )
            accountRuleDao.insertRule(
                AccountRuleEntity(
                    accountIdentifier = "Groww",
                    categoryName = "Investments",
                    ruleType = "RECEIVER",
                    description = "Always categorize Groww as Investments"
                )
            )
        }

        val goals = goalDao.getAllGoalsSnapshot()
        if (goals.isEmpty()) {
            val nextYear = Calendar.getInstance().get(Calendar.YEAR) + 1
            goalDao.insertGoal(
                GoalEntity(
                    title = "New Laptop",
                    targetAmount = 55000.0,
                    savedAmount = 22000.0,
                    targetDate = "Aug $nextYear",
                    iconName = "Laptop"
                )
            )
            goalDao.insertGoal(
                GoalEntity(
                    title = "Emergency Cushion",
                    targetAmount = 27000.0,
                    savedAmount = 14500.0,
                    targetDate = "May $nextYear",
                    iconName = "Shield"
                )
            )
            goalDao.insertGoal(
                GoalEntity(
                    title = "Semester Break Trip",
                    targetAmount = 14000.0,
                    savedAmount = 5500.0,
                    targetDate = "Jun $nextYear",
                    iconName = "Flight"
                )
            )
        }

        val existingTxns = transactionDao.getAllTransactionsSnapshot()
        if (existingTxns.isEmpty()) {
            val now = System.currentTimeMillis()
            val dayMs = 24L * 60 * 60 * 1000
            val fmtDate = SimpleDateFormat("ddMMMyy", Locale.ENGLISH)

            fun d(daysAgo: Int): Long = now - (daysAgo * dayMs)
            fun ds(daysAgo: Int): String = fmtDate.format(Date(d(daysAgo)))

            val initialTxns = listOf(
                // Current Month Income
                TransactionEntity(
                    amount = 15000.0,
                    type = "CREDIT",
                    merchant = "Papa (Allowance)",
                    category = "Income",
                    date = d(12),
                    paymentMethod = "BANK_TRANSFER",
                    accountRef = "X5678",
                    notes = "Monthly college allowance",
                    dateStr = ds(12),
                    timeStr = "10:00 AM"
                ),
                TransactionEntity(
                    amount = 3500.0,
                    type = "CREDIT",
                    merchant = "Campus TA Stipend",
                    category = "Income",
                    date = d(9),
                    paymentMethod = "BANK_TRANSFER",
                    accountRef = "X5678",
                    notes = "Lab assistant stipend",
                    dateStr = ds(9),
                    timeStr = "03:15 PM"
                ),
                // Current Month Expenses (including recurring subscriptions & dining)
                TransactionEntity(
                    amount = 450.0,
                    type = "DEBIT",
                    merchant = "Rahul Kumar",
                    category = "People",
                    date = now,
                    paymentMethod = "UPI",
                    accountRef = "2405",
                    txnRef = "429810392810",
                    notes = "Room rent & dinner split • HDFC Bank",
                    isAiCategorized = true,
                    dateStr = ds(0),
                    timeStr = "08:15 PM"
                ),
                TransactionEntity(
                    amount = 160.0,
                    type = "DEBIT",
                    merchant = "Campus Canteen",
                    category = "Food",
                    date = now - (3 * 60 * 60 * 1000),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Lunch thali & buttermilk",
                    dateStr = ds(0),
                    timeStr = "01:30 PM"
                ),
                TransactionEntity(
                    amount = 85.0,
                    type = "DEBIT",
                    merchant = "Chai Point",
                    category = "Food",
                    date = now - (1 * 60 * 60 * 1000),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Evening ginger tea & samosa",
                    dateStr = ds(0),
                    timeStr = "05:45 PM"
                ),
                TransactionEntity(
                    amount = 1150.0,
                    type = "DEBIT",
                    merchant = "Zomato",
                    category = "Food",
                    date = d(1),
                    paymentMethod = "CARD",
                    accountRef = "X5678",
                    notes = "Weekend pizza & sides with friends",
                    dateStr = ds(1),
                    timeStr = "09:45 PM"
                ),
                TransactionEntity(
                    amount = 420.0,
                    type = "DEBIT",
                    merchant = "Swiggy",
                    category = "Food",
                    date = d(2),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Late night biryani bowl",
                    dateStr = ds(2),
                    timeStr = "10:30 PM"
                ),
                TransactionEntity(
                    amount = 799.0,
                    type = "DEBIT",
                    merchant = "Netflix",
                    category = "Subscriptions",
                    date = d(3),
                    paymentMethod = "CARD",
                    accountRef = "X5678",
                    notes = "Monthly streaming plan (price increased from ₹649)",
                    isRecurring = true,
                    dateStr = ds(3),
                    timeStr = "09:00 AM"
                ),
                TransactionEntity(
                    amount = 119.0,
                    type = "DEBIT",
                    merchant = "Spotify",
                    category = "Subscriptions",
                    date = d(4),
                    paymentMethod = "CARD",
                    accountRef = "X1234",
                    notes = "Student music plan",
                    isRecurring = true,
                    dateStr = ds(4),
                    timeStr = "11:15 AM"
                ),
                TransactionEntity(
                    amount = 299.0,
                    type = "DEBIT",
                    merchant = "CloudBox Pro",
                    category = "Subscriptions",
                    date = d(5),
                    paymentMethod = "CARD",
                    accountRef = "X5678",
                    notes = "Monthly cloud storage auto-debit",
                    isRecurring = true,
                    dateStr = ds(5),
                    timeStr = "08:00 AM"
                ),
                TransactionEntity(
                    amount = 2300.0,
                    type = "DEBIT",
                    merchant = "Campus Hostel Mess",
                    category = "Food",
                    date = d(5),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Monthly mess card reload",
                    isRecurring = true,
                    dateStr = ds(5),
                    timeStr = "11:00 AM"
                ),
                TransactionEntity(
                    amount = 540.0,
                    type = "DEBIT",
                    merchant = "Domino's Pizza",
                    category = "Food",
                    date = d(4),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Hostel movie night",
                    dateStr = ds(4),
                    timeStr = "08:30 PM"
                ),
                TransactionEntity(
                    amount = 600.0,
                    type = "DEBIT",
                    merchant = "Uber",
                    category = "Transportation",
                    date = d(2),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Cab to railway station",
                    dateStr = ds(2),
                    timeStr = "06:20 PM"
                ),
                TransactionEntity(
                    amount = 300.0,
                    type = "DEBIT",
                    merchant = "Metro",
                    category = "Transportation",
                    date = d(6),
                    paymentMethod = "CARD",
                    accountRef = "X1234",
                    notes = "Metro smart card top-up",
                    dateStr = ds(6),
                    timeStr = "09:10 AM"
                ),
                TransactionEntity(
                    amount = 1450.0,
                    type = "DEBIT",
                    merchant = "Amazon",
                    category = "Shopping",
                    date = d(3),
                    paymentMethod = "CARD",
                    accountRef = "X5678",
                    notes = "Desk lamp & USB hub",
                    dateStr = ds(3),
                    timeStr = "02:40 PM"
                ),
                TransactionEntity(
                    amount = 700.0,
                    type = "DEBIT",
                    merchant = "College Xerox & Stationery",
                    category = "Education",
                    date = d(6),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Semester lab manuals",
                    dateStr = ds(6),
                    timeStr = "04:00 PM"
                ),
                // Previous Month Transactions (enables true MoM comparison & recurring detection)
                TransactionEntity(
                    amount = 15000.0,
                    type = "CREDIT",
                    merchant = "Papa (Allowance)",
                    category = "Income",
                    date = d(42),
                    paymentMethod = "BANK_TRANSFER",
                    accountRef = "X5678",
                    notes = "Last month college allowance",
                    dateStr = ds(42),
                    timeStr = "10:00 AM"
                ),
                TransactionEntity(
                    amount = 649.0,
                    type = "DEBIT",
                    merchant = "Netflix",
                    category = "Subscriptions",
                    date = d(33),
                    paymentMethod = "CARD",
                    accountRef = "X5678",
                    notes = "Monthly streaming plan",
                    isRecurring = true,
                    dateStr = ds(33),
                    timeStr = "09:00 AM"
                ),
                TransactionEntity(
                    amount = 119.0,
                    type = "DEBIT",
                    merchant = "Spotify",
                    category = "Subscriptions",
                    date = d(34),
                    paymentMethod = "CARD",
                    accountRef = "X1234",
                    notes = "Student music plan",
                    isRecurring = true,
                    dateStr = ds(34),
                    timeStr = "11:15 AM"
                ),
                TransactionEntity(
                    amount = 299.0,
                    type = "DEBIT",
                    merchant = "CloudBox Pro",
                    category = "Subscriptions",
                    date = d(35),
                    paymentMethod = "CARD",
                    accountRef = "X5678",
                    notes = "Monthly cloud storage auto-debit",
                    isRecurring = true,
                    dateStr = ds(35),
                    timeStr = "08:00 AM"
                ),
                TransactionEntity(
                    amount = 2300.0,
                    type = "DEBIT",
                    merchant = "Campus Hostel Mess",
                    category = "Food",
                    date = d(35),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Monthly mess card reload",
                    isRecurring = true,
                    dateStr = ds(35),
                    timeStr = "11:00 AM"
                ),
                TransactionEntity(
                    amount = 1480.0,
                    type = "DEBIT",
                    merchant = "Zomato",
                    category = "Food",
                    date = d(38),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Last month food deliveries",
                    dateStr = ds(38),
                    timeStr = "08:10 PM"
                ),
                TransactionEntity(
                    amount = 920.0,
                    type = "DEBIT",
                    merchant = "Uber",
                    category = "Transportation",
                    date = d(37),
                    paymentMethod = "UPI",
                    accountRef = "X1234",
                    notes = "Campus & airport rides",
                    dateStr = ds(37),
                    timeStr = "05:40 PM"
                ),
                TransactionEntity(
                    amount = 1100.0,
                    type = "DEBIT",
                    merchant = "Amazon",
                    category = "Shopping",
                    date = d(40),
                    paymentMethod = "CARD",
                    accountRef = "X5678",
                    notes = "Backpack accessories",
                    dateStr = ds(40),
                    timeStr = "01:20 PM"
                )
            )
            transactionDao.insertAll(initialTxns)
        }

        val existingRecurring = recurringExpenseDao.getAllRecurringSnapshot()
        if (existingRecurring.isEmpty()) {
            val now = System.currentTimeMillis()
            val dayMs = 24L * 60 * 60 * 1000
            val nextMonthFmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
            val nextDue = { daysAhead: Int -> nextMonthFmt.format(Date(now + daysAhead * dayMs)) }

            recurringExpenseDao.insertAll(
                listOf(
                    RecurringExpenseEntity(
                        title = "Netflix",
                        amount = 799.0,
                        previousAmount = 649.0,
                        category = "Subscriptions",
                        frequency = "Monthly",
                        nextDueDate = nextDue(27),
                        merchantNormalized = "netflix",
                        lastChargedDate = now - (3 * dayMs),
                        lastUsedDate = now - (2 * dayMs),
                        status = "PRICE_CHANGED",
                        alertMessage = "Price jumped +₹150 (+23%) from ₹649 → ₹799/mo",
                        isAutoDetected = true
                    ),
                    RecurringExpenseEntity(
                        title = "CloudBox Pro",
                        amount = 299.0,
                        previousAmount = 299.0,
                        category = "Subscriptions",
                        frequency = "Monthly",
                        nextDueDate = nextDue(25),
                        merchantNormalized = "cloudbox pro",
                        lastChargedDate = now - (5 * dayMs),
                        lastUsedDate = now - (46 * dayMs),
                        status = "UNUSED",
                        alertMessage = "Unused for 46 days — cancelling saves ₹3,588/yr",
                        isAutoDetected = true
                    ),
                    RecurringExpenseEntity(
                        title = "Spotify",
                        amount = 119.0,
                        previousAmount = 119.0,
                        category = "Subscriptions",
                        frequency = "Monthly",
                        nextDueDate = nextDue(26),
                        merchantNormalized = "spotify",
                        lastChargedDate = now - (4 * dayMs),
                        lastUsedDate = now - (1 * dayMs),
                        status = "ACTIVE",
                        alertMessage = "",
                        isAutoDetected = true
                    ),
                    RecurringExpenseEntity(
                        title = "Campus Hostel Mess",
                        amount = 2300.0,
                        previousAmount = 2300.0,
                        category = "Food",
                        frequency = "Monthly",
                        nextDueDate = nextDue(25),
                        merchantNormalized = "campus hostel mess",
                        lastChargedDate = now - (5 * dayMs),
                        lastUsedDate = now,
                        status = "ACTIVE",
                        alertMessage = "",
                        isAutoDetected = true
                    )
                )
            )
        }
    }

    companion object {
        fun normalizeMerchantKey(raw: String): String {
            return raw.trim()
                .lowercase(Locale.ENGLISH)
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .replace(Regex("\\b(upi|imps|neft|pos|atm|pvt|ltd|limited|india|payments|pay|txn|ref|dr|cr)\\b"), " ")
                .replace(Regex("\\b\\d{3,}\\b"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifBlank { raw.trim().lowercase(Locale.ENGLISH) }
        }
    }
}
