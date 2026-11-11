package app.srpass.kykfz01.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import app.srpass.kykfz01.ui.AppViewModel
import app.srpass.kykfz01.ui.components.CenterTopBar
import app.srpass.kykfz01.ui.components.MediaPickerSheet
import app.srpass.kykfz01.vault.Totp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    viewModel: AppViewModel,
    fromHidden: Boolean = false,
    editingUuid: java.util.UUID? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // null while we do not know yet, then the user's answer.
    var granted by remember { mutableStateOf<Boolean?>(null) }
    // The same album picker the notes module uses, so choosing a saved QR
    // feels exactly like choosing a note image.
    var showPicker by remember { mutableStateOf(false) }
    // The camera keeps producing frames; without this one QR would fire the
    // hand-off dozens of times per second.
    var handled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.cameraPermission.collect { granted = it }
    }
    LaunchedEffect(Unit) {
        viewModel.requestCameraPermission()
    }
    LaunchedEffect(Unit) {
        viewModel.mediaPermissionResult.collect { ok ->
            if (ok) showPicker = true
            else viewModel.showMessage("需要相册权限才能选择图片")
        }
    }

    fun onScanned(text: String) {
        if (handled) return
        if (text.isBlank()) {
            viewModel.showMessage("没有识别到二维码")
            return
        }
        val entry = Totp.parseUri(text)
        if (entry == null) {
            // Not an authenticator QR (a plain URL, a Wi-Fi code, ...). Say so
            // and let the user keep scanning instead of failing silently.
            viewModel.showMessage("这不是验证器二维码")
            return
        }
        handled = true
        // The screen jumps straight to the editor, so without a tick the user
        // has nothing to tell a successful scan from a cancelled one.
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        viewModel.onOtpScanned(entry)
        // Back to the editor this scan was started from -- editingUuid, not
        // null: using null turned "edit an existing account" into "add another
        // one", leaving the original untouched.
        viewModel.openOtpEditor(editingUuid, fromHidden)
    }

    BackHandler { viewModel.openOtpEditor(editingUuid, fromHidden) }

    Scaffold(
        topBar = {
            CenterTopBar(
                title = "扫描二维码",
                navigationIcon = {
                    androidx.compose.material3.TextButton(
                        onClick = { viewModel.openOtpEditor(editingUuid, fromHidden) }
                    ) { Text("取消") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (granted) {
                    true -> {
                        CameraPreview(
                            onText = { text ->
                                // Analysis runs on a background thread; hop to
                                // main before touching the ViewModel.
                                ContextCompat.getMainExecutor(context).execute { onScanned(text) }
                            }
                        )
                        ScannerFrame()
                    }
                    false -> Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "需要相机权限才能扫码",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "也可以在系统设置中授予相机权限，或改用下方的相册导入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    null -> Box(Modifier.fillMaxSize())   // still waiting for the dialog
                }
            }

            // Always available: the setup QR is often shown on the very phone
            // the app runs on, where there is nothing to point a camera at.
            Button(
                onClick = viewModel::requestMediaPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Text(" 从相册选择二维码图片")
            }
        }
    }

    if (showPicker) {
        MediaPickerSheet(
            onDismiss = { showPicker = false },
            onConfirm = { uris ->
                showPicker = false
                // One QR is one account, so only the first image matters.
                val uri = uris.firstOrNull()
                if (uri != null) {
                    scope.launch(Dispatchers.IO) {
                        val text = decodeQrFromUri(context, uri)
                        withContext(Dispatchers.Main) { onScanned(text.orEmpty()) }
                    }
                }
            }
        )
    }
}

/** How often the centre of the frame is re-focused while nobody is tapping. */
private const val REFOCUS_INTERVAL_MS = 2_000L
/** How long a tap on the preview keeps the periodic centre focus out of the way. */
private const val TAP_QUIET_MS = 4_000L
/** How long the ring stays solid after a tap, then how long it takes to fade. */
private const val FOCUS_RING_HOLD_MS = 250L
private const val FOCUS_RING_FADE_MS = 550L
/** Ceiling for pinch zoom; the camera clamps it to whatever the lens allows. */
private const val MAX_ZOOM = 8f
/** How long after a pinch a lifted finger is still treated as part of the pinch. */
private const val TAP_AFTER_PINCH_QUIET_MS = 250L

@Composable
private fun CameraPreview(onText: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var analyzerExecutor: java.util.concurrent.ExecutorService? = null
    var focusHandler: Handler? = null
    var refocus: Runnable? = null
    var released = false
    // Where the last tap landed, in preview pixels, so the ring can sit exactly
    // where focus was aimed. The sequence restarts the fade on a new tap.
    var focusRing by remember { mutableStateOf<Offset?>(null) }
    var ringSeq by remember { mutableStateOf(0) }
    var zoomRatio by remember { mutableStateOf(1f) }
    Box(Modifier.fillMaxSize()) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                if (released) return@addListener
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                // Without an explicit target rotation the frames arrive in the
                // sensor's own landscape orientation, and the decoder has to
                // work that 90 degree rotation out again on every frame.
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                    .build()
                    .also {
                        val executor = Executors.newSingleThreadExecutor()
                        analyzerExecutor = executor
                        // One reader for the whole session: the analyser runs
                        // on a single thread, so a shared one is safe and saves
                        // rebuilding the hints for every frame.
                        val reader = MultiFormatReader().apply { setHints(qrHints()) }
                        it.setAnalyzer(executor) { proxy ->
                            val text = runCatching { decodeQr(proxy, reader) }.getOrNull()
                            proxy.close()
                            if (!text.isNullOrBlank()) onText(text)
                        }
                    }
                val camera = runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }.getOrNull()

                // What usually stands between the user and an instant scan is
                // not the decoder but a soft frame: the code is in the box, just
                // not sharp yet. So ask for a focus pass on the centre now and
                // then, and let a tap aim at whatever the user wants sharp.
                if (camera != null) {
                    var lastTapAt = 0L
                    val centre = FocusMeteringAction.Builder(
                        SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f),
                        FocusMeteringAction.FLAG_AF
                    ).setAutoCancelDuration(2, TimeUnit.SECONDS).build()
                    val handler = Handler(Looper.getMainLooper())
                    val task = object : Runnable {
                        override fun run() {
                            if (released) return
                            // Leave a tap alone -- pulling focus back to the
                            // centre would undo what was just asked for.
                            val tapIsRecent =
                                SystemClock.elapsedRealtime() - lastTapAt < TAP_QUIET_MS
                            if (!tapIsRecent && camera.cameraInfo.isFocusMeteringSupported(centre)) {
                                camera.cameraControl.startFocusAndMetering(centre)
                            }
                            handler.postDelayed(this, REFOCUS_INTERVAL_MS)
                        }
                    }
                    focusHandler = handler
                    refocus = task
                    handler.post(task)

                    // Two fingers zoom, one finger focuses. The pinch guard is
                    // what stops the lift at the end of a pinch from reading as
                    // a tap and firing a focus pass on top of the zoom.
                    var scaling = false
                    var lastScaleEndAt = 0L
                    val scaleDetector = ScaleGestureDetector(
                        ctx,
                        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                                scaling = true
                                return true
                            }

                            override fun onScaleEnd(detector: ScaleGestureDetector) {
                                scaling = false
                                lastScaleEndAt = SystemClock.elapsedRealtime()
                            }

                            override fun onScale(detector: ScaleGestureDetector): Boolean {
                                // Read the ratio back from the camera instead of
                                // keeping a local copy: the device clamps it, so
                                // a local copy drifts out of step with the lens.
                                val current = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                                val next = (current * detector.scaleFactor).coerceIn(1f, MAX_ZOOM)
                                camera.cameraControl.setZoomRatio(next)
                                zoomRatio = next
                                return true
                            }
                        }
                    )

                    previewView.setOnTouchListener { v, event ->
                        scaleDetector.onTouchEvent(event)
                        when (event.actionMasked) {
                            MotionEvent.ACTION_UP -> {
                                // Either way the lens just moved, so hold the
                                // periodic centre focus off for a moment.
                                lastTapAt = SystemClock.elapsedRealtime()
                                val isTap = !scaling &&
                                    SystemClock.elapsedRealtime() - lastScaleEndAt > TAP_AFTER_PINCH_QUIET_MS
                                if (isTap) {
                                    val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                                    focusRing = Offset(event.x, event.y)
                                    ringSeq++
                                    camera.cameraControl.startFocusAndMetering(
                                        FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                                            .setAutoCancelDuration(4, TimeUnit.SECONDS)
                                            .build()
                                    )
                                    v.performClick()
                                }
                            }
                        }
                        true
                    }
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        onRelease = { view ->
            // Detach the camera when the preview leaves composition -- without
            // this the provider keeps producing frames (and holds the camera
            // open) even though nothing is showing it.
            released = true
            runCatching { refocus?.let { focusHandler?.removeCallbacks(it) } }
            runCatching { analyzerExecutor?.shutdown() }
            runCatching {
                ProcessCameraProvider.getInstance(view.context).get().unbindAll()
            }
        }
    )

        // A ring where the tap landed: without it a tap is hard to tell from a
        // miss, and the focus pass itself is invisible.
        val ring = focusRing
        if (ring != null) {
            var solid by remember(ringSeq) { mutableStateOf(true) }
            LaunchedEffect(ringSeq) {
                delay(FOCUS_RING_HOLD_MS)
                solid = false
                delay(FOCUS_RING_FADE_MS)
                focusRing = null
            }
            val alpha by animateFloatAsState(
                targetValue = if (solid) 0.9f else 0f,
                animationSpec = tween(durationMillis = if (solid) 0 else FOCUS_RING_FADE_MS.toInt()),
                label = "focusRing"
            )
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White,
                    center = ring,
                    radius = 30.dp.toPx(),
                    alpha = alpha,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Only while zoomed in: at 1x there is nothing to report.
        if (zoomRatio > 1.01f) {
            Text(
                String.format(java.util.Locale.US, "%.1fx", zoomRatio),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Decodes the first QR code in a camera frame.
 *
 * Reads the YUV planes directly instead of going through ImageProxy.toBitmap():
 * one less conversion per frame, and no dependency on CameraX's Kotlin
 * extension helpers.
 */
private fun decodeQr(proxy: ImageProxy, reader: MultiFormatReader): String? {
    val nv21 = proxy.toNv21()
    // Only the middle square is handed to the decoder -- the area the frame on
    // screen marks out, plus margin (see [ROI_RATIO]). Binarising the whole of
    // every frame is what made the scan feel a beat behind the hand.
    val side = (minOf(proxy.width, proxy.height) * ROI_RATIO).toInt()
    val left = (proxy.width - side) / 2
    val top = (proxy.height - side) / 2
    val source = PlanarYUVLuminanceSource(
        nv21,
        proxy.width,
        proxy.height,
        left,
        top,
        side,
        side,
        false
    )
    return reader.decode(BinaryBitmap(HybridBinarizer(source)))?.text
}

/**
 * QR only, and read as UTF-8: a code carrying Chinese text without an ECI
 * segment decodes to mojibake otherwise, and the issuer/account names are
 * exactly the fields where that shows up.
 */
private fun qrHints(): Map<DecodeHintType, Any> = mapOf(
    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    DecodeHintType.CHARACTER_SET to "UTF-8"
)

/** Share of the shorter screen edge the viewfinder covers. */
private const val FRAME_RATIO = 0.7f

/**
 * Share of the shorter *frame* edge handed to the decoder.
 *
 * Deliberately larger than [FRAME_RATIO]: the preview is centre-cropped, so the
 * box the user sees maps onto the camera frame only approximately. The margin
 * keeps "it is inside the box but will not scan" impossible while still leaving
 * the decoder roughly a third of the pixels to work through.
 */
private const val ROI_RATIO = 0.8f

/**
 * The viewfinder: a dimmed surround, a bright frame, corner marks and a line
 * sweeping up and down inside it.
 *
 * Not decoration -- it is also where the decoder is looking, so the user can
 * see at a glance why a code is or is not being read, and bringing the code
 * into the box is what makes the scan land.
 */
@Composable
private fun ScannerFrame() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val side = minOf(maxWidth, maxHeight) * FRAME_RATIO
        val sweep = rememberInfiniteTransition(label = "qrSweep")
        val progress by sweep.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "qrSweepLine"
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val px = side.toPx()
            val left = (size.width - px) / 2f
            val top = (size.height - px) / 2f
            val corner = 24.dp.toPx()
            val frame = Path().apply {
                addRoundRect(
                    RoundRect(left, top, left + px, top + px, CornerRadius(corner, corner))
                )
            }
            val dim = Path.combine(
                PathOperation.Difference,
                Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) },
                frame
            )
            drawPath(dim, Color.Black.copy(alpha = 0.55f))
            drawPath(
                frame,
                Color.White.copy(alpha = 0.85f),
                style = Stroke(width = 2.dp.toPx())
            )

            val accent = Color(0xFF4CAF50)
            val arm = px * 0.12f
            val stroke = 6.dp.toPx()
            listOf(
                Offset(left, top + arm) to Offset(left, top),
                Offset(left, top) to Offset(left + arm, top),
                Offset(left + px - arm, top) to Offset(left + px, top),
                Offset(left + px, top) to Offset(left + px, top + arm),
                Offset(left + px, top + px - arm) to Offset(left + px, top + px),
                Offset(left + px, top + px) to Offset(left + px - arm, top + px),
                Offset(left + arm, top + px) to Offset(left, top + px),
                Offset(left, top + px) to Offset(left, top + px - arm)
            ).forEach { (from, to) ->
                drawLine(accent, from, to, strokeWidth = stroke, cap = StrokeCap.Round)
            }

            val y = top + px * progress
            drawLine(
                accent,
                Offset(left + 10.dp.toPx(), y),
                Offset(left + px - 10.dp.toPx(), y),
                strokeWidth = 3.dp.toPx()
            )
        }
        Text(
            "将二维码放入框内",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = maxHeight / 2 + side / 2 + 16.dp)
        )
    }
}

/**
 * Flattens an ImageProxy's YUV_420_888 planes into a single NV21 array,
 * honouring each plane's row/pixel stride (they are not guaranteed to be
 * tightly packed).
 */
private fun ImageProxy.toNv21(): ByteArray {
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer
    yBuffer.rewind()
    uBuffer.rewind()
    vBuffer.rewind()

    val ySize = yBuffer.remaining()
    val nv21 = ByteArray(ySize + width * height / 2)
    yBuffer.get(nv21, 0, ySize)

    val chromaHeight = height / 2
    val chromaWidth = width / 2
    var out = ySize
    for (row in 0 until chromaHeight) {
        var uIndex = row * uPlane.rowStride
        var vIndex = row * vPlane.rowStride
        for (col in 0 until chromaWidth) {
            nv21[out++] = vBuffer[vIndex]   // NV21 is V then U
            nv21[out++] = uBuffer[uIndex]
            uIndex += uPlane.pixelStride
            vIndex += vPlane.pixelStride
        }
    }
    return nv21
}

/**
 * Decodes the first QR code in a picked image.
 *
 * Off the main thread: a full-resolution photo takes a moment to binarise.
 */
private fun decodeQrFromUri(context: Context, uri: Uri): String? = runCatching {
    // Downscale before decoding: a 50MP phone photo would otherwise allocate a
    // ~200MB ARGB buffer just to read a ~400px QR, which can OOM on mid-range
    // devices. setTargetSize keeps the aspect ratio, capping the longer edge.
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val maxEdge = 1536
            if (maxOf(info.size.width, info.size.height) > maxEdge) {
                decoder.setTargetSize(maxEdge, maxEdge)
            }
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }.copy(Bitmap.Config.ARGB_8888, false)

    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
    MultiFormatReader().apply { setHints(qrHints()) }
        .decode(BinaryBitmap(HybridBinarizer(source)))?.text
}.getOrNull()
