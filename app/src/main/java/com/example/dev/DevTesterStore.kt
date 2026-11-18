package com.example.dev

import com.example.data.model.BudgetItem
import com.example.data.model.CategoryUsageItem
import com.example.data.model.ChatMessage
import com.example.data.model.DEMO_DEFAULT_ITEMS
import com.example.data.model.GENERIC_STARTER_ITEMS
import com.example.data.model.MonthlyBudget
import com.example.data.model.PocketInvite
import com.example.data.model.PocketMovement
import com.example.data.model.SavingsPocket
import com.example.data.model.UserProfile
import com.example.data.repository.BudgetDataSource
import com.example.data.repository.SavingsDataSource
import com.example.data.repository.SavingsRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.security.SecureRandom
import java.util.Date
import java.util.UUID

data class DevTesterAccount(
    val uid: String,
    val displayName: String,
    val email: String,
    val defaultSalary: Long,
    val useRichSeedData: Boolean
)

object DevTesterAccounts {
    val PRIMARY = DevTesterAccount(
        uid = "dev_tester_1",
        displayName = "Usuario Demo",
        email = "tester1@dev.local",
        defaultSalary = 6000000L,
        useRichSeedData = true
    )

    val PARTNER = DevTesterAccount(
        uid = "dev_tester_2",
        displayName = "Pareja Demo",
        email = "tester2@dev.local",
        defaultSalary = 4500000L,
        useRichSeedData = false
    )

    fun companionOf(current: DevTesterAccount): DevTesterAccount {
        return if (current.uid == PRIMARY.uid) PARTNER else PRIMARY
    }
}

/**
 * In-memory sandbox store used EXCLUSIVELY in BuildConfig.DEBUG when the developer chooses
 * quick tester sign-in. Never touches Firestore when unauthenticated and is shared across
 * Tester 1 / Tester 2 so invite codes & shared pockets can be tested end-to-end.
 */
private object DevSandboxStore {
    val profiles = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    // Keyed by "${uid}|${monthKey}"
    val months = MutableStateFlow<Map<String, MonthlyBudget>>(emptyMap())
    // Keyed by "${uid}|${monthKey}" -> List<BudgetItem>
    val monthItems = MutableStateFlow<Map<String, List<BudgetItem>>>(emptyMap())
    val chatMessages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())

    // Shared across all dev tester accounts for testing partner pocket sharing
    val pockets = MutableStateFlow<List<SavingsPocket>>(emptyList())
    val movements = MutableStateFlow<Map<String, List<PocketMovement>>>(emptyMap())
    val invites = MutableStateFlow<Map<String, PocketInvite>>(emptyMap())
    private var seededInitialPocket = false
    private val random = SecureRandom()

    @Synchronized
    fun ensureSeeded() {
        if (seededInitialPocket) return
        seededInitialPocket = true
        val now = Timestamp.now()
        val pocketId = "dev_pocket_seed_1"
        pockets.value = listOf(
            SavingsPocket(
                id = pocketId,
                ownerId = DevTesterAccounts.PRIMARY.uid,
                memberUids = listOf(DevTesterAccounts.PRIMARY.uid),
                memberNames = mapOf(DevTesterAccounts.PRIMARY.uid to DevTesterAccounts.PRIMARY.displayName),
                name = "Viaje de Prueba",
                currentAmount = 850000L,
                targetAmount = 3500000L,
                iconKey = "flight",
                colorKey = "blue",
                createdAt = now,
                updatedAt = now
            )
        )
        movements.value = mapOf(
            pocketId to listOf(
                PocketMovement(
                    id = "dev_mov_seed_1",
                    pocketId = pocketId,
                    userId = DevTesterAccounts.PRIMARY.uid,
                    userName = DevTesterAccounts.PRIMARY.displayName,
                    type = PocketMovement.TYPE_INITIAL,
                    amount = 850000L,
                    balanceAfter = 850000L,
                    note = "Saldo inicial de prueba",
                    createdAt = now
                )
            )
        )
    }

    fun generateInviteCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val suffix = buildString {
            repeat(5) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
        return "AHO$suffix"
    }
}

class DevBudgetRepository(
    private val account: DevTesterAccount
) : BudgetDataSource {

    override fun observeUserProfile(): Flow<UserProfile?> =
        DevSandboxStore.profiles.map { it[account.uid] }

    override suspend fun ensureProfileAndDefaults(): Result<Unit> {
        DevSandboxStore.ensureSeeded()
        DevSandboxStore.profiles.update { current ->
            if (current.containsKey(account.uid)) current
            else current + (
                account.uid to UserProfile(
                    id = account.uid,
                    userId = account.uid,
                    monthlySalary = account.defaultSalary,
                    currency = "COP",
                    displayName = account.displayName,
                    customCategories = emptyList(),
                    showSavingsTab = true,
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
            )
        }
        return Result.success(Unit)
    }

    override suspend fun updateSalary(newSalary: Long): Result<Unit> {
        DevSandboxStore.profiles.update { map ->
            val profile = map[account.uid] ?: return@update map
            map + (account.uid to profile.copy(monthlySalary = newSalary, updatedAt = Timestamp.now()))
        }
        return Result.success(Unit)
    }

    override suspend fun updateCustomCategories(categories: List<String>): Result<Unit> {
        DevSandboxStore.profiles.update { map ->
            val profile = map[account.uid] ?: return@update map
            map + (account.uid to profile.copy(customCategories = categories, updatedAt = Timestamp.now()))
        }
        return Result.success(Unit)
    }

    override suspend fun updateShowSavingsTab(enabled: Boolean): Result<Unit> {
        DevSandboxStore.profiles.update { map ->
            val profile = map[account.uid] ?: return@update map
            val updatedHidden = if (enabled) {
                profile.hiddenTabs - "SAVINGS"
            } else {
                (profile.hiddenTabs + "SAVINGS").distinct()
            }
            map + (
                account.uid to profile.copy(
                    showSavingsTab = enabled,
                    hiddenTabs = updatedHidden,
                    updatedAt = Timestamp.now()
                )
            )
        }
        return Result.success(Unit)
    }

    override suspend fun updateHiddenTabs(hiddenTabs: List<String>): Result<Unit> {
        val clean = hiddenTabs
            .filterNot { it.equals("SETTINGS", ignoreCase = true) }
            .distinct()
        DevSandboxStore.profiles.update { map ->
            val profile = map[account.uid] ?: return@update map
            map + (
                account.uid to profile.copy(
                    hiddenTabs = clean,
                    showSavingsTab = "SAVINGS" !in clean,
                    updatedAt = Timestamp.now()
                )
            )
        }
        return Result.success(Unit)
    }

    override suspend fun findItemsUsingCategory(
        categoryName: String,
        currentMonthItems: List<BudgetItem>
    ): Result<List<CategoryUsageItem>> {
        val grouped = linkedMapOf<String, MutableList<Pair<String, Long>>>()
        val prefix = "${account.uid}|"
        DevSandboxStore.monthItems.value
            .filterKeys { it.startsWith(prefix) }
            .values
            .flatten()
            .plus(currentMonthItems)
            .filter { it.category.equals(categoryName, ignoreCase = true) }
            .forEach { item ->
                val key = item.name.trim().lowercase()
                grouped.getOrPut(key) { mutableListOf() }.add(item.name to item.amount)
            }
        val usage = grouped.values.map { list ->
            val first = list.first()
            CategoryUsageItem(name = first.first, amount = first.second, occurrences = list.size)
        }.sortedByDescending { it.amount }
        return Result.success(usage)
    }

    override suspend fun reassignCategoryAcrossAllMonths(
        oldCategory: String,
        reassignmentsByNormalizedName: Map<String, String>,
        fallbackCategory: String?
    ): Result<Unit> {
        val prefix = "${account.uid}|"
        DevSandboxStore.monthItems.update { map ->
            map.mapValues { (key, list) ->
                if (!key.startsWith(prefix)) list
                else list.map { item ->
                    if (!item.category.equals(oldCategory, ignoreCase = true)) item
                    else {
                        val target = reassignmentsByNormalizedName[item.name.trim().lowercase()] ?: fallbackCategory
                        if (!target.isNullOrBlank()) item.copy(category = target, updatedAt = Timestamp.now())
                        else item
                    }
                }
            }
        }
        return Result.success(Unit)
    }

    override fun observeMonthlyBudget(monthKey: String): Flow<MonthlyBudget?> =
        DevSandboxStore.months.map { it["${account.uid}|$monthKey"] }

    override fun observeMonthItems(monthKey: String): Flow<List<BudgetItem>> =
        DevSandboxStore.monthItems.map { it["${account.uid}|$monthKey"] ?: emptyList() }

    override suspend fun ensureMonthInitialized(monthKey: String, currentSalary: Long): Result<Unit> {
        val key = "${account.uid}|$monthKey"
        DevSandboxStore.months.update { map ->
            if (map.containsKey(key)) map
            else map + (
                key to MonthlyBudget(
                    id = monthKey,
                    userId = account.uid,
                    monthKey = monthKey,
                    salary = if (currentSalary > 0L) currentSalary else account.defaultSalary,
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
            )
        }
        DevSandboxStore.monthItems.update { map ->
            if (map.containsKey(key)) map
            else {
                val templates = if (account.useRichSeedData) DEMO_DEFAULT_ITEMS else GENERIC_STARTER_ITEMS
                val seeded = templates.map { def ->
                    BudgetItem(
                        id = UUID.randomUUID().toString(),
                        userId = account.uid,
                        monthKey = monthKey,
                        name = def.name,
                        amount = def.amount,
                        category = def.category,
                        isPaid = false,
                        isRecurring = true,
                        createdAt = Timestamp.now(),
                        updatedAt = Timestamp.now()
                    )
                }
                map + (key to seeded)
            }
        }
        return Result.success(Unit)
    }

    override suspend fun toggleItemPaid(item: BudgetItem): Result<Unit> {
        val key = "${account.uid}|${item.monthKey}"
        val nextPaid = !item.isPaid
        DevSandboxStore.monthItems.update { map ->
            val current = map[key] ?: return@update map
            map + (key to current.map {
                if (it.id == item.id) it.copy(
                    isPaid = nextPaid,
                    paidAt = if (nextPaid) Timestamp.now() else null,
                    updatedAt = Timestamp.now()
                ) else it
            })
        }
        return Result.success(Unit)
    }

    override suspend fun addItem(item: BudgetItem): Result<String> {
        val id = UUID.randomUUID().toString()
        val key = "${account.uid}|${item.monthKey}"
        val created = item.copy(
            id = id,
            userId = account.uid,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        DevSandboxStore.monthItems.update { map ->
            val current = map[key] ?: emptyList()
            map + (key to (current + created))
        }
        return Result.success(id)
    }

    override suspend fun updateItem(item: BudgetItem): Result<Unit> {
        val key = "${account.uid}|${item.monthKey}"
        DevSandboxStore.monthItems.update { map ->
            val current = map[key] ?: return@update map
            map + (key to current.map {
                if (it.id == item.id) item.copy(updatedAt = Timestamp.now()) else it
            })
        }
        return Result.success(Unit)
    }

    override suspend fun deleteItem(item: BudgetItem): Result<Unit> {
        val key = "${account.uid}|${item.monthKey}"
        DevSandboxStore.monthItems.update { map ->
            val current = map[key] ?: return@update map
            map + (key to current.filterNot { it.id == item.id })
        }
        return Result.success(Unit)
    }

    override suspend fun markAllItemsPaid(monthKey: String, isPaid: Boolean, items: List<BudgetItem>): Result<Unit> {
        val key = "${account.uid}|$monthKey"
        DevSandboxStore.monthItems.update { map ->
            val current = map[key] ?: return@update map
            map + (key to current.map {
                it.copy(
                    isPaid = isPaid,
                    paidAt = if (isPaid) Timestamp.now() else null,
                    updatedAt = Timestamp.now()
                )
            })
        }
        return Result.success(Unit)
    }

    override suspend fun markItemsPaid(monthKey: String, isPaid: Boolean, itemIds: List<String>): Result<Unit> {
        val idSet = itemIds.toSet()
        val key = "${account.uid}|$monthKey"
        DevSandboxStore.monthItems.update { map ->
            val current = map[key] ?: return@update map
            map + (key to current.map {
                if (it.id in idSet) it.copy(
                    isPaid = isPaid,
                    paidAt = if (isPaid) Timestamp.now() else null,
                    updatedAt = Timestamp.now()
                ) else it
            })
        }
        return Result.success(Unit)
    }

    override fun observeChatMessages(): Flow<List<ChatMessage>> =
        DevSandboxStore.chatMessages.map { it[account.uid] ?: emptyList() }

    override suspend fun addChatMessage(role: String, text: String, messageId: String?): Result<Unit> {
        val id = messageId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val msg = ChatMessage(
            id = id,
            userId = account.uid,
            role = role,
            text = text,
            createdAt = Timestamp.now()
        )
        DevSandboxStore.chatMessages.update { map ->
            val current = map[account.uid] ?: emptyList()
            if (current.any { it.id == id }) map else map + (account.uid to (current + msg))
        }
        return Result.success(Unit)
    }

    override suspend fun clearChatMessages(messages: List<ChatMessage>): Result<Unit> {
        DevSandboxStore.chatMessages.update { it + (account.uid to emptyList()) }
        return Result.success(Unit)
    }
}

class DevSavingsRepository(
    private val account: DevTesterAccount
) : SavingsDataSource {

    init {
        DevSandboxStore.ensureSeeded()
    }

    override fun observePockets(): Flow<List<SavingsPocket>> =
        DevSandboxStore.pockets.map { list ->
            list.filter { account.uid in it.memberUids }
                .sortedWith(
                    compareByDescending<SavingsPocket> {
                        it.createdAt?.toDate()?.time ?: Long.MAX_VALUE
                    }.thenBy { it.id }
                )
        }

    override fun observeMovements(pocketId: String): Flow<List<PocketMovement>> =
        DevSandboxStore.movements.map { map ->
            val isMember = DevSandboxStore.pockets.value.any {
                it.id == pocketId && account.uid in it.memberUids
            }
            if (!isMember) emptyList()
            else (map[pocketId] ?: emptyList()).sortedWith(
                compareByDescending<PocketMovement> {
                    it.createdAt?.toDate()?.time ?: Long.MAX_VALUE
                }.thenBy { it.id }
            )
        }

    override suspend fun createPocket(
        name: String,
        initialAmount: Long,
        targetAmount: Long?,
        iconKey: String,
        colorKey: String
    ): Result<String> {
        val id = UUID.randomUUID().toString()
        val safeInitial = initialAmount.coerceAtLeast(0L)
        val now = Timestamp.now()
        val pocket = SavingsPocket(
            id = id,
            ownerId = account.uid,
            memberUids = listOf(account.uid),
            memberNames = mapOf(account.uid to account.displayName),
            name = name.trim(),
            currentAmount = safeInitial,
            targetAmount = targetAmount?.takeIf { it > 0L },
            iconKey = iconKey,
            colorKey = colorKey,
            createdAt = now,
            updatedAt = now
        )
        DevSandboxStore.pockets.update { it + pocket }
        if (safeInitial > 0L) {
            val mov = PocketMovement(
                id = UUID.randomUUID().toString(),
                pocketId = id,
                userId = account.uid,
                userName = account.displayName,
                type = PocketMovement.TYPE_INITIAL,
                amount = safeInitial,
                balanceAfter = safeInitial,
                note = "Saldo inicial",
                createdAt = now
            )
            DevSandboxStore.movements.update { map ->
                map + (id to listOf(mov))
            }
        }
        return Result.success(id)
    }

    override suspend fun updatePocketDetails(
        pocketId: String,
        name: String,
        targetAmount: Long?,
        iconKey: String,
        colorKey: String
    ): Result<Unit> {
        DevSandboxStore.pockets.update { list ->
            list.map {
                if (it.id == pocketId && account.uid in it.memberUids) {
                    it.copy(
                        name = name.trim(),
                        targetAmount = targetAmount?.takeIf { t -> t > 0L },
                        iconKey = iconKey,
                        colorKey = colorKey,
                        updatedAt = Timestamp.now()
                    )
                } else it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun recordMovement(
        pocketId: String,
        type: String,
        amount: Long,
        note: String?
    ): Result<Long> {
        if (amount <= 0L) {
            return Result.failure(IllegalArgumentException("El monto debe ser mayor a cero."))
        }
        val pocket = DevSandboxStore.pockets.value.firstOrNull {
            it.id == pocketId && account.uid in it.memberUids
        } ?: return Result.failure(IllegalStateException("El bolsillo no existe."))

        val updatedBalance = when (type) {
            PocketMovement.TYPE_DEPOSIT -> pocket.currentAmount + amount
            PocketMovement.TYPE_WITHDRAW -> {
                if (amount > pocket.currentAmount) {
                    return Result.failure(IllegalStateException("Saldo insuficiente para retirar ese monto."))
                }
                pocket.currentAmount - amount
            }
            else -> return Result.failure(IllegalArgumentException("Tipo de movimiento inválido."))
        }

        val now = Timestamp.now()
        DevSandboxStore.pockets.update { list ->
            list.map {
                if (it.id == pocketId) it.copy(currentAmount = updatedBalance, updatedAt = now) else it
            }
        }
        val mov = PocketMovement(
            id = UUID.randomUUID().toString(),
            pocketId = pocketId,
            userId = account.uid,
            userName = account.displayName,
            type = type,
            amount = amount,
            balanceAfter = updatedBalance,
            note = note?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
            createdAt = now
        )
        DevSandboxStore.movements.update { map ->
            val current = map[pocketId] ?: emptyList()
            map + (pocketId to (listOf(mov) + current))
        }
        return Result.success(updatedBalance)
    }

    override suspend fun generateOrRegenerateShareCode(pocket: SavingsPocket): Result<String> {
        if (pocket.ownerId != account.uid) {
            return Result.failure(IllegalStateException("Solo el creador puede gestionar códigos de invitación."))
        }
        val newCode = DevSandboxStore.generateInviteCode()
        val expiresAt = Timestamp(Date(System.currentTimeMillis() + 24L * 60 * 60 * 1000))
        val now = Timestamp.now()
        DevSandboxStore.invites.update { map ->
            val cleaned = if (!pocket.shareCode.isNullOrBlank()) map - pocket.shareCode else map
            cleaned + (
                newCode to PocketInvite(
                    id = newCode,
                    pocketId = pocket.id,
                    pocketName = pocket.name,
                    ownerId = account.uid,
                    ownerName = account.displayName,
                    isActive = true,
                    expiresAt = expiresAt,
                    createdAt = now
                )
            )
        }
        DevSandboxStore.pockets.update { list ->
            list.map {
                if (it.id == pocket.id) it.copy(
                    shareCode = newCode,
                    shareCodeExpiresAt = expiresAt,
                    updatedAt = now
                ) else it
            }
        }
        return Result.success(newCode)
    }

    override suspend fun deactivateShareCode(pocket: SavingsPocket): Result<Unit> {
        if (pocket.ownerId != account.uid) {
            return Result.failure(IllegalStateException("Solo el creador puede desactivar el código."))
        }
        DevSandboxStore.invites.update { map ->
            if (!pocket.shareCode.isNullOrBlank()) map - pocket.shareCode else map
        }
        DevSandboxStore.pockets.update { list ->
            list.map {
                if (it.id == pocket.id) it.copy(
                    shareCode = null,
                    shareCodeExpiresAt = null,
                    updatedAt = Timestamp.now()
                ) else it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun joinPocketByCode(rawCode: String): Result<String> {
        val code = SavingsRepository.normalizeCode(rawCode)
        val invite = DevSandboxStore.invites.value[code]
            ?: return Result.failure(IllegalArgumentException("No encontramos ningún bolsillo con ese código."))

        if (!invite.isActive || invite.expiresAt == null || !invite.expiresAt.toDate().after(Date())) {
            return Result.failure(IllegalArgumentException("Este código expiró o fue desactivado por su creador."))
        }
        if (invite.ownerId == account.uid) {
            return Result.failure(IllegalStateException("Ya eres el creador de este bolsillo."))
        }

        val targetPocket = DevSandboxStore.pockets.value.firstOrNull { it.id == invite.pocketId }
            ?: return Result.failure(IllegalStateException("El bolsillo ya no existe."))
        if (account.uid in targetPocket.memberUids) {
            return Result.failure(IllegalStateException("Ya formas parte de '${invite.pocketName}'."))
        }

        DevSandboxStore.pockets.update { list ->
            list.map {
                if (it.id == invite.pocketId) {
                    it.copy(
                        memberUids = it.memberUids + account.uid,
                        memberNames = it.memberNames + (account.uid to account.displayName),
                        updatedAt = Timestamp.now()
                    )
                } else it
            }
        }
        return Result.success(invite.pocketName)
    }

    override suspend fun leavePocket(pocketId: String): Result<Unit> {
        DevSandboxStore.pockets.update { list ->
            list.map {
                if (it.id == pocketId && it.ownerId != account.uid) {
                    it.copy(
                        memberUids = it.memberUids - account.uid,
                        memberNames = it.memberNames - account.uid,
                        updatedAt = Timestamp.now()
                    )
                } else it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun removeMember(pocket: SavingsPocket, memberUid: String): Result<Unit> {
        if (pocket.ownerId != account.uid || memberUid == account.uid) {
            return Result.failure(IllegalStateException("No puedes realizar esta acción."))
        }
        DevSandboxStore.pockets.update { list ->
            list.map {
                if (it.id == pocket.id) {
                    it.copy(
                        memberUids = it.memberUids - memberUid,
                        memberNames = it.memberNames - memberUid,
                        updatedAt = Timestamp.now()
                    )
                } else it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun deletePocket(pocket: SavingsPocket): Result<Unit> {
        if (pocket.ownerId != account.uid) {
            return Result.failure(IllegalStateException("Solo el creador puede eliminar este bolsillo."))
        }
        DevSandboxStore.pockets.update { list -> list.filterNot { it.id == pocket.id } }
        DevSandboxStore.movements.update { it - pocket.id }
        if (!pocket.shareCode.isNullOrBlank()) {
            DevSandboxStore.invites.update { it - pocket.shareCode }
        }
        return Result.success(Unit)
    }
}
