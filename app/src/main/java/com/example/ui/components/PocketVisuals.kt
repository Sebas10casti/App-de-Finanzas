package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.CategoryAhorro
import com.example.ui.theme.CategoryDeducibles
import com.example.ui.theme.CategoryDeuda
import com.example.ui.theme.CategoryHogar
import com.example.ui.theme.CategoryPersonal
import com.example.ui.theme.CategoryServicios
import com.example.ui.theme.CategoryVehiculo
import com.example.ui.theme.SecondaryTeal

data class PocketIconChoice(
    val key: String,
    val label: String,
    val icon: ImageVector
)

data class PocketColorChoice(
    val key: String,
    val label: String,
    val color: Color
)

object PocketVisuals {
    val iconChoices = listOf(
        PocketIconChoice("savings", "Alcancía", Icons.Default.Savings),
        PocketIconChoice("flight", "Viaje", Icons.Default.Flight),
        PocketIconChoice("home", "Hogar", Icons.Default.Home),
        PocketIconChoice("shield", "Emergencia", Icons.Default.Shield),
        PocketIconChoice("favorite", "Pareja", Icons.Default.Favorite),
        PocketIconChoice("directions_car", "Vehículo", Icons.Default.DirectionsCar),
        PocketIconChoice("school", "Estudio", Icons.Default.School),
        PocketIconChoice("shopping_bag", "Compras", Icons.Default.ShoppingBag)
    )

    val colorChoices = listOf(
        PocketColorChoice("emerald", "Esmeralda", CategoryAhorro),
        PocketColorChoice("blue", "Azul", CategoryHogar),
        PocketColorChoice("teal", "Turquesa", SecondaryTeal),
        PocketColorChoice("gold", "Dorado", CategoryPersonal),
        PocketColorChoice("coral", "Coral", CategoryDeuda),
        PocketColorChoice("purple", "Púrpura", CategoryDeducibles),
        PocketColorChoice("sky", "Cielo", CategoryServicios),
        PocketColorChoice("orange", "Naranja", CategoryVehiculo)
    )

    fun getIcon(key: String): ImageVector {
        return iconChoices.firstOrNull { it.key == key }?.icon ?: Icons.Default.Savings
    }

    fun getColor(key: String): Color {
        return colorChoices.firstOrNull { it.key == key }?.color ?: CategoryAhorro
    }
}
