package com.example.notifications

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Base64
import androidx.core.app.NotificationManagerCompat
import com.example.data.model.BudgetCategories
import java.nio.charset.StandardCharsets

/**
 * Persists user configuration and short-window deduplication state for the
 * Google Wallet notification listener.
 */
object WalletListenerPreferences {
    private const val PREFS_NAME = "wallet_listener_prefs"
    private const val KEY_ENABLED = "wallet_listener_enabled"
    private const val KEY_DEFAULT_CATEGORY = "wallet_default_category"
    private const val KEY_LAST_PURCHASE = "wallet_last_purchase"
    private const val KEY_DEDUP_ENTRIES = "wallet_dedup_entries"
    private const val DEDUP_WINDOW_MS = 120_000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getDefaultCategory(context: Context): String {
        val saved = prefs(context).getString(KEY_DEFAULT_CATEGORY, null)
        return saved?.takeIf { it.isNotBlank() } ?: BudgetCategories.GASTOS_PERSONALES
    }

    fun setDefaultCategory(context: Context, category: String) {
        if (category.isNotBlank()) {
            prefs(context).edit().putString(KEY_DEFAULT_CATEGORY, category.trim()).apply()
        }
    }

    fun getLastRecordedPurchase(context: Context): String? =
        prefs(context).getString(KEY_LAST_PURCHASE, null)?.takeIf { it.isNotBlank() }

    fun setLastRecordedPurchase(context: Context, summary: String) {
        prefs(context).edit().putString(KEY_LAST_PURCHASE, summary).apply()
    }

    /**
     * Returns true if Android has Notification Listener access granted for this app.
     */
    fun hasSystemNotificationAccess(context: Context): Boolean {
        val appContext = context.applicationContext
        if (NotificationManagerCompat.getEnabledListenerPackages(appContext).contains(appContext.packageName)) {
            return true
        }
        val flat = Settings.Secure.getString(appContext.contentResolver, "enabled_notification_listeners")
            ?: return false
        val expectedComponent = ComponentName(appContext, GoogleWalletNotificationListenerService::class.java)
        val fullName = expectedComponent.flattenToString()
        val shortName = expectedComponent.flattenToShortString()
        return flat.split(':').any { it == fullName || it == shortName }
    }

    /**
     * Checks whether any of the provided deduplication signatures were already recorded within
     * [DEDUP_WINDOW_MS]. If all signatures are fresh, records them and returns true.
     */
    @Synchronized
    fun markProcessedIfNew(
        context: Context,
        signatures: List<String>,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (signatures.isEmpty()) return false
        val sharedPrefs = prefs(context)
        val raw = sharedPrefs.getString(KEY_DEDUP_ENTRIES, "") ?: ""
        val activeEntries = linkedMapOf<String, Long>()

        if (raw.isNotBlank()) {
            for (line in raw.split('\n')) {
                val sep = line.indexOf('|')
                if (sep <= 0) continue
                val ts = line.substring(0, sep).toLongOrNull() ?: continue
                if (nowMillis - ts > DEDUP_WINDOW_MS) continue
                val decodedSig = runCatching {
                    String(
                        Base64.decode(line.substring(sep + 1), Base64.NO_WRAP),
                        StandardCharsets.UTF_8
                    )
                }.getOrNull() ?: continue
                activeEntries[decodedSig] = ts
            }
        }

        if (signatures.any { activeEntries.containsKey(it) }) {
            return false
        }

        for (sig in signatures) {
            activeEntries[sig] = nowMillis
        }

        val serialized = activeEntries.entries.joinToString("\n") { (sig, ts) ->
            val encoded = Base64.encodeToString(
                sig.toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP
            )
            "$ts|$encoded"
        }
        sharedPrefs.edit().putString(KEY_DEDUP_ENTRIES, serialized).apply()
        return true
    }
}
