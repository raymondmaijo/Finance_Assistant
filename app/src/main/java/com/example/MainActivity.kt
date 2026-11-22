package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AccountRulesScreen
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.BudgetScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DebtTrackerScreen
import com.example.ui.screens.FinancialProfileScreen
import com.example.ui.screens.GoalsScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.InvestmentsScreen
import com.example.ui.screens.ManualTransactionDialog
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsPrivacyScreen
import com.example.ui.screens.SmsSimulatorDialog
import com.example.ui.screens.SpendReportScreen
import com.example.ui.screens.StatementImportScreen
import com.example.ui.screens.SubscriptionsScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.MainViewModelFactory
import kotlinx.coroutines.flow.collectLatest

sealed class NavScreen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : NavScreen("dashboard", "Home", Icons.Default.Home)
    object Transactions : NavScreen("transactions", "Transactions", Icons.AutoMirrored.Filled.ReceiptLong)
    object Budget : NavScreen("budget", "Budget", Icons.Default.AccountBalanceWallet)
    object Insights : NavScreen("insights", "Insights", Icons.Default.Insights)
    object Assistant : NavScreen("assistant", "AI Assistant", Icons.Default.AutoAwesome)

    // Sub-screens
    object SpendReports : NavScreen("spend_reports", "Spend Reports", Icons.Default.BarChart)
    object StatementImport : NavScreen("statement_import", "Import Statement", Icons.Default.FileOpen)
    object Subscriptions : NavScreen("subscriptions", "Subscriptions", Icons.Default.Autorenew)
    object FinancialProfile : NavScreen("financial_profile", "Financial Profile", Icons.Default.AccountCircle)
    object Rules : NavScreen("rules", "Account Rules", Icons.AutoMirrored.Filled.Rule)
    object Investments : NavScreen("investments", "Investments", Icons.AutoMirrored.Filled.TrendingUp)
    object DebtTracker : NavScreen("debt_tracker", "Debt & Loans", Icons.Default.CreditCard)
    object Goals : NavScreen("goals", "Goals", Icons.Default.Savings)
    object Settings : NavScreen("settings", "Settings", Icons.Default.Settings)
    object Onboarding : NavScreen("onboarding", "Welcome", Icons.Default.Home)
}

val BOTTOM_NAV_ITEMS = listOf(
    NavScreen.Dashboard,
    NavScreen.Transactions,
    NavScreen.Budget,
    NavScreen.Insights,
    NavScreen.Assistant
)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        val app = application as CampusFinApplication
        MainViewModelFactory(app.repository)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: NavScreen.Dashboard.route

                val profile by viewModel.userProfile.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                val context = LocalContext.current

                var showSimulatorDialog by remember { mutableStateOf(false) }
                var showAddTransactionDialog by remember { mutableStateOf(false) }

                // Collect toasts
                LaunchedEffect(Unit) {
                    viewModel.toastEvent.collectLatest { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }

                // Check onboarding status
                val startDestination = if (profile != null && !profile!!.isOnboardingCompleted) {
                    NavScreen.Onboarding.route
                } else {
                    NavScreen.Dashboard.route
                }

                val showTopAndBottomBars = currentRoute != NavScreen.Onboarding.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        if (showTopAndBottomBars) {
                            CenterAlignedTopAppBar(
                                title = {
                                    Text(
                                        text = when (currentRoute) {
                                            NavScreen.Dashboard.route -> "Finance Coach"
                                            NavScreen.Transactions.route -> "Transactions"
                                            NavScreen.Budget.route -> "Budget & Limits"
                                            NavScreen.Insights.route -> "Review & Insights"
                                            NavScreen.Assistant.route -> "Money Coach"
                                            NavScreen.SpendReports.route -> "Spend Reports"
                                            NavScreen.StatementImport.route -> "Import Statement"
                                            NavScreen.Subscriptions.route -> "Subscriptions & Bills"
                                            NavScreen.FinancialProfile.route -> "Financial Profile"
                                            NavScreen.Rules.route -> "Rules & Learning"
                                            NavScreen.Investments.route -> "Investments"
                                            NavScreen.DebtTracker.route -> "Debt & Loans"
                                            NavScreen.Goals.route -> "Goals & What-If"
                                            NavScreen.Settings.route -> "Settings & Privacy"
                                            else -> "Finance Coach"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                navigationIcon = {
                                    if (currentRoute != NavScreen.Dashboard.route &&
                                        currentRoute != NavScreen.Transactions.route &&
                                        currentRoute != NavScreen.Budget.route &&
                                        currentRoute != NavScreen.Insights.route &&
                                        currentRoute != NavScreen.Assistant.route
                                    ) {
                                        IconButton(onClick = { navController.popBackStack() }) {
                                            Icon(
                                                imageVector = Icons.Default.Home,
                                                contentDescription = "Back to Home"
                                            )
                                        }
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = { showSimulatorDialog = true },
                                        modifier = Modifier.testTag("topbar_test_sms_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = "Test SMS Simulator",
                                            tint = EmeraldPrimary
                                        )
                                    }
                                    IconButton(
                                        onClick = { showAddTransactionDialog = true },
                                        modifier = Modifier.testTag("topbar_add_expense_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add Expense"
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (currentRoute != NavScreen.Settings.route) {
                                                navController.navigate(NavScreen.Settings.route)
                                            }
                                        },
                                        modifier = Modifier.testTag("topbar_settings_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Settings"
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background
                                )
                            )
                        }
                    },
                    bottomBar = {
                        if (showTopAndBottomBars) {
                            NavigationBar(
                                modifier = Modifier.testTag("bottom_navigation_bar"),
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                BOTTOM_NAV_ITEMS.forEach { item ->
                                    val selected = currentRoute == item.route
                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.title,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        selected = selected,
                                        onClick = {
                                            if (currentRoute != item.route) {
                                                navController.navigate(item.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        modifier = Modifier.testTag("nav_item_${item.route}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        NavHost(
                            navController = navController,
                            startDestination = startDestination
                        ) {
                            composable(NavScreen.Onboarding.route) {
                                OnboardingScreen(
                                    viewModel = viewModel,
                                    onFinishOnboarding = {
                                        navController.navigate(NavScreen.Dashboard.route) {
                                            popUpTo(NavScreen.Onboarding.route) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable(NavScreen.Dashboard.route) {
                                DashboardScreen(
                                    viewModel = viewModel,
                                    onNavigateToTransactions = { navController.navigate(NavScreen.Transactions.route) },
                                    onNavigateToBudget = { navController.navigate(NavScreen.Budget.route) },
                                    onNavigateToAssistant = { navController.navigate(NavScreen.Assistant.route) },
                                    onNavigateToRules = { navController.navigate(NavScreen.Rules.route) },
                                    onNavigateToInvestments = { navController.navigate(NavScreen.Investments.route) },
                                    onNavigateToGoals = { navController.navigate(NavScreen.Goals.route) },
                                    onNavigateToSettings = { navController.navigate(NavScreen.Settings.route) },
                                    onNavigateToSpendReports = { navController.navigate(NavScreen.SpendReports.route) },
                                    onNavigateToStatementImport = { navController.navigate(NavScreen.StatementImport.route) },
                                    onNavigateToSubscriptions = { navController.navigate(NavScreen.Subscriptions.route) },
                                    onNavigateToFinancialProfile = { navController.navigate(NavScreen.FinancialProfile.route) },
                                    onNavigateToInsights = { navController.navigate(NavScreen.Insights.route) },
                                    onNavigateToDebtTracker = { navController.navigate(NavScreen.DebtTracker.route) },
                                    onOpenSmsSimulator = { showSimulatorDialog = true },
                                    onOpenAddTransaction = { showAddTransactionDialog = true }
                                )
                            }

                            composable(NavScreen.Transactions.route) {
                                TransactionsScreen(
                                    viewModel = viewModel,
                                    onAddTransactionClick = { showAddTransactionDialog = true },
                                    onOpenStatementImport = { navController.navigate(NavScreen.StatementImport.route) }
                                )
                            }

                            composable(NavScreen.Budget.route) {
                                BudgetScreen(viewModel = viewModel)
                            }

                            composable(NavScreen.Insights.route) {
                                InsightsScreen(
                                    viewModel = viewModel,
                                    onNavigateToTarget = { target ->
                                        when (target) {
                                            "SIMULATOR" -> navController.navigate(NavScreen.Goals.route)
                                            "SUBSCRIPTIONS" -> navController.navigate(NavScreen.Subscriptions.route)
                                            "BUDGET" -> navController.navigate(NavScreen.Budget.route)
                                            "PROFILE" -> navController.navigate(NavScreen.FinancialProfile.route)
                                            "COACH" -> navController.navigate(NavScreen.Assistant.route)
                                        }
                                    }
                                )
                            }

                            composable(NavScreen.SpendReports.route) {
                                SpendReportScreen(viewModel = viewModel)
                            }

                            composable(NavScreen.StatementImport.route) {
                                StatementImportScreen(
                                    viewModel = viewModel,
                                    onImportCompleted = { navController.navigate(NavScreen.Transactions.route) }
                                )
                            }

                            composable(NavScreen.Subscriptions.route) {
                                SubscriptionsScreen(viewModel = viewModel)
                            }

                            composable(NavScreen.FinancialProfile.route) {
                                FinancialProfileScreen(
                                    viewModel = viewModel,
                                    onNavigateToGoals = { navController.navigate(NavScreen.Goals.route) }
                                )
                            }

                            composable(NavScreen.Assistant.route) {
                                AssistantScreen(viewModel = viewModel)
                            }

                            composable(NavScreen.Rules.route) {
                                AccountRulesScreen(viewModel = viewModel)
                            }

                            composable(NavScreen.Investments.route) {
                                InvestmentsScreen(viewModel = viewModel)
                            }

                            composable(NavScreen.DebtTracker.route) {
                                DebtTrackerScreen(viewModel = viewModel)
                            }

                            composable(NavScreen.Goals.route) {
                                GoalsScreen(viewModel = viewModel)
                            }

                            composable(NavScreen.Settings.route) {
                                SettingsPrivacyScreen(
                                    viewModel = viewModel,
                                    onOpenSimulator = { showSimulatorDialog = true }
                                )
                            }
                        }
                    }
                }

                // Interactive SMS Simulator Dialog
                if (showSimulatorDialog) {
                    SmsSimulatorDialog(
                        viewModel = viewModel,
                        onDismiss = { showSimulatorDialog = false }
                    )
                }

                // Manual Add Transaction Dialog
                if (showAddTransactionDialog) {
                    ManualTransactionDialog(
                        viewModel = viewModel,
                        onDismiss = { showAddTransactionDialog = false }
                    )
                }
            }
        }
    }
}
