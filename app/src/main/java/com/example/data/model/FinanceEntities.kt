package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val type: String, // "DEBIT" or "CREDIT" (case-insensitive handling across app)
    val merchant: String, // Receiver or source name
    val category: String,
    val date: Long,
    val paymentMethod: String = "UPI", // "UPI", "CARD", "BANK_TRANSFER", "CASH"
    val accountRef: String = "", // e.g. "X1234"
    val upiId: String = "",
    val txnRef: String = "",
    val notes: String = "",
    val isRecurring: Boolean = false,
    val isAiCategorized: Boolean = false,
    val categorizedByRule: Boolean = false,
    val rawSms: String? = null,
    val dateStr: String = "", // e.g. "19Sep26"
    val timeStr: String = ""  // e.g. "08:15 PM"
) {
    val receiver: String get() = merchant
    val mode: String get() = paymentMethod
    val account: String get() = accountRef
    val isDebit: Boolean get() = type.equals("DEBIT", ignoreCase = true)
    val isCredit: Boolean get() = type.equals("CREDIT", ignoreCase = true)
}

@Entity(tableName = "account_rules")
data class AccountRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountIdentifier: String, // e.g. "Rahul Kumar", "ABC Stores", "Groww", "X1234"
    val categoryName: String,
    val description: String = "",
    val isEnabled: Boolean = true,
    val ruleType: String = "RECEIVER", // "RECEIVER" or "ACCOUNT"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey
    val categoryName: String,
    val monthlyLimit: Double,
    val monthYear: String = "" // e.g. "09-2026"
)

@Entity(tableName = "financial_goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val targetAmount: Double,
    val savedAmount: Double,
    val targetDate: String = "",
    val iconName: String = "Savings"
) {
    val remainingAmount: Double get() = (targetAmount - savedAmount).coerceAtLeast(0.0)

    fun monthsUntilTarget(nowMillis: Long = System.currentTimeMillis()): Int {
        if (targetDate.isBlank()) return 12
        val formats = listOf("MMM yyyy", "MMMM yyyy", "yyyy-MM", "MM/yyyy", "dd MMM yyyy", "dd/MM/yyyy")
        val targetCal = Calendar.getInstance()
        var parsed = false
        for (pattern in formats) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.ENGLISH)
                fmt.isLenient = false
                val date = fmt.parse(targetDate.trim())
                if (date != null) {
                    targetCal.time = date
                    parsed = true
                    break
                }
            } catch (_: Exception) {
            }
        }
        if (!parsed) return 6
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val yearDiff = targetCal.get(Calendar.YEAR) - nowCal.get(Calendar.YEAR)
        val monthDiff = targetCal.get(Calendar.MONTH) - nowCal.get(Calendar.MONTH)
        return max(1, yearDiff * 12 + monthDiff)
    }

    fun requiredMonthlyContribution(nowMillis: Long = System.currentTimeMillis()): Double {
        val months = monthsUntilTarget(nowMillis)
        return remainingAmount / months.toDouble()
    }
}

@Entity(tableName = "recurring_expenses")
data class RecurringExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: String,
    val frequency: String = "Monthly",
    val nextDueDate: String = "",
    val isConfirmed: Boolean = true,
    val merchantNormalized: String = "",
    val previousAmount: Double? = null,
    val lastChargedDate: Long = 0L,
    val lastUsedDate: Long = 0L,
    val status: String = "ACTIVE", // "ACTIVE", "PRICE_CHANGED", "UNUSED", "CANCELLED"
    val alertMessage: String = "",
    val isAutoDetected: Boolean = false
) {
    val hasAlert: Boolean get() = status == "PRICE_CHANGED" || status == "UNUSED"
    val priceDelta: Double get() = if (previousAmount != null) amount - previousAmount else 0.0
}

@Entity(tableName = "investments")
data class InvestmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String, // "Mutual Fund / SIP", "Stocks", "Fixed Deposit", "Gold", "Other"
    val investedAmount: Double,
    val currentValue: Double,
    val monthlySipAmount: Double = 0.0,
    val date: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val id: Int = 1,
    val name: String = "Student",
    val monthlyAllowance: Double = 12000.0,
    val sideIncome: Double = 3000.0,
    val monthlyRent: Double = 4500.0,
    val monthlyUtilities: Double = 1200.0,
    val otherFixedCosts: Double = 800.0,
    val emergencyFundCurrent: Double = 14000.0,
    val emergencyFundTargetMonths: Int = 3,
    val primaryAccount: String = "HDFC XX1234",
    val monthlySavingsGoal: Double = 3000.0,
    val isSmsScanningEnabled: Boolean = true,
    val maskAccountNumbers: Boolean = true,
    val isOnboardingCompleted: Boolean = false
) {
    val totalMonthlyIncome: Double get() = (monthlyAllowance + sideIncome).coerceAtLeast(0.0)
    val totalFixedCosts: Double get() = (monthlyRent + monthlyUtilities + otherFixedCosts).coerceAtLeast(0.0)
    val monthlyEssentialBaseline: Double get() = (totalFixedCosts + 2500.0).coerceAtLeast(4000.0)
    val emergencyFundTargetAmount: Double get() = monthlyEssentialBaseline * emergencyFundTargetMonths.coerceIn(1, 12)
    val emergencyFundMonthsCovered: Double
        get() = if (monthlyEssentialBaseline > 0) emergencyFundCurrent / monthlyEssentialBaseline else 0.0
    val disposableIncome: Double get() = (totalMonthlyIncome - totalFixedCosts).coerceAtLeast(0.0)
}

@Entity(tableName = "learned_mappings")
data class LearnedMappingEntity(
    @PrimaryKey
    val merchantNormalized: String,
    val categoryName: String,
    val correctionCount: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis(),
    val originalCategory: String = "",
    val sampleMerchant: String = ""
)

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val debtType: String = "STUDENT_LOAN", // "STUDENT_LOAN" or "CREDIT_CARD"
    val lender: String = "",
    val originalPrincipal: Double, // Original loan amount or Credit Card limit
    val currentBalance: Double,
    val annualInterestRate: Double, // APR percentage e.g. 8.5 or 36.0
    val minimumMonthlyPayment: Double,
    val dueDayOfMonth: Int = 5, // 1..28
    val gracePeriodMonths: Int = 0, // Student loan moratorium/grace period
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val isStudentLoan: Boolean get() = debtType.equals("STUDENT_LOAN", ignoreCase = true)
    val isCreditCard: Boolean get() = debtType.equals("CREDIT_CARD", ignoreCase = true)
    val monthlyInterestRate: Double get() = (annualInterestRate.coerceAtLeast(0.0) / 100.0) / 12.0
    val currentMonthlyInterest: Double get() = currentBalance.coerceAtLeast(0.0) * monthlyInterestRate
    val paidOffAmount: Double get() = (originalPrincipal - currentBalance).coerceAtLeast(0.0)
    val payoffProgressFraction: Float
        get() = if (originalPrincipal > 0.0) {
            (paidOffAmount / originalPrincipal).toFloat().coerceIn(0f, 1f)
        } else 0f
    val creditUtilizationPercent: Int
        get() = if (isCreditCard && originalPrincipal > 0.0) {
            ((currentBalance / originalPrincipal) * 100.0).toInt().coerceIn(0, 999)
        } else 0
}

