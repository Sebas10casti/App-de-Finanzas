package com.example.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.R
import com.example.data.model.BudgetItem
import com.example.data.repository.BudgetRepository
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Listens strictly to Google Wallet notifications (`com.google.android.apps.walletnfcrel`)
 * when enabled in app settings and registers completed purchases into the current month's budget.
 */
class GoogleWalletNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(applicationContext).isEmpty()) {
            FirebaseApp.initializeApp(applicationContext)
        }
        configureAppCheck(applicationContext, null)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBarNotification = sbn ?: return

        // Strictly listen ONLY to Google Wallet
        if (statusBarNotification.packageName != GoogleWalletNotificationParser.GOOGLE_WALLET_PACKAGE) {
            return
        }

        // Must be explicitly enabled in Ajustes
        if (!WalletListenerPreferences.isEnabled(applicationContext)) {
            return
        }

        val notification = statusBarNotification.notification ?: return
        val flags = notification.flags
        if ((flags and Notification.FLAG_GROUP_SUMMARY) != 0 ||
            (flags and Notification.FLAG_ONGOING_EVENT) != 0
        ) {
            return
        }

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        val purchase = GoogleWalletNotificationParser.parse(
            packageName = statusBarNotification.packageName,
            title = title,
            text = text,
            bigText = bigText,
            subText = subText
        ) ?: return

        val keySignature = "key:${statusBarNotification.key}:${purchase.amount}"
        val txSignature = "tx:${purchase.amount}:${purchase.merchantName.lowercase(Locale.ROOT)}"
        if (!WalletListenerPreferences.markProcessedIfNew(
                applicationContext,
                listOf(keySignature, txSignature)
            )
        ) {
            Log.d(TAG, "Ignoring duplicate Google Wallet notification for ${purchase.merchantName}")
            return
        }

        serviceScope.launch {
            persistPurchase(purchase)
        }
    }

    private suspend fun persistPurchase(purchase: WalletPurchase) {
        val user = Firebase.auth.currentUser
        if (user == null) {
            Log.w(TAG, "Skipping Google Wallet expense: no authenticated user.")
            return
        }

        try {
            val repository = BudgetRepository(applicationContext)
            repository.ensureProfileAndDefaults()

            val profile = repository.observeUserProfile().firstOrNull()
            val salary = profile?.monthlySalary ?: DEFAULT_SALARY_COP
            val monthKey = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
            repository.ensureMonthInitialized(monthKey, salary)

            val category = WalletListenerPreferences.getDefaultCategory(applicationContext)
            val noteText = getString(R.string.wallet_auto_expense_note)

            val newItem = BudgetItem(
                monthKey = monthKey,
                name = purchase.merchantName,
                amount = purchase.amount,
                category = category,
                isPaid = true,
                isRecurring = false,
                notes = noteText
            )

            val result = repository.addItem(newItem)
            if (result.isSuccess) {
                val formattedAmount = NumberFormat.getCurrencyInstance(Locale("es", "CO")).apply {
                    maximumFractionDigits = 0
                }.format(purchase.amount)
                WalletListenerPreferences.setLastRecordedPurchase(
                    applicationContext,
                    "${purchase.merchantName} ($formattedAmount)"
                )
                Log.i(TAG, "Recorded Google Wallet purchase: ${purchase.merchantName} - $formattedAmount")
            } else {
                Log.e(TAG, "Failed to save Google Wallet purchase", result.exceptionOrNull())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error recording Google Wallet purchase", e)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WalletNotifListener"
        private const val DEFAULT_SALARY_COP = 11300000L
    }
}
