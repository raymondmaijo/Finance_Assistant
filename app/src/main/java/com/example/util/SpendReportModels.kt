package com.example.util

import androidx.compose.ui.graphics.Color
import com.example.data.model.TransactionEntity

enum class ReportPeriod(val title: String) {
    WEEKLY("Weekly"),
    MONTHLY("Monthly")
}

data class CategorySpendItem(
    val category: String,
    val amount: Double,
    val percentage: Float, // 0 to 100
    val count: Int,
    val color: Color
)

data class TimeSpendItem(
    val label: String,      // e.g. "Mon", "Tue" or "Week 1", "Week 2"
    val subLabel: String,   // e.g. "15 Sep"
    val amount: Double,
    val timestamp: Long = 0L
)

data class CumulativeTrendPoint(
    val dayLabel: String,
    val dayIndex: Int,
    val actualSpent: Double,
    val targetPace: Double
)

data class PaymentModeSpendItem(
    val mode: String,
    val amount: Double,
    val percentage: Float,
    val count: Int
)

data class SpendReport(
    val period: ReportPeriod,
    val periodLabel: String,
    val dateRangeStr: String,
    val totalSpent: Double,
    val totalIncome: Double,
    val netCashFlow: Double,
    val dailyAverage: Double,
    val highestSpendDay: String,
    val topCategory: String,
    val categories: List<CategorySpendItem>,
    val timeBreakdown: List<TimeSpendItem>,
    val cumulativeTrend: List<CumulativeTrendPoint>,
    val paymentModes: List<PaymentModeSpendItem>,
    val transactions: List<TransactionEntity>,
    val topExpenses: List<TransactionEntity>,
    val studentHighlights: List<String>,
    val generatedTimestamp: Long = System.currentTimeMillis()
)

data class FileSaveResult(
    val success: Boolean,
    val fileName: String,
    val filePath: String? = null,
    val message: String
)
