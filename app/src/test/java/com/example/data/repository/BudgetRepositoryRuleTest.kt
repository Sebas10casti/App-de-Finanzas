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
    assertEquals(11300000L, profile?.monthlySalary)
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
    assertTrue(items.any { it.name == "Arriendo" })
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

  private companion object {
    const val ALICE_EMAIL = "alice@test.com"
    const val BOB_EMAIL = "bob@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 5000L
  }
}
