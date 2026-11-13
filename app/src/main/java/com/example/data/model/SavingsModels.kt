package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class SavingsPocket(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val name: String = "",
    val currentAmount: Long = 0L,
    val targetAmount: Long? = null,
    val iconKey: String = "savings",
    val colorKey: String = "emerald",
    val shareCode: String? = null,
    val shareCodeExpiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PocketMovement(
    @DocumentId val id: String = "",
    val pocketId: String = "",
    val userId: String = "",
    val userName: String = "",
    val type: String = TYPE_DEPOSIT,
    val amount: Long = 0L,
    val balanceAfter: Long = 0L,
    val note: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    companion object {
        const val TYPE_DEPOSIT = "deposit"
        const val TYPE_WITHDRAW = "withdraw"
        const val TYPE_INITIAL = "initial"
    }
}

@IgnoreExtraProperties
data class PocketInvite(
    @DocumentId val id: String = "",
    val pocketId: String = "",
    val pocketName: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    @get:PropertyName("isActive")
    @field:PropertyName("isActive")
    val isActive: Boolean = true,
    val expiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)
