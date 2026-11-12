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

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val uid = currentUser!!.uid
        key(uid) {
            MainFeatureScreen(
                currentUserId = uid,
                onSignOutComplete = { currentUser = null }
            )
        }
    }
}

@Composable
fun MainFeatureScreen(
    currentUserId: String,
    onSignOutComplete: () -> Unit
) {
    val context = LocalContext.current
    val budgetViewModel: BudgetViewModel = viewModel(
        key = currentUserId,
        factory = BudgetViewModel.Factory(context)
    )
    val savingsViewModel: SavingsViewModel = viewModel(
        key = "${currentUserId}_savings",
        factory = SavingsViewModel.Factory(context)
    )
    val showSavingsTab by budgetViewModel.showSavingsTab.collectAsState()
    val visibleTabs = remember(showSavingsTab) {
        NavTab.entries.filter { it != NavTab.SAVINGS || showSavingsTab }
    }

    var currentTab by remember { mutableStateOf(NavTab.CHECKLIST) }

    LaunchedEffect(showSavingsTab) {
        if (!showSavingsTab && currentTab == NavTab.SAVINGS) {
            currentTab = NavTab.CHECKLIST
        }
    }

    // Handle back button to return to home tab if on secondary tab
    BackHandler(enabled = currentTab != NavTab.CHECKLIST) {
        currentTab = NavTab.CHECKLIST
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("main_navigation_bar")
            ) {
                for (tab in visibleTabs) {
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
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
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                NavTab.CHECKLIST -> ChecklistScreen(viewModel = budgetViewModel)
                NavTab.SAVINGS -> SavingsScreen(viewModel = savingsViewModel)
                NavTab.METRICS -> FinancialMetricsScreen(viewModel = budgetViewModel)
                NavTab.AI_ADVISOR -> AiAdvisorChatScreen(viewModel = budgetViewModel)
                NavTab.SETTINGS -> SettingsScreen(
                    viewModel = budgetViewModel,
                    onSignOutComplete = onSignOutComplete
                )
            }
        }
    }
}
