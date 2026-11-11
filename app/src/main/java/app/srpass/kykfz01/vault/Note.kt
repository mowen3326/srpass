package app.srpass.kykfz01.vault

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TYPE_TEXT = "text"
private const val TYPE_IMAGE = "image"
private const val TYPE_AUDIO = "audio"
private const val TYPE_DRAWING = "drawing"

/** 序列化格式版本。v0 是裸数组（旧笔记），v1 是 {"v":1,"blocks":[…]}。 */
private const val BLOCKS_VERSION = 1

/**
 * A note is a list of [Block]s. Stored as a KDBX entry in a dedicated "Notes"
 * group; the block list is serialized to JSON in a custom field, and media
 * (images / audio / drawings) are kept encrypted inside the KDBX binary pool,
 * referenced by [BinaryBlock.ref].
 *
 * [updatedAt] is an epoch-millis instant (UTC); it is persisted in a custom
 * KDBX field and formatted for display using the device's local time zone.
 */
sealed interface Block {
    val type: String

    /**
     * Stable identity for one block.
     *
     * The editor keys its `LazyColumn` items and its focus target on this, so
     * it must never change while the note is being edited -- using the list
     * index instead would make every insert reshuffle the keys and throw the
     * caret into the wrong field.
     */
    val id: String
}

/**
 * 引用二进制池里一份内容的块。
 *
 * **新增任何引用二进制的块类型都必须实现这个接口**：[VaultRepository] 靠它
 * 统计"还有人在用"的二进制，漏掉一种，下一次保存的孤儿回收就会把这种内容
 * 永久删掉。
 */
sealed interface BinaryBlock : Block {
    val ref: String

    /** 写进 KDBX BinaryReference 的名字，仅作标识用。 */
    val binaryName: String
}

data class TextBlock(
    val text: String,
    val spans: List<Span> = emptyList(),
    val para: Para = Para(),
    override val id: String = newBlockId()
) : Block {
    override val type: String get() = TYPE_TEXT
}

data class ImageBlock(
    override val ref: String,
    val mime: String,
    val caption: String = "",
    override val id: String = newBlockId()
) : Block, BinaryBlock {
    override val type: String get() = TYPE_IMAGE
    override val binaryName: String get() = "image"
}

/** 一段录音。音频以 m4a 存在二进制池里，[durationMs] 用于列表/详情页显示时长。 */
data class AudioBlock(
    override val ref: String,
    val durationMs: Long = 0L,
    val title: String = "",
    override val id: String = newBlockId()
) : Block, BinaryBlock {
    override val type: String get() = TYPE_AUDIO
    override val binaryName: String get() = "audio"
}

/**
 * 一张涂鸦。
 *
 * [ref] 是导出好的 PNG（列表/详情直接当图片看、也走原来的图片查看器）；
 * [strokes] 是笔画矢量（[DrawingDoc] 的 JSON），有它才能"重新编辑"。两者
 * 都存，互为冗余：PNG 保证任何设备都能看，矢量保证能改。
 */
data class DrawingBlock(
    override val ref: String,
    val strokes: String? = null,
    override val id: String = newBlockId()
) : Block, BinaryBlock {
    override val type: String get() = TYPE_DRAWING
    override val binaryName: String get() = "drawing"
}

fun newBlockId(): String = UUID.randomUUID().toString()

/**
 * One note: title, blocks and bookkeeping times.
 *
 * [updatedAt] is an epoch-millis instant (UTC); it is persisted in a custom
 * KDBX field and formatted for display using the device's local time zone.
 *
 * [hidden] puts the note in the private "hidden space": it drops out of every
 * public list and search, and opening that space costs another credential
 * check. The flag lives in a KDBX custom field, so it travels with the note --
 * a backup/restore round-trip leaves it hidden instead of exposing it.
 */
data class Note(
    val uuid: UUID,
    val title: String = "",
    val updatedAt: Long = 0L,
    val blocks: List<Block> = emptyList(),
    /**
     * 读不出来但必须原样带回去的 Content 原文，平时为 null。
     *
     * 只有 [jsonToBlocks] 解析失败时才会有值（例如这条笔记被别的 KeePass
     * 客户端改成了纯文本）。带着它，随便一次「打开列表再保存」都不会把读不
     * 懂的内容抹掉；为空时照常写 blocks 序列化后的 JSON。
     */
    val rawContent: String? = null,
    val hidden: Boolean = false
) {
    /**
     * 列表里显示的一段摘要。
     *
     * 只有文本块有字；纯音频/涂鸦的笔记会拼出占位符，否则列表上是一条看不
     * 出内容的空卡片。
     */
    fun preview(): String {
        val sb = StringBuilder()
        for (b in blocks) {
            when (b) {
                is TextBlock -> if (b.text.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append(b.text)
                }
                is ImageBlock -> sb.appendPlaceholder("图片")
                is AudioBlock -> sb.appendPlaceholder("音频")
                is DrawingBlock -> sb.appendPlaceholder("涂鸦")
            }
        }
        val text = sb.toString().trim()
        return if (text.length > 120) text.take(120) + "…" else text
    }

    /**
     * 正文字数：所有文本块字符数之和（不含标题、不含图片/音频/涂鸦占位符）。
     */
    fun wordCount(): Int = blocks.sumOf { (it as? TextBlock)?.text?.length ?: 0 }

    private fun StringBuilder.appendPlaceholder(word: String) {
        if (isNotEmpty()) append(' ')
        append('「').append(word).append('」')
    }

    /**
     * 列表卡片要不要显示"带图"标记。
     *
     * 涂鸦也算：它存的就是一张 PNG，列表/详情里和图片走同一条展示路径。
     */
    fun hasImage(): Boolean = blocks.any { it is ImageBlock || it is DrawingBlock }

    fun hasMedia(): Boolean = blocks.any { it is BinaryBlock }
}

fun blocksToJson(blocks: List<Block>): String {
    val arr = JSONArray()
    for (b in blocks) {
        val o = JSONObject()
        o.put("id", b.id)
        when (b) {
            is TextBlock -> {
                o.put("t", TYPE_TEXT)
                o.put("text", b.text)
                if (b.spans.isNotEmpty()) o.put("spans", spansToJson(b.spans))
                if (!b.para.isBlank()) o.put("para", paraToJson(b.para))
            }
            is ImageBlock -> {
                o.put("t", TYPE_IMAGE)
                o.put("ref", b.ref)
                o.put("mime", b.mime)
                o.put("caption", b.caption)
            }
            is AudioBlock -> {
                o.put("t", TYPE_AUDIO)
                o.put("ref", b.ref)
                o.put("duration", b.durationMs)
                o.put("title", b.title)
            }
            is DrawingBlock -> {
                o.put("t", TYPE_DRAWING)
                o.put("ref", b.ref)
                if (!b.strokes.isNullOrEmpty()) o.put("strokes", b.strokes)
            }
        }
        arr.put(o)
    }
    return JSONObject().put("v", BLOCKS_VERSION).put("blocks", arr).toString()
}

/**
 * 解析 blocks JSON；返回 null 表示这段内容不是我们写的格式。
 *
 * 以前这里吞掉异常、返回"已解析出来的前半截"，调用方分不清"笔记本来就这些
 * 块"和"解析挂了"，于是打开再保存一次就把后半截连同它引用的图片一起永久
 * 写没了。现在解析不了就明确报 null，由调用方把原文带回 [Note.rawContent]。
 *
 * 旧笔记（v0，裸数组）照旧能读：没有 id / spans 就补默认值，老版本 App 读
 * 新笔记也只是丢样式，绝不能因此报 null —— 那会把整条笔记冻结住。
 */
fun jsonToBlocks(json: String): List<Block>? {
    return runCatching {
        val trimmed = json.trim()
        val arr: JSONArray = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            JSONObject(trimmed).optJSONArray("blocks") ?: return@runCatching null
        }
        val list = ArrayList<Block>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: newBlockId()
            when (o.optString("t")) {
                TYPE_TEXT -> list.add(
                    TextBlock(
                        text = o.optString("text"),
                        spans = jsonToSpans(o.optJSONArray("spans")),
                        para = jsonToPara(o.optJSONObject("para")),
                        id = id
                    )
                )
                TYPE_IMAGE -> list.add(
                    ImageBlock(
                        ref = o.optString("ref"),
                        mime = o.optString("mime", "image/*"),
                        caption = o.optString("caption"),
                        id = id
                    )
                )
                TYPE_AUDIO -> list.add(
                    AudioBlock(
                        ref = o.optString("ref"),
                        durationMs = o.optLong("duration", 0L),
                        title = o.optString("title"),
                        id = id
                    )
                )
                TYPE_DRAWING -> list.add(
                    DrawingBlock(
                        ref = o.optString("ref"),
                        strokes = o.optString("strokes").takeIf { it.isNotBlank() },
                        id = id
                    )
                )
            }
        }
        list
    }.getOrNull()
}

// ---------------------------------------------------------------------------
// Span / Para 的 JSON
// ---------------------------------------------------------------------------

private fun spansToJson(spans: List<Span>): JSONArray {
    val arr = JSONArray()
    for (s in spans) {
        val o = JSONObject()
        o.put("s", s.start)
        o.put("e", s.end)
        if (s.bold) o.put("b", 1)
        if (s.italic) o.put("i", 1)
        if (s.underline) o.put("u", 1)
        if (s.strike) o.put("k", 1)
        s.size?.let { o.put("z", it) }
        s.color?.let { o.put("c", it) }
        s.bg?.let { o.put("g", it) }
        s.font?.let { o.put("f", it) }
        arr.put(o)
    }
    return arr
}

private fun jsonToSpans(arr: JSONArray?): List<Span> {
    if (arr == null) return emptyList()
    val out = ArrayList<Span>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        // 颜色字段用 opt 而非 get：老数据可能写成 null，getLong 会抛，整条
        // 笔记就被当成"解析失败"冻结了。
        out += Span(
            start = o.optInt("s"),
            end = o.optInt("e"),
            bold = o.optInt("b") == 1,
            italic = o.optInt("i") == 1,
            underline = o.optInt("u") == 1,
            strike = o.optInt("k") == 1,
            size = if (o.has("z")) o.optInt("z") else null,
            color = if (o.has("c")) o.optLong("c") else null,
            bg = if (o.has("g")) o.optLong("g") else null,
            font = o.optString("f").takeIf { it.isNotBlank() }
        )
    }
    return out
}

private fun paraToJson(p: Para): JSONObject =
    JSONObject().put("l", p.level).put("a", p.align).put("s", p.list)

private fun jsonToPara(o: JSONObject?): Para {
    if (o == null) return Para()
    return Para(level = o.optInt("l"), align = o.optInt("a"), list = o.optInt("s"))
}

/**
 * Formats an instant for display using the device's active time zone, always as
 * the full "yyyy年M月d日 HH:mm:ss" so the year and seconds are visible.
 */
fun formatNoteTime(ts: Long): String {
    if (ts <= 0L) return ""
    val fmt = SimpleDateFormat("yyyy年M月d日 HH:mm:ss", Locale.getDefault())
    return fmt.format(Date(ts))
}

/** 格式化一段录音时长：不足一小时显示 mm:ss，否则 h:mm:ss。 */
fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%02d:%02d", m, s)
}
