package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.PocketMovement
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class SavingsRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createPocket_withInitialAmountAndMovement_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = SavingsRepository(firestore)
        val pocketName = "Viaje-${UUID.randomUUID().toString().take(6)}"

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createPocket(
                name = pocketName,
                initialAmount = 250000L,
                targetAmount = 2000000L,
                iconKey = "flight",
                colorKey = "blue"
            )
        }
        assertTrue(createResult.isSuccess)
        val pocketId = createResult.getOrThrow()

        val pockets = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observePockets().first { list -> list.any { it.id == pocketId } }
        }
        val pocket = pockets.first { it.id == pocketId }
        assertEquals(pocketName, pocket.name)
        assertEquals(250000L, pocket.currentAmount)
        assertEquals(2000000L, pocket.targetAmount)

        val movements = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMovements(pocketId).first { it.isNotEmpty() }
        }
        assertEquals(1, movements.size)
        assertEquals(PocketMovement.TYPE_INITIAL, movements.first().type)
        assertEquals(250000L, movements.first().amount)
    }

    @Test
    fun sharePocketByCode_partnerJoinsDepositsWithdrawsEditsAndLeaves_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = SavingsRepository(firestore)
        val pocketId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createPocket(
                name = "Fondo Pareja ${UUID.randomUUID().toString().take(5)}",
                initialAmount = 100000L,
                targetAmount = 1000000L,
                iconKey = "favorite",
                colorKey = "coral"
            ).getOrThrow()
        }

        val alicePocket = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observePockets().first { list -> list.any { it.id == pocketId } }
        }.first { it.id == pocketId }

        // Generate temporary code & regenerate it to ensure old code stops working
        val firstCode = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.generateOrRegenerateShareCode(alicePocket).getOrThrow()
        }
        val updatedPocket = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observePockets().first { list ->
                list.any { it.id == pocketId && it.shareCode == firstCode }
            }
        }.first { it.id == pocketId }

        val secondCode = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.generateOrRegenerateShareCode(updatedPocket).getOrThrow()
        }
        assertFalse(firstCode == secondCode)

        // Sign in as Bob (partner)
        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = SavingsRepository(firestore)

        // Old code must fail
        val oldJoin = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.joinPocketByCode(firstCode)
        }
        assertTrue(oldJoin.isFailure)

        // New formatted code (with hyphen) succeeds
        val joinResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.joinPocketByCode(SavingsRepository.formatDisplayCode(secondCode))
        }
        assertTrue(joinResult.isSuccess)

        val bobPockets = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observePockets().first { list -> list.any { it.id == pocketId } }
        }
        assertTrue(bobPockets.any { it.id == pocketId && bobUid in it.memberUids && aliceUid in it.memberUids })

        // Partner deposits 300k, attempts excessive withdrawal (fails), then withdraws 50k
        val depositResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.recordMovement(pocketId, PocketMovement.TYPE_DEPOSIT, 300000L, "Aporte Bob")
        }
        assertTrue(depositResult.isSuccess)
        assertEquals(400000L, depositResult.getOrThrow())

        val overWithdraw = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.recordMovement(pocketId, PocketMovement.TYPE_WITHDRAW, 900000L, "Exceso")
        }
        assertTrue(overWithdraw.isFailure)

        val withdrawResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.recordMovement(pocketId, PocketMovement.TYPE_WITHDRAW, 50000L, "Retiro parcial")
        }
        assertTrue(withdrawResult.isSuccess)
        assertEquals(350000L, withdrawResult.getOrThrow())

        // Partner edits goal & appearance
        val editResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.updatePocketDetails(
                pocketId = pocketId,
                name = "Fondo Pareja Editado",
                targetAmount = 1500000L,
                iconKey = "home",
                colorKey = "emerald"
            )
        }
        assertTrue(editResult.isSuccess)

        // Partner cannot delete the pocket
        val bobPocket = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observePockets().first { list ->
                list.any { it.id == pocketId && it.name == "Fondo Pareja Editado" }
            }
        }.first { it.id == pocketId }

        val bobDelete = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.deletePocket(bobPocket)
        }
        assertTrue(bobDelete.isFailure)

        // Partner leaves the pocket
        val leaveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.leavePocket(pocketId)
        }
        assertTrue(leaveResult.isSuccess)

        // After leaving, Bob loses access to movements
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeMovements(pocketId).first()
            }
            fail("Expected PERMISSION_DENIED after leaving pocket")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull(fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
        }

        // Alice (owner) can delete the pocket cleanly
        signInTestUser(ALICE_EMAIL)
        val aliceLatest = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observePockets().first { list ->
                list.any { it.id == pocketId && !(bobUid in it.memberUids) }
            }
        }.first { it.id == pocketId }
        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.deletePocket(aliceLatest)
        }
        assertTrue(deleteResult.isSuccess)
    }

    @Test
    fun unauthenticatedUser_cannotObservePockets() = runBlocking {
        auth.signOut()
        val repo = SavingsRepository(firestore)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observePockets().first()
            }
            fail("Expected exception when unauthenticated")
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun updateShowSavingsTab_persistsInUserProfile() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val budgetRepo = BudgetRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            budgetRepo.ensureProfileAndDefaults()
            budgetRepo.updateShowSavingsTab(false)
        }
        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            budgetRepo.observeUserProfile().first { it != null && !it.showSavingsTab }
        }
        assertFalse(profile!!.showSavingsTab)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@test.com"
        const val BOB_EMAIL = "bob@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
