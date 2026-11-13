package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.ai.FinancialAiService
import com.example.data.model.BudgetCategories
import com.example.data.model.BudgetItem
import com.example.data.model.CategoryUsageItem
import com.example.data.model.ChatMessage
import com.example.data.model.UserProfile
import com.example.data.repository.BudgetDataSource
import com.example.data.repository.BudgetRepository
import com.example.dev.DevBudgetRepository
import com.example.dev.DevTesterAccount
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

sealed interface DeleteCategoryUiState {
    data object Hidden : DeleteCategoryUiState
    data class Checking(val category: String) : DeleteCategoryUiState
    data class ConfirmEmpty(
        val category: String,
        val isDeleting: Boolean = false,
        val error: String? = null
    ) : DeleteCategoryUiState
    data class ConfirmInUse(
        val category: String,
        val itemsInUse: List<CategoryUsageItem>,
        val targetCategories: List<String>,
        val isDeleting: Boolean = false,
        val error: String? = null
    ) : DeleteCategoryUiState
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
    private val repository: BudgetDataSource,
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

    private val _streamingChatText = MutableStateFlow<String?>(null)
    val streamingChatText: StateFlow<String?> = _streamingChatText.asStateFlow()

    private val _chatErrorMessage = MutableStateFlow<String?>(null)
    val chatErrorMessage: StateFlow<String?> = _chatErrorMessage.asStateFlow()

    // Multi-selection of item IDs for bulk payment
    private val _selectedItemIdsForPayment = MutableStateFlow<Set<String>>(emptySet())
    val selectedItemIdsForPayment: StateFlow<Set<String>> = _selectedItemIdsForPayment.asStateFlow()

    // Optimistic item paid overrides: Map<itemId, isPaid>
    private val _optimisticPaidStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    // Local in-memory chat list to ensure immediate responsiveness
    private val _localChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())

    private val _deleteCategoryState = MutableStateFlow<DeleteCategoryUiState>(DeleteCategoryUiState.Hidden)
    val deleteCategoryState: StateFlow<DeleteCategoryUiState> = _deleteCategoryState.asStateFlow()

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val hiddenTabs: StateFlow<Set<String>> = userProfile
        .map { profile ->
            if (profile == null) {
                emptySet()
            } else {
                val explicit = profile.hiddenTabs.toSet() - "SETTINGS"
                if (!profile.showSavingsTab) explicit + "SAVINGS" else explicit
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val showSavingsTab: StateFlow<Boolean> = hiddenTabs
        .map { "SAVINGS" !in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    // Dynamic list of all categories (active defaults + user custom categories, excluding deleted)
    val allCategories: StateFlow<List<String>> = userProfile.map { profile ->
        val stored = profile?.customCategories ?: emptyList()
        val deletedBase = stored
            .filter { it.startsWith(BudgetCategories.DELETED_PREFIX) }
            .map { it.removePrefix(BudgetCategories.DELETED_PREFIX) }
        val customActive = stored.filterNot { it.startsWith(BudgetCategories.DELETED_PREFIX) }
        val baseActive = BudgetCategories.ALL.filterNot { base ->
            deletedBase.any { it.equals(base, ignoreCase = true) }
        }
        (baseActive + customActive).distinct()
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

    // Merged Chat Messages (remote + local immediate, deduplicated by shared messageId)
    val chatMessages: StateFlow<List<ChatMessage>> = combine(
        repository.observeChatMessages(),
        _localChatMessages
    ) { remote, local ->
        val map = LinkedHashMap<String, ChatMessage>()
        for (m in local) {
            map[m.id] = m
        }
        for (m in remote) {
            val existingLocal = map[m.id]
            map[m.id] = if (m.createdAt == null && existingLocal?.createdAt != null) {
                m.copy(createdAt = existingLocal.createdAt)
            } else {
                m
            }
        }
        map.values.sortedWith(
            compareBy<ChatMessage> { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                .thenBy { it.id }
        )
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

    fun setShowSavingsTab(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateShowSavingsTab(enabled)
        }
    }

    fun setTabVisible(tabKey: String, visible: Boolean, allTabKeys: List<String>) {
        if (tabKey == "SETTINGS") return
        val currentHidden = hiddenTabs.value - "SETTINGS"
        val updatedHidden = if (visible) {
            currentHidden - tabKey
        } else {
            (currentHidden + tabKey) - "SETTINGS"
        }
        viewModelScope.launch {
            repository.updateHiddenTabs(updatedHidden.toList())
        }
    }

    fun showAllTabs() {
        if (hiddenTabs.value.isEmpty()) return
        viewModelScope.launch {
            repository.updateHiddenTabs(emptyList())
        }
    }

    // Category Management
    fun addCategory(categoryName: String) {
        val trimmed = categoryName.trim()
        if (trimmed.isBlank()) return
        if (allCategories.value.any { it.equals(trimmed, ignoreCase = true) }) return

        val stored = userProfile.value?.customCategories ?: emptyList()
        val baseMatch = BudgetCategories.ALL.firstOrNull { it.equals(trimmed, ignoreCase = true) }
        val updated = if (baseMatch != null) {
            // Re-enable previously deleted base category
            stored.filterNot { it.equals("${BudgetCategories.DELETED_PREFIX}$baseMatch", ignoreCase = true) }
        } else {
            (stored + trimmed).distinct()
        }
        viewModelScope.launch {
            repository.updateCustomCategories(updated)
        }
    }

    fun renameCategory(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == oldName) return
        if (allCategories.value.any { !it.equals(oldName, ignoreCase = true) && it.equals(trimmed, ignoreCase = true) }) return

        val stored = userProfile.value?.customCategories ?: emptyList()
        val baseOld = BudgetCategories.ALL.firstOrNull { it.equals(oldName, ignoreCase = true) }
        val baseNew = BudgetCategories.ALL.firstOrNull { it.equals(trimmed, ignoreCase = true) }

        val updatedList = stored.toMutableList()
        if (baseNew != null) {
            updatedList.removeAll { it.equals("${BudgetCategories.DELETED_PREFIX}$baseNew", ignoreCase = true) }
        }
        if (baseOld != null) {
            val delMarker = "${BudgetCategories.DELETED_PREFIX}$baseOld"
            if (updatedList.none { it.equals(delMarker, ignoreCase = true) }) {
                updatedList.add(delMarker)
            }
            if (baseNew == null && updatedList.none { it.equals(trimmed, ignoreCase = true) }) {
                updatedList.add(trimmed)
            }
        } else {
            updatedList.removeAll { it.equals(oldName, ignoreCase = true) }
            if (baseNew == null && updatedList.none { it.equals(trimmed, ignoreCase = true) }) {
                updatedList.add(trimmed)
            }
        }

        viewModelScope.launch {
            repository.updateCustomCategories(updatedList.distinct())
            repository.reassignCategoryAcrossAllMonths(
                oldCategory = oldName,
                reassignmentsByNormalizedName = emptyMap(),
                fallbackCategory = trimmed
            )
            if (_selectedCategory.value?.equals(oldName, ignoreCase = true) == true) {
                _selectedCategory.value = trimmed
            }
        }
    }

    fun requestDeleteCategory(categoryName: String) {
        if (allCategories.value.size <= 1) return
        _deleteCategoryState.value = DeleteCategoryUiState.Checking(categoryName)

        viewModelScope.launch {
            val targets = allCategories.value.filterNot { it.equals(categoryName, ignoreCase = true) }
            val result = repository.findItemsUsingCategory(categoryName, monthItems.value)
            val usageItems = if (result.isSuccess) {
                result.getOrThrow()
            } else {
                monthItems.value
                    .filter { it.category.equals(categoryName, ignoreCase = true) }
                    .groupBy { it.name.trim().lowercase() }
                    .values
                    .map { list ->
                        CategoryUsageItem(
                            name = list.first().name,
                            amount = list.first().amount,
                            occurrences = list.size
                        )
                    }
                    .sortedByDescending { it.amount }
            }

            _deleteCategoryState.value = if (usageItems.isEmpty()) {
                DeleteCategoryUiState.ConfirmEmpty(category = categoryName)
            } else {
                DeleteCategoryUiState.ConfirmInUse(
                    category = categoryName,
                    itemsInUse = usageItems,
                    targetCategories = targets
                )
            }
        }
    }

    fun dismissDeleteCategory() {
        _deleteCategoryState.value = DeleteCategoryUiState.Hidden
    }

    fun confirmDeleteCategory(
        reassignmentsByNormalizedName: Map<String, String> = emptyMap(),
        fallbackCategory: String? = null
    ) {
        val currentState = _deleteCategoryState.value
        val categoryToDelete = when (currentState) {
            is DeleteCategoryUiState.ConfirmEmpty -> {
                if (currentState.isDeleting) return
                _deleteCategoryState.value = currentState.copy(isDeleting = true, error = null)
                currentState.category
            }
            is DeleteCategoryUiState.ConfirmInUse -> {
                if (currentState.isDeleting) return
                _deleteCategoryState.value = currentState.copy(isDeleting = true, error = null)
                currentState.category
            }
            else -> return
        }

        viewModelScope.launch {
            if (currentState is DeleteCategoryUiState.ConfirmInUse) {
                val reassignResult = repository.reassignCategoryAcrossAllMonths(
                    oldCategory = categoryToDelete,
                    reassignmentsByNormalizedName = reassignmentsByNormalizedName,
                    fallbackCategory = fallbackCategory ?: currentState.targetCategories.firstOrNull()
                )
                if (reassignResult.isFailure) {
                    _deleteCategoryState.value = currentState.copy(
                        isDeleting = false,
                        error = "No se pudieron reasignar los ítems. Intenta de nuevo."
                    )
                    return@launch
                }
            }

            val stored = userProfile.value?.customCategories ?: emptyList()
            val updated = stored.filterNot { it.equals(categoryToDelete, ignoreCase = true) }.toMutableList()
            val baseMatch = BudgetCategories.ALL.firstOrNull { it.equals(categoryToDelete, ignoreCase = true) }
            if (baseMatch != null) {
                val marker = "${BudgetCategories.DELETED_PREFIX}$baseMatch"
                if (updated.none { it.equals(marker, ignoreCase = true) }) {
                    updated.add(marker)
                }
            }

            val updateResult = repository.updateCustomCategories(updated.distinct())
            if (updateResult.isFailure) {
                val errMsg = "No se pudo eliminar la categoría. Intenta de nuevo."
                _deleteCategoryState.value = when (currentState) {
                    is DeleteCategoryUiState.ConfirmEmpty -> currentState.copy(isDeleting = false, error = errMsg)
                    is DeleteCategoryUiState.ConfirmInUse -> currentState.copy(isDeleting = false, error = errMsg)
                    else -> DeleteCategoryUiState.Hidden
                }
                return@launch
            }

            if (_selectedCategory.value?.equals(categoryToDelete, ignoreCase = true) == true) {
                _selectedCategory.value = null
            }
            _deleteCategoryState.value = DeleteCategoryUiState.Hidden
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
        val historyBeforeCurrent = chatMessages.value
        val userMsgId = UUID.randomUUID().toString()
        val userMsg = ChatMessage(
            id = userMsgId,
            role = "user",
            text = cleanText,
            createdAt = Timestamp.now()
        )
        // 1. Immediately display user message in UI with the exact ID used in Firestore
        _localChatMessages.update { it + userMsg }
        _isSendingChat.value = true
        _streamingChatText.value = null
        _chatErrorMessage.value = null

        viewModelScope.launch {
            // Persist user message in parallel so it does not block the AI request
            launch {
                repository.addChatMessage(role = "user", text = cleanText, messageId = userMsgId)
            }

            requestAiReply(
                userQuestion = cleanText,
                historyBeforeQuestion = historyBeforeCurrent
            )
        }
    }

    fun retryLastChatMessage() {
        if (_isSendingChat.value) return
        val messages = chatMessages.value
        val lastUserIdx = messages.indexOfLast { it.role == "user" }
        if (lastUserIdx < 0) return
        val lastUserMsg = messages[lastUserIdx]
        val historyBeforeQuestion = messages.take(lastUserIdx)

        _isSendingChat.value = true
        _streamingChatText.value = null
        _chatErrorMessage.value = null

        viewModelScope.launch {
            requestAiReply(
                userQuestion = lastUserMsg.text,
                historyBeforeQuestion = historyBeforeQuestion
            )
        }
    }

    fun dismissChatError() {
        _chatErrorMessage.value = null
    }

    private suspend fun requestAiReply(
        userQuestion: String,
        historyBeforeQuestion: List<ChatMessage>
    ) {
        val salary = userProfile.value?.monthlySalary ?: 11300000L
        val items = monthItems.value
        val monthLabel = getMonthDisplayTitle()

        try {
            var finalReply = ""
            aiService.streamChatMessage(
                userMessage = userQuestion,
                history = historyBeforeQuestion,
                currentSalary = salary,
                items = items,
                monthLabel = monthLabel
            ).collect { partial ->
                finalReply = partial
                _streamingChatText.value = partial
            }

            if (finalReply.isNotBlank()) {
                val modelMsgId = UUID.randomUUID().toString()
                val modelMsg = ChatMessage(
                    id = modelMsgId,
                    role = "model",
                    text = finalReply,
                    createdAt = Timestamp.now()
                )
                _localChatMessages.update { it + modelMsg }
                repository.addChatMessage(role = "model", text = finalReply, messageId = modelMsgId)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _chatErrorMessage.value = e.localizedMessage
                ?: "No se pudo conectar con Gemini AI. Intenta de nuevo."
        } finally {
            _streamingChatText.value = null
            _isSendingChat.value = false
        }
    }

    fun clearChat() {
        val snapshot = chatMessages.value
        _localChatMessages.value = emptyList()
        _streamingChatText.value = null
        _chatErrorMessage.value = null
        viewModelScope.launch {
            repository.clearChatMessages(snapshot)
        }
    }

    fun formatCurrency(amount: Long): String {
        val format = NumberFormat.getCurrencyInstance(Locale("es", "CO")).apply {
            maximumFractionDigits = 0
        }
        return format.format(amount)
    }

    class Factory(
        private val context: Context,
        private val devAccount: DevTesterAccount? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repo: BudgetDataSource = if (BuildConfig.DEBUG && devAccount != null) {
                DevBudgetRepository(devAccount)
            } else {
                BudgetRepository(context)
            }
            return BudgetViewModel(repo) as T
        }
    }
}
