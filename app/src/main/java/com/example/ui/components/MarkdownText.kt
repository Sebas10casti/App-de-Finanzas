package com.example.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current
) {
    val annotated = remember(text, style.fontSize) {
        parseMarkdownToAnnotatedString(text, style)
    }
    Text(
        text = annotated,
        modifier = modifier,
        color = color,
        style = style
    )
}

fun parseMarkdownToAnnotatedString(
    markdown: String,
    baseStyle: TextStyle = TextStyle.Default
): AnnotatedString {
    val lines = markdown.split("\n")
    val headingFontSize = if (baseStyle.fontSize.isSpecified) {
        (baseStyle.fontSize.value + 1.5f).sp
    } else {
        16.sp
    }

    return buildAnnotatedString {
        lines.forEachIndexed { index, rawLine ->
            val trimmedStart = rawLine.trimStart()
            when {
                trimmedStart.startsWith("### ") || trimmedStart.startsWith("## ") || trimmedStart.startsWith("# ") -> {
                    val content = trimmedStart.dropWhile { it == '#' }.trimStart()
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = headingFontSize))
                    appendInlineMarkdown(this, content)
                    pop()
                }
                trimmedStart.startsWith("- ") || trimmedStart.startsWith("* ") -> {
                    val indent = rawLine.takeWhile { it == ' ' }
                    val content = trimmedStart.drop(2).trimStart()
                    append(indent)
                    append("• ")
                    appendInlineMarkdown(this, content)
                }
                else -> {
                    appendInlineMarkdown(this, rawLine)
                }
            }
            if (index < lines.lastIndex) {
                append("\n")
            }
        }
    }
}

private fun appendInlineMarkdown(
    builder: AnnotatedString.Builder,
    text: String
) {
    var i = 0
    while (i < text.length) {
        if (text.startsWith("***", i)) {
            val end = text.indexOf("***", i + 3)
            if (end != -1 && end > i + 3) {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                appendInlineMarkdown(builder, text.substring(i + 3, end))
                builder.pop()
                i = end + 3
                continue
            }
        }
        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end != -1 && end > i + 2) {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                appendInlineMarkdown(builder, text.substring(i + 2, end))
                builder.pop()
                i = end + 2
                continue
            }
        }
        if (text.startsWith("__", i)) {
            val end = text.indexOf("__", i + 2)
            if (end != -1 && end > i + 2) {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                appendInlineMarkdown(builder, text.substring(i + 2, end))
                builder.pop()
                i = end + 2
                continue
            }
        }
        if (text[i] == '*' && i + 1 < text.length && !text[i + 1].isWhitespace()) {
            val end = findClosingSingleAsterisk(text, i + 1)
            if (end != -1 && end > i + 1) {
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                builder.append(text.substring(i + 1, end))
                builder.pop()
                i = end + 1
                continue
            }
        }
        builder.append(text[i])
        i++
    }
}

private fun findClosingSingleAsterisk(text: String, startIndex: Int): Int {
    var idx = startIndex
    while (idx < text.length) {
        if (text[idx] == '*') {
            val prevIsSpace = text[idx - 1].isWhitespace()
            val nextIsAsterisk = idx + 1 < text.length && text[idx + 1] == '*'
            if (!prevIsSpace && !nextIsAsterisk) {
                return idx
            }
            if (nextIsAsterisk) {
                idx += 2
                continue
            }
        }
        idx++
    }
    return -1
}
