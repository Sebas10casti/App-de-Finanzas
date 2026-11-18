package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
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
    val hiddenTabs: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MonthlyBudget(
    @DocumentId val id: String = "",
    val userId: String = "",
    val monthKey: String = "", // e.g. "2026-11"
    val salary: Long = 0L,
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
    @get:PropertyName("isPaid")
    @field:PropertyName("isPaid")
    val isPaid: Boolean = false,
    @get:PropertyName("isRecurring")
    @field:PropertyName("isRecurring")
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
    @get:PropertyName("isRecurring")
    @field:PropertyName("isRecurring")
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

// Default expense categories
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

// Fictional sample items used only in Debug tester/demo mode
data class DefaultItemDef(
    val name: String,
    val amount: Long,
    val category: String
)

val DEMO_DEFAULT_ITEMS = listOf(
    DefaultItemDef("Arriendo apartamento", 1200000L, BudgetCategories.GASTOS_HOGAR),
    DefaultItemDef("Energía eléctrica", 85000L, BudgetCategories.SERVICIOS_PUBLICOS),
    DefaultItemDef("Acueducto", 65000L, BudgetCategories.SERVICIOS_PUBLICOS),
    DefaultItemDef("Gas natural", 25000L, BudgetCategories.SERVICIOS_PUBLICOS),
    DefaultItemDef("Internet fibra", 90000L, BudgetCategories.SERVICIOS_PUBLICOS),
    DefaultItemDef("Plan celular", 45000L, BudgetCategories.SERVICIOS_PUBLICOS),
    DefaultItemDef("Supermercado", 650000L, BudgetCategories.GASTOS_HOGAR),
    DefaultItemDef("Transporte y combustible", 220000L, BudgetCategories.VEHICULO),
    DefaultItemDef("Salud y pensión", 420000L, BudgetCategories.DEDUCIBLES_EMPRESA),
    DefaultItemDef("Gimnasio", 110000L, BudgetCategories.DEPORTE),
    DefaultItemDef("Crédito vehículo", 650000L, BudgetCategories.DEUDA_CREDITOS),
    DefaultItemDef("Tarjeta de crédito", 350000L, BudgetCategories.DEUDA_CREDITOS),
    DefaultItemDef("Ahorro programado", 600000L, BudgetCategories.AHORRO_INVERSION),
    DefaultItemDef("Fondo de emergencia", 300000L, BudgetCategories.AHORRO_INVERSION),
    DefaultItemDef("Entretenimiento y streaming", 55000L, BudgetCategories.SERVICIOS_PUBLICOS),
    DefaultItemDef("Ocio y salidas", 250000L, BudgetCategories.GASTOS_PERSONALES)
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

