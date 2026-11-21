package com.example.ai

import com.example.data.model.BudgetEntity
import com.example.data.model.GoalEntity
import com.example.data.model.RecurringExpenseEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UserProfileEntity
import java.util.Calendar
import kotlin.math.roundToInt

enum class DriftSeverity {
    CRITICAL,
    WARNING,
    INFO,
    POSITIVE
}

data class DriftNudge(
    val id: String,
    val title: String,
    val message: String,
    val severity: DriftSeverity,
    val category: String = "",
    val driftPercent: Int = 0,
    val actionLabel: String = "",
    val actionTarget: String = "" // "SIMULATOR", "SUBSCRIPTIONS", "BUDGET", "PROFILE", "COACH"
)

object SpendingDriftEngine {

    fun computeNudges(
        transactions: List<TransactionEntity>,
        budgets: List<BudgetEntity>,
        profile: UserProfileEntity?,
        recurring: List<RecurringExpenseEntity>,
        goals: List<GoalEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<DriftNudge> {
        val nudges = mutableListOf<DriftNudge>()

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val curMonth = cal.get(Calendar.MONTH)
        val curYear = cal.get(Calendar.YEAR)
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(28)
        val daysLeft = (daysInMonth - dayOfMonth).coerceAtLeast(1)
        val monthProgress = dayOfMonth.toDouble() / daysInMonth.toDouble()

        val monthDebits = transactions.filter { tx ->
            val tCal = Calendar.getInstance().apply { timeInMillis = tx.date }
            tx.isDebit && tCal.get(Calendar.MONTH) == curMonth && tCal.get(Calendar.YEAR) == curYear
        }

        val totalSpent = monthDebits.sumOf { it.amount }
        val totalIncome = profile?.totalMonthlyIncome ?: 15000.0
        val fixedCosts = profile?.totalFixedCosts ?: 6500.0
        val savingsTarget = profile?.monthlySavingsGoal ?: 3000.0
        val variablePlan = (totalIncome - fixedCosts - savingsTarget).coerceAtLeast(3000.0)
        val expectedSpendByToday = variablePlan * monthProgress

        // 1. Overall monthly plan velocity drift
        if (totalSpent > 0 && expectedSpendByToday > 0) {
            val paceRatio = totalSpent / expectedSpendByToday
            val usedPct = ((totalSpent / variablePlan) * 100.0).roundToInt()
            val elapsedPct = (monthProgress * 100.0).roundToInt()
            val safeDailyRemaining = ((variablePlan - totalSpent).coerceAtLeast(0.0) / daysLeft).toLong()

            if (paceRatio >= 1.20) {
                val aheadPct = ((paceRatio - 1.0) * 100.0).roundToInt()
                nudges.add(
                    DriftNudge(
                        id = "overall_pace_drift",
                        title = "Spending is $aheadPct% ahead of plan",
                        message = "You've used $usedPct% of your flexible budget with ${100 - elapsedPct}% of the month left. Cap daily spend to ₹$safeDailyRemaining/day to protect your ₹${savingsTarget.toLong()} savings goal.",
                        severity = if (usedPct >= 85) DriftSeverity.CRITICAL else DriftSeverity.WARNING,
                        driftPercent = aheadPct,
                        actionLabel = "Run What-If",
                        actionTarget = "SIMULATOR"
                    )
                )
            } else if (paceRatio <= 0.85 && dayOfMonth >= 5) {
                nudges.add(
                    DriftNudge(
                        id = "overall_on_track",
                        title = "Spending pace is on plan",
                        message = "You've used $usedPct% of your flexible plan ($elapsedPct% through the month). You have ₹$safeDailyRemaining/day safe runway remaining.",
                        severity = DriftSeverity.POSITIVE,
                        driftPercent = 0,
                        actionLabel = "View Review",
                        actionTarget = "REVIEW"
                    )
                )
            }
        }

        // 2. Category-level budget drift checks
        val spendingByCat = monthDebits.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        for (budget in budgets) {
            if (budget.monthlyLimit <= 0) continue
            val spent = spendingByCat[budget.categoryName] ?: 0.0
            if (spent <= 0) continue
            val expectedCat = budget.monthlyLimit * monthProgress
            val ratioToLimit = spent / budget.monthlyLimit
            val ratioToPace = if (expectedCat > 0) spent / expectedCat else 0.0

            if (ratioToLimit >= 0.90) {
                val pct = (ratioToLimit * 100).roundToInt()
                nudges.add(
                    DriftNudge(
                        id = "cat_critical_${budget.categoryName}",
                        title = "${budget.categoryName} hit $pct% of budget",
                        message = "₹${spent.toLong()} spent vs ₹${budget.monthlyLimit.toLong()} monthly limit. Try simulating a 30% cut in ${budget.categoryName} to stay on plan.",
                        severity = DriftSeverity.CRITICAL,
                        category = budget.categoryName,
                        driftPercent = pct - 100,
                        actionLabel = "Simulate Cut",
                        actionTarget = "SIMULATOR"
                    )
                )
            } else if (ratioToPace >= 1.25 && spent >= 500.0) {
                val ahead = ((ratioToPace - 1.0) * 100).roundToInt()
                nudges.add(
                    DriftNudge(
                        id = "cat_drift_${budget.categoryName}",
                        title = "${budget.categoryName} drifting +$ahead% over pace",
                        message = "₹${spent.toLong()} spent so far vs ₹${expectedCat.toLong()} expected by day $dayOfMonth.",
                        severity = DriftSeverity.WARNING,
                        category = budget.categoryName,
                        driftPercent = ahead,
                        actionLabel = "Adjust Budget",
                        actionTarget = "BUDGET"
                    )
                )
            }
        }

        // 3. Subscription price hike & unused alerts surfaced as actionable nudges
        val priceHikes = recurring.filter { it.status == "PRICE_CHANGED" }
        if (priceHikes.isNotEmpty()) {
            val top = priceHikes.first()
            nudges.add(
                DriftNudge(
                    id = "sub_price_${top.id}",
                    title = "Subscription price changed: ${top.title}",
                    message = top.alertMessage.ifBlank { "${top.title} changed to ₹${top.amount.toLong()}/mo." },
                    severity = DriftSeverity.WARNING,
                    category = "Subscriptions",
                    actionLabel = "Review Subs",
                    actionTarget = "SUBSCRIPTIONS"
                )
            )
        }

        val unusedSubs = recurring.filter { it.status == "UNUSED" }
        if (unusedSubs.isNotEmpty()) {
            val unusedTotal = unusedSubs.sumOf { it.amount }
            nudges.add(
                DriftNudge(
                    id = "sub_unused_alert",
                    title = "${unusedSubs.size} unused subscription(s) draining ₹${unusedTotal.toLong()}/mo",
                    message = "${unusedSubs.joinToString(", ") { it.title }} hasn't been used in 30+ days. Cancelling frees ₹${(unusedTotal * 12).toLong()}/year for your goals.",
                    severity = DriftSeverity.WARNING,
                    category = "Subscriptions",
                    actionLabel = "Audit Subs",
                    actionTarget = "SUBSCRIPTIONS"
                )
            )
        }

        // 4. Emergency fund gap nudge
        if (profile != null && profile.emergencyFundMonthsCovered < profile.emergencyFundTargetMonths.toDouble()) {
            val gap = (profile.emergencyFundTargetAmount - profile.emergencyFundCurrent).coerceAtLeast(0.0)
            if (gap > 500.0) {
                nudges.add(
                    DriftNudge(
                        id = "emergency_fund_gap",
                        title = "Emergency fund at ${String.format("%.1f", profile.emergencyFundMonthsCovered)} / ${profile.emergencyFundTargetMonths} months",
                        message = "You're ₹${gap.toLong()} away from a full ${profile.emergencyFundTargetMonths}-month safety cushion.",
                        severity = DriftSeverity.INFO,
                        actionLabel = "Profile",
                        actionTarget = "PROFILE"
                    )
                )
            }
        }

        // 5. Goal funding shortfall nudge
        val totalMonthlyGoalNeed = goals.sumOf { it.requiredMonthlyContribution(nowMillis) }
        val projectedSurplus = (totalIncome - fixedCosts - totalSpent).coerceAtLeast(0.0)
        if (goals.isNotEmpty() && totalMonthlyGoalNeed > projectedSurplus + 500.0) {
            val gap = (totalMonthlyGoalNeed - projectedSurplus).toLong()
            nudges.add(
                DriftNudge(
                    id = "goal_shortfall",
                    title = "Goals need ₹$gap/mo more to hit target dates",
                    message = "Your ${goals.size} active goals need ₹${totalMonthlyGoalNeed.toLong()}/mo combined. Test cuts in the What-If Goal Planner.",
                    severity = DriftSeverity.INFO,
                    actionLabel = "Plan Goals",
                    actionTarget = "SIMULATOR"
                )
            )
        }

        return nudges.sortedBy {
            when (it.severity) {
                DriftSeverity.CRITICAL -> 0
                DriftSeverity.WARNING -> 1
                DriftSeverity.INFO -> 2
                DriftSeverity.POSITIVE -> 3
            }
        }
    }
}
