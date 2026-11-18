package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BudgetItem
import com.example.data.model.CategoryUsageItem
import com.example.data.model.ChatMessage
import com.example.data.model.GENERIC_STARTER_ITEMS
import com.example.data.model.MonthlyBudget
import com.example.data.model.TemplateItem
import com.example.data.model.UserProfile
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class BudgetRepository(private val db: FirebaseFirestore) : BudgetDataSource {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            FirebaseApp.getInstance(),
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // Observe User Profile
    override fun observeUserProfile(): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) snapshot.toObject(UserProfile::class.java) else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    // Ensure Profile and Default Templates exist
    override suspend fun ensureProfileAndDefaults(): Result<Unit> {
        val uid = requireUserId()
        val userDoc = db.collection("users").document(uid)
        return try {
            val snapshot = userDoc.get().await()
            if (!snapshot.exists()) {
                val initialSalary = 0L
                val displayName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Usuario"
                val initialItems = GENERIC_STARTER_ITEMS

                val profileMap = mapOf(
                    "userId" to uid,
                    "monthlySalary" to initialSalary,
                    "currency" to "COP",
                    "displayName" to displayName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDoc.set(profileMap).await()

                // Populate default template items
                val templateCol = userDoc.collection("templateItems")
                for (def in initialItems) {
                    val itemDoc = templateCol.document()
                    val itemMap = mapOf(
                        "userId" to uid,
                        "name" to def.name,
                        "amount" to def.amount,
                        "category" to def.category,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    itemDoc.set(itemMap).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userDoc.path)
            Result.failure(e)
        }
    }

    // Update Salary in Profile
    override suspend fun updateSalary(newSalary: Long): Result<Unit> {
        val uid = requireUserId()
        val userDoc = db.collection("users").document(uid)
        return try {
            userDoc.update(
                mapOf(
                    "monthlySalary" to newSalary,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userDoc.path)
            Result.failure(e)
        }
    }

    // Update Custom Categories in Profile
    override suspend fun updateCustomCategories(categories: List<String>): Result<Unit> {
        val uid = requireUserId()
        val userDoc = db.collection("users").document(uid)
        return try {
            userDoc.update(
                mapOf(
                    "customCategories" to categories,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userDoc.path)
            Result.failure(e)
        }
    }

    // Update Savings Tab Visibility in Profile
    override suspend fun updateShowSavingsTab(enabled: Boolean): Result<Unit> {
        val uid = requireUserId()
        val userDoc = db.collection("users").document(uid)
        return try {
            val snapshot = userDoc.get().await()
            val currentHidden = (snapshot.get("hiddenTabs") as? List<*>)
                ?.filterIsInstance<String>()
                ?.toMutableList()
                ?: mutableListOf()
            if (enabled) {
                currentHidden.removeAll { it == "SAVINGS" }
            } else if ("SAVINGS" !in currentHidden) {
                currentHidden.add("SAVINGS")
            }
            userDoc.update(
                mapOf(
                    "showSavingsTab" to enabled,
                    "hiddenTabs" to currentHidden.distinct(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userDoc.path)
            Result.failure(e)
        }
    }

    // Update configurable hidden tabs list in Profile
    override suspend fun updateHiddenTabs(hiddenTabs: List<String>): Result<Unit> {
        val uid = requireUserId()
        val userDoc = db.collection("users").document(uid)
        val cleanHidden = hiddenTabs
            .filterNot { it.equals("SETTINGS", ignoreCase = true) }
            .distinct()
        return try {
            userDoc.update(
                mapOf(
                    "hiddenTabs" to cleanHidden,
                    "showSavingsTab" to ("SAVINGS" !in cleanHidden),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userDoc.path)
            Result.failure(e)
        }
    }

    // Find all distinct items currently using a given category across months & templates
    override suspend fun findItemsUsingCategory(
        categoryName: String,
        currentMonthItems: List<BudgetItem>
    ): Result<List<CategoryUsageItem>> {
        val uid = requireUserId()
        val userDoc = db.collection("users").document(uid)
        return try {
            val grouped = linkedMapOf<String, MutableList<Pair<String, Long>>>()

            // Include in-memory current month items first
            for (item in currentMonthItems) {
                if (item.category.equals(categoryName, ignoreCase = true)) {
                    val key = item.name.trim().lowercase()
                    grouped.getOrPut(key) { mutableListOf() }.add(item.name to item.amount)
                }
            }

            coroutineScope {
                val monthsSnapDeferred = async { userDoc.collection("months").get().await() }
                val templatesSnapDeferred = async { userDoc.collection("templateItems").get().await() }

                val monthsSnap = monthsSnapDeferred.await()
                val monthItemSnaps = monthsSnap.documents.map { monthDoc ->
                    async { monthDoc.reference.collection("items").get().await() }
                }.awaitAll()

                val seenKeysFromCurrent = grouped.keys.toSet()
                val remoteGrouped = linkedMapOf<String, MutableList<Pair<String, Long>>>()

                for (snap in monthItemSnaps) {
                    for (doc in snap.documents) {
                        val cat = doc.getString("category") ?: ""
                        if (cat.equals(categoryName, ignoreCase = true)) {
                            val name = doc.getString("name") ?: continue
                            val amount = doc.getLong("amount") ?: 0L
                            val key = name.trim().lowercase()
                            remoteGrouped.getOrPut(key) { mutableListOf() }.add(name to amount)
                        }
                    }
                }

                val templatesSnap = templatesSnapDeferred.await()
                for (doc in templatesSnap.documents) {
                    val cat = doc.getString("category") ?: ""
                    if (cat.equals(categoryName, ignoreCase = true)) {
                        val name = doc.getString("name") ?: continue
                        val amount = doc.getLong("amount") ?: 0L
                        val key = name.trim().lowercase()
                        remoteGrouped.getOrPut(key) { mutableListOf() }.add(name to amount)
                    }
                }

                for ((key, list) in remoteGrouped) {
                    if (!seenKeysFromCurrent.contains(key)) {
                        grouped[key] = list
                    } else if (list.size > (grouped[key]?.size ?: 0)) {
                        grouped[key] = list
                    }
                }
            }

            val usageItems = grouped.values.map { entries ->
                val latest = entries.first()
                CategoryUsageItem(
                    name = latest.first,
                    amount = latest.second,
                    occurrences = entries.size
                )
            }.sortedByDescending { it.amount }

            Result.success(usageItems)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, userDoc.path)
            Result.failure(e)
        }
    }

    // Reassign all items using oldCategory across all months and templateItems
    override suspend fun reassignCategoryAcrossAllMonths(
        oldCategory: String,
        reassignmentsByNormalizedName: Map<String, String>,
        fallbackCategory: String?
    ): Result<Unit> {
        val uid = requireUserId()
        val userDoc = db.collection("users").document(uid)
        return try {
            val updates = mutableListOf<Pair<DocumentReference, String>>()

            coroutineScope {
                val monthsSnapDeferred = async { userDoc.collection("months").get().await() }
                val templatesSnapDeferred = async { userDoc.collection("templateItems").get().await() }

                val monthsSnap = monthsSnapDeferred.await()
                val monthItemSnaps = monthsSnap.documents.map { monthDoc ->
                    async { monthDoc.reference.collection("items").get().await() }
                }.awaitAll()

                for (snap in monthItemSnaps) {
                    for (doc in snap.documents) {
                        val cat = doc.getString("category") ?: ""
                        if (cat.equals(oldCategory, ignoreCase = true)) {
                            val nameKey = (doc.getString("name") ?: "").trim().lowercase()
                            val targetCat = reassignmentsByNormalizedName[nameKey] ?: fallbackCategory
                            if (!targetCat.isNullOrBlank()) {
                                updates.add(doc.reference to targetCat)
                            }
                        }
                    }
                }

                val templatesSnap = templatesSnapDeferred.await()
                for (doc in templatesSnap.documents) {
                    val cat = doc.getString("category") ?: ""
                    if (cat.equals(oldCategory, ignoreCase = true)) {
                        val nameKey = (doc.getString("name") ?: "").trim().lowercase()
                        val targetCat = reassignmentsByNormalizedName[nameKey] ?: fallbackCategory
                        if (!targetCat.isNullOrBlank()) {
                            updates.add(doc.reference to targetCat)
                        }
                    }
                }
            }

            // Commit in chunks of 400 to stay safely below Firestore's 500 writes/batch limit
            for (chunk in updates.chunked(400)) {
                val batch = db.batch()
                for ((ref, targetCat) in chunk) {
                    batch.update(
                        ref,
                        mapOf(
                            "category" to targetCat,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userDoc.path)
            Result.failure(e)
        }
    }

    // Observe Monthly Budget
    override fun observeMonthlyBudget(monthKey: String): Flow<MonthlyBudget?> = flow {
        val uid = requireUserId()
        val path = "users/$uid/months/$monthKey"
        emitAll(
            db.collection("users").document(uid).collection("months").document(monthKey)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) snapshot.toObject(MonthlyBudget::class.java) else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    // Observe Items for a Given Month
    override fun observeMonthItems(monthKey: String): Flow<List<BudgetItem>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/months/$monthKey/items"
        emitAll(
            db.collection("users").document(uid).collection("months").document(monthKey).collection("items")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(BudgetItem::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    // Ensure Month is initialized with items
    override suspend fun ensureMonthInitialized(monthKey: String, currentSalary: Long): Result<Unit> {
        val uid = requireUserId()
        val monthDoc = db.collection("users").document(uid).collection("months").document(monthKey)
        return try {
            val snapshot = monthDoc.get().await()
            if (!snapshot.exists()) {
                val budgetMap = mapOf(
                    "userId" to uid,
                    "monthKey" to monthKey,
                    "salary" to currentSalary,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                monthDoc.set(budgetMap).await()

                // Check if template items exist
                val templateCol = db.collection("users").document(uid).collection("templateItems")
                val templateSnap = templateCol.get().await()
                val templates = templateSnap.toObjects(TemplateItem::class.java)

                val itemsCol = monthDoc.collection("items")
                if (templates.isNotEmpty()) {
                    for (tmpl in templates) {
                        // Only recurring items carry over to new months
                        if (tmpl.isRecurring) {
                            val itemDoc = itemsCol.document()
                            val itemMap = mapOf(
                                "userId" to uid,
                                "monthKey" to monthKey,
                                "name" to tmpl.name,
                                "amount" to tmpl.amount,
                                "category" to tmpl.category,
                                "isPaid" to false,
                                "isRecurring" to true,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                            itemDoc.set(itemMap).await()
                        }
                    }
                } else {
                    // Populate with generic starter items
                    for (def in GENERIC_STARTER_ITEMS) {
                        val itemDoc = itemsCol.document()
                        val itemMap = mapOf(
                            "userId" to uid,
                            "monthKey" to monthKey,
                            "name" to def.name,
                            "amount" to def.amount,
                            "category" to def.category,
                            "isPaid" to false,
                            "isRecurring" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        itemDoc.set(itemMap).await()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, monthDoc.path)
            Result.failure(e)
        }
    }

    // Toggle Item Paid status
    override suspend fun toggleItemPaid(item: BudgetItem): Result<Unit> {
        val uid = requireUserId()
        require(item.id.isNotBlank()) { "Item ID cannot be blank" }
        val itemDoc = db.collection("users").document(uid)
            .collection("months").document(item.monthKey)
            .collection("items").document(item.id)

        val newPaid = !item.isPaid
        val updates = mutableMapOf<String, Any?>(
            "isPaid" to newPaid,
            "paidAt" to if (newPaid) FieldValue.serverTimestamp() else null,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            itemDoc.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, itemDoc.path)
            Result.failure(e)
        }
    }

    // Add new item to a month
    override suspend fun addItem(item: BudgetItem): Result<String> {
        val uid = requireUserId()
        val itemsCol = db.collection("users").document(uid)
            .collection("months").document(item.monthKey)
            .collection("items")
        val doc = itemsCol.document()
        val map = mutableMapOf<String, Any?>(
            "userId" to uid,
            "monthKey" to item.monthKey,
            "name" to item.name,
            "amount" to item.amount,
            "category" to item.category,
            "isPaid" to item.isPaid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (item.isPaid) map["paidAt"] = FieldValue.serverTimestamp()
        if (!item.isRecurring) map["isRecurring"] = false
        if (item.notes != null) map["notes"] = item.notes
        return try {
            doc.set(map).await()
            Result.success(doc.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, doc.path)
            Result.failure(e)
        }
    }

    // Update item
    override suspend fun updateItem(item: BudgetItem): Result<Unit> {
        val uid = requireUserId()
        require(item.id.isNotBlank()) { "Item ID cannot be blank" }
        val itemDoc = db.collection("users").document(uid)
            .collection("months").document(item.monthKey)
            .collection("items").document(item.id)

        val updates = mutableMapOf<String, Any?>(
            "name" to item.name,
            "amount" to item.amount,
            "category" to item.category,
            "isPaid" to item.isPaid,
            "isRecurring" to item.isRecurring,
            "notes" to item.notes,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            itemDoc.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, itemDoc.path)
            Result.failure(e)
        }
    }

    // Delete item
    override suspend fun deleteItem(item: BudgetItem): Result<Unit> {
        val uid = requireUserId()
        require(item.id.isNotBlank()) { "Item ID cannot be blank" }
        val itemDoc = db.collection("users").document(uid)
            .collection("months").document(item.monthKey)
            .collection("items").document(item.id)
        return try {
            itemDoc.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, itemDoc.path)
            Result.failure(e)
        }
    }

    // Mark all items as paid/unpaid
    override suspend fun markAllItemsPaid(monthKey: String, isPaid: Boolean, items: List<BudgetItem>): Result<Unit> {
        val uid = requireUserId()
        val itemsCol = db.collection("users").document(uid)
            .collection("months").document(monthKey)
            .collection("items")

        return try {
            val batch = db.batch()
            for (item in items) {
                if (item.isPaid != isPaid) {
                    val ref = itemsCol.document(item.id)
                    batch.update(
                        ref,
                        mapOf(
                            "isPaid" to isPaid,
                            "paidAt" to if (isPaid) FieldValue.serverTimestamp() else null,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, itemsCol.path)
            Result.failure(e)
        }
    }

    // Mark specific item IDs as paid/unpaid
    override suspend fun markItemsPaid(monthKey: String, isPaid: Boolean, itemIds: List<String>): Result<Unit> {
        if (itemIds.isEmpty()) return Result.success(Unit)
        val uid = requireUserId()
        val itemsCol = db.collection("users").document(uid)
            .collection("months").document(monthKey)
            .collection("items")

        return try {
            val batch = db.batch()
            for (id in itemIds) {
                val ref = itemsCol.document(id)
                batch.update(
                    ref,
                    mapOf(
                        "isPaid" to isPaid,
                        "paidAt" to if (isPaid) FieldValue.serverTimestamp() else null,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, itemsCol.path)
            Result.failure(e)
        }
    }

    // Chat Messages
    override fun observeChatMessages(): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/chatMessages"
        emitAll(
            db.collection("users").document(uid).collection("chatMessages")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ChatMessage::class.java).sortedWith(
                        compareBy<ChatMessage> { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                            .thenBy { it.id }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override suspend fun addChatMessage(
        role: String,
        text: String,
        messageId: String?
    ): Result<Unit> {
        val uid = requireUserId()
        val chatCol = db.collection("users").document(uid).collection("chatMessages")
        val doc = if (!messageId.isNullOrBlank()) chatCol.document(messageId) else chatCol.document()
        val map = mapOf(
            "userId" to uid,
            "role" to role,
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            doc.set(map).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, doc.path)
            Result.failure(e)
        }
    }

    override suspend fun clearChatMessages(messages: List<ChatMessage>): Result<Unit> {
        val uid = requireUserId()
        val chatCol = db.collection("users").document(uid).collection("chatMessages")
        return try {
            val remoteIds = chatCol.get().await().documents.map { it.id }
            val allIds = (remoteIds + messages.map { it.id }).filter { it.isNotBlank() }.distinct()
            allIds.chunked(400).forEach { chunk ->
                val batch = db.batch()
                for (id in chunk) {
                    batch.delete(chatCol.document(id))
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, chatCol.path)
            Result.failure(e)
        }
    }
}
