package app.srpass.kykfz01.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 富文本 span 的算法测试。
 *
 * 这段逻辑全是纯 Kotlin（不碰 Android / Compose），所以放在 JVM 单测里跑：
 * span 的平移与分段是"改一个字就会悄悄串味"的那类代码，靠手点很难验全。
 */
class RichTextTest {

    private fun bold(start: Int, end: Int) = Span(start, end, bold = true)

    @Test
    fun typingAtEndOfStyledRangeExtendsTheStyle() {
        // 在粗体末尾继续打字，新字符应当还是粗体（各家系统笔记都是这个行为）。
        val spans = listOf(bold(0, 3))
        val out = shiftSpans("abc", "abcd", spans)
        assertEquals(listOf(bold(0, 4)), out)
    }

    @Test
    fun deletingShrinksTheStyledRange() {
        val out = shiftSpans("abcd", "abc", listOf(bold(0, 4)))
        assertEquals(listOf(bold(0, 3)), out)
    }

    @Test
    fun insertingRightBeforeStyledRangeDoesNotSwallowTheNewText() {
        // 在粗体"前面"打字，新字不该被带上粗体：span 整体右移。
        val out = shiftSpans("bcd", "abcd", listOf(bold(0, 3)))
        assertEquals(listOf(bold(1, 4)), out)
    }

    @Test
    fun insertingInTheMiddleKeepsOneSpan() {
        val out = shiftSpans("abcd", "abXcd", listOf(bold(0, 4)))
        assertEquals(listOf(bold(0, 5)), out)
    }

    @Test
    fun spanFullyInsideReplacedRegionIsDropped() {
        val out = shiftSpans("hello world", "hello", listOf(bold(6, 11)))
        assertTrue(out.isEmpty())
    }

    @Test
    fun toggleAddsThenRemovesTheAttribute() {
        val on = toggleSpanAttr(
            6, emptyList(), 0, 3,
            has = { it.bold }, apply = { span, b -> span.copy(bold = b) }
        )
        assertEquals(listOf(bold(0, 3)), on)

        val off = toggleSpanAttr(
            6, on, 0, 3,
            has = { it.bold }, apply = { span, b -> span.copy(bold = b) }
        )
        assertTrue(off.isEmpty())
    }

    @Test
    fun toggleOnPartiallyStyledRangeTurnsEverythingOn() {
        // 只加粗了 [0,3)，再对 [1,5) 按一次：应当整段 [0,5) 都变粗体，
        // 而不是把没样式的那一截继续保持原样。
        val out = toggleSpanAttr(
            6, listOf(bold(0, 3)), 1, 5,
            has = { it.bold }, apply = { span, b -> span.copy(bold = b) }
        )
        assertEquals(listOf(bold(0, 5)), out)
    }

    @Test
    fun settingAnotherAttributeSplitsExistingSpan() {
        // 给 [0,10) 的粗体中间 3..6 上个红色：必须切成三段，不能让整段都变红。
        val out = setSpanAttr(10, listOf(bold(0, 10)), 3, 6) { it.copy(color = 0xFFFF0000L) }
        assertEquals(3, out.size)
        assertEquals(bold(0, 3), out[0])
        assertEquals(bold(3, 6).copy(color = 0xFFFF0000L), out[1])
        assertEquals(bold(6, 10), out[2])
    }

    @Test
    fun adjacentSegmentsWithSameAttributesAreMerged() {
        val out = normalizeSpans(10, listOf(bold(0, 3), bold(3, 6)))
        assertEquals(listOf(bold(0, 6)), out)
    }

    @Test
    fun blankSpansAreDropped() {
        val out = normalizeSpans(10, listOf(Span(0, 3), bold(3, 6)))
        assertEquals(listOf(bold(3, 6)), out)
    }

    @Test
    fun sliceSpansKeepsOnlyTheOverlappingPartAndRebases() {
        val out = sliceSpans(listOf(bold(2, 8)), 4, 6)
        assertEquals(listOf(bold(0, 2)), out)
    }
}

/** 笔记块序列化 / 兼容性的测试。 */
class NoteBlocksTest {

    @Test
    fun roundTripKeepsEveryBlockType() {
        val blocks = listOf(
            TextBlock("加粗", spans = listOf(Span(0, 2, bold = true, color = 0xFFD32F2FL))),
            ImageBlock("img-ref", "image/png", caption = "封面"),
            AudioBlock("audio-ref", 42_000L, "录音 1"),
            DrawingBlock("draw-ref", "{}")
        )
        val parsed = jsonToBlocks(blocksToJson(blocks))!!
        assertEquals(blocks, parsed)
    }

    @Test
    fun legacyArrayJsonIsStillAccepted() {
        // v0（旧版本写的裸数组）不能因为格式升级就读不出来了。
        val legacy = """[{"t":"text","text":"老笔记"},{"t":"image","ref":"r","mime":"image/*"}]"""
        val parsed = jsonToBlocks(legacy)!!
        assertEquals(2, parsed.size)
        assertEquals("老笔记", (parsed[0] as TextBlock).text)
        assertTrue((parsed[0] as TextBlock).spans.isEmpty())
        assertEquals("r", (parsed[1] as ImageBlock).ref)
    }

    @Test
    fun unknownJsonIsRejectedRatherThanHalfParsed() {
        // 解析不了要明确报 null（调用方会把原文原样带回），而不是返回半截块。
        assertEquals(null, jsonToBlocks("这不是 JSON"))
    }

    @Test
    fun previewShowsPlaceholdersForMediaOnlyNotes() {
        val note = Note(
            uuid = java.util.UUID.randomUUID(),
            title = "t",
            blocks = listOf(AudioBlock("ref", 1000L), DrawingBlock("ref2"))
        )
        val preview = note.preview()
        assertTrue(preview.contains("音频"))
        assertTrue(preview.contains("涂鸦"))
    }

    @Test
    fun drawingCountsAsAnImageForListBadges() {
        // 涂鸦存的就是 PNG，列表上的"带图"标记要把它算进去。
        val note = Note(
            uuid = java.util.UUID.randomUUID(),
            title = "t",
            blocks = listOf(DrawingBlock("ref"))
        )
        assertTrue(note.hasImage())
        assertTrue(note.hasMedia())
    }
}
