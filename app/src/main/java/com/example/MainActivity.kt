package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dev.DevTesterAccount
import com.example.dev.DevTesterAccounts
import com.example.ui.screens.AiAdvisorChatScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChecklistScreen
import com.example.ui.screens.FinancialMetricsScreen
import com.example.ui.screens.SavingsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.BudgetViewModel
import com.example.ui.viewmodel.SavingsViewModel
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

enum class NavTab(val title: String, val icon: ImageVector, val tag: String) {
    CHECKLIST("Pagos", Icons.Default.Checklist, "tab_checklist"),
    SAVINGS("Ahorros", Icons.Default.Savings, "tab_savings"),
    METRICS("Finanzas", Icons.AutoMirrored.Filled.TrendingUp, "tab_metrics"),
    AI_ADVISOR("Asesor IA", Icons.Default.AutoAwesome, "tab_ai"),
    SETTINGS("Ajustes", Icons.Default.Settings, "tab_settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration before UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var devTesterAccount by remember { mutableStateOf<DevTesterAccount?>(null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser != null) {
                devTesterAccount = null
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val activeUser = currentUser
    val activeDevAccount = if (BuildConfig.DEBUG) devTesterAccount else null

    when {
        activeUser != null -> {
            val uid = activeUser.uid
            key(uid) {
                MainFeatureScreen(
                    currentUserId = uid,
                    devTesterAccount = null,
                    onSwitchDevTester = {},
                    onSignOutComplete = { currentUser = null }
                )
            }
        }
        activeDevAccount != null -> {
            key(activeDevAccount.uid) {
                MainFeatureScreen(
                    currentUserId = activeDevAccount.uid,
                    devTesterAccount = activeDevAccount,
                    onSwitchDevTester = {
                        devTesterAccount = DevTesterAccounts.companionOf(activeDevAccount)
                    },
                    onSignOutComplete = { devTesterAccount = null }
                )
            }
        }
        else -> {
            AuthScreen(
                onAuthSuccess = {
                    devTesterAccount = null
                    currentUser = Firebase.auth.currentUser
                },
                onDevTesterLogin = { account ->
                    if (BuildConfig.DEBUG) {
                        devTesterAccount = account
                    }
                }
            )
        }
    }
}

@Composable
fun MainFeatureScreen(
    currentUserId: String,
    devTesterAccount: DevTesterAccount? = null,
    onSwitchDevTester: () -> Unit = {},
    onSignOutComplete: () -> Unit
) {
    val context = LocalContext.current
    val budgetViewModel: BudgetViewModel = viewModel(
        key = currentUserId,
        factory = BudgetViewModel.Factory(context, devTesterAccount)
    )
    val savingsViewModel: SavingsViewModel = viewModel(
        key = "${currentUserId}_savings",
        factory = SavingsViewModel.Factory(context, devTesterAccount)
    )
    val hiddenTabs by budgetViewModel.hiddenTabs.collectAsState()
    val visibleTabs = remember(hiddenTabs) {
        NavTab.entries.filter { it == NavTab.SETTINGS || it.name !in hiddenTabs }
    }

    var currentTab by remember { mutableStateOf(NavTab.CHECKLIST) }
    var isSettingsOverlayOpen by remember { mutableStateOf(false) }

    LaunchedEffect(visibleTabs) {
        if (currentTab !in visibleTabs) {
            if (currentTab == NavTab.SETTINGS) {
                isSettingsOverlayOpen = true
            }
            currentTab = visibleTabs.first()
        } else if (isSettingsOverlayOpen && NavTab.SETTINGS in visibleTabs) {
            currentTab = NavTab.SETTINGS
            isSettingsOverlayOpen = false
        }
    }

    val homeTab = visibleTabs.first()

    // Close Settings shortcut overlay first, or return to the first visible tab
    BackHandler(enabled = isSettingsOverlayOpen || currentTab != homeTab) {
        if (isSettingsOverlayOpen) {
            isSettingsOverlayOpen = false
        } else {
            currentTab = homeTab
        }
    }

    val openSettingsShortcut: (() -> Unit)? = if (NavTab.SETTINGS !in visibleTabs) {
        { isSettingsOverlayOpen = true }
    } else null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (visibleTabs.size > 1) {
                NavigationBar(
                    modifier = Modifier.testTag("main_navigation_bar")
                ) {
                    for (tab in visibleTabs) {
                        val isSelected = !isSettingsOverlayOpen && currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                isSettingsOverlayOpen = false
                                currentTab = tab
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title
                                )
                            },
                            label = { Text(text = tab.title, maxLines = 1) },
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isSettingsOverlayOpen || currentTab == NavTab.SETTINGS) {
                SettingsScreen(
                    viewModel = budgetViewModel,
                    onSignOutComplete = onSignOutComplete,
                    devTesterAccount = devTesterAccount,
                    onSwitchDevTester = onSwitchDevTester,
                    onBack = if (NavTab.SETTINGS !in visibleTabs) {
                        { isSettingsOverlayOpen = false }
                    } else null
                )
            } else {
                when (currentTab) {
                    NavTab.CHECKLIST -> ChecklistScreen(
                        viewModel = budgetViewModel,
                        onOpenSettings = openSettingsShortcut
                    )
                    NavTab.SAVINGS -> SavingsScreen(
                        viewModel = savingsViewModel,
                        onOpenSettings = openSettingsShortcut
                    )
                    NavTab.METRICS -> FinancialMetricsScreen(
                        viewModel = budgetViewModel,
                        onOpenSettings = openSettingsShortcut
                    )
                    NavTab.AI_ADVISOR -> AiAdvisorChatScreen(
                        viewModel = budgetViewModel,
                        onOpenSettings = openSettingsShortcut
                    )
                    NavTab.SETTINGS -> Unit
                }
            }
        }
    }
}
