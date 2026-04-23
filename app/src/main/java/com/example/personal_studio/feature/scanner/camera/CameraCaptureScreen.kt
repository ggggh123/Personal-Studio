package com.example.personal_studio.feature.scanner.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import java.io.File

@Composable
fun CameraCaptureScreen(
    outputDir: File,
    onCaptured: (File) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
        PermissionDeniedUi(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }, onCancel = onCancel)
        return
    }

    // 4:3 is the native sensor ratio on virtually every phone camera — it's
    // the maximum FOV the hardware exposes. Forcing Preview + ImageCapture to
    // the same ratio guarantees WYSIWYG without having to crop either side,
    // and lets the user shoot a full page from lower height.
    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
    }
    val executor = remember { ContextCompat.getMainExecutor(context) }

    // Held after bind so tap-to-focus can reach cameraControl. PreviewView
    // reference is needed to build a metering point from tap coords.
    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    // Focus-ring UI: show a brief outline at the last tap position.
    var focusRingAt by remember { mutableStateOf<Offset?>(null) }
    var focusRingKey by remember { mutableStateOf(0) }

    LaunchedEffect(focusRingKey) {
        if (focusRingKey > 0) {
            delay(FOCUS_RING_TTL_MS)
            focusRingAt = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Void)
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val preview = previewViewRef ?: return@detectTapGestures
                    val camera = cameraInstance ?: return@detectTapGestures
                    val factory = preview.meteringPointFactory
                    val point = factory.createPoint(tap.x, tap.y)
                    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                        .setAutoCancelDuration(FOCUS_AUTOCANCEL_S, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    camera.cameraControl.startFocusAndMetering(action)
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
                    // FIT_CENTER renders the full 4:3 sensor frame letterboxed
                    // in the taller portrait screen (narrow black strips top
                    // and bottom against Void — invisible). FILL_CENTER would
                    // crop the preview narrower than the capture, recreating
                    // the FOV mismatch the user pointed out.
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
                    )
                }, executor)
                preview
            },
        )

        focusRingAt?.let { pos ->
            Canvas(Modifier.fillMaxSize()) {
                val radius = FOCUS_RING_RADIUS_DP.dp.toPx()
                drawCircle(
                    color = Phosphor,
                    radius = radius,
                    center = pos,
                    style = Stroke(width = 2f),
                )
                // Inner cross-hair
                drawLine(
                    color = Phosphor,
                    start = Offset(pos.x - radius * 0.3f, pos.y),
                    end = Offset(pos.x + radius * 0.3f, pos.y),
                    strokeWidth = 1.5f,
                )
                drawLine(
                    color = Phosphor,
                    start = Offset(pos.x, pos.y - radius * 0.3f),
                    end = Offset(pos.x, pos.y + radius * 0.3f),
                    strokeWidth = 1.5f,
                )
            }
        }

        // Terminal-styled shutter bar
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Void.copy(alpha = 0.9f))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "[cancel]",
                style = MaterialTheme.typography.bodyMedium,
                color = FoamDim,
                modifier = Modifier.clickable { onCancel() },
            )
            Text(
                "[ ● capture ]",
                style = MaterialTheme.typography.bodyLarge,
                color = Phosphor,
                modifier = Modifier.clickable {
                    outputDir.mkdirs()
                    val tmp = File(outputDir, "tmp-${System.currentTimeMillis()}.jpg")
                    val opts = ImageCapture.OutputFileOptions.Builder(tmp).build()
                    imageCapture.takePicture(
                        opts,
                        executor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                onCaptured(tmp)
                            }

                            override fun onError(exc: ImageCaptureException) {
                                // TODO surface as snackbar
                            }
                        },
                    )
                },
            )
            Spacer(Modifier.width(60.dp)) // placeholder for future flash toggle
        }
    }
}

private const val FOCUS_RING_TTL_MS = 800L
private const val FOCUS_RING_RADIUS_DP = 28f
private const val FOCUS_AUTOCANCEL_S = 3L

@Composable
private fun PermissionDeniedUi(onRequest: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Void).padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("[!] camera permission denied", color = Carmine, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "capture requires the camera permission to be granted.",
            color = FoamDim,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(18.dp))
        Text("[grant]", color = Phosphor, modifier = Modifier.clickable(onClick = onRequest))
        Spacer(Modifier.height(8.dp))
        Text("[cancel]", color = FoamDim, modifier = Modifier.clickable(onClick = onCancel))
    }
}
