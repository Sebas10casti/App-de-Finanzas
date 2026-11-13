package com.example.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleWalletNotificationParserTest {

    private val walletPkg = GoogleWalletNotificationParser.GOOGLE_WALLET_PACKAGE

    @Test
    fun parse_ignoresOtherAppsEvenWithCurrencyText() {
        val result = GoogleWalletNotificationParser.parse(
            packageName = "com.whatsapp",
            title = "Banco",
            text = "Pagaste $ 45.000 en Juan Valdez"
        )
        assertNull(result)
    }

    @Test
    fun parse_colombianCopDotThousandsWithPreposition_succeeds() {
        val result = GoogleWalletNotificationParser.parse(
            packageName = walletPkg,
            title = "Google Wallet",
            text = "Pagaste $ 1.800.000 en Apple Store con Mastercard •••• 1234"
        )
        assertNotNull(result)
        assertEquals(1800000L, result?.amount)
        assertEquals("Apple Store", result?.merchantName)
    }

    @Test
    fun parse_merchantContainingSpanishEl_isNotTruncated() {
        val result = GoogleWalletNotificationParser.parse(
            packageName = walletPkg,
            title = "Google Wallet",
            text = "Pagaste $ 32.500 en Hamburguesas El Corral con Visa •••• 9988"
        )
        assertNotNull(result)
        assertEquals(32500L, result?.amount)
        assertEquals("Hamburguesas El Corral", result?.merchantName)
    }

    @Test
    fun parse_merchantInTitleWithBulletCardDetailsInText_succeeds() {
        val result = GoogleWalletNotificationParser.parse(
            packageName = walletPkg,
            title = "Juan Valdez Café",
            text = "$ 14.500 • Mastercard •••• 4321"
        )
        assertNotNull(result)
        assertEquals(14500L, result?.amount)
        assertEquals("Juan Valdez Café", result?.merchantName)
    }

    @Test
    fun parse_priceInTitleAndMerchantInBulletText_succeeds() {
        val result = GoogleWalletNotificationParser.parse(
            packageName = walletPkg,
            title = "Pagaste $ 45.000,00",
            text = "Carulla Express • Visa •••• 1122"
        )
        assertNotNull(result)
        assertEquals(45000L, result?.amount)
        assertEquals("Carulla Express", result?.merchantName)
    }

    @Test
    fun parse_englishFormatCommaThousands_succeeds() {
        val result = GoogleWalletNotificationParser.parse(
            packageName = walletPkg,
            title = "$18,500 at Starbucks",
            text = "Paid with Visa •••• 5511"
        )
        assertNotNull(result)
        assertEquals(18500L, result?.amount)
        assertEquals("Starbucks", result?.merchantName)
    }

    @Test
    fun parse_copSuffixFormat_succeeds() {
        val result = GoogleWalletNotificationParser.parse(
            packageName = walletPkg,
            title = "D1 Tiendas",
            text = "Compra por 67.900 COP aprobada"
        )
        assertNotNull(result)
        assertEquals(67900L, result?.amount)
        assertEquals("D1 Tiendas", result?.merchantName)
    }

    @Test
    fun parse_declinedPayment_isIgnored() {
        val result = GoogleWalletNotificationParser.parse(
            packageName = walletPkg,
            title = "Pago rechazado",
            text = "$ 50.000 en Exito con Visa •••• 1234"
        )
        assertNull(result)
    }

    @Test
    fun parse_boardingPassOrVerification_isIgnored() {
        val boarding = GoogleWalletNotificationParser.parse(
            packageName = walletPkg,
            title = "Tu vuelo AV123",
            text = "Tarjeta de embarque • Puerta B12"
        )
        assertNull(boarding)

        val setup = GoogleWalletNotificationParser.parse(
            packageName = walletPkg,
            title = "Tu tarjeta ya está lista",
            text = "Ya puedes pagar sin contacto con Mastercard"
        )
        assertNull(setup)
    }
}
