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
import com.metriq.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.ScreenHeader
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.core.NutritionLabelParser
import java.util.concurrent.Executors

@Composable
fun LabelScannerScreen(
    onBack: () -> Unit,
    onResult: (NutritionLabelParser.ParsedLabel) -> Unit,
    onEdit: (NutritionLabelParser.ParsedLabel) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val previewView = remember { PreviewView(context).apply { layoutParams = ViewGroup.LayoutParams(-1, -1) } }
    val lifecycleOwner = LocalLifecycleOwner.current
    var parsed by remember { mutableStateOf<NutritionLabelParser.ParsedLabel?>(null) }
    val parser = remember { NutritionLabelParser() }
    val busy = remember { mutableStateOf(false) }

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
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val analyzer = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analyzer.setAnalyzer(executor) { proxy ->
                            val media = proxy.image
                            if (media == null || busy.value) {
                                proxy.close()
                                return@setAnalyzer
                            }
                            busy.value = true
                            val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                                .process(image)
                                .addOnSuccessListener { result ->
                                    val value = parser.parse(result.text)
                                    if (value.confidence > 0.35f) parsed = value
                                }
                                .addOnCompleteListener {
                                    busy.value = false
                                    proxy.close()
                                }
                        }
                        provider?.unbindAll()
                        provider?.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analyzer,
                        )
                    } catch (_: Exception) {
                        // Camera unavailable; the status panel keeps the message visible.
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }
        onDispose { provider?.unbindAll(); executor.shutdown() }
    }

    FramedColumn {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView({ previewView }, Modifier.fillMaxSize())

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(MetriqColors.bg, Color.Transparent))),
            )
            Column(Modifier.fillMaxWidth()) {
                ScreenHeader(
                    title = stringResource(R.string.label_scan_title),
                    subtitle = stringResource(R.string.label_scan_hint),
                    onBack = onBack,
                )
            }

            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 300.dp, height = 190.dp)
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
                val label = parsed
                if (label != null) {
                    Panel(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MetriqColors.success),
                            )
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text(
                                stringResource(R.string.label_found, (label.confidence * 100).toInt()),
                                style = MaterialTheme.typography.labelLarge,
                                color = MetriqColors.accent,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${label.calories?.toInt() ?: 0} kcal",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MetriqColors.ink,
                        )
                        Text(
                            stringResource(
                                R.string.label_pfc,
                                label.protein?.toInt() ?: 0,
                                label.totalFat?.toInt() ?: 0,
                                label.totalCarbs?.toInt() ?: 0,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MetriqColors.ink2,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                            MetriqButton(
                                text = stringResource(R.string.edit),
                                onClick = { onEdit(label) },
                                modifier = Modifier.weight(1f),
                                primary = false,
                            )
                            MetriqButton(
                                text = stringResource(R.string.voice_save),
                                onClick = { onResult(label) },
                                modifier = Modifier.weight(1f),
                                primary = true,
                            )
                        }
                    }
                } else {
                    Panel(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (granted) MetriqColors.energy else MetriqColors.danger),
                            )
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text(
                                if (granted) stringResource(R.string.label_reading) else stringResource(R.string.label_camera_needed),
                                style = MaterialTheme.typography.titleMedium,
                                color = MetriqColors.ink,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.label_scan_info),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MetriqColors.ink2,
                        )
                    }
                }
            }
        }
    }
}
