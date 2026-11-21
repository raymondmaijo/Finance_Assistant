package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.BudgetEntity
import com.example.data.model.TransactionEntity
import com.example.ui.components.CategoryIcons
import com.example.ui.components.Formatters
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ReportExporter {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH)
    private val dayOfWeekFormat = SimpleDateFormat("EEE", Locale.ENGLISH)
    private val dayMonthFormat = SimpleDateFormat("dd MMM", Locale.ENGLISH)

    fun buildWeeklyReport(
        offsetWeeks: Int = 0,
        allTransactions: List<TransactionEntity>,
        monthlyAllowance: Double = 10000.0,
        budgets: List<BudgetEntity> = emptyList()
    ): SpendReport {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.add(Calendar.WEEK_OF_YEAR, offsetWeeks)

        // Set to Monday 00:00:00.000
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfWeek = calendar.timeInMillis

        // Set to Sunday 23:59:59.999
        calendar.add(Calendar.DAY_OF_WEEK, 6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfWeek = calendar.timeInMillis

        val periodLabel = if (offsetWeeks == 0) "This Week" else if (offsetWeeks == -1) "Last Week" else "$offsetWeeks Weeks Ago"
        val dateRangeStr = "${dayMonthFormat.format(Date(startOfWeek))} - ${dateFormat.format(Date(endOfWeek))}"

        val txnsInPeriod = allTransactions.filter { it.date in startOfWeek..endOfWeek }
            .sortedByDescending { it.date }

        return calculateReportData(
            period = ReportPeriod.WEEKLY,
            periodLabel = periodLabel,
            dateRangeStr = dateRangeStr,
            txns = txnsInPeriod,
            startTime = startOfWeek,
            endTime = endOfWeek,
            isWeekly = true,
            monthlyAllowance = monthlyAllowance
        )
    }

    fun buildMonthlyReport(
        offsetMonths: Int = 0,
        allTransactions: List<TransactionEntity>,
        monthlyAllowance: Double = 10000.0,
        budgets: List<BudgetEntity> = emptyList()
    ): SpendReport {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, offsetMonths)

        // Set to 1st of month 00:00:00.000
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.timeInMillis

        // Set to last day of month 23:59:59.999
        val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        calendar.set(Calendar.DAY_OF_MONTH, maxDay)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfMonth = calendar.timeInMillis

        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
        val periodLabel = if (offsetMonths == 0) "This Month (${monthFormat.format(Date(startOfMonth))})" else monthFormat.format(Date(startOfMonth))
        val dateRangeStr = "${dateFormat.format(Date(startOfMonth))} - ${dateFormat.format(Date(endOfMonth))}"

        val txnsInPeriod = allTransactions.filter { it.date in startOfMonth..endOfMonth }
            .sortedByDescending { it.date }

        return calculateReportData(
            period = ReportPeriod.MONTHLY,
            periodLabel = periodLabel,
            dateRangeStr = dateRangeStr,
            txns = txnsInPeriod,
            startTime = startOfMonth,
            endTime = endOfMonth,
            isWeekly = false,
            monthlyAllowance = monthlyAllowance
        )
    }

    private fun calculateReportData(
        period: ReportPeriod,
        periodLabel: String,
        dateRangeStr: String,
        txns: List<TransactionEntity>,
        startTime: Long,
        endTime: Long,
        isWeekly: Boolean,
        monthlyAllowance: Double
    ): SpendReport {
        val debits = txns.filter { it.type.equals("DEBIT", ignoreCase = true) }
        val credits = txns.filter { it.type.equals("CREDIT", ignoreCase = true) }

        val totalSpent = debits.sumOf { it.amount }
        val totalIncome = credits.sumOf { it.amount }
        val netCashFlow = totalIncome - totalSpent

        val daysCount = if (isWeekly) 7 else {
            val cal = Calendar.getInstance()
            cal.timeInMillis = startTime
            cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
        val dailyAverage = if (daysCount > 0) totalSpent / daysCount else 0.0

        // Group by category
        val categoryMap = debits.groupBy { it.category }
        val categoryItems = categoryMap.map { (cat, list) ->
            val amt = list.sumOf { it.amount }
            val pct = if (totalSpent > 0) ((amt / totalSpent) * 100.0).toFloat() else 0f
            CategorySpendItem(
                category = cat,
                amount = amt,
                percentage = pct,
                count = list.size,
                color = CategoryIcons.getColor(cat)
            )
        }.sortedByDescending { it.amount }

        val topCategory = categoryItems.firstOrNull()?.category ?: "None"

        // Time Breakdown (Days for weekly, weeks or sample days for monthly)
        val timeItems = mutableListOf<TimeSpendItem>()
        val cal = Calendar.getInstance()
        if (isWeekly) {
            cal.timeInMillis = startTime
            for (i in 0 until 7) {
                val dayStart = cal.timeInMillis
                val dayLabel = dayOfWeekFormat.format(cal.time)
                val subLabel = dayMonthFormat.format(cal.time)

                cal.add(Calendar.DAY_OF_MONTH, 1)
                val dayEnd = cal.timeInMillis

                val daySpent = debits.filter { it.date in dayStart until dayEnd }.sumOf { it.amount }
                timeItems.add(TimeSpendItem(label = dayLabel, subLabel = subLabel, amount = daySpent, timestamp = dayStart))
            }
        } else {
            // For monthly, group into 4-5 calendar weeks
            cal.timeInMillis = startTime
            var weekIdx = 1
            while (cal.timeInMillis <= endTime) {
                val weekStart = cal.timeInMillis
                val subLabel = dayMonthFormat.format(cal.time)
                cal.add(Calendar.DAY_OF_MONTH, 7)
                val weekEnd = minOf(cal.timeInMillis, endTime + 1)

                val weekSpent = debits.filter { it.date in weekStart until weekEnd }.sumOf { it.amount }
                timeItems.add(TimeSpendItem(label = "W$weekIdx", subLabel = subLabel, amount = weekSpent, timestamp = weekStart))
                weekIdx++
            }
        }

        // Highest Spend Day
        val maxDayItem = timeItems.maxByOrNull { it.amount }
        val highestSpendDay = if (maxDayItem != null && maxDayItem.amount > 0) {
            "${maxDayItem.label} (${maxDayItem.subLabel}): ₹${maxDayItem.amount.toLong()}"
        } else {
            "None"
        }

        // Cumulative Spend Trend
        val cumulativeTrend = mutableListOf<CumulativeTrendPoint>()
        var runningTotal = 0.0
        val targetPaceDaily = if (isWeekly) (monthlyAllowance / 4.0) / 7.0 else monthlyAllowance / daysCount
        timeItems.forEachIndexed { idx, item ->
            runningTotal += item.amount
            val targetPace = targetPaceDaily * (idx + 1)
            cumulativeTrend.add(
                CumulativeTrendPoint(
                    dayLabel = item.label,
                    dayIndex = idx,
                    actualSpent = runningTotal,
                    targetPace = targetPace
                )
            )
        }

        // Payment Mode Breakdown
        val modeMap = debits.groupBy { it.paymentMethod.ifBlank { "UPI" }.uppercase(Locale.ENGLISH) }
        val paymentModes = modeMap.map { (mode, list) ->
            val amt = list.sumOf { it.amount }
            val pct = if (totalSpent > 0) ((amt / totalSpent) * 100.0).toFloat() else 0f
            PaymentModeSpendItem(
                mode = mode,
                amount = amt,
                percentage = pct,
                count = list.size
            )
        }.sortedByDescending { it.amount }

        // Top Expenses
        val topExpenses = debits.sortedByDescending { it.amount }.take(5)

        // Highlights for Student
        val highlights = mutableListOf<String>()
        val upiItem = paymentModes.find { it.mode.contains("UPI") }
        if (upiItem != null && totalSpent > 0) {
            highlights.add("UPI accounted for ${upiItem.percentage.toInt()}% (₹${upiItem.amount.toLong()}) of total spending.")
        }
        if (categoryItems.isNotEmpty()) {
            val top = categoryItems.first()
            highlights.add("Your #1 expense category was ${top.category} at ₹${top.amount.toLong()} (${top.percentage.toInt()}%).")
        }
        val peopleCat = categoryItems.find { it.category.equals("People", ignoreCase = true) }
        if (peopleCat != null && peopleCat.amount > 0) {
            highlights.add("Peer transfers & room splits (People) totaled ₹${peopleCat.amount.toLong()} across ${peopleCat.count} transfers.")
        }
        if (totalIncome > 0) {
            highlights.add("Received / Credits recorded: ₹${totalIncome.toLong()} (Net cash flow: ₹${netCashFlow.toLong()}).")
        }

        return SpendReport(
            period = period,
            periodLabel = periodLabel,
            dateRangeStr = dateRangeStr,
            totalSpent = totalSpent,
            totalIncome = totalIncome,
            netCashFlow = netCashFlow,
            dailyAverage = dailyAverage,
            highestSpendDay = highestSpendDay,
            topCategory = topCategory,
            categories = categoryItems,
            timeBreakdown = timeItems,
            cumulativeTrend = cumulativeTrend,
            paymentModes = paymentModes,
            transactions = txns,
            topExpenses = topExpenses,
            studentHighlights = highlights
        )
    }

    // Export CSV to Device Downloads / App Storage
    fun exportCsv(context: Context, report: SpendReport): FileSaveResult {
        val timeStamp = fileDateFormat.format(Date())
        val periodPrefix = if (report.period == ReportPeriod.WEEKLY) "Weekly" else "Monthly"
        val fileName = "FinanceCoach_SpendReport_${periodPrefix}_$timeStamp.csv"

        val csvContent = buildString {
            append("========================================================================\n")
            append("CAMPUSFIN STUDENT SPEND REPORT (${report.period.title.uppercase(Locale.ENGLISH)})\n")
            append("========================================================================\n")
            append("Period:,\"${report.periodLabel} (${report.dateRangeStr})\"\n")
            append("Generated At:,\"${dateFormat.format(Date(report.generatedTimestamp))} ${timeFormat.format(Date(report.generatedTimestamp))}\"\n")
            append("Total Outflow (Spent):,Rs. ${report.totalSpent}\n")
            append("Total Inflow (Income/Credits):,Rs. ${report.totalIncome}\n")
            append("Net Balance / Cash Flow:,Rs. ${report.netCashFlow}\n")
            append("Daily Spend Average:,Rs. ${report.dailyAverage.toLong()}\n")
            append("Top Spending Category:,\"${report.topCategory}\"\n")
            append("Peak Spending Day:,\"${report.highestSpendDay}\"\n\n")

            append("------------------------------------------------------------------------\n")
            append("CATEGORY SPENDING SUMMARY\n")
            append("------------------------------------------------------------------------\n")
            append("Category,Amount (INR),Share (%),Txn Count\n")
            for (cat in report.categories) {
                append("\"${cat.category}\",${cat.amount},${String.format(Locale.ENGLISH, "%.1f", cat.percentage)}%,${cat.count}\n")
            }
            append("\n")

            append("------------------------------------------------------------------------\n")
            append("PAYMENT METHOD BREAKDOWN\n")
            append("------------------------------------------------------------------------\n")
            append("Payment Method,Amount (INR),Share (%),Txn Count\n")
            for (pm in report.paymentModes) {
                append("\"${pm.mode}\",${pm.amount},${String.format(Locale.ENGLISH, "%.1f", pm.percentage)}%,${pm.count}\n")
            }
            append("\n")

            append("------------------------------------------------------------------------\n")
            append("ITEMIZED TRANSACTIONS\n")
            append("------------------------------------------------------------------------\n")
            append("ID,Date,Time,Merchant / Party,Category,Type,Amount (INR),Payment Method,Account,Txn Ref,Notes\n")
            for (tx in report.transactions) {
                val cleanNotes = tx.notes.replace("\"", "\"\"")
                val cleanMerchant = tx.merchant.replace("\"", "\"\"")
                append("${tx.id},\"${dateFormat.format(Date(tx.date))}\",\"${timeFormat.format(Date(tx.date))}\",\"$cleanMerchant\",\"${tx.category}\",\"${tx.type}\",${tx.amount},\"${tx.paymentMethod}\",\"${tx.accountRef}\",\"${tx.txnRef}\",\"$cleanNotes\"\n")
            }
        }

        return writeToStorage(context, fileName, "text/csv", csvContent.toByteArray(Charsets.UTF_8))
    }

    // Export Formatted Text / Markdown Summary
    fun exportTextSummary(context: Context, report: SpendReport): FileSaveResult {
        val timeStamp = fileDateFormat.format(Date())
        val periodPrefix = if (report.period == ReportPeriod.WEEKLY) "Weekly" else "Monthly"
        val fileName = "FinanceCoach_SpendReport_${periodPrefix}_$timeStamp.txt"

        val textContent = buildString {
            append("FINANCE COACH - SPEND REPORT\n")
            append("====================================================\n")
            append("Report Type: ${report.period.title} Spend Analysis\n")
            append("Period:      ${report.periodLabel} (${report.dateRangeStr})\n")
            append("Generated:   ${dateFormat.format(Date())} ${timeFormat.format(Date())}\n")
            append("----------------------------------------------------\n\n")

            append("📊 FINANCIAL OVERVIEW\n")
            append("• Total Spent:         Rs. ${report.totalSpent.toLong()}\n")
            append("• Total Received:      Rs. ${report.totalIncome.toLong()}\n")
            append("• Net Cash Flow:       Rs. ${report.netCashFlow.toLong()}\n")
            append("• Daily Average:       Rs. ${report.dailyAverage.toLong()} / day\n")
            append("• Peak Spend Day:      ${report.highestSpendDay}\n")
            append("• Top Category:        ${report.topCategory}\n\n")

            append("🏷️ CATEGORY BREAKDOWN\n")
            for (cat in report.categories) {
                val bar = "█".repeat((cat.percentage / 5).toInt().coerceAtLeast(1))
                append(String.format(Locale.ENGLISH, "%-15s: Rs. %-8d (%4.1f%%) [%d txns] %s\n",
                    cat.category, cat.amount.toLong(), cat.percentage, cat.count, bar))
            }
            append("\n")

            append("💳 PAYMENT MODES\n")
            for (pm in report.paymentModes) {
                append(String.format(Locale.ENGLISH, "• %-12s: Rs. %-8d (%4.1f%%, %d txns)\n",
                    pm.mode, pm.amount.toLong(), pm.percentage, pm.count))
            }
            append("\n")

            if (report.studentHighlights.isNotEmpty()) {
                append("💡 KEY INSIGHTS FOR STUDENTS\n")
                for (hl in report.studentHighlights) {
                    append("• $hl\n")
                }
                append("\n")
            }

            append("📝 TOP EXPENSES IN THIS PERIOD\n")
            for ((idx, tx) in report.topExpenses.withIndex()) {
                append("${idx + 1}. Rs. ${tx.amount.toLong()} - ${tx.merchant} (${tx.category}) on ${dateFormat.format(Date(tx.date))}\n")
                if (tx.notes.isNotBlank()) append("   Note: ${tx.notes}\n")
            }
            append("\n====================================================\n")
            append("Tracked offline & securely by FinanceCoach\n")
        }

        return writeToStorage(context, fileName, "text/plain", textContent.toByteArray(Charsets.UTF_8))
    }

    // Share Report Intent with File Provider
    fun shareReport(context: Context, report: SpendReport, asCsv: Boolean = true) {
        try {
            val cacheDir = File(context.cacheDir, "reports")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val ext = if (asCsv) "csv" else "txt"
            val mimeType = if (asCsv) "text/csv" else "text/plain"
            val periodPrefix = if (report.period == ReportPeriod.WEEKLY) "Weekly" else "Monthly"
            val file = File(cacheDir, "FinanceCoach_${periodPrefix}_Report.$ext")

            val content = if (asCsv) {
                buildString {
                    append("Date,Merchant,Category,Amount,Payment Method,Ref\n")
                    for (tx in report.transactions) {
                        append("\"${dateFormat.format(Date(tx.date))}\",\"${tx.merchant}\",\"${tx.category}\",${tx.amount},\"${tx.paymentMethod}\",\"${tx.txnRef}\"\n")
                    }
                }
            } else {
                "FinanceCoach Spend Report (${report.periodLabel}): Total Spent: ₹${report.totalSpent.toLong()}, Top Category: ${report.topCategory}. Tracked in FinanceCoach."
            }

            FileOutputStream(file).use { it.write(content.toByteArray(Charsets.UTF_8)) }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "FinanceCoach ${report.period.title} Spend Report (${report.periodLabel})")
                putExtra(Intent.EXTRA_TEXT, "Here is my ${report.period.title.lowercase(Locale.ENGLISH)} spend report from FinanceCoach for ${report.dateRangeStr}. Total spent: Rs. ${report.totalSpent.toLong()}.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Spend Report").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun writeToStorage(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): FileSaveResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+ (saved into public Downloads)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FinanceCoach")
                }

                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os: OutputStream ->
                        os.write(bytes)
                        os.flush()
                    }
                    FileSaveResult(
                        success = true,
                        fileName = fileName,
                        filePath = "Downloads/FinanceCoach/$fileName",
                        message = "Saved to Downloads/FinanceCoach/$fileName"
                    )
                } else {
                    // Fallback to internal files
                    saveToAppDownloads(context, fileName, bytes)
                }
            } else {
                saveToAppDownloads(context, fileName, bytes)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to internal files
            saveToAppDownloads(context, fileName, bytes)
        }
    }

    private fun saveToAppDownloads(context: Context, fileName: String, bytes: ByteArray): FileSaveResult {
        return try {
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val file = File(downloadDir, fileName)
            FileOutputStream(file).use {
                it.write(bytes)
                it.flush()
            }
            FileSaveResult(
                success = true,
                fileName = fileName,
                filePath = file.absolutePath,
                message = "Saved report to ${file.name}"
            )
        } catch (e: Exception) {
            FileSaveResult(
                success = false,
                fileName = fileName,
                filePath = null,
                message = "Failed to save file: ${e.localizedMessage}"
            )
        }
    }
}
