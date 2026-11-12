package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.FinancialAiService
import com.example.data.model.BudgetCategories
import com.example.data.model.BudgetItem
import com.example.data.model.ChatMessage
import com.example.data.model.UserProfile
import com.example.data.repository.BudgetRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

sealed interface DiagnosisUiState {
    data object Idle : DiagnosisUiState
    data object Loading : DiagnosisUiState
    data class Success(val text: String) : DiagnosisUiState
    data class Error(val message: String) : DiagnosisUiState
}

enum class ItemStatusFilter {
    TODOS,
    PENDIENTES,
    PAGADOS
}

data class FinancialCalculations(
    val salary: Long = 11300000L,
    val totalBudgeted: Long = 0L,
    val totalPaid: Long = 0L,
    val totalPending: Long = 0L,
    val freeCash: Long = 0L,
    val totalDebt: Long = 0L,
    val totalSavings: Long = 0L,
    val paidCount: Int = 0,
    val totalCount: Int = 0,
    val progressFraction: Float = 0f,
    val healthScore: Int = 78,
    val categoryTotals: Map<String, Long> = emptyMap()
)

class BudgetViewModel(
    private val repository: BudgetRepository,
    private val aiService: FinancialAiService = FinancialAiService()
) : ViewModel() {

    private val monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    private val displayMonthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "CO"))
    private val shortMonthFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale("es", "CO"))

    private val _currentYearMonth = MutableStateFlow(YearMonth.now())
    val currentYearMonth: StateFlow<YearMonth> = _currentYearMonth.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _statusFilter = MutableStateFlow(ItemStatusFilter.TODOS)
    val statusFilter: StateFlow<ItemStatusFilter> = _statusFilter.asStateFlow()

    private val _diagnosisState = MutableStateFlow<DiagnosisUiState>(DiagnosisUiState.Idle)
    val diagnosisState: StateFlow<DiagnosisUiState> = _diagnosisState.asStateFlow()

    private val _isSendingChat = MutableStateFlow(false)
    val isSendingChat: StateFlow<Boolean> = _isSendingChat.asStateFlow()

    private val _chatErrorMessage = MutableStateFlow<String?>(null)
    val chatErrorMessage: StateFlow<String?> = _chatErrorMessage.asStateFlow()

    // Multi-selection of item IDs for bulk payment
    private val _selectedItemIdsForPayment = MutableStateFlow<Set<String>>(emptySet())
    val selectedItemIdsForPayment: StateFlow<Set<String>> = _selectedItemIdsForPayment.asStateFlow()

    // Optimistic item paid overrides: Map<itemId, isPaid>
    private val _optimisticPaidStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    // Local in-memory chat list to ensure immediate responsiveness
    private val _localChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    // Dynamic list of all categories (defaults + user custom categories)
    val allCategories: StateFlow<List<String>> = userProfile.map { profile ->
        val custom = profile?.customCategories ?: emptyList()
        val combined = (BudgetCategories.ALL + custom).distinct()
        combined
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), BudgetCategories.ALL)

    // Raw items for current month with optimistic paid overrides applied
    val monthItems: StateFlow<List<BudgetItem>> = _currentYearMonth
        .flatMapLatest { ym ->
            repository.observeMonthItems(ym.format(monthFormatter))
        }
        .combine(_optimisticPaidStatus) { items, overrides ->
            items.map { item ->
                val override = overrides[item.id]
                if (override != null) item.copy(isPaid = override) else item
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Filtered items for display - Sorted stably by amount descending so items don't jump around
    val filteredItems: StateFlow<List<BudgetItem>> = combine(
        monthItems,
        _selectedCategory,
        _statusFilter
    ) { items, cat, status ->
        items.filter { item ->
            val matchCat = cat == null || item.category == cat
            val matchStatus = when (status) {
                ItemStatusFilter.TODOS -> true
                ItemStatusFilter.PENDIENTES -> !item.isPaid
                ItemStatusFilter.PAGADOS -> item.isPaid
            }
            matchCat && matchStatus
        }.sortedWith(compareBy({ it.isPaid }, { -it.amount }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Total amount of currently selected items for payment
    val selectedPaymentTotalAmount: StateFlow<Long> = combine(
        monthItems,
        _selectedItemIdsForPayment
    ) { items, selectedIds ->
        items.filter { selectedIds.contains(it.id) }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    // Financial Metrics
    val financialMetrics: StateFlow<FinancialCalculations> = combine(
        userProfile,
        monthItems
    ) { profile, items ->
        val salary = profile?.monthlySalary ?: 11300000L
        val totalBudgeted = items.sumOf { it.amount }
        val totalPaid = items.filter { it.isPaid }.sumOf { it.amount }
        val totalPending = totalBudgeted - totalPaid
        val freeCash = salary - totalBudgeted
        val paidCount = items.count { it.isPaid }
        val totalCount = items.size
        val progressFraction = if (totalBudgeted > 0) (totalPaid.toFloat() / totalBudgeted.toFloat()) else 0f

        val totalDebt = items.filter { it.category == BudgetCategories.DEUDA_CREDITOS }.sumOf { it.amount }
        val totalSavings = items.filter {
            it.category == BudgetCategories.AHORRO_INVERSION || it.name.startsWith("Ahorro", ignoreCase = true)
        }.sumOf { it.amount }

        val categoryTotals = items.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        // Dynamic health score based on debt-to-income, savings rate, and free cash
        var score = 70
        if (salary > 0) {
            val savingsRate = totalSavings.toDouble() / salary
            if (savingsRate >= 0.20) score += 12 else if (savingsRate >= 0.10) score += 6

            val debtRatio = totalDebt.toDouble() / salary
            if (debtRatio <= 0.25) score += 10 else if (debtRatio <= 0.35) score += 5 else score -= 10

            if (freeCash > 1000000L) score += 8 else if (freeCash > 0) score += 4 else score -= 15
        }
        val clampedScore = score.coerceIn(40, 98)

        FinancialCalculations(
            salary = salary,
            totalBudgeted = totalBudgeted,
            totalPaid = totalPaid,
            totalPending = totalPending,
            freeCash = freeCash,
            totalDebt = totalDebt,
            totalSavings = totalSavings,
            paidCount = paidCount,
            totalCount = totalCount,
            progressFraction = progressFraction,
            healthScore = clampedScore,
            categoryTotals = categoryTotals
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), FinancialCalculations())

    // Merged Chat Messages (remote + local immediate)
    val chatMessages: StateFlow<List<ChatMessage>> = combine(
        repository.observeChatMessages(),
        _localChatMessages
    ) { remote, local ->
        val map = LinkedHashMap<String, ChatMessage>()
        for (m in remote) {
            map[m.id] = m
        }
        for (m in local) {
            map[m.id] = m
        }
        map.values.sortedBy { it.createdAt?.toDate()?.time ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        // Initialize user profile and default 13-month window (-6 to +6)
        viewModelScope.launch {
            repository.ensureProfileAndDefaults()
            val salary = userProfile.value?.monthlySalary ?: 11300000L
            val currentKey = _currentYearMonth.value.format(monthFormatter)
            repository.ensureMonthInitialized(currentKey, salary)

            // Pre-initialize nearby 6 months in background so switching is always instant
            val now = YearMonth.now()
            for (offset in -6..6) {
                val ym = now.plusMonths(offset.toLong())
                repository.ensureMonthInitialized(ym.format(monthFormatter), salary)
            }
        }
    }

    // Returns the 13-month list: 6 months back, current month, 6 months forward
    fun getAvailableMonths(): List<YearMonth> {
        val now = YearMonth.now()
        return (-6..6).map { now.plusMonths(it.toLong()) }
    }

    fun getMonthDisplayTitle(ym: YearMonth = _currentYearMonth.value): String {
        val title = ym.format(displayMonthFormatter)
        return title.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "CO")) else it.toString() }
    }

    fun getMonthShortTitle(ym: YearMonth): String {
        val title = ym.format(shortMonthFormatter)
        return title.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "CO")) else it.toString() }
    }

    fun selectMonth(ym: YearMonth) {
        _currentYearMonth.value = ym
        _optimisticPaidStatus.value = emptyMap()
        _selectedItemIdsForPayment.value = emptySet()
        viewModelScope.launch {
            val salary = userProfile.value?.monthlySalary ?: 11300000L
            repository.ensureMonthInitialized(ym.format(monthFormatter), salary)
        }
    }

    fun previousMonth() {
        val prev = _currentYearMonth.value.minusMonths(1)
        selectMonth(prev)
    }

    fun nextMonth() {
        val next = _currentYearMonth.value.plusMonths(1)
        selectMonth(next)
    }

    fun setCurrentMonth() {
        val now = YearMonth.now()
        selectMonth(now)
    }

    fun selectCategory(cat: String?) {
        _selectedCategory.value = cat
    }

    fun setStatusFilter(status: ItemStatusFilter) {
        _statusFilter.value = status
    }

    // Toggle multi-selection for bulk payment
    fun toggleSelectItemForPayment(itemId: String) {
        _selectedItemIdsForPayment.update { current ->
            if (current.contains(itemId)) current - itemId else current + itemId
        }
    }

    fun selectAllPendingForPayment() {
        val pendingIds = monthItems.value.filter { !it.isPaid }.map { it.id }.toSet()
        _selectedItemIdsForPayment.value = pendingIds
    }

    fun clearPaymentSelection() {
        _selectedItemIdsForPayment.value = emptySet()
    }

    // Mark all selected items as paid at once
    fun markSelectedAsPaid() {
        val selectedIds = _selectedItemIdsForPayment.value.toList()
        if (selectedIds.isEmpty()) return

        // 1. Instantly reflect in optimistic UI
        val optimisticMap = selectedIds.associateWith { true }
        _optimisticPaidStatus.update { it + optimisticMap }
        _selectedItemIdsForPayment.value = emptySet()

        // 2. Persist in background batch to Firestore
        viewModelScope.launch {
            val monthKey = _currentYearMonth.value.format(monthFormatter)
            repository.markItemsPaid(monthKey, true, selectedIds)
        }
    }

    // Toggle Item Paid with INSTANT optimistic UI reflection (single item toggle)
    fun toggleItemPaid(item: BudgetItem) {
        val currentPaid = _optimisticPaidStatus.value[item.id] ?: item.isPaid
        val newPaid = !currentPaid
        _optimisticPaidStatus.update { it + (item.id to newPaid) }
        // Remove from selection if toggled directly
        _selectedItemIdsForPayment.update { it - item.id }

        viewModelScope.launch {
            val result = repository.toggleItemPaid(item.copy(isPaid = currentPaid))
            if (result.isFailure) {
                // Revert optimistic change on failure
                _optimisticPaidStatus.update { it + (item.id to currentPaid) }
            }
        }
    }

    fun addItem(name: String, amount: Long, category: String, isRecurring: Boolean, notes: String?) {
        viewModelScope.launch {
            val monthKey = _currentYearMonth.value.format(monthFormatter)
            val newItem = BudgetItem(
                monthKey = monthKey,
                name = name.trim(),
                amount = amount,
                category = category,
                isPaid = false,
                isRecurring = isRecurring,
                notes = notes?.trim().takeIf { !it.isNullOrBlank() }
            )
            repository.addItem(newItem)
        }
    }

    fun updateItem(item: BudgetItem, name: String, amount: Long, category: String, isRecurring: Boolean, notes: String?) {
        viewModelScope.launch {
            val updated = item.copy(
                name = name.trim(),
                amount = amount,
                category = category,
                isRecurring = isRecurring,
                notes = notes?.trim().takeIf { !it.isNullOrBlank() }
            )
            repository.updateItem(updated)
        }
    }

    fun deleteItem(item: BudgetItem) {
        _selectedItemIdsForPayment.update { it - item.id }
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    fun markAllPaid(isPaid: Boolean) {
        val current = monthItems.value
        val updates = current.associate { it.id to isPaid }
        _optimisticPaidStatus.update { it + updates }
        _selectedItemIdsForPayment.value = emptySet()

        viewModelScope.launch {
            val monthKey = _currentYearMonth.value.format(monthFormatter)
            repository.markAllItemsPaid(monthKey, isPaid, current)
        }
    }

    fun updateSalary(newSalary: Long) {
        viewModelScope.launch {
            repository.updateSalary(newSalary)
        }
    }

    // Category Management
    fun addCategory(categoryName: String) {
        val trimmed = categoryName.trim()
        if (trimmed.isBlank()) return
        val current = userProfile.value?.customCategories ?: emptyList()
        if (current.none { it.equals(trimmed, ignoreCase = true) } && BudgetCategories.ALL.none { it.equals(trimmed, ignoreCase = true) }) {
            val updated = current + trimmed
            viewModelScope.launch {
                repository.updateCustomCategories(updated)
            }
        }
    }

    fun renameCategory(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == oldName) return
        val current = userProfile.value?.customCategories ?: emptyList()
        val updated = current.map { if (it.equals(oldName, ignoreCase = true)) trimmed else it }
        viewModelScope.launch {
            repository.updateCustomCategories(updated)
            // Also update any items in current month using oldName
            for (item in monthItems.value) {
                if (item.category.equals(oldName, ignoreCase = true)) {
                    repository.updateItem(item.copy(category = trimmed))
                }
            }
        }
    }

    fun deleteCategory(categoryName: String) {
        val current = userProfile.value?.customCategories ?: emptyList()
        val updated = current.filterNot { it.equals(categoryName, ignoreCase = true) }
        viewModelScope.launch {
            repository.updateCustomCategories(updated)
            if (_selectedCategory.value == categoryName) {
                _selectedCategory.value = null
            }
        }
    }

    fun generateDiagnosis() {
        viewModelScope.launch {
            _diagnosisState.value = DiagnosisUiState.Loading
            val profile = userProfile.value ?: UserProfile()
            val items = monthItems.value
            val monthKey = getMonthDisplayTitle()
            val result = aiService.generateDiagnosis(profile, items, monthKey)
            if (result.isSuccess) {
                _diagnosisState.value = DiagnosisUiState.Success(result.getOrThrow())
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage
                    ?: "No se pudo generar el diagnóstico financiero."
                _diagnosisState.value = DiagnosisUiState.Error(errorMsg)
            }
        }
    }

    fun sendChatMessage(text: String) {
        if (text.isBlank() || _isSendingChat.value) return
        val cleanText = text.trim()
        val localUserMsgId = "local_user_${UUID.randomUUID()}"
        val userMsg = ChatMessage(
            id = localUserMsgId,
            role = "user",
            text = cleanText,
            createdAt = Timestamp.now()
        )
        // 1. Immediately display user message in UI
        _localChatMessages.update { it + userMsg }
        _isSendingChat.value = true
        _chatErrorMessage.value = null

        viewModelScope.launch {
            // Asynchronously persist user message
            repository.addChatMessage(role = "user", text = cleanText)

            // 2. Query Gemini
            val salary = userProfile.value?.monthlySalary ?: 11300000L
            val items = monthItems.value
            val history = chatMessages.value
            val result = aiService.sendChatMessage(cleanText, history, salary, items)

            val replyText = if (result.isSuccess) {
                result.getOrThrow()
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Conexión limitada"
                _chatErrorMessage.value = err
                // Fallback expert financial guidance tailored to Colombian context
                generateFallbackFinancialAdvice(cleanText, salary, items)
            }

            val localModelMsgId = "local_model_${UUID.randomUUID()}"
            val modelMsg = ChatMessage(
                id = localModelMsgId,
                role = "model",
                text = replyText,
                createdAt = Timestamp.now()
            )
            _localChatMessages.update { it + modelMsg }
            repository.addChatMessage(role = "model", text = replyText)
            _isSendingChat.value = false
        }
    }

    private fun generateFallbackFinancialAdvice(question: String, salary: Long, items: List<BudgetItem>): String {
        val totalExpenses = items.sumOf { it.amount }
        val free = salary - totalExpenses
        val lower = question.lowercase()

        return when {
            lower.contains("deuda") || lower.contains("liquidar") || lower.contains("pagar") -> {
                """
                📊 **Estrategia recomendada para tus deudas:**
                1. **Método Avalancha / Bola de Nieve:** Identifica la deuda con mayor tasa de interés (como tu Tarjeta de Crédito Rappi) y asigna abonos extraordinarios de tu dinero libre (${formatCurrency(free)}).
                2. Para el crédito de la moto ($1.600.000 COP/mes), continúa con las cuotas puntuales y ahorra el excedente para abono a capital que reduzca el plazo.
                3. Evita diferir compras en más de 1 cuota en tarjeta de crédito.
                """.trimIndent()
            }
            lower.contains("fvp") || lower.contains("retencion") || lower.contains("impuesto") || lower.contains("pension") -> {
                """
                💡 **Optimización Tributaria (Estatuto Tributario Art. 126-1):**
                - Tus aportes a Fondo de Pensión Voluntaria (FVP) son rentas exentas hasta el 30% de tu ingreso laboral (límite 3.800 UVT).
                - Si aportas periódicamente al FVP, tu empresa disminuye la base gravable para la retención en la fuente mensual, ahorrándote dinero legalmente.
                - Además, tu medicina prepagada es deducible hasta 16 UVT mensuales.
                """.trimIndent()
            }
            lower.contains("cuenta") || lower.contains("guardar") || lower.contains("rendimiento") || lower.contains("emergencia") -> {
                """
                🏦 **Cuentas de Alto Rendimiento en Colombia:**
                - Para tu fondo de emergencia y dinero libre (${formatCurrency(free)}), utiliza cuentas con rendimientos diarios efectivos anuales (E.A.) competitivos como las 'cajitas' de Nu, bolsillos de Lulo Bank, Pibank o Ualá.
                - Tienen liquidez 24/7 y seguro de depósitos FOGAFÍN hasta por $50.000.000 COP.
                """.trimIndent()
            }
            else -> {
                """
                💼 **Diagnóstico para tu consulta:**
                - Sueldo actual: ${formatCurrency(salary)}
                - Total gastos registrados: ${formatCurrency(totalExpenses)}
                - Dinero disponible: ${formatCurrency(free)}

                Recomendación clave: Con ${formatCurrency(free)} libres este mes, el paso ideal es destinar el 50% ($${free / 2 / 1000}k) a un fondo de ahorro o abono a capital, y el 50% restante a tus metas personales y ocio planificado.
                """.trimIndent()
            }
        }
    }

    fun clearChat() {
        _localChatMessages.value = emptyList()
        viewModelScope.launch {
            repository.clearChatMessages(chatMessages.value)
        }
    }

    fun formatCurrency(amount: Long): String {
        val format = NumberFormat.getCurrencyInstance(Locale("es", "CO")).apply {
            maximumFractionDigits = 0
        }
        return format.format(amount)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repo = BudgetRepository(context)
            return BudgetViewModel(repo) as T
        }
    }
}
