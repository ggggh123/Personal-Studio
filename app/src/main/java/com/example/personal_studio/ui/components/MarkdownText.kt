package com.example.personal_studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Hull
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule

/**
 * 极小 markdown 渲染器,够覆盖 AI 流式输出常用元素:
 *   # / ## / ### 标题(行首)
 *   - / * 无序列表
 *   1. 2. ... 有序列表
 *   > 引用块
 *   `code`、**bold**(或 __bold__)、*italic*(或 _italic_)
 *   空行 → 段间距
 *
 * 流式安全:未闭合的 ** 或 ` 等会优雅退化为字面字符,不会崩溃也不会"吞掉"后续内容。
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    bodyColor: Color = Foam,
    headerColor: Color = Phosphor,
    accentColor: Color = FoamMute,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val lines = text.split('\n')
        var pendingPara = ""
        @Composable
        fun flushPara() {
            if (pendingPara.isNotBlank()) {
                Text(
                    parseInline(pendingPara.trimEnd()),
                    color = bodyColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            pendingPara = ""
        }

        for (raw in lines) {
            val line = raw.trimStart()
            when {
                line.startsWith("### ") -> {
                    flushPara()
                    Text(
                        line.removePrefix("### "), color = headerColor,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                line.startsWith("## ") -> {
                    flushPara()
                    Text(
                        line.removePrefix("## "), color = headerColor,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                }
                line.startsWith("# ") -> {
                    flushPara()
                    Text(
                        line.removePrefix("# "), color = headerColor,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    flushPara()
                    BulletRow(
                        marker = "• ", content = parseInline(line.drop(2)),
                        bodyColor = bodyColor, accentColor = accentColor,
                    )
                }
                ORDERED.matchEntire(line) != null -> {
                    flushPara()
                    val m = ORDERED.matchEntire(line)!!
                    BulletRow(
                        marker = "${m.groupValues[1]}. ",
                        content = parseInline(m.groupValues[2]),
                        bodyColor = bodyColor, accentColor = accentColor,
                    )
                }
                line.startsWith("> ") -> {
                    flushPara()
                    Row {
                        Box(Modifier.width(2.dp).height(20.dp).background(Rule))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            parseInline(line.removePrefix("> ")),
                            color = accentColor, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                line.isBlank() -> {
                    flushPara()
                    Spacer(Modifier.height(4.dp))
                }
                else -> {
                    if (pendingPara.isNotEmpty()) pendingPara += "\n"
                    pendingPara += raw
                }
            }
        }
        flushPara()
    }
}

@Composable
private fun BulletRow(
    marker: String,
    content: AnnotatedString,
    bodyColor: Color,
    accentColor: Color,
) {
    Row {
        Text(marker, color = accentColor, style = MaterialTheme.typography.bodyMedium)
        Text(content, color = bodyColor, style = MaterialTheme.typography.bodyMedium)
    }
}

private val ORDERED = Regex("""^(\d+)\.\s+(.+)$""")

private fun parseInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val n = text.length
    while (i < n) {
        val rest = text.substring(i)
        when {
            rest.startsWith("**") -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            rest.startsWith("__") -> {
                val end = text.indexOf("__", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            rest.startsWith("*") -> {  // 已先匹配 ** ,这里只可能是单 *
                val end = text.indexOf("*", i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            rest.startsWith("_") -> {
                val end = text.indexOf("_", i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            rest.startsWith("`") -> {
                val end = text.indexOf("`", i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(
                        background = Hull,
                        color = Phosphor,
                        fontFamily = FontFamily.Monospace,
                    )) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}
