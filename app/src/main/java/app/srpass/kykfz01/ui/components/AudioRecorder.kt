package app.srpass.kykfz01.ui.components

import android.media.MediaRecorder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.srpass.kykfz01.vault.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 单次录音上限：10 分钟（约 7MB @96kbps），留足入库上限内的余量。 */
private const val MAX_RECORDING_MS = 10 * 60 * 1000L

/**
 * 录音浮层：一进来就开始录，[onFinish] 把音频字节和时长交回调用方。
 *
 * 用平台 [MediaRecorder]（AAC / m4a），不引任何第三方录音库。
 *
 * 两个必须处理的生命周期问题：
 * - **退后台立刻收工**：Android 14+ 会掐掉后台录音，与其留一个录了一半的坏
 *   文件，不如直接取消；
 * - **离开组合就释放**：MediaRecorder 不 release 会一直占着麦克风。
 */
@Composable
fun AudioRecordingOverlay(
    onCancel: () -> Unit,
    onFinish: (ByteArray, Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var elapsed by remember { mutableLongStateOf(0L) }
    var failed by remember { mutableStateOf(false) }
    var consumed by remember { mutableStateOf(false) }

    val recorderRef = remember { mutableStateOf<MediaRecorder?>(null) }
    val fileRef = remember { mutableStateOf<File?>(null) }
    val startedAt = remember { mutableLongStateOf(0L) }

    fun releaseRecorder() {
        recorderRef.value?.let { r ->
            runCatching { r.stop() }
            runCatching { r.release() }
        }
        recorderRef.value = null
    }

    fun cancel() {
        consumed = true
        releaseRecorder()
        fileRef.value?.delete()
        onCancel()
    }

    fun finish() {
        val file = fileRef.value
        val ms = elapsed
        consumed = true
        releaseRecorder()
        if (file == null || ms <= 0L) {
            file?.delete()
            onCancel()
            return
        }
        scope.launch {
            // 读文件是 IO：别在 UI 线程上啃一个几 MB 的文件。
            val bytes = withContext(Dispatchers.IO) {
                val data = runCatching { file.readBytes() }.getOrNull()
                file.delete()
                data
            }
            if (bytes == null || bytes.isEmpty()) onCancel() else onFinish(bytes, ms)
        }
    }

    DisposableEffect(Unit) {
        val file = File(context.cacheDir, "note_rec_${System.currentTimeMillis()}.m4a")
        val recorder = runCatching {
            MediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        }.getOrNull()
        if (recorder == null) {
            failed = true
            file.delete()
        } else {
            recorderRef.value = recorder
            fileRef.value = file
            startedAt.longValue = android.os.SystemClock.elapsedRealtime()
        }
        onDispose {
            releaseRecorder()
            if (!consumed) fileRef.value?.delete()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) cancel()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            elapsed = android.os.SystemClock.elapsedRealtime() - startedAt.longValue
            // 到点自动收工：再长就超过单份媒体的入库上限了，与其让用户录了
            // 半天最后被拒，不如到点就停。
            if (elapsed >= MAX_RECORDING_MS) { finish(); break }
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (failed) {
                Text("无法开始录音", style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "关闭") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.size(10.dp)
                    ) {}
                    Text(
                        "录音中 ${formatDuration(elapsed)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
                Row {
                    IconButton(onClick = { cancel() }) {
                        Icon(Icons.Filled.Close, contentDescription = "取消")
                    }
                    IconButton(onClick = { finish() }) {
                        Icon(Icons.Filled.Check, contentDescription = "完成")
                    }
                }
            }
        }
    }
}


