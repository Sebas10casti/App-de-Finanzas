package com.example.ui.components

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun parseMarkdown_boldAsterisks_stripsMarkersAndAppliesBoldSpan() {
        val annotated = parseMarkdownToAnnotatedString("Aplica el **Método Bola de Nieve** hoy.")

        assertEquals("Aplica el Método Bola de Nieve hoy.", annotated.text)
        assertFalse(annotated.text.contains("**"))

        val boldSpan = annotated.spanStyles.firstOrNull {
            it.item.fontWeight == FontWeight.Bold
        }
        assertTrue(boldSpan != null)
        assertEquals(
            "Método Bola de Nieve",
            annotated.text.substring(boldSpan!!.start, boldSpan.end)
        )
    }

    @Test
    fun parseMarkdown_boldItalicAndSingleItalic_appliesStylesWithoutLiteralAsterisks() {
        val annotated = parseMarkdownToAnnotatedString("Usa ***prioridad alta*** y *disciplina*.")

        assertEquals("Usa prioridad alta y disciplina.", annotated.text)
        assertTrue(
            annotated.spanStyles.any {
                it.item.fontWeight == FontWeight.Bold &&
                    it.item.fontStyle == FontStyle.Italic &&
                    annotated.text.substring(it.start, it.end) == "prioridad alta"
            }
        )
        assertTrue(
            annotated.spanStyles.any {
                it.item.fontStyle == FontStyle.Italic &&
                    annotated.text.substring(it.start, it.end) == "disciplina"
            }
        )
    }

    @Test
    fun parseMarkdown_bulletsAndHeadings_formatsCleanly() {
        val input = """
            ## Plan del Mes
            -   **50%:** Abono a capital
            * **30%:** Fondo de emergencia
            Nota: 2 * 3 * 4 no es cursiva
        """.trimIndent()

        val annotated = parseMarkdownToAnnotatedString(input)

        val expectedText = """
            Plan del Mes
            • 50%: Abono a capital
            • 30%: Fondo de emergencia
            Nota: 2 * 3 * 4 no es cursiva
        """.trimIndent()

        assertEquals(expectedText, annotated.text)
        assertTrue(
            annotated.spanStyles.any {
                it.item.fontWeight == FontWeight.Bold &&
                    annotated.text.substring(it.start, it.end) == "50%:"
            }
        )
        assertTrue(
            annotated.spanStyles.any {
                it.item.fontWeight == FontWeight.Bold &&
                    annotated.text.substring(it.start, it.end) == "30%:"
            }
        )
    }
}
