package com.example.ai

import com.example.data.model.BudgetEntity
import com.example.data.model.DebtEntity
import com.example.data.model.GoalEntity
import com.example.data.model.RecurringExpenseEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UserProfileEntity
import com.example.ui.components.ChatVisualAnalysis
import com.example.ui.components.ComparisonMetricBar
import com.example.util.MonthlyReviewEngine
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class AssistantReply(
    val text: String,
    val visualAnalysis: ChatVisualAnalysis? = null
)

class FinancialAssistantEngine {

    private fun formatRupee(amount: Double): String {
        val sign = if (amount < 0) "-" else ""
        return sign + "₹" + NumberFormat.getNumberInstance(Locale("en", "IN")).format(abs(amount).toLong())
    }

    private fun formatDeltaRupee(delta: Double): String {
        if (abs(delta) < 1.0) return "No change"
        val sign = if (delta > 0) "+" else "-"
        return "$sign${formatRupee(abs(delta))}"
    }

    private fun formatDeltaPercent(current: Double, projected: Double): String {
        if (abs(projected - current) < 0.01) return "No change"
        if (abs(current) < 0.01) return formatDeltaRupee(projected - current)
        val pct = ((projected - current) / abs(current) * 100.0).roundToInt()
        val sign = if (pct > 0) "+" else ""
        return "$sign$pct%"
    }

    suspend fun answerQuestion(
        question: String,
        transactions: List<TransactionEntity>,
        budgets: List<BudgetEntity>,
        profile: UserProfileEntity?,
        goals: List<GoalEntity> = emptyList(),
        recurring: List<RecurringExpenseEntity> = emptyList(),
        debts: List<DebtEntity> = emptyList()
    ): String {
        return answerQuestionWithVisual(
            question = question,
            transactions = transactions,
            budgets = budgets,
            profile = profile,
            goals = goals,
            recurring = recurring,
            debts = debts
        ).text
    }

    suspend fun answerQuestionWithVisual(
        question: String,
        transactions: List<TransactionEntity>,
        budgets: List<BudgetEntity>,
        profile: UserProfileEntity?,
        goals: List<GoalEntity> = emptyList(),
        recurring: List<RecurringExpenseEntity> = emptyList(),
        debts: List<DebtEntity> = emptyList()
    ): AssistantReply {
        val q = question.lowercase(Locale.ENGLISH).trim()

        // Gather metrics for current month (case-insensitive DEBIT/CREDIT check)
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)

        val monthTransactions = transactions.filter {
            val tCal = Calendar.getInstance().apply { timeInMillis = it.date }
            tCal.get(Calendar.MONTH) == currentMonth && tCal.get(Calendar.YEAR) == currentYear
        }

        val totalSpentThisMonth = monthTransactions.filter { it.isDebit }.sumOf { it.amount }
        val totalIncomeThisMonth = monthTransactions.filter { it.isCredit }.sumOf { it.amount }
        val categorySpending = monthTransactions
            .filter { it.isDebit }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val totalIncome = profile?.totalMonthlyIncome ?: maxOf(totalIncomeThisMonth, 15000.0)
        val fixedCosts = profile?.totalFixedCosts ?: 6500.0
        val savingsGoal = profile?.monthlySavingsGoal ?: 3000.0
        val estimatedSurplus = totalIncome - fixedCosts - totalSpentThisMonth
        val remainingFlexibleBudget = (totalIncome - fixedCosts - totalSpentThisMonth).coerceAtLeast(0.0)

        // 1. HARD GUARDRAIL: Block specific security / stock / fund / crypto recommendations first!
        val guardrailCheck = CoachGuardrails.evaluateUserPrompt(question, estimatedSurplus)
        if (guardrailCheck.isRestrictedSecurityRequest && guardrailCheck.educationalResponse != null) {
            return AssistantReply(text = guardrailCheck.educationalResponse)
        }

        // 2. DETERMINISTIC WHAT-IF SCENARIO MODELER (instant exact numbers + 7-parameter bar chart!)
        val parsedScenario = WhatIfSimulatorEngine.parseNaturalLanguageScenario(question)
        if (parsedScenario != null) {
            val sim = WhatIfSimulatorEngine.simulate(
                scenario = parsedScenario,
                profile = profile,
                transactions = transactions,
                goals = goals,
                recurring = recurring
            )
            val deltaSign = if (sim.monthlySurplusDelta >= 0) "+" else "-"
            val sb = StringBuilder()
            sb.append("📊 **What-If Scenario: ${parsedScenario.label}**\n\n")
            sb.append("• **Monthly Surplus:** ${formatRupee(sim.baselineMonthlySurplus)} → **${formatRupee(sim.simulatedMonthlySurplus)}** ($deltaSign${formatRupee(abs(sim.monthlySurplusDelta))}/mo)\n")
            sb.append("• **12-Month Impact:** **$deltaSign${formatRupee(abs(sim.annualImpact))}/year**\n")
            if (parsedScenario.diningChangePercent != 0) {
                sb.append("• **Dining / Food Spend:** ${formatRupee(sim.baselineDiningMonthly)} → **${formatRupee(sim.simulatedDiningMonthly)}**\n")
            }
            if (parsedScenario.rentDeltaMonthly != 0.0) {
                sb.append("• **Fixed Costs (with Rent):** ${formatRupee(sim.baselineFixedCosts)} → **${formatRupee(sim.simulatedFixedCosts)}**\n")
            }
            if (sim.unusedSubscriptionSavings > 0) {
                sb.append("• **Unused Subscriptions Cut:** **+${formatRupee(sim.unusedSubscriptionSavings)}/mo** freed\n")
            }
            if (sim.goalProjections.isNotEmpty()) {
                sb.append("\n**Impact on Your Goals:**\n")
                sim.goalProjections.take(3).forEach { gp ->
                    val baseEta = gp.baselineMonthsToComplete?.let { "${it} mo" } ?: "Paused"
                    val simEta = gp.simulatedMonthsToComplete?.let { "${it} mo" } ?: "Paused"
                    val shift = when {
                        gp.monthsSaved > 0 -> "(${gp.monthsSaved} mo faster!)"
                        gp.monthsSaved < 0 -> "(${abs(gp.monthsSaved)} mo slower)"
                        else -> "(no ETA shift)"
                    }
                    sb.append("• **${gp.goal.title}:** $baseEta → **$simEta** $shift\n")
                }
            }
            sb.append("\n💡 *Explore the 7-parameter visual bar chart below comparing Current vs After Change values.*")

            val visual = buildWhatIfVisualAnalysis(parsedScenario, sim)
            return AssistantReply(text = sb.toString(), visualAnalysis = visual)
        }

        // Compute supporting visual chart for analytical non-what-if queries (used by both Gemini & offline paths)
        val supportingVisual = buildSupportingVisualAnalysis(
            q = q,
            transactions = transactions,
            budgets = budgets,
            profile = profile,
            goals = goals,
            recurring = recurring,
            debts = debts,
            categorySpending = categorySpending,
            totalIncome = totalIncome,
            fixedCosts = fixedCosts,
            totalSpentThisMonth = totalSpentThisMonth,
            estimatedSurplus = estimatedSurplus
        )

        // 3. Optional Gemini Cloud Coach (with strict educational guardrails & complete live profile context)
        if (GeminiService.isConfigured()) {
            val driftNudges = SpendingDriftEngine.computeNudges(transactions, budgets, profile, recurring, goals)
            val activeAlerts = recurring.filter { it.hasAlert }
            val systemContext = """
                You are Finance Coach, an educational personal-finance coach and scenario modeler.
                ${CoachGuardrails.GUARDRAIL_SYSTEM_PROMPT}

                USER PERSISTENT FINANCIAL PROFILE:
                - Name: ${profile?.name ?: "Student"}
                - Total Monthly Income: ${formatRupee(totalIncome)} (Primary: ${formatRupee(profile?.monthlyAllowance ?: 12000.0)}, Side: ${formatRupee(profile?.sideIncome ?: 3000.0)})
                - Monthly Fixed Costs: ${formatRupee(fixedCosts)} (Rent: ${formatRupee(profile?.monthlyRent ?: 4500.0)}, Utilities: ${formatRupee(profile?.monthlyUtilities ?: 1200.0)}, Other: ${formatRupee(profile?.otherFixedCosts ?: 800.0)})
                - Emergency Fund: ${formatRupee(profile?.emergencyFundCurrent ?: 14000.0)} / Target ${formatRupee(profile?.emergencyFundTargetAmount ?: 27000.0)} (${String.format(Locale.ENGLISH, "%.1f", profile?.emergencyFundMonthsCovered ?: 1.5)} of ${profile?.emergencyFundTargetMonths ?: 3} months)
                - Monthly Savings Target: ${formatRupee(savingsGoal)}
                - Variable Spent This Month: ${formatRupee(totalSpentThisMonth)}
                - Estimated Monthly Surplus: ${formatRupee(estimatedSurplus)}
                - Category Spend: ${categorySpending.entries.joinToString(", ") { "${it.key}: ${formatRupee(it.value)}" }}
                - Active Goals: ${goals.joinToString("; ") { "${it.title} (${formatRupee(it.savedAmount)}/${formatRupee(it.targetAmount)}, target ${it.targetDate})" }}
                - Subscription Alerts: ${activeAlerts.joinToString("; ") { "${it.title}: ${it.alertMessage}" }.ifBlank { "None" }}
                - Plan Drift Nudges: ${driftNudges.take(3).joinToString("; ") { it.title }.ifBlank { "On track" }}

                Keep responses concise, structured with bullet points and ₹ amounts, empathetic, and educational.
            """.trimIndent()

            val geminiResponse = GeminiService.askGemini(question, systemContext)
            if (!geminiResponse.isNullOrBlank()) {
                return AssistantReply(
                    text = CoachGuardrails.sanitizeModelOutput(geminiResponse),
                    visualAnalysis = supportingVisual
                )
            }
        }

        // 4. High-Precision Deterministic Offline Financial Coach
        val text = when {
            // Student Loans & Credit Card Debt coaching
            q.contains("debt") || q.contains("loan") || q.contains("credit card") || q.contains("apr") || q.contains("emi") -> {
                if (debts.isEmpty()) {
                    "💳 **Debt & Loans Tracker**\nYou currently have no student loans or credit cards tracked. Open **Debt & Loans** from Home to add accounts, view payment schedules, and run APR impact projections."
                } else {
                    val summary = DebtProjectionEngine.summarizePortfolio(debts)
                    val strategy = DebtProjectionEngine.comparePayoffStrategies(debts, extraMonthlyBudget = 2000.0)
                    val highest = summary.highestAprDebt
                    buildString {
                        append("💳 **Student Loans & Credit Card Debt Analysis**\n\n")
                        append("• **Total Debt Balance:** **${formatRupee(summary.totalBalance)}** (${summary.activeDebtCount} active accounts)\n")
                        append("• **Weighted Average APR:** **${String.format(Locale.ENGLISH, "%.1f", summary.weightedAverageApr)}%** | **Monthly Interest Burn:** **${formatRupee(summary.monthlyInterestBurn)}/mo**\n")
                        if (highest != null) {
                            append("• **Top Priority (Avalanche):** **${highest.name}** at **${highest.annualInterestRate}% APR** (${formatRupee(highest.currentBalance)})\n")
                        }
                        append("• **+₹2,000/mo Avalanche Impact:** Debt-free **${strategy.avalanche.monthsSavedVsMinimum} months faster** (${strategy.avalanche.payoffDateLabel}) saving **${formatRupee(strategy.avalanche.interestSavedVsMinimum)}** in interest!\n\n")
                        append("💡 *Open Debt & Loans from Home to inspect full month-by-month payment schedules and simulate APR changes.*")
                    }
                }
            }

            // Educational financial concepts
            q.contains("emergency fund") -> {
                val cur = profile?.emergencyFundCurrent ?: 14000.0
                val target = profile?.emergencyFundTargetAmount ?: 27000.0
                val months = profile?.emergencyFundMonthsCovered ?: 1.5
                val targetMonths = profile?.emergencyFundTargetMonths ?: 3
                val gap = (target - cur).coerceAtLeast(0.0)
                "🛡️ **Emergency Fund Readiness & Education**\n" +
                    "An emergency fund covers essential living expenses (rent, utilities, groceries) for 3–6 months in a liquid, low-risk account so unexpected costs never force debt.\n\n" +
                    "• **Your Current Cushion:** ${formatRupee(cur)} (**${String.format(Locale.ENGLISH, "%.1f", months)} months** covered)\n" +
                    "• **Your $targetMonths-Month Target:** **${formatRupee(target)}**\n" +
                    "• **Remaining Gap:** **${formatRupee(gap)}**\n\n" +
                    if (gap > 0) "💡 Setting aside **₹2,000/mo** closes this gap in ~**${kotlin.math.ceil(gap / 2000.0).toInt()} months**."
                    else "✅ Great work! You've hit your **$targetMonths-month** emergency cushion."
            }

            q.contains("diversif") || q.contains("compound") || q.contains("50/30/20") || q.contains("asset allocation") || q.contains("how does investing") -> {
                "🎓 **Personal Finance Concept Breakdown**\n" +
                    "• **50 / 30 / 20 Rule:** Aim to keep essentials & fixed costs near **50%** of income, flexible wants near **30%**, and savings/goals at **20%+**.\n" +
                    "• **Diversification:** Spreading risk across different asset classes (cash cushion, fixed income, broad market exposure) reduces the impact of any single asset dropping.\n" +
                    "• **Time Horizon:** Money needed within 1–3 years (like a laptop or emergency fund) generally belongs in capital-preserving liquid options, while long-term horizons can absorb more volatility.\n\n" +
                    "🛡️ *Educational overview only — I don't recommend specific securities. Want me to model how a savings rate change impacts your goal dates?*"
            }

            // Recurring & Subscription audit
            q.contains("subscription") || q.contains("recurring") || q.contains("unused") -> {
                val active = recurring.filter { it.status != "CANCELLED" }
                val totalRec = active.sumOf { it.amount }
                val priceChanges = active.filter { it.status == "PRICE_CHANGED" }
                val unused = active.filter { it.status == "UNUSED" }
                buildString {
                    append("🔁 **Recurring Payments & Subscription Audit**\n")
                    append("• **Active Recurring Commitments:** ${active.size} items totaling **${formatRupee(totalRec)}/mo**\n")
                    if (priceChanges.isNotEmpty()) {
                        append("\n⚠️ **Price Changes Detected:**\n")
                        priceChanges.forEach { append("• **${it.title}:** ${it.alertMessage}\n") }
                    }
                    if (unused.isNotEmpty()) {
                        append("\n💤 **Unused Subscriptions (30+ days):**\n")
                        unused.forEach { append("• **${it.title} (${formatRupee(it.amount)}/mo):** ${it.alertMessage}\n") }
                    }
                    if (priceChanges.isEmpty() && unused.isEmpty()) {
                        append("\n✅ No price hikes or unused subscriptions flagged right now.")
                    }
                }
            }

            // Monthly Review summary
            q.contains("monthly review") || q.contains("month review") || q.contains("report") || q.contains("how am i doing") -> {
                val review = MonthlyReviewEngine.buildMonthlyReview(0, transactions, budgets, profile, recurring, goals)
                buildString {
                    append("📋 **Monthly Review — ${review.monthLabel}**\n")
                    append("• **Financial Grade:** **${review.grade} (${review.score}/100)**\n")
                    append("• **Income:** **${formatRupee(review.totalIncome)}** | **Fixed Costs:** **${formatRupee(review.fixedCosts)}**\n")
                    append("• **Variable Spend:** **${formatRupee(review.variableSpend)}** | **Net Savings:** **${formatRupee(review.netSavings)} (${review.savingsRatePercent}%)**\n")
                    if (review.wins.isNotEmpty()) append("\n✅ **Win:** ${review.wins.first()}\n")
                    if (review.watchouts.isNotEmpty()) append("⚠️ **Watchout:** ${review.watchouts.first()}\n")
                    if (review.nextMonthFocus.isNotEmpty()) append("🎯 **Next Focus:** ${review.nextMonthFocus.first()}")
                }
            }

            // Drift check
            q.contains("drift") || q.contains("off plan") || q.contains("nudge") || q.contains("pace") -> {
                val nudges = SpendingDriftEngine.computeNudges(transactions, budgets, profile, recurring, goals)
                if (nudges.isEmpty()) {
                    "✅ **Right On Plan!**\nNo spending drift detected across your categories this month."
                } else {
                    buildString {
                        append("🧭 **Plan Drift Check-In**\n\n")
                        nudges.take(3).forEach { n ->
                            append("• **${n.title}:** ${n.message}\n")
                        }
                    }
                }
            }

            // Food spend
            q.contains("food") || q.contains("dining") -> {
                val foodSpent = categorySpending["Food"] ?: 0.0
                val foodBudget = budgets.find { it.categoryName.equals("Food", ignoreCase = true) }?.monthlyLimit ?: 4500.0
                val percent = if (foodBudget > 0) ((foodSpent / foodBudget) * 100).toInt() else 0
                "🍽️ **Food & Dining Summary**\n" +
                    "You've spent **${formatRupee(foodSpent)}** on Food this month (**$percent%** of your **${formatRupee(foodBudget)}** budget).\n\n" +
                    if (percent > 85) "⚠️ You're drifting close to your food cap. Check the **30% Dining Trim** bar chart below to see how much faster your goals finish!"
                    else "✅ Your food spend is tracking safely within budget. Check the visual comparison below for a 30% trim preview."
            }

            // Top spending / Where did money go
            q.contains("where did") || q.contains("most of my money") || q.contains("highest expense") || q.contains("top category") -> {
                if (categorySpending.isEmpty()) {
                    "📊 **Monthly Spend Breakdown**\nYou haven't recorded any variable expenses yet this month."
                } else {
                    val sorted = categorySpending.toList().sortedByDescending { it.second }
                    val top = sorted.first()
                    val breakdown = sorted.take(6).joinToString("\n") { "• **${it.first}:** **${formatRupee(it.second)}**" }
                    "📊 **Where Your Money Went**\nMost of your variable spend went to **${top.first}** (**${formatRupee(top.second)}**).\n\n**Top Categories This Month:**\n$breakdown"
                }
            }

            // Affordability query
            q.contains("afford") || q.contains("can i spend") || q.contains("can i buy") -> {
                val targetAmount = Regex("[0-9]+").find(q.replace(",", ""))?.value?.toDoubleOrNull() ?: 1000.0
                if (remainingFlexibleBudget >= targetAmount + savingsGoal) {
                    "✅ **Yes, Comfortably!**\nAfter fixed costs (**${formatRupee(fixedCosts)}**) and your savings goal (**${formatRupee(savingsGoal)}**), you have **${formatRupee(remainingFlexibleBudget - savingsGoal)}** of flexible room."
                } else if (remainingFlexibleBudget >= targetAmount) {
                    "⚠️ **Possible, With a Trade-Off**\nYou have **${formatRupee(remainingFlexibleBudget)}** left after fixed costs, so spending **${formatRupee(targetAmount)}** will dip into your monthly savings goal of **${formatRupee(savingsGoal)}**."
                } else {
                    "❌ **Not On Plan Right Now**\nOnly **${formatRupee(remainingFlexibleBudget)}** remains after fixed costs and current spending. Try modeling a trim in another category first!"
                }
            }

            // Default overview
            else -> {
                "👋 **Your Financial Snapshot**\n" +
                    "• **Income:** **${formatRupee(totalIncome)}** | **Fixed Costs:** **${formatRupee(fixedCosts)}**\n" +
                    "• **Variable Spent:** **${formatRupee(totalSpentThisMonth)}** | **Est. Surplus:** **${formatRupee(estimatedSurplus)}**\n" +
                    "• **Emergency Fund:** **${formatRupee(profile?.emergencyFundCurrent ?: 14000.0)}** (**${String.format(Locale.ENGLISH, "%.1f", profile?.emergencyFundMonthsCovered ?: 1.5)} mo**)\n\n" +
                    "**Try asking for visual simulations:**\n" +
                    "• *\"What if I cut dining out by 30%?\"*\n" +
                    "• *\"What if my rent goes up ₹2,000?\"*\n" +
                    "• *\"Audit my recurring subscriptions\"*\n" +
                    "• *\"Show my monthly review\"*"
            }
        }

        return AssistantReply(text = text, visualAnalysis = supportingVisual)
    }

    /**
     * Builds a comprehensive 7-parameter Current vs After Change grouped bar chart
     * for any What-If simulation scenario.
     */
    private fun buildWhatIfVisualAnalysis(
        scenario: WhatIfScenario,
        sim: WhatIfResult
    ): ChatVisualAnalysis {
        val baseTotalOutflow = (sim.baselineMonthlyIncome - sim.baselineMonthlySurplus).coerceAtLeast(0.0)
        val simTotalOutflow = (sim.simulatedMonthlyIncome - sim.simulatedMonthlySurplus).coerceAtLeast(0.0)

        val baseAnnualSavings = sim.baselineMonthlySurplus.coerceAtLeast(0.0) * 12.0
        val simAnnualSavings = sim.simulatedMonthlySurplus.coerceAtLeast(0.0) * 12.0

        val baseSavingsRate = if (sim.baselineMonthlyIncome > 0) {
            (sim.baselineMonthlySurplus / sim.baselineMonthlyIncome) * 100.0
        } else 0.0
        val simSavingsRate = if (sim.simulatedMonthlyIncome > 0) {
            (sim.simulatedMonthlySurplus / sim.simulatedMonthlyIncome) * 100.0
        } else 0.0
        val rateDiff = simSavingsRate - baseSavingsRate
        val rateDeltaLabel = when {
            abs(rateDiff) < 0.1 -> "No change"
            rateDiff > 0 -> "+${String.format(Locale.ENGLISH, "%.1f", rateDiff)}%"
            else -> "${String.format(Locale.ENGLISH, "%.1f", rateDiff)}%"
        }

        val metrics = mutableListOf(
            ComparisonMetricBar(
                label = "Dining / Food Spend",
                shortLabel = "Dining",
                currentValue = sim.baselineDiningMonthly,
                projectedValue = sim.simulatedDiningMonthly,
                currentFormatted = formatRupee(sim.baselineDiningMonthly),
                projectedFormatted = formatRupee(sim.simulatedDiningMonthly),
                deltaLabel = formatDeltaPercent(sim.baselineDiningMonthly, sim.simulatedDiningMonthly),
                isLowerBetter = true
            ),
            ComparisonMetricBar(
                label = "Total Monthly Outflow",
                shortLabel = "Outflow",
                currentValue = baseTotalOutflow,
                projectedValue = simTotalOutflow,
                currentFormatted = formatRupee(baseTotalOutflow),
                projectedFormatted = formatRupee(simTotalOutflow),
                deltaLabel = formatDeltaRupee(simTotalOutflow - baseTotalOutflow),
                isLowerBetter = true
            ),
            ComparisonMetricBar(
                label = "Monthly Surplus",
                shortLabel = "Surplus",
                currentValue = sim.baselineMonthlySurplus,
                projectedValue = sim.simulatedMonthlySurplus,
                currentFormatted = formatRupee(sim.baselineMonthlySurplus),
                projectedFormatted = formatRupee(sim.simulatedMonthlySurplus),
                deltaLabel = formatDeltaRupee(sim.monthlySurplusDelta),
                isLowerBetter = false
            ),
            ComparisonMetricBar(
                label = "12-Month Savings",
                shortLabel = "12M Save",
                currentValue = baseAnnualSavings,
                projectedValue = simAnnualSavings,
                currentFormatted = formatRupee(baseAnnualSavings),
                projectedFormatted = formatRupee(simAnnualSavings),
                deltaLabel = formatDeltaRupee(simAnnualSavings - baseAnnualSavings),
                isLowerBetter = false
            ),
            ComparisonMetricBar(
                label = "Savings Rate (%)",
                shortLabel = "Save %",
                currentValue = baseSavingsRate.coerceAtLeast(0.0),
                projectedValue = simSavingsRate.coerceAtLeast(0.0),
                currentFormatted = "${String.format(Locale.ENGLISH, "%.1f", baseSavingsRate)}%",
                projectedFormatted = "${String.format(Locale.ENGLISH, "%.1f", simSavingsRate)}%",
                deltaLabel = rateDeltaLabel,
                isLowerBetter = false
            ),
            ComparisonMetricBar(
                label = "Fixed Costs (Rent & Bills)",
                shortLabel = "Fixed",
                currentValue = sim.baselineFixedCosts,
                projectedValue = sim.simulatedFixedCosts,
                currentFormatted = formatRupee(sim.baselineFixedCosts),
                projectedFormatted = formatRupee(sim.simulatedFixedCosts),
                deltaLabel = formatDeltaRupee(sim.simulatedFixedCosts - sim.baselineFixedCosts),
                isLowerBetter = true
            )
        )

        // 7th parameter: Emergency Fund ETA (or Top Goal ETA, or Discretionary Spend)
        val baseEf = sim.baselineEmergencyMonthsToTarget
        val simEf = sim.simulatedEmergencyMonthsToTarget
        val topGoal = sim.goalProjections.firstOrNull {
            it.baselineMonthsToComplete != null && it.simulatedMonthsToComplete != null
        }

        when {
            topGoal != null && topGoal.baselineMonthsToComplete != null && topGoal.simulatedMonthsToComplete != null -> {
                val diffMo = topGoal.baselineMonthsToComplete - topGoal.simulatedMonthsToComplete
                val etaDelta = when {
                    diffMo > 0 -> "$diffMo mo faster"
                    diffMo < 0 -> "${abs(diffMo)} mo slower"
                    else -> "Same ETA"
                }
                metrics.add(
                    ComparisonMetricBar(
                        label = "${topGoal.goal.title} ETA",
                        shortLabel = "Goal ETA",
                        currentValue = topGoal.baselineMonthsToComplete.toDouble(),
                        projectedValue = topGoal.simulatedMonthsToComplete.toDouble(),
                        currentFormatted = "${topGoal.baselineMonthsToComplete} mo",
                        projectedFormatted = "${topGoal.simulatedMonthsToComplete} mo",
                        deltaLabel = etaDelta,
                        isLowerBetter = true
                    )
                )
            }
            baseEf != null && simEf != null -> {
                val diffMo = baseEf - simEf
                val etaDelta = when {
                    diffMo > 0 -> "$diffMo mo faster"
                    diffMo < 0 -> "${abs(diffMo)} mo slower"
                    else -> "Same ETA"
                }
                metrics.add(
                    ComparisonMetricBar(
                        label = "Emergency Fund ETA",
                        shortLabel = "EF ETA",
                        currentValue = baseEf.toDouble(),
                        projectedValue = simEf.toDouble(),
                        currentFormatted = "$baseEf mo",
                        projectedFormatted = "$simEf mo",
                        deltaLabel = etaDelta,
                        isLowerBetter = true
                    )
                )
            }
            else -> {
                metrics.add(
                    ComparisonMetricBar(
                        label = "Discretionary Spend",
                        shortLabel = "Discret.",
                        currentValue = sim.baselineDiscretionaryMonthly,
                        projectedValue = sim.simulatedDiscretionaryMonthly,
                        currentFormatted = formatRupee(sim.baselineDiscretionaryMonthly),
                        projectedFormatted = formatRupee(sim.simulatedDiscretionaryMonthly),
                        deltaLabel = formatDeltaPercent(sim.baselineDiscretionaryMonthly, sim.simulatedDiscretionaryMonthly),
                        isLowerBetter = true
                    )
                )
            }
        }

        return ChatVisualAnalysis(
            title = "What-If Visual Analysis: ${scenario.label}",
            subtitle = "Comparing Current vs After Change across ${metrics.size} key financial parameters",
            currentLegend = "Current",
            projectedLegend = "After Change",
            metrics = metrics
        )
    }

    /**
     * Builds contextual 6-parameter visual bar charts for analytical chat prompts
     * (Monthly Review, Top Spending, Food, Subscriptions, Emergency Fund).
     */
    private fun buildSupportingVisualAnalysis(
        q: String,
        transactions: List<TransactionEntity>,
        budgets: List<BudgetEntity>,
        profile: UserProfileEntity?,
        goals: List<GoalEntity>,
        recurring: List<RecurringExpenseEntity>,
        debts: List<DebtEntity>,
        categorySpending: Map<String, Double>,
        totalIncome: Double,
        fixedCosts: Double,
        totalSpentThisMonth: Double,
        estimatedSurplus: Double
    ): ChatVisualAnalysis? {
        return when {
            (q.contains("debt") || q.contains("loan") || q.contains("credit card") || q.contains("apr") || q.contains("emi")) && debts.isNotEmpty() -> {
                val summary = DebtProjectionEngine.summarizePortfolio(debts)
                val strategy = DebtProjectionEngine.comparePayoffStrategies(debts, extraMonthlyBudget = 2000.0)
                val topDebt = summary.highestAprDebt ?: debts.first()
                val baseTop = DebtProjectionEngine.buildPayoffProjection(topDebt)
                val lowerAprTop = DebtProjectionEngine.buildPayoffProjection(
                    debt = topDebt,
                    aprOverride = (topDebt.annualInterestRate - if (topDebt.isCreditCard) 6.0 else 1.5).coerceAtLeast(1.0),
                    extraMonthlyPayment = 2000.0
                )

                ChatVisualAnalysis(
                    title = "Debt Payoff & Interest Impact (+₹2,000/mo Avalanche)",
                    subtitle = "Minimum Payments vs +₹2,000/mo Extra Payment across 6 parameters",
                    currentLegend = "Min Pay",
                    projectedLegend = "Avalanche",
                    metrics = listOf(
                        ComparisonMetricBar("Total Interest Paid", "Interest", strategy.minimumOnly.totalInterestPaid, strategy.avalanche.totalInterestPaid, formatRupee(strategy.minimumOnly.totalInterestPaid), formatRupee(strategy.avalanche.totalInterestPaid), formatDeltaRupee(strategy.avalanche.totalInterestPaid - strategy.minimumOnly.totalInterestPaid), isLowerBetter = true),
                        ComparisonMetricBar("Months to Debt-Free", "Months", strategy.minimumOnly.monthsToDebtFree.toDouble(), strategy.avalanche.monthsToDebtFree.toDouble(), "${strategy.minimumOnly.monthsToDebtFree} mo", "${strategy.avalanche.monthsToDebtFree} mo", "-${strategy.avalanche.monthsSavedVsMinimum} mo", isLowerBetter = true),
                        ComparisonMetricBar("Monthly Debt Payment", "Pay/Mo", summary.totalMinimumMonthly, summary.totalMinimumMonthly + 2000.0, formatRupee(summary.totalMinimumMonthly), formatRupee(summary.totalMinimumMonthly + 2000.0), "+₹2,000", isLowerBetter = false),
                        ComparisonMetricBar("${topDebt.name.take(14)} ETA", "Top ETA", baseTop.monthsToPayoff.toDouble(), lowerAprTop.monthsToPayoff.toDouble(), "${baseTop.monthsToPayoff} mo", "${lowerAprTop.monthsToPayoff} mo", "${lowerAprTop.monthsToPayoff - baseTop.monthsToPayoff} mo", isLowerBetter = true),
                        ComparisonMetricBar("Top Debt Interest", "Top Int.", baseTop.totalInterestPaid, lowerAprTop.totalInterestPaid, formatRupee(baseTop.totalInterestPaid), formatRupee(lowerAprTop.totalInterestPaid), formatDeltaRupee(lowerAprTop.totalInterestPaid - baseTop.totalInterestPaid), isLowerBetter = true),
                        ComparisonMetricBar("Total Outflow (P+I)", "Total Cost", summary.totalBalance + strategy.minimumOnly.totalInterestPaid, summary.totalBalance + strategy.avalanche.totalInterestPaid, formatRupee(summary.totalBalance + strategy.minimumOnly.totalInterestPaid), formatRupee(summary.totalBalance + strategy.avalanche.totalInterestPaid), formatDeltaRupee(strategy.avalanche.totalInterestPaid - strategy.minimumOnly.totalInterestPaid), isLowerBetter = true)
                    )
                )
            }

            q.contains("monthly review") || q.contains("month review") || q.contains("report") || q.contains("how am i doing") -> {
                val review = MonthlyReviewEngine.buildMonthlyReview(0, transactions, budgets, profile, recurring, goals)
                val targetFixed = review.totalIncome * 0.50
                val targetVar = review.totalIncome * 0.30
                val targetSave = profile?.monthlySavingsGoal ?: (review.totalIncome * 0.20)
                val efCurrent = profile?.emergencyFundCurrent ?: 14000.0
                val efTarget = profile?.emergencyFundTargetAmount ?: 27000.0
                val recTotal = recurring.filter { it.status != "CANCELLED" }.sumOf { it.amount }
                val recOptimized = recurring.filter { it.status != "CANCELLED" && it.status != "UNUSED" }.sumOf { it.amount }

                ChatVisualAnalysis(
                    title = "Monthly Health Comparison — ${review.monthLabel}",
                    subtitle = "Your Current Actuals vs Recommended 50/30/20 Targets (6 parameters)",
                    currentLegend = "Target",
                    projectedLegend = "Actual",
                    metrics = listOf(
                        ComparisonMetricBar("Fixed Costs", "Fixed", targetFixed, review.fixedCosts, formatRupee(targetFixed), formatRupee(review.fixedCosts), formatDeltaRupee(review.fixedCosts - targetFixed), isLowerBetter = true),
                        ComparisonMetricBar("Variable Spend", "Variable", targetVar, review.variableSpend, formatRupee(targetVar), formatRupee(review.variableSpend), formatDeltaRupee(review.variableSpend - targetVar), isLowerBetter = true),
                        ComparisonMetricBar("Net Savings", "Savings", targetSave, review.netSavings, formatRupee(targetSave), formatRupee(review.netSavings), formatDeltaRupee(review.netSavings - targetSave), isLowerBetter = false),
                        ComparisonMetricBar("Savings Rate", "Save %", 20.0, review.savingsRatePercent.toDouble(), "20%", "${review.savingsRatePercent}%", "${review.savingsRatePercent - 20}% vs 20%", isLowerBetter = false),
                        ComparisonMetricBar("Emergency Fund", "EF Fund", efTarget, efCurrent, formatRupee(efTarget), formatRupee(efCurrent), "${((efCurrent / efTarget.coerceAtLeast(1.0)) * 100).roundToInt()}% ready", isLowerBetter = false),
                        ComparisonMetricBar("Recurring Bills", "Recur.", recOptimized, recTotal, formatRupee(recOptimized), formatRupee(recTotal), formatDeltaRupee(recTotal - recOptimized), isLowerBetter = true)
                    )
                )
            }

            q.contains("where did") || q.contains("most of my money") || q.contains("highest expense") || q.contains("top category") -> {
                val budgetMap = budgets.associate { it.categoryName.lowercase(Locale.ENGLISH) to it.monthlyLimit }
                val mergedCategories = (categorySpending.keys + budgets.map { it.categoryName })
                    .distinctBy { it.lowercase(Locale.ENGLISH) }
                    .map { cat ->
                        val spent = categorySpending.entries.firstOrNull { it.key.equals(cat, ignoreCase = true) }?.value ?: 0.0
                        val limit = budgetMap[cat.lowercase(Locale.ENGLISH)] ?: maxOf(spent, 1500.0)
                        Triple(cat, limit, spent)
                    }
                    .sortedByDescending { it.third }
                    .take(6)

                if (mergedCategories.isEmpty()) null else ChatVisualAnalysis(
                    title = "Category Spend vs Budget",
                    subtitle = "Budget Limit vs Actual Spend across top ${mergedCategories.size} categories",
                    currentLegend = "Budget",
                    projectedLegend = "Spent",
                    metrics = mergedCategories.map { (cat, limit, spent) ->
                        val pct = if (limit > 0) ((spent / limit) * 100).roundToInt() else 0
                        ComparisonMetricBar(
                            label = cat,
                            shortLabel = cat.take(7),
                            currentValue = limit,
                            projectedValue = spent,
                            currentFormatted = formatRupee(limit),
                            projectedFormatted = formatRupee(spent),
                            deltaLabel = "$pct% used",
                            isLowerBetter = true
                        )
                    }
                )
            }

            q.contains("food") || q.contains("dining") -> {
                val previewScenario = WhatIfScenario(
                    label = "30% Dining Trim Preview",
                    diningChangePercent = -30
                )
                val sim = WhatIfSimulatorEngine.simulate(previewScenario, profile, transactions, goals, recurring)
                buildWhatIfVisualAnalysis(previewScenario, sim)
            }

            q.contains("subscription") || q.contains("recurring") || q.contains("unused") -> {
                val subScenario = WhatIfScenario(
                    label = "Cancel Unused Subscriptions",
                    cancelUnusedSubscriptions = true
                )
                val sim = WhatIfSimulatorEngine.simulate(subScenario, profile, transactions, goals, recurring)
                buildWhatIfVisualAnalysis(subScenario, sim)
            }

            q.contains("emergency fund") -> {
                val cur = profile?.emergencyFundCurrent ?: 14000.0
                val target = profile?.emergencyFundTargetAmount ?: 27000.0
                val months = profile?.emergencyFundMonthsCovered ?: 1.5
                val targetMonths = (profile?.emergencyFundTargetMonths ?: 3).toDouble()
                val gap = (target - cur).coerceAtLeast(0.0)
                val monthlyAlloc = maxOf(estimatedSurplus * 0.35, 2000.0)
                val projectedIn6Mo = (cur + monthlyAlloc * 6).coerceAtMost(target)
                val projectedMonthsCovered = (projectedIn6Mo / maxOf(fixedCosts + 2500.0, 1.0))

                ChatVisualAnalysis(
                    title = "Emergency Fund Readiness (6-Month Projection)",
                    subtitle = "Current Cushion vs Projected Status in 6 Months across 6 parameters",
                    currentLegend = "Current",
                    projectedLegend = "In 6 Mo",
                    metrics = listOf(
                        ComparisonMetricBar("Emergency Cushion", "Cushion", cur, projectedIn6Mo, formatRupee(cur), formatRupee(projectedIn6Mo), formatDeltaRupee(projectedIn6Mo - cur), isLowerBetter = false),
                        ComparisonMetricBar("Remaining Gap", "EF Gap", gap, (target - projectedIn6Mo).coerceAtLeast(0.0), formatRupee(gap), formatRupee((target - projectedIn6Mo).coerceAtLeast(0.0)), formatDeltaRupee((target - projectedIn6Mo).coerceAtLeast(0.0) - gap), isLowerBetter = true),
                        ComparisonMetricBar("Months Covered", "Runway", months, projectedMonthsCovered, "${String.format(Locale.ENGLISH, "%.1f", months)} mo", "${String.format(Locale.ENGLISH, "%.1f", projectedMonthsCovered)} mo", "+${String.format(Locale.ENGLISH, "%.1f", (projectedMonthsCovered - months).coerceAtLeast(0.0))} mo", isLowerBetter = false),
                        ComparisonMetricBar("Target Completion %", "Ready %", (cur / target.coerceAtLeast(1.0)) * 100.0, (projectedIn6Mo / target.coerceAtLeast(1.0)) * 100.0, "${((cur / target.coerceAtLeast(1.0)) * 100).roundToInt()}%", "${((projectedIn6Mo / target.coerceAtLeast(1.0)) * 100).roundToInt()}%", "+${(((projectedIn6Mo - cur) / target.coerceAtLeast(1.0)) * 100).roundToInt()}%", isLowerBetter = false),
                        ComparisonMetricBar("Monthly EF Transfer", "EF/mo", 0.0, monthlyAlloc, "₹0", formatRupee(monthlyAlloc), "+${formatRupee(monthlyAlloc)}", isLowerBetter = false),
                        ComparisonMetricBar("Target Goal Cushion", "Target", target, target, formatRupee(target), formatRupee(target), "${targetMonths.toInt()} mo goal", isLowerBetter = false)
                    )
                )
            }

            else -> null
        }
    }
}
