package app.srpass.kykfz01.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import app.srpass.kykfz01.ui.components.CenterTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.srpass.kykfz01.ui.AppViewModel
import kotlinx.coroutines.launch
import java.util.UUID

/** 全屏看图要放得大到 5 倍，解码尺寸按 2048 留足细节。 */
private const val FULL_VIEW_MAX_DIM = 2048

/**
 * Full-screen viewer for a note image. Supports pinch-zoom and pan; the back
 * gesture / close button returns to the note it was opened from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    viewModel: AppViewModel,
    ref: String,
    noteUuid: UUID,
    fromHidden: Boolean = false
) {
    val bitmapState = remember { mutableStateOf<Bitmap?>(null) }
    // 加载失败（图在库里不存在、或解码不出来）以前是永远转圈，用户只能靠猜。
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(ref) {
        // 全屏看图是唯一需要高清的地方：正文那一列按屏幕宽解码就够了。
        bitmapState.value = viewModel.getNoteImage(ref, FULL_VIEW_MAX_DIM)
        loading = false
    }
    val bitmap = bitmapState.value
    val scope = rememberCoroutineScope()

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset += pan
    }

    fun leave() {
        // 带上这张图的 ref：笔记那头靠它滚回原来的位置。
        viewModel.openNoteEditor(noteUuid, fromHidden, ref)
    }

    BackHandler { leave() }

    Scaffold(
        topBar = {
            CenterTopBar(
                title = "图片预览",
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch { viewModel.saveNoteImageToGallery(ref) }
                    }) {
                        Icon(Icons.Filled.Save, contentDescription = "保存到相册")
                    }
                }
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .clipToBounds()
                // 点一下就关：全屏看图就是"看一眼就走"，再去够左上角那个返回
                // 箭头太绕。拖动和双指缩放都超出了 tap 的判定范围，不会误关。
                .pointerInput(Unit) { detectTapGestures(onTap = { leave() }) },
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .transformable(transformState),
                    contentScale = ContentScale.Fit
                )
            } else if (loading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Text("图片加载失败", color = Color.White)
            }
        }
    }
}
