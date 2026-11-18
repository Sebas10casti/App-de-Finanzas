package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.BuildConfig
import com.example.NavTab
import com.example.data.model.BudgetCategories
import com.example.dev.DevTesterAccount
import com.example.dev.DevTesterAccounts
import com.example.notifications.WalletListenerPreferences
import com.example.ui.components.CategoryVisuals
import com.example.ui.viewmodel.BudgetViewModel
import com.example.ui.viewmodel.DeleteCategoryUiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    viewModel: BudgetViewModel,
    onSignOutComplete: () -> Unit,
    devTesterAccount: DevTesterAccount? = null,
    onSwitchDevTester: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val userProfile by viewModel.userProfile.collectAsState()
    val hiddenTabs by viewModel.hiddenTabs.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()
    val deleteCategoryState by viewModel.deleteCategoryState.collectAsState()
    val currentUser = Firebase.auth.currentUser
    val isDevTester = BuildConfig.DEBUG && devTesterAccount != null

    var showSalaryDialog by remember { mutableStateOf(false) }
    var salaryInput by remember { mutableStateOf("") }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var isTabsExpanded by remember { mutableStateOf(false) }
    var isCategoriesExpanded by remember { mutableStateOf(false) }

    // Google Wallet Automatic Notification Listener state
    var walletListenerEnabled by remember {
        mutableStateOf(WalletListenerPreferences.isEnabled(context))
    }
    var hasNotificationAccess by remember {
        mutableStateOf(WalletListenerPreferences.hasSystemNotificationAccess(context))
    }
    var walletDefaultCategory by remember {
        mutableStateOf(WalletListenerPreferences.getDefaultCategory(context))
    }
    var lastWalletPurchase by remember {
        mutableStateOf(WalletListenerPreferences.getLastRecordedPurchase(context))
    }
    var showNotificationAccessDialog by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasNotificationAccess = WalletListenerPreferences.hasSystemNotificationAccess(context)
        walletListenerEnabled = WalletListenerPreferences.isEnabled(context)
        lastWalletPurchase = WalletListenerPreferences.getLastRecordedPurchase(context)
    }

    val effectiveWalletCategory = remember(walletDefaultCategory, allCategories) {
        allCategories.firstOrNull { it.equals(walletDefaultCategory, ignoreCase = true) }
            ?: allCategories.firstOrNull()
            ?: walletDefaultCategory
    }

    // Category Management Dialog States
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }
    var categoryToEdit by remember { mutableStateOf<String?>(null) }
    var editCategoryInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("btn_back_from_settings")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver"
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = "Configuración & Cuenta",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 1. User Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDevTester) MaterialTheme.colorScheme.tertiaryContainer
                                else MaterialTheme.colorScheme.primaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isDevTester) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isDevTester) {
                                devTesterAccount!!.displayName
                            } else {
                                currentUser?.displayName ?: userProfile?.displayName?.takeIf { it.isNotBlank() } ?: "Usuario"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isDevTester) {
                                devTesterAccount!!.email
                            } else {
                                currentUser?.email ?: "Conectado con cuenta Google"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isDevTester) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "MODO DESARROLLO (DATOS LOCALES)",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }

                if (isDevTester && onSwitchDevTester != null) {
                    val companion = DevTesterAccounts.companionOf(devTesterAccount!!)
                    OutlinedButton(
                        onClick = onSwitchDevTester,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_switch_dev_tester"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cambiar a ${companion.displayName}")
                    }
                }
            }
        }

        // 2. Monthly Salary Configuration Card (Redesigned with hero-style currency display)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachMoney,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sueldo Mensual Base",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Base de cálculo para el dinero libre disponible",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Prominent Salary Amount Display Box
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "INGRESO NETO MENSUAL",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = viewModel.formatCurrency(userProfile?.monthlySalary ?: 0L),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Button(
                            onClick = {
                                salaryInput = (userProfile?.monthlySalary ?: 0L).toString()
                                showSalaryDialog = true
                            },
                            modifier = Modifier
                                .testTag("btn_edit_salary"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Modificar")
                        }
                    }
                }
            }
        }

        // 2a. Google Wallet Automatic Expense Listener Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_wallet_listener"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val iconTint = when {
                            walletListenerEnabled && hasNotificationAccess -> Color(0xFF16A34A)
                            walletListenerEnabled -> Color(0xFFD97706)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(iconTint.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Contactless,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Pagos con Google Wallet",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Solo escucha notificaciones de Google Wallet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = walletListenerEnabled,
                        onCheckedChange = { checked ->
                            walletListenerEnabled = checked
                            WalletListenerPreferences.setEnabled(context, checked)
                            if (checked && !hasNotificationAccess) {
                                showNotificationAccessDialog = true
                            }
                        },
                        modifier = Modifier.testTag("switch_wallet_listener")
                    )
                }

                // Privacy & Behavior Info
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                        .copy(alpha = 0.45f)
                        .compositeOver(MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = if (walletListenerEnabled) {
                            "Activo: cada compra aprobada en Google Wallet se añadirá automáticamente al mes actual como gasto único pagado."
                        } else {
                            "Actívalo para registrar automáticamente tus compras con Google Wallet en el presupuesto del mes sin leer otras apps."
                        },
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = walletListenerEnabled) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // System Notification Listener Permission Status Banner
                        if (hasNotificationAccess) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wallet_permission_granted_badge"),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF16A34A)
                                    .copy(alpha = 0.12f)
                                    .compositeOver(MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, Color(0xFF16A34A).copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Acceso a notificaciones concedido",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFF15803D)
                                    )
                                }
                            }
                        } else {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wallet_permission_missing_banner"),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF59E0B)
                                    .copy(alpha = 0.14f)
                                    .compositeOver(MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.WarningAmber,
                                            contentDescription = null,
                                            tint = Color(0xFFB45309),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Falta permiso de notificaciones en Android",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF92400E)
                                        )
                                    }
                                    Text(
                                        text = "Habilita 'Mi Presupuesto' en el listado de acceso a notificaciones para detectar pagos de Google Wallet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF92400E)
                                    )
                                    Button(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("btn_open_notification_listener_settings"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Conceder permiso de notificaciones")
                                    }
                                }
                            }
                        }

                        // Default Category Selector for Google Wallet Purchases
                        CategorySelectorDropdown(
                            label = "Categoría para compras de Wallet",
                            placeholder = "Selecciona una categoría",
                            selectedCategory = effectiveWalletCategory,
                            categories = allCategories,
                            onCategorySelected = { chosen ->
                                walletDefaultCategory = chosen
                                WalletListenerPreferences.setDefaultCategory(context, chosen)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("selector_wallet_default_category")
                        )

                        if (isDevTester) {
                            Text(
                                text = "Nota: El registro en segundo plano guarda gastos cuando has iniciado sesión con Google.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!lastWalletPurchase.isNullOrBlank()) {
                            Text(
                                text = "Último pago detectado: $lastWalletPurchase",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // 2b. Navigation Tabs Visibility Configuration Card (Collapsible, Ajustes always fixed)
        val configurableTabs = remember { NavTab.entries.filter { it != NavTab.SETTINGS } }
        val configurableTabKeys = remember(configurableTabs) { configurableTabs.map { it.name } }
        val visibleConfigurableCount = remember(hiddenTabs, configurableTabs) {
            configurableTabs.count { it.name !in hiddenTabs }
        }
        val anyConfigurableHidden = visibleConfigurableCount < configurableTabs.size

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_tab_visibility"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isTabsExpanded = !isTabsExpanded }
                        .testTag("toggle_tabs_section"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Pestañas Visibles",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$visibleConfigurableCount de ${configurableTabs.size} visibles · Ajustes siempre activo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isTabsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isTabsExpanded) "Contraer pestañas visibles" else "Desplegar pestañas visibles",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = isTabsExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (anyConfigurableHidden) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { viewModel.showAllTabs() },
                                    modifier = Modifier.testTag("btn_show_all_tabs")
                                ) {
                                    Text("Mostrar todas")
                                }
                            }
                        }

                        for (tab in configurableTabs) {
                            val isVisible = tab.name !in hiddenTabs
                            val description = when (tab) {
                                NavTab.CHECKLIST -> "Checklist mensual de gastos y sueldo"
                                NavTab.SAVINGS -> "Bolsillos personales y compartidos"
                                NavTab.METRICS -> "Salud financiera, regla 50/30/20 y diagnóstico"
                                NavTab.AI_ADVISOR -> "Chat financiero inteligente con IA"
                                NavTab.SETTINGS -> ""
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isVisible) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = null,
                                                tint = if (isVisible) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = tab.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = description,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Switch(
                                        checked = isVisible,
                                        onCheckedChange = { checked ->
                                            viewModel.setTabVisible(tab.name, checked, configurableTabKeys)
                                        },
                                        modifier = Modifier.testTag("switch_tab_${tab.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Category Management Card (Collapsible Dropdown)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isCategoriesExpanded = !isCategoriesExpanded }
                        .testTag("toggle_categories_section"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Gestión de Categorías",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${allCategories.size} categorías activas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isCategoriesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isCategoriesExpanded) "Contraer categorías" else "Desplegar categorías",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = isCategoriesExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Categorías activas (${allCategories.size})",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = {
                                    newCategoryInput = ""
                                    showAddCategoryDialog = true
                                },
                                modifier = Modifier.testTag("btn_add_category"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Añadir")
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (cat in allCategories) {
                                val isBase = BudgetCategories.ALL.any { it.equals(cat, ignoreCase = true) }
                                val catColor = CategoryVisuals.getColor(cat)

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(catColor.copy(alpha = 0.14f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = CategoryVisuals.getIcon(cat),
                                                    contentDescription = null,
                                                    tint = catColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = cat,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isBase) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant
                                                ) {
                                                    Text(
                                                        text = "Base",
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    categoryToEdit = cat
                                                    editCategoryInput = cat
                                                },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("btn_edit_category_$cat")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Editar $cat",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    viewModel.requestDeleteCategory(cat)
                                                },
                                                enabled = allCategories.size > 1,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("btn_delete_category_$cat")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Eliminar $cat",
                                                    tint = if (allCategories.size > 1) {
                                                        MaterialTheme.colorScheme.error
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                                    },
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Information on Colombian Tax & Budgeting
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Información Financiera Colombia",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "• Fondo Pensión Voluntaria (FVP): Tus aportes son rentas exentas hasta el 30% del ingreso laboral o 3.800 UVT anuales (Art. 126-1 E.T.).\n\n• Medicina Prepagada: Deducible hasta 16 UVT mensuales disminuyendo tu base de retención en la fuente.\n\n• Cuentas de Alto Rendimiento: Mantén tu fondo de emergencia en cuentas remuneradas (Nu, Lulo, Pibank, Ualá) con rentabilidades E.A. sobre saldo diario.",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 5. Sign Out
        OutlinedButton(
            onClick = { showSignOutConfirm = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_sign_out"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cerrar Sesión")
        }

        Spacer(modifier = Modifier.height(40.dp))
    }

    // Add Category Dialog
    if (showAddCategoryDialog) {
        var hasError by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("Nueva Categoría") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Escribe el nombre de la nueva categoría para tus gastos:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = newCategoryInput,
                        onValueChange = {
                            if (it.length <= 50) {
                                newCategoryInput = it
                                hasError = false
                            }
                        },
                        label = { Text("Nombre de la categoría") },
                        placeholder = { Text("Ej: Mascotas, Educación, Viajes...") },
                        singleLine = true,
                        isError = hasError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_category_name")
                    )
                    if (hasError) {
                        Text(
                            text = "Por favor ingresa un nombre válido y no repetido.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newCategoryInput.trim()
                        if (name.isBlank() || allCategories.any { it.equals(name, ignoreCase = true) }) {
                            hasError = true
                        } else {
                            viewModel.addCategory(name)
                            showAddCategoryDialog = false
                        }
                    },
                    modifier = Modifier.testTag("btn_save_category")
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Edit Category Dialog
    categoryToEdit?.let { targetCat ->
        var hasError by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { categoryToEdit = null },
            title = { Text("Editar Categoría") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Modificar el nombre de '$targetCat':",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = editCategoryInput,
                        onValueChange = {
                            if (it.length <= 50) {
                                editCategoryInput = it
                                hasError = false
                            }
                        },
                        label = { Text("Nuevo nombre") },
                        singleLine = true,
                        isError = hasError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_category_name")
                    )
                    if (hasError) {
                        Text(
                            text = "Nombre inválido o ya existente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newName = editCategoryInput.trim()
                        val duplicatesAnother = allCategories.any {
                            !it.equals(targetCat, ignoreCase = true) && it.equals(newName, ignoreCase = true)
                        }
                        if (newName.isBlank() || duplicatesAnother) {
                            hasError = true
                        } else {
                            viewModel.renameCategory(targetCat, newName)
                            categoryToEdit = null
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_edit_category")
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToEdit = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Delete Category Flow Dialog (Checking / Empty / In-Use with Reassignment)
    DeleteCategoryDialog(
        state = deleteCategoryState,
        viewModel = viewModel,
        onDismiss = { viewModel.dismissDeleteCategory() },
        onConfirmEmpty = { viewModel.confirmDeleteCategory() },
        onConfirmInUse = { reassignments, fallback ->
            viewModel.confirmDeleteCategory(
                reassignmentsByNormalizedName = reassignments,
                fallbackCategory = fallback
            )
        }
    )

    // Edit Salary Dialog
    if (showSalaryDialog) {
        AlertDialog(
            onDismissRequest = { showSalaryDialog = false },
            title = { Text("Actualizar Sueldo Mensual") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Ingresa tu ingreso neto mensual en pesos colombianos:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = salaryInput,
                        onValueChange = { input ->
                            salaryInput = input.filter { it.isDigit() }
                        },
                        label = { Text("Sueldo (COP)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_salary_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newSalary = salaryInput.toLongOrNull() ?: 0L
                        if (newSalary > 0) {
                            viewModel.updateSalary(newSalary)
                            showSalaryDialog = false
                        }
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSalaryDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Sign Out Confirmation
    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text(if (isDevTester) "Salir del Modo Tester" else "Cerrar Sesión") },
            text = {
                Text(
                    if (isDevTester) {
                        "¿Deseas salir del usuario tester actual y volver al inicio?"
                    } else {
                        "¿Deseas cerrar sesión en este dispositivo? Tus datos permanecen seguros en la nube de Google."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirm = false
                        if (isDevTester) {
                            onSignOutComplete()
                        } else {
                            coroutineScope.launch {
                                withContext(NonCancellable) {
                                    try {
                                        credentialManager.clearCredentialState(ClearCredentialStateRequest())
                                    } catch (e: Exception) {
                                        // Ignored
                                    } finally {
                                        Firebase.auth.signOut()
                                        onSignOutComplete()
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isDevTester) "Salir" else "Cerrar Sesión")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Notification Listener Permission Explanatory Dialog
    if (showNotificationAccessDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationAccessDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Permitir acceso a notificaciones") },
            text = {
                Text(
                    "Para registrar compras de Google Wallet automáticamente, Android requiere que actives 'Mi Presupuesto' en Acceso a notificaciones.\n\nSolo procesamos notificaciones de Google Wallet; cualquier otra app se ignora por completo."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotificationAccessDialog = false
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_open_notif_settings")
                ) {
                    Text("Ir a Ajustes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationAccessDialog = false }) {
                    Text("Más tarde")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteCategoryDialog(
    state: DeleteCategoryUiState,
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit,
    onConfirmEmpty: () -> Unit,
    onConfirmInUse: (reassignments: Map<String, String>, fallback: String?) -> Unit
) {
    when (state) {
        is DeleteCategoryUiState.Hidden -> Unit

        is DeleteCategoryUiState.Checking -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Verificando categoría") },
                text = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Text(
                            text = "Revisando gastos asociados a '${state.category}'...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                }
            )
        }

        is DeleteCategoryUiState.ConfirmEmpty -> {
            AlertDialog(
                onDismissRequest = { if (!state.isDeleting) onDismiss() },
                title = { Text("Eliminar Categoría") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "¿Estás seguro de que deseas eliminar la categoría '${state.category}'? No tiene gastos asociados.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (state.error != null) {
                            Text(
                                text = state.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onConfirmEmpty,
                        enabled = !state.isDeleting,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("btn_confirm_delete_empty_category")
                    ) {
                        if (state.isDeleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onError
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !state.isDeleting
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        is DeleteCategoryUiState.ConfirmInUse -> {
            key(state.category, state.itemsInUse) {
                var bulkCategory by remember { mutableStateOf("") }
                val itemAssignments = remember { mutableStateMapOf<String, String>() }
                val allAssigned = state.itemsInUse.isNotEmpty() &&
                    state.itemsInUse.all { !itemAssignments[it.name.trim().lowercase()].isNullOrBlank() }

                AlertDialog(
                    onDismissRequest = { if (!state.isDeleting) onDismiss() },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text("Categoría en uso")
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                            ) {
                                Text(
                                    text = "La categoría '${state.category}' está siendo usada por ${state.itemsInUse.size} ítem(s). Si continúas con la eliminación, debes pasar estos ítems a otra categoría:",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            if (state.itemsInUse.size > 1) {
                                CategorySelectorDropdown(
                                    label = "Pasar todos a otra categoría (opcional)",
                                    placeholder = "Aplicar misma categoría a todos",
                                    selectedCategory = bulkCategory,
                                    categories = state.targetCategories,
                                    onCategorySelected = { chosen ->
                                        bulkCategory = chosen
                                        for (item in state.itemsInUse) {
                                            itemAssignments[item.name.trim().lowercase()] = chosen
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("selector_bulk_reassign_category")
                                )
                            }

                            for (usageItem in state.itemsInUse) {
                                val itemKey = usageItem.name.trim().lowercase()
                                val assignedCat = itemAssignments[itemKey] ?: ""

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = usageItem.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = viewModel.formatCurrency(usageItem.amount),
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        CategorySelectorDropdown(
                                            label = "Mover este ítem a",
                                            placeholder = "Selecciona una categoría",
                                            selectedCategory = assignedCat,
                                            categories = state.targetCategories,
                                            onCategorySelected = { chosen ->
                                                itemAssignments[itemKey] = chosen
                                                if (bulkCategory != chosen) {
                                                    bulkCategory = ""
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("selector_reassign_item_$itemKey")
                                        )
                                    }
                                }
                            }

                            if (state.error != null) {
                                Text(
                                    text = state.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                onConfirmInUse(
                                    itemAssignments.toMap(),
                                    bulkCategory.ifBlank { itemAssignments.values.firstOrNull() }
                                )
                            },
                            enabled = !state.isDeleting && allAssigned,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("btn_confirm_reassign_delete_category")
                        ) {
                            if (state.isDeleting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onError
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Mover y eliminar")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = onDismiss,
                            enabled = !state.isDeleting
                        ) {
                            Text("Cancelar")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelectorDropdown(
    label: String,
    placeholder: String,
    selectedCategory: String,
    categories: List<String>,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedCategory,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            for (cat in categories) {
                val catColor = CategoryVisuals.getColor(cat)
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(catColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cat)
                        }
                    },
                    onClick = {
                        onCategorySelected(cat)
                        expanded = false
                    }
                )
            }
        }
    }
}

