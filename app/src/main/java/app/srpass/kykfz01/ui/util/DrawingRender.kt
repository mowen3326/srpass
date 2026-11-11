package app.srpass.kykfz01.ui.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import app.srpass.kykfz01.vault.DrawingDoc
import java.io.ByteArrayOutputStream

/**
 * 把矢量笔画重画成 PNG。
 *
 * 用 android.graphics 重画而不是对 Compose 的 Canvas 截图：截图会带上屏幕
 * 密度和主题色，换设备/换主题后图就变了；重画出来的图只取决于笔画本身。
 *
 * 底色固定为白：涂鸦在深色主题下显示时，透明底会变成一片黑，笔画看不清。
 */
fun renderDrawing(doc: DrawingDoc): ByteArray {
    val w = doc.exportWidth.coerceIn(256, 2048)
    val h = doc.exportHeight.coerceIn(256, 4096)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    for (stroke in doc.strokes) {
        if (stroke.pts.size < 4) continue
        val path = Path()
        path.moveTo(stroke.pts[0] * w, stroke.pts[1] * h)
        var i = 2
        while (i + 1 < stroke.pts.size) {
            path.lineTo(stroke.pts[i] * w, stroke.pts[i + 1] * h)
            i += 2
        }
        paint.color = stroke.color.toInt()
        // 线宽按导出宽度缩放：画布上看到多粗，导出的图就是多粗（相对而言）。
        paint.strokeWidth = stroke.width * (w / 1080f)
        canvas.drawPath(path, paint)
    }

    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    bitmap.recycle()
    return out.toByteArray()
}
