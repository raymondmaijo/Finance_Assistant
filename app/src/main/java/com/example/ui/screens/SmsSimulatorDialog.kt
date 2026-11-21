package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sms.SmsTransactionParser
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.viewmodel.MainViewModel

data class SampleSmsScenario(
    val title: String,
    val description: String,
    val sender: String,
    val body: String,
    val isIgnored: Boolean = false
)

val SAMPLE_INDIAN_SMS = listOf(
    SampleSmsScenario(
        title = "a/c2405 Rahul Kumar (Ref + Bal + Bank)",
        description = "Debited Rs 350 from a/c2405 on 19Sep26 08:15 PM via UPI to Rahul Kumar Ref 429810392810.Bal Rs 5,420.00. Not you?Call1800120120- HDFC Bank",
        sender = "VM-HDFCBK",
        body = "Debited Rs 350 from a/c2405 on 19Sep26 08:15 PM via UPI to Rahul Kumar Ref 429810392810.Bal Rs 5,420.00. Not you?Call1800120120- HDFC Bank"
    ),
    SampleSmsScenario(
        title = "a/c2405 Full Month Canteen (Ref + Bal)",
        description = "Debited Rs 140 from a/c2405 on 19September26 01:30 PM via UPI to Campus Canteen Ref 987654321012.Bal Rs 5,280.00. Not you?Call1800200- HDFC Bank",
        sender = "VM-HDFCBK",
        body = "Debited Rs 140 from a/c2405 on 19September26 01:30 PM via UPI to Campus Canteen Ref 987654321012.Bal Rs 5,280.00. Not you?Call1800200- HDFC Bank"
    ),
    SampleSmsScenario(
        title = "Campus Canteen Thali (UPI)",
        description = "Debited Rs 140 from a/c X1234 on 19Sep26 01:30 PM via UPI to Campus Canteen",
        sender = "VM-HDFCBK",
        body = "Debited Rs 140 from a/c X1234 on 19Sep26 01:30 PM via UPI to Campus Canteen"
    ),
    SampleSmsScenario(
        title = "Chai Point & Bun Maska (UPI)",
        description = "Debited Rs 60 via UPI to Chai Point",
        sender = "VM-HDFCBK",
        body = "Debited Rs 60 via UPI to Chai Point"
    ),
    SampleSmsScenario(
        title = "Zepto Midnight Munchies (UPI)",
        description = "Paid Rs 210 to Zepto via UPI",
        sender = "VK-AXISBK",
        body = "Paid Rs 210 to Zepto via UPI"
    ),
    SampleSmsScenario(
        title = "Swiggy Late Night Biryani (UPI)",
        description = "Debited Rs 250 via UPI to Swiggy",
        sender = "VM-HDFCBK",
        body = "Debited Rs 250 via UPI to Swiggy"
    ),
    SampleSmsScenario(
        title = "Sharma Dhaba Group Dinner (UPI)",
        description = "UPI txn of Rs 380 to Sharma Dhaba",
        sender = "VM-HDFCBK",
        body = "UPI txn of Rs 380 to Sharma Dhaba"
    ),
    SampleSmsScenario(
        title = "Rahul Kumar (Standard Bank Format)",
        description = "Debited Rs 350 from a/c X1234 on 19Sep26 08:15 PM via UPI to Rahul Kumar",
        sender = "VM-HDFCBK",
        body = "Debited Rs 350 from a/c X1234 on 19Sep26 08:15 PM via UPI to Rahul Kumar"
    ),
    SampleSmsScenario(
        title = "Zomato via Card (X5678)",
        description = "Debited Rs 1200 from a/c X5678 on 21Sep26 10:45 AM via CARD to Zomato",
        sender = "AX-SBIINB",
        body = "Debited Rs 1200 from a/c X5678 on 21Sep26 10:45 AM via CARD to Zomato"
    ),
    SampleSmsScenario(
        title = "Domino's Pizza Treat (UPI)",
        description = "Debited Rs 540 via UPI to Dominos",
        sender = "VM-HDFCBK",
        body = "Debited Rs 540 via UPI to Dominos"
    ),
    SampleSmsScenario(
        title = "ABC Stores (Permanent Rule Test)",
        description = "Debited Rs 350 from a/c X1234 on 19Sep26 02:30 PM via CARD to ABC Stores",
        sender = "VK-ICICIB",
        body = "Debited Rs 350 from a/c X1234 on 19Sep26 02:30 PM via CARD to ABC Stores"
    ),
    SampleSmsScenario(
        title = "Groww (Investments Rule Test)",
        description = "Debited Rs 3000 from a/c X5678 on 20Sep26 09:00 AM via UPI to Groww",
        sender = "VM-HDFCBK",
        body = "Debited Rs 3000 from a/c X5678 on 20Sep26 09:00 AM via UPI to Groww"
    ),
    SampleSmsScenario(
        title = "Uber Ride (Transportation)",
        description = "Debited Rs 600 via UPI to Uber",
        sender = "VK-PAYTMB",
        body = "Debited Rs 600 via UPI to Uber"
    ),
    SampleSmsScenario(
        title = "Ignored: OTP Security Message",
        description = "Contains OTP keyword -> strictly ignored",
        sender = "VM-HDFCBK",
        body = "482910 is your secret OTP for login to netbanking. Do not share OTP or password with anyone.",
        isIgnored = true
    ),
    SampleSmsScenario(
        title = "Ignored: Advertisement / Offer / Cashback",
        description = "Contains Offer & Cashback keywords -> ignored",
        sender = "AD-RECHRG",
        body = "Special Cashback Offer: Get Rs 100 cashback on your next recharge! Advertisement from partner.",
        isIgnored = true
    ),
    SampleSmsScenario(
        title = "Ignored: Failed Transaction",
        description = "Contains Failed keyword -> ignored",
        sender = "VM-HDFCBK",
        body = "Debited Rs 500 from a/c X1234 failed due to network timeout. No money was deducted.",
        isIgnored = true
    )
)

@Composable
fun SmsSimulatorDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var customBody by remember { mutableStateOf("Debited Rs 350 from a/c X1234 on 19Sep26 08:15 PM via UPI to Rahul Kumar") }
    var customSender by remember { mutableStateOf("VM-HDFCBK") }
    var parsePreview by remember { mutableStateOf<String?>(null) }

    // Run preview once on initial mount
    remember {
        val parsed = SmsTransactionParser.parse(customBody, customSender)
        parsePreview = if (parsed != null) {
            "Extracted: Type: ${parsed.type} • Amount: ₹${parsed.amount.toLong()} • Account: ${parsed.account} • Date: ${parsed.date} • Time: ${parsed.time} • Mode: ${parsed.mode} • Receiver: ${parsed.receiver}"
        } else {
            "Ignored by filter (Non-transactional / OTP / Offer / Failed)"
        }
        true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("sms_simulator_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Structured Bank SMS Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "Test automatic extraction of Amount, Receiver, Account, Date, Time, Mode, and Receiver-Based Categorization.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Text("Pre-crafted Standard SMS:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                items(SAMPLE_INDIAN_SMS) { scenario ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                customSender = scenario.sender
                                customBody = scenario.body
                                val parsed = SmsTransactionParser.parse(scenario.body, scenario.sender)
                                parsePreview = if (parsed != null) {
                                    "Extracted: Type: ${parsed.type} • Amount: ₹${parsed.amount.toLong()} • Account: ${parsed.account} • Date: ${parsed.date} • Time: ${parsed.time} • Mode: ${parsed.mode} • Receiver: ${parsed.receiver}"
                                } else {
                                    "Ignored by privacy & security filter (OTP / Offer / Cashback / Failed / Reversed)"
                                }
                            }
                            .testTag("scenario_${scenario.title.take(10)}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (scenario.isIgnored)
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = scenario.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (scenario.isIgnored) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = scenario.sender,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = scenario.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Live SMS Body Input:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customSender,
                        onValueChange = { customSender = it },
                        label = { Text("Sender Header") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customBody,
                        onValueChange = {
                            customBody = it
                            val parsed = SmsTransactionParser.parse(it, customSender)
                            parsePreview = if (parsed != null) {
                                "Extracted:\n• Type: ${parsed.type}\n• Amount: ₹${parsed.amount.toLong()}\n• Account: ${parsed.account}\n• Date: ${parsed.date}\n• Time: ${parsed.time}\n• Mode: ${parsed.mode}\n• Receiver: ${parsed.receiver}"
                            } else {
                                "Ignored by filter (Non-transactional / OTP / Offer / Failed / Reversed)"
                            }
                        },
                        label = { Text("Bank SMS Text") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("simulator_sms_body_input"),
                        minLines = 3
                    )
                }

                if (parsePreview != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = parsePreview!!,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (customBody.isNotBlank()) {
                        viewModel.processSimulatedOrRealSms(customBody, customSender)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("execute_simulated_sms_btn")
            ) {
                Text("Process & Store")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
