package app.srpass.kykfz01.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Typeface
import android.text.Html
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.widget.Toast
import app.srpass.kykfz01.vault.Span
import app.srpass.kykfz01.vault.normalizeSpans

fun copyToClipboard(context: Context, label: String, text: String, clearAfterMs: Long = 60_000L) {
    // applicationContext throughout: the pending clear outlives the screen that
    // started it, and holding that Activity for 60s would leak it.
    val appContext = context.applicationContext
    val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(appContext, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    // Auto-clear the clipboard a short while later so a copied password does not
    // linger for other apps to read. Only clear if the user has not copied
    // something else in the meantime.
    if (clearAfterMs > 0) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.postDelayed({
            if (cm.primaryClip?.getItemAt(0)?.text?.toString() == text) {
                cm.setPrimaryClip(ClipData.newPlainText(label, ""))
            }
        }, clearAfterMs)
    }
}

// ---- 带图片的粘贴 -------------------------------------------------------

/** 粘贴进来的一段：要么是一段文字，要么是一张图片。 */
sealed interface PasteSegment {
    /** [text] 已转成纯文本，[spans] 是从 HTML 的 b/i/u/s 还原出来的样式。 */
    data class Text(val text: String, val spans: List<Span>) : PasteSegment

    /**
     * 图片来源，原样保留交给入库那一步去认：
     * `data:image/...;base64,xxx`（内嵌）、`content://…`、`file://…`。
     */
    data class Image(val src: String) : PasteSegment
}

/** 剪贴板里一个 item 的三种可能内容，取到哪个用哪个。 */
private class RawClipItem(
    val imageUri: String?,
    val html: String?,
    val text: String?
)

private val IMG_TAG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val IMG_SRC =
    Regex("""src\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)

/**
 * 把剪贴板内容按原文顺序拆成"文字 / 图片 / 文字…"。
 *
 * 返回 `null` 表示**剪贴板里没有我们能处理的图片** —— 这时调用方应当交回系统
 * 默认的文本粘贴，否则同一段文字会被插入两次。
 *
 * 只认本地来源：http(s) 的图片一律忽略（外链图片离开原 App 就会失效，
 * 图片必须完整存进库里）。
 */
fun readPasteSegments(context: Context): List<PasteSegment>? {
    val cm = context.applicationContext
        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cm.primaryClip ?: return null
    val desc = clip.description
    // ClipDescription.hasMimeType("image/*") 在很多版本上是**精确匹配**，源 App
    // 声明的 image/png 根本匹配不上，所以自己遍历一遍按前缀判断。
    val mimes = ArrayList<String>()
    for (i in 0 until desc.mimeTypeCount) mimes += desc.getMimeType(i)
    val hasImageMime = mimes.any { it.startsWith("image/", ignoreCase = true) }
    val cr = context.applicationContext.contentResolver

    // 逐 item 收集，**顺序就是原文顺序**。小米笔记这类 App 不给 HTML，而是把
    // 「文字 / 图片 / 文字」拆成好几个 ClipData item —— 顺序信息只存在于 item
    // 序列里，早先只取第一段文字，图片后面的内容就全丢了。
    val items = ArrayList<RawClipItem>()
    for (i in 0 until clip.itemCount) {
        val item = clip.getItemAt(i)
        // 有些 App 不把图放在 item.uri 上，而是塞在 item.intent 的 EXTRA_STREAM
        // 里（分享/复制走的是同一套），这条也得接。
        val uri = runCatching { item.uri }.getOrNull()
            ?: runCatching {
                // 带 Class 参数的重载（API 33+，minSdk 34 够用）：类型在这里就定
                // 死，不用再 as?，也不会拿到不是 Uri 的东西。
                item.intent?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            }.getOrNull()
        var imageUri: String? = null
        if (uri != null) {
            // 直接问 ContentResolver 这个 Uri 是什么类型，比看 ClipDescription 准
            // —— 源 App 声明的 MIME 常常跟实际内容对不上。
            val ty = runCatching { cr.getType(uri) }.getOrNull()
            if (ty?.startsWith("image/", ignoreCase = true) == true || hasImageMime || mimes.isEmpty()) {
                imageUri = uri.toString()
            }
        }
        val h = runCatching { item.htmlText }.getOrNull()?.takeIf { it.isNotBlank() }
        val t = runCatching { item.text?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }
        items += RawClipItem(imageUri, h, t)
    }

    val source = items.mapNotNull { it.html }.firstOrNull()
        ?: items.mapNotNull { it.text }.firstOrNull { it.contains("<img", ignoreCase = true) }
    if (source == null) {
        // 没有 HTML：按 item 顺序把「文字 / 图片 / 文字…」原样还原。一张图都没有
        // 才交回系统默认文本粘贴。
        val out = ArrayList<PasteSegment>()
        for (it in items) {
            when {
                it.imageUri != null -> out += PasteSegment.Image(it.imageUri)
                it.text != null -> out += PasteSegment.Text(it.text, emptyList())
            }
        }
        if (out.none { it is PasteSegment.Image }) return null
        return out
    }

    val out = ArrayList<PasteSegment>()
    var last = 0
    for (m in IMG_TAG.findAll(source)) {
        addTextSegment(out, source.substring(last, m.range.first))
        val g = IMG_SRC.find(m.value)?.groupValues
        val src = g?.let { it[1].takeIf { s -> s.isNotEmpty() } ?: it[2].takeIf { s -> s.isNotEmpty() } ?: it[3] }
        if (!src.isNullOrBlank() && isLocalImageSrc(src)) {
            out += PasteSegment.Image(decodeEntities(src))
        }
        last = (m.range.last + 1).coerceAtMost(source.length)
    }
    addTextSegment(out, source.substring(last))

    // HTML 里一张图都没有：还是交给系统按纯文本粘。
    if (out.none { it is PasteSegment.Image }) return null
    return out
}

/**
 * 粘完一次就把剪贴板清掉。
 *
 * 内容已经进库了，剪贴板再留一份只会让"剪贴板中有内容"那条提示反复弹出来 ——
 * 每次回到编辑页都被提醒一次。
 */
fun clearClipboard(context: Context) {
    val cm = context.applicationContext
        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    runCatching { cm.clearPrimaryClip() }
}

/**
 * 轻量探测：剪贴板里现在有没有能拿到的图片。
 *
 * 底栏那个"粘贴图片"按钮靠它点亮 —— 输入法的粘贴键绕过了 Compose 的粘贴回调，
 * 光靠事件拦截接不住，得有一个随时可点的显式入口。
 */
fun clipboardHasImage(context: Context): Boolean =
    readPasteSegments(context)?.any { it is PasteSegment.Image } ?: false

private fun isLocalImageSrc(src: String): Boolean {
    val s = src.trim()
    if (s.startsWith("data:image", ignoreCase = true)) return true
    if (s.startsWith("content://", ignoreCase = true)) return true
    if (s.startsWith("file://", ignoreCase = true)) return true
    if (s.startsWith("http://", ignoreCase = true)) return false
    if (s.startsWith("https://", ignoreCase = true)) return false
    // 裸路径（相对路径之类）当成本地处理，读不到会在入库那一步报错。
    return !s.contains("://")
}

private fun decodeEntities(src: String): String =
    runCatching { Html.fromHtml(src, Html.FROM_HTML_MODE_COMPACT).toString() }.getOrElse { src }

private fun addTextSegment(out: ArrayList<PasteSegment>, html: String) {
    if (html.isBlank()) return
    val spanned: Spanned =
        runCatching { Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT) }.getOrNull() ?: return
    // 块级标签会留下尾随换行，那是排版而不是内容；行内的换行要保留。
    val text = spanned.toString().trim('\n', '\r')
    if (text.isEmpty()) return
    out += PasteSegment.Text(text, spansFromHtml(spanned, text.length))
}

private fun spansFromHtml(spanned: Spanned, length: Int): List<Span> {
    val out = ArrayList<Span>()
    for (s in spanned.getSpans(0, spanned.length, CharacterStyle::class.java)) {
        val start = spanned.getSpanStart(s)
        val end = spanned.getSpanEnd(s)
        if (end <= start) continue
        val base = Span(start, end)
        out += when (s) {
            is StyleSpan -> when (s.style) {
                Typeface.BOLD -> base.copy(bold = true)
                Typeface.ITALIC -> base.copy(italic = true)
                Typeface.BOLD_ITALIC -> base.copy(bold = true, italic = true)
                else -> base
            }
            is UnderlineSpan -> base.copy(underline = true)
            is StrikethroughSpan -> base.copy(strike = true)
            else -> base
        }
    }
    return normalizeSpans(length, out)
}
