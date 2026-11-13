package com.example.dev

import com.example.data.model.PocketMovement
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevTesterStoreTest {

    @Test
    fun devBudgetRepositories_isolateUserProfilesAndMonths() = runBlocking {
        val repo1 = DevBudgetRepository(DevTesterAccounts.PRIMARY)
        val repo2 = DevBudgetRepository(DevTesterAccounts.PARTNER)

        repo1.ensureProfileAndDefaults()
        repo2.ensureProfileAndDefaults()

        val profile1 = repo1.observeUserProfile().first()
        val profile2 = repo2.observeUserProfile().first()

        assertNotNull(profile1)
        assertNotNull(profile2)
        assertEquals(DevTesterAccounts.PRIMARY.defaultSalary, profile1!!.monthlySalary)
        assertEquals(DevTesterAccounts.PARTNER.defaultSalary, profile2!!.monthlySalary)

        repo1.ensureMonthInitialized("2026-11", profile1.monthlySalary)
        repo2.ensureMonthInitialized("2026-11", profile2.monthlySalary)

        val items1 = repo1.observeMonthItems("2026-11").first()
        val items2 = repo2.observeMonthItems("2026-11").first()

        assertTrue(items1.isNotEmpty())
        assertTrue(items2.isNotEmpty())
        assertTrue(items1.all { it.userId == DevTesterAccounts.PRIMARY.uid })
        assertTrue(items2.all { it.userId == DevTesterAccounts.PARTNER.uid })

        repo1.updateHiddenTabs(listOf("SAVINGS", "SETTINGS")).getOrThrow()
        val updated1 = repo1.observeUserProfile().first()
        assertEquals(listOf("SAVINGS"), updated1?.hiddenTabs)
        assertFalse(updated1?.showSavingsTab ?: true)

        repo1.updateHiddenTabs(emptyList()).getOrThrow()
        val restored1 = repo1.observeUserProfile().first()
        assertTrue(restored1?.hiddenTabs?.isEmpty() == true)
        assertTrue(restored1?.showSavingsTab == true)
    }

    @Test
    fun devSavingsRepositories_supportPartnerInviteAndSharedMovements() = runBlocking {
        val savings1 = DevSavingsRepository(DevTesterAccounts.PRIMARY)
        val savings2 = DevSavingsRepository(DevTesterAccounts.PARTNER)

        val pocketId = savings1.createPocket(
            name = "Vacaciones Tester",
            initialAmount = 200_000L,
            targetAmount = 1_000_000L,
            iconKey = "flight",
            colorKey = "blue"
        ).getOrThrow()

        val createdPocket = savings1.observePockets().first().first { it.id == pocketId }
        val inviteCode = savings1.generateOrRegenerateShareCode(createdPocket).getOrThrow()
        assertTrue(inviteCode.startsWith("AHO"))

        val joinedName = savings2.joinPocketByCode(inviteCode).getOrThrow()
        assertEquals("Vacaciones Tester", joinedName)

        val sharedPocketForPartner = savings2.observePockets().first().first { it.id == pocketId }
        assertTrue(DevTesterAccounts.PARTNER.uid in sharedPocketForPartner.memberUids)

        val updatedBalance = savings2.recordMovement(
            pocketId = pocketId,
            type = PocketMovement.TYPE_DEPOSIT,
            amount = 150_000L,
            note = "Aporte pareja"
        ).getOrThrow()
        assertEquals(350_000L, updatedBalance)

        val movements = savings1.observeMovements(pocketId).first()
        assertEquals(2, movements.size)

        savings2.leavePocket(pocketId).getOrThrow()
        val remainingPartnerPockets = savings2.observePockets().first()
        assertFalse(remainingPartnerPockets.any { it.id == pocketId })
    }
}
