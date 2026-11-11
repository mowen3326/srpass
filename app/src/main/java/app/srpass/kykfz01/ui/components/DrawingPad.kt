package app.srpass.kykfz01.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.srpass.kykfz01.vault.DrawingDoc
import app.srpass.kykfz01.vault.DrawingStroke

private val DRAW_COLORS = listOf(
    0xFF000000L, 0xFFD32F2FL, 0xFF1565C0L, 0xFF2E7D32L, 0xFFEF6C00L, 0xFF6A1B9AL,
    0xFF5D4037L, 0xFF455A64L
)
private val DRAW_WIDTHS = listOf(4f, 8f, 18f)

/** 导出位图的基准宽度：屏幕上的线宽按它换算，保证屏幕所见即导出所得。 */
private const val BASE_WIDTH = 1080f

/**
 * 涂鸦板（编辑器内的一层覆盖，不单独开页面）。
 *
 * 不开页面的原因：编辑器里正在写的笔记状态只活在 NoteEditorScreen 里，一旦
 * 跳走再回来，整条未保存的笔记就没了。覆盖层不会影响下面编辑器的状态。
 *
 * 笔画存**归一化坐标**（[DrawingStroke]），屏幕上和导出的 PNG 用同一套比例，
 * 所以换手机、换密度画出来的图完全一样；导出走 [app.srpass.kykfz01.ui.util.renderDrawing]
 * 重画，而不是对这里截图（截图会带上主题色和屏幕密度）。
 */
@Composable
fun DrawingPadOverlay(
    initial: DrawingDoc?,
    onCancel: () -> Unit,
    onConfirm: (DrawingDoc) -> Unit
) {
    val strokes = remember { mutableStateListOf<DrawingStroke>() }
    LaunchedEffect(Unit) {
        // 只在首次进入时灌入已有笔画（"重新编辑"的场景）。放在 LaunchedEffect
        // 里而不是组合过程中：组合里改状态会触发再次组合，笔画会重复灌两遍。
        if (strokes.isEmpty() && initial != null) strokes.addAll(initial.strokes)
    }

    // 覆盖层自己吃掉返回键：否则一按返回就穿透到编辑器，弹出"要丢弃吗"。
    BackHandler { onCancel() }

    // 正在画的那一笔先存屏幕像素坐标，抬手时才换算成归一化坐标。
    val currentPts = remember { mutableStateListOf<Offset>() }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    var color by remember { mutableLongStateOf(DRAW_COLORS.first()) }
    var width by remember { mutableFloatStateOf(DRAW_WIDTHS[1]) }

    fun commitStroke() {
        val w = canvasSize.width.coerceAtLeast(1).toFloat()
        val h = canvasSize.height.coerceAtLeast(1).toFloat()
        if (currentPts.size >= 2) {
            val pts = ArrayList<Float>(currentPts.size * 2)
            for (p in currentPts) { pts += p.x / w; pts += p.y / h }
            strokes += DrawingStroke(color = color, width = width, pts = pts)
        }
        currentPts.clear()
    }

    // systemBarsPadding：App 是 enableEdgeToEdge 的，不避让的话顶栏"取消/完成"
    // 会压在状态栏底下（看得到点不到），底栏会压在手势导航条底下。
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, contentDescription = "取消")
                }
                Text("涂鸦", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    commitStroke()
                    // 一笔没画就点"完成"：当成取消，别在笔记里插一张空白图。
                    if (strokes.isEmpty()) {
                        onCancel()
                        return@TextButton
                    }
                    val ratio = if (canvasSize.width > 0) {
                        canvasSize.height.toFloat() / canvasSize.width.toFloat()
                    } else 1f
                    onConfirm(DrawingDoc(ratio = ratio, strokes = strokes.toList()))
                }) { Text("完成") }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
                    .background(Color.White)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { off ->
                                currentPts.clear()
                                currentPts += off
                            },
                            onDrag = { change, _ ->
                                // 丢掉过近的点：手指不动时也会刷出一堆重复坐标，
                                // 全都存下来 JSON 会白白膨胀好几倍。
                                val last = currentPts.lastOrNull()
                                if (last == null || (change.position - last).getDistance() > 2f) {
                                    currentPts += change.position
                                }
                            },
                            onDragEnd = { commitStroke() },
                            onDragCancel = { commitStroke() }
                        )
                    }
            ) {
                canvasSize = IntSize(size.width.toInt(), size.height.toInt())
                val scale = size.width / BASE_WIDTH
                drawStrokes(strokes, size.width, size.height, scale)
                if (currentPts.size >= 2) {
                    drawStroke(
                        pts = currentPts.map { Offset(it.x, it.y) },
                        color = Color(color),
                        width = width * scale
                    )
                }
            }

            // 两行而不是一行：撤销/清空 + 线宽 + 8 个颜色挤在一行有 460dp 宽，
            // 窄屏上右边那一半会被挤出屏幕 —— 按钮看着在布局里，实际点不到。
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row {
                        IconButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销")
                        }
                        IconButton(onClick = { strokes.clear() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "清空")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DRAW_WIDTHS.forEach { w ->
                            Box(
                                Modifier
                                    .padding(horizontal = 5.dp)
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .then(
                                        if (width == w) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        } else Modifier
                                    )
                                    .clickable { width = w },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier
                                        .size((w / 2f).dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DRAW_COLORS.forEach { c ->
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(
                                    if (color == c) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    } else Modifier.border(1.dp, Color(0x33000000), CircleShape)
                                )
                                .clickable { color = c }
                        )
                        Spacer(Modifier.width(2.dp))
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawStrokes(
    strokes: SnapshotStateList<DrawingStroke>,
    w: Float,
    h: Float,
    scale: Float
) {
    for (s in strokes) {
        if (s.pts.size < 4) continue
        drawStroke(
            pts = buildList {
                var i = 0
                while (i + 1 < s.pts.size) {
                    add(Offset(s.pts[i] * w, s.pts[i + 1] * h))
                    i += 2
                }
            },
            color = Color(s.color),
            width = s.width * scale
        )
    }
}

private fun DrawScope.drawStroke(pts: List<Offset>, color: Color, width: Float) {
    if (pts.size < 2) return
    val path = Path().apply {
        moveTo(pts.first().x, pts.first().y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}
