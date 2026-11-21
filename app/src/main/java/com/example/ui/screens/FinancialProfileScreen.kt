package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.Formatters
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SkyTertiary
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.MainViewModel
import java.util.Locale

@Composable
fun FinancialProfileScreen(
    viewModel: MainViewModel,
    onNavigateToGoals: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val goals by viewModel.allGoals.collectAsStateWithLifecycle()

    var name by remember(profile) { mutableStateOf(profile?.name ?: "Student") }
    var allowanceStr by remember(profile) { mutableStateOf((profile?.monthlyAllowance ?: 15000.0).toLong().toString()) }
    var sideIncomeStr by remember(profile) { mutableStateOf((profile?.sideIncome ?: 3500.0).toLong().toString()) }
    var rentStr by remember(profile) { mutableStateOf((profile?.monthlyRent ?: 4500.0).toLong().toString()) }
    var utilitiesStr by remember(profile) { mutableStateOf((profile?.monthlyUtilities ?: 1200.0).toLong().toString()) }
    var otherFixedStr by remember(profile) { mutableStateOf((profile?.otherFixedCosts ?: 800.0).toLong().toString()) }
    var efCurrentStr by remember(profile) { mutableStateOf((profile?.emergencyFundCurrent ?: 14500.0).toLong().toString()) }
    var efTargetMonths by remember(profile) { mutableIntStateOf(profile?.emergencyFundTargetMonths ?: 3) }
    var savingsGoalStr by remember(profile) { mutableStateOf((profile?.monthlySavingsGoal ?: 3500.0).toLong().toString()) }

    val totalIncome = (allowanceStr.toDoubleOrNull() ?: 0.0) + (sideIncomeStr.toDoubleOrNull() ?: 0.0)
    val totalFixed = (rentStr.toDoubleOrNull() ?: 0.0) + (utilitiesStr.toDoubleOrNull() ?: 0.0) + (otherFixedStr.toDoubleOrNull() ?: 0.0)
    val disposable = (totalIncome - totalFixed).coerceAtLeast(0.0)
    val efCurrent = efCurrentStr.toDoubleOrNull() ?: 0.0
    val essentialMonthly = (totalFixed + 2500.0).coerceAtLeast(4000.0)
    val efTarget = essentialMonthly * efTargetMonths
    val efMonthsCovered = if (essentialMonthly > 0) efCurrent / essentialMonthly else 0.0
    val efProgress = if (efTarget > 0) (efCurrent / efTarget).toFloat().coerceIn(0f, 1f) else 0f

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("financial_profile_screen"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Persistent Profile & Emergency Fund Runway Hero
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("profile_runway_hero"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF065F46), Color(0xFF0F172A))
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Persistent Financial Profile",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFA7F3D0)
                                )
                                Text(
                                    text = "Disposable: ${Formatters.formatRupee(disposable)}/mo",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0x3310B981)
                            ) {
                                Text(
                                    text = "${String.format(Locale.ENGLISH, "%.1f", efMonthsCovered)} / $efTargetMonths mo EF",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldLight,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Total Income", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                                Text(Formatters.formatRupee(totalIncome), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Column {
                                Text("Fixed Costs", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                                Text(Formatters.formatRupee(totalFixed), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = WarningOrange)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Emergency Fund", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                                Text(Formatters.formatRupee(efCurrent), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = EmeraldLight)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LinearProgressIndicator(
                            progress = { efProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = EmeraldLight,
                            trackColor = Color(0x33FFFFFF)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Emergency Fund Target ($efTargetMonths mo essential costs): ${Formatters.formatRupee(efTarget)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }

        // Income Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Income Sources", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Profile Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = allowanceStr,
                        onValueChange = { allowanceStr = it },
                        label = { Text("Primary Monthly Income / Allowance (₹)") },
                        modifier = Modifier.fillMaxWidth().testTag("fp_allowance_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = sideIncomeStr,
                        onValueChange = { sideIncomeStr = it },
                        label = { Text("Side Income / Stipend / Freelance (₹)") },
                        modifier = Modifier.fillMaxWidth().testTag("fp_side_income_input"),
                        singleLine = true
                    )
                }
            }
        }

        // Fixed Costs Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Monthly Fixed Costs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(
                        value = rentStr,
                        onValueChange = { rentStr = it },
                        label = { Text("Rent / Hostel Fee (₹/mo)") },
                        modifier = Modifier.fillMaxWidth().testTag("fp_rent_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = utilitiesStr,
                        onValueChange = { utilitiesStr = it },
                        label = { Text("Utilities / Wi-Fi / Mobile (₹/mo)") },
                        modifier = Modifier.fillMaxWidth().testTag("fp_utilities_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = otherFixedStr,
                        onValueChange = { otherFixedStr = it },
                        label = { Text("Other Fixed Commitments / EMIs (₹/mo)") },
                        modifier = Modifier.fillMaxWidth().testTag("fp_other_fixed_input"),
                        singleLine = true
                    )
                }
            }
        }

        // Emergency Fund & Savings Target Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = SkyTertiary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Emergency Fund & Savings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.topUpEmergencyFund(1000.0) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("top_up_ef_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+₹1,000")
                        }
                    }

                    OutlinedTextField(
                        value = efCurrentStr,
                        onValueChange = { efCurrentStr = it },
                        label = { Text("Current Emergency Fund Balance (₹)") },
                        modifier = Modifier.fillMaxWidth().testTag("fp_ef_current_input"),
                        singleLine = true
                    )

                    Text(
                        text = "Emergency Fund Target Runway",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3, 6, 9, 12).forEach { months ->
                            FilterChip(
                                selected = efTargetMonths == months,
                                onClick = { efTargetMonths = months },
                                label = { Text("${months} mo") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = savingsGoalStr,
                        onValueChange = { savingsGoalStr = it },
                        label = { Text("Monthly Savings Target (₹)") },
                        modifier = Modifier.fillMaxWidth().testTag("fp_savings_goal_input"),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            viewModel.saveFullFinancialProfile(
                                name = name,
                                monthlyAllowance = allowanceStr.toDoubleOrNull() ?: 15000.0,
                                sideIncome = sideIncomeStr.toDoubleOrNull() ?: 0.0,
                                monthlyRent = rentStr.toDoubleOrNull() ?: 0.0,
                                monthlyUtilities = utilitiesStr.toDoubleOrNull() ?: 0.0,
                                otherFixedCosts = otherFixedStr.toDoubleOrNull() ?: 0.0,
                                emergencyFundCurrent = efCurrentStr.toDoubleOrNull() ?: 0.0,
                                emergencyFundTargetMonths = efTargetMonths,
                                monthlySavingsGoal = savingsGoalStr.toDoubleOrNull() ?: 3000.0,
                                primaryAccount = profile?.primaryAccount ?: "HDFC XX1234",
                                smsScanning = profile?.isSmsScanningEnabled ?: true,
                                maskAccounts = profile?.maskAccountNumbers ?: true
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_full_profile_btn")
                    ) {
                        Text("Save Persistent Financial Profile", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Goals Summary Link Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = WarningOrange.copy(alpha = 0.10f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(WarningOrange.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Savings, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${goals.size} Active Goal(s) with Target Dates",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val reqMonthly = goals.sumOf { it.requiredMonthlyContribution() }
                        Text(
                            text = "Combined pace needed: ${Formatters.formatRupee(reqMonthly)}/mo • Model What-If scenarios",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = onNavigateToGoals, modifier = Modifier.testTag("profile_open_planner_btn")) {
                        Text("Plan")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
