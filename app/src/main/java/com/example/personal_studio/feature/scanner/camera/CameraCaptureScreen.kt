package com.example.personal_studio.feature.scanner.camera

import com.example.personal_studio.ui.theme.Tg
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.ui.draw.clip
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.personal_studio.ui.components.ShutterButton
import com.example.personal_studio.ui.theme.DarkCanvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun CameraCaptureScreen(
    outputDir: File,
    /**
     * Fires when a JPEG is written. [liveCornersNorm] are the last live-
     * preview corners in [0,1]² portrait coordinates if auto-detect was on
     * and produced a valid quadrilateral at capture time — used to skip a
     * second, orientation-dependent post-capture inference that was
     * disagreeing with what the user just saw.
     */
    onCaptured: (file: File, autoDetect: Boolean, liveCornersNorm: List<android.graphics.PointF>?) -> Unit,
    onCancel: () -> Unit,
) = DarkCanvas {
    val toast = com.example.personal_studio.ui.shell.LocalShellToast.current

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val vm: CameraCaptureViewModel = hiltViewModel()
    val camState by vm.state.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        PermissionDeniedUi(
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onCancel = onCancel,
        )
        return@DarkCanvas
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
    }
    // ImageAnalysis runs continuously; we gate whether to ACT on frames via
    // vm.analyzeFrame() which checks autoDetect. Keeping it bound means
    // toggling auto-detect doesn't require rebinding the camera (which would
    // cause a visible preview flicker).
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
    }
    val executor = remember { ContextCompat.getMainExecutor(context) }

    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var focusRingAt by remember { mutableStateOf<Offset?>(null) }
    var focusRingKey by remember { mutableStateOf(0) }
    var boxSize by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(focusRingKey) {
        if (focusRingKey > 0) {
            delay(FOCUS_RING_TTL_MS)
            focusRingAt = null
        }
    }

    // Wire the analyzer once. vm.analyzeFrame drops frames while inference
    // is in flight (~270 ms) — our effective live-preview rate.
    LaunchedEffect(imageAnalysis) {
        imageAnalysis.setAnalyzer(executor) { proxy ->
            try {
                vm.analyzeFrame(proxy.toBitmap())
            } finally {
                proxy.close()
            }
        }
    }

    // React to the flash toggle by poking the live camera's CameraControl.
    LaunchedEffect(camState.flashOn, cameraInstance) {
        cameraInstance?.cameraControl?.enableTorch(camState.flashOn)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Tg.colors.bg)
            .onSizeChanged { boxSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val preview = previewViewRef ?: return@detectTapGestures
                    val cam = cameraInstance ?: return@detectTapGestures
                    val factory = preview.meteringPointFactory
                    val point = factory.createPoint(tap.x, tap.y)
                    val action = FocusMeteringAction.Builder(
                        point,
                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                    ).setAutoCancelDuration(FOCUS_AUTOCANCEL_S, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    cam.cameraControl.startFocusAndMetering(action)
                    focusRingAt = tap
                    focusRingKey++
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val preview = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                }
                previewViewRef = preview
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val previewUseCase = Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build()
                        .also { it.setSurfaceProvider(preview.surfaceProvider) }
                    provider.unbindAll()
                    cameraInstance = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        previewUseCase,
                        imageCapture,
                        imageAnalysis,
                    )
                }, executor)
                preview
            },
        )

        // Live corner polygon overlay — drawn over the preview when
        // autoDetect is on and the analyzer has produced a valid quad.
        if (camState.autoDetect && camState.liveCorners?.size == 4 && camState.analyzedWidth > 0 && boxSize.width > 0) {
            LiveCornersOverlay(
                corners = camState.liveCorners!!,
                analyzedW = camState.analyzedWidth,
                analyzedH = camState.analyzedHeight,
                boxSize = boxSize,
            )
        }

        val focusColor = Tg.colors.accent
        focusRingAt?.let { pos ->
            Canvas(Modifier.fillMaxSize()) {
                val radius = FOCUS_RING_RADIUS_DP.dp.toPx()
                drawCircle(
                    color = focusColor,
                    radius = radius,
                    center = pos,
                    style = Stroke(width = 2f),
                )
                drawLine(
                    color = focusColor,
                    start = Offset(pos.x - radius * 0.3f, pos.y),
                    end = Offset(pos.x + radius * 0.3f, pos.y),
                    strokeWidth = 1.5f,
                )
                drawLine(
                    color = focusColor,
                    start = Offset(pos.x, pos.y - radius * 0.3f),
                    end = Offset(pos.x, pos.y + radius * 0.3f),
                    strokeWidth = 1.5f,
                )
            }
        }

        // Top toggles — auto-detect + flash.
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.example.personal_studio.ui.components.PhotoChip(
                label = "自动识边",
                selected = camState.autoDetect,
                onClick = { vm.setAutoDetect(!camState.autoDetect) },
                icon = androidx.compose.material.icons.Icons.Outlined.DocumentScanner,
                role = androidx.compose.ui.semantics.Role.Switch,
            )
            Spacer(Modifier.width(8.dp))
            com.example.personal_studio.ui.components.PhotoChip(
                label = "闪光",
                selected = camState.flashOn,
                onClick = { vm.setFlash(!camState.flashOn) },
                icon = if (camState.flashOn) androidx.compose.material.icons.Icons.Outlined.FlashOn else androidx.compose.material.icons.Icons.Outlined.FlashOff,
                role = androidx.compose.ui.semantics.Role.Switch,
            )
        }

        // Shutter bar
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Tg.colors.bg.copy(alpha = 0.9f))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "取消",
                style = MaterialTheme.typography.bodyLarge,
                color = com.example.personal_studio.ui.theme.Tg.colors.text,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .clickable { onCancel() }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
            ShutterButton(
                onClick = {
                    outputDir.mkdirs()
                    val tmp = File(outputDir, "tmp-${System.currentTimeMillis()}.jpg")
                    val opts = ImageCapture.OutputFileOptions.Builder(tmp).build()
                    imageCapture.takePicture(
                        opts,
                        executor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                // Snapshot the live detection at capture instant.
                                // Normalise into [0,1]² portrait coords so the
                                // downstream VM can multiply by the decoded
                                // bitmap's dimensions without needing to know
                                // the analyzer's native size. The 90° CW
                                // rotation (landscape analyzer → portrait
                                // display) matches what the overlay renderer
                                // is already doing, so the numbers the user
                                // saw are what get passed on.
                                val liveNorm = camState.liveCorners
                                    ?.takeIf {
                                        it.size == 4 &&
                                            camState.analyzedWidth > 0 &&
                                            camState.analyzedHeight > 0
                                    }
                                    ?.map { p ->
                                        android.graphics.PointF(
                                            1f - p.y / camState.analyzedHeight.toFloat(),
                                            p.x / camState.analyzedWidth.toFloat(),
                                        )
                                    }
                                onCaptured(tmp, camState.autoDetect, liveNorm)
                            }

                            override fun onError(exc: ImageCaptureException) {
                                toast?.show("拍摄失败，请再试一次")
                            }
                        },
                    )
                },
            )
            // Balances 取消 so the shutter sits centred.
            Text("取消", style = MaterialTheme.typography.bodyLarge, color = Color.Transparent, modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
        }
    }
}

/**
 * Maps corners from analyzer-frame pixel space into the preview's Compose-px
 * space (FIT_CENTER letterbox) and draws the outline + handle markers.
 *
 * CameraX's analyzer frame is sensor-native orientation (landscape). With our
 * phone held portrait and preview configured as 4:3 → portrait box is 3:4.
 * We apply the same rotation we'd expect of the display (analyzer x-axis is
 * the short dim, y-axis is the long dim when rotated 90°).
 */
@Composable
private fun LiveCornersOverlay(
    corners: List<android.graphics.PointF>,
    analyzedW: Int,
    analyzedH: Int,
    boxSize: Size,
) {
    // Analyzer frames arrive in sensor orientation (landscape on the phone's
    // physical sensor). With setTargetAspectRatio(4:3) and the phone held
    // portrait, the analyzer-frame width corresponds to the display's HEIGHT
    // and vice versa — map accordingly.
    val rotatedAnalyzerW = analyzedH.toFloat()
    val rotatedAnalyzerH = analyzedW.toFloat()

    // FIT_CENTER of 3:4 (rotated) into the full-screen box.
    val contentScale = minOf(
        boxSize.width / rotatedAnalyzerW,
        boxSize.height / rotatedAnalyzerH,
    )
    val contentW = rotatedAnalyzerW * contentScale
    val contentH = rotatedAnalyzerH * contentScale
    val offsetX = (boxSize.width - contentW) / 2f
    val offsetY = (boxSize.height - contentH) / 2f

    // Rotate 90° CW: (x, y) in analyzer → (analyzerH - y, x) in display.
    val displayCorners = corners.map { p ->
        val rx = (analyzedH - p.y) * contentScale + offsetX
        val ry = p.x * contentScale + offsetY
        Offset(rx, ry)
    }

    val quadColor = Tg.colors.accent
    Canvas(Modifier.fillMaxSize()) {
        val path = Path().apply {
            moveTo(displayCorners[0].x, displayCorners[0].y)
            lineTo(displayCorners[1].x, displayCorners[1].y)
            lineTo(displayCorners[2].x, displayCorners[2].y)
            lineTo(displayCorners[3].x, displayCorners[3].y)
            close()
        }
        drawPath(path, color = quadColor.copy(alpha = 0.85f), style = Stroke(width = 3f))
        val dotR = 6.dp.toPx()
        displayCorners.forEach { c ->
            drawCircle(color = quadColor, radius = dotR, center = c)
        }
    }
}

private const val FOCUS_RING_TTL_MS = 800L
private const val FOCUS_RING_RADIUS_DP = 28f
private const val FOCUS_AUTOCANCEL_S = 3L

@Composable
private fun PermissionDeniedUi(onRequest: () -> Unit, onCancel: () -> Unit) {
    val c = com.example.personal_studio.ui.theme.Tg.colors
    Column(
        Modifier.fillMaxSize().background(c.bg).padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("需要相机权限", color = c.text, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "拍摄文档和题目需要使用相机。照片只保存在本机。",
            color = c.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.example.personal_studio.ui.components.TgPillButton("取消", onClick = onCancel, style = com.example.personal_studio.ui.components.PillStyle.SECONDARY, modifier = Modifier.weight(1f))
            com.example.personal_studio.ui.components.TgPillButton("授权", onClick = onRequest, modifier = Modifier.weight(1f))
        }
    }
}
