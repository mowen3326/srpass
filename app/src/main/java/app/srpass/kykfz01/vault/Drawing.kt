package app.srpass.kykfz01.vault

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一条笔画。
 *
 * 坐标是**归一化**的 0..1（相对画布宽高），不是像素：同一份笔画在不同屏幕
 * 尺寸、不同导出分辨率下画出来的图完全一致，也不会因为换手机就变形。
 * 点集用扁平的 Float 数组（x,y,x,y…）而不是对象数组，JSON 体积小一半以上。
 */
data class DrawingStroke(
    val color: Long,
    val width: Float,
    /** 归一化坐标：x0,y0,x1,y1… */
    val pts: List<Float>
)

/** 一幅涂鸦的矢量描述，序列化后存在 [DrawingBlock.strokes] 里。 */
data class DrawingDoc(
    /** 导出位图宽度（px），高度由 [ratio] 决定。 */
    val exportWidth: Int = 1080,
    /** 高 / 宽，决定导出高度。 */
    val ratio: Float = 1f,
    val strokes: List<DrawingStroke> = emptyList()
) {
    val exportHeight: Int get() = (exportWidth * ratio).toInt().coerceAtLeast(1)
}

private const val KEY_W = "w"
private const val KEY_RATIO = "r"
private const val KEY_STROKES = "s"
private const val KEY_COLOR = "c"
private const val KEY_WIDTH = "d"
private const val KEY_PTS = "p"

fun drawingToJson(doc: DrawingDoc): String {
    val arr = JSONArray()
    for (s in doc.strokes) {
        if (s.pts.size < 4) continue // 单点不成线
        val pts = JSONArray()
        for (p in s.pts) pts.put(p.toDouble())
        arr.put(JSONObject().put(KEY_COLOR, s.color).put(KEY_WIDTH, s.width.toDouble()).put(KEY_PTS, pts))
    }
    return JSONObject()
        .put(KEY_W, doc.exportWidth)
        .put(KEY_RATIO, doc.ratio.toDouble())
        .put(KEY_STROKES, arr)
        .toString()
}

/**
 * 反序列化笔画；返回 null 表示这份数据不是我们写的格式。
 *
 * 和 blocks 的解析一样：解析不了要明确报 null（当作"没有笔画、只能看图"），
 * 绝不能返回半截数据 —— 那会让人以为涂鸦被改坏了。
 */
fun jsonToDrawing(json: String?): DrawingDoc? {
    if (json.isNullOrBlank()) return null
    return runCatching {
        val o = JSONObject(json)
        val arr = o.optJSONArray(KEY_STROKES) ?: return@runCatching null
        val strokes = ArrayList<DrawingStroke>(arr.length())
        for (i in 0 until arr.length()) {
            val so = arr.optJSONObject(i) ?: continue
            val ptsArr = so.optJSONArray(KEY_PTS) ?: continue
            val pts = ArrayList<Float>(ptsArr.length())
            for (j in 0 until ptsArr.length()) pts += ptsArr.optDouble(j, 0.0).toFloat()
            strokes += DrawingStroke(
                color = so.optLong(KEY_COLOR, 0xFF000000L),
                width = so.optDouble(KEY_WIDTH, 6.0).toFloat(),
                pts = pts
            )
        }
        DrawingDoc(
            exportWidth = o.optInt(KEY_W, 1080),
            ratio = o.optDouble(KEY_RATIO, 1.0).toFloat(),
            strokes = strokes
        )
    }.getOrNull()
}
