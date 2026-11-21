package com.example.sms

import android.content.Context
import android.net.Uri
import com.example.ai.CategorizationEngine
import com.example.ai.CategorizationSource
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SmsScanResult(
    val totalFound: Int,
    val newlyImported: Int,
    val upiCount: Int,
    val latestTransaction: TransactionEntity? = null
)

class SmsInboxReader(
    private val context: Context,
    private val repository: FinanceRepository,
    private val categorizationEngine: CategorizationEngine
) {

    suspend fun scanRecentSms(limit: Int = 150): SmsScanResult = withContext(Dispatchers.IO) {
        var importedCount = 0
        var totalFound = 0
        var upiCount = 0
        var latestTxn: TransactionEntity? = null

        try {
            // Try standard inbox URI, fallback to content://sms if needed
            var cursor = context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("_id", "address", "body", "date"),
                null,
                null,
                "date DESC LIMIT $limit"
            )

            if (cursor == null || cursor.count == 0) {
                cursor?.close()
                cursor = context.contentResolver.query(
                    Uri.parse("content://sms"),
                    arrayOf("_id", "address", "body", "date"),
                    "type = 1", // Inbox type in Android Telephony
                    null,
                    "date DESC LIMIT $limit"
                )
            }

            cursor?.use {
                val addressIdx = it.getColumnIndex("address")
                val bodyIdx = it.getColumnIndex("body")
                val dateIdx = it.getColumnIndex("date")

                val activeRules = repository.getActiveRulesSnapshot()
                val existingTransactions = repository.getTransactionsSnapshot().toMutableList()

                while (it.moveToNext()) {
                    val address = if (addressIdx != -1) it.getString(addressIdx) ?: "" else ""
                    val body = if (bodyIdx != -1) it.getString(bodyIdx) ?: "" else ""
                    val smsDate = if (dateIdx != -1) it.getLong(dateIdx) else System.currentTimeMillis()

                    val parsed = SmsTransactionParser.parse(body, address)
                    if (parsed != null) {
                        totalFound++
                        val isUpi = parsed.mode.equals("UPI", ignoreCase = true) ||
                                body.contains("upi", ignoreCase = true) ||
                                parsed.receiver.contains("upi", ignoreCase = true)
                        if (isUpi) upiCount++

                        val txnTime = if (parsed.timestamp > 0) parsed.timestamp else smsDate

                        // Duplicate detection: check same amount, merchant, and timestamp proximity (within 3 mins) or matching txnRef
                        val isDuplicate = existingTransactions.any { existing ->
                            existing.amount == parsed.amount &&
                                    existing.merchant.equals(parsed.receiver, ignoreCase = true) &&
                                    (Math.abs(existing.date - txnTime) < 180_000 ||
                                            (existing.txnRef.isNotBlank() && existing.txnRef == parsed.txnRef))
                        }

                        if (!isDuplicate) {
                            val catResult = categorizationEngine.categorize(
                                receiver = parsed.receiver,
                                account = parsed.account,
                                upiId = parsed.upiId,
                                type = parsed.type,
                                activeRules = activeRules
                            )

                            val notesText = buildString {
                                append(when (catResult.source) {
                                    CategorizationSource.RECEIVER_RULE -> "Permanent Rule (${catResult.matchedRule?.accountIdentifier})"
                                    CategorizationSource.ACCOUNT_RULE -> "Account Rule (${catResult.matchedRule?.accountIdentifier})"
                                    CategorizationSource.USER_LEARNED -> "Learned Mapping"
                                    CategorizationSource.RECEIVER_DICTIONARY -> "Categorized by Receiver"
                                    CategorizationSource.AI_HEURISTIC -> "AI Heuristic (${catResult.category})"
                                    else -> "Imported from Device SMS"
                                })
                                if (parsed.txnRef.isNotBlank()) append(" • Ref: ${parsed.txnRef}")
                                if (parsed.balance != null) append(" • Bal: ₹${parsed.balance.toLong()}")
                                if (parsed.bankName.isNotBlank()) append(" • ${parsed.bankName}")
                            }

                            val transaction = TransactionEntity(
                                amount = parsed.amount,
                                type = parsed.type,
                                merchant = parsed.receiver,
                                category = catResult.category,
                                date = txnTime,
                                paymentMethod = if (isUpi) "UPI" else parsed.mode,
                                accountRef = parsed.account.ifBlank { "Primary" },
                                upiId = parsed.upiId,
                                txnRef = parsed.txnRef,
                                notes = notesText,
                                isAiCategorized = (catResult.source == CategorizationSource.AI_HEURISTIC || catResult.source == CategorizationSource.GEMINI_API),
                                categorizedByRule = (catResult.source == CategorizationSource.ACCOUNT_RULE || catResult.source == CategorizationSource.RECEIVER_RULE),
                                dateStr = parsed.date,
                                timeStr = parsed.time,
                                rawSms = parsed.rawBody
                            )

                            repository.insertTransaction(transaction)
                            existingTransactions.add(transaction)
                            importedCount++
                            if (latestTxn == null) {
                                latestTxn = transaction
                            }
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted
        } catch (e: Exception) {
            // Other content resolver errors
        }

        SmsScanResult(
            totalFound = totalFound,
            newlyImported = importedCount,
            upiCount = upiCount,
            latestTransaction = latestTxn
        )
    }
}
