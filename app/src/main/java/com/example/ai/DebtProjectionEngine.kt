package com.example.ai

import com.example.data.model.DebtEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

data class PaymentScheduleRow(
    val monthIndex: Int,
    val dueDateLabel: String,
    val paymentAmount: Double,
    val principalPaid: Double,
    val interestPaid: Double,
    val remainingBalance: Double,
    val cumulativeInterest: Double,
    val isGracePeriod: Boolean = false
)

data class DebtPayoffProjection(
    val debt: DebtEntity,
    val effectiveApr: Double,
    val monthlyPaymentUsed: Double,
    val monthsToPayoff: Int,
    val payoffDateLabel: String,
    val totalInterestPaid: Double,
    val totalAmountPaid: Double,
    val isAmortizing: Boolean,
    val schedule: List<PaymentScheduleRow>
)

data class InterestRateImpactPoint(
    val label: String,
    val aprPercent: Double,
    val deltaApr: Double,
    val monthsToPayoff: Int,
    val totalInterest: Double,
    val interestDeltaVsBase: Double,
    val monthsDeltaVsBase: Int,
    val monthlyInterestFirstMonth: Double,
    val isAmortizing: Boolean
)

data class StrategyOutcome(
    val strategyName: String,
    val monthsToDebtFree: Int,
    val payoffDateLabel: String,
    val totalInterestPaid: Double,
    val interestSavedVsMinimum: Double,
    val monthsSavedVsMinimum: Int,
    val priorityOrder: List<String>
)

data class PayoffStrategyComparison(
    val extraMonthlyBudget: Double,
    val minimumOnly: StrategyOutcome,
    val avalanche: StrategyOutcome,
    val snowball: StrategyOutcome
)

data class DebtPortfolioSummary(
    val totalBalance: Double,
    val studentLoanBalance: Double,
    val creditCardBalance: Double,
    val totalMinimumMonthly: Double,
    val weightedAverageApr: Double,
    val monthlyInterestBurn: Double,
    val activeDebtCount: Int,
    val highestAprDebt: DebtEntity?
)

object DebtProjectionEngine {

    private const val MAX_SIMULATION_MONTHS = 360
    private val monthYearFmt = SimpleDateFormat("MMM yyyy", Locale.ENGLISH)

    fun summarizePortfolio(debts: List<DebtEntity>): DebtPortfolioSummary {
        val active = debts.filter { it.currentBalance > 0.01 }
        val totalBal = active.sumOf { it.currentBalance }
        val loanBal = active.filter { it.isStudentLoan }.sumOf { it.currentBalance }
        val cardBal = active.filter { it.isCreditCard }.sumOf { it.currentBalance }
        val totalMin = active.sumOf { it.minimumMonthlyPayment }
        val weightedApr = if (totalBal > 0.0) {
            active.sumOf { it.currentBalance * it.annualInterestRate } / totalBal
        } else 0.0
        val monthlyBurn = active.sumOf { it.currentMonthlyInterest }
        return DebtPortfolioSummary(
            totalBalance = totalBal,
            studentLoanBalance = loanBal,
            creditCardBalance = cardBal,
            totalMinimumMonthly = totalMin,
            weightedAverageApr = weightedApr,
            monthlyInterestBurn = monthlyBurn,
            activeDebtCount = active.size,
            highestAprDebt = active.maxByOrNull { it.annualInterestRate }
        )
    }

    /**
     * Standard EMI calculation for student loans / installment plans.
     */
    fun calculateMonthlyEmi(principal: Double, annualRatePercent: Double, tenureMonths: Int): Double {
        if (principal <= 0.0 || tenureMonths <= 0) return 0.0
        val r = (annualRatePercent.coerceAtLeast(0.0) / 100.0) / 12.0
        if (r <= 0.000001) return principal / tenureMonths
        val factor = (1.0 + r).pow(tenureMonths)
        return principal * r * factor / (factor - 1.0)
    }

    /**
     * Builds a month-by-month amortization schedule and summary metrics for a single debt,
     * supporting custom APR shifts, extra monthly payments, and student loan grace periods.
     */
    fun buildPayoffProjection(
        debt: DebtEntity,
        aprOverride: Double? = null,
        extraMonthlyPayment: Double = 0.0,
        nowMillis: Long = System.currentTimeMillis()
    ): DebtPayoffProjection {
        val effectiveApr = (aprOverride ?: debt.annualInterestRate).coerceIn(0.0, 65.0)
        val monthlyRate = (effectiveApr / 100.0) / 12.0
        val targetPayment = (debt.minimumMonthlyPayment + extraMonthlyPayment).coerceAtLeast(0.0)

        if (debt.currentBalance <= 0.01) {
            return DebtPayoffProjection(
                debt = debt,
                effectiveApr = effectiveApr,
                monthlyPaymentUsed = targetPayment,
                monthsToPayoff = 0,
                payoffDateLabel = "Paid Off",
                totalInterestPaid = 0.0,
                totalAmountPaid = 0.0,
                isAmortizing = true,
                schedule = emptyList()
            )
        }

        val rows = mutableListOf<PaymentScheduleRow>()
        var balance = debt.currentBalance
        var cumulativeInterest = 0.0
        var totalPaid = 0.0
        val graceMonths = if (debt.isStudentLoan) debt.gracePeriodMonths.coerceIn(0, 60) else 0
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }

        // Check whether post-grace monthly payment actually beats monthly interest
        var postGraceBalance = balance
        if (graceMonths > 0) {
            for (g in 1..graceMonths) {
                postGraceBalance += postGraceBalance * monthlyRate
            }
        }
        val firstActiveInterest = postGraceBalance * monthlyRate
        val isAmortizing = targetPayment > firstActiveInterest + 0.5 || (monthlyRate == 0.0 && targetPayment > 0.0)

        var month = 0
        while (balance > 0.5 && month < MAX_SIMULATION_MONTHS) {
            month++
            cal.add(Calendar.MONTH, 1)
            val dateLabel = monthYearFmt.format(cal.time)
            val interest = balance * monthlyRate

            if (month <= graceMonths) {
                // Moratorium / grace period: interest accrues, payment = 0
                balance += interest
                cumulativeInterest += interest
                rows.add(
                    PaymentScheduleRow(
                        monthIndex = month,
                        dueDateLabel = dateLabel,
                        paymentAmount = 0.0,
                        principalPaid = 0.0,
                        interestPaid = interest,
                        remainingBalance = balance,
                        cumulativeInterest = cumulativeInterest,
                        isGracePeriod = true
                    )
                )
            } else {
                if (!isAmortizing && month > graceMonths + 60) {
                    // Cap non-amortizing projection cleanly at 60 active months so table stays readable
                    break
                }
                val totalDue = balance + interest
                val actualPayment = if (targetPayment >= totalDue) totalDue else targetPayment
                val principalPaid = (actualPayment - interest).coerceAtLeast(0.0)
                balance = (totalDue - actualPayment).coerceAtLeast(0.0)
                cumulativeInterest += interest
                totalPaid += actualPayment

                rows.add(
                    PaymentScheduleRow(
                        monthIndex = month,
                        dueDateLabel = dateLabel,
                        paymentAmount = actualPayment,
                        principalPaid = principalPaid,
                        interestPaid = interest,
                        remainingBalance = balance,
                        cumulativeInterest = cumulativeInterest,
                        isGracePeriod = false
                    )
                )
            }
        }

        val payoffLabel = when {
            !isAmortizing -> "Never (Payment ≤ Interest)"
            balance > 0.5 -> "30+ yrs"
            rows.isEmpty() -> "Paid Off"
            else -> rows.last().dueDateLabel
        }

        return DebtPayoffProjection(
            debt = debt,
            effectiveApr = effectiveApr,
            monthlyPaymentUsed = targetPayment,
            monthsToPayoff = if (isAmortizing) rows.size else MAX_SIMULATION_MONTHS,
            payoffDateLabel = payoffLabel,
            totalInterestPaid = cumulativeInterest,
            totalAmountPaid = totalPaid,
            isAmortizing = isAmortizing,
            schedule = rows
        )
    }

    /**
     * Generates a 5-point interest rate sensitivity curve for the selected debt.
     * Student loans step by ±1% / ±2% APR; high-APR credit cards step by -12%, -6%, 0%, +3%, +6%
     * so users can see the impact of refinancing/EMI conversion vs penalty APR hikes.
     */
    fun buildInterestRateSensitivity(
        debt: DebtEntity,
        extraMonthlyPayment: Double = 0.0
    ): List<InterestRateImpactPoint> {
        val deltas = if (debt.isCreditCard && debt.annualInterestRate >= 20.0) {
            listOf(-12.0, -6.0, 0.0, 3.0, 6.0)
        } else {
            listOf(-2.0, -1.0, 0.0, 1.0, 2.0)
        }

        val baseline = buildPayoffProjection(
            debt = debt,
            aprOverride = debt.annualInterestRate,
            extraMonthlyPayment = extraMonthlyPayment
        )

        return deltas.map { delta ->
            val testedApr = (debt.annualInterestRate + delta).coerceIn(0.0, 60.0)
            val proj = buildPayoffProjection(
                debt = debt,
                aprOverride = testedApr,
                extraMonthlyPayment = extraMonthlyPayment
            )
            val label = when {
                abs(delta) < 0.01 -> "Current"
                delta > 0 -> "+${delta.roundToInt()}%"
                else -> "${delta.roundToInt()}%"
            }
            InterestRateImpactPoint(
                label = label,
                aprPercent = testedApr,
                deltaApr = testedApr - debt.annualInterestRate,
                monthsToPayoff = proj.monthsToPayoff,
                totalInterest = proj.totalInterestPaid,
                interestDeltaVsBase = proj.totalInterestPaid - baseline.totalInterestPaid,
                monthsDeltaVsBase = proj.monthsToPayoff - baseline.monthsToPayoff,
                monthlyInterestFirstMonth = debt.currentBalance * (testedApr / 100.0 / 12.0),
                isAmortizing = proj.isAmortizing
            )
        }
    }

    /**
     * Compares Debt Avalanche (highest APR first) vs Debt Snowball (lowest balance first)
     * against paying minimums only across all active debts.
     */
    fun comparePayoffStrategies(
        debts: List<DebtEntity>,
        extraMonthlyBudget: Double,
        nowMillis: Long = System.currentTimeMillis()
    ): PayoffStrategyComparison {
        val active = debts.filter { it.currentBalance > 0.01 }
        val minOutcome = simulateMultiDebtStrategy(
            debts = active,
            extraBudget = 0.0,
            sortByAvalanche = true,
            strategyName = "Minimum Payments",
            nowMillis = nowMillis,
            baseInterest = null,
            baseMonths = null
        )
        val avalanche = simulateMultiDebtStrategy(
            debts = active,
            extraBudget = extraMonthlyBudget,
            sortByAvalanche = true,
            strategyName = "Avalanche (Highest APR)",
            nowMillis = nowMillis,
            baseInterest = minOutcome.totalInterestPaid,
            baseMonths = minOutcome.monthsToDebtFree
        )
        val snowball = simulateMultiDebtStrategy(
            debts = active,
            extraBudget = extraMonthlyBudget,
            sortByAvalanche = false,
            strategyName = "Snowball (Smallest Bal.)",
            nowMillis = nowMillis,
            baseInterest = minOutcome.totalInterestPaid,
            baseMonths = minOutcome.monthsToDebtFree
        )
        return PayoffStrategyComparison(
            extraMonthlyBudget = extraMonthlyBudget,
            minimumOnly = minOutcome,
            avalanche = avalanche,
            snowball = snowball
        )
    }

    private fun simulateMultiDebtStrategy(
        debts: List<DebtEntity>,
        extraBudget: Double,
        sortByAvalanche: Boolean,
        strategyName: String,
        nowMillis: Long,
        baseInterest: Double?,
        baseMonths: Int?
    ): StrategyOutcome {
        if (debts.isEmpty()) {
            return StrategyOutcome(
                strategyName = strategyName,
                monthsToDebtFree = 0,
                payoffDateLabel = "Debt Free",
                totalInterestPaid = 0.0,
                interestSavedVsMinimum = 0.0,
                monthsSavedVsMinimum = 0,
                priorityOrder = emptyList()
            )
        }

        val ordered = if (sortByAvalanche) {
            debts.sortedWith(compareByDescending<DebtEntity> { it.annualInterestRate }.thenBy { it.currentBalance })
        } else {
            debts.sortedWith(compareBy<DebtEntity> { it.currentBalance }.thenByDescending { it.annualInterestRate })
        }

        val balances = ordered.map { it.currentBalance }.toMutableList()
        val rates = ordered.map { it.monthlyInterestRate }
        val mins = ordered.map { it.minimumMonthlyPayment.coerceAtLeast(100.0) }
        val totalMonthlyPool = mins.sum() + extraBudget.coerceAtLeast(0.0)

        var month = 0
        var totalInterest = 0.0

        while (balances.any { it > 0.5 } && month < MAX_SIMULATION_MONTHS) {
            month++
            var remainingPool = totalMonthlyPool

            // Step 1: Accrue monthly interest and pay minimums on all active accounts
            for (i in balances.indices) {
                if (balances[i] <= 0.5) continue
                val interest = balances[i] * rates[i]
                totalInterest += interest
                balances[i] += interest

                val minPay = minOf(balances[i], mins[i], remainingPool)
                balances[i] = (balances[i] - minPay).coerceAtLeast(0.0)
                remainingPool = (remainingPool - minPay).coerceAtLeast(0.0)
            }

            // Step 2: Roll over any freed minimums + extra budget to top priority debt
            for (i in balances.indices) {
                if (remainingPool <= 0.5) break
                if (balances[i] <= 0.5) continue
                val extraPay = minOf(balances[i], remainingPool)
                balances[i] = (balances[i] - extraPay).coerceAtLeast(0.0)
                remainingPool = (remainingPool - extraPay).coerceAtLeast(0.0)
            }
        }

        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.MONTH, month)
        }
        val dateLabel = if (balances.any { it > 0.5 }) "30+ yrs" else monthYearFmt.format(cal.time)
        val savedInterest = if (baseInterest != null) (baseInterest - totalInterest).coerceAtLeast(0.0) else 0.0
        val savedMonths = if (baseMonths != null) (baseMonths - month).coerceAtLeast(0) else 0

        return StrategyOutcome(
            strategyName = strategyName,
            monthsToDebtFree = month,
            payoffDateLabel = dateLabel,
            totalInterestPaid = totalInterest,
            interestSavedVsMinimum = savedInterest,
            monthsSavedVsMinimum = savedMonths,
            priorityOrder = ordered.map { it.name }
        )
    }
}
