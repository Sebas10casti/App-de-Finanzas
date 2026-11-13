package com.example.data.repository

import com.example.data.model.BudgetItem
import com.example.data.model.CategoryUsageItem
import com.example.data.model.ChatMessage
import com.example.data.model.MonthlyBudget
import com.example.data.model.PocketMovement
import com.example.data.model.SavingsPocket
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface BudgetDataSource {
    fun observeUserProfile(): Flow<UserProfile?>
    suspend fun ensureProfileAndDefaults(): Result<Unit>
    suspend fun updateSalary(newSalary: Long): Result<Unit>
    suspend fun updateCustomCategories(categories: List<String>): Result<Unit>
    suspend fun updateShowSavingsTab(enabled: Boolean): Result<Unit>
    suspend fun updateHiddenTabs(hiddenTabs: List<String>): Result<Unit>
    suspend fun findItemsUsingCategory(
        categoryName: String,
        currentMonthItems: List<BudgetItem> = emptyList()
    ): Result<List<CategoryUsageItem>>
    suspend fun reassignCategoryAcrossAllMonths(
        oldCategory: String,
        reassignmentsByNormalizedName: Map<String, String>,
        fallbackCategory: String? = null
    ): Result<Unit>
    fun observeMonthlyBudget(monthKey: String): Flow<MonthlyBudget?>
    fun observeMonthItems(monthKey: String): Flow<List<BudgetItem>>
    suspend fun ensureMonthInitialized(monthKey: String, currentSalary: Long): Result<Unit>
    suspend fun toggleItemPaid(item: BudgetItem): Result<Unit>
    suspend fun addItem(item: BudgetItem): Result<String>
    suspend fun updateItem(item: BudgetItem): Result<Unit>
    suspend fun deleteItem(item: BudgetItem): Result<Unit>
    suspend fun markAllItemsPaid(monthKey: String, isPaid: Boolean, items: List<BudgetItem>): Result<Unit>
    suspend fun markItemsPaid(monthKey: String, isPaid: Boolean, itemIds: List<String>): Result<Unit>
    fun observeChatMessages(): Flow<List<ChatMessage>>
    suspend fun addChatMessage(role: String, text: String, messageId: String? = null): Result<Unit>
    suspend fun clearChatMessages(messages: List<ChatMessage>): Result<Unit>
}

interface SavingsDataSource {
    fun observePockets(): Flow<List<SavingsPocket>>
    fun observeMovements(pocketId: String): Flow<List<PocketMovement>>
    suspend fun createPocket(
        name: String,
        initialAmount: Long,
        targetAmount: Long?,
        iconKey: String,
        colorKey: String
    ): Result<String>
    suspend fun updatePocketDetails(
        pocketId: String,
        name: String,
        targetAmount: Long?,
        iconKey: String,
        colorKey: String
    ): Result<Unit>
    suspend fun recordMovement(
        pocketId: String,
        type: String,
        amount: Long,
        note: String?
    ): Result<Long>
    suspend fun generateOrRegenerateShareCode(pocket: SavingsPocket): Result<String>
    suspend fun deactivateShareCode(pocket: SavingsPocket): Result<Unit>
    suspend fun joinPocketByCode(rawCode: String): Result<String>
    suspend fun leavePocket(pocketId: String): Result<Unit>
    suspend fun removeMember(pocket: SavingsPocket, memberUid: String): Result<Unit>
    suspend fun deletePocket(pocket: SavingsPocket): Result<Unit>
}
