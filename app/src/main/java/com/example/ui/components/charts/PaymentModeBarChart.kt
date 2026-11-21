package com.example.ui.components.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.Formatters
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MintAccent
import com.example.ui.theme.SkyTertiary
import com.example.ui.theme.WarningOrange
import com.example.util.PaymentModeSpendItem

@Composable
fun PaymentModeBarChart(
    paymentModes: List<PaymentModeSpendItem>,
    modifier: Modifier = Modifier
) {
    if (paymentModes.isEmpty()) return

    val modeColors = mapOf(
        "UPI" to EmeraldPrimary,
        "CARD" to SkyTertiary,
        "CASH" to WarningOrange,
        "NETBANKING" to MintAccent
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("payment_mode_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Payment Mode Distribution (Distribution Bar)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Track outflow across UPI, Card, and Cash channels",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Multi-segment horizontal stacked bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                paymentModes.forEach { item ->
                    val color = modeColors[item.mode.uppercase()] ?: MaterialTheme.colorScheme.primary
                    val weight = item.percentage.coerceAtLeast(1f)
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .height(18.dp)
                            .background(color)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Breakdown items
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                paymentModes.forEach { item ->
                    val color = modeColors[item.mode.uppercase()] ?: MaterialTheme.colorScheme.primary
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(color, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = when {
                                    item.mode.contains("UPI", ignoreCase = true) -> Icons.Default.QrCodeScanner
                                    item.mode.contains("CARD", ignoreCase = true) -> Icons.Default.CreditCard
                                    else -> Icons.Default.Payments
                                },
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.mode,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${item.count} txns)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "${Formatters.formatRupee(item.amount)} • ${item.percentage.toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
