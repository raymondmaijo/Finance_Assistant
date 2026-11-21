package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserProfileEntity
import com.example.ui.components.Formatters
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.SkyTertiary
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SettingsPrivacyScreen(
    viewModel: MainViewModel,
    onOpenSimulator: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()

    var name by remember(profile) { mutableStateOf(profile?.name ?: "Student") }
    var allowanceStr by remember(profile) { mutableStateOf(profile?.monthlyAllowance?.toLong()?.toString() ?: "10000") }
    var primaryAcct by remember(profile) { mutableStateOf(profile?.primaryAccount ?: "HDFC XX1234") }
    var savingsGoalStr by remember(profile) { mutableStateOf(profile?.monthlySavingsGoal?.toLong()?.toString() ?: "2500") }

    var isSmsScanningEnabled by remember(profile) { mutableStateOf(profile?.isSmsScanningEnabled ?: true) }
    var maskAccounts by remember(profile) { mutableStateOf(profile?.maskAccountNumbers ?: true) }

    var showClearDataDialog by remember { mutableStateOf(false) }

    // Check runtime permissions
    val hasReceiveSms = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    val hasReadSms = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Trigger sync if granted
        val readGranted = permissions[Manifest.permission.READ_SMS] == true
        if (readGranted) {
            viewModel.scanInbox(context) {}
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_privacy_screen"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)
    ) {
        // Privacy Guarantee Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("privacy_guarantee_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Finance Coach Privacy Manifesto",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "🔒 100% On-Device Processing: Financial SMS messages are parsed locally on your phone.\n" +
                                "🛡️ Zero OTP Exposure: Verification codes and OTPs are never recorded or stored.\n" +
                                "🚫 No Personal Chats: Non-financial SMS are completely ignored.\n" +
                                "🙈 Account Masking: Bank account & card numbers are masked as XXXX1234 across all views.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // SMS Permissions & Synchronization
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SMS Detection & Permissions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatic SMS Scanning", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Detects bank & UPI messages in real-time",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isSmsScanningEnabled,
                            onCheckedChange = {
                                isSmsScanningEnabled = it
                                saveProfile(viewModel, name, allowanceStr, primaryAcct, savingsGoalStr, it, maskAccounts)
                            },
                            modifier = Modifier.testTag("sms_scanning_toggle")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Android SMS Permission", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (hasReceiveSms && hasReadSms) "Granted (Ready to read transactions)" else "Permission required to auto-read bank SMS",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasReceiveSms) EmeraldLight else WarningOrange
                            )
                        }
                        if (!hasReceiveSms || !hasReadSms) {
                            Button(
                                onClick = {
                                    permissionLauncher.launch(
                                        arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
                                    )
                                },
                                modifier = Modifier.testTag("request_sms_perm_btn")
                            ) {
                                Text("Grant")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.scanInbox(context) {} },
                                modifier = Modifier.testTag("sync_inbox_btn")
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Scan Inbox")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Test SMS button
                    OutlinedButton(
                        onClick = onOpenSimulator,
                        modifier = Modifier.fillMaxWidth().testTag("open_simulator_from_settings_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Launch SMS Transaction Simulator (Test Bank SMS)")
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Student Profile Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Student Profile & Budget Setup",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Your Name") },
                        modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = allowanceStr,
                        onValueChange = { allowanceStr = it },
                        label = { Text("Monthly Allowance / Income (₹)") },
                        modifier = Modifier.fillMaxWidth().testTag("profile_allowance_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = primaryAcct,
                        onValueChange = { primaryAcct = it },
                        label = { Text("Primary Bank Account (e.g. HDFC XX1234)") },
                        modifier = Modifier.fillMaxWidth().testTag("profile_account_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = savingsGoalStr,
                        onValueChange = { savingsGoalStr = it },
                        label = { Text("Monthly Savings Target (₹)") },
                        modifier = Modifier.fillMaxWidth().testTag("profile_savings_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mask Account Numbers", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Display as XXXX1234 in UI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = maskAccounts,
                            onCheckedChange = { maskAccounts = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            saveProfile(viewModel, name, allowanceStr, primaryAcct, savingsGoalStr, isSmsScanningEnabled, maskAccounts)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_profile_settings_btn")
                    ) {
                        Text("Save Profile Changes")
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Data Management / Reset
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Data Management",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Load demo student transactions or wipe your local database clean.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.loadSampleStudentData() },
                            modifier = Modifier.weight(1f).testTag("load_demo_data_btn")
                        ) {
                            Text("Load Demo Data")
                        }

                        Button(
                            onClick = { showClearDataDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                            modifier = Modifier.weight(1f).testTag("clear_history_btn")
                        ) {
                            Text("Clear History")
                        }
                    }
                }
            }
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear All Transactions?") },
            text = { Text("This will permanently delete all tracked transactions from your phone. Your profile settings and account rules will remain intact.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllTransactions()
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun saveProfile(
    viewModel: MainViewModel,
    name: String,
    allowanceStr: String,
    primaryAcct: String,
    savingsGoalStr: String,
    smsScanning: Boolean,
    maskAccounts: Boolean
) {
    val allowance = allowanceStr.toDoubleOrNull() ?: 10000.0
    val savings = savingsGoalStr.toDoubleOrNull() ?: 2500.0
    viewModel.saveUserProfile(name, allowance, primaryAcct, savings, smsScanning, maskAccounts)
}
