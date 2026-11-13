package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.PocketInvite
import com.example.data.model.PocketMovement
import com.example.data.model.SavingsPocket
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.Date

class SavingsRepository(private val db: FirebaseFirestore) : SavingsDataSource {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            FirebaseApp.getInstance(),
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth
    private val random = SecureRandom()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing savings pockets.")
    }

    private fun currentDisplayName(): String {
        val user = auth.currentUser
        return user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "Usuario"
    }

    override fun observePockets(): Flow<List<SavingsPocket>> = flow {
        val uid = requireUserId()
        val path = "savingsPockets"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(SavingsPocket::class.java).sortedWith(
                        compareByDescending<SavingsPocket> {
                            it.createdAt?.toDate()?.time ?: Long.MAX_VALUE
                        }.thenBy { it.id }
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

    override fun observeMovements(pocketId: String): Flow<List<PocketMovement>> = flow {
        val uid = requireUserId()
        val path = "savingsPockets/$pocketId/movements"
        emitAll(
            db.collection("savingsPockets").document(pocketId)
                .collection("movements")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(PocketMovement::class.java).sortedWith(
                        compareByDescending<PocketMovement> {
                            it.createdAt?.toDate()?.time ?: Long.MAX_VALUE
                        }.thenBy { it.id }
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

    override suspend fun createPocket(
        name: String,
        initialAmount: Long,
        targetAmount: Long?,
        iconKey: String,
        colorKey: String
    ): Result<String> {
        val uid = requireUserId()
        val displayName = currentDisplayName()
        val cleanName = name.trim()
        val safeInitial = initialAmount.coerceAtLeast(0L)
        val safeTarget = targetAmount?.takeIf { it > 0L }
        val pocketDoc = db.collection("savingsPockets").document()

        val pocketMap = hashMapOf<String, Any?>(
            "ownerId" to uid,
            "memberUids" to listOf(uid),
            "memberNames" to mapOf(uid to displayName),
            "name" to cleanName,
            "currentAmount" to safeInitial,
            "targetAmount" to safeTarget,
            "iconKey" to iconKey,
            "colorKey" to colorKey,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            pocketDoc.set(pocketMap).await()
            if (safeInitial > 0L) {
                val movementDoc = pocketDoc.collection("movements").document()
                val movementMap = hashMapOf<String, Any?>(
                    "pocketId" to pocketDoc.id,
                    "userId" to uid,
                    "userName" to displayName,
                    "type" to PocketMovement.TYPE_INITIAL,
                    "amount" to safeInitial,
                    "balanceAfter" to safeInitial,
                    "note" to "Saldo inicial",
                    "createdAt" to FieldValue.serverTimestamp()
                )
                movementDoc.set(movementMap).await()
            }
            Result.success(pocketDoc.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, pocketDoc.path)
            Result.failure(e)
        }
    }

    override suspend fun updatePocketDetails(
        pocketId: String,
        name: String,
        targetAmount: Long?,
        iconKey: String,
        colorKey: String
    ): Result<Unit> {
        requireUserId()
        val pocketDoc = db.collection("savingsPockets").document(pocketId)
        val updates = hashMapOf<String, Any?>(
            "name" to name.trim(),
            "targetAmount" to targetAmount?.takeIf { it > 0L },
            "iconKey" to iconKey,
            "colorKey" to colorKey,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            pocketDoc.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, pocketDoc.path)
            Result.failure(e)
        }
    }

    override suspend fun recordMovement(
        pocketId: String,
        type: String,
        amount: Long,
        note: String?
    ): Result<Long> {
        val uid = requireUserId()
        val displayName = currentDisplayName()
        if (amount <= 0L) {
            return Result.failure(IllegalArgumentException("El monto debe ser mayor a cero."))
        }
        val pocketDoc = db.collection("savingsPockets").document(pocketId)
        val movementDoc = pocketDoc.collection("movements").document()
        val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }?.take(200)

        return try {
            val newBalance = db.runTransaction { tx ->
                val snapshot = tx.get(pocketDoc)
                if (!snapshot.exists()) {
                    throw IllegalStateException("El bolsillo ya no existe.")
                }
                val currentBalance = snapshot.getLong("currentAmount") ?: 0L
                val updatedBalance = when (type) {
                    PocketMovement.TYPE_DEPOSIT -> currentBalance + amount
                    PocketMovement.TYPE_WITHDRAW -> {
                        if (amount > currentBalance) {
                            throw IllegalStateException("Saldo insuficiente para retirar ese monto.")
                        }
                        currentBalance - amount
                    }
                    else -> throw IllegalArgumentException("Tipo de movimiento inválido.")
                }

                tx.update(
                    pocketDoc,
                    mapOf(
                        "currentAmount" to updatedBalance,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val movementMap = hashMapOf<String, Any?>(
                    "pocketId" to pocketId,
                    "userId" to uid,
                    "userName" to displayName,
                    "type" to type,
                    "amount" to amount,
                    "balanceAfter" to updatedBalance,
                    "note" to cleanNote,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                tx.set(movementDoc, movementMap)
                updatedBalance
            }.await()
            Result.success(newBalance)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is IllegalStateException && e !is IllegalArgumentException) {
                handleFirestoreError(e, OperationType.UPDATE, pocketDoc.path)
            }
            Result.failure(e)
        }
    }

    override suspend fun generateOrRegenerateShareCode(pocket: SavingsPocket): Result<String> {
        val uid = requireUserId()
        if (pocket.ownerId != uid) {
            return Result.failure(IllegalStateException("Solo el creador puede gestionar códigos de invitación."))
        }
        val ownerName = currentDisplayName()
        val newCode = generateInviteCode()
        val expiresAt = Timestamp(Date(System.currentTimeMillis() + INVITE_TTL_MS))
        val pocketDoc = db.collection("savingsPockets").document(pocket.id)
        val newInviteDoc = db.collection("pocketInvites").document(newCode)

        return try {
            // Remove previous invite document if present
            val oldCode = pocket.shareCode
            if (!oldCode.isNullOrBlank()) {
                try {
                    db.collection("pocketInvites").document(oldCode).delete().await()
                } catch (_: Exception) {
                    // Best-effort cleanup of old invite
                }
            }

            val inviteMap = hashMapOf<String, Any?>(
                "pocketId" to pocket.id,
                "pocketName" to pocket.name,
                "ownerId" to uid,
                "ownerName" to ownerName,
                "isActive" to true,
                "expiresAt" to expiresAt,
                "createdAt" to FieldValue.serverTimestamp()
            )
            val batch = db.batch()
            batch.set(newInviteDoc, inviteMap)
            batch.update(
                pocketDoc,
                mapOf(
                    "shareCode" to newCode,
                    "shareCodeExpiresAt" to expiresAt,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(newCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, newInviteDoc.path)
            Result.failure(e)
        }
    }

    override suspend fun deactivateShareCode(pocket: SavingsPocket): Result<Unit> {
        val uid = requireUserId()
        if (pocket.ownerId != uid) {
            return Result.failure(IllegalStateException("Solo el creador puede desactivar el código."))
        }
        val pocketDoc = db.collection("savingsPockets").document(pocket.id)
        return try {
            val batch = db.batch()
            val oldCode = pocket.shareCode
            if (!oldCode.isNullOrBlank()) {
                batch.delete(db.collection("pocketInvites").document(oldCode))
            }
            batch.update(
                pocketDoc,
                mapOf(
                    "shareCode" to null,
                    "shareCodeExpiresAt" to null,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, pocketDoc.path)
            Result.failure(e)
        }
    }

    override suspend fun joinPocketByCode(rawCode: String): Result<String> {
        val uid = requireUserId()
        val displayName = currentDisplayName()
        val normalizedCode = normalizeCode(rawCode)
        if (normalizedCode.length < 4) {
            return Result.failure(IllegalArgumentException("Ingresa un código válido."))
        }
        val inviteDoc = db.collection("pocketInvites").document(normalizedCode)
        return try {
            val inviteSnap = inviteDoc.get().await()
            if (!inviteSnap.exists()) {
                return Result.failure(IllegalArgumentException("No encontramos ningún bolsillo con ese código."))
            }
            val invite = inviteSnap.toObject(PocketInvite::class.java)
                ?: return Result.failure(IllegalArgumentException("Código inválido."))

            val now = Date()
            if (!invite.isActive || invite.expiresAt == null || !invite.expiresAt.toDate().after(now)) {
                return Result.failure(IllegalArgumentException("Este código expiró o fue desactivado por su creador."))
            }
            if (invite.ownerId == uid) {
                return Result.failure(IllegalStateException("Ya eres el creador de este bolsillo."))
            }

            // Check if already a member
            val existingMemberPockets = db.collection("savingsPockets")
                .whereArrayContains("memberUids", uid)
                .get()
                .await()
            if (existingMemberPockets.documents.any { it.id == invite.pocketId }) {
                return Result.failure(IllegalStateException("Ya formas parte de '${invite.pocketName}'."))
            }

            val pocketDoc = db.collection("savingsPockets").document(invite.pocketId)
            pocketDoc.update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "memberNames.$uid" to displayName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(invite.pocketName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is IllegalArgumentException && e !is IllegalStateException) {
                handleFirestoreError(e, OperationType.UPDATE, inviteDoc.path)
            }
            Result.failure(e)
        }
    }

    override suspend fun leavePocket(pocketId: String): Result<Unit> {
        val uid = requireUserId()
        val pocketDoc = db.collection("savingsPockets").document(pocketId)
        return try {
            pocketDoc.update(
                mapOf(
                    "memberUids" to FieldValue.arrayRemove(uid),
                    "memberNames.$uid" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, pocketDoc.path)
            Result.failure(e)
        }
    }

    override suspend fun removeMember(pocket: SavingsPocket, memberUid: String): Result<Unit> {
        val uid = requireUserId()
        if (pocket.ownerId != uid || memberUid == uid) {
            return Result.failure(IllegalStateException("No puedes realizar esta acción."))
        }
        val pocketDoc = db.collection("savingsPockets").document(pocket.id)
        return try {
            pocketDoc.update(
                mapOf(
                    "memberUids" to FieldValue.arrayRemove(memberUid),
                    "memberNames.$memberUid" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, pocketDoc.path)
            Result.failure(e)
        }
    }

    override suspend fun deletePocket(pocket: SavingsPocket): Result<Unit> {
        val uid = requireUserId()
        if (pocket.ownerId != uid) {
            return Result.failure(IllegalStateException("Solo el creador puede eliminar este bolsillo."))
        }
        val pocketDoc = db.collection("savingsPockets").document(pocket.id)
        return try {
            val movementRefs = pocketDoc.collection("movements").get().await().documents.map { it.reference }
            movementRefs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
            val code = pocket.shareCode
            if (!code.isNullOrBlank()) {
                try {
                    db.collection("pocketInvites").document(code).delete().await()
                } catch (_: Exception) {
                }
            }
            pocketDoc.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, pocketDoc.path)
            Result.failure(e)
        }
    }

    private fun generateInviteCode(): String {
        val suffix = buildString {
            repeat(5) {
                append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)])
            }
        }
        return "AHO$suffix"
    }

    companion object {
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val INVITE_TTL_MS = 24L * 60 * 60 * 1000 // 24 hours

        fun normalizeCode(raw: String): String {
            return raw.trim().uppercase().replace("-", "").replace(" ", "")
        }

        fun formatDisplayCode(normalized: String): String {
            return if (normalized.startsWith("AHO") && normalized.length > 3) {
                "AHO-${normalized.removePrefix("AHO")}"
            } else {
                normalized
            }
        }
    }
}
