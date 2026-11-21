package com.example.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.ai.CategorizationEngine
import com.example.ai.CategorizationSource
import com.example.data.db.AppDatabase
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val database = AppDatabase.getInstance(context)
        val repository = FinanceRepository(database)
        val categorizationEngine = CategorizationEngine(repository)

        // Process in background coroutine
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profile = repository.getUserProfileSnapshot()
                if (profile?.isSmsScanningEnabled == false) {
                    return@launch
                }

                val fullBody = StringBuilder()
                var sender = ""
                for (sms in messages) {
                    fullBody.append(sms.messageBody)
                    sender = sms.originatingAddress ?: sender
                }

                val parsed = SmsTransactionParser.parse(fullBody.toString(), sender)
                if (parsed != null) {
                    val activeRules = repository.getActiveRulesSnapshot()
                    val catResult = categorizationEngine.categorize(
                        receiver = parsed.merchantOrParty,
                        account = parsed.accountRef,
                        upiId = parsed.upiId,
                        type = parsed.type,
                        activeRules = activeRules
                    )

                    val notesText = buildString {
                        append("Auto-detected from SMS")
                        if (parsed.txnRef.isNotBlank()) append(" • Ref: ${parsed.txnRef}")
                        if (parsed.balance != null) append(" • Bal: ₹${parsed.balance.toLong()}")
                        if (parsed.bankName.isNotBlank()) append(" • ${parsed.bankName}")
                    }

                    val transaction = TransactionEntity(
                        amount = parsed.amount,
                        type = parsed.type,
                        merchant = parsed.merchantOrParty,
                        category = catResult.category,
                        date = parsed.timestamp,
                        paymentMethod = parsed.paymentMethod,
                        accountRef = parsed.accountRef,
                        upiId = parsed.upiId,
                        txnRef = parsed.txnRef,
                        notes = notesText,
                        isAiCategorized = (catResult.source == CategorizationSource.AI_HEURISTIC || catResult.source == CategorizationSource.GEMINI_API),
                        categorizedByRule = (catResult.source == CategorizationSource.ACCOUNT_RULE),
                        dateStr = parsed.date,
                        timeStr = parsed.time,
                        rawSms = parsed.rawBody
                    )

                    repository.insertTransaction(transaction)

                    // Post notification
                    val formattedAmount = "₹" + parsed.amount.toLong()
                    val title = if (parsed.type.equals("Credit", ignoreCase = true)) "Money Received: $formattedAmount" else "Expense Detected: $formattedAmount"
                    val msg = "${parsed.merchantOrParty} • ${catResult.category}${if (catResult.source == CategorizationSource.ACCOUNT_RULE) " (Account Rule applied)" else ""}"
                    NotificationHelper.showTransactionNotification(context, title, msg)
                }
            } catch (e: Exception) {
                // Gracefully ignore parsing errors
            } finally {
                pendingResult.finish()
            }
        }
    }
}
