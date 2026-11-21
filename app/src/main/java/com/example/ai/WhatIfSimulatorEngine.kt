package com.example.ai

import com.example.data.model.GoalEntity
import com.example.data.model.RecurringExpenseEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UserProfileEntity
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

data class WhatIfScenario(
    val label: String = "Custom Scenario",
    val diningChangePercent: Int = 0, // e.g. -30 means cut dining out by 30%
    val discretionaryChangePercent: Int = 0, // Shopping & Entertainment % change
    val rentDeltaMonthly: Double = 0.0, // e.g. +2000 means rent goes up by ₹2000
    val incomeDeltaMonthly: Double = 0.0, // e.g. +3000 means side income up ₹3000
    val cancelUnusedSubscriptions: Boolean = false
)

data class GoalProjection(
    val goal: GoalEntity,
    val targetMonthsAvailable: Int,
    val requiredMonthly: Double,
    val baselineAllocatedMonthly: Double,
    val simulatedAllocatedMonthly: Double,
    val baselineMonthsToComplete: Int?, // null if surplus <= 0
    val simulatedMonthsToComplete: Int?,
    val monthsSaved: Int // positive = faster, negative = delayed
)

data class WhatIfResult(
    val scenario: WhatIfScenario,
    val baselineMonthlyIncome: Double,
    val simulatedMonthlyIncome: Double,
    val baselineFixedCosts: Double,
    val simulatedFixedCosts: Double,
    val baselineDiningMonthly: Double,
    val simulatedDiningMonthly: Double,
    val baselineDiscretionaryMonthly: Double,
    val simulatedDiscretionaryMonthly: Double,
    val baselineMonthlySurplus: Double,
    val simulatedMonthlySurplus: Double,
    val monthlySurplusDelta: Double,
    val annualImpact: Double,
    val unusedSubscriptionSavings: Double,
    val baselineEmergencyMonthsToTarget: Int?,
    val simulatedEmergencyMonthsToTarget: Int?,
    val goalProjections: List<GoalProjection>
)

object WhatIfSimulatorEngine {

    fun simulate(
        scenario: WhatIfScenario,
        profile: UserProfileEntity?,
        transactions: List<TransactionEntity>,
        goals: List<GoalEntity>,
        recurring: List<RecurringExpenseEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): WhatIfResult {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val curMonth = cal.get(Calendar.MONTH)
        val curYear = cal.get(Calendar.YEAR)

        val monthDebits = transactions.filter { tx ->
            val tCal = Calendar.getInstance().apply { timeInMillis = tx.date }
            tx.isDebit && tCal.get(Calendar.MONTH) == curMonth && tCal.get(Calendar.YEAR) == curYear
        }

        val baseIncome = profile?.totalMonthlyIncome ?: 15000.0
        val baseFixed = profile?.totalFixedCosts ?: 6500.0

        // Estimate monthly dining out / food variable spend
        val diningSpendRaw = monthDebits
            .filter { it.category.equals("Food", ignoreCase = true) }
            .sumOf { it.amount }
        val baseDining = if (diningSpendRaw > 0) diningSpendRaw else 3800.0

        val discretionaryRaw = monthDebits
            .filter {
                it.category.equals("Entertainment", ignoreCase = true) ||
                    it.category.equals("Shopping", ignoreCase = true) ||
                    it.category.equals("People", ignoreCase = true)
            }
            .sumOf { it.amount }
        val baseDiscretionary = if (discretionaryRaw > 0) discretionaryRaw else 2200.0

        val otherVariable = monthDebits
            .filter {
                !it.category.equals("Food", ignoreCase = true) &&
                    !it.category.equals("Entertainment", ignoreCase = true) &&
                    !it.category.equals("Shopping", ignoreCase = true) &&
                    !it.category.equals("People", ignoreCase = true) &&
                    !it.category.equals("Investments", ignoreCase = true)
            }
            .sumOf { it.amount }

        val unusedSubTotal = recurring
            .filter { it.status == "UNUSED" }
            .sumOf { it.amount }

        // Baseline vs Simulated
        val simIncome = (baseIncome + scenario.incomeDeltaMonthly).coerceAtLeast(0.0)
        val simFixed = (baseFixed + scenario.rentDeltaMonthly).coerceAtLeast(0.0)
        val simDining = (baseDining * (1.0 + scenario.diningChangePercent / 100.0)).coerceAtLeast(0.0)
        val simDiscretionary = (baseDiscretionary * (1.0 + scenario.discretionaryChangePercent / 100.0)).coerceAtLeast(0.0)
        val subSavings = if (scenario.cancelUnusedSubscriptions) unusedSubTotal else 0.0

        val baseTotalOutflow = baseFixed + baseDining + baseDiscretionary + otherVariable
        val simTotalOutflow = (simFixed + simDining + simDiscretionary + otherVariable - subSavings).coerceAtLeast(0.0)

        val baseSurplus = baseIncome - baseTotalOutflow
        val simSurplus = simIncome - simTotalOutflow
        val delta = simSurplus - baseSurplus

        // Emergency fund target months calculation (adjusted if rent/fixed costs rise!)
        val efCurrent = profile?.emergencyFundCurrent ?: 14000.0
        val efTargetMonths = profile?.emergencyFundTargetMonths ?: 3
        val baseEfTarget = (baseFixed + 2500.0) * efTargetMonths
        val simEfTarget = (simFixed + 2500.0) * efTargetMonths
        val baseEfGap = (baseEfTarget - efCurrent).coerceAtLeast(0.0)
        val simEfGap = (simEfTarget - efCurrent).coerceAtLeast(0.0)

        val efMonthlyShareBase = (baseSurplus * 0.35).coerceAtLeast(0.0)
        val efMonthlyShareSim = (simSurplus * 0.35).coerceAtLeast(0.0)

        val baseEfMonths = when {
            baseEfGap <= 0 -> 0
            efMonthlyShareBase <= 0 -> null
            else -> ceil(baseEfGap / efMonthlyShareBase).toInt()
        }
        val simEfMonths = when {
            simEfGap <= 0 -> 0
            efMonthlyShareSim <= 0 -> null
            else -> ceil(simEfGap / efMonthlyShareSim).toInt()
        }

        // Distribute surplus proportionally across active goals by their required monthly need
        val totalGoalNeed = goals.sumOf { it.requiredMonthlyContribution(nowMillis) }.coerceAtLeast(1.0)
        val goalProjections = goals.map { goal ->
            val reqMonthly = goal.requiredMonthlyContribution(nowMillis)
            val weight = reqMonthly / totalGoalNeed
            val baseAlloc = (baseSurplus.coerceAtLeast(0.0) * weight)
            val simAlloc = (simSurplus.coerceAtLeast(0.0) * weight)

            val baseMonths = when {
                goal.remainingAmount <= 0 -> 0
                baseAlloc <= 1.0 -> null
                else -> ceil(goal.remainingAmount / baseAlloc).toInt()
            }
            val simMonths = when {
                goal.remainingAmount <= 0 -> 0
                simAlloc <= 1.0 -> null
                else -> ceil(goal.remainingAmount / simAlloc).toInt()
            }

            val savedMonths = if (baseMonths != null && simMonths != null) baseMonths - simMonths else 0

            GoalProjection(
                goal = goal,
                targetMonthsAvailable = goal.monthsUntilTarget(nowMillis),
                requiredMonthly = reqMonthly,
                baselineAllocatedMonthly = baseAlloc,
                simulatedAllocatedMonthly = simAlloc,
                baselineMonthsToComplete = baseMonths,
                simulatedMonthsToComplete = simMonths,
                monthsSaved = savedMonths
            )
        }

        return WhatIfResult(
            scenario = scenario,
            baselineMonthlyIncome = baseIncome,
            simulatedMonthlyIncome = simIncome,
            baselineFixedCosts = baseFixed,
            simulatedFixedCosts = simFixed,
            baselineDiningMonthly = baseDining,
            simulatedDiningMonthly = simDining,
            baselineDiscretionaryMonthly = baseDiscretionary,
            simulatedDiscretionaryMonthly = simDiscretionary,
            baselineMonthlySurplus = baseSurplus,
            simulatedMonthlySurplus = simSurplus,
            monthlySurplusDelta = delta,
            annualImpact = delta * 12.0,
            unusedSubscriptionSavings = subSavings,
            baselineEmergencyMonthsToTarget = baseEfMonths,
            simulatedEmergencyMonthsToTarget = simEfMonths,
            goalProjections = goalProjections
        )
    }

    /**
     * Parses natural-language what-if queries for the AI Coach, e.g.:
     * "what if I cut dining out by 30%"
     * "what if my rent goes up by 2000"
     * "what if I cancel unused subscriptions"
     */
    fun parseNaturalLanguageScenario(query: String): WhatIfScenario? {
        val q = query.lowercase(Locale.ENGLISH)
        if (!q.contains("what if") && !q.contains("simulate") && !q.contains("scenario") &&
            !q.contains("if i cut") && !q.contains("if my rent") && !q.contains("if rent")
        ) {
            return null
        }

        val pctMatch = Regex("""(\d{1,2})\s*%""").find(q)?.groupValues?.get(1)?.toIntOrNull()
        val rupeeMatch = Regex("""(?:₹|rs\.?|inr|\s)(\d{3,6})""").find(q)?.groupValues?.get(1)?.toDoubleOrNull()

        var diningPct = 0
        var discretionaryPct = 0
        var rentDelta = 0.0
        var incomeDelta = 0.0
        var cancelUnused = false
        val labels = mutableListOf<String>()

        if (q.contains("dining") || q.contains("food") || q.contains("eating out") || q.contains("zomato") || q.contains("swiggy")) {
            val pct = pctMatch ?: 30
            diningPct = if (q.contains("increase") || q.contains("more") || q.contains("up")) pct else -pct
            labels.add("${if (diningPct < 0) "Cut" else "Increase"} dining by ${kotlin.math.abs(diningPct)}%")
        }

        if (q.contains("rent") || q.contains("hostel") || q.contains("fixed")) {
            val delta = rupeeMatch ?: 2000.0
            rentDelta = if (q.contains("drop") || q.contains("cut") || q.contains("down") || q.contains("less") || q.contains("save")) -delta else delta
            labels.add("Rent ${if (rentDelta >= 0) "+" else "-"}₹${kotlin.math.abs(rentDelta).toLong()}/mo")
        }

        if (q.contains("shopping") || q.contains("entertainment") || q.contains("discretionary")) {
            val pct = pctMatch ?: 25
            discretionaryPct = if (q.contains("increase") || q.contains("up")) pct else -pct
            labels.add("${if (discretionaryPct < 0) "Cut" else "Raise"} discretionary ${kotlin.math.abs(discretionaryPct)}%")
        }

        if (q.contains("subscription") || q.contains("unused")) {
            cancelUnused = true
            labels.add("Cancel unused subscriptions")
        }

        if (q.contains("income") || q.contains("earn") || q.contains("stipend") || q.contains("freelance") || q.contains("salary") || q.contains("allowance")) {
            val delta = rupeeMatch ?: 3000.0
            incomeDelta = if (q.contains("drop") || q.contains("lose") || q.contains("cut")) -delta else delta
            labels.add("Income ${if (incomeDelta >= 0) "+" else "-"}₹${kotlin.math.abs(incomeDelta).toLong()}/mo")
        }

        if (labels.isEmpty()) {
            // Default helpful simulation if user just says "run a what-if simulation"
            diningPct = -30
            cancelUnused = true
            labels.add("Cut dining 30% + cancel unused subs")
        }

        return WhatIfScenario(
            label = labels.joinToString(" & "),
            diningChangePercent = diningPct,
            discretionaryChangePercent = discretionaryPct,
            rentDeltaMonthly = rentDelta,
            incomeDeltaMonthly = incomeDelta,
            cancelUnusedSubscriptions = cancelUnused
        )
    }

    fun formatPercent(value: Double): String = "${value.roundToInt()}%"
}
