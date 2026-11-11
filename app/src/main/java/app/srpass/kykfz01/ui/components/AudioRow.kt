package app.srpass.kykfz01.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import app.srpass.kykfz01.ui.AppViewModel
import app.srpass.kykfz01.vault.AudioBlock
import app.srpass.kykfz01.vault.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 一条录音：播放 / 暂停 + 进度 + 时长。
 *
 * 平台 [MediaPlayer] 就够用，不引 ExoPlayer：本地一段 m4a，不需要流式播放、
 * 也不需要它那套网络栈（这个应用是离线优先的）。没有波形图 —— 波形要把音频
 * 解码成 PCM 才能算，为一个"好看"付出一次完整解码不值得。
 */
@Composable
fun NoteAudioRow(
    viewModel: AppViewModel,
    block: AudioBlock,
    onDelete: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stop() {
        player?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
        player = null
        playing = false
        position = 0L
    }

    // 离开页面必须释放：MediaPlayer 占着音频焦点不放，会导致别的应用放不出声。
    DisposableEffect(block.ref) { onDispose { stop() } }

    LaunchedEffect(playing) {
        while (playing) {
            position = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0).toLong()
            delay(200)
        }
    }

    fun toggle() {
        val mp = player
        if (mp != null) {
            if (runCatching { mp.isPlaying }.getOrDefault(false)) {
                runCatching { mp.pause() }
                playing = false
            } else {
                runCatching { mp.start() }
                playing = true
            }
            return
        }
        loading = true
        scope.launch {
            // MediaPlayer 要的是文件路径：解密流式落到缓存目录，播完不管它，
            // 下次同一条录音直接复用（缓存目录由系统清理）。
            val file = viewModel.getNoteAudioFile(block.ref)
            if (file == null) {
                loading = false
                viewModel.showMessage("音频读取失败")
                return@launch
            }
            val created = runCatching {
                MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener { playing = false; position = 0L }
                    prepare()
                    start()
                }
            }.getOrNull()
            loading = false
            if (created == null) {
                viewModel.showMessage("音频播放失败")
                return@launch
            }
            player = created
            playing = true
        }
    }

    val total = block.durationMs.coerceAtLeast(0L)
    val progress = if (total > 0) (position.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { toggle() }, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "暂停" else "播放"
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${formatDuration(position)} / ${formatDuration(total)}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (onDelete != null) {
                IconButton(onClick = { stop(); onDelete() }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除音频")
                }
            }
        }
    }
}
