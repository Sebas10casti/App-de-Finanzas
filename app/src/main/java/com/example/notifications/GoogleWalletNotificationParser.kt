package com.example.notifications

import kotlin.math.roundToLong

data class WalletPurchase(
    val merchantName: String,
    val amount: Long
)

/**
 * Parses notifications strictly from Google Wallet ([GOOGLE_WALLET_PACKAGE]) into expense entries.
 */
object GoogleWalletNotificationParser {
    const val GOOGLE_WALLET_PACKAGE = "com.google.android.apps.walletnfcrel"
    private const val FALLBACK_MERCHANT = "Compra Google Wallet"
    private const val MAX_NAME_LENGTH = 120
    private const val MAX_AMOUNT_COP = 1_000_000_000L

    private val CURRENCY_PREFIX_REGEX = Regex(
        pattern = """(?i)(?:COP|US\$|USD|\$)\s*(\d{1,3}(?:[.,]\d{3})+|\d+)(?:([.,])(\d{1,2}))?(?!\d)"""
    )
    private val CURRENCY_SUFFIX_REGEX = Regex(
        pattern = """(?i)(?<!\d)(\d{1,3}(?:[.,]\d{3})+|\d+)(?:([.,])(\d{1,2}))?\s*(?:COP|USD)\b"""
    )
    private val MERCHANT_PREPOSITION_REGEX = Regex(
        pattern = """(?i)\b(?:en|at)\s+([^\n•]+?)(?=\s+(?:con|with|usando|using)\b|\s+(?:el|on)\s+\d|\s+a\s+las\b|\s*[•|(\[]|\s*$)"""
    )
    private val GENERIC_TITLES = setOf(
        "google wallet",
        "billetera de google",
        "wallet",
        "billetera",
        "google pay",
        "pago",
        "compra",
        "payment",
        "purchase"
    )
    private val DECLINED_KEYWORDS = listOf(
        "rechazad",
        "declined",
        "no se pudo",
        "fallid",
        "failed",
        "insufficient",
        "insuficiente",
        "cancelad",
        "canceled",
        "cancelled",
        "caducad",
        "expired"
    )
    private val NON_PURCHASE_KEYWORDS = listOf(
        "código de verificación",
        "codigo de verificacion",
        "verification code",
        "tarjeta ya está lista",
        "tarjeta ya esta lista",
        "ready to pay",
        "ready for contactless",
        "tarjeta de embarque",
        "boarding pass",
        "pase de abordar"
    )
    private val CARD_NOISE_REGEX = Regex(
        pattern = """(?i)(?:visa|mastercard|amex|american express|maestro|diners|tarjeta|card|••••|\*\*\*\*|\.\.\.\.)"""
    )

    fun parse(
        packageName: String?,
        title: String?,
        text: String?,
        bigText: String? = null,
        subText: String? = null
    ): WalletPurchase? {
        if (packageName != GOOGLE_WALLET_PACKAGE) return null

        val cleanTitle = title?.trim().orEmpty()
        val cleanText = text?.trim().orEmpty()
        val cleanBigText = bigText?.trim().orEmpty()
        val cleanSubText = subText?.trim().orEmpty()

        val combined = listOf(cleanTitle, cleanBigText, cleanText, cleanSubText)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        if (combined.isBlank()) return null

        val lower = combined.lowercase()
        if (DECLINED_KEYWORDS.any { lower.contains(it) }) return null
        if (NON_PURCHASE_KEYWORDS.any { lower.contains(it) }) return null

        val amount = extractAmount(cleanBigText)
            ?: extractAmount(cleanText)
            ?: extractAmount(cleanTitle)
            ?: extractAmount(cleanSubText)
            ?: return null

        if (amount <= 0L || amount > MAX_AMOUNT_COP) return null

        val merchant = extractMerchant(
            title = cleanTitle,
            text = cleanText,
            bigText = cleanBigText
        )

        return WalletPurchase(
            merchantName = merchant,
            amount = amount
        )
    }

    internal fun extractAmount(source: String): Long? {
        if (source.isBlank()) return null
        val match = CURRENCY_PREFIX_REGEX.find(source) ?: CURRENCY_SUFFIX_REGEX.find(source) ?: return null
        val wholeRaw = match.groupValues[1]
        val decimalSep = match.groupValues[2]
        val decimalDigits = match.groupValues[3]

        val wholeDigits = wholeRaw.replace(".", "").replace(",", "")
        val wholeValue = wholeDigits.toLongOrNull() ?: return null

        if (decimalDigits.isEmpty()) return wholeValue

        // If decimals exist (e.g. $45.000,00 or $12.50), round only when non-zero fractional cents exist
        val centsValue = decimalDigits.toDoubleOrNull() ?: 0.0
        val divisor = if (decimalDigits.length == 1) 10.0 else 100.0
        val rounded = (wholeValue.toDouble() + (centsValue / divisor)).roundToLong()
        return if (decimalSep.isNotEmpty()) rounded else wholeValue
    }

    internal fun extractMerchant(
        title: String,
        text: String,
        bigText: String
    ): String {
        val candidates = listOf(bigText, text, title).filter { it.isNotBlank() }

        // 1. Explicit "en <Merchant>" or "at <Merchant>"
        for (candidate in candidates) {
            val match = MERCHANT_PREPOSITION_REGEX.find(candidate)
            if (match != null) {
                val cleaned = sanitizeMerchantCandidate(match.groupValues[1])
                if (cleaned != null) return cleaned
            }
        }

        // 2. Title itself if it looks like a merchant name (not generic "Google Wallet" or just the price)
        if (title.isNotBlank()) {
            val cleanedTitle = sanitizeMerchantCandidate(title)
            if (cleanedTitle != null) return cleanedTitle
        }

        // 3. First descriptive segment from bigText / text split by bullet/newline/hyphen
        for (candidate in listOf(bigText, text)) {
            val segments = candidate.split('•', '\n', '|')
            for (seg in segments) {
                val cleaned = sanitizeMerchantCandidate(seg)
                if (cleaned != null) return cleaned
            }
        }

        return FALLBACK_MERCHANT
    }

    private fun sanitizeMerchantCandidate(raw: String): String? {
        var text = raw.trim()
        if (text.isBlank()) return null

        // Strip currency amounts & common payment lead-ins
        text = CURRENCY_PREFIX_REGEX.replace(text, " ")
        text = CURRENCY_SUFFIX_REGEX.replace(text, " ")
        text = text.replace(Regex("""(?i)\b(?:pagaste|pago aprobado|pago realizado|pago con tarjeta|pago|compra por|compra en|compra|you paid|paid|purchase at|purchase)\b"""), " ")
        text = text.replace(Regex("""(?i)\b(?:con|with|por|en|at)\s*$"""), " ")

        // Remove card mask suffixes like "•••• 1234" or "Visa 1234"
        text = text.replace(Regex("""(?i)(?:•{2,}|\*{2,})\s*\d{0,4}"""), " ")
        text = text.replace(Regex("""\s+"""), " ").trim(' ', '-', ':', '.', ',', '•', '|')

        if (text.isBlank()) return null
        val lower = text.lowercase()
        if (lower in GENERIC_TITLES) return null
        if (CARD_NOISE_REGEX.containsMatchIn(lower) && text.split(' ').size <= 3) return null
        if (text.all { it.isDigit() || it == '.' || it == ',' || it == '$' }) return null

        return text.take(MAX_NAME_LENGTH).trim()
    }
}
