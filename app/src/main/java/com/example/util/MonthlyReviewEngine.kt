package com.example.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.model.BudgetEntity
import com.example.data.model.GoalEntity
import com.example.data.model.RecurringExpenseEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UserProfileEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class BudgetAdherenceRow(
    val category: String,
    val budgeted: Double,
    val actual: Double,
    val variance: Double, // positive = under budget, negative = over budget
    val percentUsed: Int
)

data class MonthlyReviewReport(
    val monthLabel: String,
    val monthOffset: Int,
    val score: Int,
    val grade: String,
    val totalIncome: Double,
    val fixedCosts: Double,
    val variableSpend: Double,
    val totalOutflow: Double,
    val netSavings: Double,
    val savingsRatePercent: Int,
    val previousMonthSpend: Double,
    val monthOverMonthDelta: Double,
    val monthOverMonthDeltaPercent: Int,
    val budgetRows: List<BudgetAdherenceRow>,
    val activeSubscriptionsTotal: Double,
    val priceChangedSubs: List<RecurringExpenseEntity>,
    val unusedSubs: List<RecurringExpenseEntity>,
    val emergencyFundCurrent: Double,
    val emergencyFundTarget: Double,
    val emergencyFundMonthsCovered: Double,
    val goalsSnapshot: List<GoalEntity>,
    val wins: List<String>,
    val watchouts: List<String>,
    val nextMonthFocus: List<String>
)

object MonthlyReviewEngine {

    private val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
    private val fileTsFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH)

    fun buildMonthlyReview(
        monthOffset: Int,
        transactions: List<TransactionEntity>,
        budgets: List<BudgetEntity>,
        profile: UserProfileEntity?,
        recurring: List<RecurringExpenseEntity>,
        goals: List<GoalEntity>
    ): MonthlyReviewReport {
        val targetCal = Calendar.getInstance().apply { add(Calendar.MONTH, monthOffset) }
        val targetMonth = targetCal.get(Calendar.MONTH)
        val targetYear = targetCal.get(Calendar.YEAR)
        val monthLabel = monthFmt.format(targetCal.time)

        val prevCal = (targetCal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
        val prevMonth = prevCal.get(Calendar.MONTH)
        val prevYear = prevCal.get(Calendar.YEAR)

        val currentTxns = transactions.filter { tx ->
            val c = Calendar.getInstance().apply { timeInMillis = tx.date }
            c.get(Calendar.MONTH) == targetMonth && c.get(Calendar.YEAR) == targetYear
        }
        val prevTxns = transactions.filter { tx ->
            val c = Calendar.getInstance().apply { timeInMillis = tx.date }
            c.get(Calendar.MONTH) == prevMonth && c.get(Calendar.YEAR) == prevYear
        }

        val recordedCredits = currentTxns.filter { it.isCredit }.sumOf { it.amount }
        val profileIncome = profile?.totalMonthlyIncome ?: 15000.0
        val totalIncome = if (recordedCredits > 0) maxOf(recordedCredits, profileIncome) else profileIncome

        val fixedCosts = profile?.totalFixedCosts ?: 6500.0
        val currentDebits = currentTxns.filter { it.isDebit }
        val variableSpend = currentDebits.sumOf { it.amount }
        val totalOutflow = fixedCosts + variableSpend
        val netSavings = totalIncome - totalOutflow
        val savingsRate = if (totalIncome > 0) ((netSavings / totalIncome) * 100.0).roundToInt() else 0

        val prevMonthSpend = prevTxns.filter { it.isDebit }.sumOf { it.amount }
        val momDelta = variableSpend - prevMonthSpend
        val momDeltaPct = if (prevMonthSpend > 0) ((momDelta / prevMonthSpend) * 100.0).roundToInt() else 0

        // Budget adherence
        val actualByCat = currentDebits.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        val budgetRows = budgets.map { b ->
            val actual = actualByCat[b.categoryName] ?: 0.0
            val variance = b.monthlyLimit - actual
            val pct = if (b.monthlyLimit > 0) ((actual / b.monthlyLimit) * 100.0).roundToInt() else 0
            BudgetAdherenceRow(
                category = b.categoryName,
                budgeted = b.monthlyLimit,
                actual = actual,
                variance = variance,
                percentUsed = pct
            )
        }.sortedByDescending { it.percentUsed }

        // Recurring audit
        val activeRecurring = recurring.filter { it.status != "CANCELLED" }
        val activeSubsTotal = activeRecurring.sumOf { if (it.frequency == "Weekly") it.amount * 4.0 else it.amount }
        val priceChanged = activeRecurring.filter { it.status == "PRICE_CHANGED" }
        val unused = activeRecurring.filter { it.status == "UNUSED" }

        // Emergency fund
        val efCurrent = profile?.emergencyFundCurrent ?: 14000.0
        val efTarget = profile?.emergencyFundTargetAmount ?: 27000.0
        val efMonths = profile?.emergencyFundMonthsCovered ?: 1.5

        // Financial Scorecard (0..100)
        var score = 50
        score += when {
            savingsRate >= 25 -> 25
            savingsRate >= 15 -> 18
            savingsRate >= 5 -> 10
            savingsRate >= 0 -> 2
            else -> -15
        }
        val overBudgetCount = budgetRows.count { it.percentUsed > 100 }
        score += when (overBudgetCount) {
            0 -> 15
            1 -> 6
            else -> -8
        }
        if (efMonths >= (profile?.emergencyFundTargetMonths ?: 3)) {
            score += 10
        } else if (efMonths >= 1.5) {
            score += 5
        }
        if (unused.isNotEmpty()) score -= 5
        if (priceChanged.isNotEmpty()) score -= 3
        score = score.coerceIn(15, 98)

        val grade = when {
            score >= 85 -> "A"
            score >= 72 -> "B"
            score >= 58 -> "C"
            else -> "D"
        }

        // Wins, watchouts & next-month focus
        val wins = mutableListOf<String>()
        val watchouts = mutableListOf<String>()
        val nextFocus = mutableListOf<String>()

        if (netSavings > 0) {
            wins.add("Positive net savings of ₹${netSavings.toLong()} ($savingsRate% savings rate).")
        } else {
            watchouts.add("Outflows exceeded income by ₹${kotlin.math.abs(netSavings).toLong()} this month.")
        }

        val underBudgetBest = budgetRows.filter { it.actual > 0 && it.percentUsed <= 80 }.minByOrNull { it.percentUsed }
        if (underBudgetBest != null) {
            wins.add("Stayed ${100 - underBudgetBest.percentUsed}% under your ${underBudgetBest.category} budget (saved ₹${underBudgetBest.variance.toLong()}).")
        }

        if (prevMonthSpend > 0 && momDelta < 0) {
            wins.add("Variable spending dropped ${kotlin.math.abs(momDeltaPct)}% (₹${kotlin.math.abs(momDelta).toLong()}) compared to last month.")
        } else if (prevMonthSpend > 0 && momDelta > 500) {
            watchouts.add("Variable spending rose +$momDeltaPct% (+₹${momDelta.toLong()}) compared to last month.")
        }

        budgetRows.filter { it.percentUsed > 100 }.forEach { row ->
            watchouts.add("${row.category} went ₹${kotlin.math.abs(row.variance).toLong()} over budget (${row.percentUsed}% used).")
        }

        if (priceChanged.isNotEmpty()) {
            val sub = priceChanged.first()
            watchouts.add("${sub.title} increased to ₹${sub.amount.toLong()}/mo — verify if the new tier is worth keeping.")
        }
        if (unused.isNotEmpty()) {
            val saveYr = (unused.sumOf { it.amount } * 12).toLong()
            nextFocus.add("Cancel ${unused.size} unused subscription(s) (${unused.joinToString { it.title }}) to unlock ₹$saveYr/year.")
        }
        val foodActual = actualByCat["Food"] ?: 0.0
        if (foodActual > 2000.0) {
            nextFocus.add("Trimming dining out by 20% next month would add ₹${(foodActual * 0.20).toLong()}/mo directly to your goals.")
        }
        val efGap = (efTarget - efCurrent).coerceAtLeast(0.0)
        if (efGap > 500.0) {
            nextFocus.add("Direct ₹${minOf(efGap, maxOf(1000.0, netSavings * 0.35)).toLong()} toward your emergency fund to reach ${profile?.emergencyFundTargetMonths ?: 3} months of coverage.")
        }
        if (nextFocus.isEmpty()) {
            nextFocus.add("Keep automating transfers on income day so your savings goal happens before discretionary spending.")
        }

        return MonthlyReviewReport(
            monthLabel = monthLabel,
            monthOffset = monthOffset,
            score = score,
            grade = grade,
            totalIncome = totalIncome,
            fixedCosts = fixedCosts,
            variableSpend = variableSpend,
            totalOutflow = totalOutflow,
            netSavings = netSavings,
            savingsRatePercent = savingsRate,
            previousMonthSpend = prevMonthSpend,
            monthOverMonthDelta = momDelta,
            monthOverMonthDeltaPercent = momDeltaPct,
            budgetRows = budgetRows,
            activeSubscriptionsTotal = activeSubsTotal,
            priceChangedSubs = priceChanged,
            unusedSubs = unused,
            emergencyFundCurrent = efCurrent,
            emergencyFundTarget = efTarget,
            emergencyFundMonthsCovered = efMonths,
            goalsSnapshot = goals,
            wins = wins,
            watchouts = watchouts,
            nextMonthFocus = nextFocus
        )
    }

    fun exportMonthlyReviewText(context: Context, review: MonthlyReviewReport): FileSaveResult {
        val fileName = "FinanceCoach_MonthlyReview_${fileTsFmt.format(Date())}.txt"
        val body = buildString {
            append("FINANCE COACH MONTHLY FINANCIAL REVIEW — ${review.monthLabel.uppercase(Locale.ENGLISH)}\n")
            append("============================================================\n")
            append("Financial Health Grade: ${review.grade} (${review.score}/100)\n")
            append("Total Income:           Rs. ${review.totalIncome.toLong()}\n")
            append("Fixed Costs:            Rs. ${review.fixedCosts.toLong()}\n")
            append("Variable Spending:      Rs. ${review.variableSpend.toLong()}\n")
            append("Net Monthly Savings:    Rs. ${review.netSavings.toLong()} (${review.savingsRatePercent}%)\n")
            if (review.previousMonthSpend > 0) {
                val sign = if (review.monthOverMonthDelta >= 0) "+" else "-"
                append("MoM Variable Change:    ${sign}Rs. ${kotlin.math.abs(review.monthOverMonthDelta).toLong()} (${sign}${kotlin.math.abs(review.monthOverMonthDeltaPercent)}%)\n")
            }
            append("\nBUDGET ADHERENCE\n")
            append("------------------------------------------------------------\n")
            for (row in review.budgetRows) {
                append(String.format(Locale.ENGLISH, "%-16s Actual: Rs. %-7d / Budget: Rs. %-7d (%d%%)\n",
                    row.category, row.actual.toLong(), row.budgeted.toLong(), row.percentUsed))
            }
            append("\nSUBSCRIPTIONS & RECURRING AUDIT\n")
            append("------------------------------------------------------------\n")
            append("Monthly Recurring Total: Rs. ${review.activeSubscriptionsTotal.toLong()}\n")
            append("Price Change Alerts:     ${review.priceChangedSubs.size}\n")
            append("Unused Subscriptions:    ${review.unusedSubs.size}\n")
            append("\nEMERGENCY FUND & GOALS\n")
            append("------------------------------------------------------------\n")
            append("Emergency Fund: Rs. ${review.emergencyFundCurrent.toLong()} / Rs. ${review.emergencyFundTarget.toLong()} (${String.format(Locale.ENGLISH, "%.1f", review.emergencyFundMonthsCovered)} months)\n")
            for (g in review.goalsSnapshot) {
                val pct = if (g.targetAmount > 0) ((g.savedAmount / g.targetAmount) * 100).toInt() else 0
                append("- ${g.title}: Rs. ${g.savedAmount.toLong()} / Rs. ${g.targetAmount.toLong()} ($pct%) | Target: ${g.targetDate.ifBlank { "Flexible" }}\n")
            }
            append("\nNEXT MONTH EDUCATIONAL FOCUS\n")
            append("------------------------------------------------------------\n")
            review.nextMonthFocus.forEach { append("- $it\n") }
            append("\nNote: Educational budgeting review only. Does not recommend specific securities.\n")
        }

        return try {
            val bytes = body.toByteArray(Charsets.UTF_8)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FinanceCoach")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    return FileSaveResult(true, fileName, "Downloads/FinanceCoach/$fileName", "Monthly Review saved to Downloads/FinanceCoach/$fileName")
                }
            }
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val file = File(dir, fileName)
            FileOutputStream(file).use { it.write(bytes) }
            FileSaveResult(true, fileName, file.absolutePath, "Monthly Review saved to ${file.name}")
        } catch (e: Exception) {
            FileSaveResult(false, fileName, null, "Could not save review: ${e.localizedMessage}")
        }
    }
}
