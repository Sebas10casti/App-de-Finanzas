package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BudgetItem
import com.example.ui.components.CategoryVisuals
import com.example.ui.viewmodel.BudgetViewModel
import com.example.ui.viewmodel.ItemStatusFilter
import java.time.YearMonth

@Composable
fun ChecklistScreen(
    viewModel: BudgetViewModel
) {
    val currentYearMonth by viewModel.currentYearMonth.collectAsState()
    val availableMonths = remember { viewModel.getAvailableMonths() }
    val metrics by viewModel.financialMetrics.collectAsState()
    val filteredItems by viewModel.filteredItems.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()
    val selectedItemIds by viewModel.selectedItemIdsForPayment.collectAsState()
    val selectedTotalAmount by viewModel.selectedPaymentTotalAmount.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<BudgetItem?>(null) }
    var itemToDelete by remember { mutableStateOf<BudgetItem?>(null) }
    var showMarkAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add_expense")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Añadir nuevo gasto"
                )
            }
        },
        bottomBar = {
            // Floating payment action bar that appears whenever 1 or more items are selected
            AnimatedVisibility(
                visible = selectedItemIds.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_action_bar"),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
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
                                text = "${selectedItemIds.size} ítem(s) seleccionado(s)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Total: ${viewModel.formatCurrency(selectedTotalAmount)}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { viewModel.clearPaymentSelection() },
                                modifier = Modifier.testTag("btn_cancel_payment_selection")
                            ) {
                                Text("Cancelar")
                            }

                            Button(
                                onClick = { viewModel.markSelectedAsPaid() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF16A34A),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.testTag("btn_confirm_mark_paid")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Marcar Pagados (${selectedItemIds.size})",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1. 13-Month Navigation Header (-6 to +6 months)
            MonthNavigator(
                currentYearMonth = currentYearMonth,
                availableMonths = availableMonths,
                onSelectMonth = { viewModel.selectMonth(it) },
                onPrevious = { viewModel.previousMonth() },
                onNext = { viewModel.nextMonth() },
                onToday = { viewModel.setCurrentMonth() },
                viewModel = viewModel
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 2. Budget and Free Cash Summary Card (Redesigned Hero Card)
                item {
                    BudgetSummaryCard(
                        salary = metrics.salary,
                        totalBudgeted = metrics.totalBudgeted,
                        totalPaid = metrics.totalPaid,
                        totalPending = metrics.totalPending,
                        freeCash = metrics.freeCash,
                        paidCount = metrics.paidCount,
                        totalCount = metrics.totalCount,
                        progressFraction = metrics.progressFraction,
                        viewModel = viewModel
                    )
                }

                // 3. Status and Category Filter Chips
                item {
                    FilterControls(
                        currentStatus = statusFilter,
                        onStatusChange = { viewModel.setStatusFilter(it) },
                        selectedCategory = selectedCategory,
                        allCategories = allCategories,
                        onCategorySelect = { viewModel.selectCategory(it) },
                        onMarkAllClick = { showMarkAllDialog = true }
                    )
                }

                // 4. Checklist Items
                if (filteredItems.isEmpty()) {
                    item {
                        EmptyStateCard(statusFilter = statusFilter)
                    }
                } else {
                    items(filteredItems, key = { it.id }) { item ->
                        val isSelectedForPayment = selectedItemIds.contains(item.id)
                        BudgetItemRow(
                            item = item,
                            isSelectedForPayment = isSelectedForPayment,
                            onToggleSelectForPayment = { viewModel.toggleSelectItemForPayment(item.id) },
                            onTogglePaidDirectly = { viewModel.toggleItemPaid(item) },
                            onEdit = { itemToEdit = item },
                            onDelete = { itemToDelete = item },
                            viewModel = viewModel
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // Add Item Dialog
    if (showAddDialog) {
        AddEditItemDialog(
            item = null,
            categories = allCategories,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, amount, cat, isRecurring, notes ->
                viewModel.addItem(name, amount, cat, isRecurring, notes)
                showAddDialog = false
            }
        )
    }

    // Edit Item Dialog
    itemToEdit?.let { item ->
        AddEditItemDialog(
            item = item,
            categories = allCategories,
            onDismiss = { itemToEdit = null },
            onConfirm = { name, amount, cat, isRecurring, notes ->
                viewModel.updateItem(item, name, amount, cat, isRecurring, notes)
                itemToEdit = null
            }
        )
    }

    // Delete Item Confirmation
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Eliminar gasto") },
            text = { Text("¿Deseas eliminar '${item.name}' (${viewModel.formatCurrency(item.amount)}) de este mes?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem(item)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Mark All Confirmation Dialog
    if (showMarkAllDialog) {
        AlertDialog(
            onDismissRequest = { showMarkAllDialog = false },
            title = { Text("Gestión rápida de pagos") },
            text = { Text("Selecciona qué deseas hacer con los ítems de este mes:") },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.selectAllPendingForPayment()
                            showMarkAllDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Seleccionar pendientes para pagar")
                    }
                    Button(
                        onClick = {
                            viewModel.markAllPaid(true)
                            showMarkAllDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Marcar Todos como Pagados")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.markAllPaid(false)
                            showMarkAllDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Marcar Todos como Pendientes")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showMarkAllDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

/**
 * 13-Month Navigator with quick horizontal carousel & step navigation
 */
@Composable
private fun MonthNavigator(
    currentYearMonth: YearMonth,
    availableMonths: List<YearMonth>,
    onSelectMonth: (YearMonth) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    viewModel: BudgetViewModel
) {
    val isTodayMonth = currentYearMonth == YearMonth.now()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Main Top Bar: [<] Title + Badge [>]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.testTag("btn_prev_month")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Mes anterior"
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onToday() }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = viewModel.getMonthDisplayTitle(currentYearMonth),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isTodayMonth) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFDCFCE7)
                            ) {
                                Text(
                                    text = "Actual",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color(0xFF15803D)
                                )
                            }
                        }
                    }
                    if (!isTodayMonth) {
                        Text(
                            text = "Toca para volver al mes actual",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.testTag("btn_next_month")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Mes siguiente"
                    )
                }
            }

            // 13-Month Quick Selector Carousel (-6 to +6 months)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (ym in availableMonths) {
                    val isSelected = ym == currentYearMonth
                    val isCurrent = ym == YearMonth.now()
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectMonth(ym) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = viewModel.getMonthShortTitle(ym),
                                    fontWeight = if (isSelected || isCurrent) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isCurrent) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                            )
                                    )
                                }
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("chip_month_${ym.year}_${ym.monthValue}")
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * Beautiful, Modern Financial Hero Card with Material 3 styling
 */
@Composable
private fun BudgetSummaryCard(
    salary: Long,
    totalBudgeted: Long,
    totalPaid: Long,
    totalPending: Long,
    freeCash: Long,
    paidCount: Int,
    totalCount: Int,
    progressFraction: Float,
    viewModel: BudgetViewModel
) {
    val freePercent = if (salary > 0) ((freeCash.toDouble() / salary) * 100).coerceAtLeast(0.0) else 0.0
    val isPositiveFree = freeCash >= 0
    val freeColor = if (isPositiveFree) Color(0xFF16A34A) else MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("budget_summary_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Icon + Title + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Resumen del Presupuesto",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isPositiveFree) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = if (isPositiveFree) "En Equilibrio" else "Déficit",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isPositiveFree) Color(0xFF15803D) else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Big Bold Free Cash Highlight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(freeColor.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "DINERO LIBRE DISPONIBLE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = viewModel.formatCurrency(freeCash),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp
                        ),
                        color = freeColor
                    )
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.1f", freePercent)}% del sueldo",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = freeColor
                    )
                }
            }

            // Two-column split: Sueldo vs Gastos Totales
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Sueldo Card
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sueldo Base",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = viewModel.formatCurrency(salary),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Total Gastos Card
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Gastos Totales",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = viewModel.formatCurrency(totalBudgeted),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Payment Progress Breakdown
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pagado: ${viewModel.formatCurrency(totalPaid)} ($paidCount/$totalCount)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF16A34A)
                    )
                    Text(
                        text = "Pendiente: ${viewModel.formatCurrency(totalPending)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = Color(0xFF16A34A),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${(progressFraction * 100).toInt()}% completado",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (paidCount == totalCount && totalCount > 0) {
                        Text(
                            text = "🎉 ¡Todo liquidado!",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF16A34A)
                        )
                    } else {
                        Text(
                            text = "Quedan ${totalCount - paidCount} pagos por realizar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterControls(
    currentStatus: ItemStatusFilter,
    onStatusChange: (ItemStatusFilter) -> Unit,
    selectedCategory: String?,
    allCategories: List<String>,
    onCategorySelect: (String?) -> Unit,
    onMarkAllClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Status Row & Mark All Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = currentStatus == ItemStatusFilter.TODOS,
                    onClick = { onStatusChange(ItemStatusFilter.TODOS) },
                    label = { Text("Todos") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                FilterChip(
                    selected = currentStatus == ItemStatusFilter.PENDIENTES,
                    onClick = { onStatusChange(ItemStatusFilter.PENDIENTES) },
                    label = { Text("Pendientes") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                FilterChip(
                    selected = currentStatus == ItemStatusFilter.PAGADOS,
                    onClick = { onStatusChange(ItemStatusFilter.PAGADOS) },
                    label = { Text("Pagados") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            IconButton(
                onClick = onMarkAllClick,
                modifier = Modifier.testTag("btn_mark_all_dialog")
            ) {
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = "Opciones de pago masivo",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Horizontal Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategorySelect(null) },
                label = { Text("Todas las categorías") }
            )
            for (category in allCategories) {
                val catColor = CategoryVisuals.getColor(category)
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategorySelect(if (selectedCategory == category) null else category) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(catColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(category)
                        }
                    }
                )
            }
        }
    }
}

/**
 * BudgetItemRow supporting:
 * - Unpaid items: Checkbox toggles item selection for batch payment, staying in place.
 * - Paid items: Displays prominent green '✓ Pagado' badge. Clicking unchecks it back to pending.
 * - Card highlights when selected for payment.
 */
@Composable
private fun BudgetItemRow(
    item: BudgetItem,
    isSelectedForPayment: Boolean,
    onToggleSelectForPayment: () -> Unit,
    onTogglePaidDirectly: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    viewModel: BudgetViewModel
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val categoryColor = CategoryVisuals.getColor(item.category)
    val categoryIcon = CategoryVisuals.getIcon(item.category)

    val cardBorder = when {
        isSelectedForPayment -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else -> null
    }

    val cardBackgroundColor = when {
        isSelectedForPayment -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        item.isPaid -> MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("budget_item_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isPaid) 1.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (item.isPaid) {
                        onTogglePaidDirectly()
                    } else {
                        onToggleSelectForPayment()
                    }
                }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox:
            // - If paid: displays checked green checkmark. Clicking row reverts to pending.
            // - If unpaid: displays selection checkbox for batch payment.
            Checkbox(
                checked = if (item.isPaid) true else isSelectedForPayment,
                onCheckedChange = {
                    if (item.isPaid) {
                        onTogglePaidDirectly()
                    } else {
                        onToggleSelectForPayment()
                    }
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = if (item.isPaid) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("checkbox_${item.id}")
            )

            // Category Icon
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Item Name and Category / Recurrence Badges
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (item.isPaid) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (item.isPaid) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // One-time or Monthly Recurrence badge
                    if (!item.isRecurring) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFEF3C7)
                        ) {
                            Text(
                                text = "Solo este mes",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = Color(0xFFB45309)
                            )
                        }
                    }

                    if (item.isPaid) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFDCFCE7)
                        ) {
                            Text(
                                text = "✓ Pagado",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF15803D)
                            )
                        }
                    } else if (isSelectedForPayment) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Seleccionado",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Amount & Options Menu
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = viewModel.formatCurrency(item.amount),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (item.isPaid) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (item.isPaid) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurface
                )

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opciones",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (item.isPaid) {
                            DropdownMenuItem(
                                text = { Text("Marcar como pendiente") },
                                leadingIcon = { Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onTogglePaidDirectly()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Marcar como pagado ya") },
                                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A)) },
                                onClick = {
                                    menuExpanded = false
                                    onTogglePaidDirectly()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Editar") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(statusFilter: ItemStatusFilter) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = when (statusFilter) {
                    ItemStatusFilter.PENDIENTES -> "¡Excelente! No tienes gastos pendientes en este filtro."
                    ItemStatusFilter.PAGADOS -> "No hay gastos marcados como pagados todavía."
                    ItemStatusFilter.TODOS -> "No hay gastos registrados en este mes o categoría."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditItemDialog(
    item: BudgetItem?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, amount: Long, category: String, isRecurring: Boolean, notes: String?) -> Unit
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var amountText by remember { mutableStateOf(item?.amount?.toString() ?: "") }
    var selectedCat by remember { mutableStateOf(item?.category ?: categories.firstOrNull() ?: "Gastos Hogar") }
    var isRecurring by remember { mutableStateOf(item?.isRecurring ?: true) }
    var notes by remember { mutableStateOf(item?.notes ?: "") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Nuevo Gasto" else "Editar Gasto") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        hasError = false
                    },
                    label = { Text("Nombre del gasto / ítem") },
                    placeholder = { Text("Ej: Internet, Tarjeta, etc.") },
                    singleLine = true,
                    isError = hasError && name.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_item_name")
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }
                        amountText = filtered
                        hasError = false
                    },
                    label = { Text("Valor en pesos (COP)") },
                    placeholder = { Text("Ej: 1400000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = hasError && (amountText.toLongOrNull() ?: 0L) <= 0L,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_item_amount")
                )

                // Category selector
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCat,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        for (cat in categories) {
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    selectedCat = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Recurrence Selection (Mensual vs Solo este mes)
                Text(
                    text = "¿Con qué frecuencia ocurre este gasto?",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 1: Monthly Recurring
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isRecurring) 2.dp else 1.dp,
                                color = if (isRecurring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { isRecurring = true }
                            .padding(10.dp),
                        color = if (isRecurring) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = null,
                                tint = if (isRecurring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Mensual Fijo",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isRecurring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Se repite cada mes",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Option 2: One-off / Only this month
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (!isRecurring) 2.dp else 1.dp,
                                color = if (!isRecurring) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { isRecurring = false }
                            .padding(10.dp),
                        color = if (!isRecurring) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surface
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = if (!isRecurring) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Solo Este Mes",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (!isRecurring) Color(0xFF92400E) else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Gasto puntual único",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas u observaciones (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toLongOrNull() ?: 0L
                    if (name.isBlank() || amount <= 0L) {
                        hasError = true
                    } else {
                        onConfirm(name, amount, selectedCat, isRecurring, notes)
                    }
                },
                modifier = Modifier.testTag("btn_save_item")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
