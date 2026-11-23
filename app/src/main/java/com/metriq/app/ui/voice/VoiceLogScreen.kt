package com.metriq.app.ui.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.metriq.app.R
import com.metriq.app.ui.components.FoodIcon
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.ScreenHeader
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.log.FoodDraft
import com.metriq.app.ui.log.FramedColumn
import com.metriq.app.ui.log.darkFieldColors
import com.metriq.app.ui.log.fmt0
import com.metriq.app.ui.log.macroMeta
import com.metriq.app.ui.log.toFoodDraft
import com.metriq.app.util.parseDoubleLoose
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.app.ui.theme.body
import com.metriq.app.ui.theme.bodyStrong
import com.metriq.app.ui.theme.caption
import com.metriq.app.ui.theme.h2
import com.metriq.app.ui.theme.number24
import com.metriq.app.voice.ParsedPortion
import com.metriq.app.voice.VoiceLogViewModel
import com.metriq.data.db.Food
import java.util.Locale

/**
 * Log food from a spoken or typed phrase. Recognition is strictly on-device
 * ([RecognizerIntent.EXTRA_PREFER_OFFLINE]); no result is ever sent to a cloud service.
 */
@Composable
fun VoiceLogScreen(
    onBack: () -> Unit,
    onSave: (FoodDraft) -> Unit,
) {
    val context = LocalContext.current
    val vm = remember { VoiceLogViewModel() }
    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }

    var phrase by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<ParsedPortion?>(null) }
    var food by remember { mutableStateOf<Food?>(null) }
    var gramsText by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    val startHint = stringResource(R.string.voice_start_pt)
    val unavailableHint = stringResource(R.string.voice_unavailable)
    var status by remember {
        mutableStateOf(if (available) startHint else unavailableHint)
    }

    // Permission result is routed through a remembered slot so the launcher can be
    // declared before the startListening function that consumes the result.
    val permissionResult = remember { mutableStateOf<(Boolean) -> Unit>({}) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> permissionResult.value(granted) }

    var recognizerRef by remember { mutableStateOf<SpeechRecognizer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            recognizerRef?.destroy()
            recognizerRef = null
        }
    }

    fun runParse(text: String) {
        val result = vm.parse(text)
        if (result == null) {
            parsed = null
            food = null
            status = context.getString(R.string.voice_no_match)
            return
        }
        parsed = result
        food = vm.foodFor(result.foodName)
        gramsText = fmt0(result.grams)
        status = context.getString(R.string.voice_matched, result.foodName)
    }

    fun startListening(permissionChecked: Boolean = false) {
        if (!available) {
            status = context.getString(R.string.voice_unavailable)
            return
        }
        if (!permissionChecked &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        recognizerRef?.destroy()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizerRef = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                status = context.getString(R.string.voice_listening)
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                listening = false
            }

            override fun onError(error: Int) {
                listening = false
                status = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        context.getString(R.string.voice_mic_permission_off)
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        context.getString(R.string.voice_no_catch)
                    else -> context.getString(R.string.voice_failed)
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    phrase = text
                    runParse(text)
                } else {
                    status = context.getString(R.string.voice_not_understood)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        listening = true
        status = context.getString(R.string.voice_listening)
        try {
            recognizer.startListening(intent)
        } catch (_: Exception) {
            listening = false
            status = context.getString(R.string.voice_cannot_start)
        }
    }

    LaunchedEffect(Unit) {
        permissionResult.value = { granted ->
            if (granted) {
                startListening(permissionChecked = true)
            } else {
                status = context.getString(R.string.voice_permission_denied)
            }
        }
    }

    FramedColumn {
        ScreenHeader(
            title = stringResource(R.string.voice_title),
            subtitle = stringResource(R.string.voice_subtitle),
            onBack = onBack,
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.voice_intro),
                style = MaterialTheme.typography.h2,
                color = MetriqColors.ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.voice_example),
                style = MaterialTheme.typography.body,
                color = MetriqColors.ink2,
            )
            Spacer(Modifier.height(14.dp))

            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = phrase,
                        onValueChange = { phrase = it },
                        placeholder = {
                            Text(stringResource(R.string.voice_placeholder), color = MetriqColors.ink3)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = darkFieldColors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { runParse(phrase) }),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    val micColor by animateColorAsState(
                        targetValue = if (listening) MetriqColors.danger else MetriqColors.surface2,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        label = "micBackground",
                    )
                    IconButton(
                        onClick = { startListening() },
                        enabled = available && !listening,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(micColor)
                            .border(1.dp, MetriqColors.border, CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Speak",
                            tint = if (listening) MetriqColors.ink else MetriqColors.accent,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    MetriqButton(
                        text = stringResource(R.string.voice_parse),
                        onClick = { runParse(phrase) },
                        primary = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.caption,
                    color = if (parsed == null) MetriqColors.ink3 else MetriqColors.ink2,
                )
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader(
                title = stringResource(R.string.voice_preview),
                action = parsed?.let { "${fmt0(it.grams)} g" },
            )
            Spacer(Modifier.height(10.dp))
            Panel(Modifier.fillMaxWidth()) {
                val p = parsed
                val f = food
                if (p == null || f == null) {
                    Text(
                        text = stringResource(R.string.voice_no_parse),
                        style = MaterialTheme.typography.body,
                        color = MetriqColors.ink2,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FoodIcon(f.category, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = p.foodName,
                                style = MaterialTheme.typography.bodyStrong,
                                color = MetriqColors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${fmt0(p.quantity)} ${p.unit} · " +
                                    stringResource(R.string.match_percent, (p.confidence * 100).toInt()),
                                style = MaterialTheme.typography.caption,
                                color = MetriqColors.ink2,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
                    Spacer(Modifier.height(14.dp))

                    val effectiveGrams = parseDoubleLoose(gramsText) ?: p.grams
                    val factor = effectiveGrams / 100.0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = gramsText,
                            onValueChange = { gramsText = it },
                            label = { Text(stringResource(R.string.grams)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = darkFieldColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text = fmt0(f.calories * factor),
                                style = MaterialTheme.typography.number24,
                                color = MetriqColors.accent,
                                maxLines = 1,
                            )
                            Text(
                                text = "kcal",
                                style = MaterialTheme.typography.caption,
                                color = MetriqColors.ink2,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = macroMeta(
                            f.calories * factor,
                            f.protein * factor,
                            f.carbs * factor,
                            f.fat * factor,
                        ),
                        style = MaterialTheme.typography.body,
                        color = MetriqColors.ink2,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Bottom action bar: stays visible above scrolled content and the keyboard.
        val servingOne = stringResource(R.string.serving_one)
        Row(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .background(MetriqColors.surface1)
                .border(
                    1.dp,
                    MetriqColors.border,
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetriqButton(
                text = stringResource(R.string.voice_save),
                primary = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val p = parsed
                    val f = food
                    val grams = parseDoubleLoose(gramsText)
                    if (p == null || f == null || grams == null || grams <= 0.0) return@MetriqButton
                    // Keep raw per-100 g macros: LogRepository scales by grams/100 on read.
                    onSave(f.toFoodDraft(grams, servingOne))
                    // onSave navigates to DASHBOARD; calling onBack here would land on SEARCH.
                },
            )
        }
    }
}
