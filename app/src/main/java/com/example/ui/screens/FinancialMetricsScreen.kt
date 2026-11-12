package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CategoryVisuals
import com.example.ui.components.MarkdownText
import com.example.ui.viewmodel.BudgetViewModel
import com.example.ui.viewmodel.DiagnosisUiState
import java.util.Locale

@Composable
fun FinancialMetricsScreen(
    viewModel: BudgetViewModel
) {
    val metrics by viewModel.financialMetrics.collectAsState()
    val diagnosisState by viewModel.diagnosisState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Column {
            Text(
                text = "Salud de tu Sueldo",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Análisis financiero detallado y recomendaciones prácticas para Colombia",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. Health Score Card
        HealthScoreCard(
            score = metrics.healthScore,
            salary = metrics.salary,
            freeCash = metrics.freeCash,
            totalDebt = metrics.totalDebt,
            totalSavings = metrics.totalSavings,
            viewModel = viewModel
        )

        // 2. Gemini AI Live Diagnosis Trigger
        AiDiagnosisBanner(
            diagnosisState = diagnosisState,
            onGenerateClick = { viewModel.generateDiagnosis() }
        )

        // 3. Recommended Actions: "Qué deberías hacer"
        RecommendedActionsCard(
            freeCash = metrics.freeCash,
            viewModel = viewModel
        )

        // 4. Category Breakdown
        CategoryDistributionCard(
            categoryTotals = metrics.categoryTotals,
            totalSalary = metrics.salary,
            viewModel = viewModel
        )

        // 5. 50/30/20 Rule Comparison
        Rule503020Card(
            salary = metrics.salary,
            categoryTotals = metrics.categoryTotals,
            freeCash = metrics.freeCash,
            viewModel = viewModel
        )

        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
private fun HealthScoreCard(
    score: Int,
    salary: Long,
    freeCash: Long,
    totalDebt: Long,
    totalSavings: Long,
    viewModel: BudgetViewModel
) {
    val debtPct = if (salary > 0) (totalDebt.toDouble() / salary * 100) else 0.0
    val savingsPct = if (salary > 0) (totalSavings.toDouble() / salary * 100) else 0.0
    val freePct = if (salary > 0) (freeCash.toDouble() / salary * 100) else 0.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("health_score_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Score Financiero",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$score / 100",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (score >= 75) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF16A34A).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Text(
                text = when {
                    score >= 80 -> "¡Excelente equilibrio! Tienes un buen porcentaje de ahorro e inversión y deudas en un rango manejable."
                    score >= 65 -> "Estado financiero saludable. Tu prioridad número 1 debe ser cancelar las deudas de mayor tasa de interés."
                    else -> "Atención a tus obligaciones de crédito para ampliar tu margen de dinero libre mensual."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Three metric badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricMiniBadge(
                    label = "Ahorro/Inv.",
                    value = "${String.format(Locale.US, "%.1f", savingsPct)}%",
                    subtext = viewModel.formatCurrency(totalSavings),
                    color = Color(0xFF16A34A),
                    modifier = Modifier.weight(1f)
                )
                MetricMiniBadge(
                    label = "Endeudamiento",
                    value = "${String.format(Locale.US, "%.1f", debtPct)}%",
                    subtext = viewModel.formatCurrency(totalDebt),
                    color = Color(0xFFDC2626),
                    modifier = Modifier.weight(1f)
                )
                MetricMiniBadge(
                    label = "Libre",
                    value = "${String.format(Locale.US, "%.1f", freePct)}%",
                    subtext = viewModel.formatCurrency(freeCash),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricMiniBadge(
    label: String,
    value: String,
    subtext: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AiDiagnosisBanner(
    diagnosisState: DiagnosisUiState,
    onGenerateClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_diagnosis_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Diagnóstico Inteligente con Gemini",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Análisis profundo de tus números reales y proyecciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            when (diagnosisState) {
                is DiagnosisUiState.Idle -> {
                    Button(
                        onClick = onGenerateClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_generate_diagnosis"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analizar mi sueldo con IA")
                    }
                }
                is DiagnosisUiState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Analizando tu presupuesto y números reales...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                is DiagnosisUiState.Success -> {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MarkdownText(
                                text = diagnosisState.text,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = onGenerateClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Actualizar análisis")
                                }
                            }
                        }
                    }
                }
                is DiagnosisUiState.Error -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Aviso del servicio en la nube: ${diagnosisState.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = onGenerateClick,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generar diagnóstico financiero")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendedActionsCard(
    freeCash: Long,
    viewModel: BudgetViewModel
) {
    val items by viewModel.monthItems.collectAsState()
    val debts = items.filter { it.category == com.example.data.model.BudgetCategories.DEUDA_CREDITOS }.sortedBy { it.amount }
    val fvp = items.firstOrNull { it.name.contains("pensión", ignoreCase = true) || it.name.contains("fvp", ignoreCase = true) }
    val prepagada = items.firstOrNull { it.name.contains("prepagada", ignoreCase = true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "¿Qué deberías hacer con tu sueldo?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            val debtActionText = if (debts.isNotEmpty()) {
                val smallest = debts.first()
                if (debts.size >= 2) {
                    "Liquida primero tu deuda menor ('${smallest.name}' de ${viewModel.formatCurrency(smallest.amount)}). Al eliminarla, liberas ese flujo mensual inmediatamente para amortizar tus créditos mayores."
                } else {
                    "Abona a capital de tu crédito ('${smallest.name}' de ${viewModel.formatCurrency(smallest.amount)}) para reducir el cobro de intereses bancarios."
                }
            } else {
                "¡Excelente! No tienes deudas registradas este mes. Puedes destinar todo tu dinero libre a tu fondo de ahorro o inversiones de alto rendimiento."
            }

            ActionItemRow(
                number = "1",
                title = "Estrategia de Deudas (Bola de Nieve)",
                description = debtActionText
            )

            ActionItemRow(
                number = "2",
                title = "Aprovecha los ${viewModel.formatCurrency(freeCash)} Libres",
                description = "Destina este excedente a consolidar un Fondo de Emergencia de 3 a 6 meses de gastos básicos en cuentas remuneradas (Nu, Lulo, Pibank) para que rinda a tasas efectivas anuales."
            )

            val taxActionText = if (fvp != null || prepagada != null) {
                "Tus aportes a ${fvp?.name ?: "pensión voluntaria"}${if (prepagada != null) " y medicina prepagada" else ""} son deducibles según el Estatuto Tributario, reduciendo tu retención en la fuente mensual."
            } else {
                "En Colombia puedes abrir un Fondo de Pensión Voluntaria (FVP) o deducir medicina prepagada para disminuir legalmente tu retención en la fuente (Art. 126-1 E.T.)."
            }

            ActionItemRow(
                number = "3",
                title = "Beneficio Tributario en Colombia",
                description = taxActionText
            )
        }
    }
}

@Composable
private fun ActionItemRow(
    number: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryDistributionCard(
    categoryTotals: Map<String, Long>,
    totalSalary: Long,
    viewModel: BudgetViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Distribución por Categorías",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            val sortedCategories = categoryTotals.entries.sortedByDescending { it.value }
            for ((category, amount) in sortedCategories) {
                val percentage = if (totalSalary > 0) (amount.toFloat() / totalSalary.toFloat()) else 0f
                val catColor = CategoryVisuals.getColor(category)

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(catColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = category,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "${viewModel.formatCurrency(amount)} (${String.format(Locale.US, "%.1f", percentage * 100)}%)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LinearProgressIndicator(
                        progress = { percentage.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = catColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Rule503020Card(
    salary: Long,
    categoryTotals: Map<String, Long>,
    freeCash: Long,
    viewModel: BudgetViewModel
) {
    // 50/30/20 calculation
    val needs = (categoryTotals["Gastos Hogar"] ?: 0L) +
            (categoryTotals["Servicios Publicos"] ?: 0L) +
            (categoryTotals["DeduciblesEmpresa"] ?: 0L) +
            (categoryTotals["Vehiculo"] ?: 0L)

    val wants = (categoryTotals["Gastos personales"] ?: 0L) +
            (categoryTotals["Deporte"] ?: 0L)

    val savingsAndDebt = (categoryTotals["Ahorro e inversión"] ?: 0L) +
            (categoryTotals["Deuda y creditos"] ?: 0L)

    val needsPct = if (salary > 0) (needs.toDouble() / salary * 100) else 0.0
    val wantsPct = if (salary > 0) (wants.toDouble() / salary * 100) else 0.0
    val savingsDebtPct = if (salary > 0) (savingsAndDebt.toDouble() / salary * 100) else 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tu Presupuesto vs Regla 50 / 30 / 20",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Rule503020Row(
                title = "Necesidades Básicas (Meta: ≤50%)",
                actualPct = String.format(Locale.US, "%.1f", needsPct),
                actualAmount = viewModel.formatCurrency(needs),
                status = "✅ Excelente (${String.format(Locale.US, "%.1f", needsPct)}%)",
                isGood = needsPct <= 50.0
            )

            Rule503020Row(
                title = "Estilo de Vida y Ocio (Meta: ≤30%)",
                actualPct = String.format(Locale.US, "%.1f", wantsPct),
                actualAmount = viewModel.formatCurrency(wants),
                status = "✅ Muy controlado (${String.format(Locale.US, "%.1f", wantsPct)}%)",
                isGood = wantsPct <= 30.0
            )

            Rule503020Row(
                title = "Ahorros y Desendeudamiento (Meta: ≥20%)",
                actualPct = String.format(Locale.US, "%.1f", savingsDebtPct),
                actualAmount = viewModel.formatCurrency(savingsAndDebt),
                status = "🚀 Alto enfoque (${String.format(Locale.US, "%.1f", savingsDebtPct)}%)",
                isGood = true
            )
        }
    }
}

@Composable
private fun Rule503020Row(
    title: String,
    actualPct: String,
    actualAmount: String,
    status: String,
    isGood: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = if (isGood) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
            )
        }
        Text(
            text = "Total actual: $actualAmount",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
