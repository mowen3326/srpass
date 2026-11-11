package app.srpass.kykfz01.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.srpass.kykfz01.ui.AppViewModel
import app.srpass.kykfz01.ui.HiddenKind
import app.srpass.kykfz01.ui.components.AudioRecordingOverlay
import app.srpass.kykfz01.ui.components.CenterTopBar
import app.srpass.kykfz01.ui.components.DiscardChangesDialog
import app.srpass.kykfz01.ui.components.DrawingPadOverlay
import app.srpass.kykfz01.ui.components.MediaPickerSheet
import app.srpass.kykfz01.ui.components.NoteAudioRow
import app.srpass.kykfz01.ui.components.NoteStyleBar
import app.srpass.kykfz01.ui.util.PasteSegment
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.srpass.kykfz01.ui.util.clearClipboard
import app.srpass.kykfz01.ui.util.clipboardHasImage
import app.srpass.kykfz01.ui.util.readPasteSegments
import app.srpass.kykfz01.ui.util.renderDrawing
import app.srpass.kykfz01.ui.util.spansToAnnotated
import app.srpass.kykfz01.vault.AudioBlock
import app.srpass.kykfz01.vault.BinaryBlock
import app.srpass.kykfz01.vault.Block
import app.srpass.kykfz01.vault.DrawingBlock
import app.srpass.kykfz01.vault.DrawingDoc
import app.srpass.kykfz01.vault.ImageBlock
import app.srpass.kykfz01.vault.Note
import app.srpass.kykfz01.vault.Span
import app.srpass.kykfz01.vault.TextBlock
import app.srpass.kykfz01.vault.blocksToJson
import app.srpass.kykfz01.vault.drawingToJson
import app.srpass.kykfz01.vault.formatNoteTime
import app.srpass.kykfz01.vault.jsonToBlocks
import app.srpass.kykfz01.vault.jsonToDrawing
import app.srpass.kykfz01.vault.newBlockId
import app.srpass.kykfz01.vault.normalizeSpans
import app.srpass.kykfz01.vault.setSpanAttr
import app.srpass.kykfz01.vault.shiftSpans
import app.srpass.kykfz01.vault.sliceSpans
import app.srpass.kykfz01.vault.toggleSpanAttr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/** 涂鸦覆盖层的目标：null 表示"新建"，否则是"重画这一块"。 */
private data class DrawingTarget(val blockId: String?, val strokes: String?)

/**
 * 编辑态的块序列跨配置变更（旋转）保存：直接复用落库的 JSON 编解码，避免为
 * 编辑态再写一套序列化。
 */
private val blocksSaver = listSaver<List<Block>, String>(
    save = { listOf(blocksToJson(it)) },
    restore = { flat ->
        val json = flat.firstOrNull()
        if (json.isNullOrBlank()) emptyList() else jsonToBlocks(json) ?: emptyList()
    }
)

/**
 * 把落库的块整理成"可编辑"的形状。
 *
 * 一条不变式：**每个媒体块后面必须有文本块** —— 那是"在图片下面继续写字"
 * 的落脚点，没有它就又回到"插了图就没法在图后面打字"的老问题。
 *
 * 文本块**允许内部换行**（回车就是 '\n'，交给输入框自己处理）。早先的版本把
 * 每次回车都拆成新块，结果光标、输入法候选词、span 坐标都要在块之间来回搬，
 * 输入体验碎得没法用。
 */
private fun prepareBlocks(src: List<Block>): List<Block> {
    val out = ArrayList<Block>(src.size + 2)
    for (i in src.indices) {
        out += src[i]
        if (src[i] is BinaryBlock && src.getOrNull(i + 1) !is TextBlock) out += TextBlock("")
    }
    if (out.isEmpty() || out.last() is BinaryBlock) out += TextBlock("")
    return out
}

/**
 * 保存前去掉"只是光标落脚点"的空文本块，别把它们写成内容。
 *
 * 中间的也要清，不能只清尾部：粘贴、把内容删空都会留下空块，只清尾部的话它
 * 们会被写进笔记，下次打开就是孤零零一行"开始输入…"。打开时 [prepareBlocks]
 * 会把媒体后面需要的落脚块补回来，所以这里清干净不会丢东西。
 */
private fun commitBlocks(blocks: List<Block>): List<Block> {
    val out = ArrayList(blocks)
    out.removeAll { it is TextBlock && it.text.isEmpty() }
    return out
}

private fun isBlankContent(blocks: List<Block>): Boolean =
    blocks.all { it is TextBlock && it.text.isBlank() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    viewModel: AppViewModel,
    uuid: UUID?,
    fromHidden: Boolean = false,
    /** 从全屏看图返回时带上那张图的 ref：滚回它旁边，别让用户从头翻。 */
    scrollToRef: String? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun leave() {
        // 这次编辑里导入却没用上的媒体别留在库里：它们会跟着每一次备份走。
        viewModel.discardPendingBinaries()
        if (fromHidden) viewModel.goHiddenSpace(HiddenKind.NOTE) else viewModel.goNotesList()
    }

    // Snapshot once: this reads and re-parses the note, so running it on every
    // recomposition would re-do that work on each keystroke.
    //
    // 做成可变状态是因为保存不再退出页面：存完之后这份快照得跟着换成刚存进去
    // 的版本，否则笔记刚"已保存"却一直显示着未保存。
    var existing by remember(uuid) { mutableStateOf(uuid?.let { viewModel.getNote(it) }) }
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var originalBlocks by remember(uuid) {
        mutableStateOf(prepareBlocks(existing?.blocks ?: emptyList()))
    }
    var blocks by rememberSaveable(stateSaver = blocksSaver) { mutableStateOf(originalBlocks) }

    // 每个输入框自己的 TextFieldValue（含 selection / composition）。用
    // snapshotStateMap：改光标也要能触发重组，否则工具栏的按钮状态会滞后。
    val fieldValues = remember { mutableStateMapOf<String, TextFieldValue>() }

    /**
     * 样式作用在哪个文本块上。
     *
     * 关键：**不随失焦清空**。点工具栏按钮时输入框会失去焦点（哪怕按钮本身
     * 不可聚焦，软键盘收起也会带走焦点），一清空所有样式按钮就全部失效 —— 这正是
     * "加粗/字体怎么点都没反应"的原因。它只在块被删掉时才换目标。
     */
    var styleBlockId by remember {
        mutableStateOf(originalBlocks.filterIsInstance<TextBlock>().firstOrNull()?.id)
    }
    /**
     * 需要主动抢焦点的块（插入媒体后要把光标放到图后面的空块里）。
     * 抢完立刻清空，否则每次重组都会把焦点硬抢回去。
     */
    var pendingFocusId by remember {
        // 只有新建笔记才自动把光标放进正文（省一次点击）。已有笔记进来是查看
        // 态，点哪一块才从哪一块开始写，不能替用户先把焦点抢走。
        mutableStateOf(if (uuid == null) originalBlocks.filterIsInstance<TextBlock>().firstOrNull()?.id else null)
    }

    /**
     * 用户最后一次**真正碰过**的正文位置：块 id + 光标偏移。
     *
     * 插入媒体只认它。[styleBlockId] 不行 —— 它会被删块、合并段落这些动作改写，
     * 而"第一个文本块"在图片居多的笔记里往往就是末尾那一段，照它插入等于永远
     * 插到笔记底部。这里只记用户点过/打过字的地方；一次都没碰过正文（比如点
     * 标题直接进的编辑态）就返回 null，交给 [captureInsertAnchor] 落到最前面。
     */
    var lastFocus by remember { mutableStateOf<Pair<String, Int>?>(null) }

    /**
     * 插入媒体的锚点：**块 id + 光标偏移**，在点开选择器那一刻就记下来。
     *
     * 图片要先压缩再入库，中间隔着好几秒，而这段时间里"当前块"一直在漂 —— 点
     * 按钮、弹选择器都会把焦点带跑，上一次插入又把 [styleBlockId] 留在了图后面
     * 那个空块上（笔记末尾）。等图片准备好再回头读它，插入点就已经不是用户点
     * 的那一处了，看着就是"图跑到笔记底部去了"。锚点在发起时锁死，之后焦点怎么
     * 动都不影响。
     */
    var insertAnchor by remember { mutableStateOf<Pair<String, Int>?>(null) }

    var showPicker by remember { mutableStateOf(false) }
    var needSettings by remember { mutableStateOf(false) }
    // 涂鸦与录音都在编辑器内以覆盖层完成：跳到独立页面会让编辑器被销毁，
    // 这条还没保存的笔记就一起没了。
    var drawingTarget by remember { mutableStateOf<DrawingTarget?>(null) }
    var recording by remember { mutableStateOf(false) }
    var needMicSettings by remember { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    /**
     * 浏览态 / 编辑态。
     *
     * 已有笔记默认**浏览态**：不弹键盘、底栏收起、图上是"点开看大图"，正文整屏
     * 可读。点哪一段就在那一段接着写 —— 早先是"先按编辑、跳到另一个页面"，滚动
     * 位置归零，看到一半想改还得从头翻回去。
     *
     * 进编辑态只有一条路：点到要改的地方（正文或标题）。新建笔记没有"看"的这
     * 一步，直接进编辑态。
     */
    var editing by rememberSaveable { mutableStateOf(uuid == null) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    // 连点两下保存会写进两条。
    var saving by remember { mutableStateOf(false) }

    // existing / originalBlocks 必须进 key：保存后它们被换成刚存进去的版本，而
    // title 与 blocks 一个字没变 —— 少这两个 key，存完的笔记会一直算"未保存"。
    val dirty = remember(title, blocks, existing, originalBlocks) {
        title != (existing?.title ?: "") || commitBlocks(blocks) != commitBlocks(originalBlocks)
    }

    /** 返回前先问一句，免得误触一下把刚写完的笔记全丢了。 */
    fun requestLeave() {
        if (dirty) showDiscard = true else leave()
    }

    BackHandler { requestLeave() }

    // The permission answer survives activity recreation this way; a direct
    // callback from the activity would be lost if the device rotated while the
    // system dialog was up.
    LaunchedEffect(Unit) {
        viewModel.mediaPermissionResult.collect { granted ->
            if (granted) showPicker = true else needSettings = true
        }
    }
    LaunchedEffect(Unit) {
        viewModel.micPermission.collect { granted ->
            if (granted) recording = true else needMicSettings = true
        }
    }

    // ---- 块操作 ---------------------------------------------------------

    fun indexOf(id: String?): Int = if (id == null) -1 else blocks.indexOfFirst { it.id == id }

    /**
     * 样式/插入的目标文本块：记住的那块还在就用它，否则退回第一个文本块。
     *
     * 有了这个兜底，工具栏永远有作用对象 —— 用户不会遇到"按钮亮着但点了没反应"。
     */
    fun targetBlock(): TextBlock? =
        blocks.firstOrNull { it.id == styleBlockId } as? TextBlock
            ?: blocks.filterIsInstance<TextBlock>().firstOrNull()

    fun selectionOf(b: TextBlock): TextRange =
        fieldValues[b.id]?.selection ?: TextRange(b.text.length)

    /** 记下"这次插入要落在哪"：光标所在块 + 块内偏移，供 [insertAfterFocus] 用。 */
    fun captureInsertAnchor() {
        // 只认用户亲手碰过的那一处。[styleBlockId] 不行：它被删块、合并段落这些
        // 动作改写过，而它的兜底"第一个文本块"在图片居多的笔记里往往就是末尾
        // 那一段 —— 照它插就是"永远插到笔记底部"。
        val marked = lastFocus?.let { (id, caret) ->
            (blocks.firstOrNull { it.id == id } as? TextBlock)
                ?.let { it.id to caret.coerceIn(0, it.text.length) }
        }
        // 一次都没碰过正文（例如点标题直接进的编辑态）：落到最前面，而不是末尾。
        insertAnchor = marked
            ?: blocks.filterIsInstance<TextBlock>().firstOrNull()?.let { it.id to 0 }
    }

    /**
     * 在光标所在位置**之后**插入若干块。
     *
     * 光标落在某个文本块中间时先把它劈成两半：媒体插在断点处，后半段留在媒体
     * 后面的新块里，样式坐标也按断点切开。最后无论如何都补一个文本块并聚焦它
     * —— 那就是"在图片下面继续写字"的落点。
     */
    fun insertAfterFocus(inserted: List<Block>) {
        // 先取走锚点：这次成不成都不该留着它，否则残留会左右下一次插入。
        val anchor = insertAnchor
        insertAnchor = null
        if (inserted.isEmpty()) return
        // 锚点第一优先；它指的那块没了（期间被删）才退回 styleBlockId；两个都
        // 落空时用第一个文本块 —— 直接跳 lastIndex 会把内容插到笔记最末尾。
        val idx = listOfNotNull(anchor?.first, styleBlockId)
            .firstNotNullOfOrNull { indexOf(it).takeIf { i -> i >= 0 } }
            ?: blocks.indexOfFirst { it is TextBlock }.takeIf { it >= 0 }
            ?: blocks.lastIndex
        val list = ArrayList(blocks)
        val cur = list.getOrNull(idx) as? TextBlock
        // 锚点记的就是这一块的偏移，直接用；没有锚点才去看输入框里的光标。
        val caret = if (cur == null) 0
        else ((anchor?.second ?: fieldValues[cur.id]?.selection?.max ?: cur.text.length))
            .coerceIn(0, cur.text.length)

        // 光标就落在一个空块里时，别把这个空块留在插入内容前面：它没有任何内容
        // 可留，却会顶着"开始输入…"赖在那儿（在最前面时退格还删不掉）。直接用
        // 插入的内容把它的位置顶掉。
        val emptyCur = cur?.takeIf { it.text.isEmpty() }
        if (emptyCur != null) {
            fieldValues.remove(emptyCur.id)
            list.removeAt(idx)
        }

        val rest: TextBlock? = if (cur != null && emptyCur == null) {
            val head = cur.copy(
                text = cur.text.substring(0, caret),
                spans = sliceSpans(cur.spans, 0, caret)
            )
            list[idx] = head
            fieldValues[cur.id] = TextFieldValue(head.text, selection = TextRange(caret))
            TextBlock(
                text = cur.text.substring(caret),
                spans = sliceSpans(cur.spans, caret, cur.text.length)
            )
        } else null

        var at = if (emptyCur != null) idx else idx + 1
        // 媒体必须紧跟断点：先落媒体，再把后半段接到它后面。反着来（后半段在前、
        // 媒体在后）的话，图会跑到整段文字之后 —— 光标明明在段中间，图却出现在
        // 段末甚至笔记底部。光标本来就在段末时后半段为空，顺序碰巧是对的，这也
        // 就是"多点几次有时候又成功了"的由来。
        list.addAll(at, inserted)
        at += inserted.size
        if (rest != null && rest.text.isNotEmpty()) {
            list.add(at, rest)
            at++
        }

        // 媒体后面永远留一个文本块。后半段本来就是空的就直接用它，别留下两个
        // 连着的空块。
        val landing = if (rest != null && rest.text.isEmpty()) rest else TextBlock("")
        list.add(at, landing)
        fieldValues[landing.id] = TextFieldValue(landing.text, selection = TextRange(0))
        styleBlockId = landing.id
        pendingFocusId = landing.id
        // 锚点跟着挪到落脚块：不挪的话连着插第二张时，它还停在上半段上，新图会
        // 跑到上一张前面去。
        lastFocus = landing.id to 0
        editing = true
        blocks = list
    }

    fun deleteBlock(id: String) {
        val idx = indexOf(id)
        if (idx < 0) return
        val list = ArrayList(blocks).also { it.removeAt(idx) }
        fieldValues.remove(id)
        // 删掉媒体后，紧跟其后的空落脚块一起走：它存在的唯一理由（"在图片下
        // 面接着写"）没了，留着就是一条删不掉的空行。笔记里得留个能输入的地
        // 方，所以只剩它一个文本块时不删。
        val next = list.getOrNull(idx)
        if (next is TextBlock && next.text.isEmpty() && list.count { it is TextBlock } > 1) {
            list.removeAt(idx)
            fieldValues.remove(next.id)
        }
        // 兜底：末尾是媒体就没地方往下写了，补一个空块回来。
        if (list.isEmpty() || list.last() is BinaryBlock) list += TextBlock("")
        if (styleBlockId == id) styleBlockId = list.filterIsInstance<TextBlock>().firstOrNull()?.id
        if (pendingFocusId == id) pendingFocusId = null
        // 锚点指的那块已经不在了就挪走：留着它，下次插入会在 [captureInsertAnchor]
        // 里匹配不上而退回"第一个文本块"，凭空跳到笔记最前面。
        val marked = lastFocus?.first
        if (marked != null && !list.any { it.id == marked }) {
            lastFocus = list.filterIsInstance<TextBlock>().firstOrNull()?.let { it.id to 0 }
        }
        blocks = list
    }

    /**
     * 光标停在一个块的最前面时按退格：前一个块是文本就并进来，是图片/音频/
     * 涂鸦就把它删掉。
     *
     * 说明：软键盘的退格是走 InputConnection 的删除命令、不产生按键事件，所以
     * 这个入口只对**实体键盘**生效。软件键盘下多余的文本块还有另一条清理路径
     * —— 空的文本块一旦失焦就会自动消失（见 [onBlur]）。
     */
    fun mergeIntoPrevious(id: String) {
        val idx = indexOf(id)
        if (idx < 0) return
        val cur = blocks[idx] as? TextBlock ?: return
        val list = ArrayList(blocks)
        if (idx == 0) {
            // 第一个块前面没东西可并。它是空的话就把自己删掉 —— 否则它会一直
            // 顶着"开始输入…"占位符，按退格又毫无反应，看着就像删不掉。
            // 笔记里总得留一个能输入的地方，所以只剩这一块时不删。
            if (cur.text.isEmpty() && list.size > 1) {
                list.removeAt(idx)
                fieldValues.remove(id)
                // 落在下一个**文本**块上：图片/音频块没有输入框，焦点给它等于
                // 丢焦点，键盘会白收起来。
                val next = list.filterIsInstance<TextBlock>().firstOrNull()
                styleBlockId = next?.id
                pendingFocusId = next?.id
                blocks = list
            }
            return
        }
        val prev = blocks[idx - 1]
        when (prev) {
            is TextBlock -> {
                val shifted = cur.spans.map {
                    it.copy(start = it.start + prev.text.length, end = it.end + prev.text.length)
                }
                val mergedText = prev.text + cur.text
                val merged = prev.copy(
                    text = mergedText,
                    spans = normalizeSpans(mergedText.length, prev.spans + shifted)
                )
                list[idx - 1] = merged
                list.removeAt(idx)
                fieldValues.remove(id)
                fieldValues[merged.id] =
                    TextFieldValue(mergedText, selection = TextRange(prev.text.length))
                styleBlockId = merged.id
                pendingFocusId = merged.id
            }
            is BinaryBlock -> list.removeAt(idx - 1)
        }
        blocks = list
    }

    /**
     * 失焦时把空文本块收掉。
     *
     * 软键盘的退格走 InputConnection 的删除命令、不产生按键事件，所以
     * [mergeIntoPrevious] 那条路（[Modifier.onKeyEvent]）在软键盘下根本不会触发
     * —— "把内容删空后剩下的空块"得在失焦时清掉，否则那个"开始输入…"占位符就
     * 一直赖在页面上。
     *
     * 两条保留规则：笔记里总要剩一个能输入的地方；媒体后面那个落脚块是"在图
     * 下面接着写"的入口，也不能收。
     */
    fun dropEmptyBlock(id: String) {
        val idx = indexOf(id)
        if (idx < 0) return
        val b = blocks[idx] as? TextBlock ?: return
        if (b.text.isNotEmpty()) return
        if (blocks.count { it is TextBlock } <= 1) return
        if (blocks.getOrNull(idx - 1) is BinaryBlock) return
        // 锚点指着这一块：插入还在半路上（图片在压缩），删了它就等于把插入点删没了。
        if (insertAnchor?.first == id) return
        val list = ArrayList(blocks)
        list.removeAt(idx)
        fieldValues.remove(id)
        if (styleBlockId == id) {
            styleBlockId = list.filterIsInstance<TextBlock>().firstOrNull()?.id
        }
        if (pendingFocusId == id) pendingFocusId = null
        blocks = list
    }

    /**
     * 文本变化：块内**允许换行**，回车就是一个 '\n'，直接存进块里。
     *
     * 不再按回车拆块：块一多，光标、输入法候选、span 坐标就要在块之间搬来搬去，
     * 每敲一下都可能跳焦点。
     */
    fun onTextChange(block: TextBlock, value: TextFieldValue) {
        val idx = indexOf(block.id)
        if (idx < 0) return
        fieldValues[block.id] = value
        styleBlockId = block.id
        // 光标在哪就记到哪：这是"点哪里插入哪里"的唯一依据，样式目标可以变，
        // 用户最后碰过的地方不能变。
        lastFocus = block.id to value.selection.max
        blocks = ArrayList(blocks).also {
            it[idx] = block.copy(
                text = value.text,
                spans = shiftSpans(block.text, value.text, block.spans)
            )
        }
    }

    // ---- 富文本样式 -----------------------------------------------------

    /** 当前光标/选区处的属性，用来点亮工具栏按钮。 */
    fun currentSpan(): Span {
        val b = targetBlock() ?: return Span(0, 0)
        val at = selectionOf(b).min.coerceIn(0, b.text.length)
        return b.spans.firstOrNull { it.start <= at && at < it.end } ?: Span(0, 0)
    }

    /** 当前段落级别（0 正文，1..3 标题）。 */
    fun currentLevel(): Int = targetBlock()?.para?.level ?: 0

    /**
     * 对目标块的选区套用样式；没有选区时作用于**整块**（点一下"加粗"就把
     * 光标所在段加粗，这更符合直觉，也避免"选不中字就没法改样式"）。
     */
    fun editSpans(transform: (Int, List<Span>, Int, Int) -> List<Span>) {
        val b = targetBlock() ?: return
        val idx = indexOf(b.id)
        if (idx < 0) return
        val sel = selectionOf(b)
        val a = sel.min.coerceIn(0, b.text.length)
        val e = sel.max.coerceIn(a, b.text.length)
        val (start, end) = if (e > a) a to e else 0 to b.text.length
        styleBlockId = b.id
        blocks = ArrayList(blocks).also {
            it[idx] = b.copy(spans = transform(b.text.length, b.spans, start, end))
        }
    }

    /** 段落级别（正文/标题）：作用在整块上，它是段落属性而不是字符属性。 */
    fun setLevel(level: Int) {
        val b = targetBlock() ?: return
        val idx = indexOf(b.id)
        if (idx < 0) return
        styleBlockId = b.id
        blocks = ArrayList(blocks).also { it[idx] = b.copy(para = b.para.copy(level = level)) }
    }

    fun toggleBold() = editSpans { len, spans, s, e ->
        toggleSpanAttr(len, spans, s, e, has = { it.bold }) { span, on -> span.copy(bold = on) }
    }

    fun toggleItalic() = editSpans { len, spans, s, e ->
        toggleSpanAttr(len, spans, s, e, has = { it.italic }) { span, on -> span.copy(italic = on) }
    }

    fun toggleUnderline() = editSpans { len, spans, s, e ->
        toggleSpanAttr(len, spans, s, e, has = { it.underline }) { span, on -> span.copy(underline = on) }
    }

    fun toggleStrike() = editSpans { len, spans, s, e ->
        toggleSpanAttr(len, spans, s, e, has = { it.strike }) { span, on -> span.copy(strike = on) }
    }

    fun setFontSize(size: Int?) = editSpans { len, spans, s, e ->
        setSpanAttr(len, spans, s, e) { it.copy(size = size) }
    }

    fun setFont(font: String?) = editSpans { len, spans, s, e ->
        setSpanAttr(len, spans, s, e) { it.copy(font = font) }
    }

    fun setColor(color: Long?) = editSpans { len, spans, s, e ->
        setSpanAttr(len, spans, s, e) { it.copy(color = color) }
    }

    fun setHighlight(bg: Long?) = editSpans { len, spans, s, e ->
        setSpanAttr(len, spans, s, e) { it.copy(bg = bg) }
    }

    // ---- 插入图片 -------------------------------------------------------

    /**
     * 把剪贴板里的"文字 / 图片"序列转成块序列。图片在这里就入库（压进 KDBX
     * 二进制池），所以粘进来的图和从相册插入的图是同一条存储路径，备份不会丢。
     *
     * @return 块序列 + 导入失败的图片数。
     */
    suspend fun pasteSegmentsToBlocks(
        segments: List<PasteSegment>
    ): Pair<List<Block>, Int> {
        var failed = 0
        val out = ArrayList<Block>()
        for (seg in segments) {
            when (seg) {
                is PasteSegment.Text -> if (seg.text.isNotEmpty()) {
                    out += TextBlock(seg.text, seg.spans)
                }

                is PasteSegment.Image -> {
                    val ref = viewModel.importClipboardImage(seg.src)
                    if (ref != null) out += ImageBlock(ref, "image/*") else failed++
                }
            }
        }
        return out to failed
    }

    /**
     * 接管"带图片的粘贴"。
     *
     * 返回 true = 这次粘贴由我们消费（剪贴板里有图，按原文顺序还原成"文本块 /
     * 图片块 / 文本块…"，再交给 [insertAfterFocus] 插到光标处）；
     * false = 纯文字，交回系统默认粘贴 —— 这一步很关键，否则同一段文字会被
     * 插进来两次。
     */
    fun pasteRich(): Boolean {
        val segments = readPasteSegments(context) ?: return false
        captureInsertAnchor()
        scope.launch {
            val (first, failed) = pasteSegmentsToBlocks(segments)
            if (failed == 0) {
                if (first.isNotEmpty()) {
                    insertAfterFocus(first)
                    clearClipboard(context)
                }
                return@launch
            }
            // 读不出来的头号原因：来源应用没给这个 Uri 的读权限。图本来就在公共
            // 相册里的话，补一次媒体权限通常就够了。
            viewModel.requestMediaPermissionThen { granted ->
                if (!granted) {
                    viewModel.showMessage(
                        "有 $failed 张图片读不出来：来源应用未授权，可先保存到相册再插入"
                    )
                    if (first.isNotEmpty()) insertAfterFocus(first)
                    return@requestMediaPermissionThen
                }
                scope.launch {
                    val (retry, stillFailed) = pasteSegmentsToBlocks(segments)
                    if (stillFailed > 0) viewModel.showMessage("有 $stillFailed 张图片导入失败")
                    if (retry.isNotEmpty()) {
                        insertAfterFocus(retry)
                        clearClipboard(context)
                    }
                }
            }
        }
        return true
    }

    fun insertImages(uris: List<Uri>) {
        scope.launch {
            var failed = 0
            val inserted = ArrayList<Block>()
            uris.forEach { uri ->
                val ref = viewModel.importImageToVault(uri)
                if (ref != null) inserted += ImageBlock(ref, "image/*") else failed++
            }
            if (failed > 0) viewModel.showMessage("有 $failed 张图片导入失败")
            insertAfterFocus(inserted)
        }
    }

    // ---- 涂鸦 -----------------------------------------------------------

    /**
     * 涂鸦完成：导出 PNG 入二进制池，同时把笔画矢量写进块里。
     *
     * "重画"时会留下一张没人引用的旧 PNG，下一次保存的孤儿回收会顺手清掉它，
     * 不用在这里专门删。
     */
    fun confirmDrawing(doc: DrawingDoc) {
        val target = drawingTarget ?: return
        drawingTarget = null
        scope.launch {
            val png = withContext(Dispatchers.Default) { renderDrawing(doc) }
            val ref = viewModel.importDrawingToVault(png) ?: return@launch
            val json = drawingToJson(doc)
            val id = target.blockId
            if (id == null) {
                insertAfterFocus(listOf(DrawingBlock(ref, json)))
            } else {
                val idx = indexOf(id)
                if (idx >= 0) {
                    blocks = ArrayList(blocks).also { it[idx] = DrawingBlock(ref, json, id = id) }
                }
            }
        }
    }

    // ---- 录音 -----------------------------------------------------------

    fun onAudioRecorded(bytes: ByteArray, durationMs: Long) {
        recording = false
        scope.launch {
            val ref = viewModel.importAudioToVault(bytes)
            if (ref != null) insertAfterFocus(listOf(AudioBlock(ref, durationMs)))
        }
    }

    // ---- 保存 -----------------------------------------------------------

    fun save() {
        if (saving) return
        val content = commitBlocks(blocks)
        if (title.isBlank() && (content.isEmpty() || isBlankContent(content))) {
            viewModel.showMessage("请至少填写标题或内容")
            return
        }
        // 一个字都没改就别写库：updateNote 会把"保存于"刷成现在，看着像刚动过
        // 一样，而用户其实只是进来看看。
        if (existing != null && !dirty) {
            leave()
            return
        }
        val note = Note(
            uuid = existing?.uuid ?: UUID.randomUUID(),
            title = title.trim(),
            blocks = content,
            // Carrying the flag over is what keeps a hidden note hidden after
            // being edited; without it one save would silently publish it. New
            // notes written from inside the hidden space start hidden.
            hidden = existing?.hidden ?: fromHidden
        )
        scope.launch {
            saving = true
            try {
                if (existing == null) viewModel.addNote(note) else viewModel.updateNote(note)
                // 存完就留在这儿：才写完就被扔回列表，想接着写还得再翻一遍找
                // 回来。把内存里的快照同步成刚存进去的版本（dirty 归零、顶栏
                // 的勾收起），滚动位置、光标、键盘一律不动。
                title = note.title
                existing = viewModel.getNote(note.uuid) ?: note
                originalBlocks = content
                viewModel.showMessage("已保存")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                viewModel.showMessage("保存失败：${e.message ?: "未知错误"}")
            } finally {
                saving = false
            }
        }
    }

    val scrollState = rememberScrollState()
    val span = currentSpan()

    // 每个文本块一个"滚到可见"的请求器：面板展开会挤掉正文一截高度，把正在
    // 编辑的那块顶回可视区。用块 id 缓存，删掉的块留一条空记录无伤大雅。
    val bringRequesters = remember { mutableMapOf<String, BringIntoViewRequester>() }

    // 剪贴板里一有图就把底栏的"粘贴图片"点亮。输入法的粘贴键直接 commit 纯
    // 文本，绕过了 Compose 的粘贴回调 —— 没有这个入口，从别的笔记 App 复制
    // 的图就永远进不来。
    var hasClipImage by remember { mutableStateOf(false) }
    // 剪贴板每换一次内容 clipKey 就 +1：横幅靠它判断"是不是新复制的"。粘完或
    // 关掉后记下当前值不再打扰，等下次剪贴板一变，横幅自己又冒出来。
    var clipKey by remember { mutableIntStateOf(0) }
    var dismissedKey by remember { mutableIntStateOf(-1) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val cm = context.applicationContext
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // Android 10 起**后台应用读不到剪贴板**：在别的 App 里点复制时这里收到
        // 回调也只会读到空，所以回到前台（ON_RESUME）必须再测一次 —— 少了这一
        // 步，按钮就会一直灰着。
        fun refresh() {
            hasClipImage = clipboardHasImage(context)
        }
        val handler = Handler(Looper.getMainLooper())
        // 只有"剪贴板内容真的换了"才 +1。回到前台的补测不动它，否则用户刚关掉
        // 的横幅一进页面又冒出来。
        val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
            clipKey++
            refresh()
        }
        val lifeObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh()
                // 刚 resume 的这一刻系统往往还没把本 App 判成前台，读出来仍然是
                // null，所以稍后再补一次。真正读得准的时刻是用户**点击**按钮时
                // —— 那时窗口必然有焦点，见下方按钮始终可点。
                handler.postDelayed({ refresh() }, 800)
            }
        }
        cm.addPrimaryClipChangedListener(clipListener)
        lifecycleOwner.lifecycle.addObserver(lifeObserver)
        refresh()
        onDispose {
            handler.removeCallbacksAndMessages(null)
            cm.removePrimaryClipChangedListener(clipListener)
            lifecycleOwner.lifecycle.removeObserver(lifeObserver)
        }
    }

    var panelOpen by remember { mutableStateOf(false) }
    LaunchedEffect(panelOpen) {
        if (!panelOpen) return@LaunchedEffect
        val id = styleBlockId ?: return@LaunchedEffect
        // 正文被挤矮 → 可滚动范围变大，这个变化要等这一帧布局跑完才生效。立刻
        // 按旧视口判断"已经可见"，结果就是一点都不滚 —— 上一版补不动就栽在这。
        // 内容短到滚不动时最多等 400ms 就放弃。
        val before = scrollState.maxValue
        withTimeoutOrNull(400) {
            snapshotFlow { scrollState.maxValue }.first { it > before }
        }
        runCatching { bringRequesters[id]?.bringIntoView() }
    }

    // 底栏滑出同样会挤掉正文一截高度，正在点的那一段可能被顶出视口。跟上面那条
    // 一样，要等这一帧布局跑完再把它带回来。
    LaunchedEffect(editing) {
        if (!editing) return@LaunchedEffect
        val id = styleBlockId ?: return@LaunchedEffect
        val before = scrollState.maxValue
        withTimeoutOrNull(400) {
            snapshotFlow { scrollState.maxValue }.first { it > before }
        }
        runCatching { bringRequesters[id]?.bringIntoView() }
    }

    // 从全屏看图回来：滚回那张图旁边。媒体块自己没有输入框（也就没有
    // requester），拿它后面那个落脚文本块当锚点 —— prepareBlocks 保证媒体后面
    // 一定有文本块。
    LaunchedEffect(scrollToRef) {
        val ref = scrollToRef ?: return@LaunchedEffect
        val idx = blocks.indexOfFirst { (it as? BinaryBlock)?.ref == ref }
        if (idx < 0) return@LaunchedEffect
        val anchor = blocks.drop(idx).filterIsInstance<TextBlock>().firstOrNull()
            ?: return@LaunchedEffect
        // 等正文真的能滚再带过来；短笔记本来就滚不动，最多等 400ms 就放弃。
        withTimeoutOrNull(400) { snapshotFlow { scrollState.maxValue }.first { it > 0 } }
        runCatching { bringRequesters[anchor.id]?.bringIntoView() }
    }

    Scaffold(
        // 默认 contentWindowInsets 会把导航条那一截算进内容区的**底部**，底栏
        // 因此悬在屏幕底/键盘上方，上下都露出一条窗口背景。这里清零：状态栏由
        // TopAppBar 自己避让，底部完全交给 Box（ime）和底栏（navigationBars）。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterTopBar(
                title = when {
                    existing == null -> "新建笔记"
                    editing -> "编辑笔记"
                    else -> "笔记详情"
                },
                navigationIcon = {
                    IconButton(onClick = { requestLeave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 一个字都没改就没什么可存的，别把"保存"摆在那儿当装饰。
                    if (dirty) {
                        IconButton(onClick = { save() }) {
                            Icon(Icons.Filled.Check, contentDescription = "保存")
                        }
                    }
                    // 新建的笔记存过一次之后也能删：它已经进库了。
                    if (existing != null) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                }
            )
        }
    ) { padding ->
        // 整块编辑区按"键盘上沿"收缩：底栏因此天然贴在键盘顶部、中间不留缝，
        // 正文也永远不会落到键盘底下。手势条那一截交给底栏自己吃（它的背景要
        // 一直画到屏幕底），这里不加 —— 加了底栏底下就会露出一条空白。
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.ime)
        ) {
            Column(Modifier.fillMaxSize()) {
                // 滚动条颜色提前取：drawWithContent 的 lambda 不是 @Composable 上下文。
                val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
                // 滚动时才显示、停止后淡出。
                var isScrolling by remember { mutableStateOf(false) }
                LaunchedEffect(scrollState) {
                    snapshotFlow { scrollState.isScrollInProgress }
                        .collect { isScrolling = it }
                }
                val scrollbarAlpha by animateFloatAsState(
                    targetValue = if (isScrolling) 1f else 0f,
                    animationSpec = tween(180),
                    label = "scrollbarAlpha"
                )
                // 用可滚动 Column 而不是 LazyColumn：文本输入框一旦滚出可视区，
                // LazyColumn 会把它回收掉，正在输入的那块连同输入法会话一起没了。
                // weight(1f)：正文只占底栏上面的那截，底栏是布局里的**一行**而不是
                // 盖在正文上的浮层 —— 这样"面板压住文字"在布局上就不可能发生，
                // 也不用再去量底栏有多高。
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // 只读滚动条：画在右侧 16dp 边距里，浅色细条，只显示位置、
                        // 不可交互，也不会压到文字。
                        .drawWithContent {
                            drawContent()
                            val max = scrollState.maxValue
                            if (max > 0 && scrollbarAlpha > 0f) {
                                val viewport = size.height
                                val total = max + viewport
                                val trackWidth = 5.dp.toPx()
                                val thumbHeight = (viewport * viewport / total)
                                    .coerceAtLeast(20.dp.toPx())
                                val travel = viewport - thumbHeight
                                val top = (scrollState.value.toFloat() / max) * travel
                                drawRoundRect(
                                    color = scrollbarColor.copy(alpha = scrollbarColor.alpha * scrollbarAlpha),
                                    topLeft = Offset(size.width - trackWidth, top),
                                    size = Size(trackWidth, thumbHeight),
                                    cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)
                                )
                            }
                        }
                        // 底部不额外留白：块与块之间本来就有 10dp 间距，这里再垫
                        // 一层就变成文字和底栏之间"多出来的一条缝"。
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                        .verticalScroll(scrollState)
                ) {
                    TitleField(title, onFocus = { editing = true }) { title = it }
                    // 浏览态给一行"保存于"：原来那个独立的详情页有，进来只看一
                    // 眼的用户靠它判断新鲜度。
                    val saved = existing
                    if (!editing && saved != null && saved.updatedAt > 0) {
                        Text(
                            "保存于 ${formatNoteTime(saved.updatedAt)} · ${saved.wordCount()}字",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    blocks.forEach { block ->
                        when (block) {
                            is TextBlock -> TextBlockEditor(
                                block = block,
                                stored = fieldValues[block.id],
                                autoFocus = pendingFocusId == block.id,
                                // 点到哪一块就从哪一块开始写 —— 这是从浏览态进
                                // 入编辑态的唯一入口。
                                onFocus = { sel ->
                                    styleBlockId = block.id
                                    // 点一下就把光标偏移记下来：只点光标不改字时，
                                    // 输入框不会回调 onValueChange，光靠它记不住
                                    // 位置 —— 插图就会落到整段之后而不是光标处。
                                    fieldValues[block.id] =
                                        TextFieldValue(block.text, selection = sel)
                                    lastFocus = block.id to sel.max
                                    pendingFocusId = null
                                    editing = true
                                },
                                onBlur = { dropEmptyBlock(block.id) },
                                // 只有"整篇笔记就剩这一个空块"才提示"开始输入…"。
                                //
                                // 其余空块都是落脚点（媒体后面那个尤其典型：它被
                                // [dropEmptyBlock] 保护着、删不掉，一旦摆出提示就
                                // 成了图片下面一条删不掉的"空白行"）。点一下就能
                                // 写，不需要靠占位符提示。浏览态一律不显示。
                                hintVisible = editing && blocks.size == 1,
                                onValueChange = { onTextChange(block, it) },
                                onBackspaceAtStart = { mergeIntoPrevious(block.id) },
                                onPasteRich = { pasteRich() },
                                    modifier = Modifier.bringIntoViewRequester(
                                    bringRequesters.getOrPut(block.id) { BringIntoViewRequester() }
                                )
                            )
                            is ImageBlock -> ImageBlockEditor(
                                viewModel,
                                block,
                                editing = editing,
                                onView = {
                                    uuid?.let { viewModel.openImageViewer(block.ref, it, fromHidden) }
                                },
                                onDelete = { deleteBlock(block.id) }
                            )
                            is DrawingBlock -> DrawingBlockEditor(
                                viewModel,
                                block,
                                editing = editing,
                                onView = {
                                    uuid?.let { viewModel.openImageViewer(block.ref, it, fromHidden) }
                                },
                                onDelete = { deleteBlock(block.id) },
                                onRedraw = { drawingTarget = DrawingTarget(block.id, block.strokes) }
                            )
                            is AudioBlock -> NoteAudioRow(
                                viewModel,
                                block,
                                onDelete = if (editing) { { deleteBlock(block.id) } } else null
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }

                // 复制完切回来，剪贴板里有图就直接给一条显眼的入口。MIUI 长按
                // 弹出的那个菜单是输入法给的，不走 Compose 的粘贴回调，拦不住
                // —— 这条横幅就是它的替代：一点就按原文顺序把文字和图片粘进来。
                // 浏览态没有光标，粘进来也不知道该放哪儿，先不打扰。
                if (editing && hasClipImage && clipKey != dismissedKey) {
                    PasteClipBanner(
                        onPaste = {
                            if (pasteRich()) dismissedKey = clipKey
                            else viewModel.showMessage("剪贴板里没有可粘贴的内容")
                        },
                        onDismiss = { dismissedKey = clipKey }
                    )
                }

                // 底栏是这一列的最后一行（不是浮层），键盘/手势条的避让由外层
                // Box 统一做，这里什么都不用加。
                //
                // 浏览态整个收起来：那会儿是"看"，键盘不弹、样式按钮也用不上，
                // 留着只会白占一截正文，还容易误触。
                AnimatedVisibility(
                    visible = editing,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    NoteStyleBar(
                        modifier = Modifier.fillMaxWidth(),
                        current = span,
                        currentLevel = currentLevel(),
                        onPickLevel = { setLevel(it) },
                        styleEnabled = targetBlock() != null,
                        onToggleBold = { toggleBold() },
                        onToggleItalic = { toggleItalic() },
                        onToggleUnderline = { toggleUnderline() },
                        onToggleStrike = { toggleStrike() },
                        onPickFontSize = { setFontSize(it) },
                        onPickFont = { setFont(it) },
                        onPickColor = { setColor(it) },
                        onPickHighlight = { setHighlight(it) },
                        onInsertImage = {
                            captureInsertAnchor()
                            viewModel.requestMediaPermission()
                        },
                        onPasteImage = {
                            if (!pasteRich()) viewModel.showMessage("剪贴板里没有可粘贴的内容")
                        },
                        pasteImageEnabled = hasClipImage,
                        onInsertDrawing = {
                            captureInsertAnchor()
                            drawingTarget = DrawingTarget(null, null)
                        },
                        onInsertAudio = {
                            captureInsertAnchor()
                            viewModel.requestMicPermission()
                        },
                        onPanelOpen = { panelOpen = it }
                    )
                }
            }

            if (recording) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    AudioRecordingOverlay(
                        onCancel = { recording = false },
                        onFinish = { bytes, ms -> onAudioRecorded(bytes, ms) }
                    )
                }
            }
        }
    }

    if (drawingTarget != null) {
        DrawingPadOverlay(
            initial = jsonToDrawing(drawingTarget?.strokes),
            onCancel = { drawingTarget = null },
            onConfirm = { confirmDrawing(it) }
        )
    }

    if (showPicker) {
        MediaPickerSheet(
            onDismiss = { showPicker = false },
            onConfirm = { uris ->
                showPicker = false
                insertImages(uris)
            }
        )
    }

    if (needSettings) {
        AlertDialog(
            onDismissRequest = { needSettings = false },
            title = { Text("需要相册权限") },
            text = { Text("请授予照片访问权限后，才能浏览相册并插入图片。") },
            confirmButton = {
                TextButton(onClick = {
                    needSettings = false
                    // The app is sending the user to Settings; that is not the
                    // user leaving. Without this the vault would lock while we
                    // are away and discard the note being edited.
                    viewModel.suppressLockOnLeave = true
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { needSettings = false }) { Text("取消") }
            }
        )
    }

    if (needMicSettings) {
        AlertDialog(
            onDismissRequest = { needMicSettings = false },
            title = { Text("需要麦克风权限") },
            text = { Text("录音只用于笔记音频，全程在本机完成并随库加密保存。请在系统设置中授予麦克风权限。") },
            confirmButton = {
                TextButton(onClick = {
                    needMicSettings = false
                    viewModel.suppressLockOnLeave = true
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { needMicSettings = false }) { Text("取消") }
            }
        )
    }

    if (showDiscard) {
        DiscardChangesDialog(
            onDismiss = { showDiscard = false },
            onDiscard = { showDiscard = false; leave() }
        )
    }

    // 删除入口原来在独立的详情页上，那个页面没了，按钮跟着搬到这里。
    if (showDelete) {
        existing?.let { target ->
            AlertDialog(
                onDismissRequest = { showDelete = false },
                title = { Text("删除笔记") },
                text = { Text("确定要删除「${target.title.ifBlank { "这条笔记" }}」吗？删除后无法恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        showDelete = false
                        scope.launch {
                            viewModel.deleteNote(target.uuid)
                            leave()
                        }
                    }) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { showDelete = false }) { Text("取消") }
                }
            )
        }
    }
}

/**
 * 剪贴板里有图时贴在底栏上方的一条提示。
 *
 * 长按菜单里那个"粘贴"是输入法给的，不走 Compose 的回调，拦不住 —— 这条横幅
 * 就是它的替代：复制完切回来一眼能看见，点一下就按原文顺序把文字和图片粘进来。
 */
@Composable
private fun PasteClipBanner(onPaste: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.ContentPaste,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "剪贴板中有内容",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onPaste) { Text("粘贴") }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "忽略")
            }
        }
    }
}

/**
 * 标题输入框。
 *
 * 用 BasicTextField 而不是 Material 的 TextField：后者自带 16dp 的内容内边
 * 距，标题就会比正文往右错开一格 —— 正文块用的也是 BasicTextField，两边得
 * 是同一个起点。占位符因此也要自己画。
 */
@Composable
private fun TitleField(value: String, onFocus: () -> Unit, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = { new -> onChange(new.replace("\n", "")) },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocus() },
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(
                        "标题",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                inner()
            }
        }
    )
}

/**
 * 一个文本块 = 一个**多行**输入框（回车就是块内的 '\n'，不再拆块）。
 *
 * 值是受控的：文本与 span 存在块里（要落库），selection / composition 存在
 * [stored] 里。回填时保留 composition，否则中文输入法的拼音候选会被打断。
 */
@Composable
private fun TextBlockEditor(
    block: TextBlock,
    stored: TextFieldValue?,
    autoFocus: Boolean,
    /** 聚焦，带上这一刻的光标位置 —— 插入媒体要按它决定插在哪。 */
    onFocus: (TextRange) -> Unit,
    /** 失焦：空的块趁这个机会收掉，免得占位符一直留在页面上。 */
    onBlur: () -> Unit = {},
    /** 是否显示"开始输入…"占位符：浏览态不显示，那会儿它只是个落脚点。 */
    hintVisible: Boolean = true,
    onValueChange: (TextFieldValue) -> Unit,
    onBackspaceAtStart: () -> Unit,
    /** 带图片的粘贴：返回 true 表示这次粘贴已由我们处理。 */
    onPasteRich: () -> Boolean = { false },
    /** 正文可视区的底边（窗口坐标 y），由滚动容器量出来。 */
    modifier: Modifier = Modifier
) {
    val requester = remember { FocusRequester() }
    // 只在"变成需要聚焦的那块"时请求，而不是每帧都请求。
    LaunchedEffect(autoFocus) { if (autoFocus) requester.requestFocus() }

    var focused by remember { mutableStateOf(false) }
    /** 最近一次的文本布局结果，用来算光标那一行的矩形。 */
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val caretRequester = remember { BringIntoViewRequester() }

    val annotated = spansToAnnotated(block.text, block.spans, block.para.level)
    val value = remember(block.text, block.spans, block.para, stored) {
        stored?.takeIf { it.text == block.text }?.copy(annotatedString = annotated)
            ?: TextFieldValue(
                annotatedString = annotated,
                selection = TextRange(block.text.length)
            )
    }

    // 输入框高度是 wrapContent：多写一行它就自己长高，父级滚动容器却不知道光标
    // 已经跑到视口外 —— 官方的自动滚只在输入框高度固定时管用。所以每次文本重新
    // 布局后，把光标那一行自己带进视口。光标本来就在视口里时 bringIntoView 不会
    // 动，所以敲字不会抖。
    LaunchedEffect(layoutResult) {
        val lr = layoutResult ?: return@LaunchedEffect
        if (!focused) return@LaunchedEffect
        val offset = value.selection.min.coerceIn(0, lr.layoutInput.text.length)
        val rect = runCatching { lr.getCursorRect(offset) }.getOrNull()
            ?: return@LaunchedEffect
        runCatching { caretRequester.bringIntoView(rect) }
    }

    // 这个版本的 Compose 还没有 Modifier.onPaste，粘贴只能从长按菜单这条路上
    // 接：包一层 TextToolbar，把"粘贴"换成我们的处理 —— 剪贴板里没图时返回
    // false，再交回系统默认那条路。
    val toolbar = rememberPasteToolbar(onPasteRich)

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(requester)
                // 把光标那一行带进视口走的是官方这条通道：自己调
                // scrollState.animateScrollBy 会被 TextField 内部的滚动动画顶掉
                // （同一个 ScrollState 上的动画互斥）。
                .bringIntoViewRequester(caretRequester)
                .onFocusChanged {
                    if (it.isFocused) {
                        focused = true
                        onFocus(value.selection)
                    } else {
                        focused = false
                        onBlur()
                    }
                }
                .onKeyEvent { event ->
                    // 光标在块首按退格：并进上一个块（或删掉前面的图片/音频）。
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace &&
                        value.selection.collapsed && value.selection.start == 0
                    ) {
                        onBackspaceAtStart()
                        true
                    } else {
                        false
                    }
                },
            // 关键：ImeAction.None 才会让输入法把回车当"换行"并给一个回车键。
            // 留 Unspecified 时，国内主流输入法（小米/搜狗/百度）一律显示"发送"，
            // 按下去什么都不会输入 —— 也就是"换行做不到"。
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            onTextLayout = { layoutResult = it },
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth()) {
                    if (block.text.isEmpty() && hintVisible) {
                        Text(
                            "开始输入…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    inner()
                }
            }
        )
    }
}

/**
 * 在系统的文本工具栏外面包一层，只改"粘贴"那一项：先给我们一次机会处理
 * （带图片的粘贴），处理不了再交回原来那个回调。
 */
@Composable
private fun rememberPasteToolbar(onPasteRich: () -> Boolean): TextToolbar {
    val base = LocalTextToolbar.current
    return remember(base) {
        object : TextToolbar {
            override val status: TextToolbarStatus get() = base.status

            override fun hide() = base.hide()

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                base.showMenu(
                    rect,
                    onCopyRequested,
                    { if (!onPasteRich()) onPasteRequested?.invoke() },
                    onCutRequested,
                    onSelectAllRequested
                )
            }
        }
    }
}

/**
 * 涂鸦块：就是一张 PNG，外加"重新编辑"（有笔画数据时才显示）。
 *
 * 跟图片块一样，浏览态只给"点开看大图"，那两个按钮收起来。
 */
@Composable
private fun DrawingBlockEditor(
    viewModel: AppViewModel,
    block: DrawingBlock,
    editing: Boolean,
    onView: () -> Unit,
    onDelete: () -> Unit,
    onRedraw: () -> Unit
) {
    val bitmapState = remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(block.ref) { bitmapState.value = viewModel.getNoteImage(block.ref) }
    val bitmap = bitmapState.value
    Box(Modifier.fillMaxWidth()) {
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (editing) Modifier else Modifier.clickable(onClick = onView)),
                contentScale = ContentScale.FillWidth
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
        if (editing) {
            Row(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                // 没有笔画数据（例如别处恢复的旧涂鸦）就只给删除。
                if (block.strokes != null) {
                    IconButton(
                        onClick = onRedraw,
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                            CircleShape
                        )
                    ) {
                        Icon(
                            Icons.Filled.Brush,
                            contentDescription = "重新编辑涂鸦",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        CircleShape
                    )
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除涂鸦",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * 图片块。
 *
 * 浏览态点图是"看大图"，删除按钮收起来 —— 那会儿用户是来看的，每张图上挂个
 * 删除图标既碍眼又容易误触。
 */
@Composable
private fun ImageBlockEditor(
    viewModel: AppViewModel,
    block: ImageBlock,
    editing: Boolean,
    onView: () -> Unit,
    onDelete: () -> Unit
) {
    val bitmapState = remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(block.ref) { bitmapState.value = viewModel.getNoteImage(block.ref) }
    val bitmap = bitmapState.value
    Box(Modifier.fillMaxWidth()) {
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (editing) Modifier else Modifier.clickable(onClick = onView)),
                contentScale = ContentScale.FillWidth
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
        if (editing) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除图片",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
