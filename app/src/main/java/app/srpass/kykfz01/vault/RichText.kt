package app.srpass.kykfz01.vault

/**
 * 富文本样式：一个 [Span] 覆盖文本的 [start, end) 区间，携带这段的属性集合。
 *
 * 这里只存"逻辑值"（布尔 / sp / 0xAARRGGBB / 字体族 key），不存 Compose 的
 * `SpanStyle` 或 Android 的 `Typeface`：那两者都不可序列化，一旦写进 KDBX，
 * 换设备或升级依赖后就废了。字体族存的是 [SysFont.key] 这样的短字符串，
 * 渲染时再映射回系统字体。
 */
data class Span(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    /** 字号（sp）；null = 跟随正文字号。 */
    val size: Int? = null,
    /** 文字颜色，0xAARRGGBB；null = 跟随主题。 */
    val color: Long? = null,
    /** 高亮背景色，0xAARRGGBB；null = 无高亮。 */
    val bg: Long? = null,
    /** [SysFont.key]；null = 默认字体。 */
    val font: String? = null
) {
    /** 一个属性都没有：这种 span 不值得存，normalize 时会丢掉。 */
    fun isBlank(): Boolean =
        !bold && !italic && !underline && !strike &&
            size == null && color == null && bg == null && font == null

    /** 属性是否完全相同（用于合并相邻段）。 */
    fun sameAttrsAs(other: Span): Boolean =
        bold == other.bold && italic == other.italic &&
            underline == other.underline && strike == other.strike &&
            size == other.size && color == other.color &&
            bg == other.bg && font == other.font
}

/** 段落级样式：标题级别 / 对齐 / 列表。字段先就位，UI 后续再开。 */
data class Para(
    /** 0 = 正文，1..3 = 标题级别。 */
    val level: Int = 0,
    /** 0 = 左，1 = 居中，2 = 右。 */
    val align: Int = 0,
    /** 0 = 无，1 = 无序列表，2 = 有序列表，3 = 引用。 */
    val list: Int = 0
) {
    fun isBlank(): Boolean = level == 0 && align == 0 && list == 0
}

/**
 * 系统通用字体族。
 *
 * 走 Android 的通用族名（`Typeface.create(name, NORMAL)`），不打包任何字体
 * 文件，APK 零增量；映射在 UI 层完成（vault 层保持不依赖 Android）。
 * 传入系统不认识的族名不会抛异常，会回退到默认字体。
 *
 * key 是**落库值**，一旦发布就不能再改（旧笔记的 span 里存的是它）；要加
 * 字体只能往后追加新枚举，[of] 对不认识的 key 一律回退到 [DEFAULT]。
 */
enum class SysFont(val key: String, val familyName: String) {
    DEFAULT("default", "sans-serif"),
    THIN("thin", "sans-serif-thin"),
    LIGHT("light", "sans-serif-light"),
    MEDIUM("medium", "sans-serif-medium"),
    BLACK("black", "sans-serif-black"),
    CONDENSED("condensed", "sans-serif-condensed"),
    CONDENSED_MEDIUM("condensed_medium", "sans-serif-condensed-medium"),
    SMALLCAPS("smallcaps", "sans-serif-smallcaps"),
    SERIF("serif", "serif"),
    SERIF_MONO("serif_mono", "serif-monospace"),
    MONO("mono", "monospace"),
    CASUAL("casual", "casual"),
    CURSIVE("cursive", "cursive");

    companion object {
        fun of(key: String?): SysFont = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

// ---------------------------------------------------------------------------
// span 集合的规范化 / 编辑
// ---------------------------------------------------------------------------

/**
 * 夹到文本长度内、丢掉空 span、并按顺序合并属性相同的相邻段。
 *
 * 所有进出存储的 span 都过一遍这个函数，[toggleSpanAttr] 的分段算法才能
 * 依赖"互不重叠、按 start 升序"这个不变式。
 */
fun normalizeSpans(textLength: Int, spans: List<Span>): List<Span> {
    if (spans.isEmpty()) return emptyList()
    val sorted = if (spans.size > 1) spans.sortedBy { it.start } else spans
    val out = ArrayList<Span>(sorted.size)
    for (s in sorted) {
        val a = s.start.coerceIn(0, textLength)
        val b = s.end.coerceIn(a, textLength)
        if (b <= a || s.isBlank()) continue
        val last = out.lastOrNull()
        if (last != null && last.end == a && last.sameAttrsAs(s)) {
            out[out.size - 1] = last.copy(end = b)
        } else {
            out.add(s.copy(start = a, end = b))
        }
    }
    return out
}

/**
 * 文本被编辑后，把 span 区间跟着平移。
 *
 * 刻意不依赖 Compose 内部的 `EditingBuffer` 行为：这里用"公共前缀 / 后缀"
 * 求出真正的替换区间 `[oldStart, oldEnd) → 新文本`，然后按 delta 平移，
 * 落在替换区间内的部分塌缩到边界（长度归零的 span 直接丢掉）。
 *
 * 一个例外是**紧贴编辑点的结尾**：在粗体段末尾继续打字，新输入的字符应当
 * 继续保持粗体（这也是各家系统笔记的行为），所以 `end == oldStart` 且是插入
 * 时把结尾延伸到 `newEnd`。
 */
fun shiftSpans(oldText: String, newText: String, spans: List<Span>): List<Span> {
    if (oldText == newText || spans.isEmpty()) return spans
    val oldStart = commonPrefixLength(oldText, newText)
    val suffix = commonSuffixLength(oldText, newText, oldStart)
    val oldEnd = oldText.length - suffix
    val newEnd = newText.length - suffix
    val delta = newText.length - oldText.length

    val out = ArrayList<Span>(spans.size)
    for (s in spans) {
        // 边界正好落在编辑点上时的归属（这是唯一有歧义的地方，取各家笔记的
        // 通行做法）：
        //  - 结尾落在编辑点 → 延伸（粗体末尾继续打字，新字还是粗体）；
        //  - 起点落在编辑点 → 让开（在粗体前面打字，新字不该被带上粗体）。
        val start = when {
            s.start < oldStart -> s.start
            s.start == oldStart -> if (delta > 0) oldStart + delta else oldStart
            s.start >= oldEnd -> s.start + delta
            else -> oldStart
        }
        val end = when {
            s.end < oldStart -> s.end
            s.end == oldStart -> if (s.start < oldStart && delta > 0) newEnd else s.end
            s.end >= oldEnd -> s.end + delta
            else -> oldStart
        }
        if (end > start) out.add(s.copy(start = start, end = end))
    }
    return normalizeSpans(newText.length, out)
}

/**
 * 截取 [from, to) 这一段文本的 span，坐标平移成"相对 from"。
 *
 * 把旧笔记里一整段带换行的正文拆成多个块时用得到：每行只带走落在自己这
 * 一段上的样式。
 */
fun sliceSpans(spans: List<Span>, from: Int, to: Int): List<Span> {
    if (spans.isEmpty() || to <= from) return emptyList()
    val out = ArrayList<Span>(spans.size)
    for (s in spans) {
        val a = maxOf(s.start, from)
        val b = minOf(s.end, to)
        if (b > a) out += s.copy(start = a - from, end = b - from)
    }
    return normalizeSpans(to - from, out)
}

/**
 * 打开/关闭某个属性（粗体、斜体…）。
 *
 * [start]..[end] 内**每一段都已带该属性**时是关闭，否则是给整段都加上 ——
 * 这是各家笔记编辑器的通行语义：一段里只要有一处没加粗，按一下就是全加粗。
 */
fun toggleSpanAttr(
    textLength: Int,
    spans: List<Span>,
    start: Int,
    end: Int,
    has: (Span) -> Boolean,
    apply: (Span, Boolean) -> Span
): List<Span> = editRange(textLength, spans, start, end, has) { span, on -> apply(span, on) }

/** 直接写入属性（颜色、字号这类不是"开关"的样式）。 */
fun setSpanAttr(
    textLength: Int,
    spans: List<Span>,
    start: Int,
    end: Int,
    apply: (Span) -> Span
): List<Span> = editRange(textLength, spans, start, end, null) { span, _ -> apply(span) }

/**
 * 把 [start, end) 按所有 span 的边界切成互不重叠的小段，逐段套用 [apply]。
 *
 * 不切段直接覆盖会造成"半个 span 被改属性"：例如 `加粗[0,10)` 上给 `[3,6)`
 * 加红色，[0,10) 整体会变红。分段后每一小段独立重算属性，才是对的。
 */
private fun editRange(
    textLength: Int,
    spans: List<Span>,
    start: Int,
    end: Int,
    has: ((Span) -> Boolean)?,
    apply: (Span, Boolean) -> Span
): List<Span> {
    val a = start.coerceIn(0, textLength)
    val b = end.coerceIn(a, textLength)
    if (b <= a) return spans

    val cuts = sortedSetOf(0, textLength, a, b)
    for (s in spans) {
        cuts += s.start.coerceIn(0, textLength)
        cuts += s.end.coerceIn(0, textLength)
    }
    val points = cuts.toList()
    val segments = ArrayList<Span>(points.size)
    for (i in 0 until points.size - 1) {
        val s = points[i]
        val e = points[i + 1]
        if (e <= s) continue
        val covering = spans.firstOrNull { it.start <= s && e <= it.end }
        segments += (covering ?: Span(s, e)).copy(start = s, end = e)
    }

    // 区间内是否"全都已经有这个属性"：决定这一按是加还是去掉。
    val turnOff = has != null &&
        segments.any { it.start >= a && it.end <= b } &&
        segments.filter { it.start >= a && it.end <= b }.all(has)

    val out = ArrayList<Span>(segments.size)
    for (seg in segments) {
        out += if (seg.start >= a && seg.end <= b) {
            apply(seg, !turnOff).copy(start = seg.start, end = seg.end)
        } else {
            seg
        }
    }
    return normalizeSpans(textLength, out)
}

private fun commonPrefixLength(a: String, b: String): Int {
    val n = minOf(a.length, b.length)
    var i = 0
    while (i < n && a[i] == b[i]) i++
    return i
}

private fun commonSuffixLength(a: String, b: String, prefix: Int): Int {
    val n = minOf(a.length - prefix, b.length - prefix)
    var i = 0
    while (i < n && a[a.length - 1 - i] == b[b.length - 1 - i]) i++
    return i
}
