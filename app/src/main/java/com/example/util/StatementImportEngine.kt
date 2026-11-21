package com.example.util

import android.content.Context
import android.net.Uri
import com.example.ai.CategorizationEngine
import com.example.ai.CategorizationSource
import com.example.data.model.AccountRuleEntity
import com.example.data.model.TransactionEntity
import com.example.sms.SmsTransactionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.Inflater
import kotlin.math.abs

enum class StatementSourceFormat(val label: String) {
    PDF("PDF Statement"),
    CSV("CSV Statement"),
    SMS("Bulk SMS / Text")
}

data class ImportedTransactionDraft(
    val id: String = UUID.randomUUID().toString(),
    val dateMillis: Long,
    val dateDisplay: String,
    val rawNarration: String,
    val cleanedMerchant: String,
    val amount: Double,
    val type: String, // "DEBIT" or "CREDIT"
    val paymentMethod: String,
    val accountRef: String = "",
    val upiId: String = "",
    val txnRef: String = "",
    val category: String,
    val categorizationSource: CategorizationSource,
    val sourceFormat: StatementSourceFormat,
    val isDuplicate: Boolean = false,
    val isSelected: Boolean = true,
    val userCorrected: Boolean = false
)

data class StatementImportPreview(
    val sourceFormat: StatementSourceFormat,
    val fileName: String,
    val drafts: List<ImportedTransactionDraft>,
    val rawLinesProcessed: Int,
    val duplicateCount: Int,
    val warningMessage: String? = null
)

object StatementImportEngine {

    private val displayDateFmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
    private val shortDateFmt = SimpleDateFormat("ddMMMyy", Locale.ENGLISH)

    private val DATE_FORMATS = listOf(
        "dd/MM/yyyy", "dd-MM-yyyy", "dd/MM/yy", "dd-MM-yy",
        "yyyy-MM-dd", "yyyy/MM/dd",
        "dd MMM yyyy", "dd-MMM-yyyy", "dd MMM yy", "dd-MMM-yy",
        "ddMMMyy", "ddMMMyyyy",
        "MM/dd/yyyy", "MM-dd-yyyy", "MMM dd, yyyy", "dd.MM.yyyy"
    )

    private val DATE_REGEX = Regex(
        """(\b\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}\b|\b\d{4}[/\-.]\d{1,2}[/\-.]\d{1,2}\b|\b\d{1,2}[\s\-]?[A-Za-z]{3,9}[\s\-]?\d{2,4}\b)"""
    )

    private val AMOUNT_REGEX = Regex(
        """(?:(?:rs\.?|inr|₹|\$)\s*)?(\d{1,3}(?:,\d{2,3})*(?:\.\d{1,2})|\d+(?:\.\d{1,2}))""",
        RegexOption.IGNORE_CASE
    )

    private val UPI_ID_REGEX = Regex("""\b([a-zA-Z0-9._-]+@[a-zA-Z0-9]+)\b""")
    private val REF_REGEX = Regex("""\b(\d{9,16})\b""")

    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        format: StatementSourceFormat,
        fileName: String,
        categorizationEngine: CategorizationEngine,
        activeRules: List<AccountRuleEntity>,
        existingTransactions: List<TransactionEntity>
    ): StatementImportPreview = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext StatementImportPreview(
                sourceFormat = format,
                fileName = fileName,
                drafts = emptyList(),
                rawLinesProcessed = 0,
                duplicateCount = 0,
                warningMessage = "Could not read selected file."
            )

        when (format) {
            StatementSourceFormat.PDF -> {
                val extractedText = extractTextFromPdfBytes(bytes)
                if (extractedText.isBlank()) {
                    StatementImportPreview(
                        sourceFormat = format,
                        fileName = fileName,
                        drafts = emptyList(),
                        rawLinesProcessed = 0,
                        duplicateCount = 0,
                        warningMessage = "This PDF appears to be an image-only scan or encrypted file. Try exporting a text-based PDF/CSV from your bank or paste text lines."
                    )
                } else {
                    parseMessyTextStatement(
                        rawText = extractedText,
                        format = StatementSourceFormat.PDF,
                        fileName = fileName,
                        categorizationEngine = categorizationEngine,
                        activeRules = activeRules,
                        existingTransactions = existingTransactions
                    )
                }
            }
            StatementSourceFormat.CSV -> {
                val rawText = bytes.toString(Charsets.UTF_8)
                parseMessyCsv(
                    rawCsv = rawText,
                    fileName = fileName,
                    categorizationEngine = categorizationEngine,
                    activeRules = activeRules,
                    existingTransactions = existingTransactions
                )
            }
            StatementSourceFormat.SMS -> {
                val rawText = bytes.toString(Charsets.UTF_8)
                parseBulkSmsOrText(
                    rawText = rawText,
                    fileName = fileName,
                    categorizationEngine = categorizationEngine,
                    activeRules = activeRules,
                    existingTransactions = existingTransactions
                )
            }
        }
    }

    suspend fun parseMessyCsv(
        rawCsv: String,
        fileName: String,
        categorizationEngine: CategorizationEngine,
        activeRules: List<AccountRuleEntity>,
        existingTransactions: List<TransactionEntity>
    ): StatementImportPreview = withContext(Dispatchers.Default) {
        val lines = rawCsv.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return@withContext StatementImportPreview(
                StatementSourceFormat.CSV, fileName, emptyList(), 0, 0, "Empty CSV content."
            )
        }

        // Detect delimiter across lines (comma, semicolon, tab, or pipe)
        val delimiter = detectDelimiter(lines)

        // Find header line (banks often include 3-12 preamble metadata lines before actual CSV table)
        var headerIndex = -1
        var dateCol = -1
        var descCol = -1
        var debitCol = -1
        var creditCol = -1
        var amountCol = -1
        var typeCol = -1
        var refCol = -1

        for ((idx, line) in lines.withIndex()) {
            val cols = splitDelimitedLine(line, delimiter).map { it.lowercase(Locale.ENGLISH).trim() }
            if (cols.size < 3) continue
            val hasDate = cols.any { it.contains("date") || it.contains("txn dt") || it == "dt" }
            val hasDesc = cols.any {
                it.contains("narration") || it.contains("description") || it.contains("particular") ||
                    it.contains("merchant") || it.contains("remark") || it.contains("details") || it.contains("payee")
            }
            val hasMoney = cols.any {
                it.contains("amount") || it.contains("debit") || it.contains("withdrawal") ||
                    it.contains("credit") || it.contains("deposit") || it.contains("dr") || it.contains("cr")
            }
            if (hasDate && (hasDesc || hasMoney)) {
                headerIndex = idx
                dateCol = cols.indexOfFirst { it.contains("date") || it.contains("dt") }
                descCol = cols.indexOfFirst {
                    it.contains("narration") || it.contains("description") || it.contains("particular") ||
                        it.contains("merchant") || it.contains("remark") || it.contains("details") || it.contains("payee")
                }
                debitCol = cols.indexOfFirst {
                    it.contains("withdrawal") || it.contains("debit") || it == "dr" || it.contains("dr amt") || it.contains("paid out")
                }
                creditCol = cols.indexOfFirst {
                    it.contains("deposit") || it.contains("credit") || it == "cr" || it.contains("cr amt") || it.contains("paid in")
                }
                amountCol = cols.indexOfFirst {
                    it.contains("amount") && !it.contains("balance")
                }
                typeCol = cols.indexOfFirst {
                    it == "type" || it.contains("dr/cr") || it.contains("cr/dr") || it.contains("txn type")
                }
                refCol = cols.indexOfFirst {
                    it.contains("ref") || it.contains("chq") || it.contains("utr")
                }
                break
            }
        }

        val drafts = mutableListOf<ImportedTransactionDraft>()
        val startIdx = if (headerIndex >= 0) headerIndex + 1 else 0

        for (i in startIdx until lines.size) {
            val cols = splitDelimitedLine(lines[i], delimiter)
            if (cols.size < 2) continue

            // Skip footer / summary rows
            val joinedLower = lines[i].lowercase(Locale.ENGLISH)
            if (joinedLower.contains("opening balance") || joinedLower.contains("closing balance") ||
                joinedLower.contains("statement summary") || joinedLower.startsWith("total")
            ) continue

            val rawDate = cols.getOrNull(dateCol)?.takeIf { dateCol >= 0 }
                ?: cols.firstOrNull { DATE_REGEX.containsMatchIn(it) }
                ?: continue
            val parsedMillis = parseFlexibleDate(rawDate) ?: continue

            val rawDesc = cols.getOrNull(descCol)?.takeIf { descCol >= 0 && it.isNotBlank() }
                ?: cols.filterIndexed { cIdx, v ->
                    cIdx != dateCol && cIdx != debitCol && cIdx != creditCol && cIdx != amountCol &&
                        v.length >= 3 && parseAmountToken(v) == null
                }.maxByOrNull { it.length }
                ?: "Bank Transaction"

            var amount = 0.0
            var type = "DEBIT"

            if (debitCol >= 0 || creditCol >= 0) {
                val debitAmt = cols.getOrNull(debitCol)?.let { parseAmountToken(it) } ?: 0.0
                val creditAmt = cols.getOrNull(creditCol)?.let { parseAmountToken(it) } ?: 0.0
                if (creditAmt > 0.0 && debitAmt == 0.0) {
                    amount = creditAmt
                    type = "CREDIT"
                } else if (debitAmt > 0.0) {
                    amount = debitAmt
                    type = "DEBIT"
                }
            }

            if (amount <= 0.0 && amountCol >= 0) {
                val rawAmtCell = cols.getOrNull(amountCol) ?: ""
                val parsedAmt = parseSignedAmount(rawAmtCell)
                if (parsedAmt != null && abs(parsedAmt) > 0.0) {
                    amount = abs(parsedAmt)
                    val typeCell = cols.getOrNull(typeCol)?.lowercase(Locale.ENGLISH) ?: ""
                    type = when {
                        typeCell.contains("cr") || typeCell.contains("credit") || typeCell.contains("dep") -> "CREDIT"
                        typeCell.contains("dr") || typeCell.contains("debit") || typeCell.contains("wd") -> "DEBIT"
                        rawAmtCell.contains("cr", ignoreCase = true) -> "CREDIT"
                        rawAmtCell.contains("dr", ignoreCase = true) -> "DEBIT"
                        parsedAmt < 0 -> "DEBIT"
                        rawDesc.contains("/cr/", ignoreCase = true) || rawDesc.contains("salary", ignoreCase = true) ||
                            rawDesc.contains("refund", ignoreCase = true) || rawDesc.contains("allowance", ignoreCase = true) -> "CREDIT"
                        else -> "DEBIT"
                    }
                }
            }

            if (amount <= 0.0) {
                // Messy headerless CSV fallback: pick first numeric column after date
                val numericCols = cols.mapIndexedNotNull { idx, cell ->
                    if (idx == dateCol) null else parseSignedAmount(cell)?.let { idx to it }
                }
                val firstAmt = numericCols.firstOrNull { abs(it.second) > 0.0 } ?: continue
                amount = abs(firstAmt.second)
                type = if (firstAmt.second > 0 && (joinedLower.contains("cr") || joinedLower.contains("credit") || joinedLower.contains("deposit"))) {
                    "CREDIT"
                } else {
                    "DEBIT"
                }
            }

            val refVal = cols.getOrNull(refCol)?.trim().orEmpty()
                .ifBlank { REF_REGEX.find(rawDesc)?.groupValues?.getOrNull(1).orEmpty() }

            drafts.add(
                buildCategorizedDraft(
                    dateMillis = parsedMillis,
                    rawNarration = rawDesc,
                    amount = amount,
                    type = type,
                    txnRef = refVal,
                    format = StatementSourceFormat.CSV,
                    categorizationEngine = categorizationEngine,
                    activeRules = activeRules,
                    existingTransactions = existingTransactions
                )
            )
        }

        val dupCount = drafts.count { it.isDuplicate }
        StatementImportPreview(
            sourceFormat = StatementSourceFormat.CSV,
            fileName = fileName,
            drafts = drafts,
            rawLinesProcessed = lines.size,
            duplicateCount = dupCount,
            warningMessage = if (drafts.isEmpty()) "No valid transaction rows detected in CSV. Check that dates and amounts are present." else null
        )
    }

    suspend fun parseMessyTextStatement(
        rawText: String,
        format: StatementSourceFormat,
        fileName: String,
        categorizationEngine: CategorizationEngine,
        activeRules: List<AccountRuleEntity>,
        existingTransactions: List<TransactionEntity>
    ): StatementImportPreview = withContext(Dispatchers.Default) {
        val rawLines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        // Merge wrapped multi-line PDF narrations into logical rows starting with a date
        val logicalLines = mutableListOf<String>()
        for (line in rawLines) {
            val startsWithDate = DATE_REGEX.find(line)?.range?.first?.let { it <= 4 } == true
            if (startsWithDate || logicalLines.isEmpty()) {
                logicalLines.add(line)
            } else {
                // Append continuation line only if previous line doesn't already look complete with an amount
                val prev = logicalLines.last()
                if (!AMOUNT_REGEX.containsMatchIn(prev)) {
                    logicalLines[logicalLines.lastIndex] = "$prev $line"
                } else {
                    logicalLines.add(line)
                }
            }
        }

        val drafts = mutableListOf<ImportedTransactionDraft>()
        for (line in logicalLines) {
            val lower = line.lowercase(Locale.ENGLISH)
            if (lower.contains("opening balance") || lower.contains("closing balance") ||
                lower.contains("statement of account") || lower.contains("page ") ||
                lower.contains("date") && lower.contains("narration")
            ) continue

            val dateMatch = DATE_REGEX.find(line) ?: continue
            val parsedMillis = parseFlexibleDate(dateMatch.value) ?: continue

            val afterDate = line.substring(dateMatch.range.last + 1).trim()
            val amountMatches = AMOUNT_REGEX.findAll(afterDate).toList()
            if (amountMatches.isEmpty()) continue

            // In messy bank PDFs, a row often ends with: <Txn Amount> <Running Balance>
            // If 2+ decimal amounts appear at the tail, the penultimate is usually the txn amount and last is balance.
            val decimalMatches = amountMatches.filter { it.groupValues[1].contains(".") || it.value.contains("rs", true) || it.value.contains("₹") }
            val chosenMatch = when {
                decimalMatches.size >= 2 -> decimalMatches[decimalMatches.size - 2]
                decimalMatches.size == 1 -> decimalMatches.first()
                amountMatches.size >= 2 -> amountMatches[amountMatches.size - 2]
                else -> amountMatches.first()
            }

            val amount = chosenMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            if (amount <= 0.0 || amount > 10_000_000.0) continue

            val narrationPart = afterDate.substring(0, chosenMatch.range.first).trim()
                .ifBlank { afterDate.replace(chosenMatch.value, "").trim() }

            val isCredit = lower.contains(" cr ") || lower.endsWith(" cr") ||
                lower.contains("(cr)") || lower.contains("/cr/") ||
                lower.contains("credited") || lower.contains("deposit") ||
                lower.contains("salary") || lower.contains("stipend") ||
                lower.contains("refund") || lower.contains("allowance")
            val type = if (isCredit && !lower.contains("/dr/") && !lower.contains(" dr ")) "CREDIT" else "DEBIT"

            drafts.add(
                buildCategorizedDraft(
                    dateMillis = parsedMillis,
                    rawNarration = narrationPart.ifBlank { line },
                    amount = amount,
                    type = type,
                    txnRef = REF_REGEX.find(line)?.groupValues?.getOrNull(1).orEmpty(),
                    format = format,
                    categorizationEngine = categorizationEngine,
                    activeRules = activeRules,
                    existingTransactions = existingTransactions
                )
            )
        }

        val dupCount = drafts.count { it.isDuplicate }
        StatementImportPreview(
            sourceFormat = format,
            fileName = fileName,
            drafts = drafts,
            rawLinesProcessed = rawLines.size,
            duplicateCount = dupCount,
            warningMessage = if (drafts.isEmpty()) "Could not locate transaction lines with dates & amounts in this PDF/text." else null
        )
    }

    suspend fun parseBulkSmsOrText(
        rawText: String,
        fileName: String,
        categorizationEngine: CategorizationEngine,
        activeRules: List<AccountRuleEntity>,
        existingTransactions: List<TransactionEntity>
    ): StatementImportPreview = withContext(Dispatchers.Default) {
        val chunks = rawText
            .split(Regex("""\n{2,}|\r\n\r\n|\n(?=(?:Debited|Credited|Dear|Rs\.?|INR|₹|UPI|Paid|Sent|Alert|Acct|A/c))"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val drafts = mutableListOf<ImportedTransactionDraft>()
        for (chunk in chunks) {
            val smsParsed = SmsTransactionParser.parse(chunk, "IMPORTED-SMS")
            if (smsParsed != null) {
                val catResult = categorizationEngine.categorize(
                    receiver = smsParsed.receiver,
                    account = smsParsed.account,
                    upiId = smsParsed.upiId,
                    type = smsParsed.type,
                    activeRules = activeRules
                )
                val normalizedType = if (smsParsed.type.equals("CREDIT", ignoreCase = true)) "CREDIT" else "DEBIT"
                val isDup = isLikelyDuplicate(
                    existingTransactions,
                    smsParsed.amount,
                    smsParsed.receiver,
                    smsParsed.timestamp,
                    smsParsed.txnRef
                )
                drafts.add(
                    ImportedTransactionDraft(
                        dateMillis = smsParsed.timestamp,
                        dateDisplay = displayDateFmt.format(Date(smsParsed.timestamp)),
                        rawNarration = chunk.take(140),
                        cleanedMerchant = smsParsed.receiver,
                        amount = smsParsed.amount,
                        type = normalizedType,
                        paymentMethod = smsParsed.mode,
                        accountRef = smsParsed.account,
                        upiId = smsParsed.upiId,
                        txnRef = smsParsed.txnRef,
                        category = catResult.category,
                        categorizationSource = catResult.source,
                        sourceFormat = StatementSourceFormat.SMS,
                        isDuplicate = isDup,
                        isSelected = !isDup
                    )
                )
            }
        }

        // If SMS parser yielded nothing, fall back to messy text statement parser
        if (drafts.isEmpty()) {
            return@withContext parseMessyTextStatement(
                rawText = rawText,
                format = StatementSourceFormat.SMS,
                fileName = fileName,
                categorizationEngine = categorizationEngine,
                activeRules = activeRules,
                existingTransactions = existingTransactions
            )
        }

        val dupCount = drafts.count { it.isDuplicate }
        StatementImportPreview(
            sourceFormat = StatementSourceFormat.SMS,
            fileName = fileName,
            drafts = drafts,
            rawLinesProcessed = chunks.size,
            duplicateCount = dupCount
        )
    }

    private suspend fun buildCategorizedDraft(
        dateMillis: Long,
        rawNarration: String,
        amount: Double,
        type: String,
        txnRef: String,
        format: StatementSourceFormat,
        categorizationEngine: CategorizationEngine,
        activeRules: List<AccountRuleEntity>,
        existingTransactions: List<TransactionEntity>
    ): ImportedTransactionDraft {
        val upiId = UPI_ID_REGEX.find(rawNarration)?.groupValues?.getOrNull(1).orEmpty()
        val paymentMethod = detectPaymentMethod(rawNarration)
        val cleanedMerchant = cleanMessyNarration(rawNarration, upiId)

        val catResult = categorizationEngine.categorize(
            receiver = cleanedMerchant,
            account = "",
            upiId = upiId,
            type = type,
            activeRules = activeRules
        )

        val isDup = isLikelyDuplicate(existingTransactions, amount, cleanedMerchant, dateMillis, txnRef)

        return ImportedTransactionDraft(
            dateMillis = dateMillis,
            dateDisplay = displayDateFmt.format(Date(dateMillis)),
            rawNarration = rawNarration.take(140),
            cleanedMerchant = cleanedMerchant,
            amount = amount,
            type = type,
            paymentMethod = paymentMethod,
            upiId = upiId,
            txnRef = txnRef,
            category = catResult.category,
            categorizationSource = catResult.source,
            sourceFormat = format,
            isDuplicate = isDup,
            isSelected = !isDup
        )
    }

    fun cleanMessyNarration(raw: String, upiId: String = ""): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return "Unknown Merchant"

        // Messy Indian UPI bank narrations: "UPI/DR/429810392810/SWIGGY INSTAMART/YESB/swiggy@ybl"
        if (trimmed.contains("/") && trimmed.uppercase(Locale.ENGLISH).startsWith("UPI")) {
            val parts = trimmed.split("/").map { it.trim() }.filter { it.isNotBlank() }
            val candidate = parts.firstOrNull { part ->
                val upper = part.uppercase(Locale.ENGLISH)
                upper != "UPI" && upper != "DR" && upper != "CR" && upper != "P2M" && upper != "P2P" &&
                    !part.all { it.isDigit() } && !part.contains("@") &&
                    part.length > 2 && !setOf("YESB", "HDFC", "ICIC", "SBIN", "UTIB", "KKBK", "PYTM", "OKICICI", "OKAXIS").contains(upper)
            }
            if (!candidate.isNullOrBlank()) {
                return candidate.replace(Regex("[*_]+"), " ").replace(Regex("\\s+"), " ").trim().toTitleCase()
            }
        }

        // Messy POS/NEFT/IMPS narrations: "POS 4321XXXX9876 ZOMATO ORDER BLR IN"
        var clean = trimmed
            .replace(Regex("""(?i)^(upi|pos|neft|imps|rtgs|ach|ecs|atm|bil|vis|mc|debit card|card purchase|autopay|si)[\s/\-:]*"""), "")
            .replace(Regex("""(?i)\b(dr|cr|ref|utr|rrn|txn|id|no|india|pvt|ltd|limited|blr|mum|del|in)\b"""), " ")
            .replace(Regex("""\b[xX*]*\d{4,}\b"""), " ")
            .replace(Regex("""[a-zA-Z0-9._-]+@[a-zA-Z0-9]+"""), " ")
            .replace(Regex("""[^a-zA-Z0-9\s&'.-]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (clean.length < 2 && upiId.isNotBlank()) {
            clean = upiId.substringBefore("@").replace(".", " ").replace("_", " ")
        }
        if (clean.length > 34) {
            clean = clean.substring(0, 34).trim()
        }
        return clean.ifBlank { trimmed.take(28) }.toTitleCase()
    }

    private fun detectPaymentMethod(narration: String): String {
        val lower = narration.lowercase(Locale.ENGLISH)
        return when {
            lower.contains("upi") || lower.contains("@") -> "UPI"
            lower.contains("pos") || lower.contains("card") || lower.contains("visa") || lower.contains("mastercard") -> "CARD"
            lower.contains("neft") || lower.contains("imps") || lower.contains("rtgs") || lower.contains("ach") || lower.contains("ecs") -> "BANK_TRANSFER"
            lower.contains("atm") || lower.contains("cash") -> "CASH"
            else -> "BANK_TRANSFER"
        }
    }

    private fun isLikelyDuplicate(
        existing: List<TransactionEntity>,
        amount: Double,
        merchant: String,
        dateMillis: Long,
        txnRef: String
    ): Boolean {
        val dayWindowMs = 2L * 24 * 60 * 60 * 1000
        return existing.any { ex ->
            val refMatch = txnRef.isNotBlank() && ex.txnRef.isNotBlank() && ex.txnRef.equals(txnRef, ignoreCase = true)
            val sameAmtAndNearDate = abs(ex.amount - amount) < 0.5 &&
                abs(ex.date - dateMillis) <= dayWindowMs &&
                (ex.merchant.equals(merchant, ignoreCase = true) ||
                    ex.merchant.contains(merchant.take(6), ignoreCase = true) ||
                    merchant.contains(ex.merchant.take(6), ignoreCase = true))
            refMatch || sameAmtAndNearDate
        }
    }

    /**
     * Pure-Kotlin PDF text extractor supporting uncompressed & FlateDecode compressed content streams,
     * extracting text operators (Tj / TJ / ') and plain text lines with zero native crash risk.
     */
    fun extractTextFromPdfBytes(bytes: ByteArray): String {
        val rawLatin = String(bytes, Charsets.ISO_8859_1)
        val extracted = StringBuilder()

        // 1. Scan all stream ... endstream blocks
        var searchPos = 0
        while (true) {
            val streamIdx = rawLatin.indexOf("stream", searchPos)
            if (streamIdx < 0) break
            var dataStart = streamIdx + 6
            if (dataStart < bytes.size && bytes[dataStart] == '\r'.code.toByte()) dataStart++
            if (dataStart < bytes.size && bytes[dataStart] == '\n'.code.toByte()) dataStart++

            val endIdx = rawLatin.indexOf("endstream", dataStart)
            if (endIdx < 0) break
            searchPos = endIdx + 9

            val headerWindow = rawLatin.substring((streamIdx - 200).coerceAtLeast(0), streamIdx)
            val streamBytes = bytes.copyOfRange(dataStart, endIdx.coerceAtLeast(dataStart))

            val contentStr = if (headerWindow.contains("/FlateDecode")) {
                inflateSilently(streamBytes)?.toString(Charsets.ISO_8859_1) ?: ""
            } else {
                streamBytes.toString(Charsets.ISO_8859_1)
            }

            if (contentStr.contains("Tj") || contentStr.contains("TJ")) {
                extracted.append(extractPdfTextOperators(contentStr)).append('\n')
            }
        }

        // 2. Fallback: if PDF had plain text or no streams (e.g. simple generated PDF or text-backed PDF)
        if (extracted.isBlank()) {
            val directOps = extractPdfTextOperators(rawLatin)
            if (directOps.isNotBlank()) {
                extracted.append(directOps)
            } else {
                // Extract printable ASCII lines containing dates
                rawLatin.lines()
                    .filter { line -> DATE_REGEX.containsMatchIn(line) && AMOUNT_REGEX.containsMatchIn(line) }
                    .forEach { extracted.append(it).append('\n') }
            }
        }

        return extracted.toString()
    }

    private fun inflateSilently(compressed: ByteArray): ByteArray? {
        return try {
            val inflater = Inflater()
            inflater.setInput(compressed)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var guard = 0
            while (!inflater.finished() && guard < 2000) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    output.write(buffer, 0, count)
                }
                guard++
            }
            inflater.end()
            output.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun extractPdfTextOperators(stream: String): String {
        val sb = StringBuilder()
        // Match "(text) Tj" or "[(text) -10 (more)] TJ"
        val tjPattern = Regex("""\(([^()\\]*(?:\\.[^()\\]*)*)\)\s*Tj""")
        val tjArrayPattern = Regex("""\[(.*?)\]\s*TJ""", RegexOption.DOT_MATCHES_ALL)
        val innerParen = Regex("""\(([^()\\]*(?:\\.[^()\\]*)*)\)""")

        // Process line-by-line / operator-by-operator to preserve row breaks on Td/TD/T*/ET
        val tokens = stream.split(Regex("""(?=\b(?:BT|ET|T\*|Td|TD)\b)"""))
        for (block in tokens) {
            var blockHasText = false
            for (m in tjPattern.findAll(block)) {
                sb.append(unescapePdfString(m.groupValues[1])).append(' ')
                blockHasText = true
            }
            for (arr in tjArrayPattern.findAll(block)) {
                for (inner in innerParen.findAll(arr.groupValues[1])) {
                    sb.append(unescapePdfString(inner.groupValues[1]))
                    blockHasText = true
                }
                sb.append(' ')
            }
            if (blockHasText) sb.append('\n')
        }
        return sb.toString()
    }

    private fun unescapePdfString(s: String): String {
        return s.replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
            .replace("\\n", " ")
            .replace("\\r", " ")
            .replace("\\t", " ")
    }

    private fun detectDelimiter(lines: List<String>): Char {
        val candidates = listOf(',', ';', '\t', '|')
        val sample = lines.take(15)
        return candidates.maxByOrNull { delim ->
            sample.sumOf { line -> line.count { it == delim } }
        } ?: ','
    }

    private fun splitDelimitedLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delimiter && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    private fun parseAmountToken(raw: String): Double? {
        val clean = raw.trim()
            .replace(Regex("""(?i)(rs\.?|inr|₹|\$|dr|cr)"""), "")
            .replace(",", "")
            .replace(" ", "")
        if (clean.isBlank() || clean == "-" || clean == "--") return null
        return clean.toDoubleOrNull()?.let { abs(it) }
    }

    private fun parseSignedAmount(raw: String): Double? {
        val clean = raw.trim()
            .replace(Regex("""(?i)(rs\.?|inr|₹|\$|dr|cr)"""), "")
            .replace(",", "")
            .replace(" ", "")
        if (clean.isBlank() || clean == "-" || clean == "--") return null
        // Handle accounting negative format: (450.00)
        if (clean.startsWith("(") && clean.endsWith(")")) {
            return clean.substring(1, clean.length - 1).toDoubleOrNull()?.let { -abs(it) }
        }
        return clean.toDoubleOrNull()
    }

    private fun parseFlexibleDate(raw: String): Long? {
        val cleaned = raw.trim().replace(Regex("""\s+"""), " ")
        for (pattern in DATE_FORMATS) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.ENGLISH)
                fmt.isLenient = false
                val parsed = fmt.parse(cleaned)
                if (parsed != null) {
                    val cal = Calendar.getInstance().apply { time = parsed }
                    // Guard against 2-digit year parsing to 0026
                    if (cal.get(Calendar.YEAR) < 100) {
                        cal.add(Calendar.YEAR, 2000)
                    }
                    return cal.timeInMillis
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun toTransactionEntity(draft: ImportedTransactionDraft): TransactionEntity {
        val dateObj = Date(draft.dateMillis)
        return TransactionEntity(
            amount = draft.amount,
            type = draft.type,
            merchant = draft.cleanedMerchant,
            category = draft.category,
            date = draft.dateMillis,
            paymentMethod = draft.paymentMethod,
            accountRef = draft.accountRef.ifBlank { "Imported" },
            upiId = draft.upiId,
            txnRef = draft.txnRef,
            notes = buildString {
                append("Imported from ${draft.sourceFormat.label}")
                if (draft.userCorrected) append(" • Corrected by user")
                else if (draft.categorizationSource == CategorizationSource.USER_LEARNED) append(" • Learned rule")
                else if (draft.categorizationSource == CategorizationSource.RECEIVER_RULE ||
                    draft.categorizationSource == CategorizationSource.ACCOUNT_RULE
                ) append(" • Permanent rule")
            },
            isRecurring = draft.category.equals("Subscriptions", ignoreCase = true),
            isAiCategorized = draft.categorizationSource == CategorizationSource.AI_HEURISTIC ||
                draft.categorizationSource == CategorizationSource.RECEIVER_DICTIONARY,
            categorizedByRule = draft.categorizationSource == CategorizationSource.USER_LEARNED ||
                draft.categorizationSource == CategorizationSource.RECEIVER_RULE ||
                draft.categorizationSource == CategorizationSource.ACCOUNT_RULE,
            rawSms = draft.rawNarration,
            dateStr = shortDateFmt.format(dateObj),
            timeStr = "12:00 PM"
        )
    }

    private fun String.toTitleCase(): String {
        return split(" ").filter { it.isNotBlank() }.joinToString(" ") { w ->
            w.lowercase(Locale.ENGLISH).replaceFirstChar { it.titlecase(Locale.ENGLISH) }
        }
    }

    // Realistic messy bank samples so users can immediately verify PDF, CSV & Multi-SMS ingestion
    fun sampleMessyCsv(): String {
        val cal = Calendar.getInstance()
        val f = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
        fun d(daysAgo: Int): String {
            val c = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -daysAgo) }
            return f.format(c.time)
        }
        return """
            STATE BANK OF INDIA - ACCOUNT STATEMENT
            Account Holder: Aarav Sharma,,,Account No: XXXXXXXX1234
            Branch: CAMPUS TECH PARK,,,Currency: INR
            
            Txn Date,Value Date,Description,Ref No./Cheque No.,Debit,Credit,Balance
            ${d(1)},${d(1)},"UPI/DR/431092841222/SWIGGY INSTAMART/YESB/swiggy@ybl",431092841222,"485.00","","14,215.00"
            ${d(2)},${d(2)},"POS 4321XXXX5678 BLUE TOKAI COFFEE BLR",882910394,"320.00","","14,700.00"
            ${d(3)},${d(3)},"UPI/DR/430918273645/UBER TRIP INDIA/HDFC/uber@hdfcbank",430918273645,"275.50","","15,020.00"
            ${d(4)},${d(4)},"SI AUTOPAY NETFLIX.COM MUMBAI",992019384,"799.00","","15,295.50"
            ${d(5)},${d(5)},"NEFT/CR/998123123/FREELANCE UI DESIGN/ICIC",998123123,"","4,500.00","16,094.50"
            ${d(6)},${d(6)},"UPI/DR/430112233445/SHARMA XEROX CORNER/SBIN/sharma@okaxis",430112233445,"180.00","","11,594.50"
            Closing Balance,,,,,,14215.00
        """.trimIndent()
    }

    fun sampleMessyPdfText(): String {
        val cal = Calendar.getInstance()
        val f = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
        fun d(daysAgo: Int): String {
            val c = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -daysAgo) }
            return f.format(c.time)
        }
        return """
            HDFC BANK LTD - STATEMENT OF ACCOUNT (PDF EXTRACT)
            Page 1 of 2   Period: Current Month
            Date        Narration                                   Withdrawal    Balance
            ${d(1)}  UPI-ZOMATO ONLINE ORDER-9918273645-HDFC        640.00    12,840.00
            ${d(2)}  POS VISA*5678 DECATHLON SPORTS INDIA         1,299.00    13,480.00
            ${d(3)}  UPI-CHAI SUTTA BAR CAMPUS-4481920384-YBL       145.00    14,779.00
            ${d(4)}  ACH DR SPOTIFY INDIA STUDENT SUB               119.00    14,924.00
            ${d(5)}  IMPS CR SCHOLARSHIP GRANT UNIVERSITY         3,000.00    15,043.00
        """.trimIndent()
    }

    fun sampleBulkMessySms(): String {
        val f = SimpleDateFormat("ddMMMyy", Locale.ENGLISH)
        val today = f.format(Date())
        return """
            Debited Rs 310.00 from a/c2405 on $today 01:15 PM via UPI to Third Wave Coffee Ref 429810399911.Bal Rs 9420. Not you?Call1800222- HDFC Bank

            Paid Rs 490 to Rapido Bike Taxi via UPI

            UPI txn of Rs 850.00 to Campus Bookstore on $today

            Your OTP for HDFC NetBanking is 849201. Do not share with anyone.

            Debited Rs 699 via CARD to GymFit Monthly Pass
        """.trimIndent()
    }
}
