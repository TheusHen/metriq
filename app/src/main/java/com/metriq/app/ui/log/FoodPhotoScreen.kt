package com.metriq.app.ui.log

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metriq.app.log.FoodRecognitionManager
import com.metriq.app.ui.components.FoodIcon
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.ScreenHeader
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.app.ui.theme.body
import com.metriq.app.ui.theme.bodyStrong
import com.metriq.app.ui.theme.caption
import com.metriq.app.ui.theme.label
import com.metriq.app.ui.theme.number24
import com.metriq.data.FoodHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * On-device photo recognition with a review step: pick which matches to log and adjust
 * each portion in grams before committing. Nothing is uploaded.
 */
@Composable
fun FoodPhotoScreen(
    manager: FoodRecognitionManager,
    onBack: () -> Unit,
    onSelect: (List<Pair<FoodHit, Double>>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var suggestions by remember { mutableStateOf<List<FoodRecognitionManager.Suggestion>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Upload a photo to identify foods on-device.") }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var gramsById by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    fun gramsFor(index: Int, suggestion: FoodRecognitionManager.Suggestion): Double =
        gramsById[index]?.toDoubleOrNull()
            ?: suggestion.food?.servingGrams
            ?: 100.0

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            busy = true
            error = false
            message = "Identifying foods on-device…"
            scope.launch {
                try {
                    val result = withContext(Dispatchers.Default) {
                        val bitmap = android.graphics.ImageDecoder.decodeBitmap(
                            android.graphics.ImageDecoder.createSource(context.contentResolver, uri),
                        )
                        manager.recognize(bitmap)
                    }
                    suggestions = result
                    selected = result.indices.filter { result[it].food != null }.toSet()
                    gramsById = result.mapIndexed { index, item ->
                        index to fmt0(item.food?.servingGrams ?: 100.0)
                    }.toMap()
                    message = if (result.isEmpty()) {
                        "No food labels identified. Try a clearer photo."
                    } else {
                        "Review the matches, then choose what to log."
                    }
                } catch (_: Exception) {
                    suggestions = emptyList()
                    selected = emptySet()
                    gramsById = emptyMap()
                    error = true
                    message = "Could not read that image. Try another photo."
                } finally {
                    busy = false
                }
            }
        }
    }

    val totalKcal = selected.toList().sumOf { index ->
        suggestions.getOrNull(index)?.let { suggestion ->
            suggestions[index].food?.calories?.let { calories ->
                calories * gramsFor(index, suggestion) / 100.0
            }
        } ?: 0.0
    }

    FramedColumn {
        ScreenHeader(
            title = "AI food photo",
            subtitle = "On-device recognition",
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
            Panel(Modifier.fillMaxWidth()) {
                Text(
                    "Choose a photo of your meal. Matching happens on your device — nothing is uploaded.",
                    style = MaterialTheme.typography.body,
                    color = MetriqColors.ink2,
                )
                Spacer(Modifier.height(14.dp))
                MetriqButton(
                    text = if (busy) "Identifying…" else "Upload photo",
                    onClick = { if (!busy) pick.launch("image/*") },
                    primary = true,
                )
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader(
                title = "Suggestions",
                action = if (suggestions.isEmpty()) null else "${suggestions.size} found",
            )
            Spacer(Modifier.height(10.dp))
            Panel(Modifier.fillMaxWidth()) {
                when {
                    busy -> Text(
                        text = "Identifying foods on-device…",
                        style = MaterialTheme.typography.body,
                        color = MetriqColors.ink2,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )

                    suggestions.isEmpty() -> Text(
                        text = message,
                        style = MaterialTheme.typography.body,
                        color = if (error) MetriqColors.danger else MetriqColors.ink2,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )

                    else -> {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.label,
                            color = MetriqColors.ink3,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                        suggestions.forEachIndexed { index, suggestion ->
                            val hit = suggestion.food
                            val match = String.format(
                                Locale.ENGLISH,
                                "%.0f%% match",
                                suggestion.confidence * 100,
                            )
                            if (hit == null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FoodIcon(
                                        "food",
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = suggestion.label,
                                            style = MaterialTheme.typography.bodyStrong,
                                            color = MetriqColors.ink2,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "$match · No match in your library",
                                            style = MaterialTheme.typography.caption,
                                            color = MetriqColors.ink3,
                                        )
                                    }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = index in selected,
                                        onCheckedChange = { checked ->
                                            selected = if (checked) selected + index else selected - index
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MetriqColors.accent,
                                            uncheckedColor = MetriqColors.ink3,
                                            checkmarkColor = MetriqColors.accentInk,
                                        ),
                                    )
                                    FoodIcon(hit.category)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = hit.name,
                                            style = MaterialTheme.typography.bodyStrong,
                                            color = MetriqColors.ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = match,
                                            style = MaterialTheme.typography.caption,
                                            color = MetriqColors.ink2,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = gramsById[index] ?: fmt0(hit.servingGrams ?: 100.0),
                                        onValueChange = { value -> gramsById = gramsById + (index to value) },
                                        label = { Text("Grams") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = darkFieldColors(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 46.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = "${fmt0(hit.calories * gramsFor(index, suggestion) / 100.0)} kcal",
                                        style = MaterialTheme.typography.caption,
                                        color = MetriqColors.ink2,
                                    )
                                }
                            }
                            if (index < suggestions.lastIndex) {
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Bottom action bar: live running total plus the commit button.
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
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${selected.size} selected",
                    style = MaterialTheme.typography.label,
                    color = MetriqColors.ink2,
                )
                Text(
                    text = "${fmt0(totalKcal)} kcal",
                    style = MaterialTheme.typography.number24,
                    color = MetriqColors.accent,
                )
            }
            Spacer(Modifier.width(12.dp))
            MetriqButton(
                text = "Log ${selected.size} foods",
                primary = true,
                onClick = {
                    val pairs = selected.toList().sorted().mapNotNull { index ->
                        suggestions.getOrNull(index)?.food?.let { hit ->
                            hit to gramsFor(index, suggestions[index])
                        }
                    }
                    if (pairs.isNotEmpty()) onSelect(pairs)
                },
            )
        }
    }
}
