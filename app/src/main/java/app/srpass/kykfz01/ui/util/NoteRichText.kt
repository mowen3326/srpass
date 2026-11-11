package app.srpass.kykfz01.ui.util

import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.srpass.kykfz01.vault.Span
import app.srpass.kykfz01.vault.SysFont

/**
 * 把存储的字体族 key 映射成 Compose 的 [FontFamily]。
 *
 * 走 Android 的**通用字体族名**（`Typeface.create`），不打包任何字体文件，
 * APK 零增量。系统不认识的族名不会抛异常、会自动回退到默认字体，所以这里
 * 的 runCatching 只是兜底（个别定制 ROM 会在 create 里抛 NPE）。
 *
 * 注意：中文场景下「衬线 / 等宽 / 手写」的实际观感取决于设备是否装了对应的
 * 中文字形，很多机器会回退到同一套黑体——这是系统字体的正常行为，不是 bug。
 */
fun sysFontFamily(key: String?): FontFamily? {
    val font = SysFont.of(key)
    if (font == SysFont.DEFAULT) return null
    return runCatching { FontFamily(Typeface.create(font.familyName, Typeface.NORMAL)) }
        .getOrNull()
}

/** 标题级别 → 字号（sp）。标题一律加粗，好和正文一眼分开。 */
private fun headingSize(level: Int): Int = when (level) {
    1 -> 24
    2 -> 20
    else -> 18
}

/**
 * 把"纯文本 + span 列表"渲染成 [AnnotatedString]。渲染产物，不落库。
 *
 * [level] 是段落级标题（0 = 正文）：标题样式先铺满整块，**再**叠加用户自己
 * 加的 span，所以"标题里挑两个字标红"这类局部样式能盖住标题底色，不会互相打架。
 */
fun spansToAnnotated(text: String, spans: List<Span>, level: Int = 0): AnnotatedString =
    buildAnnotatedString {
        append(text)
        if (level > 0 && text.isNotEmpty()) {
            addStyle(
                spanStyleOf(Span(0, text.length, bold = true, size = headingSize(level))),
                0,
                text.length
            )
        }
        for (s in spans) {
            val a = s.start.coerceIn(0, text.length)
            val b = s.end.coerceIn(a, text.length)
            if (b <= a) continue
            addStyle(spanStyleOf(s), a, b)
        }
    }

fun spanStyleOf(s: Span): SpanStyle = SpanStyle(
    fontWeight = if (s.bold) FontWeight.Bold else null,
    fontStyle = if (s.italic) FontStyle.Italic else null,
    textDecoration = when {
        s.underline && s.strike -> TextDecoration.combine(
            listOf(TextDecoration.Underline, TextDecoration.LineThrough)
        )
        s.underline -> TextDecoration.Underline
        s.strike -> TextDecoration.LineThrough
        else -> null
    },
    fontSize = s.size?.sp ?: TextUnit.Unspecified,
    color = s.color?.let { Color(it) } ?: Color.Unspecified,
    background = s.bg?.let { Color(it) } ?: Color.Unspecified,
    fontFamily = sysFontFamily(s.font)
)
