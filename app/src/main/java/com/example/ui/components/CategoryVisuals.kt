package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.model.BudgetCategories
import com.example.ui.theme.CategoryAhorro
import com.example.ui.theme.CategoryDeducibles
import com.example.ui.theme.CategoryDeporte
import com.example.ui.theme.CategoryDeuda
import com.example.ui.theme.CategoryHogar
import com.example.ui.theme.CategoryOtros
import com.example.ui.theme.CategoryPersonal
import com.example.ui.theme.CategoryServicios
import com.example.ui.theme.CategoryVehiculo

object CategoryVisuals {
    private val customPalette = listOf(
        Color(0xFF0D9488), // Teal
        Color(0xFF6366F1), // Indigo
        Color(0xFFEC4899), // Pink
        Color(0xFFF59E0B), // Amber
        Color(0xFF8B5CF6), // Purple
        Color(0xFF06B6D4), // Cyan
        Color(0xFF10B981), // Emerald
        Color(0xFFF97316), // Orange
        Color(0xFF84CC16)  // Lime
    )

    fun getColor(category: String): Color {
        return when (category) {
            BudgetCategories.GASTOS_HOGAR -> CategoryHogar
            BudgetCategories.SERVICIOS_PUBLICOS -> CategoryServicios
            BudgetCategories.DEDUCIBLES_EMPRESA -> CategoryDeducibles
            BudgetCategories.DEPORTE -> CategoryDeporte
            BudgetCategories.DEUDA_CREDITOS -> CategoryDeuda
            BudgetCategories.GASTOS_PERSONALES -> CategoryPersonal
            BudgetCategories.AHORRO_INVERSION -> CategoryAhorro
            BudgetCategories.VEHICULO -> CategoryVehiculo
            BudgetCategories.OTROS -> CategoryOtros
            else -> {
                val index = kotlin.math.abs(category.hashCode()) % customPalette.size
                customPalette[index]
            }
        }
    }

    fun getIcon(category: String): ImageVector {
        val lower = category.lowercase()
        return when {
            category == BudgetCategories.GASTOS_HOGAR || lower.contains("hogar") || lower.contains("casa") || lower.contains("arriendo") -> Icons.Default.Home
            category == BudgetCategories.SERVICIOS_PUBLICOS || lower.contains("servicio") || lower.contains("luz") || lower.contains("agua") -> Icons.Default.Lightbulb
            category == BudgetCategories.DEDUCIBLES_EMPRESA || lower.contains("deducible") || lower.contains("impuesto") -> Icons.Default.ReceiptLong
            category == BudgetCategories.DEPORTE || lower.contains("deporte") || lower.contains("gym") || lower.contains("fitness") -> Icons.Default.FitnessCenter
            category == BudgetCategories.DEUDA_CREDITOS || lower.contains("deuda") || lower.contains("credito") || lower.contains("tarjeta") -> Icons.Default.CreditCard
            category == BudgetCategories.GASTOS_PERSONALES || lower.contains("personal") || lower.contains("ropa") || lower.contains("compras") -> Icons.Default.ShoppingBag
            category == BudgetCategories.AHORRO_INVERSION || lower.contains("ahorro") || lower.contains("inversion") || lower.contains("cdt") -> Icons.Default.Savings
            category == BudgetCategories.VEHICULO || lower.contains("vehiculo") || lower.contains("moto") || lower.contains("carro") || lower.contains("gasolina") -> Icons.Default.TwoWheeler
            else -> Icons.Default.Category
        }
    }
}
