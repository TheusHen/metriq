package com.metriq.app.ui.log

import android.Manifest
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.metriq.app.AppGraph
import com.metriq.app.R
import com.metriq.app.barcode.LookupOutcome
import com.metriq.app.barcode.OpenFoodFactsLookup
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.ScreenHeader
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.app.ui.theme.body
import com.metriq.app.ui.theme.bodyStrong
import com.metriq.app.ui.theme.caption
import com.metriq.app.ui.theme.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The opt-in marker sits on the method that actually reads [ImageProxy.image], so the
 * screen itself (and its callers) carry no opt-in requirement.
 */
private class BarcodeAnalyzer(
    private val consumed: AtomicBoolean,
    private val context: android.content.Context,
    private val onDecoded: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    @ExperimentalGetImage
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null || consumed.get()) {
            proxy.close()
            return
        }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        BarcodeScanning.getClient().process(image)
            .addOnSuccessListener { results ->
                results.firstOrNull()?.rawValue?.let { value ->
                    ContextCompat.getMainExecutor(context).execute { onDecoded(value) }
                }
            }
            .addOnCompleteListener { proxy.close() }
    }
}

@Composable
fun BarcodeScreen(
    onClose: () -> Unit,
    onDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    val previewView = remember { PreviewView(context).apply { layoutParams = ViewGroup.LayoutParams(-1, -1) } }
    val consumed = remember { AtomicBoolean(false) }

    var pendingCode by remember { mutableStateOf<String?>(null) }
    var pendingMessage by remember { mutableStateOf("") }
    var pendingIsError by remember { mutableStateOf(false) }
    var canLookupOnline by remember { mutableStateOf(false) }
    var onlineBusy by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }
    var manualError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.CAMERA) }

    // Always hand the barcode to MainActivity on the main thread, whichever path found it.
    fun emitDetected(code: String) {
        ContextCompat.getMainExecutor(context).execute { onDetected(code) }
    }

    fun decide(code: String) {
        pendingCode = code
        canLookupOnline = false
        pendingIsError = false
        onlineBusy = false
        pendingMessage = context.getString(R.string.barcode_checking, code)
        scope.launch {
            val local = withContext(Dispatchers.IO) { AppGraph.store.food.byBarcode(code) }
            withContext(Dispatchers.Main) {
                if (local != null) {
                    emitDetected(code)
                } else if (!OpenFoodFactsLookup.isValid(code)) {
                    consumed.set(false) // let the camera try another code
                    pendingIsError = true
                    pendingMessage = context.getString(R.string.barcode_invalid)
                } else {
                    consumed.set(false) // decision ended without a match: resume scanning
                    canLookupOnline = true
                    pendingMessage = context.getString(R.string.barcode_not_in_library)
                }
            }
        }
    }

    fun lookupOnline() {
        val code = pendingCode ?: return
        onlineBusy = true
        canLookupOnline = false
        pendingIsError = false
        pendingMessage = context.getString(R.string.barcode_searching_online, code)
        scope.launch {
            val outcome = OpenFoodFactsLookup.lookupAndStore(code, AppGraph.store)
            withContext(Dispatchers.Main) {
                onlineBusy = false
                when (outcome) {
                    is LookupOutcome.Success -> {
                        pendingMessage = context.getString(R.string.barcode_product_added, outcome.product.name)
                        emitDetected(code)
                    }
                    LookupOutcome.NotFound -> {
                        consumed.set(false)
                        pendingIsError = true
                        canLookupOnline = true
                        pendingMessage = context.getString(R.string.barcode_not_found, code)
                    }
                    LookupOutcome.NoNutrition -> {
                        consumed.set(false)
                        pendingIsError = true
                        canLookupOnline = true
                        pendingMessage = context.getString(R.string.barcode_no_nutrition)
                    }
                    LookupOutcome.InvalidBarcode -> {
                        consumed.set(false)
                        pendingIsError = true
                        pendingMessage = context.getString(R.string.barcode_invalid)
                    }
                    is LookupOutcome.NetworkError -> {
                        consumed.set(false)
                        pendingIsError = true
                        canLookupOnline = true
                        pendingMessage = context.getString(R.string.barcode_network_error, outcome.detail)
                    }
                    is LookupOutcome.Error -> {
                        consumed.set(false)
                        pendingIsError = true
                        canLookupOnline = true
                        pendingMessage = outcome.message
                    }
                }
            }
        }
    }

    fun manualLookup() {
        val normalized = OpenFoodFactsLookup.normalize(manual)
        if (!OpenFoodFactsLookup.isValid(normalized)) {
            manualError = context.getString(R.string.barcode_manual_invalid)
            return
        }
        manualError = null
        decide(normalized)
    }

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
                        analyzer.setAnalyzer(
                            executor,
                            BarcodeAnalyzer(consumed, context) { value ->
                                if (consumed.compareAndSet(false, true)) decide(value.trim())
                            },
                        )
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
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    FramedColumn {
        ScreenHeader(
            title = stringResource(R.string.barcode_title),
            subtitle = stringResource(R.string.barcode_subtitle),
            onBack = onClose,
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MetriqColors.surface1),
            ) {
                AndroidView({ previewView }, Modifier.fillMaxSize())
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(width = 240.dp, height = 120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, MetriqColors.accent, RoundedCornerShape(16.dp)),
                )
                if (!granted) {
                    Text(
                        text = stringResource(R.string.barcode_camera_needed),
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink2,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (granted) MetriqColors.success else MetriqColors.danger),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (granted) stringResource(R.string.barcode_looking) else stringResource(R.string.barcode_camera_unavailable),
                        style = MaterialTheme.typography.label,
                        color = MetriqColors.ink,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.barcode_instructions),
                    style = MaterialTheme.typography.body,
                    color = MetriqColors.ink2,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.barcode_online_note),
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink3,
                )
            }

            val code = pendingCode
            if (code != null) {
                Spacer(Modifier.height(12.dp))
                Panel(Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.search_barcode), style = MaterialTheme.typography.label, color = MetriqColors.ink3)
                    Spacer(Modifier.height(4.dp))
                    Text(text = code, style = MaterialTheme.typography.bodyStrong, color = MetriqColors.ink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = pendingMessage,
                        style = MaterialTheme.typography.body,
                        color = if (pendingIsError) MetriqColors.danger else MetriqColors.ink2,
                    )
                    if (canLookupOnline && !onlineBusy) {
                        Spacer(Modifier.height(12.dp))
                        MetriqButton(
                            text = stringResource(R.string.barcode_online_button),
                            onClick = { lookupOnline() },
                            primary = true,
                        )
                    } else if (onlineBusy) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.barcode_searching_general),
                            style = MaterialTheme.typography.caption,
                            color = MetriqColors.ink2,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Panel(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.barcode_enter),
                    style = MaterialTheme.typography.bodyStrong,
                    color = MetriqColors.ink,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manual,
                        onValueChange = { value ->
                            manual = value
                            manualError = null
                        },
                        placeholder = { Text(stringResource(R.string.barcode_example), color = MetriqColors.ink3) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = darkFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    MetriqButton(text = stringResource(R.string.barcode_lookup), onClick = { manualLookup() }, primary = false)
                }
                val error = manualError
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(text = error, style = MaterialTheme.typography.caption, color = MetriqColors.danger)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
