package com.example.ai

import com.example.data.model.RecurringExpenseEntity
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class RecurringScanSummary(
    val detectedCount: Int,
    val priceChangeAlerts: Int,
    val unusedAlerts: Int,
    val totalMonthlyRecurring: Double,
    val potentialAnnualSavingsFromUnused: Double
)

object RecurringDetectionEngine {

    private val KNOWN_SUBSCRIPTION_KEYWORDS = setOf(
        "netflix", "spotify", "prime", "amazon prime", "youtube", "hotstar", "disney",
        "icloud", "google one", "cloudbox", "dropbox", "chatgpt", "openai", "claude",
        "notion", "canva", "figma", "adobe", "gym", "cult", "hostel mess", "mess",
        "jio", "airtel", "vi ", "broadband", "wifi", "act fibernet", "rent", "audible",
        "sonyliv", "zee5", "swiggy one", "zomato gold", "linkedin"
    )

    private val DISCRETIONARY_UNUSED_CANDIDATES = setOf(
        "netflix", "spotify", "prime", "youtube", "hotstar", "disney", "cloudbox",
        "dropbox", "canva", "adobe", "gym", "cult", "audible", "sonyliv", "zee5",
        "linkedin", "notion", "swiggy one", "zomato gold", "gymfit"
    )

    private val dueDateFmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)

    fun detectAndMerge(
        transactions: List<TransactionEntity>,
        existingRecurring: List<RecurringExpenseEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<List<RecurringExpenseEntity>, RecurringScanSummary> {
        val dayMs = 24L * 60 * 60 * 1000
        val debits = transactions.filter { it.isDebit }
        val grouped = debits.groupBy { FinanceRepository.normalizeMerchantKey(it.merchant) }
            .filterKeys { it.isNotBlank() }

        val existingByKey = existingRecurring.associateBy {
            it.merchantNormalized.ifBlank { FinanceRepository.normalizeMerchantKey(it.title) }
        }.toMutableMap()

        val resultMap = LinkedHashMap<String, RecurringExpenseEntity>()
        // Preserve existing user-managed items first
        for (item in existingRecurring) {
            val key = item.merchantNormalized.ifBlank { FinanceRepository.normalizeMerchantKey(item.title) }
            resultMap[key] = evaluateAlerts(item.copy(merchantNormalized = key), nowMillis)
        }

        var newlyDetected = 0

        for ((normKey, txns) in grouped) {
            val sorted = txns.sortedByDescending { it.date }
            val latest = sorted.first()
            val isKnownSub = KNOWN_SUBSCRIPTION_KEYWORDS.any { normKey.contains(it) } ||
                latest.category.equals("Subscriptions", ignoreCase = true) ||
                latest.isRecurring

            // Check interval cadence if multiple transactions exist
            var hasRecurringCadence = false
            if (sorted.size >= 2) {
                val gapsDays = sorted.zipWithNext { a, b -> abs(a.date - b.date) / dayMs }
                // Monthly (20-40d) or Weekly (6-9d) cadence
                hasRecurringCadence = gapsDays.any { it in 20L..40L || it in 6L..9L }
            }

            if (!isKnownSub && !hasRecurringCadence) continue

            val previousTxn = sorted.drop(1).firstOrNull { abs(latest.date - it.date) >= 5 * dayMs }
            val previousAmt = previousTxn?.amount ?: existingByKey[normKey]?.previousAmount
            val freq = if (sorted.size >= 2 && abs(sorted[0].date - sorted[1].date) / dayMs in 6L..10L) {
                "Weekly"
            } else {
                "Monthly"
            }
            val nextDueMillis = latest.date + (if (freq == "Weekly") 7L else 30L) * dayMs
            val existing = resultMap[normKey]

            if (existing != null) {
                if (existing.status == "CANCELLED") continue
                val updatedPrev = when {
                    previousTxn != null && abs(latest.amount - previousTxn.amount) >= 1.0 -> previousTxn.amount
                    abs(latest.amount - existing.amount) >= 1.0 -> existing.amount
                    else -> existing.previousAmount
                }
                val merged = existing.copy(
                    amount = latest.amount,
                    previousAmount = updatedPrev,
                    lastChargedDate = maxOf(existing.lastChargedDate, latest.date),
                    nextDueDate = dueDateFmt.format(Date(nextDueMillis)),
                    frequency = freq
                )
                resultMap[normKey] = evaluateAlerts(merged, nowMillis)
            } else {
                newlyDetected++
                val isDiscretionary = DISCRETIONARY_UNUSED_CANDIDATES.any { normKey.contains(it) }
                // If auto-detected subscription hasn't been interacted with, initialize lastUsedDate
                val initialLastUsed = if (isDiscretionary && latest.date < nowMillis - 32 * dayMs) {
                    latest.date
                } else {
                    latest.date
                }
                val created = RecurringExpenseEntity(
                    title = latest.merchant,
                    amount = latest.amount,
                    category = if (isKnownSub && latest.category == "Miscellaneous") "Subscriptions" else latest.category,
                    frequency = freq,
                    nextDueDate = dueDateFmt.format(Date(nextDueMillis)),
                    isConfirmed = true,
                    merchantNormalized = normKey,
                    previousAmount = previousAmt,
                    lastChargedDate = latest.date,
                    lastUsedDate = initialLastUsed,
                    status = "ACTIVE",
                    alertMessage = "",
                    isAutoDetected = true
                )
                resultMap[normKey] = evaluateAlerts(created, nowMillis)
            }
        }

        val finalList = resultMap.values.sortedWith(
            compareByDescending<RecurringExpenseEntity> { it.hasAlert }
                .thenByDescending { it.status != "CANCELLED" }
                .thenByDescending { it.amount }
        )

        val activeItems = finalList.filter { it.status != "CANCELLED" }
        val priceAlerts = activeItems.count { it.status == "PRICE_CHANGED" }
        val unusedAlerts = activeItems.count { it.status == "UNUSED" }
        val unusedMonthly = activeItems.filter { it.status == "UNUSED" }.sumOf { it.amount }

        return finalList to RecurringScanSummary(
            detectedCount = newlyDetected,
            priceChangeAlerts = priceAlerts,
            unusedAlerts = unusedAlerts,
            totalMonthlyRecurring = activeItems.sumOf { if (it.frequency == "Weekly") it.amount * 4.0 else it.amount },
            potentialAnnualSavingsFromUnused = unusedMonthly * 12.0
        )
    }

    fun evaluateAlerts(item: RecurringExpenseEntity, nowMillis: Long = System.currentTimeMillis()): RecurringExpenseEntity {
        if (item.status == "CANCELLED") return item
        val dayMs = 24L * 60 * 60 * 1000

        // 1. Check for price change (>= ₹10 or >= 3% change)
        val prev = item.previousAmount
        if (prev != null && prev > 0.0) {
            val delta = item.amount - prev
            val pct = ((delta / prev) * 100.0).toInt()
            if (abs(delta) >= 10.0 && abs(pct) >= 3) {
                val direction = if (delta > 0) "increased" else "dropped"
                val sign = if (delta > 0) "+" else "-"
                return item.copy(
                    status = "PRICE_CHANGED",
                    alertMessage = "Price $direction $sign₹${abs(delta).toLong()} ($sign${abs(pct)}%) from ₹${prev.toLong()} → ₹${item.amount.toLong()}/mo"
                )
            }
        }

        // 2. Check for unused subscription (>30 days since last recorded usage)
        val daysSinceUsed = if (item.lastUsedDate > 0L) ((nowMillis - item.lastUsedDate) / dayMs).toInt() else 0
        if (daysSinceUsed >= 30) {
            val annualSave = (item.amount * 12).toLong()
            return item.copy(
                status = "UNUSED",
                alertMessage = "Unused for $daysSinceUsed days — pausing or cancelling saves ₹$annualSave/yr"
            )
        }

        return item.copy(status = "ACTIVE", alertMessage = "")
    }
}
