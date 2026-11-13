package com.example.ai

import android.util.Log
import com.example.data.model.BudgetCategories
import com.example.data.model.BudgetItem
import com.example.data.model.ChatMessage
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import java.net.ConnectException
import java.net.UnknownHostException
import java.text.NumberFormat
import java.util.Locale

private const val TAG = "FinancialAiService"
private const val MODEL_NAME = "gemini-3.5-flash-lite"
private const val AI_TIMEOUT_MS = 60_000L

class FinancialAiService {

    private val copFormat = NumberFormat.getCurrencyInstance(Locale("es", "CO")).apply {
        maximumFractionDigits = 0
    }

    private val advisorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.4f
                maxOutputTokens = 1024
            },
            systemInstruction = content {
                text(
                    """
                    Eres un asesor financiero personal cercano, directo y experto en finanzas de Colombia.
                    Hablas en español natural (nada robótico ni plantillas genéricas repetitivas).

                    Reglas clave de conversación:
                    1. Responde exactamente a lo que el usuario te preguntó de forma concisa y accionable (sin introducciones largas). Si pregunta "qué puedo eliminar", analiza su lista REAL de gastos del mes y señala ítems concretos (por ejemplo ocio, suscripciones, compras no esenciales o gastos duplicados) indicando sus montos reales en COP y cuánto liberaría.
                    2. NO repitas todo el resumen de sueldo y gastos en cada mensaje salvo que sea necesario para responder.
                    3. Usa únicamente los datos reales del usuario que se adjuntan en el contexto. Nunca inventes gastos, tarjetas ni montos que no existan en su lista.
                    4. Usa viñetas cortas solo cuando ayuden a leer mejor.
                    5. Ten presente el contexto colombiano cuando aplique: pesos colombianos (COP), cuentas remuneradas (Nu, Lulo, Pibank, Ualá), CDTs, FVP (Art. 126-1 E.T.), medicina prepagada y estrategias Bola de Nieve / Avalancha para deudas.
                    """.trimIndent()
                )
            }
        )
    }

    suspend fun generateDiagnosis(
        profile: UserProfile,
        items: List<BudgetItem>,
        monthKey: String
    ): Result<String> {
        return try {
            val budgetSnapshot = buildBudgetContextSnapshot(
                salary = profile.monthlySalary,
                items = items,
                monthLabel = monthKey
            )

            val prompt = """
                $budgetSnapshot

                Con base exclusivamente en estos datos reales de mi mes ($monthKey), entrégame un diagnóstico financiero claro y personalizado con:
                1. 📊 Calificación de salud financiera (puntaje de 1 a 100 y por qué).
                2. 🔍 Análisis breve de cómo está distribuido mi sueldo (gastos esenciales, deudas, ahorro/inversión y margen libre).
                3. 🚀 Acciones concretas para este mes (qué optimizar o eliminar, cómo atacar las deudas reales que tengo registradas y qué hacer con el dinero libre).
                4. 💡 Un tip práctico tributario o de rentabilidad en Colombia adaptado a mi situación actual.
            """.trimIndent()

            val responseText = withTimeout(AI_TIMEOUT_MS) {
                advisorModel.generateContent(prompt).text
            }

            if (!responseText.isNullOrBlank()) {
                Result.success(responseText.trim())
            } else {
                Result.failure(IllegalStateException("Gemini AI devolvió una respuesta vacía."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Diagnosis generation failed", e)
            Result.failure(IllegalStateException(formatUserFacingError(e), e))
        }
    }

    fun streamChatMessage(
        userMessage: String,
        history: List<ChatMessage>,
        currentSalary: Long,
        items: List<BudgetItem>,
        monthLabel: String
    ): Flow<String> = flow {
        try {
            val budgetSnapshot = buildBudgetContextSnapshot(
                salary = currentSalary,
                items = items,
                monthLabel = monthLabel
            )

            val chatHistory = buildList {
                // Ground the conversation with the user's current live budget snapshot
                add(
                    content("user") {
                        text("Estos son mis datos financieros reales y actualizados para tener en cuenta en esta conversación:\n\n$budgetSnapshot")
                    }
                )
                add(
                    content("model") {
                        text("Entendido, tengo presentes tu sueldo, tus gastos reales detallados, deudas y ahorros de $monthLabel. ¿En qué te ayudo?")
                    }
                )

                for (msg in history.takeLast(12)) {
                    val text = msg.text.trim()
                    if (text.isBlank()) continue
                    val role = if (msg.role == "user") "user" else "model"
                    add(content(role) { text(text) })
                }
            }

            val chat = advisorModel.startChat(history = chatHistory)
            val accumulated = StringBuilder()

            withTimeout(AI_TIMEOUT_MS) {
                chat.sendMessageStream(userMessage).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }

            if (accumulated.isBlank()) {
                throw IllegalStateException("Gemini AI devolvió una respuesta vacía.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Chat streaming failed", e)
            throw IllegalStateException(formatUserFacingError(e), e)
        }
    }

    private fun buildBudgetContextSnapshot(
        salary: Long,
        items: List<BudgetItem>,
        monthLabel: String
    ): String {
        val totalExpenses = items.sumOf { it.amount }
        val totalPaid = items.filter { it.isPaid }.sumOf { it.amount }
        val totalPending = totalExpenses - totalPaid
        val freeCash = salary - totalExpenses

        val debts = items.filter { it.category == BudgetCategories.DEUDA_CREDITOS }
        val savings = items.filter {
            it.category == BudgetCategories.AHORRO_INVERSION || it.name.startsWith("Ahorro", ignoreCase = true)
        }

        val categoryBreakdown = items.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }
            .joinToString("\n") { (cat, sum) ->
                val pct = if (salary > 0) (sum.toDouble() / salary * 100) else 0.0
                "- $cat: ${copFormat.format(sum)} (${String.format(Locale.US, "%.1f", pct)}% del sueldo)"
            }

        val sortedItems = items.sortedByDescending { it.amount }
        val itemsListText = if (sortedItems.isEmpty()) {
            "No hay gastos registrados aún en este mes."
        } else {
            sortedItems.joinToString("\n") { item ->
                val paidLabel = if (item.isPaid) "PAGADO" else "PENDIENTE"
                val recurrenceLabel = if (item.isRecurring) "Mensual fijo" else "Solo este mes"
                val notesPart = item.notes?.takeIf { it.isNotBlank() }?.let { " · Nota: $it" } ?: ""
                "- ${item.name} | Categoría: ${item.category} | Monto: ${copFormat.format(item.amount)} | $paidLabel | $recurrenceLabel$notesPart"
            }
        }

        return """
            Resumen financiero del mes ($monthLabel):
            - Sueldo mensual registrado: ${copFormat.format(salary)}
            - Total gastos registrados: ${copFormat.format(totalExpenses)} (Pagado: ${copFormat.format(totalPaid)}, Pendiente: ${copFormat.format(totalPending)})
            - Dinero libre disponible: ${copFormat.format(freeCash)}
            - Total en Deuda y créditos: ${copFormat.format(debts.sumOf { it.amount })}
            - Total en Ahorro e inversión: ${copFormat.format(savings.sumOf { it.amount })}

            Totales por categoría:
            $categoryBreakdown

            Lista completa de ítems del mes (de mayor a menor valor):
            $itemsListText
        """.trimIndent()
    }

    private fun formatUserFacingError(e: Throwable): String {
        val msg = e.message.orEmpty()
        val lower = msg.lowercase(Locale.ROOT)
        return when {
            e is TimeoutCancellationException ->
                "La consulta tardó demasiado (más de 60s). Verifica tu conexión e intenta de nuevo."
            lower.contains("app check") || lower.contains("appcheck") ||
                lower.contains("attestation") || lower.contains("403") ->
                "Error de verificación App Check con Firebase AI ($msg). Si instalaste un APK Debug directo en el celular, usa la compilación Release o ejecútalo desde AI Studio."
            e is UnknownHostException || e is ConnectException ||
                lower.contains("unable to resolve host") || lower.contains("network") ->
                "Sin conexión a internet para consultar a Gemini AI. Revisa tu red e intenta nuevamente."
            lower.contains("429") || lower.contains("quota") || lower.contains("resource_exhausted") ->
                "Límite temporal de solicitudes a Gemini AI alcanzado. Espera unos segundos e intenta de nuevo."
            else ->
                e.localizedMessage ?: "No se pudo obtener respuesta de Gemini AI."
        }
    }
}
