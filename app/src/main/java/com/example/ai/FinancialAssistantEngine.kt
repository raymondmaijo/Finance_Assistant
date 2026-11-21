package com.example.ai

import com.example.data.model.BudgetEntity
import com.example.data.model.GoalEntity
import com.example.data.model.RecurringExpenseEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UserProfileEntity
import com.example.util.MonthlyReviewEngine
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class FinancialAssistantEngine {

    private fun formatRupee(amount: Double): String {
        val sign = if (amount < 0) "-" else ""
        return sign + "₹" + NumberFormat.getNumberInstance(Locale("en", "IN")).format(abs(amount).toLong())
    }

    suspend fun answerQuestion(
        question: String,
        transactions: List<TransactionEntity>,
        budgets: List<BudgetEntity>,
        profile: UserProfileEntity?,
        goals: List<GoalEntity> = emptyList(),
        recurring: List<RecurringExpenseEntity> = emptyList()
    ): String {
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
            return guardrailCheck.educationalResponse
        }

        // 2. DETERMINISTIC WHAT-IF SCENARIO MODELER (instant, exact numbers!)
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
            sb.append("• **12-Month Impact:** $deltaSign${formatRupee(abs(sim.annualImpact))}/year\n")
            if (parsedScenario.diningChangePercent != 0) {
                sb.append("• **Dining / Food Spend:** ${formatRupee(sim.baselineDiningMonthly)} → ${formatRupee(sim.simulatedDiningMonthly)}\n")
            }
            if (parsedScenario.rentDeltaMonthly != 0.0) {
                sb.append("• **Fixed Costs (with Rent):** ${formatRupee(sim.baselineFixedCosts)} → ${formatRupee(sim.simulatedFixedCosts)}\n")
            }
            if (sim.unusedSubscriptionSavings > 0) {
                sb.append("• **Unused Subscriptions Cut:** +${formatRupee(sim.unusedSubscriptionSavings)}/mo freed\n")
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
            sb.append("\n💡 *Open the Goal Planner → What-If Simulator tab to fine-tune sliders interactively.*")
            return sb.toString()
        }

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
                return CoachGuardrails.sanitizeModelOutput(geminiResponse)
            }
        }

        // 4. High-Precision Deterministic Offline Financial Coach
        return when {
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
                    "• **Your $targetMonths-Month Target:** ${formatRupee(target)}\n" +
                    "• **Remaining Gap:** ${formatRupee(gap)}\n\n" +
                    if (gap > 0) "💡 Setting aside ₹2,000/mo closes this gap in ~**${kotlin.math.ceil(gap / 2000.0).toInt()} months**."
                    else "✅ Great work! You've hit your $targetMonths-month emergency cushion."
            }

            q.contains("diversif") || q.contains("compound") || q.contains("50/30/20") || q.contains("asset allocation") || q.contains("how does investing") -> {
                "🎓 **Personal Finance Concept Breakdown**\n" +
                    "• **50 / 30 / 20 Rule:** Aim to keep essentials & fixed costs near 50% of income, flexible wants near 30%, and savings/goals at 20%+.\n" +
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
                    append("• **Income:** ${formatRupee(review.totalIncome)} | **Fixed Costs:** ${formatRupee(review.fixedCosts)}\n")
                    append("• **Variable Spend:** ${formatRupee(review.variableSpend)} | **Net Savings:** ${formatRupee(review.netSavings)} (${review.savingsRatePercent}%)\n")
                    if (review.wins.isNotEmpty()) append("\n✅ **Win:** ${review.wins.first()}\n")
                    if (review.watchouts.isNotEmpty()) append("⚠️ **Watchout:** ${review.watchouts.first()}\n")
                    if (review.nextMonthFocus.isNotEmpty()) append("🎯 **Next Focus:** ${review.nextMonthFocus.first()}")
                }
            }

            // Drift check
            q.contains("drift") || q.contains("off plan") || q.contains("nudge") || q.contains("pace") -> {
                val nudges = SpendingDriftEngine.computeNudges(transactions, budgets, profile, recurring, goals)
                if (nudges.isEmpty()) {
                    "✅ You're right on plan! No spending drift detected across your categories."
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
                "🍽️ You've spent **${formatRupee(foodSpent)}** on Food this month ($percent% of your ${formatRupee(foodBudget)} budget).\n" +
                    if (percent > 85) "⚠️ You're drifting close to your food cap. Try asking *\"What if I cut dining out by 30%?\"* to see how much faster your goals finish."
                    else "✅ Your food spend is tracking safely within budget."
            }

            // Top spending / Where did money go
            q.contains("where did") || q.contains("most of my money") || q.contains("highest expense") || q.contains("top category") -> {
                if (categorySpending.isEmpty()) {
                    "You haven't recorded any variable expenses yet this month."
                } else {
                    val sorted = categorySpending.toList().sortedByDescending { it.second }
                    val top = sorted.first()
                    val breakdown = sorted.take(4).joinToString("\n") { "• ${it.first}: **${formatRupee(it.second)}**" }
                    "Most of your variable spend went to **${top.first}** (${formatRupee(top.second)}).\n\n**Top Categories This Month:**\n$breakdown"
                }
            }

            // Affordability query
            q.contains("afford") || q.contains("can i spend") || q.contains("can i buy") -> {
                val targetAmount = Regex("[0-9]+").find(q.replace(",", ""))?.value?.toDoubleOrNull() ?: 1000.0
                if (remainingFlexibleBudget >= targetAmount + savingsGoal) {
                    "✅ **Yes, comfortably!** After fixed costs (${formatRupee(fixedCosts)}) and your savings goal (${formatRupee(savingsGoal)}), you have **${formatRupee(remainingFlexibleBudget - savingsGoal)}** of flexible room."
                } else if (remainingFlexibleBudget >= targetAmount) {
                    "⚠️ **Possible, but with a trade-off:** You have **${formatRupee(remainingFlexibleBudget)}** left after fixed costs, so spending ${formatRupee(targetAmount)} will dip into your monthly savings goal of ${formatRupee(savingsGoal)}."
                } else {
                    "❌ **Not on plan right now:** Only **${formatRupee(remainingFlexibleBudget)}** remains after fixed costs and current spending. Try modeling a trim in another category first!"
                }
            }

            // Default overview
            else -> {
                "👋 **Your Financial Snapshot**\n" +
                    "• **Income:** ${formatRupee(totalIncome)} | **Fixed Costs:** ${formatRupee(fixedCosts)}\n" +
                    "• **Variable Spent:** ${formatRupee(totalSpentThisMonth)} | **Est. Surplus:** ${formatRupee(estimatedSurplus)}\n" +
                    "• **Emergency Fund:** ${formatRupee(profile?.emergencyFundCurrent ?: 14000.0)} (${String.format(Locale.ENGLISH, "%.1f", profile?.emergencyFundMonthsCovered ?: 1.5)} mo)\n\n" +
                    "Try asking:\n" +
                    "• *\"What if I cut dining out by 30%?\"*\n" +
                    "• *\"What if my rent goes up ₹2,000?\"*\n" +
                    "• *\"Audit my recurring subscriptions\"*\n" +
                    "• *\"Show my monthly review\"*"
            }
        }
    }
}
