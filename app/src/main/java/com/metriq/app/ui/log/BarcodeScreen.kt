package com.metriq.app.ui.log

import android.Manifest
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.ScreenHeader
import com.metriq.app.ui.theme.MetriqColors
import java.util.concurrent.Executors

@Composable
fun BarcodeScreen(
    onClose: () -> Unit,
    onDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    val previewView = remember { PreviewView(context).apply { layoutParams = ViewGroup.LayoutParams(-1, -1) } }
    val consumed = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.CAMERA) }

    DisposableEffect(granted) {
        var provider: ProcessCameraProvider? = null
        val executor = Executors.newSingleThreadExecutor()
        if (granted) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        provider = future.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analyzer = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analyzer.setAnalyzer(executor) { proxy ->
                            val media = proxy.image
                            if (media == null || consumed.value) {
                                proxy.close()
                                return@setAnalyzer
                            }
                            val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                            BarcodeScanning.getClient().process(image)
                                .addOnSuccessListener { results ->
                                    results.firstOrNull()?.rawValue?.let { value ->
                                        if (!consumed.value) {
                                            consumed.value = true
                                            onDetected(value)
                                        }
                                    }
                                }
                                .addOnCompleteListener { proxy.close() }
                        }
                        provider?.unbindAll()
                        provider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
                    } catch (_: Exception) {
                        // Camera unavailable; the status panel keeps the message visible.
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    FramedColumn {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView({ previewView }, Modifier.fillMaxSize())

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(listOf(MetriqColors.bg, Color.Transparent)),
                    ),
            )
            Column(Modifier.fillMaxWidth()) {
                ScreenHeader(
                    title = "Scan barcode",
                    subtitle = "Align the code inside the frame",
                    onBack = onClose,
                )
            }

            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 260.dp, height = 140.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MetriqColors.accent.copy(alpha = 0.04f))
                    .border(2.dp, MetriqColors.accent, RoundedCornerShape(20.dp)),
            )

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Panel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (granted) MetriqColors.success else MetriqColors.danger),
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(
                            if (granted) "Looking for a barcode" else "Camera permission needed",
                            style = MaterialTheme.typography.titleMedium,
                            color = MetriqColors.ink,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Point your camera at the barcode on a food package. It will be detected automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MetriqColors.ink2,
                    )
                }
            }
        }
    }
}
