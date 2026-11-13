package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.BudgetItem
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BudgetRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun ensureProfileAndDefaults_authenticatedUser_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = BudgetRepository(firestore)

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureProfileAndDefaults()
    }
    assertTrue(result.isSuccess)

    val profile = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.observeUserProfile().first()
    }
    assertNotNull(profile)
    assertEquals(0L, profile?.monthlySalary)
    assertTrue(profile?.hiddenTabs?.isEmpty() == true)

    val hiddenResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.updateHiddenTabs(listOf("METRICS", "AI_ADVISOR", "SETTINGS"))
    }
    assertTrue(hiddenResult.isSuccess)

    val updatedProfile = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeUserProfile().first { it != null && it.hiddenTabs.size == 2 }
    }
    assertEquals(listOf("METRICS", "AI_ADVISOR"), updatedProfile?.hiddenTabs)
    assertTrue(updatedProfile?.showSavingsTab == true)
  }

  @Test
  fun ensureMonthInitialized_authenticatedUser_populatesMonthItems() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = BudgetRepository(firestore)

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureMonthInitialized("2026-11", 11300000L)
    }
    assertTrue(result.isSuccess)

    val items = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMonthItems("2026-11").first { it.isNotEmpty() }
    }
    assertTrue(items.any { it.name.contains("Arriendo") })
  }

  @Test
  fun paidStatus_andRecurringFlag_persistAndDeserializeFromFirestore() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = BudgetRepository(firestore)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureMonthInitialized("2026-12", 11300000L)
    }.getOrThrow()

    val oneOffId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.addItem(
        BudgetItem(
          monthKey = "2026-12",
          name = "Gasto Único",
          amount = 250000L,
          category = "Otros",
          isPaid = false,
          isRecurring = false
        )
      )
    }.getOrThrow()

    val initialItems = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMonthItems("2026-12").first { list -> list.any { it.id == oneOffId } }
    }
    val oneOffItem = initialItems.first { it.id == oneOffId }
    assertEquals(false, oneOffItem.isRecurring)
    assertEquals(false, oneOffItem.isPaid)

    val secondItem = initialItems.first { it.id != oneOffId }

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.toggleItemPaid(oneOffItem)
    }.getOrThrow()
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.markItemsPaid("2026-12", true, listOf(secondItem.id))
    }.getOrThrow()

    // Simulate a fresh repository read (as after app restart)
    val freshRepo = BudgetRepository(firestore)
    val reloaded = withTimeout(FLOW_TIMEOUT_MS) {
      freshRepo.observeMonthItems("2026-12").first { list ->
        list.firstOrNull { it.id == oneOffId }?.isPaid == true &&
          list.firstOrNull { it.id == secondItem.id }?.isPaid == true
      }
    }
    assertEquals(true, reloaded.first { it.id == oneOffId }.isPaid)
    assertEquals(false, reloaded.first { it.id == oneOffId }.isRecurring)
    assertEquals(true, reloaded.first { it.id == secondItem.id }.isPaid)

    val walletLikeId = withTimeout(DEFAULT_TIMEOUT_MS) {
      freshRepo.addItem(
        BudgetItem(
          monthKey = "2026-12",
          name = "Juan Valdez",
          amount = 14500L,
          category = "Gastos personales",
          isPaid = true,
          isRecurring = false,
          notes = "Registrado automáticamente desde Google Wallet"
        )
      )
    }.getOrThrow()

    val walletReloaded = withTimeout(FLOW_TIMEOUT_MS) {
      freshRepo.observeMonthItems("2026-12").first { list -> list.any { it.id == walletLikeId } }
    }.first { it.id == walletLikeId }
    assertEquals(true, walletReloaded.isPaid)
    assertEquals(false, walletReloaded.isRecurring)
    assertNotNull(walletReloaded.paidAt)
  }

  @Test
  fun unauthenticatedUser_failsWithException() = runBlocking {
    auth.signOut()
    val repo = BudgetRepository(firestore)

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeMonthItems("2026-11").first()
      }
      fail("Expected exception when unauthenticated")
    } catch (e: Exception) {
      assertNotNull(e)
    }
  }

  @Test
  fun crossUser_cannotAccessOtherUserItems() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = BudgetRepository(firestore)
    val addResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.addItem(
        BudgetItem(
          monthKey = "2026-11",
          name = "Secret Expense",
          amount = 50000L,
          category = "Otros",
          isPaid = false
        )
      )
    }
    assertTrue(addResult.isSuccess)
    val itemId = addResult.getOrThrow()

    // Sign in as Bob
    signInTestUser(BOB_EMAIL)
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore.collection("users").document(aliceUid)
          .collection("months").document("2026-11")
          .collection("items").document(itemId)
          .get()
          .await()
      }
      fail("Expected PERMISSION_DENIED when Bob tries to read Alice's item")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    } catch (e: Exception) {
      val firestoreException = e.unwrapFirestoreException()
      assertNotNull("Expected FirebaseFirestoreException", firestoreException)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }
  }

  @Test
  fun categoryReassignment_acrossMonthsAndTemplates_succeeds() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = BudgetRepository(firestore)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureProfileAndDefaults()
      repo.ensureMonthInitialized("2026-11", 5000000L)
      repo.ensureMonthInitialized("2026-12", 5000000L)
      repo.addItem(
        BudgetItem(
          monthKey = "2026-11",
          name = "Natación Club",
          amount = 150000L,
          category = "Deporte",
          isPaid = false
        )
      )
      repo.addItem(
        BudgetItem(
          monthKey = "2026-12",
          name = "Natación Club",
          amount = 150000L,
          category = "Deporte",
          isPaid = false
        )
      )
    }

    val usageResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.findItemsUsingCategory("Deporte")
    }
    assertTrue(usageResult.isSuccess)
    val usageItems = usageResult.getOrThrow()
    assertTrue(usageItems.any { it.name == "Natación Club" })

    val reassignResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.reassignCategoryAcrossAllMonths(
        oldCategory = "Deporte",
        reassignmentsByNormalizedName = mapOf("natación club" to "Gastos personales"),
        fallbackCategory = "Otros"
      )
    }
    assertTrue(reassignResult.isSuccess)

    val afterUsage = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.findItemsUsingCategory("Deporte")
    }
    assertTrue(afterUsage.isSuccess)
    assertEquals(0, afterUsage.getOrThrow().size)

    val monthItems = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMonthItems("2026-11").first { list ->
        list.any { it.name == "Natación Club" && it.category == "Gastos personales" }
      }
    }
    assertTrue(monthItems.any { it.name == "Natación Club" && it.category == "Gastos personales" })

    val customCatResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.updateCustomCategories(listOf("!DEL:Deporte"))
    }
    assertTrue(customCatResult.isSuccess)
  }

  @Test
  fun addChatMessage_withDeterministicId_usesExactDocumentIdAndClearsAll() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = BudgetRepository(firestore)
    val deterministicId = "msg-deterministic-1234"

    val addResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.addChatMessage(
        role = "user",
        text = "¿Cuál de mis deudas debería liquidar primero?",
        messageId = deterministicId
      )
    }
    assertTrue(addResult.isSuccess)

    val messages = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeChatMessages().first { it.isNotEmpty() }
    }
    assertEquals(1, messages.size)
    assertEquals(deterministicId, messages.first().id)

    val clearResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.clearChatMessages(emptyList())
    }
    assertTrue(clearResult.isSuccess)

    val afterClear = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeChatMessages().first { it.isEmpty() }
    }
    assertTrue(afterClear.isEmpty())
  }

  private companion object {
    const val ALICE_EMAIL = "alice@test.com"
    const val BOB_EMAIL = "bob@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 5000L
  }
}
