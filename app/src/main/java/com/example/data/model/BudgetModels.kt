package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val monthlySalary: Long = 0L,
    val currency: String = "COP",
    val displayName: String = "",
    val customCategories: List<String> = emptyList(),
    val showSavingsTab: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MonthlyBudget(
    @DocumentId val id: String = "",
    val userId: String = "",
    val monthKey: String = "", // e.g. "2026-11"
    val salary: Long = 11300000L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BudgetItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val monthKey: String = "",
    val name: String = "",
    val amount: Long = 0L,
    val category: String = "Otros",
    val isPaid: Boolean = false,
    val isRecurring: Boolean = true, // true = fixed monthly expense, false = one-off this month
    @ServerTimestamp val paidAt: Timestamp? = null,
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TemplateItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val amount: Long = 0L,
    val category: String = "Otros",
    val isRecurring: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user", // "user" or "model"
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class CategoryUsageItem(
    val name: String,
    val amount: Long,
    val occurrences: Int = 1
)

// Categories used in Sebas's Excel file
object BudgetCategories {
    const val DELETED_PREFIX = "!DEL:"
    const val GASTOS_HOGAR = "Gastos Hogar"
    const val SERVICIOS_PUBLICOS = "Servicios Publicos"
    const val DEDUCIBLES_EMPRESA = "DeduciblesEmpresa"
    const val DEPORTE = "Deporte"
    const val DEUDA_CREDITOS = "Deuda y creditos"
    const val GASTOS_PERSONALES = "Gastos personales"
    const val AHORRO_INVERSION = "Ahorro e inversión"
    const val VEHICULO = "Vehiculo"
    const val OTROS = "Otros"

    val ALL = listOf(
        GASTOS_HOGAR,
        SERVICIOS_PUBLICOS,
        DEDUCIBLES_EMPRESA,
        DEPORTE,
        DEUDA_CREDITOS,
        GASTOS_PERSONALES,
        AHORRO_INVERSION,
        VEHICULO,
        OTROS
    )
}

// Default items exactly from Sebas's Excel sheet
data class DefaultItemDef(
    val name: String,
    val amount: Long,
    val category: String
)

val SEBAS_DEFAULT_ITEMS = listOf(
    DefaultItemDef("Arriendo", 1400000L, BudgetCategories.GASTOS_HOGAR),
    DefaultItemDef("Luz", 90000L, BudgetCategories.SERVICIOS_PUBLICOS),
    DefaultItemDef("Agua", 90000L, BudgetCategories.SERVICIOS_PUBLICOS),
    DefaultItemDef("Gas", 10000L, BudgetCategories.SERVICIOS_PUBLICOS),
    DefaultItemDef("Internet", 100000L, BudgetCategories.SERVICIOS_PUBLICOS),
    DefaultItemDef("Mercado", 700000L, BudgetCategories.GASTOS_HOGAR),
    DefaultItemDef("Datos", 40000L, BudgetCategories.SERVICIOS_PUBLICOS),
    DefaultItemDef("Ahorro gastos de la moto", 300000L, BudgetCategories.VEHICULO),
    DefaultItemDef("Medicina prepagada", 340000L, BudgetCategories.DEDUCIBLES_EMPRESA),
    DefaultItemDef("Retencion en la fuente", 114000L, BudgetCategories.DEDUCIBLES_EMPRESA),
    DefaultItemDef("Fondo de solidaridad", 100000L, BudgetCategories.DEDUCIBLES_EMPRESA),
    DefaultItemDef("Salud y Pension", 760000L, BudgetCategories.DEDUCIBLES_EMPRESA),
    DefaultItemDef("Gym, natación, atletismo", 200000L, BudgetCategories.DEPORTE),
    DefaultItemDef("Anbu", 120000L, BudgetCategories.GASTOS_HOGAR),
    DefaultItemDef("Ocio y vida personal", 600000L, BudgetCategories.GASTOS_PERSONALES),
    DefaultItemDef("Credito Moto", 1600000L, BudgetCategories.DEUDA_CREDITOS),
    DefaultItemDef("Tarjeta Credito Rappi", 700000L, BudgetCategories.DEUDA_CREDITOS),
    DefaultItemDef("Fondo pensión voluntaria", 1800000L, BudgetCategories.AHORRO_INVERSION),
    DefaultItemDef("Cuota Seguro Moto", 200000L, BudgetCategories.GASTOS_HOGAR),
    DefaultItemDef("Gemini Plan", 20000L, BudgetCategories.SERVICIOS_PUBLICOS),
    DefaultItemDef("Netflix y youtube music", 40000L, BudgetCategories.SERVICIOS_PUBLICOS),
    DefaultItemDef("Ahorro Viaje con amor", 100000L, BudgetCategories.GASTOS_PERSONALES),
    DefaultItemDef("Compras Internet", 200000L, BudgetCategories.GASTOS_PERSONALES),
    DefaultItemDef("Ahorro emergencia", 300000L, BudgetCategories.GASTOS_HOGAR),
    DefaultItemDef("Abono Computador Juano", 200000L, BudgetCategories.DEUDA_CREDITOS)
)

val GENERIC_STARTER_ITEMS = listOf(
    DefaultItemDef("Arriendo / Vivienda", 0L, BudgetCategories.GASTOS_HOGAR),
    DefaultItemDef("Luz y Agua", 0L, BudgetCategories.SERVICIOS_PUBLICOS),
    DefaultItemDef("Internet y Telefonía", 0L, BudgetCategories.SERVICIOS_PUBLICOS),
    DefaultItemDef("Mercado y Alimentación", 0L, BudgetCategories.GASTOS_HOGAR),
    DefaultItemDef("Transporte / Movilidad", 0L, BudgetCategories.VEHICULO),
    DefaultItemDef("Salud y Seguridad Social", 0L, BudgetCategories.DEDUCIBLES_EMPRESA),
    DefaultItemDef("Ahorro / Fondo Emergencia", 0L, BudgetCategories.AHORRO_INVERSION),
    DefaultItemDef("Gastos Personales y Ocio", 0L, BudgetCategories.GASTOS_PERSONALES)
)

