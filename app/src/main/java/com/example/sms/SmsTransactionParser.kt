package com.example.sms

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

data class ParsedSmsTransaction(
    val type: String, // "Debit" or "Credit"
    val amount: Double,
    val account: String, // e.g. "X1234", "2405"
    val date: String, // e.g. "19Sep26", "19September26"
    val time: String, // e.g. "08:15 PM"
    val mode: String, // e.g. "UPI", "CARD"
    val receiver: String, // e.g. "Rahul Kumar", "Zomato"
    val timestamp: Long,
    val rawSender: String = "",
    val rawBody: String = "",
    val upiId: String = "",
    val txnRef: String = "",
    val balance: Double? = null,
    val bankName: String = "",
    val emergencyNumber: String = ""
) {
    // Backward compatibility helpers
    val merchantOrParty: String get() = receiver
    val accountRef: String get() = account
    val paymentMethod: String get() = mode
}

object SmsTransactionParser {

    // Keywords strictly required to be ignored per requirements:
    // OTP, Offer, Cashback, Failed, Reversed, Pending, Advertisement
    private val IGNORED_KEYWORDS_PATTERN = Pattern.compile(
        "(?i)\\b(otp|offer|cashback|failed|reversed|pending|advertisement|one[ -]?time[ -]?password|verification code|security code|login code|do not share|cvv|pin|declined|unsuccessful|cancelled)\\b"
    )

    // Full structured bank SMS pattern with Ref, Bal & Emergency contact:
    // Debited Rs <amount> from a/c2405 on <date ddMonthNameyy> <time> via UPI to <reciever name> Ref <12digit number>.Bal Rs <Balanace amount>. Not you?Call<Emergency Number>- <Bank Name>
    // e.g. Debited Rs 350 from a/c2405 on 19Sep26 08:15 PM via UPI to Rahul Kumar Ref 429810392810.Bal Rs 5,420.00. Not you?Call1800120120- HDFC Bank
    private val STRUCTURED_DEBIT_WITH_REF_BAL_PATTERN = Pattern.compile(
        "(?i)debited\\s+(?:rs\\.?|inr|₹)?\\s*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)\\s+from\\s+a\\/c\\s*([A-Za-z0-9*]+)\\s+on\\s+([0-9]{1,2}\\s*[- ]?[A-Za-z]{3,12}\\s*[- ]?[0-9]{2,4})\\s+([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?(?:\\s*(?:AM|PM|am|pm))?)\\s+via\\s+([A-Za-z0-9]+)\\s+to\\s+(.+?)\\s+Ref\\s*([0-9A-Za-z]{6,18})\\s*\\.?\\s*Bal\\s*(?:rs\\.?|inr|₹)?\\s*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)(?:\\s*\\.?\\s*Not\\s*you\\?\\s*Call\\s*([0-9+\\- ]+)?(?:\\s*-\\s*(.+?))?)?$"
    )

    // Primary structured bank SMS pattern:
    // Debited Rs <amount> from a/c X<account_number> on <date> <time> via <transaction_mode> to <receiver_name>
    // e.g. Debited Rs 350 from a/c X1234 on 19Sep26 08:15 PM via UPI to Rahul Kumar
    private val STRUCTURED_DEBIT_PATTERN = Pattern.compile(
        "(?i)debited\\s+(?:rs\\.?|inr|₹)?\\s*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)\\s+from\\s+a\\/c\\s*([A-Za-z0-9*]+)\\s+on\\s+([0-9]{1,2}\\s*[- ]?[A-Za-z]{3,12}\\s*[- ]?[0-9]{2,4})\\s+([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?(?:\\s*(?:AM|PM|am|pm))?)\\s+via\\s+([A-Za-z0-9]+)\\s+to\\s+(.+)"
    )

    // Short format pattern:
    // Debited Rs <amount> via <mode> to <receiver_name>
    // e.g. Debited Rs 250 via UPI to Swiggy
    private val SHORT_DEBIT_PATTERN = Pattern.compile(
        "(?i)debited\\s+(?:rs\\.?|inr|₹)?\\s*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)\\s+via\\s+([A-Za-z0-9]+)\\s+to\\s+(.+)"
    )

    // Direct UPI Payment patterns:
    // e.g. Paid Rs 180 to Campus Canteen via UPI
    // e.g. Sent Rs 75 to Chai Point via UPI
    private val DIRECT_UPI_PAID_PATTERN = Pattern.compile(
        "(?i)(?:paid|sent)\\s+(?:rs\\.?|inr|₹)?\\s*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)\\s+to\\s+([A-Za-z0-9\\s&.'-]+?)\\s+(?:via|using|through)\\s+upi(?:\\s+ref.*)?$"
    )

    // UPI Txn pattern:
    // e.g. UPI txn of Rs 120.00 to Nescafe Stall
    private val UPI_TXN_OF_PATTERN = Pattern.compile(
        "(?i)upi\\s+txn(?:\\s+of)?\\s+(?:rs\\.?|inr|₹)?\\s*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)\\s+(?:to|towards)\\s+([A-Za-z0-9\\s&.'-]+?)(?:\\s+on|\\s+ref|$)"
    )

    // General patterns for other standard Indian bank notifications
    private val GENERAL_AMOUNT_PATTERN = Pattern.compile(
        "(?i)(?:rs\\.?|inr|₹)\\s*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]{1,2})?)"
    )

    private val GENERAL_ACCOUNT_PATTERN = Pattern.compile(
        "(?i)(?:a/c|acct|account|card)\\s*(?:no\\.?)?\\s*(?:ending(?:\\s+with)?)?\\s*[:#]?\\s*([xX*]*[0-9]{3,6})"
    )

    private val UPI_VPA_PATTERN = Pattern.compile(
        "(?i)\\b([a-zA-Z0-9._-]+@[a-zA-Z0-9]+)\\b"
    )

    private val REF_NO_PATTERN = Pattern.compile(
        "(?i)(?:ref(?:\\s*no)?|utr|txn(?:\\s*id)?|reference|rrn)\\s*[:\\s#-]?\\s*([A-Za-z0-9]{6,16})"
    )

    private val TO_RECEIVER_PATTERN = Pattern.compile(
        "(?i)(?:to|towards|at|for\\s+upi\\s+transaction\\s+to|paid\\s+to)\\s+([A-Za-z0-9][A-Za-z0-9\\s&.'-]{1,30}?)(?=\\s+(?:on|ref|via|through|using|bal|balance|avl|a/c|utr|credited|debited|is|dated|\\.)|$)"
    )

    private val FROM_SENDER_PATTERN = Pattern.compile(
        "(?i)(?:from|by|transferred\\s+from)\\s+([A-Za-z0-9][A-Za-z0-9\\s&.'-]{1,30}?)(?=\\s+(?:on|ref|via|through|using|bal|balance|avl|a/c|utr|is|dated|\\.)|$)"
    )

    fun isIgnoredMessage(body: String): Boolean {
        return IGNORED_KEYWORDS_PATTERN.matcher(body).find()
    }

    fun isFinancialMessage(body: String, sender: String = ""): Boolean {
        if (isIgnoredMessage(body)) return false

        val lower = body.lowercase()
        val hasDebitCredit = lower.contains("debited") || lower.contains("credited") ||
                lower.contains("spent") || lower.contains("paid") || lower.contains("received") ||
                lower.contains("transferred")
        val hasCurrency = lower.contains("rs") || lower.contains("inr") || lower.contains("₹")
        return hasDebitCredit && hasCurrency
    }

    fun parse(body: String, sender: String = ""): ParsedSmsTransaction? {
        val trimmedBody = body.trim()

        // 1. Strict filtering of ignored keywords: OTP, Offer, Cashback, Failed, Reversed, Pending, Advertisement
        if (isIgnoredMessage(trimmedBody)) {
            return null
        }

        // 2. Try Structured Bank SMS Pattern with Ref, Bal & Emergency Call
        // e.g. "Debited Rs 350 from a/c2405 on 19Sep26 08:15 PM via UPI to Rahul Kumar Ref 429810392810.Bal Rs 5,420.00. Not you?Call1800120120- HDFC Bank"
        val refBalMatcher = STRUCTURED_DEBIT_WITH_REF_BAL_PATTERN.matcher(trimmedBody)
        if (refBalMatcher.find()) {
            val amountStr = refBalMatcher.group(1)?.replace(",", "") ?: "0"
            val account = refBalMatcher.group(2)?.trim() ?: ""
            val dateStr = refBalMatcher.group(3)?.trim() ?: ""
            val timeStr = refBalMatcher.group(4)?.trim() ?: ""
            val mode = refBalMatcher.group(5)?.trim()?.uppercase() ?: "UPI"
            val receiverRaw = refBalMatcher.group(6)?.trim() ?: ""
            val refNo = refBalMatcher.group(7)?.trim() ?: ""
            val balStr = refBalMatcher.group(8)?.replace(",", "")?.trim()
            val emergencyNum = refBalMatcher.group(9)?.trim() ?: ""
            val bankNameRaw = refBalMatcher.group(10)?.trim() ?: ""

            val amount = amountStr.toDoubleOrNull() ?: 0.0
            val balance = balStr?.toDoubleOrNull()
            val receiver = cleanReceiverName(receiverRaw)
            val timestamp = parseDateTimeToMillis(dateStr, timeStr)
            val bank = bankNameRaw.ifBlank { extractBankName(sender) }

            return ParsedSmsTransaction(
                type = "Debit",
                amount = amount,
                account = account,
                date = dateStr,
                time = timeStr,
                mode = mode,
                receiver = receiver,
                timestamp = timestamp,
                rawSender = sender.ifBlank { bank },
                rawBody = maskRawSms(trimmedBody),
                txnRef = refNo,
                balance = balance,
                bankName = bank,
                emergencyNumber = emergencyNum
            )
        }

        // 3. Try Standard Structured Bank SMS Pattern
        val structuredMatcher = STRUCTURED_DEBIT_PATTERN.matcher(trimmedBody)
        if (structuredMatcher.find()) {
            val amountStr = structuredMatcher.group(1)?.replace(",", "") ?: "0"
            val account = structuredMatcher.group(2)?.trim() ?: ""
            val dateStr = structuredMatcher.group(3)?.trim() ?: ""
            val timeStr = structuredMatcher.group(4)?.trim() ?: ""
            val mode = structuredMatcher.group(5)?.trim()?.uppercase() ?: "UPI"
            val receiverRaw = structuredMatcher.group(6)?.trim() ?: ""

            val amount = amountStr.toDoubleOrNull() ?: 0.0
            val receiver = cleanReceiverName(receiverRaw)
            val timestamp = parseDateTimeToMillis(dateStr, timeStr)

            // Extract optional ref or bal if present in receiverRaw or body
            val refMatcher = REF_NO_PATTERN.matcher(trimmedBody)
            val refNo = if (refMatcher.find()) refMatcher.group(1) ?: "" else ""

            return ParsedSmsTransaction(
                type = "Debit",
                amount = amount,
                account = account,
                date = dateStr,
                time = timeStr,
                mode = mode,
                receiver = receiver,
                timestamp = timestamp,
                rawSender = sender,
                rawBody = maskRawSms(trimmedBody),
                txnRef = refNo
            )
        }

        // 3. Try Short Debit Pattern: "Debited Rs <amount> via <mode> to <receiver>"
        val shortMatcher = SHORT_DEBIT_PATTERN.matcher(trimmedBody)
        if (shortMatcher.find()) {
            val amountStr = shortMatcher.group(1)?.replace(",", "") ?: "0"
            val mode = shortMatcher.group(2)?.trim()?.uppercase() ?: "UPI"
            val receiverRaw = shortMatcher.group(3)?.trim() ?: ""

            val amount = amountStr.toDoubleOrNull() ?: 0.0
            val receiver = cleanReceiverName(receiverRaw)
            val cal = Calendar.getInstance()
            val dateStr = SimpleDateFormat("ddMMMyy", Locale.ENGLISH).format(cal.time)
            val timeStr = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(cal.time)

            return ParsedSmsTransaction(
                type = "Debit",
                amount = amount,
                account = "Primary",
                date = dateStr,
                time = timeStr,
                mode = mode,
                receiver = receiver,
                timestamp = System.currentTimeMillis(),
                rawSender = sender,
                rawBody = maskRawSms(trimmedBody)
            )
        }

        // 4. Try Direct UPI Paid Pattern: "Paid/Sent Rs <amount> to <receiver> via UPI"
        val directUpiMatcher = DIRECT_UPI_PAID_PATTERN.matcher(trimmedBody)
        if (directUpiMatcher.find()) {
            val amountStr = directUpiMatcher.group(1)?.replace(",", "") ?: "0"
            val receiverRaw = directUpiMatcher.group(2)?.trim() ?: ""
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            val receiver = cleanReceiverName(receiverRaw)
            val cal = Calendar.getInstance()
            val dateStr = SimpleDateFormat("ddMMMyy", Locale.ENGLISH).format(cal.time)
            val timeStr = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(cal.time)

            return ParsedSmsTransaction(
                type = "Debit",
                amount = amount,
                account = "Primary",
                date = dateStr,
                time = timeStr,
                mode = "UPI",
                receiver = receiver,
                timestamp = System.currentTimeMillis(),
                rawSender = sender,
                rawBody = maskRawSms(trimmedBody)
            )
        }

        // 5. Try UPI Txn of pattern: "UPI txn of Rs <amount> to <receiver>"
        val upiTxnMatcher = UPI_TXN_OF_PATTERN.matcher(trimmedBody)
        if (upiTxnMatcher.find()) {
            val amountStr = upiTxnMatcher.group(1)?.replace(",", "") ?: "0"
            val receiverRaw = upiTxnMatcher.group(2)?.trim() ?: ""
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            val receiver = cleanReceiverName(receiverRaw)
            val cal = Calendar.getInstance()
            val dateStr = SimpleDateFormat("ddMMMyy", Locale.ENGLISH).format(cal.time)
            val timeStr = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(cal.time)

            return ParsedSmsTransaction(
                type = "Debit",
                amount = amount,
                account = "Primary",
                date = dateStr,
                time = timeStr,
                mode = "UPI",
                receiver = receiver,
                timestamp = System.currentTimeMillis(),
                rawSender = sender,
                rawBody = maskRawSms(trimmedBody)
            )
        }

        // 6. Generalized Bank Parsing (for other standard bank formats e.g. HDFC, ICICI, SBI, UPI)
        if (!isFinancialMessage(trimmedBody, sender)) {
            return null
        }

        val lowerBody = trimmedBody.lowercase()
        val isCredit = lowerBody.contains("credited") || lowerBody.contains("received") || lowerBody.contains("deposited")
        val isDebit = lowerBody.contains("debited") || lowerBody.contains("spent") || lowerBody.contains("paid") || lowerBody.contains("withdrawn")

        if (!isCredit && !isDebit) return null
        val type = if (isCredit) "Credit" else "Debit"

        // Amount
        val amtMatcher = GENERAL_AMOUNT_PATTERN.matcher(trimmedBody)
        if (!amtMatcher.find()) return null
        val amount = amtMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        // Account
        var account = ""
        val acctMatcher = GENERAL_ACCOUNT_PATTERN.matcher(trimmedBody)
        if (acctMatcher.find()) {
            account = acctMatcher.group(1)?.uppercase() ?: ""
        }

        // Mode
        val mode = when {
            lowerBody.contains("via upi") || lowerBody.contains("through upi") || lowerBody.contains("upi") -> "UPI"
            lowerBody.contains("via card") || lowerBody.contains("card") || lowerBody.contains("pos") -> "CARD"
            lowerBody.contains("neft") || lowerBody.contains("imps") || lowerBody.contains("rtgs") -> "BANK_TRANSFER"
            lowerBody.contains("wallet") -> "WALLET"
            else -> "UPI"
        }

        // Receiver / Source
        var receiver = ""
        if (type == "Debit") {
            val toMatcher = TO_RECEIVER_PATTERN.matcher(trimmedBody)
            if (toMatcher.find()) {
                receiver = cleanReceiverName(toMatcher.group(1) ?: "")
            }
        } else {
            val fromMatcher = FROM_SENDER_PATTERN.matcher(trimmedBody)
            if (fromMatcher.find()) {
                receiver = cleanReceiverName(fromMatcher.group(1) ?: "")
            }
        }

        // Fallbacks for receiver name
        if (receiver.isBlank()) {
            val upiMatcher = UPI_VPA_PATTERN.matcher(trimmedBody)
            if (upiMatcher.find()) {
                val vpa = upiMatcher.group(1) ?: ""
                receiver = vpa.substringBefore("@").replace(".", " ").capitalizeWords()
            } else if (sender.isNotBlank()) {
                receiver = extractBankName(sender)
            } else {
                receiver = if (type == "Credit") "Received Funds" else "Miscellaneous"
            }
        }

        val cal = Calendar.getInstance()
        val dateStr = SimpleDateFormat("ddMMMyy", Locale.ENGLISH).format(cal.time)
        val timeStr = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(cal.time)

        return ParsedSmsTransaction(
            type = type,
            amount = amount,
            account = account.ifBlank { "Primary" },
            date = dateStr,
            time = timeStr,
            mode = mode,
            receiver = receiver,
            timestamp = System.currentTimeMillis(),
            rawSender = sender,
            rawBody = maskRawSms(trimmedBody)
        )
    }

    fun cleanReceiverName(raw: String): String {
        // Remove trailing punctuation, ref tags, bal tags, not you tags
        var clean = raw.trim()
            .replace(Regex("(?i)^(vpa|upi|ref|acct|m/s|transfer to|payment to)\\s*"), "")
            .replace(Regex("(?i)\\s+(ref|bal|balance|avl|utr|dated|on|a/c|not\\s*you).*$"), "")
            .replace(Regex("[^a-zA-Z0-9\\s&.'-]"), "")
            .trim()

        if (clean.length > 30) {
            clean = clean.substring(0, 30).trim()
        }
        return clean.capitalizeWords()
    }

    fun parseDateTimeToMillis(dateStr: String, timeStr: String): Long {
        return try {
            val cleanDate = dateStr.trim()
            val cleanTime = timeStr.trim()
            val combined = "$cleanDate $cleanTime"

            // Try full format: e.g. "19Sep26 08:15 PM" or "19September26 08:15 PM"
            val formats = listOf(
                SimpleDateFormat("ddMMMyy hh:mm a", Locale.ENGLISH),
                SimpleDateFormat("ddMMMMyy hh:mm a", Locale.ENGLISH),
                SimpleDateFormat("ddMMMyyyy hh:mm a", Locale.ENGLISH),
                SimpleDateFormat("ddMMMMyyyy hh:mm a", Locale.ENGLISH),
                SimpleDateFormat("dd-MMM-yy hh:mm a", Locale.ENGLISH),
                SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.ENGLISH),
                SimpleDateFormat("dd-MMMM-yy hh:mm a", Locale.ENGLISH),
                SimpleDateFormat("dd-MMMM-yyyy hh:mm a", Locale.ENGLISH),
                SimpleDateFormat("dd MMM yy hh:mm a", Locale.ENGLISH),
                SimpleDateFormat("dd MMMM yy hh:mm a", Locale.ENGLISH),
                SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.ENGLISH),
                SimpleDateFormat("dd MMMM yyyy hh:mm a", Locale.ENGLISH),
                SimpleDateFormat("ddMMMyy HH:mm", Locale.ENGLISH),
                SimpleDateFormat("ddMMMMyy HH:mm", Locale.ENGLISH),
                SimpleDateFormat("ddMMMyy", Locale.ENGLISH),
                SimpleDateFormat("ddMMMMyy", Locale.ENGLISH)
            )

            for (fmt in formats) {
                try {
                    val parsed = fmt.parse(combined)
                    if (parsed != null) return parsed.time
                } catch (_: Exception) {}
            }

            // Fallback: parse date only
            val dateFmt = SimpleDateFormat("ddMMMyy", Locale.ENGLISH)
            val parsedDate = dateFmt.parse(cleanDate)
            parsedDate?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    fun maskAccount(account: String): String {
        if (account.isBlank()) return ""
        val digits = account.filter { it.isDigit() }
        return if (digits.length >= 4) {
            "XXXX" + digits.takeLast(4)
        } else {
            "XXXX$digits"
        }
    }

    fun maskRawSms(text: String): String {
        return text.replace(Regex("\\b\\d{4,16}\\b")) { match ->
            val v = match.value
            if (v.length <= 4) v else "X".repeat(v.length - 4) + v.takeLast(4)
        }
    }

    private fun extractBankName(sender: String): String {
        val s = sender.uppercase()
        return when {
            s.contains("HDFC") -> "HDFC Bank"
            s.contains("SBI") -> "State Bank of India"
            s.contains("ICICI") -> "ICICI Bank"
            s.contains("AXIS") -> "Axis Bank"
            s.contains("KOTAK") -> "Kotak Bank"
            s.contains("PAYTM") -> "Paytm Bank"
            s.contains("PNB") -> "Punjab National Bank"
            s.contains("BOB") -> "Bank of Baroda"
            else -> "Bank Transfer"
        }
    }

    private fun String.capitalizeWords(): String {
        return split(" ").filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}
